"""
认证模块 Pydantic 请求/响应模型定义

重要说明：
1. 本文件用于定义接口入参和出参的数据结构
2. 这里不是数据库模型，不直接映射 MySQL 表
3. 但这里的业务语义必须与你真实数据库表结构保持一致
4. 当前业务已确定：
   - 手机号 + 密码登录
   - 手机验证码登录
   - 微信 PC 网站扫码登录
   - 微信首次登录必须绑定手机号
   - 首次手机号/微信登录自动注册
"""

# 导入可选类型
from typing import Optional

# 导入 Pydantic 基础模型
from pydantic import BaseModel

# 导入字段定义工具
from pydantic import Field


class SendSmsCodeRequest(BaseModel):
    """
    发送短信验证码请求体

    使用场景：
    1. 手机验证码登录时发送验证码
    2. 微信绑定手机号时发送验证码
    """

    # 手机号
    mobile: str = Field(
        ...,
        description="手机号",
        examples=["13800138000"],
    )

    # 业务类型
    # 当前仅支持：
    # 1. login = 登录
    # 2. bind_mobile = 微信绑定手机号
    biz_type: str = Field(
        ...,
        description="业务类型：login=登录，bind_mobile=绑定手机号",
        examples=["login"],
    )


class PasswordLoginRequest(BaseModel):
    """
    手机号 + 密码登录请求体
    """

    # 手机号
    mobile: str = Field(
        ...,
        description="手机号",
        examples=["13800138000"],
    )

    # 密码
    password: str = Field(
        ...,
        description="密码",
        examples=["123456"],
    )


class SmsLoginRequest(BaseModel):
    """
    手机验证码登录请求体
    """

    # 手机号
    mobile: str = Field(
        ...,
        description="手机号",
        examples=["13800138000"],
    )

    # 短信验证码
    code: str = Field(
        ...,
        description="短信验证码",
        examples=["123456"],
    )


class WechatLoginRequest(BaseModel):
    """
    微信扫码登录请求体

    说明：
    1. 前端从地址栏拿到 code 和 state 后
    2. 调后端该接口
    3. 后端再去完成真正的微信登录逻辑
    """

    # 微信回调 code
    code: str = Field(
        ...,
        description="微信回调 code",
    )

    # 微信回调 state
    state: str = Field(
        ...,
        description="微信回调 state",
    )


class WechatBindMobileRequest(BaseModel):
    """
    微信登录后绑定手机号请求体

    使用场景：
    1. 微信首次扫码登录成功
    2. 但当前用户未绑定手机号
    3. 后端返回 bind_token
    4. 前端提交 bind_token + mobile + code 完成绑定
    """

    # 绑定手机号专用 token
    bind_token: str = Field(
        ...,
        description="绑定手机号专用 token",
    )

    # 手机号
    mobile: str = Field(
        ...,
        description="要绑定的手机号",
        examples=["13800138000"],
    )

    # 验证码
    code: str = Field(
        ...,
        description="绑定手机号验证码",
        examples=["123456"],
    )


class SetPasswordRequest(BaseModel):
    """
    设置密码请求体

    使用场景：
    1. 用户通过短信登录后首次设置密码
    2. 或者后续在用户中心修改密码
    """

    # 新密码
    new_password: str = Field(
        ...,
        min_length=6,
        description="新密码，至少 6 位",
        examples=["abc123456"],
    )


class LoginResponse(BaseModel):
    """
    正常登录成功响应体

    适用于：
    1. 手机号 + 密码登录成功
    2. 手机验证码登录成功
    3. 微信登录成功且已绑定手机号
    4. 微信绑定手机号成功后正式登录
    """

    # 登录 access token
    access_token: str = Field(
        ...,
        description="登录 access token",
    )

    # token 类型
    token_type: str = Field(
        default="bearer",
        description="token 类型",
    )

    # 当前用户 ID
    user_id: int = Field(
        ...,
        description="用户ID",
    )

    # 当前用户手机号
    mobile: Optional[str] = Field(
        default=None,
        description="手机号，未绑定时可能为空",
    )


class BindMobileRequiredResponse(BaseModel):
    """
    需要绑定手机号时的响应体

    适用于：
    1. 微信扫码登录成功
    2. 但用户还没有绑定手机号
    3. 前端拿到该响应后，应切换到绑定手机号界面
    """

    # 下一步操作
    next_step: str = Field(
        default="bind_mobile",
        description="下一步动作标识，当前固定为 bind_mobile",
    )

    # 绑定手机号 token
    bind_token: str = Field(
        ...,
        description="绑定手机号专用 token",
    )

    # 提示文案
    message: str = Field(
        default="微信登录成功，请先绑定手机号",
        description="前端展示给用户的提示信息",
    )


class UserProfileResponse(BaseModel):
    """
    当前登录用户资料响应体

    对应 /me 接口返回
    """

    # 用户 ID
    user_id: int = Field(
        ...,
        description="用户ID",
    )

    # 手机号
    mobile: Optional[str] = Field(
        default=None,
        description="手机号",
    )

    # 昵称
    nickname: Optional[str] = Field(
        default=None,
        description="昵称",
    )

    # 头像地址
    avatar_url: Optional[str] = Field(
        default=None,
        description="头像地址",
    )

    # 用户状态
    status: str = Field(
        ...,
        description="用户状态，例如 active / disabled",
    )
