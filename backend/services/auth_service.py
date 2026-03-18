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

# 导入 datetime，用于计算当天起始时间等
from datetime import datetime
import secrets

# 导入 SQLAlchemy Session
from sqlalchemy.orm import Session

# 导入 ORM 模型
from backend.models.auth_models import SmsCode
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import WechatLoginState
from backend.models.auth_models import InviteCode

# 导入系统配置
from backend.config.db_conf import settings

# 导入安全工具
from backend.utils.security import create_access_token
from backend.utils.security import create_bind_token
from backend.utils.security import hash_code
from backend.utils.security import hash_password
from backend.utils.security import validate_password_strength
from backend.utils.security import verify_code
from backend.utils.security import verify_password

# 导入通用工具
from backend.utils.common import add_minutes
from backend.utils.common import generate_state
from backend.utils.common import now_utc

# 导入短信服务
from backend.services.tencent_sms_service import send_sms_code

# 导入微信服务
from backend.services.wechat_service import get_wechat_access_info_by_code
from backend.services.wechat_service import get_wechat_userinfo


def get_active_user_by_id(db: Session, user_id: int) -> User | None:
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


def get_active_wechat_identity_by_user_id(db: Session, user_id: int) -> UserAuthIdentity | None:
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


def create_login_log(
        db: Session,
        login_type: str,
        login_identifier: str | None,
        success: bool,
        user_id: int | None = None,
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
    3. 这里会把 state 入库，后续微信回调时校验
    """
    # 生成随机 state
    state = generate_state(16)

    # 创建 state 记录
    state_record = WechatLoginState(
        state=state,
        is_used=0,
        expires_time=add_minutes(10),
        delete_flag="1",
    )

    # 入库
    db.add(state_record)
    db.commit()

    # 返回 state
    return state


def verify_wechat_state(db: Session, state: str) -> bool:
    """
    校验微信扫码 state 是否有效

    校验规则：
    1. 必须存在
    2. 必须 delete_flag='1'
    3. 必须未使用
    4. 必须未过期
    5. 校验成功后立即标记为已使用，防止重复使用
    """
    # 查询有效 state
    record = (
        db.query(WechatLoginState)
        .filter(
            WechatLoginState.state == state,
            WechatLoginState.delete_flag == "1",
        )
        .first()
    )

    # 不存在则失败
    if not record:
        return False

    # 已使用则失败
    if record.is_used == 1:
        return False

    # 已过期则失败
    if record.expires_time < now_utc():
        return False

    # 标记已使用
    record.is_used = 1
    db.commit()

    return True


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
    """
    # 先查最近一条有效验证码记录
    latest_record = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
            SmsCode.delete_flag == "1",
        )
        .order_by(SmsCode.create_time.desc())
        .first()
    )

    # 如果有最近一条记录，则做冷却时间校验
    if latest_record:
        # 计算最近发送时间到当前的秒数差
        delta_seconds = (datetime.utcnow() - latest_record.create_time).total_seconds()

        # 如果还没到冷却时间，则拒绝发送
        if delta_seconds < settings.sms_send_cooldown_seconds:
            raise ValueError("发送过于频繁，请稍后再试")

    # 计算今天零点
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    # 统计今天该手机号该业务的发送次数
    today_count = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
            SmsCode.delete_flag == "1",
            SmsCode.create_time >= today_start,
        )
        .count()
    )

    # 超出每日上限则拒绝发送
    if today_count >= settings.sms_daily_limit:
        raise ValueError("今日验证码发送次数已达上限")

    # 真正发送短信
    # 注意：send_sms_code 返回的是明文验证码，仅用于本次立即做哈希入库
    code = send_sms_code(mobile=mobile, biz_type=biz_type)

    # 创建验证码记录
    record = SmsCode(
        mobile=mobile,
        biz_type=biz_type,
        code_hash=hash_code(code),
        is_used=0,
        expires_time=add_minutes(settings.sms_code_expire_minutes),
        delete_flag="1",
    )

    # 入库
    db.add(record)
    db.commit()


def verify_sms_code(db: Session, mobile: str, biz_type: str, code: str) -> bool:
    """
    校验短信验证码

    校验逻辑：
    1. 查询最近一条有效、未使用的验证码
    2. 必须未过期
    3. 验证码必须匹配
    4. 成功后立即标记为已使用
    """
    # 查询最近一条有效验证码
    record = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
            SmsCode.delete_flag == "1",
            SmsCode.is_used == 0,
        )
        .order_by(SmsCode.create_time.desc())
        .first()
    )

    # 没记录则失败
    if not record:
        return False

    # 过期则失败
    if record.expires_time < now_utc():
        return False

    # 验证码不匹配则失败
    if not verify_code(code, record.code_hash):
        return False

    # 标记已使用
    record.is_used = 1
    db.commit()

    return True


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
        issued_by_user_id: int | None = None,
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
        issued_by_user_id: int | None = None,
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
            nickname=userinfo.get("nickname"),
            avatar_url=userinfo.get("headimgurl"),
            status="active",
            is_temp_wechat_user=1,
            delete_flag="1",
        )

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
        bind_user_id: int,
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
    if not existing_mobile_user.nickname and current_user.nickname:
        existing_mobile_user.nickname = current_user.nickname

    # 如果老用户缺头像，可继承临时微信用户头像
    if not existing_mobile_user.avatar_url and current_user.avatar_url:
        existing_mobile_user.avatar_url = current_user.avatar_url

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


def set_password_for_user(db: Session, user_id: int, new_password: str) -> None:
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

    # 中文注释：设置密码前先走统一密码强度校验，确保前后端规则一致
    validate_password_strength(new_password)

    # 保存密码哈希
    user.password_hash = hash_password(new_password)

    # 提交
    db.commit()


def update_user_status(
    db: Session,
    target_status: str,
    user_id: int | None = None,
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


def get_user_profile(db: Session, user_id: int) -> User | None:
    """
    获取当前用户资料

    说明：
    1. 只返回 delete_flag='1' 的有效用户
    2. 用户是否禁用，由上层接口视情况决定是否继续返回
    """
    return get_active_user_by_id(db, user_id)
