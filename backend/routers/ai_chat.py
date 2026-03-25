"""
AI 对话 WebSocket 与 REST 路由。

本文件同时承载：
1. REST 查询接口：用于会话恢复、历史消息查询、结果查询。
2. WebSocket 实时通道：用于对话、流式输出、取消生成。
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from dataclasses import field
from threading import Event
from typing import Any
from typing import Callable

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from fastapi import WebSocket
from fastapi import WebSocketDisconnect
from pydantic import ValidationError

from backend.config.db_conf import SessionLocal
from backend.config.db_conf import get_db
from backend.schemas.ai_chat import AiChatWsEnvelope
from backend.schemas.ai_chat import ConnectInitPayload
from backend.schemas.ai_chat import UserMessagePayload
from backend.services.ai_chat_pipeline_service import CONVERSATION_PROMPT_KEY
from backend.services.ai_chat_pipeline_service import build_conversation_context
from backend.services.ai_chat_pipeline_service import run_manual_profile_generation
from backend.services.ai_chat_pipeline_service import run_post_assistant_pipeline
from backend.services.ai_chat_service import append_assistant_message
from backend.services.ai_chat_service import append_user_message
from backend.services.ai_chat_service import get_active_session_by_student_and_domain
from backend.services.ai_chat_service import get_profile_result_detail
from backend.services.ai_chat_service import get_session_detail
from backend.services.ai_chat_service import get_visible_messages_before_id
from backend.services.ai_chat_service import init_or_get_session
from backend.services.ai_prompt_runtime_service import PromptRuntimeError
from backend.services.ai_prompt_runtime_service import PromptStreamChunk
from backend.services.ai_prompt_runtime_service import PromptStreamSession
from backend.services.ai_prompt_runtime_service import stream_prompt_with_context
from backend.utils.security import decode_token_safe


router = APIRouter()


def _extract_bearer_token(authorization: str | None) -> str | None:
    """从 Authorization 请求头里提取 Bearer Token。"""

    if not authorization:
        return None
    if not authorization.startswith("Bearer "):
        return None
    return authorization.replace("Bearer ", "", 1).strip()


def _get_current_student_id(authorization: str | None) -> str:
    """
    解析当前登录 student_id。

    当前系统口径已经确认：
    1. `student_id` 直接复用 `users.id`
    2. 当前请求中的 student_id 统一来自 access token 的 `sub`
    """

    token = _extract_bearer_token(authorization)
    if not token:
        raise HTTPException(status_code=401, detail="未登录或 token 缺失")

    payload = decode_token_safe(token)
    if not payload:
        raise HTTPException(status_code=401, detail="token 无效或已过期")

    if payload.get("token_use") != "access":
        raise HTTPException(status_code=401, detail="token 类型错误")

    student_id = payload.get("sub")
    if not student_id:
        raise HTTPException(status_code=401, detail="token 缺少用户信息")
    return str(student_id)


@dataclass(slots=True)
class AiChatConnectionContext:
    """
    单条 WebSocket 连接绑定的最小上下文。

    字段说明：
    1. `student_id / session_id / biz_domain`：连接归属的核心身份信息。
    2. `generation_task`：当前这条连接上正在跑的生成任务。
    3. `cancel_event`：取消生成的跨协程信号。
    4. `stream_close_callback`：真正关闭上游流式响应的回调。
    """

    student_id: str
    session_id: str
    biz_domain: str
    generation_task: asyncio.Task[Any] | None = None
    cancel_event: Event = field(default_factory=Event)
    stream_close_callback: Callable[[], None] | None = None


class AiChatConnectionManager:
    """
    管理当前进程内 WebSocket 连接状态。

    这一层只做“连接级运行态管理”，不做数据库持久化。
    主要作用：
    1. 绑定 student_id / session_id
    2. 记录当前是否有生成任务在跑
    3. 支持 cancel_generation 通过连接上下文找到并打断当前流
    """

    def __init__(self) -> None:
        self._connections: dict[int, AiChatConnectionContext] = {}

    async def accept(self, websocket: WebSocket) -> None:
        """接受客户端 WebSocket 连接。"""

        await websocket.accept()

    def bind(self, websocket: WebSocket, context: AiChatConnectionContext) -> None:
        """把当前连接和 student_id / session_id 绑定起来。"""

        self._connections[id(websocket)] = context

    def get(self, websocket: WebSocket) -> AiChatConnectionContext | None:
        """读取当前连接绑定的上下文。"""

        return self._connections.get(id(websocket))

    def has_running_generation(self, websocket: WebSocket) -> bool:
        """判断当前连接是否已有正在执行中的生成任务。"""

        context = self.get(websocket)
        if context is None or context.generation_task is None:
            return False
        return not context.generation_task.done()

    def reset_generation_control(self, websocket: WebSocket) -> None:
        """在一轮新生成开始前，重置取消控制状态。"""

        context = self.get(websocket)
        if context is None:
            return
        context.cancel_event.clear()
        context.stream_close_callback = None

    def set_generation_task(self, websocket: WebSocket, task: asyncio.Task[Any]) -> None:
        """记录当前连接的生成任务。"""

        context = self.get(websocket)
        if context is None:
            return
        context.generation_task = task

    def set_stream_close_callback(self, websocket: WebSocket, callback: Callable[[], None] | None) -> None:
        """注册当前流的主动关闭回调。"""

        context = self.get(websocket)
        if context is None:
            return
        context.stream_close_callback = callback

    def clear_generation_runtime(self, websocket: WebSocket) -> None:
        """清理一轮生成的运行态。"""

        context = self.get(websocket)
        if context is None:
            return
        context.generation_task = None
        context.cancel_event.clear()
        context.stream_close_callback = None

    def request_cancel(self, websocket: WebSocket) -> bool:
        """请求取消当前生成。"""

        context = self.get(websocket)
        if context is None or context.generation_task is None or context.generation_task.done():
            return False

        context.cancel_event.set()
        if context.stream_close_callback is not None:
            try:
                context.stream_close_callback()
            except Exception:
                pass
        return True

    def is_cancel_requested(self, websocket: WebSocket) -> bool:
        """判断当前连接是否已经收到取消请求。"""

        context = self.get(websocket)
        if context is None:
            return False
        return context.cancel_event.is_set()

    def disconnect(self, websocket: WebSocket) -> None:
        """断开连接并清理运行态。"""

        context = self._connections.pop(id(websocket), None)
        if context is None:
            return

        context.cancel_event.set()
        if context.stream_close_callback is not None:
            try:
                context.stream_close_callback()
            except Exception:
                pass

        if context.generation_task is not None and not context.generation_task.done():
            context.generation_task.cancel()


manager = AiChatConnectionManager()


@router.get("/api/v1/ai-chat/sessions/current")
def get_current_active_session(
    biz_domain: str = Query(..., description="业务域；当前传 student_profile_build"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    查询当前学生在指定业务域下是否存在 active 会话。

    这个接口的作用：
    1. 页面打开时先查是否已有正在进行中的 AI 建档会话。
    2. 如果有，前端就走恢复流程；如果没有，再决定是否开始新会话。
    """

    student_id = _get_current_student_id(authorization)
    session = get_active_session_by_student_and_domain(
        db,
        student_id=student_id,
        biz_domain=biz_domain,
    )
    if session is None:
        return {
            "has_active_session": False,
            "session": None,
        }

    return {
        "has_active_session": True,
        "session": {
            "session_id": session.session_id,
            "session_status": session.session_status,
            "current_stage": session.current_stage,
            "current_round": session.current_round,
            "missing_dimensions": session.missing_dimensions_json or [],
            "last_message_at": session.last_message_at.isoformat() if session.last_message_at else None,
        },
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}")
def get_ai_chat_session_detail(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    查询单个会话详情。

    这个接口的作用：
    1. 前端恢复指定 session 时读取当前阶段、轮次、缺失维度等核心状态。
    2. 所有查询都带 student_id 归属校验，避免越权读取别人的会话。
    """

    student_id = _get_current_student_id(authorization)
    try:
        session = get_session_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    except ValueError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    return {
        "session_id": session.session_id,
        "student_id": session.student_id,
        "biz_domain": session.biz_domain,
        "session_status": session.session_status,
        "current_stage": session.current_stage,
        "current_round": session.current_round,
        "missing_dimensions": session.missing_dimensions_json or [],
        "last_message_at": session.last_message_at.isoformat() if session.last_message_at else None,
        "completed_at": session.completed_at.isoformat() if session.completed_at else None,
        "final_profile_id": session.final_profile_id,
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}/messages")
def get_ai_chat_session_messages(
    session_id: str,
    limit: int = Query(default=20, ge=1, le=100, description="单次返回消息数量"),
    before_id: int | None = Query(default=None, description="分页游标；返回 id 小于该值的更早消息"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    分页查询会话消息。

    这个接口只返回前端可见消息，不返回 internal_state 内部状态消息。
    主要用于：
    1. 页面刷新后恢复聊天记录
    2. 聊天窗口向上翻页加载更早历史
    """

    student_id = _get_current_student_id(authorization)
    try:
        session = get_session_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    except ValueError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    messages = get_visible_messages_before_id(
        db,
        session_id=session_id,
        before_id=before_id,
        limit=limit,
    )
    return {
        "items": [
            {
                "id": item.id,
                "message_role": item.message_role,
                "message_type": item.message_type,
                "sequence_no": item.sequence_no,
                "content": item.content,
                "create_time": item.create_time.isoformat(),
            }
            for item in messages
        ],
        "has_more": len(messages) == limit,
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}/result")
def get_ai_chat_session_result(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """查询某个 session 的最终建档结果。"""

    student_id = _get_current_student_id(authorization)
    try:
        result = get_profile_result_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    except ValueError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    if result is None:
        raise HTTPException(status_code=404, detail="建档结果不存在")

    return {
        "session_id": result.session_id,
        "profile_result_id": result.profile_result_id,
        "result_status": result.result_status,
        "summary_text": result.summary_text,
        "radar_scores_json": result.radar_scores_json or {},
        "save_error_message": result.save_error_message,
        "create_time": result.create_time.isoformat(),
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}/radar")
def get_ai_chat_session_radar(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """单独查询六维图结果。"""

    student_id = _get_current_student_id(authorization)
    try:
        result = get_profile_result_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    except ValueError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    if result is None:
        raise HTTPException(status_code=404, detail="六维图结果不存在")

    return {
        "session_id": result.session_id,
        "profile_result_id": result.profile_result_id,
        "result_status": result.result_status,
        "radar_scores_json": result.radar_scores_json or {},
    }


@router.post("/api/v1/ai-chat/sessions/{session_id}/build-profile")
def build_ai_chat_profile_result(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    显式触发某个会话的最终建档与六维图生成。

    这个接口的作用：
    1. 把“信息采集完成”与“真正生成六维图”拆开
    2. 只有学生主动点击按钮时，才执行 extraction / scoring / 正式业务表入库
    3. 生成完成后仍然保留会话，学生后续可继续补充信息并再次生成
    """

    student_id = _get_current_student_id(authorization)
    try:
        session = get_session_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    except ValueError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc

    if session is None:
        raise HTTPException(status_code=404, detail="会话不存在")

    try:
        result = run_manual_profile_generation(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=session.biz_domain,
        )
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc

    return {
        "session_id": session_id,
        "profile_result_id": result.profile_result_id,
        "result_status": result.result_status,
        "summary_text": result.summary_text,
        "radar_scores_json": result.radar_scores_json or {},
        "save_error_message": result.save_error_message,
    }


@router.websocket("/ws/ai-chat")
async def ai_chat_websocket(websocket: WebSocket) -> None:
    """
    AI 对话 WebSocket 入口。

    当前支持的消息类型：
    1. `connect_init`：建连初始化，负责鉴权和会话绑定。
    2. `ping`：心跳。
    3. `user_message`：保存用户发言，并启动本轮 AI 生成任务。
    4. `cancel_generation`：真正打断当前正在进行中的流式生成。
    5. `continue_generation`：当前先明确返回不支持，因为被取消的上游流无法原地续流。
    """

    await manager.accept(websocket)

    try:
        while True:
            raw_message = await websocket.receive_json()
            envelope = _parse_envelope(raw_message)
            if envelope is None:
                await _send_error(
                    websocket,
                    code="INVALID_MESSAGE",
                    message="WebSocket 消息格式不合法",
                )
                continue

            if envelope.type == "connect_init":
                await _handle_connect_init(websocket, envelope)
                continue

            if envelope.type == "ping":
                await _send_event(
                    websocket,
                    event_type="pong",
                    request_id=envelope.request_id,
                    session_id=envelope.session_id,
                )
                continue

            context = manager.get(websocket)
            if context is None:
                await _send_error(
                    websocket,
                    code="CONNECTION_NOT_INITIALIZED",
                    message="请先发送 connect_init 完成连接初始化",
                    request_id=envelope.request_id,
                    session_id=envelope.session_id,
                )
                continue

            if envelope.type == "user_message":
                await _handle_user_message(websocket, envelope, context)
                continue

            if envelope.type == "cancel_generation":
                await _handle_cancel_generation(websocket, envelope, context)
                continue

            if envelope.type == "continue_generation":
                await _send_error(
                    websocket,
                    code="CONTINUE_NOT_SUPPORTED",
                    message="当前版本不支持在取消后继续同一轮生成，请重新发送消息发起新一轮对话",
                    request_id=envelope.request_id,
                    session_id=context.session_id,
                )
                continue

            await _send_error(
                websocket,
                code="UNSUPPORTED_EVENT_TYPE",
                message=f"暂不支持的消息类型：{envelope.type}",
                request_id=envelope.request_id,
                session_id=context.session_id,
            )
    except WebSocketDisconnect:
        manager.disconnect(websocket)


def _parse_envelope(raw_message: Any) -> AiChatWsEnvelope | None:
    """把客户端原始 JSON 解析成统一消息包。"""

    try:
        return AiChatWsEnvelope.model_validate(raw_message)
    except ValidationError:
        return None


async def _handle_connect_init(websocket: WebSocket, envelope: AiChatWsEnvelope) -> None:
    """
    处理 connect_init 首包。

    这一步做 4 件事：
    1. 校验 access_token。
    2. 校验 token.sub 与 student_id 一致。
    3. 查找或创建 ai_chat_sessions。
    4. 把当前 websocket 和 student_id / session_id 绑定。
    """

    try:
        payload = ConnectInitPayload.model_validate(
            {
                "student_id": envelope.payload.get("student_id"),
                "session_id": envelope.payload.get("session_id") or envelope.session_id,
                "biz_domain": envelope.payload.get("biz_domain"),
                "access_token": envelope.payload.get("access_token"),
            }
        )
    except ValidationError:
        await _send_error(
            websocket,
            code="INVALID_CONNECT_INIT",
            message="connect_init 缺少必要字段",
            request_id=envelope.request_id,
            session_id=envelope.session_id,
        )
        return

    token_payload = decode_token_safe(payload.access_token)
    if not token_payload:
        await _send_error(
            websocket,
            code="INVALID_ACCESS_TOKEN",
            message="access_token 无效或已过期",
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return

    if token_payload.get("token_use") != "access":
        await _send_error(
            websocket,
            code="INVALID_TOKEN_TYPE",
            message="当前 token 不是 access token",
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return

    if str(token_payload.get("sub")) != payload.student_id:
        await _send_error(
            websocket,
            code="STUDENT_ID_MISMATCH",
            message="student_id 与 access_token 不匹配",
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return

    db = SessionLocal()
    try:
        session_result = init_or_get_session(
            db,
            student_id=payload.student_id,
            session_id=payload.session_id,
            biz_domain=payload.biz_domain,
        )
    except ValueError as exc:
        await _send_error(
            websocket,
            code="SESSION_INIT_FAILED",
            message=str(exc),
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return
    finally:
        db.close()

    manager.bind(
        websocket,
        AiChatConnectionContext(
            student_id=session_result.student_id,
            session_id=session_result.session_id,
            biz_domain=session_result.biz_domain,
        ),
    )

    await _send_event(
        websocket,
        event_type="connect_ack",
        request_id=envelope.request_id,
        session_id=session_result.session_id,
        payload={
            "student_id": session_result.student_id,
            "biz_domain": session_result.biz_domain,
            "session_status": session_result.session_status,
            "current_stage": session_result.current_stage,
            "current_round": session_result.current_round,
            "is_new_session": session_result.is_new_session,
        },
    )


async def _handle_user_message(
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
) -> None:
    """
    启动一轮新的 user_message 处理任务。

    这里特意不直接 `await` 执行完整链路，而是启动后台任务，原因是：
    1. 只有这样主接收循环才能继续收 `cancel_generation`。
    2. 如果仍然串行阻塞在一次 user_message 内部，用户在生成过程中发取消包也收不到。
    """

    try:
        payload = UserMessagePayload.model_validate(envelope.payload)
    except ValidationError:
        await _send_error(
            websocket,
            code="INVALID_USER_MESSAGE",
            message="user_message 缺少有效的 content 字段",
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
        return

    if envelope.session_id and envelope.session_id != context.session_id:
        await _send_error(
            websocket,
            code="SESSION_ID_MISMATCH",
            message="消息中的 session_id 与当前连接绑定的 session_id 不一致",
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
        return

    if manager.has_running_generation(websocket):
        await _send_error(
            websocket,
            code="GENERATION_IN_PROGRESS",
            message="当前已有一轮生成正在进行中，请等待完成或先取消当前生成",
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
        return

    manager.reset_generation_control(websocket)
    task = asyncio.create_task(
        _run_user_message_pipeline(
            websocket=websocket,
            envelope=envelope,
            context=context,
            payload=payload,
        )
    )
    manager.set_generation_task(websocket, task)
    task.add_done_callback(lambda finished_task: _on_generation_task_done(websocket, finished_task))


async def _handle_cancel_generation(
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
) -> None:
    """
    处理 cancel_generation。

    取消策略分两层：
    1. 先设置 cancel_event，让当前生成任务知道“用户已经要求停止”。
    2. 再尝试调用上游流的 close 回调，主动关闭 HTTP 流。
    """

    cancelled = manager.request_cancel(websocket)
    if not cancelled:
        await _send_error(
            websocket,
            code="NO_ACTIVE_GENERATION",
            message="当前没有正在进行中的生成任务",
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
        return

    await _send_event(
        websocket,
        event_type="generation_cancel_requested",
        request_id=envelope.request_id,
        session_id=context.session_id,
        payload={
            "status": "requested",
        },
    )


async def _run_user_message_pipeline(
    *,
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
    payload: UserMessagePayload,
) -> None:
    """
    真正执行一轮 user_message 的完整链路。

    本轮链路分三段：
    1. 用户消息落库
    2. conversation_prompt 流式生成 assistant，并支持途中取消
    3. 如果未取消，则继续跑 progress / extraction / scoring / 入库
    """

    try:
        user_message_result = await asyncio.to_thread(
            _persist_user_message_sync,
            context.student_id,
            context.session_id,
            payload.content,
        )

        await _send_event(
            websocket,
            event_type="user_message_saved",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "message_id": user_message_result.message_id,
                "sequence_no": user_message_result.sequence_no,
                "current_round": user_message_result.current_round,
                "last_message_at": user_message_result.last_message_at.isoformat(),
            },
        )

        await _send_event(
            websocket,
            event_type="stage_changed",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "current_stage": "conversation",
            },
        )

        conversation_context = await asyncio.to_thread(
            _load_conversation_context_sync,
            context.student_id,
            context.session_id,
        )
        stream_session = await asyncio.to_thread(
            _open_conversation_stream_session_sync,
            conversation_context,
        )
        manager.set_stream_close_callback(websocket, stream_session.close)

        cancelled, assistant_content = await _stream_assistant_tokens(
            websocket=websocket,
            envelope=envelope,
            context=context,
            stream_session=stream_session,
        )
        manager.set_stream_close_callback(websocket, None)

        if cancelled:
            await _send_event(
                websocket,
                event_type="generation_cancelled",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "discarded_partial_reply": bool(assistant_content),
                },
            )
            await _send_event(
                websocket,
                event_type="stage_changed",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "current_stage": "conversation",
                },
            )
            return

        assistant_message_result = await asyncio.to_thread(
            _persist_assistant_message_sync,
            context.student_id,
            context.session_id,
            assistant_content,
            user_message_result.message_id,
        )

        await _send_event(
            websocket,
            event_type="assistant_done",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "message_id": assistant_message_result.message_id,
                "sequence_no": assistant_message_result.sequence_no,
                "content": assistant_content,
                "current_round": assistant_message_result.current_round,
            },
        )

        if manager.is_cancel_requested(websocket):
            await _send_event(
                websocket,
                event_type="generation_cancelled",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "discarded_partial_reply": False,
                },
            )
            await _send_event(
                websocket,
                event_type="stage_changed",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "current_stage": "conversation",
                },
            )
            return

        await _send_event(
            websocket,
            event_type="stage_changed",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "current_stage": "progress_updating",
            },
        )

        post_result = await asyncio.to_thread(
            _run_post_assistant_pipeline_sync,
            context.student_id,
            context.session_id,
            context.biz_domain,
        )

        await _send_event(
            websocket,
            event_type="progress_updated",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "missing_dimensions": post_result.progress_result.get("missing_dimensions", []),
                "next_question_focus": post_result.progress_result.get("next_question_focus"),
                "stop_ready": post_result.progress_result.get("stop_ready", False),
                "dimension_progress": post_result.progress_result.get("dimension_progress", {}),
            },
        )

        # 中文注释：
        # 这里有一个非常关键的时序问题：
        # 1. 前端会把接下来这条 `stage_changed: conversation` 理解成“本轮已经处理完，可以继续发下一条消息”
        # 2. 但如果仍然等到 task.done_callback 里才清理 generation 运行态，
        #    那么用户在收到这条事件后立刻发送下一条消息，就会被后端误判成
        #    “当前已有一轮生成正在进行中”
        # 3. 因此要在发出“可继续对话”的阶段事件之前，先释放本轮生成锁，
        #    保证前端看到可发送状态时，后端也已经真正允许下一轮进入
        manager.clear_generation_runtime(websocket)

        next_stage = "build_ready" if post_result.progress_result.get("stop_ready") else "conversation"
        await _send_event(
            websocket,
            event_type="stage_changed",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "current_stage": next_stage,
            },
        )

        if post_result.final_profile_generated:
            # 中文注释：
            # 进入最终结果阶段后，当前这轮对话生成已经结束，
            # 上面的 clear_generation_runtime 已经提前释放了运行锁。
            # 这样前端即使很快恢复到可交互状态，也不会再撞上“仍在生成中”的旧状态。
            await _send_event(
                websocket,
                event_type="radar_scores_ready",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "profile_result_id": post_result.profile_result_id,
                    "radar_scores_json": post_result.radar_scores_json or {},
                },
            )

            await _send_event(
                websocket,
                event_type="summary_ready",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "profile_result_id": post_result.profile_result_id,
                    "summary_text": post_result.summary_text or "",
                },
            )

            await _send_event(
                websocket,
                event_type="profile_saved",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "profile_result_id": post_result.profile_result_id,
                    "result_status": post_result.result_status or "generated",
                    "save_error_message": post_result.save_error_message,
                },
            )

            await _send_event(
                websocket,
                event_type="stage_changed",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "current_stage": "completed" if post_result.result_status == "saved" else "failed",
                },
            )
    except asyncio.CancelledError:
        raise
    except (ValueError, PromptRuntimeError) as exc:
        await _send_error(
            websocket,
            code="CHAT_PIPELINE_FAILED",
            message=str(exc),
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
    except Exception as exc:
        await _send_error(
            websocket,
            code="CHAT_PIPELINE_UNEXPECTED_ERROR",
            message=str(exc),
            request_id=envelope.request_id,
            session_id=context.session_id,
        )


async def _stream_assistant_tokens(
    *,
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
    stream_session: PromptStreamSession,
) -> tuple[bool, str]:
    """
    按上游流式返回，逐段向前端推送 assistant_token。

    返回值：
    1. 第一个值表示本轮是否被用户主动取消。
    2. 第二个值是当前已经累计得到的 assistant 全文。
    """

    accumulated_text = ""

    while True:
        if manager.is_cancel_requested(websocket):
            stream_session.close()
            return True, accumulated_text

        stream_step = await asyncio.to_thread(
            _read_next_stream_chunk_sync,
            stream_session.iterator,
        )
        if stream_step["done"]:
            error = stream_step["error"]
            if manager.is_cancel_requested(websocket):
                return True, accumulated_text
            if error is not None:
                raise PromptRuntimeError(f"流式生成中断：{error}") from error
            return False, accumulated_text

        chunk = stream_step["chunk"]
        if not isinstance(chunk, PromptStreamChunk):
            continue

        if manager.is_cancel_requested(websocket):
            stream_session.close()
            return True, accumulated_text

        accumulated_text = chunk.accumulated_text
        await _send_event(
            websocket,
            event_type="assistant_token",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "delta_text": chunk.delta_text,
                "accumulated_text": chunk.accumulated_text,
            },
        )


def _on_generation_task_done(websocket: WebSocket, task: asyncio.Task[Any]) -> None:
    """
    后台生成任务结束后的统一清理回调。

    这里要做两件事：
    1. 主动读取 task.exception()，避免未消费异常导致控制台告警。
    2. 清理当前连接上的 generation 运行态，允许下一轮继续发消息。
    """

    try:
        task.exception()
    except asyncio.CancelledError:
        pass
    except Exception:
        pass
    finally:
        manager.clear_generation_runtime(websocket)


def _persist_user_message_sync(student_id: str, session_id: str, content: str):
    """在线程中执行用户消息落库。"""

    db = SessionLocal()
    try:
        return append_user_message(
            db,
            student_id=student_id,
            session_id=session_id,
            content=content,
        )
    finally:
        db.close()


def _load_conversation_context_sync(student_id: str, session_id: str) -> dict[str, Any]:
    """在线程中加载 conversation_prompt 所需上下文。"""

    db = SessionLocal()
    try:
        return build_conversation_context(
            db,
            student_id=student_id,
            session_id=session_id,
        )
    finally:
        db.close()


def _open_conversation_stream_session_sync(context: dict[str, Any]) -> PromptStreamSession:
    """在线程中打开 conversation_prompt 的上游流式会话。"""

    db = SessionLocal()
    try:
        return stream_prompt_with_context(
            db,
            prompt_key=CONVERSATION_PROMPT_KEY,
            context=context,
        )
    finally:
        db.close()


def _read_next_stream_chunk_sync(iterator):
    """
    在线程中读取上游流的下一个 chunk。

    返回结构统一做成 dict，避免 `StopIteration` 和网络异常直接穿透到协程层。
    """

    try:
        chunk = next(iterator)
        return {
            "done": False,
            "chunk": chunk,
            "error": None,
        }
    except StopIteration:
        return {
            "done": True,
            "chunk": None,
            "error": None,
        }
    except Exception as exc:
        return {
            "done": True,
            "chunk": None,
            "error": exc,
        }


def _persist_assistant_message_sync(
    student_id: str,
    session_id: str,
    content: str,
    parent_message_id: int,
):
    """在线程中保存 assistant 完整回复。"""

    db = SessionLocal()
    try:
        return append_assistant_message(
            db,
            student_id=student_id,
            session_id=session_id,
            content=content,
            parent_message_id=parent_message_id,
        )
    finally:
        db.close()


def _run_post_assistant_pipeline_sync(
    student_id: str,
    session_id: str,
    biz_domain: str,
):
    """
    在线程中执行 assistant 结束后的后半段链路。

    这一段仍然是同步服务调用，因此也放进线程执行，避免阻塞事件循环。
    """

    db = SessionLocal()
    try:
        return run_post_assistant_pipeline(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
    finally:
        db.close()


async def _send_event(
    websocket: WebSocket,
    *,
    event_type: str,
    request_id: str | None = None,
    session_id: str | None = None,
    payload: dict[str, Any] | None = None,
) -> None:
    """统一发送服务端事件。"""

    await websocket.send_json(
        {
            "type": event_type,
            "request_id": request_id,
            "session_id": session_id,
            "payload": payload or {},
        }
    )


async def _send_error(
    websocket: WebSocket,
    *,
    code: str,
    message: str,
    request_id: str | None = None,
    session_id: str | None = None,
) -> None:
    """统一发送错误事件。"""

    await _send_event(
        websocket,
        event_type="error",
        request_id=request_id,
        session_id=session_id,
        payload={
            "code": code,
            "message": message,
        },
    )
