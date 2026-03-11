"""应用配置与数据库连接初始化。"""

from functools import lru_cache
from pathlib import Path
from typing import Annotated, Any

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict
import paramiko
from sqlalchemy import create_engine
from sqlalchemy.engine import URL
from sqlalchemy.orm import sessionmaker
from sshtunnel import SSHTunnelForwarder


BASE_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = BASE_DIR / ".env"

if not hasattr(paramiko, "DSSKey"):
    paramiko.DSSKey = paramiko.RSAKey


class Settings(BaseSettings):
    """集中管理应用运行参数与数据库配置。"""

    app_name: str = "EarthSeaEdu API"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    app_debug: bool = True

    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_database: str = "earthseaedu_dev"
    mysql_user: str = "earthseaedu_dev_user"
    mysql_password: str = "DihaijiaoyuDev12!"
    mysql_charset: str = "utf8mb4"

    ssh_enabled: bool = False
    ssh_host: str = ""
    ssh_port: int = 22
    ssh_user: str = ""
    ssh_password: str = ""
    ssh_remote_bind_host: str = "127.0.0.1"
    ssh_remote_bind_port: int = 3306
    ssh_local_bind_host: str = "127.0.0.1"
    ssh_local_bind_port: int = 0

    backend_cors_origins: Annotated[list[str], NoDecode] = Field(
        default_factory=lambda: [
            "http://localhost:5173",
            "http://127.0.0.1:5173",
        ]
    )

    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @field_validator("backend_cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: str | list[str]) -> list[str]:
        """兼容逗号分隔字符串和列表两种 CORS 配置写法。"""
        if isinstance(value, str):
            return [item.strip() for item in value.split(",") if item.strip()]
        return value

    @property
    def sqlalchemy_database_uri(self) -> URL:
        """拼装 SQLAlchemy 使用的 MySQL 连接参数。"""
        return URL.create(
            drivername="mysql+pymysql",
            username=self.mysql_user,
            password=self.mysql_password,
            host=self.database_host,
            port=self.database_port,
            database=self.mysql_database,
            query={"charset": self.mysql_charset},
        )

    @property
    def database_host(self) -> str:
        """返回 SQLAlchemy 应连接的数据库主机。"""
        if self.ssh_enabled:
            return self.ssh_local_bind_host
        return self.mysql_host

    @property
    def database_port(self) -> int:
        """返回 SQLAlchemy 应连接的数据库端口。"""
        if self.ssh_enabled:
            return self.ssh_local_bind_port
        return self.mysql_port


@lru_cache
def get_settings() -> Settings:
    """缓存配置实例，避免重复读取环境变量。"""
    return Settings()


settings = get_settings()

tunnel: SSHTunnelForwarder | None = None


def _build_ssh_tunnel() -> SSHTunnelForwarder:
    """创建并启动 SSH 隧道，把远程 MySQL 映射到本地端口。"""
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
    if settings.ssh_password:
        ssh_kwargs["ssh_password"] = settings.ssh_password

    ssh_tunnel = SSHTunnelForwarder(**ssh_kwargs)
    ssh_tunnel.start()
    return ssh_tunnel


if settings.ssh_enabled:
    tunnel = _build_ssh_tunnel()
    settings.ssh_local_bind_port = tunnel.local_bind_port


engine = create_engine(
    settings.sqlalchemy_database_uri,
    pool_pre_ping=True,
    future=True,
)

SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine,
)
