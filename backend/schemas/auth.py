# 导入可选类型
from typing import Optional

# 导入 BaseModel
from pydantic import BaseModel

# 导入字段校验
from pydantic import Field


class SendSmsCodeRequest(BaseModel):
    """
    发送短信验证码请求体
    """

    # 手机号
    mobile: str = Field(..., description="手机号")

    # 业务类型
    # login 或 bind_mobile
    biz_type: str = Field(..., description="业务类型")


class PasswordLoginRequest(BaseModel):
    """
    手机号 + 密码登录请求体
    """

    # 手机号
    mobile: str = Field(..., description="手机号")

    # 密码
    password: str = Field(..., description="密码")


class SmsLoginRequest(BaseModel):
    """
    手机验证码登录请求体
    """

    # 手机号
    mobile: str = Field(..., description="手机号")

    # 验证码
    code: str = Field(..., description="验证码")


class WechatLoginRequest(BaseModel):
    """
    微信登录请求体

    说明：
    前端从微信回调页拿到 code 和 state 之后，
    调后端这个接口进行实际登录处理
    """

    # 微信回调 code
    code: str = Field(..., description="微信回调 code")

    # 微信回调 state
    state: str = Field(..., description="微信回调 state")


class WechatBindMobileRequest(BaseModel):
    """
    微信绑定手机号请求体
    """

    # bind token
    bind_token: str = Field(..., description="绑定手机号 token")

    # 手机号
    mobile: str = Field(..., description="手机号")

    # 验证码
    code: str = Field(..., description="验证码")


class SetPasswordRequest(BaseModel):
    """
    设置密码请求体
    """

    # 新密码
    new_password: str = Field(..., min_length=6, description="新密码")


class LoginResponse(BaseModel):
    """
    登录成功响应体
    """

    # access token
    access_token: str

    # token 类型
    token_type: str = "bearer"

    # 用户 ID
    user_id: int

    # 手机号
    mobile: Optional[str] = None


class BindMobileRequiredResponse(BaseModel):
    """
    需要绑定手机号时的响应体
    """

    # 下一步动作
    next_step: str = "bind_mobile"

    # bind token
    bind_token: str

    # 提示信息
    message: str = "微信登录成功，请先绑定手机号"


class UserProfileResponse(BaseModel):
    """
    当前用户信息响应体
    """

    # 用户 ID
    user_id: int

    # 手机号
    mobile: Optional[str] = None

    # 昵称
    nickname: Optional[str] = None

    # 头像
    avatar_url: Optional[str] = None

    # 状态
    status: str
