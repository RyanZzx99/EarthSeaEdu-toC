"""
Redis 客户端封装

说明：
1. 统一创建和管理项目内的 Redis 连接
2. 供验证码、防重放、限流等能力复用
"""

# 导入 Redis 客户端类型
from redis import Redis

# 导入项目配置
from backend.config.db_conf import settings


# 中文注释：创建全局 Redis 客户端，默认使用 redis-py 内置连接池
redis_client = Redis(
    host=settings.redis_host,
    port=settings.redis_port,
    password=settings.redis_password or None,
    db=settings.redis_db,
    decode_responses=settings.redis_decode_responses,
)


def get_redis_client() -> Redis:
    """
    获取全局 Redis 客户端
    """
    return redis_client
