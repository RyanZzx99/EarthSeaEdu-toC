"""
AI 对话 WebSocket 与档案表单接口相关 schema。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel
from pydantic import Field


class AiChatWsEnvelope(BaseModel):
    """WebSocket 统一消息包。"""

    type: str = Field(..., description="消息类型")
    request_id: str | None = Field(default=None, description="请求唯一 ID")
    session_id: str | None = Field(default=None, description="当前会话 ID")
    payload: dict[str, Any] = Field(default_factory=dict, description="消息负载")


class ConnectInitPayload(BaseModel):
    """WebSocket 建连初始化消息体。"""

    student_id: str = Field(..., description="学生 ID；直接复用 users.id")
    session_id: str | None = Field(default=None, description="会话 ID；允许为空，服务端可自动创建")
    biz_domain: str = Field(..., description="业务域")
    access_token: str = Field(..., description="登录 access token")


class UserMessagePayload(BaseModel):
    """用户发言消息体。"""

    content: str = Field(..., min_length=1, description="用户发送的文本消息内容")


class ArchiveFormSavePayload(BaseModel):
    """
    档案表单保存请求体。

    中文注释：
    这里提交的是“正式业务表结构”的表单数据，
    后端会先更新正式业务表，再基于数据库最新快照重算六维图。
    """

    archive_form: dict[str, Any] = Field(..., description="用户编辑后的正式档案表单数据")
