# 导入 FastAPI
from fastapi import FastAPI

# 导入 CORS 中间件
from fastapi.middleware.cors import CORSMiddleware

# 导入配置
from backend.config.db_conf import settings

# 导入数据库 Base 和引擎
from backend.config.db_conf import Base
from backend.config.db_conf import engine

# 导入模型，确保建表时加载所有表
import backend.models  # noqa: F401

# 导入路由
from backend.routers import auth
from backend.routers import health


# 创建 FastAPI 应用
app = FastAPI(
    title=settings.app_name,
    debug=settings.app_debug,
)

# 注册跨域中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.backend_cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(health.router, prefix="/api/v1", tags=["health"])
app.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])

@app.on_event("startup")
def on_startup():
    """
    应用启动时自动建表

    TODO:
    正式生产环境建议改为 Alembic 迁移
    当前为了让你快速跑通，先使用 create_all
    """
    Base.metadata.create_all(bind=engine)

@app.get("/")
def root():
    """
    根路由
    """
    return {"message": f"{settings.app_name} is running"}
