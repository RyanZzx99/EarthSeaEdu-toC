# 导入 datetime
from datetime import datetime
from datetime import timedelta
from datetime import timezone

# 导入 JWT
from jose import JWTError
from jose import jwt

# 导入密码哈希上下文
from passlib.context import CryptContext

# 导入项目配置
from backend.config.db_conf import settings


# 创建密码上下文对象
pwd_context = CryptContext(
    schemes=["bcrypt"],      # 使用 bcrypt 算法
    deprecated="auto",       # 自动处理旧算法
)


def hash_password(password: str) -> str:
    """
    对密码进行哈希
    """
    return pwd_context.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    校验明文密码和哈希密码是否匹配
    """
    return pwd_context.verify(plain_password, hashed_password)


def hash_code(code: str) -> str:
    """
    对短信验证码做哈希
    复用 bcrypt，避免明文落库
    """
    return pwd_context.hash(code)


def verify_code(plain_code: str, hashed_code: str) -> bool:
    """
    校验短信验证码
    """
    return pwd_context.verify(plain_code, hashed_code)


def create_access_token(data: dict) -> str:
    """
    创建正式登录 token
    """
    # 复制原始数据，避免直接修改外部传入对象
    to_encode = data.copy()

    # 计算过期时间
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_access_token_expire_minutes
    )

    # 写入过期时间和 token 类型
    to_encode.update(
        {
            "exp": expire,
            "token_use": "access",
        }
    )

    # 生成 JWT
    return jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def create_bind_token(data: dict) -> str:
    """
    创建绑定手机号专用 token
    """
    # 复制数据
    to_encode = data.copy()

    # 计算 bind token 过期时间
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_bind_token_expire_minutes
    )

    # 写入过期时间和 token 类型
    to_encode.update(
        {
            "exp": expire,
            "token_use": "bind_mobile",
        }
    )

    # 生成 JWT
    return jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def decode_token(token: str) -> dict:
    """
    解码 JWT token
    """
    # 直接解码
    return jwt.decode(
        token,
        settings.jwt_secret_key,
        algorithms=[settings.jwt_algorithm],
    )


def decode_token_safe(token: str) -> dict | None:
    """
    安全解码 token
    解码失败时返回 None
    """
    try:
        # 调用标准解码函数
        return decode_token(token)
    except JWTError:
        # 失败返回 None
        return None
