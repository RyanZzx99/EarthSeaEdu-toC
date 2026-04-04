"""健康检查接口的响应模型定义。"""

from pydantic import BaseModel


class HealthResponse(BaseModel):
    """应用健康检查响应。"""

    status: str


class DatabaseHealthResponse(BaseModel):
    """数据库健康检查响应。"""

    status: str
    database: str
