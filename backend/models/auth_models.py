# 导入 datetime 用于时间字段
from datetime import datetime

# 导入 SQLAlchemy 字段类型
from sqlalchemy import Boolean
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import String
from sqlalchemy import Text

# 导入 SQLAlchemy 列定义
from sqlalchemy import Column

# 导入关系定义
from sqlalchemy.orm import relationship

# 导入 Base
from backend.config.db_conf import  Base


class User(Base):
    """
    用户主表

    说明：
    1 系统内最终只认 user_id
    2 手机号、微信等都只是登录方式
    3 多种登录方式最后都归属于同一个用户
    """

    # 表名
    __tablename__ = "users"

    # 主键
    id = Column(Integer, primary_key=True, index=True)

    # 手机号
    # 这里就是“手机号 + 密码登录”里的账号字段
    mobile = Column(String(20), unique=True, index=True, nullable=True)

    # 是否已验证手机号
    mobile_verified = Column(Boolean, default=False, nullable=False)

    # 密码哈希值
    password_hash = Column(String(255), nullable=True)

    # 用户昵称
    nickname = Column(String(100), nullable=True)

    # 头像地址
    avatar_url = Column(String(500), nullable=True)

    # 用户状态
    # active=正常
    # disabled=禁用
    status = Column(String(20), default="active", nullable=False)

    # 是否是微信首次扫码产生的临时账号
    is_temp_wechat_user = Column(Boolean, default=False, nullable=False)

    # 创建时间
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # 更新时间
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    # 关联到登录身份表
    auth_identities = relationship("UserAuthIdentity", back_populates="user")


class UserAuthIdentity(Base):
    """
    用户登录身份表

    说明：
    1 一个用户可以有多个登录身份
    2 例如手机号、微信 openid、unionid 等
    3 当前项目先重点支持微信
    """

    # 表名
    __tablename__ = "user_auth_identities"

    # 主键
    id = Column(Integer, primary_key=True, index=True)

    # 用户 ID
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)

    # 登录类型
    # wechat_open = 微信网站应用登录
    identity_type = Column(String(50), nullable=False, index=True)

    # 唯一身份标识
    # 这里主要存 openid
    identity_key = Column(String(255), nullable=False, unique=True, index=True)

    # 扩展标识
    # 这里可存 unionid，没有也可以为空
    identity_extra = Column(String(255), nullable=True)

    # 创建时间
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # 反向关联 user
    user = relationship("User", back_populates="auth_identities")


class SmsCode(Base):
    """
    短信验证码表

    说明：
    1 不存明文验证码，只存哈希
    2 区分业务场景，例如 login / bind_mobile
    3 支持已使用标记，防止重复使用
    """

    # 表名
    __tablename__ = "sms_codes"

    # 主键
    id = Column(Integer, primary_key=True, index=True)

    # 手机号
    mobile = Column(String(20), nullable=False, index=True)

    # 业务场景
    # login=登录
    # bind_mobile=绑定手机号
    biz_type = Column(String(50), nullable=False, index=True)

    # 验证码哈希
    code_hash = Column(String(255), nullable=False)

    # 是否已使用
    is_used = Column(Boolean, default=False, nullable=False)

    # 过期时间
    expires_at = Column(DateTime, nullable=False)

    # 创建时间
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class WechatLoginState(Base):
    """
    微信扫码登录 state 表

    说明：
    1 微信扫码登录需要 state 防 CSRF
    2 扫码时生成 state
    3 回调时校验 state 是否存在且未过期
    """

    # 表名
    __tablename__ = "wechat_login_states"

    # 主键
    id = Column(Integer, primary_key=True, index=True)

    # state 值
    state = Column(String(100), nullable=False, unique=True, index=True)

    # 是否已使用
    is_used = Column(Boolean, default=False, nullable=False)

    # 过期时间
    expires_at = Column(DateTime, nullable=False)

    # 创建时间
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class UserLoginLog(Base):
    """
    用户登录日志表

    说明：
    1 记录登录行为
    2 便于后续安全审计和风控扩展
    """

    # 表名
    __tablename__ = "user_login_logs"

    # 主键
    id = Column(Integer, primary_key=True, index=True)

    # 用户 ID
    user_id = Column(Integer, nullable=True, index=True)

    # 登录方式
    login_type = Column(String(50), nullable=False, index=True)

    # 登录标识，例如手机号/openid
    login_identifier = Column(String(255), nullable=True)

    # 是否成功
    success = Column(Boolean, default=True, nullable=False)

    # 失败原因
    failure_reason = Column(Text, nullable=True)

    # IP 地址
    ip = Column(String(64), nullable=True)

    # 用户代理
    user_agent = Column(Text, nullable=True)

    # 创建时间
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
