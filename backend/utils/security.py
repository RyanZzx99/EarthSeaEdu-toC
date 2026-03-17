"""
安全工具模块

主要职责：
1. 密码哈希
2. 密码校验
3. 短信验证码哈希
4. 短信验证码校验
5. JWT access token 生成
6. JWT bind token 生成
7. JWT token 解码

重要说明：
1. 本文件不直接依赖数据库表字段
2. 但它服务于当前登录体系：
   - 手机号 + 密码登录
   - 手机验证码登录
   - 微信扫码登录
   - 微信首次登录后绑定手机号
3. 当前 token 设计分为两类：
   - access token：正式登录 token
   - bind token：微信未绑定手机号时的短期绑定 token
"""

# 导入时间处理工具
from datetime import datetime
from datetime import timedelta
from datetime import timezone

# 导入 JWT 相关异常和编码工具
from jose import JWTError
from jose import jwt

# 导入密码哈希上下文
from passlib.context import CryptContext

# 导入项目配置
from backend.config.db_conf import settings


# 创建密码上下文对象
# 说明：
# 1. 当前统一使用 bcrypt
# 2. deprecated="auto" 表示未来如果切换哈希策略，可自动兼容旧值
pwd_context = CryptContext(
    schemes=["bcrypt"],
    deprecated="auto",
)


def hash_password(password: str) -> str:
    """
    对用户密码进行哈希

    参数：
        password: 用户输入的原始明文密码

    返回：
        哈希后的密码字符串

    说明：
    1. 数据库绝不能存明文密码
    2. 每次哈希结果都可能不同，这是正常的，因为 bcrypt 会自动加盐
    """
    return pwd_context.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    校验用户输入密码是否与数据库中的哈希密码匹配

    参数：
        plain_password: 用户输入的明文密码
        hashed_password: 数据库中保存的密码哈希

    返回：
        True = 匹配成功
        False = 匹配失败
    """
    return pwd_context.verify(plain_password, hashed_password)


def hash_code(code: str) -> str:
    """
    对短信验证码做哈希

    参数：
        code: 短信验证码明文

    返回：
        哈希后的验证码字符串

    说明：
    1. 验证码虽然是短期值，也不建议明文入库
    2. 这里复用 bcrypt 做哈希
    """
    return pwd_context.hash(code)


def verify_code(plain_code: str, hashed_code: str) -> bool:
    """
    校验短信验证码是否匹配

    参数：
        plain_code: 用户输入的验证码明文
        hashed_code: 数据库里存储的验证码哈希

    返回：
        True = 匹配成功
        False = 匹配失败
    """
    return pwd_context.verify(plain_code, hashed_code)


def create_access_token(data: dict) -> str:
    """
    创建正式登录 access token

    参数：
        data: 要写入 token 的业务数据
              当前通常至少会包含：
              {
                  "sub": "用户ID字符串"
              }

    返回：
        JWT 字符串

    说明：
    1. access token 用于正式登录后的接口访问
    2. 当前 token 中会额外写入：
       - exp：过期时间
       - token_use：标记 token 用途为 access
    3. routers/auth.py 中会校验 token_use == "access"
    """
    # 复制一份数据，避免修改调用方传入的原始 dict
    to_encode = data.copy()

    # 计算过期时间
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_access_token_expire_minutes
    )

    # 把过期时间和 token 用途写入 payload
    to_encode.update(
        {
            "exp": expire,
            "token_use": "access",
        }
    )

    # 使用项目配置中的密钥和算法进行 JWT 编码
    encoded_jwt = jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )

    # 返回生成好的 JWT
    return encoded_jwt


def create_bind_token(data: dict) -> str:
    """
    创建绑定手机号专用 bind token

    参数：
        data: 要写入 token 的业务数据
              当前通常会包含：
              {
                  "sub": "用户ID字符串",
                  "openid": "微信openid"
              }

    返回：
        JWT 字符串

    说明：
    1. bind token 只在微信登录成功但未绑定手机号时使用
    2. 它的有效期应比 access token 短很多
    3. 当前 token 中会额外写入：
       - exp：过期时间
       - token_use：标记 token 用途为 bind_mobile
    4. routers/auth.py 中会校验 token_use == "bind_mobile"
    """
    # 复制 payload 数据
    to_encode = data.copy()

    # 计算 bind token 的过期时间
    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_bind_token_expire_minutes
    )

    # 写入过期时间和 token 用途
    to_encode.update(
        {
            "exp": expire,
            "token_use": "bind_mobile",
        }
    )

    # 编码 JWT
    encoded_jwt = jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )

    # 返回 JWT
    return encoded_jwt


def decode_token(token: str) -> dict:
    """
    标准解码 JWT token

    参数：
        token: JWT 字符串

    返回：
        解码后的 payload 字典

    异常：
        如果 token 非法、过期、签名不匹配，会抛异常

    说明：
    1. 适合在你确定要抛异常时使用
    2. 如果你想安全返回 None，请使用 decode_token_safe
    """
    return jwt.decode(
        token,
        settings.jwt_secret_key,
        algorithms=[settings.jwt_algorithm],
    )


def decode_token_safe(token: str) -> dict | None:
    """
    安全解码 JWT token

    参数：
        token: JWT 字符串

    返回：
        解码成功返回 payload 字典
        解码失败返回 None

    说明：
    1. 适合 router 层直接使用
    2. 避免把底层 JWT 异常直接暴露出去
    """
    try:
        # 调用标准解码函数
        return decode_token(token)
    except JWTError:
        # 任何 JWT 相关异常统一返回 None
        return None