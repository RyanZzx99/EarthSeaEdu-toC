"""
通用工具模块

主要职责：
1. 统一获取当前 UTC 时间
2. 时间偏移计算（加分钟、加秒）
3. 生成短信验证码
4. 生成微信扫码 state

重要说明：
1. 本文件不直接依赖数据库表结构
2. 但会被 auth_service.py 直接使用
3. 当前登录系统里，主要服务这些场景：
   - 生成短信验证码
   - 计算验证码过期时间
   - 生成微信扫码 state
   - 计算微信 state 过期时间
"""

# 导入 secrets，用于生成安全随机字符串
import secrets

# 导入 string，用于字符集定义
import string

# 导入 datetime，用于时间处理
from datetime import datetime
from datetime import timedelta


def now_utc() -> datetime:
    """
    获取当前 UTC 时间

    返回：
        当前 UTC 时间的 datetime 对象

    说明：
    1. 后续验证码过期、微信 state 过期等判断，都建议统一基于 UTC 时间
    2. 这样可以减少时区混乱
    """
    return datetime.utcnow()


def add_minutes(minutes: int) -> datetime:
    """
    在当前 UTC 时间基础上增加指定分钟数

    参数：
        minutes: 要增加的分钟数

    返回：
        增加后的 UTC 时间

    使用场景：
    1. 生成短信验证码 expires_time
    2. 生成微信登录 state expires_time
    """
    return now_utc() + timedelta(minutes=minutes)


def add_seconds(seconds: int) -> datetime:
    """
    在当前 UTC 时间基础上增加指定秒数

    参数：
        seconds: 要增加的秒数

    返回：
        增加后的 UTC 时间

    说明：
    1. 当前版本里主要时间控制还是按分钟
    2. 这个函数保留给后续更细粒度的限流/短时状态使用
    """
    return now_utc() + timedelta(seconds=seconds)


def generate_numeric_code(length: int = 6) -> str:
    """
    生成纯数字验证码

    参数：
        length: 验证码长度，默认 6 位

    返回：
        指定位数的纯数字字符串

    说明：
    1. 当前短信验证码默认就是 6 位数字
    2. 使用 secrets.choice 而不是 random，更适合安全场景
    """
    # 定义数字字符集
    digits = string.digits

    # 基于安全随机源生成验证码
    return "".join(secrets.choice(digits) for _ in range(length))


def generate_state(length: int = 32) -> str:
    """
    生成微信扫码登录使用的 state

    参数：
        length: state 的随机长度参数，默认 32

    返回：
        URL 安全的随机字符串

    说明：
    1. state 主要用于防止 CSRF
    2. 这里使用 token_urlsafe 生成更适合 URL 传输的随机值
    3. 实际返回长度可能和参数不完全一一对应，这是正常的
    """
    return secrets.token_urlsafe(length)