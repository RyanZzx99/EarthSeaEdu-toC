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

# 导入 SQLAlchemy Session
from sqlalchemy.orm import Session

# 导入 ORM 模型
from backend.models.auth_models import SmsCode
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import WechatLoginState

# 导入系统配置
from backend.config.db_conf import settings

# 导入安全工具
from backend.utils.security import create_access_token
from backend.utils.security import create_bind_token
from backend.utils.security import hash_code
from backend.utils.security import hash_password
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


def login_by_sms(db: Session, mobile: str, code: str) -> dict:
    """
    手机验证码登录

    业务规则：
    1. 验证码校验通过后才能继续
    2. 如果手机号不存在，则自动注册
    3. 如果手机号已存在，则直接登录
    4. 自动补齐手机号已验证状态
    """
    # 校验 login 场景验证码
    ok = verify_sms_code(db, mobile, "login", code)

    if not ok:
        raise ValueError("验证码错误或已过期")

    # 查询当前手机号是否已有用户
    user = get_active_user_by_mobile(db, mobile)

    # 不存在则自动注册
    if not user:
        user = register_user_by_mobile(db, mobile)

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


def bind_mobile_for_wechat_user(db: Session, bind_user_id: int, mobile: str, code: str) -> dict:
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
    # 第一步：校验 bind_mobile 场景验证码
    ok = verify_sms_code(db, mobile, "bind_mobile", code)

    if not ok:
        raise ValueError("验证码错误或已过期")

    # 第二步：查询当前微信对应的用户
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

    # 第三步：查询手机号是否已有有效用户
    existing_mobile_user = get_active_user_by_mobile(db, mobile)

    # 情况 A：手机号没有对应用户，直接绑定到当前微信用户
    if not existing_mobile_user:
        current_user.mobile = mobile
        current_user.mobile_verified = 1
        current_user.is_temp_wechat_user = 0
        db.commit()
        db.refresh(current_user)

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

    # 保存密码哈希
    user.password_hash = hash_password(new_password)

    # 提交
    db.commit()


def get_user_profile(db: Session, user_id: int) -> User | None:
    """
    获取当前用户资料

    说明：
    1. 只返回 delete_flag='1' 的有效用户
    2. 用户是否禁用，由上层接口视情况决定是否继续返回
    """
    return get_active_user_by_id(db, user_id)
