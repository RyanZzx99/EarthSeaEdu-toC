"""应用配置与数据库连接初始化。"""

from functools import lru_cache
from pathlib import Path
from typing import Annotated, Any, List

import paramiko
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict
from sqlalchemy import create_engine
from sqlalchemy.engine import URL
from sqlalchemy.orm import sessionmaker
from sshtunnel import SSHTunnelForwarder


# backend 目录绝对路径（用于稳定定位 .env）
BASE_DIR = Path(__file__).resolve().parents[1]
# 项目后端环境变量文件路径
ENV_FILE = BASE_DIR / ".env"

# 某些 paramiko 版本没有 DSSKey，这里做兼容补丁，避免 sshtunnel 初始化异常
if not hasattr(paramiko, "DSSKey"):
    paramiko.DSSKey = paramiko.RSAKey


class Settings(BaseSettings):
    # -----------------------------
    # 基础应用配置
    # -----------------------------
    # 应用名称（用于 FastAPI 文档标题等）
    app_name: str = "EarthSeaEdu API"
    # 运行环境标识（例如 development / production）
    app_env: str = "development"
    # 服务监听主机
    app_host: str = "0.0.0.0"
    # 服务监听端口
    app_port: int = 8000
    # 是否开启调试模式
    app_debug: bool = True

    # -----------------------------
    # 数据库配置
    # -----------------------------
    # MySQL 主机地址（未启用 SSH 隧道时生效）
    mysql_host: str = "127.0.0.1"
    # MySQL 端口（未启用 SSH 隧道时生效）
    mysql_port: int = 3306
    # MySQL 用户名
    mysql_user: str = "root"
    # MySQL 密码
    mysql_password: str = "123456"
    # 数据库名（项目旧代码字段，保留）
    mysql_database: str = "earthseaedu"
    # 数据库名（你新增的字段，兼容保留）
    mysql_db: str = "earthseaedu"
    # 连接字符集
    mysql_charset: str = "utf8mb4"

    # -----------------------------
    # SSH 隧道配置（保留原项目能力）
    # -----------------------------
    # 是否启用 SSH 隧道连库
    ssh_enabled: bool = False
    # SSH 主机（堡垒机或数据库机）
    ssh_host: str = ""
    # SSH 端口
    ssh_port: int = 22
    # SSH 用户名
    ssh_user: str = ""
    # SSH 密码
    ssh_password: str = ""
    # SSH 远端可见的数据库地址（通常是远端 127.0.0.1:3306）
    ssh_remote_bind_host: str = "127.0.0.1"
    ssh_remote_bind_port: int = 3306
    # 本地隧道监听地址
    ssh_local_bind_host: str = "127.0.0.1"
    # 本地隧道监听端口，0 表示自动分配
    ssh_local_bind_port: int = 0

    # -----------------------------
    # CORS 配置
    # -----------------------------
    # 允许跨域来源列表。
    # 使用 NoDecode + 自定义校验器，兼容 "a,b,c" 与 ["a","b","c"] 两种写法。
    backend_cors_origins: Annotated[List[str], NoDecode] = Field(default=["*"])

    # -----------------------------
    # JWT 配置
    # -----------------------------
    # JWT 密钥（上线务必替换）
    jwt_secret_key: str = "replace_this_with_a_very_strong_secret_key"
    # JWT 算法
    jwt_algorithm: str = "HS256"
    # access token 过期时间（分钟）
    jwt_access_token_expire_minutes: int = 60 * 24 * 7
    # bind token 过期时间（分钟）
    jwt_bind_token_expire_minutes: int = 10

    # -----------------------------
    # 腾讯云短信配置
    # -----------------------------
    # 腾讯云 SecretId
    tencentcloud_secret_id: str = ""
    # 腾讯云 SecretKey
    tencentcloud_secret_key: str = ""
    # 地域（短信服务地域）
    tencentcloud_sms_region: str = "ap-guangzhou"
    # 短信应用 SDK AppID
    tencentcloud_sms_sdk_app_id: str = ""
    # 短信签名
    tencentcloud_sms_sign_name: str = ""
    # 登录验证码模板 ID
    tencentcloud_sms_template_login: str = ""
    # 绑定手机号验证码模板 ID
    tencentcloud_sms_template_bind_mobile: str = ""
    # 短信 mock 开关（开发阶段建议 True）
    tencentcloud_sms_mock: bool = True

    # -----------------------------
    # 微信开放平台配置（PC 网站扫码登录）
    # -----------------------------
    # 微信开放平台 AppID
    # TODO: 改成你自己的真实值
    wechat_open_app_id: str = ""
    # 微信开放平台 AppSecret
    # TODO: 改成你自己的真实值
    wechat_open_app_secret: str = ""
    # 微信回调地址（必须和平台配置一致）
    # TODO: 必须和微信开放平台后台配置完全一致
    wechat_open_redirect_uri: str = ""
    # 前端登录页地址（扫码回跳可用）
    # TODO: 改成你的前端地址
    frontend_login_page_url: str = "http://localhost:5173"

    # -----------------------------
    # 业务配置
    # -----------------------------
    # 验证码有效期（分钟）
    sms_code_expire_minutes: int = 5
    # 短信发送冷却时间（秒）
    sms_send_cooldown_seconds: int = 60
    # 单手机号每日最大发送次数
    sms_daily_limit: int = 10

    # -----------------------------
    # .env 文件读取配置
    # -----------------------------
    model_config = SettingsConfigDict(
        # 指向 backend/.env
        env_file=ENV_FILE,
        # 以 UTF-8 读取，避免中文或特殊字符乱码
        env_file_encoding="utf-8",
        # 环境变量大小写不敏感
        case_sensitive=False,
        # 忽略未声明的额外字段，避免启动时报错
        extra="ignore",
    )

    @field_validator("backend_cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: str | list[str]) -> list[str]:
        """兼容逗号字符串与列表两种 CORS 输入格式。"""
        if isinstance(value, str):
            return [item.strip() for item in value.split(",") if item.strip()]
        return value

    @property
    def resolved_mysql_database(self) -> str:
        """统一数据库名：优先 mysql_database，空时回退 mysql_db。"""
        return self.mysql_database or self.mysql_db

    @property
    def database_host(self) -> str:
        """返回 SQLAlchemy 实际连接主机。启用 SSH 时走本地隧道地址。"""
        if self.ssh_enabled:
            return self.ssh_local_bind_host
        return self.mysql_host

    @property
    def database_port(self) -> int:
        """返回 SQLAlchemy 实际连接端口。启用 SSH 时走本地隧道端口。"""
        if self.ssh_enabled:
            return self.ssh_local_bind_port
        return self.mysql_port

    @property
    def sqlalchemy_database_uri(self) -> str:
        """
        构造 SQLAlchemy 连接字符串。

        注意：
        1. 这里先用 URL.create 组装，再 render_as_string 输出字符串；
        2. hide_password=False 必须保留，否则会变成 ***，导致鉴权失败。
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
    """返回缓存后的 Settings，避免每次导入都重复读取 .env。"""
    return Settings()


# 全局配置对象：业务代码统一从这里读取配置
settings = get_settings()
# 全局 SSH 隧道对象占位（仅在 ssh_enabled=True 时初始化）
tunnel: SSHTunnelForwarder | None = None


def _build_ssh_tunnel() -> SSHTunnelForwarder:
    """创建并启动 SSH 隧道，将本地端口转发到远端 MySQL。"""
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
    # 仅在设置了密码时附加，避免空密码干扰 SSH 连接
    if settings.ssh_password:
        ssh_kwargs["ssh_password"] = settings.ssh_password

    ssh_tunnel = SSHTunnelForwarder(**ssh_kwargs)
    ssh_tunnel.start()
    return ssh_tunnel


# 启动时若启用 SSH，则先建立隧道，再把随机本地端口回写到 settings
if settings.ssh_enabled:
    tunnel = _build_ssh_tunnel()
    settings.ssh_local_bind_port = tunnel.local_bind_port


# 全局数据库引擎：连接池开启 pre_ping，避免取到失效连接
engine = create_engine(
    settings.sqlalchemy_database_uri,
    pool_pre_ping=True,
    future=True,
)

# Session 工厂：按请求创建 session，配合依赖注入统一关闭
SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)
