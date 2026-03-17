"""应用配置与数据库连接初始化。"""

# 导入 lru_cache，用于缓存 Settings 实例，避免重复读取 .env
from functools import lru_cache

# 导入 Path，用于稳定定位 backend/.env
from pathlib import Path

# 导入类型注解
from typing import Annotated, Any, List

# 导入 paramiko，用于 SSH 连接兼容处理
import paramiko

# 导入 Pydantic 字段定义与校验器
from pydantic import Field
from pydantic import field_validator

# 导入 pydantic-settings
from pydantic_settings import BaseSettings
from pydantic_settings import NoDecode
from pydantic_settings import SettingsConfigDict

# 导入 SQLAlchemy 数据库连接工具
from sqlalchemy import create_engine
from sqlalchemy.engine import URL
from sqlalchemy.orm import sessionmaker
from sqlalchemy.orm import declarative_base

# 导入 SSH 隧道工具
from sshtunnel import SSHTunnelForwarder


# backend 目录绝对路径（用于稳定定位 .env）
BASE_DIR = Path(__file__).resolve().parents[1]

# backend/.env 文件路径
ENV_FILE = BASE_DIR / ".env"


# 某些 paramiko 版本没有 DSSKey，这里做兼容补丁，避免 sshtunnel 初始化异常
if not hasattr(paramiko, "DSSKey"):
    paramiko.DSSKey = paramiko.RSAKey


class Settings(BaseSettings):
    """
    系统配置类

    说明：
    1. 统一读取 backend/.env
    2. 保留你当前项目已有配置能力
    3. 补齐登录认证系统所需配置
    """

    # =========================================================
    # 一、基础应用配置
    # =========================================================

    # 应用名称（用于 FastAPI 文档标题等）
    app_name: str = "EarthSeaEdu API"

    # 运行环境标识（development / production / test 等）
    app_env: str = "development"

    # 服务监听主机
    app_host: str = "0.0.0.0"

    # 服务监听端口
    app_port: int = 8000

    # 是否开启调试模式
    app_debug: bool = True

    # =========================================================
    # 二、MySQL 数据库配置
    # =========================================================

    # MySQL 主机地址（未启用 SSH 隧道时生效）
    mysql_host: str = "127.0.0.1"

    # MySQL 端口（未启用 SSH 隧道时生效）
    mysql_port: int = 3306

    # MySQL 用户名
    mysql_user: str = "root"

    # MySQL 密码
    mysql_password: str = "123456"

    # 数据库名（旧字段，保留兼容）
    mysql_database: str = "earthseaedu"

    # 数据库名（新字段，保留兼容）
    mysql_db: str = "earthseaedu"

    # 连接字符集
    mysql_charset: str = "utf8mb4"

    # =========================================================
    # 三、SSH 隧道配置（保留原项目能力）
    # =========================================================

    # 是否启用 SSH 隧道连接数据库
    ssh_enabled: bool = False

    # SSH 主机（堡垒机或数据库所在服务器）
    ssh_host: str = ""

    # SSH 端口
    ssh_port: int = 22

    # SSH 用户名
    ssh_user: str = ""

    # SSH 密码
    ssh_password: str = ""

    # SSH 远端数据库地址
    ssh_remote_bind_host: str = "127.0.0.1"
    ssh_remote_bind_port: int = 3306

    # 本地隧道监听地址
    ssh_local_bind_host: str = "127.0.0.1"

    # 本地隧道监听端口，0 表示自动分配
    ssh_local_bind_port: int = 0

    # =========================================================
    # 四、CORS 配置
    # =========================================================

    # 允许跨域来源列表
    # 支持：
    # 1. ["http://localhost:5173"]
    # 2. http://localhost:5173,http://127.0.0.1:5173
    backend_cors_origins: Annotated[List[str], NoDecode] = Field(default=["*"])

    # =========================================================
    # 五、JWT 配置
    # =========================================================

    # JWT 密钥
    # TODO: 上线前必须替换为强随机字符串
    jwt_secret_key: str = "replace_this_with_a_very_strong_secret_key"

    # JWT 算法
    jwt_algorithm: str = "HS256"

    # 正式登录 access token 过期时间（分钟）
    jwt_access_token_expire_minutes: int = 60 * 24 * 7

    # 微信绑定手机号专用 bind token 过期时间（分钟）
    jwt_bind_token_expire_minutes: int = 10

    # =========================================================
    # 六、腾讯云短信配置
    # =========================================================

    # 腾讯云 SecretId
    tencentcloud_secret_id: str = ""

    # 腾讯云 SecretKey
    tencentcloud_secret_key: str = ""

    # 腾讯云短信地域
    tencentcloud_sms_region: str = "ap-guangzhou"

    # 腾讯云短信 SDK AppID
    tencentcloud_sms_sdk_app_id: str = ""

    # 腾讯云短信签名
    tencentcloud_sms_sign_name: str = ""

    # 登录验证码模板 ID
    tencentcloud_sms_template_login: str = ""

    # 绑定手机号验证码模板 ID
    tencentcloud_sms_template_bind_mobile: str = ""

    # 是否启用短信 mock 模式
    # True = 不真实发短信，仅本地联调
    # False = 调用真实腾讯云短信
    tencentcloud_sms_mock: bool = True

    # =========================================================
    # 七、微信开放平台配置（PC 网站扫码登录）
    # =========================================================

    # 微信开放平台 AppID
    # TODO: 改成你自己的真实值
    wechat_open_app_id: str = ""

    # 微信开放平台 AppSecret
    # TODO: 改成你自己的真实值
    wechat_open_app_secret: str = ""

    # 微信扫码成功后回调到后端的地址
    # 例如：
    # http://127.0.0.1:8000/api/v1/auth/wechat/callback
    # TODO: 必须和微信开放平台后台配置完全一致
    wechat_open_redirect_uri: str = ""

    # 后端接住微信回调后，再 302 跳转给前端登录页的地址
    # 由于你当前前端已经走“路由版登录页”，这里建议直接指向 /login
    frontend_login_page_url: str = "http://localhost:5173/login"

    # =========================================================
    # 八、业务配置
    # =========================================================

    # 短信验证码有效期（分钟）
    sms_code_expire_minutes: int = 5

    # 短信发送冷却时间（秒）
    sms_send_cooldown_seconds: int = 60

    # 单手机号每日最大发送次数
    sms_daily_limit: int = 10

    # =========================================================
    # 十、邀请码管理配置
    # =========================================================

    # 邀请码管理接口管理员密钥
    # 说明：
    # 1. 用于保护“生成邀请码/发放邀请码”接口
    # 2. 生产环境必须设置为强随机字符串
    invite_admin_key: str = ""

    # =========================================================
    # 九、.env 文件读取配置
    # =========================================================

    model_config = SettingsConfigDict(
        # 指向 backend/.env
        env_file=ENV_FILE,
        # 以 UTF-8 读取
        env_file_encoding="utf-8",
        # 环境变量名大小写不敏感
        case_sensitive=False,
        # 忽略未声明的额外字段
        extra="ignore",
    )

    @field_validator("backend_cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: str | list[str]) -> list[str]:
        """
        兼容逗号字符串与列表两种 CORS 输入格式
        """
        if isinstance(value, str):
            return [item.strip() for item in value.split(",") if item.strip()]
        return value

    @property
    def resolved_mysql_database(self) -> str:
        """
        统一数据库名

        说明：
        1. 优先使用 mysql_database
        2. 如果为空，则回退 mysql_db
        """
        return self.mysql_database or self.mysql_db

    @property
    def database_host(self) -> str:
        """
        返回 SQLAlchemy 实际连接主机

        说明：
        1. 如果启用了 SSH 隧道，则连接本地隧道地址
        2. 否则直接连接 mysql_host
        """
        if self.ssh_enabled:
            return self.ssh_local_bind_host
        return self.mysql_host

    @property
    def database_port(self) -> int:
        """
        返回 SQLAlchemy 实际连接端口

        说明：
        1. 如果启用了 SSH 隧道，则连接本地隧道端口
        2. 否则直接连接 mysql_port
        """
        if self.ssh_enabled:
            return self.ssh_local_bind_port
        return self.mysql_port

    @property
    def sqlalchemy_database_uri(self) -> str:
        """
        构造 SQLAlchemy 数据库连接字符串

        注意：
        1. 这里使用 URL.create 组装更稳妥
        2. hide_password=False 必须保留，否则会变成 *** 导致连接失败
        """
        return URL.create(
            drivername="mysql+pymysql",
            username=self.mysql_user,
            password=self.mysql_password,
            host=self.database_host,
            port=self.database_port,
            database=self.resolved_mysql_database,
            query={"charset": self.mysql_charset},
        ).render_as_string(hide_password=False)


@lru_cache
def get_settings() -> Settings:
    """
    返回缓存后的 Settings，避免每次导入都重复读取 .env
    """
    return Settings()


# 全局配置对象
settings = get_settings()

# 全局 SSH 隧道对象占位
tunnel: SSHTunnelForwarder | None = None


def _build_ssh_tunnel() -> SSHTunnelForwarder:
    """
    创建并启动 SSH 隧道，将本地端口转发到远端 MySQL
    """
    ssh_kwargs: dict[str, Any] = {
        "ssh_address_or_host": (settings.ssh_host, settings.ssh_port),
        "ssh_username": settings.ssh_user,
        "remote_bind_address": (
            settings.ssh_remote_bind_host,
            settings.ssh_remote_bind_port,
        ),
        "local_bind_address": (
            settings.ssh_local_bind_host,
            settings.ssh_local_bind_port,
        ),
    }

    # 仅在设置了密码时才附加
    if settings.ssh_password:
        ssh_kwargs["ssh_password"] = settings.ssh_password

    # 创建 SSH 隧道对象
    ssh_tunnel = SSHTunnelForwarder(**ssh_kwargs)

    # 启动隧道
    ssh_tunnel.start()

    return ssh_tunnel


# 如果启用了 SSH 隧道，则在模块初始化时建立隧道
if settings.ssh_enabled:
    tunnel = _build_ssh_tunnel()
    settings.ssh_local_bind_port = tunnel.local_bind_port


# 创建全局数据库引擎
engine = create_engine(
    settings.sqlalchemy_database_uri,
    pool_pre_ping=True,   # 取连接前先检测是否有效
    pool_recycle=3600,    # 避免 MySQL 长连接失效
    echo=False,           # 生产环境建议保持 False
)

# 创建 Session 工厂
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)

# 创建 ORM Base
Base = declarative_base()


def get_db():
    """
    FastAPI 依赖注入使用的数据库会话生成器
    """
    # 创建数据库会话
    db = SessionLocal()

    try:
        # 提供给接口层使用
        yield db
    finally:
        # 请求结束后关闭会话
        db.close()
