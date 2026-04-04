"""系统健康检查相关路由。"""

from fastapi import APIRouter
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from backend.config.db_conf import engine
from backend.schemas.health import DatabaseHealthResponse, HealthResponse


router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    """返回应用层健康状态，不依赖数据库。"""
    return HealthResponse(status="ok")


@router.get("/db-health", response_model=DatabaseHealthResponse)
def db_health_check() -> DatabaseHealthResponse:
    """执行简单 SQL，确认数据库连接可用。"""
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        return DatabaseHealthResponse(status="ok", database="connected")
    except SQLAlchemyError as exc:
        # 将异常类型返回给调用方，便于快速定位连接失败原因。
        return DatabaseHealthResponse(
            status="error",
            database=exc.__class__.__name__,
        )
