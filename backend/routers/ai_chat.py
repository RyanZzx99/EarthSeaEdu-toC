"""
AI 瀵硅瘽 WebSocket 涓?REST 璺敱銆?
鏈枃浠跺悓鏃舵壙杞斤細
1. REST 鏌ヨ鎺ュ彛锛氱敤浜庝細璇濇仮澶嶃€佸巻鍙叉秷鎭煡璇€佺粨鏋滄煡璇€?2. WebSocket 瀹炴椂閫氶亾锛氱敤浜庡璇濄€佹祦寮忚緭鍑恒€佸彇娑堢敓鎴愩€?"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from dataclasses import field
from time import perf_counter
from threading import Event
from typing import Any
from typing import Callable

from fastapi import APIRouter
from fastapi import BackgroundTasks
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
from backend.services.ai_chat_pipeline_service import run_manual_profile_generation_background
from backend.services.ai_chat_pipeline_service import run_progress_update_pipeline
from backend.services.ai_chat_service import append_assistant_message
from backend.services.ai_chat_service import append_user_message
from backend.services.ai_chat_service import get_active_session_by_student_and_domain
from backend.services.ai_chat_service import get_latest_progress_state
from backend.services.ai_chat_service import get_profile_result_detail
from backend.services.ai_chat_service import get_session_detail
from backend.services.ai_chat_service import get_visible_messages_before_id
from backend.services.ai_chat_service import init_or_get_session
from backend.services.ai_chat_service import update_session_stage
from backend.services.ai_prompt_runtime_service import PromptRuntimeError
from backend.services.ai_prompt_runtime_service import PromptStreamChunk
from backend.services.ai_prompt_runtime_service import PromptStreamSession
from backend.services.ai_prompt_runtime_service import stream_prompt_with_context
from backend.utils.flow_logging import build_flow_prefix
from backend.utils.flow_logging import log_flow_info
from backend.utils.flow_logging import log_timed_step
from backend.utils.security import decode_token_safe


router = APIRouter()
logger = logging.getLogger(__name__)


def _extract_bearer_token(authorization: str | None) -> str | None:
    """从 Authorization 请求头中提取 Bearer Token。"""

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
    2. 当前请求中的 `student_id` 统一来自 access token 的 `sub`
    """

    token = _extract_bearer_token(authorization)
    if not token:
        raise HTTPException(status_code=401, detail="鏈櫥褰曟垨 token 缂哄け")

    payload = decode_token_safe(token)
    if not payload:
        raise HTTPException(status_code=401, detail="token 鏃犳晥鎴栧凡杩囨湡")

    if payload.get("token_use") != "access":
        raise HTTPException(status_code=401, detail="token 绫诲瀷閿欒")

    student_id = payload.get("sub")
    if not student_id:
        raise HTTPException(status_code=401, detail="token 缂哄皯鐢ㄦ埛淇℃伅")
    return str(student_id)


@dataclass(slots=True)
class AiChatConnectionContext:
    """
    鍗曟潯 WebSocket 杩炴帴缁戝畾鐨勬渶灏忎笂涓嬫枃銆?
    瀛楁璇存槑锛?    1. `student_id / session_id / biz_domain`锛氳繛鎺ュ綊灞炵殑鏍稿績韬唤淇℃伅銆?    2. `generation_task`锛氬綋鍓嶈繖鏉¤繛鎺ヤ笂姝ｅ湪璺戠殑鐢熸垚浠诲姟銆?    3. `cancel_event`锛氬彇娑堢敓鎴愮殑璺ㄥ崗绋嬩俊鍙枫€?    4. `stream_close_callback`锛氱湡姝ｅ叧闂笂娓告祦寮忓搷搴旂殑鍥炶皟銆?    """

    student_id: str
    session_id: str
    biz_domain: str
    generation_task: asyncio.Task[Any] | None = None
    cancel_event: Event = field(default_factory=Event)
    stream_close_callback: Callable[[], None] | None = None


class AiChatConnectionManager:
    """
    绠＄悊褰撳墠杩涚▼鍐?WebSocket 杩炴帴鐘舵€併€?
    杩欎竴灞傚彧鍋氣€滆繛鎺ョ骇杩愯鎬佺鐞嗏€濓紝涓嶅仛鏁版嵁搴撴寔涔呭寲銆?    涓昏浣滅敤锛?    1. 缁戝畾 student_id / session_id
    2. 璁板綍褰撳墠鏄惁鏈夌敓鎴愪换鍔″湪璺?    3. 鏀寔 cancel_generation 閫氳繃杩炴帴涓婁笅鏂囨壘鍒板苟鎵撴柇褰撳墠娴?    """

    def __init__(self) -> None:
        self._connections: dict[int, AiChatConnectionContext] = {}

    async def accept(self, websocket: WebSocket) -> None:
        """接受客户端 WebSocket 连接。"""

        await websocket.accept()

    def bind(self, websocket: WebSocket, context: AiChatConnectionContext) -> None:
        """绑定当前连接的最小上下文。"""

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
        """在新一轮生成开始前重置取消控制状态。"""

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
    biz_domain: str = Query(..., description="涓氬姟鍩燂紱褰撳墠浼?student_profile_build"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    鏌ヨ褰撳墠瀛︾敓鍦ㄦ寚瀹氫笟鍔″煙涓嬫槸鍚﹀瓨鍦?active 浼氳瘽銆?
    杩欎釜鎺ュ彛鐨勪綔鐢細
    1. 椤甸潰鎵撳紑鏃跺厛鏌ユ槸鍚﹀凡鏈夋鍦ㄨ繘琛屼腑鐨?AI 寤烘。浼氳瘽銆?    2. 濡傛灉鏈夛紝鍓嶇灏辫蛋鎭㈠娴佺▼锛涘鏋滄病鏈夛紝鍐嶅喅瀹氭槸鍚﹀紑濮嬫柊浼氳瘽銆?    """

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
    鏌ヨ鍗曚釜浼氳瘽璇︽儏銆?
    杩欎釜鎺ュ彛鐨勪綔鐢細
    1. 鍓嶇鎭㈠鎸囧畾 session 鏃惰鍙栧綋鍓嶉樁娈点€佽疆娆°€佺己澶辩淮搴︾瓑鏍稿績鐘舵€併€?    2. 鎵€鏈夋煡璇㈤兘甯?student_id 褰掑睘鏍￠獙锛岄伩鍏嶈秺鏉冭鍙栧埆浜虹殑浼氳瘽銆?    """

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
        "remark": session.remark,
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}/messages")
def get_ai_chat_session_messages(
    session_id: str,
    limit: int = Query(default=20, ge=1, le=100, description="鍗曟杩斿洖娑堟伅鏁伴噺"),
    before_id: int | None = Query(default=None, description="鍒嗛〉娓告爣锛涜繑鍥?id 灏忎簬璇ュ€肩殑鏇存棭娑堟伅"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    鍒嗛〉鏌ヨ浼氳瘽娑堟伅銆?
    杩欎釜鎺ュ彛鍙繑鍥炲墠绔彲瑙佹秷鎭紝涓嶈繑鍥?internal_state 鍐呴儴鐘舵€佹秷鎭€?    涓昏鐢ㄤ簬锛?    1. 椤甸潰鍒锋柊鍚庢仮澶嶈亰澶╄褰?    2. 鑱婂ぉ绐楀彛鍚戜笂缈婚〉鍔犺浇鏇存棭鍘嗗彶
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


@router.post("/api/v1/ai-chat/sessions/{session_id}/build-profile", status_code=202)
def build_ai_chat_profile_result(
    session_id: str,
    background_tasks: BackgroundTasks,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    显式触发某个会话的最终建档与六维图生成。

    新口径：
    1. 接口只负责受理后台任务，不同步等待完整结果。
    2. extraction、scoring、正式业务表入库都放到后台任务里执行。
    3. 前端通过轮询会话阶段来展示 loading，并在任务结束后再读取最终结果。
    """

    student_id = _get_current_student_id(authorization)
    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="受理生成六维图请求",
        student_id=student_id,
        session_id=session_id,
    ):
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

        if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
            raise HTTPException(
                status_code=409,
                detail="当前上一轮对话仍在处理中，请等待处理完成后再生成六维图。",
            )

        latest_progress_result = get_latest_progress_state(
            db,
            session_id=session_id,
        )
        if not isinstance(latest_progress_result, dict):
            raise HTTPException(status_code=409, detail="当前还没有可用的建档进度，请先继续对话补充信息。")
        if latest_progress_result.get("stop_ready") is not True:
            raise HTTPException(status_code=409, detail="当前信息还不足以生成六维图，请先继续补充关键信息。")

        update_session_stage(
            db,
            student_id=student_id,
            session_id=session_id,
            current_stage="extraction",
            remark=None,
        )
        background_tasks.add_task(
            run_manual_profile_generation_background,
            student_id=student_id,
            session_id=session_id,
            biz_domain=session.biz_domain,
        )
        log_flow_info(
            logger,
            flow_name="AI建档",
            step_name="受理生成六维图请求",
            student_id=student_id,
            session_id=session_id,
            message="后台任务已创建，进入结构化提取阶段。",
        )

        return {
            "session_id": session_id,
            "task_status": "accepted",
            "current_stage": "extraction",
            "message": "六维图正在后台生成中",
        }

@router.websocket("/ws/ai-chat")
async def ai_chat_websocket(websocket: WebSocket) -> None:
    """
    AI 瀵硅瘽 WebSocket 鍏ュ彛銆?
    褰撳墠鏀寔鐨勬秷鎭被鍨嬶細
    1. `connect_init`锛氬缓杩炲垵濮嬪寲锛岃礋璐ｉ壌鏉冨拰浼氳瘽缁戝畾銆?    2. `ping`锛氬績璺炽€?    3. `user_message`锛氫繚瀛樼敤鎴峰彂瑷€锛屽苟鍚姩鏈疆 AI 鐢熸垚浠诲姟銆?    4. `cancel_generation`锛氱湡姝ｆ墦鏂綋鍓嶆鍦ㄨ繘琛屼腑鐨勬祦寮忕敓鎴愩€?    5. `continue_generation`锛氬綋鍓嶅厛鏄庣‘杩斿洖涓嶆敮鎸侊紝鍥犱负琚彇娑堢殑涓婃父娴佹棤娉曞師鍦扮画娴併€?    """

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
                    message="当前版本暂不支持在取消后继续同一轮生成，请重新发送消息发起新一轮对话。",
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
    澶勭悊 connect_init 棣栧寘銆?
    杩欎竴姝ュ仛 4 浠朵簨锛?    1. 鏍￠獙 access_token銆?    2. 鏍￠獙 token.sub 涓?student_id 涓€鑷淬€?    3. 鏌ユ壘鎴栧垱寤?ai_chat_sessions銆?    4. 鎶婂綋鍓?websocket 鍜?student_id / session_id 缁戝畾銆?    """

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
        await _send_connect_error_and_close(
            websocket,
            code="INVALID_CONNECT_INIT",
            message="connect_init 缺少必要字段",
            request_id=envelope.request_id,
            session_id=envelope.session_id,
        )
        return

    token_payload = decode_token_safe(payload.access_token)
    if not token_payload:
        await _send_connect_error_and_close(
            websocket,
            code="INVALID_ACCESS_TOKEN",
            message="access_token 无效或已过期",
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return

    if token_payload.get("token_use") != "access":
        await _send_connect_error_and_close(
            websocket,
            code="INVALID_TOKEN_TYPE",
            message="当前 token 不是 access token",
            request_id=envelope.request_id,
            session_id=payload.session_id,
        )
        return

    if str(token_payload.get("sub")) != payload.student_id:
        await _send_connect_error_and_close(
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
        await _send_connect_error_and_close(
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
    鍚姩涓€杞柊鐨?user_message 澶勭悊浠诲姟銆?
    杩欓噷鐗规剰涓嶇洿鎺?`await` 鎵ц瀹屾暣閾捐矾锛岃€屾槸鍚姩鍚庡彴浠诲姟锛屽師鍥犳槸锛?    1. 鍙湁杩欐牱涓绘帴鏀跺惊鐜墠鑳界户缁敹 `cancel_generation`銆?    2. 濡傛灉浠嶇劧涓茶闃诲鍦ㄤ竴娆?user_message 鍐呴儴锛岀敤鎴峰湪鐢熸垚杩囩▼涓彂鍙栨秷鍖呬篃鏀朵笉鍒般€?    """

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
    澶勭悊 cancel_generation銆?
    鍙栨秷绛栫暐鍒嗕袱灞傦細
    1. 鍏堣缃?cancel_event锛岃褰撳墠鐢熸垚浠诲姟鐭ラ亾鈥滅敤鎴峰凡缁忚姹傚仠姝⑩€濄€?    2. 鍐嶅皾璇曡皟鐢ㄤ笂娓告祦鐨?close 鍥炶皟锛屼富鍔ㄥ叧闂?HTTP 娴併€?    """

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
    鐪熸鎵ц涓€杞?user_message 鐨勫畬鏁撮摼璺€?
    褰撳墠閾捐矾椤哄簭锛?    1. 鐢ㄦ埛娑堟伅钀藉簱
    2. 绔嬪埢鍏堟洿鏂?progress 蹇収
    3. conversation_prompt 鍩轰簬鈥滄渶鏂?progress鈥濇祦寮忕敓鎴?assistant
    4. assistant 缁撴潫鍚庯紝鎶婂垰鎵嶅凡缁忚绠楀ソ鐨?progress 鍥炵粰鍓嶇

    杩欐牱璁捐鐨勬牳蹇冪洰鐨勶紝鏄伩鍏?assistant 鍦ㄥ綋鍓嶈疆閲屼粛鐒剁湅鍒颁笂涓€杞棫杩涘害锛?    浠庤€岄噸澶嶈拷闂敤鎴峰垰鍒氬凡缁忓洖绛旇繃鐨勬Ы浣嶃€?    """

    try:
        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="整轮用户消息处理",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            with log_timed_step(
                logger,
                flow_name="AI对话",
                step_name="保存用户消息",
                student_id=context.student_id,
                session_id=context.session_id,
            ):
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
                    "current_stage": "progress_updating",
                },
            )

            with log_timed_step(
                logger,
                flow_name="AI对话",
                step_name="执行进度提取",
                student_id=context.student_id,
                session_id=context.session_id,
            ):
                progress_result = await asyncio.to_thread(
                    _run_progress_update_pipeline_sync,
                    context.student_id,
                    context.session_id,
                    context.biz_domain,
                )

        if manager.is_cancel_requested(websocket):
            await _send_event(
                websocket,
                event_type="progress_updated",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "missing_dimensions": progress_result.get("missing_dimensions", []),
                    "next_question_focus": progress_result.get("next_question_focus"),
                    "stop_ready": progress_result.get("stop_ready", False),
                    "dimension_progress": progress_result.get("dimension_progress", {}),
                },
            )
            manager.clear_generation_runtime(websocket)
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
                    "current_stage": "build_ready" if progress_result.get("stop_ready") else "conversation",
                    "conversation_phase": None if progress_result.get("stop_ready") else "ready_for_input",
                },
            )
            return

        # 中文注释：
        # `conversation` 这个阶段在当前系统里有两种语义：
        # 1. `generating_assistant`：本轮正在调用 conversation_prompt 生成助手回复
        # 2. `ready_for_input`：本轮已经结束，可以继续输入下一条消息
        # 如果只发一个裸的 `conversation`，前端无法区分这两种状态，就会误把“已可继续输入”当成“仍在生成中”。
        await _send_event(
            websocket,
            event_type="stage_changed",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "current_stage": "conversation",
                "conversation_phase": "generating_assistant",
            },
        )

        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="构建回复上下文",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            conversation_context = await asyncio.to_thread(
                _load_conversation_context_sync,
                context.student_id,
                context.session_id,
                progress_result,
            )
        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="打开流式回复连接",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            stream_session = await asyncio.to_thread(
                _open_conversation_stream_session_sync,
                conversation_context,
            )
        manager.set_stream_close_callback(websocket, stream_session.close)

        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="流式生成助手回复",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
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
                event_type="progress_updated",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "missing_dimensions": progress_result.get("missing_dimensions", []),
                    "next_question_focus": progress_result.get("next_question_focus"),
                    "stop_ready": progress_result.get("stop_ready", False),
                    "dimension_progress": progress_result.get("dimension_progress", {}),
                },
            )
            manager.clear_generation_runtime(websocket)
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
                    "current_stage": "build_ready" if progress_result.get("stop_ready") else "conversation",
                    "conversation_phase": None if progress_result.get("stop_ready") else "ready_for_input",
                },
            )
            return

        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="保存助手回复",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
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
                event_type="progress_updated",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "missing_dimensions": progress_result.get("missing_dimensions", []),
                    "next_question_focus": progress_result.get("next_question_focus"),
                    "stop_ready": progress_result.get("stop_ready", False),
                    "dimension_progress": progress_result.get("dimension_progress", {}),
                },
            )
            manager.clear_generation_runtime(websocket)
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
                    "current_stage": "build_ready" if progress_result.get("stop_ready") else "conversation",
                    "conversation_phase": None if progress_result.get("stop_ready") else "ready_for_input",
                },
            )
            return

        await _send_event(
            websocket,
            event_type="progress_updated",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "missing_dimensions": progress_result.get("missing_dimensions", []),
                "next_question_focus": progress_result.get("next_question_focus"),
                "stop_ready": progress_result.get("stop_ready", False),
                "dimension_progress": progress_result.get("dimension_progress", {}),
            },
        )

        # 涓枃娉ㄩ噴锛?        # 杩欓噷鏈変竴涓潪甯稿叧閿殑鏃跺簭闂锛?        # 1. 鍓嶇浼氭妸鎺ヤ笅鏉ヨ繖鏉?`stage_changed: conversation` 鐞嗚В鎴愨€滄湰杞凡缁忓鐞嗗畬锛屽彲浠ョ户缁彂涓嬩竴鏉℃秷鎭€?        # 2. 浣嗗鏋滀粛鐒剁瓑鍒?task.done_callback 閲屾墠娓呯悊 generation 杩愯鎬侊紝
        #    閭ｄ箞鐢ㄦ埛鍦ㄦ敹鍒拌繖鏉′簨浠跺悗绔嬪埢鍙戦€佷笅涓€鏉℃秷鎭紝灏变細琚悗绔鍒ゆ垚
        #    鈥滃綋鍓嶅凡鏈変竴杞敓鎴愭鍦ㄨ繘琛屼腑鈥?        # 3. 鍥犳瑕佸湪鍙戝嚭鈥滃彲缁х画瀵硅瘽鈥濈殑闃舵浜嬩欢涔嬪墠锛屽厛閲婃斁鏈疆鐢熸垚閿侊紝
        #    淇濊瘉鍓嶇鐪嬪埌鍙彂閫佺姸鎬佹椂锛屽悗绔篃宸茬粡鐪熸鍏佽涓嬩竴杞繘鍏?        manager.clear_generation_runtime(websocket)

        next_stage = "build_ready" if progress_result.get("stop_ready") else "conversation"
        log_flow_info(
            logger,
            flow_name="AI对话",
            step_name="整轮用户消息处理",
            student_id=context.student_id,
            session_id=context.session_id,
            message=f"本轮处理完成，下一阶段：{next_stage}",
        )
        await _send_event(
            websocket,
            event_type="stage_changed",
            request_id=envelope.request_id,
            session_id=context.session_id,
            payload={
                "current_stage": next_stage,
                "conversation_phase": None if next_stage == "build_ready" else "ready_for_input",
            },
        )
    except asyncio.CancelledError:
        raise
    except (ValueError, PromptRuntimeError) as exc:
        logger.exception(
            "%s 对话链路失败：%s",
            build_flow_prefix(
                flow_name="AI对话",
                step_name="整轮用户消息处理",
                student_id=context.student_id,
                session_id=context.session_id,
            ),
            exc,
        )
        await _send_error(
            websocket,
            code="CHAT_PIPELINE_FAILED",
            message=str(exc),
            request_id=envelope.request_id,
            session_id=context.session_id,
        )
    except Exception as exc:
        logger.exception(
            "%s 对话链路出现未预期异常：%s",
            build_flow_prefix(
                flow_name="AI对话",
                step_name="整轮用户消息处理",
                student_id=context.student_id,
                session_id=context.session_id,
            ),
            exc,
        )
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
    鎸変笂娓告祦寮忚繑鍥烇紝閫愭鍚戝墠绔帹閫?assistant_token銆?
    杩斿洖鍊硷細
    1. 绗竴涓€艰〃绀烘湰杞槸鍚﹁鐢ㄦ埛涓诲姩鍙栨秷銆?    2. 绗簩涓€兼槸褰撳墠宸茬粡绱寰楀埌鐨?assistant 鍏ㄦ枃銆?    """

    accumulated_text = ""
    chunk_count = 0
    first_token_logged = False
    stream_started_at = perf_counter()

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
            log_flow_info(
                logger,
                flow_name="AI对话",
                step_name="流式生成助手回复",
                student_id=context.student_id,
                session_id=context.session_id,
                message=f"流式回复结束，共返回 {chunk_count} 个分片，总耗时 {(perf_counter() - stream_started_at) * 1000:.2f} ms",
            )
            return False, accumulated_text

        chunk = stream_step["chunk"]
        if not isinstance(chunk, PromptStreamChunk):
            continue

        if manager.is_cancel_requested(websocket):
            stream_session.close()
            return True, accumulated_text

        accumulated_text = chunk.accumulated_text
        chunk_count += 1
        if not first_token_logged:
            first_token_logged = True
            log_flow_info(
                logger,
                flow_name="AI对话",
                step_name="流式生成助手回复",
                student_id=context.student_id,
                session_id=context.session_id,
                message=f"已收到首个回复分片，首字耗时 {(perf_counter() - stream_started_at) * 1000:.2f} ms",
            )
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
    鍚庡彴鐢熸垚浠诲姟缁撴潫鍚庣殑缁熶竴娓呯悊鍥炶皟銆?
    杩欓噷瑕佸仛涓や欢浜嬶細
    1. 涓诲姩璇诲彇 task.exception()锛岄伩鍏嶆湭娑堣垂寮傚父瀵艰嚧鎺у埗鍙板憡璀︺€?    2. 娓呯悊褰撳墠杩炴帴涓婄殑 generation 杩愯鎬侊紝鍏佽涓嬩竴杞户缁彂娑堟伅銆?    """

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


def _load_conversation_context_sync(
    student_id: str,
    session_id: str,
    current_progress_json: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """在线程中加载 conversation_prompt 所需上下文。"""

    db = SessionLocal()
    try:
        return build_conversation_context(
            db,
            student_id=student_id,
            session_id=session_id,
            current_progress_json=current_progress_json,
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
    鍦ㄧ嚎绋嬩腑璇诲彇涓婃父娴佺殑涓嬩竴涓?chunk銆?
    杩斿洖缁撴瀯缁熶竴鍋氭垚 dict锛岄伩鍏?`StopIteration` 鍜岀綉缁滃紓甯哥洿鎺ョ┛閫忓埌鍗忕▼灞傘€?    """

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


def _run_progress_update_pipeline_sync(
    student_id: str,
    session_id: str,
    biz_domain: str,
):
    """
    鍦ㄧ嚎绋嬩腑鎵ц褰撳墠杞殑 progress_extraction 鏇存柊銆?
    涓枃娉ㄩ噴锛?    杩欎竴姝ョ幇鍦ㄥ彂鐢熷湪 assistant 鍥炲涔嬪墠锛?    瀹冪殑杈撳嚭浼氱洿鎺ヤ綔涓?conversation_prompt 鐨勬渶鏂颁笂涓嬫枃杈撳叆銆?    """

    db = SessionLocal()
    try:
        return run_progress_update_pipeline(
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


async def _send_connect_error_and_close(
    websocket: WebSocket,
    *,
    code: str,
    message: str,
    request_id: str | None = None,
    session_id: str | None = None,
) -> None:
    """
    统一处理 connect_init 阶段的错误。

    说明：
    1. 握手初始化失败时，前端还没有拿到 connect_ack，此时继续保持连接没有实际意义。
    2. 因此这里会先发送明确的 error 事件，再主动关闭 WebSocket。
    3. 这样前端可以在初始化 promise 阶段拿到真正的失败原因，而不是只看到“连接已关闭”。
    """

    await _send_error(
        websocket,
        code=code,
        message=message,
        request_id=request_id,
        session_id=session_id,
    )
    await websocket.close(code=1008, reason=message[:120] if message else "connect_init_failed")
