# 导入 datetime
from datetime import datetime

# 导入 SQLAlchemy Session
from sqlalchemy.orm import Session

# 导入数据库模型
from backend.models.auth_models import SmsCode
from backend.models.auth_models import User
from backend.models.auth_models import UserAuthIdentity
from backend.models.auth_models import UserLoginLog
from backend.models.auth_models import WechatLoginState

# 导入配置
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
from backend.utils.common import add_seconds
from backend.utils.common import generate_state
from backend.utils.common import now_utc

# 导入短信服务
from backend.services.tencent_sms_service import send_sms_code

# 导入微信服务
from backend.services.wechat_service import get_wechat_access_info_by_code
from backend.services.wechat_service import get_wechat_userinfo


def get_user_by_mobile(db: Session, mobile: str) -> User | None:
    """
    按手机号查询用户
    """
    return db.query(User).filter(User.mobile == mobile).first()


def get_user_by_wechat_openid(db: Session, openid: str) -> User | None:
    """
    按微信 openid 查询用户
    """
    identity = (
        db.query(UserAuthIdentity)
        .filter(
            UserAuthIdentity.identity_type == "wechat_open",
            UserAuthIdentity.identity_key == openid,
        )
        .first()
    )

    if not identity:
        return None

    return db.query(User).filter(User.id == identity.user_id).first()


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
    """
    log = UserLoginLog(
        user_id=user_id,
        login_type=login_type,
        login_identifier=login_identifier,
        success=success,
        failure_reason=failure_reason,
        ip=ip,
        user_agent=user_agent,
    )

    db.add(log)
    db.commit()


def create_wechat_state(db: Session) -> str:
    """
    创建微信扫码 state
    """
    # 生成随机 state
    state = generate_state(16)

    # 创建记录
    state_record = WechatLoginState(
        state=state,
        is_used=False,
        expires_at=add_minutes(10),
    )

    # 写入数据库
    db.add(state_record)
    db.commit()

    # 返回 state
    return state


def verify_wechat_state(db: Session, state: str) -> bool:
    """
    校验微信 state
    """
    # 查询 state 记录
    record = (
        db.query(WechatLoginState)
        .filter(WechatLoginState.state == state)
        .first()
    )

    # 不存在则失败
    if not record:
        return False

    # 已使用则失败
    if record.is_used:
        return False

    # 已过期则失败
    if record.expires_at < now_utc():
        return False

    # 标记已使用
    record.is_used = True
    db.commit()

    return True


def send_and_save_sms_code(db: Session, mobile: str, biz_type: str) -> None:
    """
    发送并保存短信验证码
    """
    # 查询最近一条同手机号、同业务验证码
    latest_record = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
        )
        .order_by(SmsCode.id.desc())
        .first()
    )

    # 如果最近发送时间未超过冷却时间，则拒绝发送
    if latest_record:
        cooldown_deadline = latest_record.created_at.timestamp() + settings.sms_send_cooldown_seconds
        if datetime.utcnow().timestamp() < cooldown_deadline:
            raise ValueError("发送过于频繁，请稍后再试")

    # 查询当天发送次数
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    today_count = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
            SmsCode.created_at >= today_start,
        )
        .count()
    )

    # 超过每日上限则拒绝
    if today_count >= settings.sms_daily_limit:
        raise ValueError("今日验证码发送次数已达上限")

    # 真正发送短信
    code = send_sms_code(mobile=mobile, biz_type=biz_type)

    # 存储哈希，不存明文
    record = SmsCode(
        mobile=mobile,
        biz_type=biz_type,
        code_hash=hash_code(code),
        is_used=False,
        expires_at=add_minutes(settings.sms_code_expire_minutes),
    )

    # 写入数据库
    db.add(record)
    db.commit()


def verify_sms_code(db: Session, mobile: str, biz_type: str, code: str) -> bool:
    """
    校验短信验证码
    """
    # 查询最近一条未使用验证码
    record = (
        db.query(SmsCode)
        .filter(
            SmsCode.mobile == mobile,
            SmsCode.biz_type == biz_type,
            SmsCode.is_used == False,  # noqa: E712
        )
        .order_by(SmsCode.id.desc())
        .first()
    )

    # 没有记录则失败
    if not record:
        return False

    # 已过期则失败
    if record.expires_at < now_utc():
        return False

    # 校验验证码
    if not verify_code(code, record.code_hash):
        return False

    # 标记已使用
    record.is_used = True
    db.commit()

    return True


def register_user_by_mobile(db: Session, mobile: str) -> User:
    """
    按手机号自动注册用户
    """
    user = User(
        mobile=mobile,
        mobile_verified=True,
        status="active",
        is_temp_wechat_user=False,
    )

    db.add(user)
    db.commit()
    db.refresh(user)

    return user


def login_by_password(db: Session, mobile: str, password: str) -> dict:
    """
    手机号 + 密码登录
    """
    # 查用户
    user = get_user_by_mobile(db, mobile)

    # 用户不存在
    if not user:
        raise ValueError("用户不存在")

    # 用户被禁用
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

    # 返回响应
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user_id": user.id,
        "mobile": user.mobile,
    }


def login_by_sms(db: Session, mobile: str, code: str) -> dict:
    """
    手机验证码登录
    """
    # 校验验证码
    ok = verify_sms_code(db, mobile, "login", code)

    if not ok:
        raise ValueError("验证码错误或已过期")

    # 按手机号查用户
    user = get_user_by_mobile(db, mobile)

    # 如果用户不存在，则自动注册
    if not user:
        user = register_user_by_mobile(db, mobile)

    # 用户状态校验
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 补充手机号验证状态
    if not user.mobile_verified:
        user.mobile_verified = True
        db.commit()

    # 生成正式 token
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
    微信扫码登录

    核心规则：
    1 必须校验 state
    2 如果 openid 已绑定用户：
       - 若用户已绑定手机号，直接登录
       - 若用户未绑定手机号，返回 bind_token
    3 如果 openid 未绑定用户：
       - 自动创建临时微信账号
       - 绑定 openid
       - 返回 bind_token
    """
    # 校验 state
    if not verify_wechat_state(db, state):
        raise ValueError("微信登录 state 无效或已过期")

    # 用 code 换微信信息
    access_info = get_wechat_access_info_by_code(code)

    # 拿到 openid
    openid = access_info.get("openid")

    # 拿到 unionid
    unionid = access_info.get("unionid")

    # 兜底校验
    if not openid:
        raise ValueError("微信 openid 获取失败")

    # 查询是否已绑定过该微信
    user = get_user_by_wechat_openid(db, openid)

    # 如果没绑定过，则创建临时用户并绑定微信
    if not user:
        # 尝试拉取微信昵称头像
        userinfo = {}
        if access_info.get("access_token"):
            userinfo = get_wechat_userinfo(
                access_token=access_info.get("access_token"),
                openid=openid,
            )

        # 创建临时用户
        user = User(
            mobile=None,
            mobile_verified=False,
            password_hash=None,
            nickname=userinfo.get("nickname"),
            avatar_url=userinfo.get("headimgurl"),
            status="active",
            is_temp_wechat_user=True,
        )

        db.add(user)
        db.commit()
        db.refresh(user)

        # 创建微信身份绑定
        identity = UserAuthIdentity(
            user_id=user.id,
            identity_type="wechat_open",
            identity_key=openid,
            identity_extra=unionid,
        )

        db.add(identity)
        db.commit()

    # 如果用户被禁用
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 已绑定手机号，则直接登录
    if user.mobile:
        access_token = create_access_token({"sub": str(user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": user.id,
            "mobile": user.mobile,
        }

    # 未绑定手机号，则下发 bind token
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

    规则：
    1 校验 bind_mobile 验证码
    2 如果手机号没有对应用户：
       - 直接绑到当前微信临时账号
    3 如果手机号已有老用户：
       - 如果老用户未绑定其他微信，则把微信身份迁移到老用户
       - 如果老用户已绑定其他微信，则拒绝
    """
    # 校验验证码
    ok = verify_sms_code(db, mobile, "bind_mobile", code)

    if not ok:
        raise ValueError("验证码错误或已过期")

    # 查当前微信登录产生的用户
    current_user = db.query(User).filter(User.id == bind_user_id).first()

    if not current_user:
        raise ValueError("绑定用户不存在")

    # 当前用户如果已经有手机号，就不应该再绑
    if current_user.mobile:
        raise ValueError("当前微信账号已绑定手机号")

    # 查询当前微信身份
    current_identity = (
        db.query(UserAuthIdentity)
        .filter(
            UserAuthIdentity.user_id == current_user.id,
            UserAuthIdentity.identity_type == "wechat_open",
        )
        .first()
    )

    if not current_identity:
        raise ValueError("当前微信身份不存在")

    # 查询手机号是否已有用户
    existing_mobile_user = get_user_by_mobile(db, mobile)

    # 情况 1：手机号没有用户，直接绑定当前用户
    if not existing_mobile_user:
        current_user.mobile = mobile
        current_user.mobile_verified = True
        current_user.is_temp_wechat_user = False

        db.commit()
        db.refresh(current_user)

        access_token = create_access_token({"sub": str(current_user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": current_user.id,
            "mobile": current_user.mobile,
        }

    # 情况 2：手机号有老用户，但老用户被禁用
    if existing_mobile_user.status != "active":
        raise ValueError("该手机号对应账号已被禁用")

    # 如果手机号对应的就是当前用户，理论上不应该发生，但做兼容
    if existing_mobile_user.id == current_user.id:
        current_user.mobile_verified = True
        current_user.is_temp_wechat_user = False
        db.commit()

        access_token = create_access_token({"sub": str(current_user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user_id": current_user.id,
            "mobile": current_user.mobile,
        }

    # 查询老用户是否已经绑定别的微信
    existing_wechat_identity = (
        db.query(UserAuthIdentity)
        .filter(
            UserAuthIdentity.user_id == existing_mobile_user.id,
            UserAuthIdentity.identity_type == "wechat_open",
        )
        .first()
    )

    # 如果老用户已绑定其他微信，则拒绝合并
    if existing_wechat_identity:
        # 如果是同一个 openid，也可以视为已绑定完成
        if existing_wechat_identity.identity_key != current_identity.identity_key:
            raise ValueError("该手机号已绑定其他微信账号，无法再次绑定")

    # 把当前微信身份迁移到老用户
    current_identity.user_id = existing_mobile_user.id

    # 老用户补充手机号验证状态
    existing_mobile_user.mobile_verified = True

    # 如果老用户没有昵称头像，可继承临时用户资料
    if not existing_mobile_user.nickname and current_user.nickname:
        existing_mobile_user.nickname = current_user.nickname

    if not existing_mobile_user.avatar_url and current_user.avatar_url:
        existing_mobile_user.avatar_url = current_user.avatar_url

    # 将临时用户禁用或标记废弃
    # 这里采用“禁用临时号”的方式，避免物理删除影响审计
    current_user.status = "disabled"
    current_user.is_temp_wechat_user = True

    db.commit()
    db.refresh(existing_mobile_user)

    # 给老用户签发正式 token
    access_token = create_access_token({"sub": str(existing_mobile_user.id)})

    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user_id": existing_mobile_user.id,
        "mobile": existing_mobile_user.mobile,
    }


def set_password_for_user(db: Session, user_id: int, new_password: str) -> None:
    """
    为当前用户设置密码
    """
    # 查询用户
    user = db.query(User).filter(User.id == user_id).first()

    if not user:
        raise ValueError("用户不存在")

    # 用户状态校验
    if user.status != "active":
        raise ValueError("用户已被禁用")

    # 密码哈希后保存
    user.password_hash = hash_password(new_password)

    db.commit()


def get_user_profile(db: Session, user_id: int) -> User | None:
    """
    获取用户信息
    """
    return db.query(User).filter(User.id == user_id).first()
