"""
登录认证相关 ORM 模型定义

重要说明：
1. 本文件严格按照你当前真实执行的 MySQL 表结构编写
2. 字段命名已完全对齐你当前数据库，不再使用之前建议版字段名
3. 当前统一使用：
   - create_time / update_time
   - expires_time
   - delete_flag
4. delete_flag 含义：
   - '0' = 停用
   - '1' = 使用中
5. 后续所有查询默认都应该只查 delete_flag='1' 的有效数据
6. 本文件已改为 SQLAlchemy 2.0 的 Mapped / mapped_column 写法，
   这样可以更好地配合 IDE / 类型检查器，减少：
   “应为类型 int，但实际为 InstrumentedAttribute”
   这类提示问题
7. 你要注意区分：
   - User.id      -> 类属性，主要用于 ORM 查询表达式
   - user.id      -> 实例属性，真实值类型才是 int
"""

# 导入 datetime，用于时间字段类型定义和默认时间
from datetime import datetime

# 导入 SQLAlchemy 字段类型
from sqlalchemy import BigInteger
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import String
from sqlalchemy import Text

# 导入 SQLAlchemy 2.0 推荐的 ORM 类型标注方式
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

# 导入数据库 Base
from backend.config.db_conf import Base


class User(Base):
    """
    用户主表，对应 MySQL 表：users

    设计说明：
    1. 系统最终识别用户使用的是 id
    2. 手机号、微信身份都只是“登录方式”
    3. 多种登录方式最终都会归属于同一个用户
    4. 微信首次扫码时，可能会先创建一个临时用户（无手机号）
    5. 当前模型字段已严格对齐你真实数据库字段：
       - create_time
       - update_time
       - delete_flag
    """

    # 表名，必须与数据库真实表名一致
    __tablename__ = "users"

    # 主键 ID
    # 说明：
    # 1. 数据库中是 BIGINT UNSIGNED AUTO_INCREMENT
    # 2. Python 侧一般按 int 使用即可
    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID，用户唯一标识",
    )

    # 手机号
    # 说明：
    # 1. 允许为空，因为微信首次登录时可能还未绑定手机号
    # 2. 当前你的业务里，“手机号 + 密码登录”的账号字段就是它
    mobile: Mapped[str | None] = mapped_column(
        String(20),
        unique=True,
        index=True,
        nullable=True,
        comment="手机号，支持手机号+密码登录；允许为空，因为微信首次登录时可能还未绑定手机号",
    )

    # 手机号是否已验证
    # 说明：
    # 1. 数据库实际类型是 TINYINT(1)
    # 2. ORM 这里按 int 使用最稳妥
    # 3. 取值语义：
    #    0 = 未验证
    #    1 = 已验证
    mobile_verified: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="手机号是否已验证：0=否，1=是",
    )

    # 密码哈希
    # 说明：
    # 1. 数据库存储的是加密后的哈希值
    # 2. 绝对不能存明文密码
    # 3. 如果用户还没设置密码，这里允许为空
    password_hash: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="密码哈希值，不存明文密码；未设置密码时可为空",
    )

    # 用户昵称
    # 说明：
    # 1. 可来源于微信昵称
    # 2. 也可后续由用户自己修改
    nickname: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="用户昵称，可来源于微信昵称，也可后续用户自行修改",
    )

    # 用户头像地址
    # 说明：
    # 1. 可来源于微信头像
    # 2. 也可以后续用户自己修改
    avatar_url: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="用户头像地址，可来源于微信头像",
    )

    # 用户状态
    # 说明：
    # 1. 这是业务状态，不等同于 delete_flag
    # 2. 常见取值：
    #    active   = 正常可登录
    #    disabled = 被禁用
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        comment="用户状态：active=正常，disabled=禁用",
    )

    # 是否为微信首次扫码产生的临时账号
    # 说明：
    # 1. 微信首次登录但还没绑定手机号时，会先创建临时账号
    # 2. 绑定手机号后应转为正式账号
    # 3. 取值语义：
    #    0 = 否
    #    1 = 是
    is_temp_wechat_user: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="是否为微信首次扫码产生的临时账号：0=否，1=是",
    )

    # 创建时间
    # 说明：
    # 1. 对齐数据库字段 create_time
    # 2. 以后代码里不要再写 created_at
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )

    # 更新时间
    # 说明：
    # 1. 对齐数据库字段 update_time
    # 2. 以后代码里不要再写 updated_at
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )

    # 删除标志
    # 说明：
    # 1. 这是逻辑删除标志
    # 2. 与 status 不同，delete_flag 更偏向“记录是否有效”
    # 3. 当前约定：
    #    '0' = 停用
    #    '1' = 使用中
    # 4. 后续查询默认都应带：
    #    User.delete_flag == "1"
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标志（0-停用，1-使用中）",
    )

    # 关联登录身份表
    # 说明：
    # 1. 一个用户可以有多个第三方登录身份
    # 2. 当前主要是微信 openid 绑定
    auth_identities: Mapped[list["UserAuthIdentity"]] = relationship(
        back_populates="user",
        lazy="select",
    )


class UserAuthIdentity(Base):
    """
    用户登录身份绑定表，对应 MySQL 表：user_auth_identities

    设计说明：
    1. 一个用户可以对应多个身份
    2. 当前主要用于微信 PC 网站扫码登录
    3. 后续可以扩展 QQ / Apple / 邮箱 等第三方身份
    4. 当前模型字段已严格对齐你真实数据库字段：
       - create_time
       - update_time
       - delete_flag
    """

    # 表名
    __tablename__ = "user_auth_identities"

    # 主键 ID
    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )

    # 关联用户 ID
    # 说明：
    # 1. 对应 users.id
    # 2. 这是多种身份归属于同一个用户的关键关联字段
    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        index=True,
        comment="关联 users.id",
    )

    # 身份类型
    # 说明：
    # 1. 当前主要用 wechat_open
    # 2. 后续可以扩展更多身份来源
    identity_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        index=True,
        comment="身份类型，例如：wechat_open",
    )

    # 身份唯一标识
    # 说明：
    # 1. 当前主要存微信 openid
    # 2. 数据库中对 identity_key 做了唯一约束
    identity_key: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        unique=True,
        index=True,
        comment="身份唯一标识，当前主要存微信 openid",
    )

    # 扩展身份信息
    # 说明：
    # 1. 当前可存 unionid
    # 2. 如果微信未返回 unionid，可以为空
    identity_extra: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="扩展身份信息，当前可存微信 unionid；没有可为空",
    )

    # 创建时间
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )

    # 更新时间
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )

    # 删除标志
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标志（0-停用，1-使用中）",
    )

    # 反向关联用户
    # 说明：
    # 1. 通过 identity.user 可回到对应用户对象
    user: Mapped["User"] = relationship(
        back_populates="auth_identities",
        lazy="select",
    )


class SmsCode(Base):
    """
    短信验证码表，对应 MySQL 表：sms_codes

    设计说明：
    1. 只存验证码哈希，不存明文验证码
    2. 区分业务场景：
       - login = 登录
       - bind_mobile = 绑定手机号
    3. 通过 is_used + expires_time 控制一次性与有效期
    4. 当前模型字段已严格对齐你真实数据库字段：
       - expires_time
       - create_time
       - update_time
       - delete_flag
    """

    # 表名
    __tablename__ = "sms_codes"

    # 主键 ID
    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )

    # 手机号
    # 说明：
    # 1. 是接收验证码的手机号
    # 2. 用于验证码登录或绑定手机号校验
    mobile: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        index=True,
        comment="接收验证码的手机号",
    )

    # 业务类型
    # 说明：
    # 1. login = 登录
    # 2. bind_mobile = 绑定手机号
    biz_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        index=True,
        comment="业务类型：login=登录，bind_mobile=绑定手机号",
    )

    # 验证码哈希
    # 说明：
    # 1. 不能存验证码明文
    # 2. 校验时用 verify_code 比较
    code_hash: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="验证码哈希值，不存明文",
    )

    # 是否已使用
    # 说明：
    # 1. 0 = 未使用
    # 2. 1 = 已使用
    # 3. 验证码验证成功后应立即改成 1
    is_used: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="是否已使用：0=否，1=是",
    )

    # 过期时间
    # 说明：
    # 1. 对齐数据库字段 expires_time
    # 2. 后续代码不要再写 expires_at
    expires_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        comment="验证码过期时间",
    )

    # 创建时间
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )

    # 更新时间
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )

    # 删除标志
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标志（0-停用，1-使用中）",
    )


class WechatLoginState(Base):
    """
    微信扫码登录 state 表，对应 MySQL 表：wechat_login_states

    设计说明：
    1. 用于防止 CSRF
    2. 前端获取微信扫码地址时，后端生成 state 并入库
    3. 微信回调时校验 state：
       - 是否存在
       - 是否未过期
       - 是否未使用
    4. 当前模型字段已严格对齐你真实数据库字段：
       - expires_time
       - create_time
       - update_time
       - delete_flag
    """

    # 表名
    __tablename__ = "wechat_login_states"

    # 主键 ID
    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )

    # 微信登录 state
    # 说明：
    # 1. 由后端生成随机字符串
    # 2. 数据库中唯一
    # 3. 微信回调时必须原样带回
    state: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        unique=True,
        index=True,
        comment="微信登录 state 值，随机字符串",
    )

    # 是否已使用
    # 说明：
    # 1. 0 = 未使用
    # 2. 1 = 已使用
    # 3. state 一旦成功校验，应立刻标记为已使用，防止重放
    is_used: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="是否已使用：0=否，1=是",
    )

    # 过期时间
    # 说明：
    # 1. 对齐数据库字段 expires_time
    # 2. 后续代码不要再写 expires_at
    expires_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        comment="state 过期时间",
    )

    # 创建时间
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )

    # 更新时间
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )

    # 删除标志
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标志（0-停用，1-使用中）",
    )


class UserLoginLog(Base):
    """
    用户登录日志表，对应 MySQL 表：user_login_logs

    设计说明：
    1. 记录密码登录、短信登录、微信登录、绑定手机号等行为
    2. 用于后续安全审计、风控、问题排查
    3. 登录失败时 user_id 可以为空
    4. 当前模型字段已严格对齐你真实数据库字段：
       - create_time
       - update_time
       - delete_flag
    """

    # 表名
    __tablename__ = "user_login_logs"

    # 主键 ID
    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )

    # 用户 ID
    # 说明：
    # 1. 登录成功时通常有值
    # 2. 登录失败时可能为空
    user_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("users.id", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
        index=True,
        comment="用户ID；登录失败时可能为空",
    )

    # 登录类型
    # 说明：
    # 1. 例如：
    #    password
    #    sms
    #    wechat_open
    #    wechat_bind_mobile
    login_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        index=True,
        comment="登录类型：password / sms / wechat_open / wechat_bind_mobile",
    )

    # 登录标识
    # 说明：
    # 1. 可存手机号
    # 2. 也可以存脱敏 openid
    login_identifier: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="登录标识，如手机号、openid脱敏值等",
    )

    # 是否成功
    # 说明：
    # 1. 0 = 失败
    # 2. 1 = 成功
    success: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="是否成功：0=失败，1=成功",
    )

    # 失败原因
    # 说明：
    # 1. 成功时可为空
    # 2. 失败时建议记录清晰原因，方便排查
    failure_reason: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="失败原因；成功时可为空",
    )

    # IP 地址
    ip: Mapped[str | None] = mapped_column(
        String(64),
        nullable=True,
        comment="客户端IP地址",
    )

    # 用户代理
    user_agent: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
        comment="客户端User-Agent",
    )

    # 创建时间
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )

    # 更新时间
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )

    # 删除标志
    delete_flag: Mapped[str] = mapped_column(
        String(1),
        nullable=False,
        default="1",
        comment="删除标志（0-停用，1-使用中）",
    )