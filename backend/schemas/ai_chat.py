"""
AI 对话 WebSocket 相关 schema。

这一层的作用：
1. 统一约束 WebSocket 收发消息的包结构。
2. 让路由层先做最外层协议校验，再进入 service。
3. 把不同事件的 payload 定义拆开，避免路由里手写字段提取逻辑。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel
from pydantic import Field


class AiChatWsEnvelope(BaseModel):
    """
    WebSocket 统一消息包。

    字段说明：
    1. type：消息类型，例如 connect_init、user_message、ping。
    2. request_id：前端生成的请求唯一标识，便于前后端对齐一次消息往返。
    3. session_id：当前会话ID；首包时可以为空，由服务端决定是否创建。
    4. payload：具体事件负载。
    """

    type: str = Field(..., description="消息类型")
    request_id: str | None = Field(default=None, description="请求唯一ID")
    session_id: str | None = Field(default=None, description="当前会话ID")
    payload: dict[str, Any] = Field(default_factory=dict, description="消息负载")


class ConnectInitPayload(BaseModel):
    """
    WebSocket 建连初始化消息体。

    作用：
    1. 绑定当前连接的 student_id。
    2. 校验 access_token 是否与 student_id 一致。
    3. 决定当前连接是恢复旧会话，还是创建新会话。
    """

    student_id: str = Field(..., description="学生ID；直接复用 users.id")
    session_id: str | None = Field(default=None, description="会话ID；允许为空，服务端可自动创建")
    biz_domain: str = Field(..., description="业务域")
    access_token: str = Field(..., description="登录 access token")


class UserMessagePayload(BaseModel):
    """
    用户发言消息体。

    当前先做最小结构：
    1. 只接文本消息。
    2. 后续如需支持图片、语音、附件，可继续扩展字段。
    """

    content: str = Field(..., min_length=1, description="用户发送的文本消息内容")
