# 导入随机字符串生成工具
import secrets

# 导入字符串模块
import string

# 导入 datetime
from datetime import datetime
from datetime import timedelta


def now_utc() -> datetime:
    """
    返回当前 UTC 时间
    """
    return datetime.utcnow()


def add_minutes(minutes: int) -> datetime:
    """
    当前时间加分钟
    """
    return now_utc() + timedelta(minutes=minutes)


def add_seconds(seconds: int) -> datetime:
    """
    当前时间加秒
    """
    return now_utc() + timedelta(seconds=seconds)


def generate_numeric_code(length: int = 6) -> str:
    """
    生成纯数字验证码
    """
    # 构造数字字符集
    digits = string.digits

    # 随机生成指定长度数字验证码
    return "".join(secrets.choice(digits) for _ in range(length))


def generate_state(length: int = 32) -> str:
    """
    生成微信扫码 state
    """
    # 生成 URL 安全随机字符串
    return secrets.token_urlsafe(length)
