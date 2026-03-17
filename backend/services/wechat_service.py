"""
微信登录服务模块

主要职责：
1. 构造微信 PC 网站扫码登录地址
2. 使用微信回调 code 换取 access_token / openid / unionid
3. 获取微信用户公开信息（昵称、头像）

重要说明：
1. 当前场景是“微信开放平台 PC 网站扫码登录”
2. 不是公众号 H5 授权
3. 也不是小程序登录
4. 当前整个登录流程里，微信服务层只负责调用微信接口
5. 用户绑定、自动注册、合并账号这些逻辑在 auth_service.py 中处理
"""

# 导入 requests，用于发 HTTP 请求
import requests

# 导入 quote，用于 URL 编码 redirect_uri
from urllib.parse import quote

# 导入项目配置
from backend.config.db_conf import settings


def build_wechat_qr_authorize_url(state: str) -> str:
    """
    构造微信 PC 网站扫码登录地址

    参数：
        state: 后端生成并入库的随机 state 字符串

    返回：
        可直接跳转的微信扫码地址

    说明：
    1. 前端点击“微信扫码登录”按钮时，先调后端接口
    2. 后端生成 state，并通过本函数拼接微信扫码地址
    3. 前端拿到该地址后，浏览器直接跳转过去
    4. 用户扫码并确认后，微信会回调到你配置的 redirect_uri
    """

    # 对回调地址做 URL 编码
    # 因为 redirect_uri 是 URL 参数的一部分，必须编码
    redirect_uri = quote(settings.wechat_open_redirect_uri, safe="")

    # 拼接微信开放平台 PC 网站扫码登录地址
    authorize_url = (
        "https://open.weixin.qq.com/connect/qrconnect"
        f"?appid={settings.wechat_open_app_id}"
        f"&redirect_uri={redirect_uri}"
        "&response_type=code"
        "&scope=snsapi_login"
        f"&state={state}"
        "#wechat_redirect"
    )

    # 返回拼接好的扫码地址
    return authorize_url


def get_wechat_access_info_by_code(code: str) -> dict:
    """
    通过微信回调 code 换取 access_token / openid / unionid

    参数：
        code: 微信回调时带回来的临时授权码

    返回：
        微信接口返回的数据字典，典型结构类似：
        {
            "access_token": "...",
            "expires_in": 7200,
            "refresh_token": "...",
            "openid": "...",
            "scope": "snsapi_login",
            "unionid": "..."
        }

    说明：
    1. code 不是用户身份本身，只是一次性临时授权码
    2. 你真正用于识别用户的是 openid
    3. unionid 不是所有情况下都有，取决于微信返回
    4. 如果微信返回 errcode，则说明换取失败
    """
    # 微信 OAuth 获取 access_token 的接口地址
    url = "https://api.weixin.qq.com/sns/oauth2/access_token"

    # 请求参数
    params = {
        "appid": settings.wechat_open_app_id,
        "secret": settings.wechat_open_app_secret,
        "code": code,
        "grant_type": "authorization_code",
    }

    # 发起 GET 请求
    # timeout=10 防止网络问题导致长期阻塞
    response = requests.get(url, params=params, timeout=10)

    # 解析微信返回的 JSON
    data = response.json()

    # 如果微信返回 errcode，说明换取失败
    if "errcode" in data:
        raise ValueError(f"微信换取 access_token 失败: {data}")

    # 额外校验一下 openid 是否存在
    if not data.get("openid"):
        raise ValueError(f"微信返回数据缺少 openid: {data}")

    # 返回微信响应数据
    return data


def get_wechat_userinfo(access_token: str, openid: str) -> dict:
    """
    获取微信用户公开资料

    参数：
        access_token: 微信 access_token
        openid: 微信 openid

    返回：
        用户公开资料字典，常见字段可能包括：
        {
            "openid": "...",
            "nickname": "...",
            "sex": 1,
            "province": "...",
            "city": "...",
            "country": "...",
            "headimgurl": "...",
            "privilege": [],
            "unionid": "..."
        }

    说明：
    1. 这个接口主要用于拿昵称和头像
    2. 不是微信登录的强依赖
    3. 如果失败，不应该让整个登录链路直接崩掉
    4. 当前策略是：
       - 获取失败则返回空字典
       - 后续用户仍可完成登录 / 绑定手机号流程
    """
    # 微信获取用户资料接口
    url = "https://api.weixin.qq.com/sns/userinfo"

    # 请求参数
    params = {
        "access_token": access_token,
        "openid": openid,
    }

    try:
        # 发起请求
        response = requests.get(url, params=params, timeout=10)

        # 解析 JSON
        data = response.json()

        # 如果微信返回 errcode，说明获取用户资料失败
        # 这里不抛异常，直接返回空字典，避免影响主登录流程
        if "errcode" in data:
            return {}

        # 正常返回用户资料
        return data

    except Exception:
        # TODO:
        # 生产环境建议这里接入正式日志系统，
        # 例如 logger.exception("获取微信用户信息失败")
        # 当前先安全兜底返回空字典
        return {}