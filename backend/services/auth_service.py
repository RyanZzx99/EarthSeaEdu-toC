"""
登录认证服务层

重要说明：
1. 本文件已严格按你当前真实 MySQL 表结构重写
2. 已统一使用真实字段名：
   - create_time / update_time
   - expires_time
   - delete_flag
3. 所有查询默认只查询 delete_flag='1' 的有效数据
4. 当前业务规则已按你的要求实现：
   - 手机号 + 密码登录
   - 手机验证码登录
   - 微信 PC 扫码登录
   - 微信首次登录必须强制绑定手机号
   - 首次手机号/微信登录自动注册
   - 绑定手机号时支持合并到已有手机号账号
"""

import secrets

# 导入 SQLAlchemy Session
from sqlalchemy.orm import Session

# 导入 ORM 模型
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import InviteCode

# 导入安全工具
from backend.utils.security import create_access_token
from backend.utils.security import create_bind_token
from backend.utils.security import hash_password
from backend.utils.security import validate_password_strength
from backend.utils.security import verify_password

# 导入通用工具
from backend.utils.common import add_minutes
from backend.utils.common import generate_state
from backend.utils.common import now_utc

# 导入短信服务
from backend.services.tencent_sms_service import send_sms_code
from backend.services.auth_cache_service import create_wechat_login_state_cache
from backend.services.auth_cache_service import consume_wechat_login_state_cache
from backend.services.auth_cache_service import save_sms_code_cache
from backend.services.auth_cache_service import validate_sms_send_allowed
from backend.services.auth_cache_service import verify_sms_code_cache

# 导入微信服务
from backend.services.wechat_service import get_wechat_access_info_by_code
from backend.services.wechat_service import get_wechat_userinfo
from backend.services.nickname_guard_service import create_nickname_audit_log
from backend.services.nickname_guard_service import evaluate_nickname_guard
from backend.services.nickname_guard_service import NicknameGuardHit


def get_active_user_by_id(db: Session, user_id: str) -> User | None:
    """
    按用户 ID 查询有效用户

    说明：
    1. 只查 delete_flag='1' 的有效记录
    2. 用户是否可登录，还需要继续检查 status 是否为 active
    """
    return (
        db.query(User)
        .filter(
            User.id == user_id,
            User.delete_flag == "1",
        )
        .first()
    )


def get_active_user_by_mobile(db: Session, mobile: str) -> User | None:
    """
    按手机号查询有效用户

    说明：
    1. 登录时必须只查有效记录
    2. mobile 是你当前业务里“手机号 + 密码登录”的账号字段
    """
    return (
        db.query(User)
        .filter(
            User.mobile == mobile,
            User.delete_flag == "1",
        )
        .first()
    )


def get_active_user_by_wechat_openid(db: Session, openid: str) -> User | None:
    """
    按微信 openid 查询对应的有效用户

    查询逻辑：
    1. 先去 user_auth_identities 表查有效微信身份
    2. 再根据 user_id 去 users 表查有效用户
    """
    # 查询微信身份绑定记录
    identity = (
        db.query(UserAuthIdentity)
        .filter(
            UserAuthIdentity.identity_type == "wechat_open",
            UserAuthIdentity.identity_key == openid,
            UserAuthIdentity.delete_flag == "1",
        )
        .first()
    )

    # 没找到说明没绑定过
    if not identity:
        return None

    # 再查对应用户
    return get_active_user_by_id(db, identity.user_id)


def get_active_wechat_identity_by_user_id(db: Session, user_id: str) -> UserAuthIdentity | None:
    """
    查询某个用户当前有效的微信身份绑定记录
    """
    return (
        db.query(UserAuthIdentity)
        .filter(
            UserAuthIdentity.user_id == user_id,
            UserAuthIdentity.identity_type == "wechat_open",
            UserAuthIdentity.delete_flag == "1",
        )
        .first()
    )


def apply_wechat_profile_to_user(user: User, userinfo: dict) -> None:
    """
    把微信公开资料同步到用户对象

    说明：
    1. 这里统一收口微信资料落库逻辑，避免分散在多个流程里
    2. 仅在微信接口实际返回了对应字段时才覆盖，避免把已有值误清空
    """
    # 中文注释：昵称和头像属于微信登录时最常见的公开资料，若微信返回则同步到用户表
    if userinfo.get("nickname"):
        user.nickname = userinfo.get("nickname")

    if userinfo.get("headimgurl"):
        user.avatar_url = userinfo.get("headimgurl")

    # 中文注释：微信 sex 通常为 0/1/2，这里仅在返回值存在时同步
    if userinfo.get("sex") is not None:
        user.sex = userinfo.get("sex")

    # 中文注释：地理字段用微信公开资料补齐，方便后续统计和展示
    if userinfo.get("province"):
        user.province = userinfo.get("province")

    if userinfo.get("city"):
        user.city = userinfo.get("city")

    if userinfo.get("country"):
        user.country = userinfo.get("country")


def create_login_log(
        db: Session,
        login_type: str,
        login_identifier: str | None,
        success: bool,
        user_id: str | None = None,
        failure_reason: str | None = None,
        ip: str | None = None,
        user_agent: str | None = None,
) -> None:
    """
    写入登录日志

    说明：
    1. 成功 / 失败日志都建议写
    2. 失败时 user_id 可能为空
    3. delete_flag 默认写 '1'
    """
    log = UserLoginLog(
        user_id=user_id,
        login_type=login_type,
        login_identifier=login_identifier,
        success=1 if success else 0,
        failure_reason=failure_reason,
        ip=ip,
        user_agent=user_agent,
        delete_flag="1",
    )

    db.add(log)
    db.commit()


def create_wechat_state(db: Session) -> str:
    """
    创建微信扫码登录 state

    说明：
    1. 前端获取微信扫码地址时调用
    2. 用于防止 CSRF
    3. 这里会把 state 写入 Redis，后续微信回调时校验
    """
    # 生成随机 state
    state = generate_state(16)

    # 中文注释：微信扫码登录 state 属于短期一次性状态，改为写入 Redis
    create_wechat_login_state_cache(
        state=state,
        expire_minutes=10,
    )

    # 返回 state
    return state


def verify_wechat_state(db: Session, state: str) -> bool:
    """
    校验微信扫码 state 是否有效

    校验规则：
    1. 必须存在
    2. Redis key 必须存在
    3. 必须未被消费
    4. 必须未过期
    5. 校验成功后立即删除，防止重复使用
    """
    # 中文注释：直接从 Redis 中原子消费 state，消费失败即说明无效或已过期
    return consume_wechat_login_state_cache(state)


def send_and_save_sms_code(db: Session, mobile: str, biz_type: str) -> None:
    """
    发送并保存短信验证码

    支持业务：
    1. login
    2. bind_mobile

    控制规则：
    1. 同手机号同业务，60 秒内不能重复发
    2. 同手机号同业务，每天最多发送固定次数
    3. 只保存验证码哈希，不保存明文
    4. 短期状态全部改走 Redis，不再写 MySQL
    """
    # 中文注释：先做 Redis 前置校验，避免冷却期内或超过日限时仍然触发真实短信发送
    validate_sms_send_allowed(
        mobile=mobile,
        biz_type=biz_type,
    )

    # 中文注释：先由短信服务生成并下发明文验证码，随后只把哈希写入 Redis
    code = send_sms_code(mobile=mobile, biz_type=biz_type)

    # 中文注释：验证码、冷却时间、每日次数统一使用 Redis 维护
    save_sms_code_cache(
        mobile=mobile,
        biz_type=biz_type,
        code=code,
    )


def verify_sms_code(db: Session, mobile: str, biz_type: str, code: str) -> bool:
    """
    校验短信验证码

    校验逻辑：
    1. 从 Redis 读取当前有效验证码
    2. Redis TTL 负责过期控制
    3. 验证码必须匹配
    4. 成功后立即删除，防止重复使用
    """
    # 中文注释：Redis 中不存在即表示验证码错误、已过期或已被使用
    return verify_sms_code_cache(
        mobile=mobile,
        biz_type=biz_type,
        code=code,
    )


def register_user_by_mobile(db: Session, mobile: str) -> User:
    """
    通过手机号自动注册正式用户

    使用场景：
    1. 手机验证码首次登录
    2. 当前手机号在系统中不存在时自动建号
    """
    user = User(
        mobile=mobile,
        mobile_verified=1,
        password_hash=None,
        nickname=None,
        avatar_url=None,
        status="active",
        is_temp_wechat_user=0,
        delete_flag="1",
    )

    db.add(user)
    db.commit()
    db.refresh(user)

    return user


def generate_invite_code_value(length: int = 10) -> str:
    """
    生成邀请码字符串

    说明：
    1. 使用大写字母+数字
    2. 排除易混字符，降低人工输入错误率
    """
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(length))


def create_invite_codes(
        db: Session,
        count: int,
        expires_days: int | None = None,
        note: str | None = None,
        issued_by_user_id: str | None = None,
) -> list[InviteCode]:
    """
    批量生成邀请码

    说明：
    1. 仅创建“未使用”邀请码
    2. 如设置过期天数，则自动写入过期时间
    """
    rows: list[InviteCode] = []

    for _ in range(count):
        # 循环直到生成一个数据库中不存在的邀请码
        while True:
            code_value = generate_invite_code_value()
            exists = (
                db.query(InviteCode)
                .filter(
                    InviteCode.code == code_value,
                    InviteCode.delete_flag == "1",
                )
                .first()
            )
            if not exists:
                break

        expires_time = None
        if expires_days is not None and expires_days > 0:
            expires_time = add_minutes(expires_days * 24 * 60)

        row = InviteCode(
            code=code_value,
            # status='1' 表示未使用
            status="1",
            issued_by_user_id=issued_by_user_id,
            # 邀请码生成时即记录发放时间（生成时间）
            issued_time=now_utc(),
            expires_time=expires_time,
            note=note,
            delete_flag="1",
        )
        db.add(row)
        rows.append(row)

    db.commit()
    for row in rows:
        db.refresh(row)

    return rows


def issue_invite_code(
        db: Session,
        code: str,
        mobile: str,
        issued_by_user_id: str | None = None,
) -> InviteCode:
    """
    发放邀请码给指定手机号

    说明：
    1. 发放不会改变邀请码为已使用
    2. 仅允许发放“未使用”状态（status='1'）的邀请码
    """
    row = (
        db.query(InviteCode)
        .filter(
            InviteCode.code == code.strip().upper(),
            InviteCode.delete_flag == "1",
        )
        .first()
    )

    if not row:
        raise ValueError("邀请码不存在")

    # status='1' 才允许发放
    if row.status != "1":
        raise ValueError("邀请码不可发放（已使用或已禁用）")

    row.issued_to_mobile = mobile
    row.issued_by_user_id = issued_by_user_id
    row.issued_time = now_utc()

    db.commit()
    db.refresh(row)
    return row


def update_invite_code_status(
        db: Session,
        code: str,
        target_status: str,
) -> InviteCode:
    """
    修改邀请码状态

    状态说明：
    1. '1' = 未使用
    2. '2' = 已使用
    3. '3' = 已禁用
    """
    # 仅允许 1/2/3 三种状态
    if target_status not in {"1", "2", "3"}:
        raise ValueError("状态仅支持 1、2、3")

    # 按邀请码查询有效记录
    row = (
        db.query(InviteCode)
        .filter(
            InviteCode.code == code.strip().upper(),
            InviteCode.delete_flag == "1",
        )
        .first()
    )

    if not row:
        raise ValueError("邀请码不存在")

    # 更新状态
    row.status = target_status

    # 若改成“未使用”，清理使用信息，便于重新发放/核销
    if target_status == "1":
        row.used_by_user_id = None
        row.used_time = None

    # 若改成“已使用”，且尚无使用时间，则补当前时间
    if target_status == "2" and not row.used_time:
        row.used_time = now_utc()

    db.commit()
    db.refresh(row)
    return row


def list_invite_codes(
        db: Session,
        status: str | None = None,
        mobile: str | None = None,
        code_keyword: str | None = None,
        limit: int = 50,
) -> tuple[list[InviteCode], int]:
    """
    查询邀请码列表

    说明：
    1. 支持按状态筛选
    2. 支持按发放手机号筛选
    3. 支持按邀请码关键字模糊筛选
    4. 返回列表与总数
    """
    # 安全限制查询条数，避免一次性拉取过多数据
    safe_limit = max(1, min(limit, 200))

    # 基础查询：仅查询有效记录
    query = db.query(InviteCode).filter(InviteCode.delete_flag == "1").order_by(
        InviteCode.status.asc() , InviteCode.issued_time.desc())

    # 状态筛选
    if status:
        query = query.filter(InviteCode.status == status)

    # 手机号筛选
    if mobile:
        query = query.filter(InviteCode.issued_to_mobile == mobile)

    # 邀请码关键字筛选（大小写无关）
    if code_keyword:
        query = query.filter(InviteCode.code.ilike(f"%{code_keyword.strip()}%"))

    # 先统计总数，再取前 N 条
    total = query.count()

    rows = (
        query.order_by(InviteCode.create_time.desc())
        .limit(safe_limit)
        .all()
    )

    return rows, total


def consume_invite_code_for_register(
        db: Session,
        code: str | None,
        register_mobile: str,
) -> InviteCode:
    """
    新用户注册时核销邀请码

    核销规则：
    1. 新注册必须提供邀请码
    2. 邀请码必须存在且状态为未使用（status='1'）
    3. 邀请码如有过期时间，必须未过期
    4. 邀请码如绑定了发放手机号，必须与注册手机号一致
    """
    if not code or not code.strip():
        raise ValueError("新用户注册需要填写邀请码")

    normalized_code = code.strip().upper()

    row = (
        db.query(InviteCode)
        .filter(
            InviteCode.code == normalized_code,
            InviteCode.delete_flag == "1",
        )
        .first()
    )

    if not row:
        raise ValueError("邀请码不存在")

    # status='1' 才允许核销
    if row.status != "1":
        raise ValueError("邀请码已使用或已失效")

    if row.expires_time and row.expires_time < now_utc():
        raise ValueError("邀请码已过期")

    if row.issued_to_mobile and row.issued_to_mobile != register_mobile:
        raise ValueError("邀请码与手机号不匹配")

    # 若邀请码此前未绑定手机号，则在首次被注册使用时补齐目标手机号
    # 说明：
    # 1. issued_time 语义为“生成时间”，不在这里改写
    # 2. issued_to_mobile 为空时补齐为当前注册手机号
    if not row.issued_to_mobile:
        row.issued_to_mobile = register_mobile

    # 核销后置为 status='2'（已使用）
    row.status = "2"
    row.used_time = now_utc()
    return row


def precheck_invite_code_for_register(
        db: Session,
        code: str | None,
        register_mobile: str,
) -> InviteCode:
    """
    新用户注册前预校验邀请码（不核销）

    说明：
    1. 仅做合法性检查，不修改状态
    2. 通过后返回邀请码记录对象，供后续真正核销使用
    """
    if not code or not code.strip():
        raise ValueError("新用户注册需要填写邀请码")

    normalized_code = code.strip().upper()

    row = (
        db.query(InviteCode)
        .filter(
            InviteCode.code == normalized_code,
            InviteCode.delete_flag == "1",
        )
        .first()
    )

    if not row:
        raise ValueError("邀请码不存在")

    # status='1' 才允许注册使用
    if row.status != "1":
        raise ValueError("邀请码已使用或已失效")

    if row.expires_time and row.expires_time < now_utc():
        raise ValueError("邀请码已过期")

    if row.issued_to_mobile and row.issued_to_mobile != register_mobile:
        raise ValueError("邀请码与手机号不匹配")

    return row


def login_by_password(db: Session, mobile: str, password: str) -> dict:
    """
    手机号 + 密码登录

    业务规则：
    1. 按手机号查用户
    2. 必须是有效用户 delete_flag='1'
    3. 用户状态必须 active
    4. 必须已设置密码
    5. 密码校验通过后签发正式 access token
    """
    # 按手机号查用户
    user = get_active_user_by_mobile(db, mobile)

    # 用户不存在
    if not user:
        raise ValueError("用户不存在")

    # 用户状态异常
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 未设置密码
    if not user.password_hash:
        raise ValueError("该账号尚未设置密码，请先使用短信登录")

    # 密码错误
    if not verify_password(password, user.password_hash):
        raise ValueError("密码错误")

    # 生成正式 access token
    access_token = create_access_token({"sub": str(user.id)})

    # 返回登录结果
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user_id": user.id,
        "mobile": user.mobile,
    }


def login_by_sms(db: Session, mobile: str, code: str, invite_code: str | None = None) -> dict:
    """
    手机验证码登录

    业务规则：
    1. 验证码校验通过后才能继续
    2. 如果手机号不存在，则自动注册
    3. 如果手机号已存在，则直接登录
    4. 自动补齐手机号已验证状态
    """
    # 先查当前手机号是否已有用户
    # 说明：
    # 1. 只有“新用户注册”才需要邀请码
    # 2. 老用户登录不校验邀请码
    user = get_active_user_by_mobile(db, mobile)

    # 新用户先预校验邀请码（只校验，不核销）
    if not user:
        precheck_invite_code_for_register(
            db=db,
            code=invite_code,
            register_mobile=mobile,
        )

    # 再校验 login 场景验证码
    ok = verify_sms_code(db, mobile, "login", code)
    if not ok:
        raise ValueError("验证码错误或已过期")

    # 不存在则自动注册（验证码通过后再正式核销邀请码）
    if not user:
        # 新用户注册时必须校验邀请码
        invite_row = consume_invite_code_for_register(
            db=db,
            code=invite_code,
            register_mobile=mobile,
        )

        user = register_user_by_mobile(db, mobile)

        # 记录邀请码使用人
        invite_row.used_by_user_id = user.id
        db.commit()

    # 状态异常则拒绝
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 如果手机号还没标记验证，这里补成已验证
    if user.mobile_verified != 1:
        user.mobile_verified = 1
        db.commit()

    # 签发正式 token
    access_token = create_access_token({"sub": str(user.id)})

    # 返回登录结果
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user_id": user.id,
        "mobile": user.mobile,
    }


def login_by_wechat(db: Session, code: str, state: str) -> dict:
    """
    微信 PC 网站扫码登录

    业务规则：
    1. 必须先校验 state
    2. 用微信 code 换 openid / unionid
    3. 如果该 openid 已绑定用户：
       - 若该用户已绑定手机号，则直接登录
       - 若未绑定手机号，则下发 bind_token，要求继续绑定手机号
    4. 如果该 openid 未绑定用户：
       - 自动创建临时微信用户
       - 自动绑定微信身份
       - 下发 bind_token，要求继续绑定手机号
    """
    # 第一步：校验 state
    if not verify_wechat_state(db, state):
        raise ValueError("微信登录 state 无效或已过期")

    # 第二步：通过 code 向微信换取 access_info
    access_info = get_wechat_access_info_by_code(code)

    # 取 openid
    openid = access_info.get("openid")

    # 取 unionid
    unionid = access_info.get("unionid")

    # openid 是当前用户在该微信开放平台应用下的唯一标识
    if not openid:
        raise ValueError("微信 openid 获取失败")

    # 第三步：查当前 openid 是否已绑定有效用户
    user = get_active_user_by_wechat_openid(db, openid)

    # 如果没绑定过，则自动创建临时用户并绑定微信身份
    if not user:
        # 尝试获取微信用户资料
        userinfo = {}

        # 如果微信返回了 access_token，则尝试拉取头像昵称
        if access_info.get("access_token"):
            userinfo = get_wechat_userinfo(
                access_token=access_info.get("access_token"),
                openid=openid,
            )

        # 创建临时微信用户
        user = User(
            mobile=None,
            mobile_verified=0,
            password_hash=None,
            nickname=None,
            avatar_url=None,
            sex=None,
            province=None,
            city=None,
            country=None,
            status="active",
            is_temp_wechat_user=1,
            delete_flag="1",
        )

        # 中文注释：新建微信临时用户后，统一把微信公开资料同步到用户对象
        apply_wechat_profile_to_user(user, userinfo)

        db.add(user)
        db.commit()
        db.refresh(user)

        # 创建微信身份绑定记录
        identity = UserAuthIdentity(
            user_id=user.id,
            identity_type="wechat_open",
            identity_key=openid,
            identity_extra=unionid,
            delete_flag="1",
        )

        db.add(identity)
        db.commit()

    # 中文注释：已存在的微信用户每次登录时也同步一遍微信公开资料，保证资料尽量保持最新
    if access_info.get("access_token"):
        latest_userinfo = get_wechat_userinfo(
            access_token=access_info.get("access_token"),
            openid=openid,
        )
        apply_wechat_profile_to_user(user, latest_userinfo)
        db.commit()
        db.refresh(user)

    # 用户状态检查
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 如果用户已经绑定手机号，则直接登录
    if user.mobile:
        access_token = create_access_token({"sub": str(user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": user.id,
            "mobile": user.mobile,
        }

    # 如果没有绑定手机号，则签发 bind_token
    bind_token = create_bind_token(
        {
            "sub": str(user.id),
            "openid": openid,
        }
    )

    return {
        "next_step": "bind_mobile",
        "bind_token": bind_token,
        "message": "微信登录成功，请先绑定手机号",
    }


def bind_mobile_for_wechat_user(
        db: Session,
        bind_user_id: str,
        mobile: str,
        code: str,
        invite_code: str | None = None,
) -> dict:
    """
    微信用户绑定手机号

    核心规则：
    1. 必须先校验 bind_mobile 场景验证码
    2. 如果手机号没有对应用户：
       - 直接绑定到当前微信临时用户
       - 当前用户转为正式用户
    3. 如果手机号已有老用户：
       - 若老用户未绑定其他微信，则把当前微信身份迁移到老用户
       - 当前临时微信用户停用
       - 最终登录老用户
    4. 如果老用户已绑定别的微信，则拒绝绑定
    """
    # 第一步：查询当前微信对应的用户
    current_user = get_active_user_by_id(db, bind_user_id)

    if not current_user:
        raise ValueError("绑定用户不存在")

    # 当前用户如果已经有手机号，说明理论上不该再走绑定流程
    if current_user.mobile:
        raise ValueError("当前微信账号已绑定手机号")

    # 查询当前用户对应的微信身份
    current_identity = get_active_wechat_identity_by_user_id(db, current_user.id)

    if not current_identity:
        raise ValueError("当前微信身份不存在")

    # 第二步：查询手机号是否已有有效用户
    existing_mobile_user = get_active_user_by_mobile(db, mobile)

    # 若是新手机号注册场景，先预校验邀请码（只校验，不核销）
    if not existing_mobile_user:
        precheck_invite_code_for_register(
            db=db,
            code=invite_code,
            register_mobile=mobile,
        )

    # 第三步：校验 bind_mobile 场景验证码
    ok = verify_sms_code(db, mobile, "bind_mobile", code)
    if not ok:
        raise ValueError("验证码错误或已过期")

    # 情况 A：手机号没有对应用户，属于“新注册”场景，必须核销邀请码
    if not existing_mobile_user:
        # 新手机号注册时强制邀请码校验
        invite_row = consume_invite_code_for_register(
            db=db,
            code=invite_code,
            register_mobile=mobile,
        )

        current_user.mobile = mobile
        current_user.mobile_verified = 1
        current_user.is_temp_wechat_user = 0
        db.commit()
        db.refresh(current_user)

        # 记录邀请码使用人
        invite_row.used_by_user_id = current_user.id
        db.commit()

        # 签发正式 token
        access_token = create_access_token({"sub": str(current_user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": current_user.id,
            "mobile": current_user.mobile,
        }

    # 情况 B：手机号已有老用户，但状态异常
    if existing_mobile_user.status != "active":
        raise ValueError("该手机号对应账号已被禁用")

    # 如果手机号对应的就是当前用户，做兼容处理
    if existing_mobile_user.id == current_user.id:
        current_user.mobile_verified = 1
        current_user.is_temp_wechat_user = 0
        db.commit()

        access_token = create_access_token({"sub": str(current_user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": current_user.id,
            "mobile": current_user.mobile,
        }

    # 查询老用户是否已绑定其他有效微信
    existing_wechat_identity = get_active_wechat_identity_by_user_id(db, existing_mobile_user.id)

    # 如果老用户已绑定其他微信，且不是同一个 openid，则拒绝
    if existing_wechat_identity and existing_wechat_identity.identity_key != current_identity.identity_key:
        raise ValueError("该手机号已绑定其他微信账号，无法再次绑定")

    # 第四步：把当前微信身份迁移到老用户
    current_identity.user_id = existing_mobile_user.id

    # 老用户补齐手机号已验证状态
    existing_mobile_user.mobile_verified = 1

    # 如果老用户缺昵称，可继承临时微信用户昵称
    if current_user.nickname:
        existing_mobile_user.nickname = current_user.nickname

    # 如果老用户缺头像，可继承临时微信用户头像
    if current_user.avatar_url:
        existing_mobile_user.avatar_url = current_user.avatar_url

    # 中文注释：微信资料字段在合并账号时一并迁移到正式账号，避免历史微信资料丢失
    if current_user.sex is not None:
        existing_mobile_user.sex = current_user.sex

    if current_user.province:
        existing_mobile_user.province = current_user.province

    if current_user.city:
        existing_mobile_user.city = current_user.city

    if current_user.country:
        existing_mobile_user.country = current_user.country

    # 当前临时用户不做物理删除，改成逻辑停用
    # 这样更利于审计和问题排查
    current_user.status = "disabled"
    current_user.delete_flag = "0"

    # 提交事务
    db.commit()
    db.refresh(existing_mobile_user)

    # 对老用户签发正式 token
    access_token = create_access_token({"sub": str(existing_mobile_user.id)})

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user_id": existing_mobile_user.id,
        "mobile": existing_mobile_user.mobile,
    }


def set_password_for_user(db: Session, user_id: str, new_password: str) -> None:
    """
    当前登录用户设置密码

    说明：
    1. 只允许对有效用户设置密码
    2. 用户状态必须是 active
    3. 这里直接覆盖 password_hash
    4. TODO：后续如要做密码历史校验，可以新增密码历史表
    """
    # 查询有效用户
    user = get_active_user_by_id(db, user_id)

    if not user:
        raise ValueError("用户不存在")

    # 用户状态校验
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 中文注释：如果用户已经设置过密码，则新密码不能与当前密码相同
    if user.password_hash and verify_password(new_password, user.password_hash):
        raise ValueError("新密码不能与当前密码相同")

    # 中文注释：设置密码前先走统一密码强度校验，确保前后端规则一致
    validate_password_strength(new_password)

    # 保存密码哈希
    user.password_hash = hash_password(new_password)

    # 提交
    db.commit()


def check_nickname_availability(
    db: Session,
    user_id: str,
    nickname: str,
    scene: str = "check",
    write_audit_log: bool = True,
    client_ip: str | None = None,
    user_agent: str | None = None,
) -> tuple[bool, str]:
    """
    检查当前用户准备修改的昵称是否可用

    说明：
    1. 这是昵称“检查”和“修改”共用的统一入口。
    2. scene 用于区分本次调用来源，便于审核日志落到不同业务场景。
    3. write_audit_log 允许上层在复用本函数时控制是否立即写日志，避免重复记账。
    4. client_ip / user_agent 由路由层透传，用于补全审核日志上下文。
    """
    # 中文注释：先确认当前用户有效，避免给失效账号执行可用性校验
    user = get_active_user_by_id(db, user_id)
    if not user:
        raise ValueError("用户不存在")

    if user.status != "active":
        raise ValueError("用户已被禁用")

    normalized_nickname = nickname.strip()
    if not normalized_nickname:
        # 中文注释：空昵称属于接口基础校验失败，不是词库命中，因此手工构造 system 类型命中结果。
        guard_hit = NicknameGuardHit(
            decision="reject",
            message="请输入昵称",
            normalized_nickname="",
            hit_source="system",
        )
        if write_audit_log:
            create_nickname_audit_log(
                db=db,
                scene=scene,
                raw_nickname=nickname,
                user_id=user_id,
                guard_hit=guard_hit,
                client_ip=client_ip,
                user_agent=user_agent,
            )
        return False, "请输入昵称"

    if len(normalized_nickname) > 100:
        # 中文注释：超长昵称也属于基础规则，仍统一记入昵称审核日志，方便统计无效输入。
        guard_hit = NicknameGuardHit(
            decision="reject",
            message="昵称长度不能超过 100 位",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        )
        if write_audit_log:
            create_nickname_audit_log(
                db=db,
                scene=scene,
                raw_nickname=nickname,
                user_id=user_id,
                guard_hit=guard_hit,
                client_ip=client_ip,
                user_agent=user_agent,
            )
        return False, "昵称长度不能超过 100 位"

    # 中文注释：若与当前昵称一致，则按不可用处理，避免用户误以为已发生变更
    if normalized_nickname == (user.nickname or ""):
        # 中文注释：这里同样不是词库命中，而是业务幂等校验失败。
        guard_hit = NicknameGuardHit(
            decision="reject",
            message="该昵称与当前昵称相同",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        )
        if write_audit_log:
            create_nickname_audit_log(
                db=db,
                scene=scene,
                raw_nickname=nickname,
                user_id=user_id,
                guard_hit=guard_hit,
                client_ip=client_ip,
                user_agent=user_agent,
            )
        return False, "该昵称与当前昵称相同"

    # 中文注释：真正的昵称风控从这里开始，调用独立 service 统一执行词条和联系方式规则。
    guard_hit = evaluate_nickname_guard(db, normalized_nickname)
    if not guard_hit.passed:
        # 中文注释：命中风控时直接记日志并返回，不再继续做重名校验。
        if write_audit_log:
            create_nickname_audit_log(
                db=db,
                scene=scene,
                raw_nickname=nickname,
                user_id=user_id,
                guard_hit=guard_hit,
                client_ip=client_ip,
                user_agent=user_agent,
            )
        return False, guard_hit.message

    duplicate_user = (
        db.query(User)
        .filter(
            User.nickname == normalized_nickname,
            User.delete_flag == "1",
            User.id != user_id,
        )
        .first()
    )

    if duplicate_user:
        # 中文注释：重名属于业务约束，不属于词库命中，所以依旧构造 system 类型日志。
        guard_hit = NicknameGuardHit(
            decision="reject",
            message="该昵称已被占用",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        )
        if write_audit_log:
            create_nickname_audit_log(
                db=db,
                scene=scene,
                raw_nickname=nickname,
                user_id=user_id,
                guard_hit=guard_hit,
                client_ip=client_ip,
                user_agent=user_agent,
            )
        return False, "该昵称已被占用"

    # 中文注释：走到这里说明昵称在风控和业务唯一性上都通过，可以记录一条 pass 日志。
    if write_audit_log:
        create_nickname_audit_log(
            db=db,
            scene=scene,
            raw_nickname=nickname,
            user_id=user_id,
            guard_hit=guard_hit,
            client_ip=client_ip,
            user_agent=user_agent,
        )

    return True, "该昵称可以使用"


def update_nickname_for_user(
    db: Session,
    user_id: str,
    nickname: str,
    client_ip: str | None = None,
    user_agent: str | None = None,
) -> User:
    """
    当前登录用户修改昵称

    说明：
    1. 只允许修改有效用户的昵称
    2. 用户状态必须是 active
    3. 昵称会自动去掉首尾空白

    额外说明：
    1. 修改接口复用 check_nickname_availability，保证“先检查”和“真正保存”规则一致。
    2. 这里调用检查逻辑时关闭其自动写日志，避免 update 过程产生两条相同失败日志。
    3. client_ip / user_agent 会传入审核日志，用于后续运营排查来源。
    """
    # 中文注释：昵称修改前先走统一可用性校验，避免和检查接口规则不一致
    available, message = check_nickname_availability(
        db=db,
        user_id=user_id,
        nickname=nickname,
        scene="update",
        write_audit_log=False,
        client_ip=client_ip,
        user_agent=user_agent,
    )
    if not available:
        raise ValueError(message)

    user = get_active_user_by_id(db, user_id)
    if not user:
        raise ValueError("用户不存在")

    normalized_nickname = nickname.strip()

    user.nickname = normalized_nickname
    db.commit()
    db.refresh(user)

    # 中文注释：昵称真正修改成功后，再单独记录一条 update 场景的 pass 日志。
    # 这样后续在运营后台可以区分：
    # 1. 用户只是试过某个昵称
    # 2. 用户最终真的把昵称改成了什么
    create_nickname_audit_log(
        db=db,
        scene="update",
        raw_nickname=nickname,
        user_id=user_id,
        guard_hit=NicknameGuardHit(
            decision="pass",
            message="昵称修改成功",
            normalized_nickname=normalized_nickname,
            hit_source="system",
        ),
        client_ip=client_ip,
        user_agent=user_agent,
    )
    return user


def check_password_availability(
    db: Session,
    user_id: str,
    new_password: str,
) -> tuple[bool, str]:
    """
    检查当前用户准备设置的新密码是否可用
    """
    user = get_active_user_by_id(db, user_id)
    if not user:
        raise ValueError("用户不存在")

    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 中文注释：如果已存在旧密码，则禁止新密码与当前密码重复
    if user.password_hash and verify_password(new_password, user.password_hash):
        return False, "新密码不能与当前密码相同"

    try:
        validate_password_strength(new_password)
    except ValueError as e:
        return False, str(e)

    return True, "该密码可以使用"


def update_user_status(
    db: Session,
    target_status: str,
    user_id: str | None = None,
    mobile: str | None = None,
) -> User:
    """
    管理员修改用户状态

    说明：
    1. 仅支持 active / disabled
    2. user_id 与 mobile 至少传一个
    3. 优先按 user_id 修改，其次按 mobile 修改
    """
    # 中文注释：用户状态只允许 active/disabled，避免落库脏数据
    if target_status not in {"active", "disabled"}:
        raise ValueError("用户状态仅支持 active 或 disabled")

    # 中文注释：至少需要一个定位条件，避免误操作全表
    if user_id is None and (mobile is None or not mobile.strip()):
        raise ValueError("user_id 与 mobile 至少需要提供一个")

    query = db.query(User).filter(User.delete_flag == "1")

    # 中文注释：优先走 user_id 精确匹配；未提供 user_id 时再按手机号匹配
    if user_id is not None:
        user = query.filter(User.id == user_id).first()
    else:
        user = query.filter(User.mobile == mobile.strip()).first()

    if not user:
        raise ValueError("用户不存在")

    # 中文注释：状态无变化时不重复提交事务
    if user.status == target_status:
        return user

    user.status = target_status
    db.commit()
    db.refresh(user)
    return user


def get_user_profile(db: Session, user_id: str) -> User | None:
    """
    获取当前用户资料

    说明：
    1. 只返回 delete_flag='1' 的有效用户
    2. 用户是否禁用，由上层接口视情况决定是否继续返回
    """
    return get_active_user_by_id(db, user_id)
