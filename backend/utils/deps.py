"""FastAPI 依赖项工具函数。"""

from collections.abc import Generator

from sqlalchemy.orm import Session

from backend.config.db_conf import SessionLocal


def get_db() -> Generator[Session, None, None]:
    """按请求提供数据库会话，并在结束后自动关闭。"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
