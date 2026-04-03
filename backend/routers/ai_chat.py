"""
AI 瀵硅瘽 WebSocket 涓?REST 璺敱銆?
鏈枃浠跺悓鏃舵壙杞斤細
1. REST 鏌ヨ鎺ュ彛锛氱敤浜庝細璇濇仮澶嶃€佸巻鍙叉秷鎭煡璇€佺粨鏋滄煡璇€?2. WebSocket 瀹炴椂閫氶亾锛氱敤浜庡璇濄€佹祦寮忚緭鍑恒€佸彇娑堢敓鎴愩€?"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from dataclasses import dataclass
from dataclasses import field
from time import perf_counter
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
from backend.schemas.ai_chat import ArchiveFormSavePayload
from backend.schemas.ai_chat import UserMessagePayload
from backend.services.ai_chat_pipeline_service import CONVERSATION_PROMPT_KEY
from backend.services.ai_chat_pipeline_service import PROGRESS_PROMPT_KEY
from backend.services.ai_chat_pipeline_service import _build_progress_extraction_context
from backend.services.ai_chat_pipeline_service import _merge_progress_with_archive_snapshot
from backend.services.ai_chat_pipeline_service import _parse_json_object
from backend.services.ai_chat_pipeline_service import build_conversation_context
from backend.services.ai_chat_pipeline_service import persist_progress_result
from backend.services.ai_chat_pipeline_service import run_progress_update_pipeline
from backend.services.ai_chat_profile_draft_service import extract_latest_patch_into_ai_chat_profile_draft
from backend.services.ai_chat_profile_draft_service import get_ai_chat_profile_draft_detail
from backend.services.ai_chat_profile_draft_service import regenerate_profile_result_from_ai_chat_profile_draft
from backend.services.ai_chat_profile_draft_service import sync_ai_chat_profile_draft_from_official_snapshot
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
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.ai_prompt_runtime_service import stream_prompt_with_context
from backend.services.business_profile_form_service import load_business_profile_form_bundle
from backend.services.business_profile_manual_save_service import regenerate_profile_result_from_business_profile_snapshot
from backend.services.business_profile_manual_save_service import save_business_profile_form_only
from backend.utils.flow_logging import build_flow_prefix
from backend.utils.flow_logging import log_flow_info
from backend.utils.flow_logging import log_timed_step
from backend.utils.security import decode_token_safe


router = APIRouter()
logger = logging.getLogger(__name__)
ASSISTANT_REPLY_START_MARKER = "<assistant_reply>"
ASSISTANT_REPLY_END_MARKER = "</assistant_reply>"
PROGRESS_RESULT_START_MARKER = "<progress_result>"
PROGRESS_RESULT_END_MARKER = "</progress_result>"
PROFILE_DRAFT_FLOW_NAME = "智能建档"


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


def _get_latest_user_text_from_conversation_context(
    conversation_context: dict[str, Any] | None,
) -> str:
    """
    从当前对话上下文里提取最后一条用户可见消息。

    中文注释：
    1. 合并 prompt 的语义兜底只看“当前这一轮用户刚说了什么”。
    2. 这样规则足够保守，不会因为老历史误触发回退。
    """

    if not isinstance(conversation_context, dict):
        return ""

    conversation_history = conversation_context.get("conversation_history")
    if not isinstance(conversation_history, list):
        return ""

    for item in reversed(conversation_history):
        if not isinstance(item, dict):
            continue
        if item.get("message_role") != "user":
            continue
        content = item.get("content")
        if isinstance(content, str) and content.strip():
            return content.strip()
    return ""


def _contains_any_keyword(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in text for keyword in keywords)


def _has_explicit_grade_info(text: str) -> bool:
    return bool(
        re.search(
            r"(高[一二三]|初[一二三]|(?:9|10|11|12)年级|G(?:9|10|11|12)|IB(?:11|12|1|2)|AS|A2|gap[\s-]*year)",
            text,
            flags=re.IGNORECASE,
        )
    )


def _has_explicit_target_country_info(text: str) -> bool:
    return _contains_any_keyword(
        text,
        (
            "英国",
            "美国",
            "加拿大",
            "澳大利亚",
            "新加坡",
            "香港",
            "中国香港",
            "日本",
        ),
    )


def _has_explicit_major_info(text: str) -> bool:
    return bool(
        re.search(
            r"(目标专业|申请专业|专业方向|专业是|专业为|专业可能是|想申|方向是|方向为|方向想走|方向考虑|PPE|计算机科学|数据科学|机械工程|商科|经济)",
            text,
            flags=re.IGNORECASE,
        )
    )


def _has_explicit_curriculum_info(text: str) -> bool:
    positive_patterns = (
        r"普高体系",
        r"中国普高",
        r"国内普高",
        r"我是普高",
        r"普高转国际部",
        r"ib体系",
        r"读ib",
        r"我是ib",
        r"ib学生",
        r"a-level体系",
        r"a level体系",
        r"读a-level",
        r"学a-level",
        r"转去a-level",
        r"alevel体系",
        r"美高\s*\+\s*ap",
        r"ap体系",
        r"上过ap",
        r"修ap",
        r"ap课程",
    )
    return any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in positive_patterns)


def _get_dimension_status(progress_result: dict[str, Any], dimension_name: str) -> str:
    dimension_progress = progress_result.get("dimension_progress")
    if not isinstance(dimension_progress, dict):
        return ""
    dimension_item = dimension_progress.get(dimension_name)
    if not isinstance(dimension_item, dict):
        return ""
    status = dimension_item.get("status")
    return str(status or "").strip()


def _detect_combined_progress_fallback_reason(
    *,
    assistant_content: str,
    progress_result: dict[str, Any],
    conversation_context: dict[str, Any] | None,
) -> str | None:
    """
    判断合并 prompt 返回的 progress_result 是否明显不可信。

    中文注释：
    1. 这里只拦截“非常明显的错判”，避免把错误进度直接写库。
    2. 一旦命中，就回退到旧版 progress_extraction，让结构化进度判断重新跑一次。
    """

    if not isinstance(progress_result, dict):
        return "progress_result 不是合法对象"

    stop_ready = progress_result.get("stop_ready") is True
    missing_dimensions = progress_result.get("missing_dimensions") or []
    next_question_focus = progress_result.get("next_question_focus")

    if stop_ready and missing_dimensions:
        return "stop_ready=true 但 missing_dimensions 仍不为空"
    if stop_ready and next_question_focus is not None:
        return "stop_ready=true 但 next_question_focus 仍不为空"

    if _contains_any_keyword(
        assistant_content,
        (
            "现在可以建档",
            "已经可以建档",
            "可以生成六维图",
            "信息已经足够",
        ),
    ) and not stop_ready:
        return "assistant 文案表示可以建档，但 progress_result.stop_ready=false"

    latest_user_text = _get_latest_user_text_from_conversation_context(conversation_context)
    if not latest_user_text:
        return None

    basic_info_progress = progress_result.get("basic_info_progress")
    if not isinstance(basic_info_progress, dict):
        return "basic_info_progress 不是合法对象"

    def _is_collected(field_name: str) -> bool:
        field_item = basic_info_progress.get(field_name)
        return isinstance(field_item, dict) and field_item.get("collected") is True

    if _has_explicit_grade_info(latest_user_text) and not _is_collected("current_grade"):
        return "当前轮用户已明确提供年级，但 progress_result 未收集 current_grade"
    if _has_explicit_target_country_info(latest_user_text) and not _is_collected("target_country"):
        return "当前轮用户已明确提供目标国家，但 progress_result 未收集 target_country"
    if _has_explicit_major_info(latest_user_text) and not _is_collected("major_interest"):
        return "当前轮用户已明确提供目标专业，但 progress_result 未收集 major_interest"
    if _has_explicit_curriculum_info(latest_user_text) and not _is_collected("curriculum_system"):
        return "当前轮用户已明确提供课程体系，但 progress_result 未收集 curriculum_system"

    dimension_keyword_mappings = {
        "academic": ("成绩", "排名", "GPA", "预估", "预测总分", "科目", "年级前"),
        "language": ("雅思", "托福", "DET", "多邻国", "PTE", "LanguageCert", "剑桥英语"),
        "standardized": ("SAT", "ACT"),
        "competition": ("竞赛", "比赛", "USACO", "AMC", "论文比赛"),
        "activity": ("学生会", "社团", "队长", "部长", "志愿", "活动", "club"),
        "project": ("项目", "科研", "实习", "小程序", "summer program", "program", "开发", "课题"),
    }
    for dimension_name, keywords in dimension_keyword_mappings.items():
        if _contains_any_keyword(latest_user_text, keywords) and _get_dimension_status(progress_result, dimension_name) == "missing":
            return f"当前轮用户已明确提供 {dimension_name} 维信息，但 progress_result 仍标记为 missing"

    return None


@router.get("/api/v1/ai-chat/sessions/current")
def get_current_active_session(
    biz_domain: str = Query(..., description="涓氬姟鍩燂紱褰撳墠浼?student_profile_build"),
    create_if_missing: bool = Query(default=False, description="若当前没有 active 会话，是否自动创建一个新会话"),
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
    if session is None and create_if_missing:
        init_result = init_or_get_session(
            db,
            student_id=student_id,
            session_id=None,
            biz_domain=biz_domain,
        )
        return {
            "has_active_session": True,
            "session": {
                "session_id": init_result.session_id,
                "session_status": init_result.session_status,
                "current_stage": init_result.current_stage,
                "current_round": init_result.current_round,
                "missing_dimensions": [],
                "last_message_at": None,
                "is_new_session": init_result.is_new_session,
            },
        }

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
        "profile_json": result.profile_json,
        "summary_text": result.summary_text,
        "radar_scores_json": result.radar_scores_json or {},
        "db_payload_json": result.db_payload_json,
        "save_error_message": result.save_error_message,
        "create_time": result.create_time.isoformat(),
        "update_time": result.update_time.isoformat(),
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


@router.get("/api/v1/ai-chat/sessions/{session_id}/archive-form")
def get_ai_chat_session_archive_form(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    读取正式档案表单。

    中文注释：
    这里返回的数据来自正式业务表，而不是 ai_chat_profile_results.profile_json。
    前端拿到这份快照后，按表单渲染给学生修改。
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

    form_bundle = load_business_profile_form_bundle(
        db,
        student_id=student_id,
    )
    refreshed_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )

    return {
        "session_id": session_id,
        "archive_form": form_bundle["archive_form"],
        "form_meta": form_bundle["form_meta"],
        "result_status": refreshed_result.result_status if refreshed_result else None,
        "summary_text": refreshed_result.summary_text if refreshed_result else None,
        "radar_scores_json": refreshed_result.radar_scores_json if refreshed_result else {},
        "save_error_message": refreshed_result.save_error_message if refreshed_result else None,
        "create_time": refreshed_result.create_time.isoformat() if refreshed_result else None,
        "update_time": refreshed_result.update_time.isoformat() if refreshed_result else None,
    }


@router.post("/api/v1/ai-chat/sessions/{session_id}/archive-form")
def save_ai_chat_session_archive_form(
    session_id: str,
    payload: ArchiveFormSavePayload,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    保存正式档案表单。

    中文注释：
    这条链路只更新正式业务表，不触发 AI 重算六维图。
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

    if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
        raise HTTPException(
            status_code=409,
            detail="当前档案还在后台处理中，请等待当前流程完成后再手动保存。",
        )

    try:
        save_business_profile_form_only(
            db,
            student_id=student_id,
            session_id=session_id,
            archive_form=payload.archive_form,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    form_bundle = load_business_profile_form_bundle(
        db,
        student_id=student_id,
    )
    refreshed_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    return {
        "session_id": session_id,
        "archive_form": form_bundle["archive_form"],
        "form_meta": form_bundle["form_meta"],
        "profile_result_id": refreshed_result.profile_result_id if refreshed_result else None,
        "result_status": refreshed_result.result_status if refreshed_result else None,
        "profile_json": refreshed_result.profile_json if refreshed_result else None,
        "summary_text": refreshed_result.summary_text if refreshed_result else None,
        "radar_scores_json": refreshed_result.radar_scores_json if refreshed_result else {},
        "db_payload_json": refreshed_result.db_payload_json if refreshed_result else None,
        "save_error_message": refreshed_result.save_error_message if refreshed_result else None,
        "create_time": refreshed_result.create_time.isoformat() if refreshed_result else None,
        "update_time": refreshed_result.update_time.isoformat() if refreshed_result else None,
    }


@router.post("/api/v1/ai-chat/sessions/{session_id}/archive-form/regenerate-radar")
def regenerate_ai_chat_session_archive_radar(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    基于数据库最新正式档案快照重新生成六维图。
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

    if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
        raise HTTPException(
            status_code=409,
            detail="当前档案还在后台处理中，请等待当前流程完成后再重新生成六维图。",
        )

    try:
        regenerate_profile_result_from_business_profile_snapshot(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=session.biz_domain,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    form_bundle = load_business_profile_form_bundle(
        db,
        student_id=student_id,
    )
    refreshed_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if refreshed_result is None:
        raise HTTPException(status_code=404, detail="更新后的建档结果不存在")

    return {
        "session_id": refreshed_result.session_id,
        "archive_form": form_bundle["archive_form"],
        "form_meta": form_bundle["form_meta"],
        "profile_result_id": refreshed_result.profile_result_id,
        "result_status": refreshed_result.result_status,
        "profile_json": refreshed_result.profile_json,
        "summary_text": refreshed_result.summary_text,
        "radar_scores_json": refreshed_result.radar_scores_json or {},
        "db_payload_json": refreshed_result.db_payload_json,
        "save_error_message": refreshed_result.save_error_message,
        "create_time": refreshed_result.create_time.isoformat(),
        "update_time": refreshed_result.update_time.isoformat(),
    }


@router.get("/api/v1/ai-chat/sessions/{session_id}/draft")
def get_ai_chat_profile_draft(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    读取当前 session 的对话草稿。
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

    return get_ai_chat_profile_draft_detail(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=session.biz_domain,
    )


@router.post("/api/v1/ai-chat/sessions/{session_id}/draft/sync-from-official")
def sync_ai_chat_profile_draft(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    用正式业务表快照覆盖当前 session 的对话草稿。
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

    if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
        raise HTTPException(
            status_code=409,
            detail="当前会话仍在后台处理中，请等待当前流程完成后再同步智能建档草稿。",
        )

    return sync_ai_chat_profile_draft_from_official_snapshot(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=session.biz_domain,
        source_round=session.current_round,
    )


@router.post("/api/v1/ai-chat/sessions/{session_id}/draft/extract-latest-patch")
def extract_ai_chat_profile_draft_latest_patch(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    从最近一轮对话提取 patch，并更新当前 session 的对话草稿。
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

    if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
        raise HTTPException(
            status_code=409,
            detail="当前会话仍在后台处理中，请等待当前流程完成后再提取最新对话 patch。",
        )

    try:
        return extract_latest_patch_into_ai_chat_profile_draft(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=session.biz_domain,
            source_round=session.current_round,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post("/api/v1/ai-chat/sessions/{session_id}/draft/regenerate-radar")
def regenerate_ai_chat_profile_draft_radar(
    session_id: str,
    authorization: str | None = Header(default=None, alias="Authorization"),
    db=Depends(get_db),
):
    """
    基于当前 session 的对话草稿直接重算六维图。
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

    if session.current_stage in {"progress_updating", "extraction", "scoring", "profile_saving"}:
        raise HTTPException(
            status_code=409,
            detail="当前会话仍在后台处理中，请等待当前流程完成后再重算六维图。",
        )

    try:
        return regenerate_profile_result_from_ai_chat_profile_draft(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=session.biz_domain,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

@router.websocket("/ws/ai-chat")
async def ai_chat_websocket(websocket: WebSocket) -> None:
    """
    AI 对话 WebSocket 入口。

    当前正式链路：
    1. 对话阶段维护 draft
    2. progress 与 conversation 都优先读取最新 draft
    3. assistant 回复完成后，会额外抽取一轮 draft patch
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
                await _handle_user_message_draft(websocket, envelope, context)
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


async def _handle_user_message_draft(
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
) -> None:
    """
    启动一轮基于 draft 的 user_message 处理任务。
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
        _run_user_message_pipeline_draft(
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


async def _run_user_message_pipeline_draft(
    *,
    websocket: WebSocket,
    envelope: AiChatWsEnvelope,
    context: AiChatConnectionContext,
    payload: UserMessagePayload,
) -> None:
    """
    执行一轮基于 draft 的完整对话链路。
    """

    try:
        progress_result: dict[str, Any] = {}
        with log_timed_step(
            logger,
            flow_name=PROFILE_DRAFT_FLOW_NAME,
            step_name="整轮用户消息处理",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            with log_timed_step(
                logger,
                flow_name=PROFILE_DRAFT_FLOW_NAME,
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
            flow_name=PROFILE_DRAFT_FLOW_NAME,
            step_name="执行进度提取",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            progress_result = await asyncio.to_thread(
                    _run_progress_update_pipeline_draft_sync,
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
                "missing_dimensions": progress_result.get("missing_dimensions", []),
                "next_question_focus": progress_result.get("next_question_focus"),
                "stop_ready": progress_result.get("stop_ready", False),
                "dimension_progress": progress_result.get("dimension_progress", {}),
            },
        )

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
            flow_name=PROFILE_DRAFT_FLOW_NAME,
            step_name="构建回复上下文",
            student_id=context.student_id,
            session_id=context.session_id,
        ):
            conversation_context = await asyncio.to_thread(
                    _load_draft_conversation_context_sync,
                context.student_id,
                context.session_id,
                context.biz_domain,
                progress_result,
            )

        with log_timed_step(
            logger,
            flow_name=PROFILE_DRAFT_FLOW_NAME,
            step_name="打开回复流式连接",
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
            flow_name=PROFILE_DRAFT_FLOW_NAME,
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

        if not assistant_content:
            raise ValueError("conversation 未能产出可保存的助手回复。")

        with log_timed_step(
            logger,
            flow_name=PROFILE_DRAFT_FLOW_NAME,
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

        draft_patch_error_message: str | None = None
        try:
            with log_timed_step(
                logger,
                flow_name=PROFILE_DRAFT_FLOW_NAME,
                step_name="提取并合并 draft patch",
                student_id=context.student_id,
                session_id=context.session_id,
            ):
                draft_detail = await asyncio.to_thread(
                    _extract_latest_patch_into_ai_chat_profile_draft_sync,
                    context.student_id,
                    context.session_id,
                    context.biz_domain,
                    assistant_message_result.current_round,
                )
            await _send_event(
                websocket,
                event_type="draft_updated",
                request_id=envelope.request_id,
                session_id=context.session_id,
                payload={
                    "version_no": draft_detail.get("version_no"),
                    "source_round": draft_detail.get("source_round"),
                },
            )
        except Exception as draft_exc:  # noqa: BLE001
            draft_patch_error_message = str(draft_exc)
            logger.exception(
                "%s draft patch 提取失败：%s",
                build_flow_prefix(
                    flow_name=PROFILE_DRAFT_FLOW_NAME,
                    step_name="提取并合并 draft patch",
                    student_id=context.student_id,
                    session_id=context.session_id,
                ),
                draft_exc,
            )

        manager.clear_generation_runtime(websocket)

        next_stage = "build_ready" if progress_result.get("stop_ready") else "conversation"
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

        if draft_patch_error_message:
            await _send_error(
                websocket,
                code="DRAFT_PATCH_FAILED",
                message=f"本轮回复已保存，但对话草稿 patch 提取失败：{draft_patch_error_message}",
                request_id=envelope.request_id,
                session_id=context.session_id,
            )
    except asyncio.CancelledError:
        raise
    except (ValueError, PromptRuntimeError) as exc:
        logger.exception(
            "%s 对话链路失败：%s",
            build_flow_prefix(
                flow_name=PROFILE_DRAFT_FLOW_NAME,
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
                flow_name=PROFILE_DRAFT_FLOW_NAME,
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


def _load_draft_conversation_context_sync(
    student_id: str,
    session_id: str,
    biz_domain: str,
    current_progress_json: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    在线程中加载 conversation_prompt 所需的 draft 对话上下文。

    中文注释：
    1. 旧链路里的 conversation 上下文会把正式业务表快照并入 prompt。
    2. 当前正式链路要求“继续对话优先读最新 draft”，所以这里显式把 archive 快照替换成 draft。
    3. 旧的正式档案上下文函数保持不动，这里单独走 draft 上下文装配。
    """

    db = SessionLocal()
    try:
        conversation_context = build_conversation_context(
            db,
            student_id=student_id,
            session_id=session_id,
            current_progress_json=current_progress_json,
        )
        draft_detail = get_ai_chat_profile_draft_detail(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
        draft_json = draft_detail.get("draft_json") or {}
        conversation_context["current_official_archive_form_snapshot"] = (
            conversation_context.get("current_archive_form_snapshot") or {}
        )
        conversation_context["current_archive_form_snapshot"] = draft_json
        conversation_context["current_draft_json"] = draft_json
        conversation_context["current_progress_json"] = _merge_progress_with_archive_snapshot(
            conversation_context.get("current_progress_json") or {},
            draft_json,
        )
        return conversation_context
    finally:
        db.close()


def _run_progress_update_pipeline_draft_sync(
    student_id: str,
    session_id: str,
    biz_domain: str,
):
    """
    在线程中执行 progress_extraction，并让 prompt 读取最新 draft 快照。

    中文注释：
    1. 这条实验链故意不复用 run_progress_update_pipeline 的最终上下文，
       因为旧链路默认会把正式业务表快照并入 progress。
    2. 这里保留原 prompt、原 persist 逻辑，只替换 prompt 看到的 archive 快照为 draft。
    """

    db = SessionLocal()
    try:
        progress_context = _build_progress_extraction_context(
            db,
            student_id=student_id,
            session_id=session_id,
        )
        draft_detail = get_ai_chat_profile_draft_detail(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
        draft_json = draft_detail.get("draft_json") or {}
        progress_context["current_official_archive_form_snapshot"] = (
            progress_context.get("current_archive_form_snapshot") or {}
        )
        progress_context["current_archive_form_snapshot"] = draft_json
        progress_context["current_draft_json"] = draft_json
        progress_context["current_progress_json"] = _merge_progress_with_archive_snapshot(
            progress_context.get("current_progress_json") or {},
            draft_json,
        )
        progress_runtime_result = execute_prompt_with_context(
            db,
            prompt_key=PROGRESS_PROMPT_KEY,
            context=progress_context,
        )
        progress_json = _parse_json_object(
            raw_text=progress_runtime_result.content,
            scene_name="draft_progress_extraction",
        )
        return persist_progress_result(
            db,
            student_id=student_id,
            session_id=session_id,
            progress_result=progress_json,
            progress_context=progress_context,
        )
    finally:
        db.close()


def _extract_latest_patch_into_ai_chat_profile_draft_sync(
    student_id: str,
    session_id: str,
    biz_domain: str,
    source_round: int,
):
    """在线程中把最近一轮对话 patch 并入 draft。"""

    db = SessionLocal()
    try:
        return extract_latest_patch_into_ai_chat_profile_draft(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            source_round=source_round,
        )
    finally:
        db.close()


def _open_stream_session_sync(prompt_key: str, context: dict[str, Any]) -> PromptStreamSession:
    """在线程中按指定 prompt_key 打开上游流式会话。"""

    db = SessionLocal()
    try:
        return stream_prompt_with_context(
            db,
            prompt_key=prompt_key,
            context=context,
        )
    finally:
        db.close()


def _open_conversation_stream_session_sync(context: dict[str, Any]) -> PromptStreamSession:
    """在线程中打开 conversation_prompt 的上游流式会话。"""

    return _open_stream_session_sync(
        CONVERSATION_PROMPT_KEY,
        context,
    )


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


def _persist_progress_result_sync(
    student_id: str,
    session_id: str,
    progress_result: dict[str, Any],
    progress_context: dict[str, Any] | None = None,
):
    """在线程中保存一份已由合并 prompt 返回的 progress 结果。"""

    db = SessionLocal()
    try:
        return persist_progress_result(
            db,
            student_id=student_id,
            session_id=session_id,
            progress_result=progress_result,
            progress_context=progress_context,
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
