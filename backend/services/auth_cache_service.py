"""
认证相关 Redis 缓存服务

说明：
1. 统一管理短信验证码、发送限流、微信扫码 state 等短期状态
2. 这些数据天然带过期时间，适合放 Redis，不再落 MySQL
3. 所有方法都尽量保持幂等和一次性消费语义
"""

from datetime import datetime, timedelta

from redis.exceptions import WatchError

from backend.config.db_conf import settings
from backend.utils.redis_client import get_redis_client
from backend.utils.security import hash_code
from backend.utils.security import verify_code


def _build_sms_code_key(mobile: str, biz_type: str) -> str:
    """
    生成短信验证码缓存 key
    """
    return f"auth:sms:code:{biz_type}:{mobile}"


def _build_sms_cooldown_key(mobile: str, biz_type: str) -> str:
    """
    生成短信发送冷却 key
    """
    return f"auth:sms:cooldown:{biz_type}:{mobile}"


def _build_sms_daily_limit_key(mobile: str, biz_type: str, date_str: str) -> str:
    """
    生成短信每日发送次数统计 key
    """
    return f"auth:sms:daily:{date_str}:{biz_type}:{mobile}"


def _build_wechat_state_key(state: str) -> str:
    """
    生成微信扫码登录 state 缓存 key
    """
    return f"auth:wechat:state:{state}"


def _seconds_until_next_utc_day() -> int:
    """
    计算距离下一个 UTC 零点的秒数
    """
    now = datetime.utcnow()
    next_day = (now + timedelta(days=1)).replace(
        hour=0,
        minute=0,
        second=0,
        microsecond=0,
    )
    return max(1, int((next_day - now).total_seconds()))


def create_wechat_login_state_cache(state: str, expire_minutes: int = 10) -> None:
    """
    写入微信扫码登录 state

    说明：
    1. Redis TTL 直接承担过期控制
    2. value 本身无业务含义，只需要占位即可
    """
    redis_client = get_redis_client()

    # 中文注释：state 只需要存在性校验，因此 value 固定写 1 即可
    redis_client.set(
        _build_wechat_state_key(state),
        "1",
        ex=max(1, expire_minutes * 60),
    )


def consume_wechat_login_state_cache(state: str) -> bool:
    """
    校验并消费微信扫码登录 state

    说明：
    1. 使用 GETDEL 保证校验成功后立即删除
    2. 不存在即说明无效、已过期或已被消费
    """
    redis_client = get_redis_client()

    # 中文注释：GETDEL 可以原子地“读取并删除”，避免同一个 state 被重复使用
    result = redis_client.execute_command("GETDEL", _build_wechat_state_key(state))
    return result is not None


def save_sms_code_cache(mobile: str, biz_type: str, code: str) -> None:
    """
    保存短信验证码并更新冷却与当日发送计数

    控制规则：
    1. 冷却时间内禁止重复发送
    2. 同手机号同业务每日发送次数受限
    3. 只存验证码哈希，不存明文
    """
    redis_client = get_redis_client()
    cooldown_key = _build_sms_cooldown_key(mobile, biz_type)
    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    daily_limit_key = _build_sms_daily_limit_key(mobile, biz_type, today_str)
    daily_count = int(redis_client.get(daily_limit_key) or 0)

    sms_code_key = _build_sms_code_key(mobile, biz_type)
    code_expire_seconds = max(1, settings.sms_code_expire_minutes * 60)
    cooldown_seconds = max(1, settings.sms_send_cooldown_seconds)
    seconds_until_next_day = _seconds_until_next_utc_day()

    # 中文注释：短信发送成功后再写 Redis，确保计数只统计真正成功的发送
    pipeline = redis_client.pipeline()
    pipeline.set(sms_code_key, hash_code(code), ex=code_expire_seconds)
    pipeline.set(cooldown_key, "1", ex=cooldown_seconds)
    pipeline.incr(daily_limit_key)

    # 中文注释：首次写入当日计数时设置过期到第二天零点，避免计数长期累积
    if daily_count == 0:
        pipeline.expire(daily_limit_key, seconds_until_next_day)

    pipeline.execute()


def validate_sms_send_allowed(mobile: str, biz_type: str) -> None:
    """
    校验短信验证码是否允许发送

    说明：
    1. 只做前置校验，不写入任何状态
    2. 用于保证短信发送前就拦截冷却和日限逻辑
    """
    redis_client = get_redis_client()
    cooldown_key = _build_sms_cooldown_key(mobile, biz_type)
    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    daily_limit_key = _build_sms_daily_limit_key(mobile, biz_type, today_str)

    # 中文注释：存在冷却 key 说明该手机号该业务距离上次发送仍在冷却中
    if redis_client.exists(cooldown_key):
        raise ValueError("发送过于频繁，请稍后再试")

    daily_count = int(redis_client.get(daily_limit_key) or 0)
    if daily_count >= settings.sms_daily_limit:
        raise ValueError("今日验证码发送次数已达上限")


def verify_sms_code_cache(mobile: str, biz_type: str, code: str) -> bool:
    """
    校验并消费短信验证码

    说明：
    1. Redis TTL 已承担过期判断，key 不存在即视为错误或过期
    2. 校验成功后删除 key，保证验证码只能使用一次
    3. 使用 WATCH 保证并发下的“验证后删除”尽量原子
    """
    redis_client = get_redis_client()
    sms_code_key = _build_sms_code_key(mobile, biz_type)

    for _ in range(3):
        try:
            with redis_client.pipeline() as pipeline:
                pipeline.watch(sms_code_key)
                code_hash = pipeline.get(sms_code_key)

                if not code_hash:
                    pipeline.unwatch()
                    return False

                if not verify_code(code, code_hash):
                    pipeline.unwatch()
                    return False

                # 中文注释：验证码校验通过后立即删除，防止重复提交再次通过
                pipeline.multi()
                pipeline.delete(sms_code_key)
                pipeline.execute()
                return True
        except WatchError:
            # 中文注释：并发竞争时重试少量次数，避免瞬时并发导致误判
            continue

    return False
