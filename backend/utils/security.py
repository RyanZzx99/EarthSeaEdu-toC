"""
安全工具模块

主要职责：
1. 用户密码哈希与校验（bcrypt）
2. 短信验证码哈希与校验（SHA256）
3. JWT access token 生成
4. JWT bind token 生成
5. JWT token 解码

重要说明：
1. 用户密码继续使用 bcrypt
2. 短信验证码不要再使用 bcrypt
3. 原因：
   - bcrypt 有 72 bytes 限制
   - 验证码本身是短期值，不需要 bcrypt 这种慢哈希
   - 改成 SHA256 更适合验证码场景
"""

# 导入时间处理工具
from datetime import datetime
from datetime import timedelta
from datetime import timezone

# 导入 hashlib，用于短信验证码 SHA256 哈希
import hashlib
import string

# 导入 JWT 相关异常和编码工具
from jose import JWTError
from jose import jwt

# 导入 pyca/bcrypt（直接做密码哈希与校验）
import bcrypt

# 导入项目配置
from backend.config.db_conf import settings


# bcrypt 明文密码最大只支持 72 bytes（按 UTF-8 计算）
BCRYPT_PASSWORD_MAX_BYTES = 72
PASSWORD_MIN_LENGTH = 8
PASSWORD_MAX_LENGTH = 24
PASSWORD_SPECIAL_CHARS = string.punctuation


def get_utf8_byte_length(value: str) -> int:
    """
    计算字符串按 UTF-8 编码后的字节长度
    """
    return len(value.encode("utf-8"))


def ensure_bcrypt_password_length(password: str) -> None:
    """
    校验密码是否满足 bcrypt 的 72 bytes 限制
    """
    if get_utf8_byte_length(password) > BCRYPT_PASSWORD_MAX_BYTES:
        raise ValueError("密码长度不能超过 72 字节（英文约 72 位，中文约 24 位）")


def validate_password_strength(password: str) -> None:
    """
    校验密码是否符合当前项目规则

    当前规则：
    1. 长度 8-24 位
    2. 不能包含空格或其他空白字符
    3. 至少包含字母、数字、特殊字符中的 2 种
    4. 必须满足 bcrypt 的 72 bytes 限制
    """
    # 中文注释：先校验 bcrypt 的 72 字节限制，避免后续哈希时报错
    ensure_bcrypt_password_length(password)

    # 中文注释：按字符个数校验前端选定的 8-24 位规则
    if len(password) < PASSWORD_MIN_LENGTH or len(password) > PASSWORD_MAX_LENGTH:
        raise ValueError("密码长度需为 8-24 位")

    # 中文注释：禁止任何空白字符，避免输入看起来相同但实际不同的密码
    if any(char.isspace() for char in password):
        raise ValueError("密码不能包含空格或其他空白字符")

    # 中文注释：统计密码命中的字符类型，至少满足两类
    has_letter = any(char.isalpha() for char in password)
    has_digit = any(char.isdigit() for char in password)
    has_special = any(char in PASSWORD_SPECIAL_CHARS for char in password)
    matched_types = sum([has_letter, has_digit, has_special])

    if matched_types < 2:
        raise ValueError("密码至少需包含字母、数字、特殊字符中的 2 种")


def hash_password(password: str) -> str:
    """
    对用户密码进行哈希
    """
    ensure_bcrypt_password_length(password)
    password_bytes = password.encode("utf-8")
    hashed_bytes = bcrypt.hashpw(password_bytes, bcrypt.gensalt())
    return hashed_bytes.decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    校验用户输入密码是否与数据库中的密码哈希匹配
    """
    ensure_bcrypt_password_length(plain_password)
    try:
        return bcrypt.checkpw(
            plain_password.encode("utf-8"),
            hashed_password.encode("utf-8"),
        )
    except ValueError:
        return False


def hash_code(code: str) -> str:
    """
    对短信验证码进行 SHA256 哈希

    说明：
    1. 验证码不再使用 bcrypt
    2. 统一先转成字符串，再做哈希，避免传入非字符串对象
    """
    # 统一转字符串，防止传入 int / 其他对象
    code_str = str(code)

    # 计算 SHA256 哈希并返回十六进制字符串
    return hashlib.sha256(code_str.encode("utf-8")).hexdigest()


def verify_code(plain_code: str, hashed_code: str) -> bool:
    """
    校验短信验证码是否匹配
    """
    # 对用户输入验证码做同样的 SHA256 计算
    return hash_code(str(plain_code)) == hashed_code


def create_access_token(data: dict) -> str:
    """
    创建正式登录 access token
    """
    to_encode = data.copy()

    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_access_token_expire_minutes
    )

    to_encode.update(
        {
            "exp": expire,
            "token_use": "access",
        }
    )

    return jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def create_bind_token(data: dict) -> str:
    """
    创建绑定手机号专用 bind token
    """
    to_encode = data.copy()

    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_bind_token_expire_minutes
    )

    to_encode.update(
        {
            "exp": expire,
            "token_use": "bind_mobile",
        }
    )

    return jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def create_wechat_register_token(data: dict) -> str:
    """
    创建微信扫码后补填邀请码专用 token
    """
    to_encode = data.copy()

    expire = datetime.now(timezone.utc) + timedelta(
        minutes=settings.jwt_bind_token_expire_minutes
    )

    to_encode.update(
        {
            "exp": expire,
            "token_use": "wechat_register",
        }
    )

    return jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def decode_token(token: str) -> dict:
    """
    标准解码 JWT token
    """
    return jwt.decode(
        token,
        settings.jwt_secret_key,
        algorithms=[settings.jwt_algorithm],
    )


def decode_token_safe(token: str) -> dict | None:
    """
    安全解码 JWT token
    """
    try:
        return decode_token(token)
    except JWTError:
        return None
