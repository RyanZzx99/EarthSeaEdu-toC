# 导入 requests，用于请求微信开放平台接口
import requests

# 导入 urllib.parse.quote，用于对 redirect_uri 做 URL 编码
from urllib.parse import quote

# 导入项目配置
from backend.config.db_conf import settings


def build_wechat_qr_authorize_url(state: str) -> str:
    """
    构造微信 PC 网站扫码登录地址

    参数:
        state: 防 CSRF 的随机字符串，由后端生成并入库

    返回:
        可直接跳转的微信扫码地址
    """

    # 对回调地址做 URL 编码
    # 因为 redirect_uri 作为 URL 参数的一部分，必须编码
    redirect_uri = quote(settings.wechat_open_redirect_uri, safe="")

    # 拼接微信开放平台扫码登录地址
    url = (
        "https://open.weixin.qq.com/connect/qrconnect"
        f"?appid={settings.wechat_open_app_id}"
        f"&redirect_uri={redirect_uri}"
        "&response_type=code"
        "&scope=snsapi_login"
        f"&state={state}"
        "#wechat_redirect"
    )

    # 返回扫码地址
    return url


def get_wechat_access_info_by_code(code: str) -> dict:
    """
    用微信回调 code 换取 access_token / openid / unionid

    参数:
        code: 微信回调时带回来的临时 code

    返回:
        微信接口返回的数据字典
    """

    # 微信 OAuth 获取 access_token 接口
    url = "https://api.weixin.qq.com/sns/oauth2/access_token"

    # 请求参数
    params = {
        "appid": settings.wechat_open_app_id,
        "secret": settings.wechat_open_app_secret,
        "code": code,
        "grant_type": "authorization_code",
    }

    # 发起 HTTP GET 请求
    # TODO: 生产环境可接入更稳健的超时、重试、日志系统
    response = requests.get(url, params=params, timeout=10)

    # 解析微信返回 JSON
    data = response.json()

    # 微信返回 errcode 说明失败
    if "errcode" in data:
        raise ValueError(f"微信换取 access_token 失败: {data}")

    # 返回接口结果
    return data


def get_wechat_userinfo(access_token: str, openid: str) -> dict:
    """
    获取微信用户公开信息（昵称、头像等）

    说明:
        不是核心登录强依赖
        获取失败时可降级为空字典

    参数:
        access_token: 微信 access_token
        openid: 微信 openid

    返回:
        用户信息字典
    """

    # 微信用户信息接口
    url = "https://api.weixin.qq.com/sns/userinfo"

    # 请求参数
    params = {
        "access_token": access_token,
        "openid": openid,
    }

    # 发请求
    response = requests.get(url, params=params, timeout=10)

    # 解析响应
    data = response.json()

    # 如果失败，则返回空字典
    if "errcode" in data:
        return {}

    # 正常返回用户信息
    return data
