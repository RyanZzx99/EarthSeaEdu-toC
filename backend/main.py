"""FastAPI 应用入口，负责组装中间件与路由。"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.config.db_conf import settings
from backend.routers import health


app = FastAPI(
    title=settings.app_name,
    debug=settings.app_debug,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.backend_cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/api/v1", tags=["health"])


@app.get("/")
def root() -> dict[str, str]:
    """根路径探活接口，用于快速确认服务已启动。"""
    return {"message": f"{settings.app_name} is running"}
