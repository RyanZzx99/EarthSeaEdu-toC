"""
AI 对话会话服务层。

这一层负责：
1. 封装 ai_chat_sessions / ai_chat_messages 的数据库操作。
2. 为 WebSocket 路由提供“可直接调用”的会话读写接口。
3. 为后续 Prompt 编排提供统一的数据访问能力。

当前已经覆盖的最小能力：
1. connect_init 时确认或创建会话。
2. user_message / assistant_message / internal_state_message 的统一落库。
3. 读取会话可见历史消息。
4. 读取最新一份 progress_extraction 结果。
5. 更新会话的 collected_slots_json / missing_dimensions_json。
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import logging
from typing import Any
from uuid import uuid4

from sqlalchemy import func
from sqlalchemy import select
from sqlalchemy.orm import Session

from backend.models.ai_chat_models import AiChatMessage
from backend.models.ai_chat_models import AiChatProfileResult
from backend.models.ai_chat_models import AiChatSession
from backend.utils.flow_logging import log_timed_step


DEFAULT_BIZ_DOMAIN = "student_profile_build"
DEFAULT_SESSION_STATUS = "active"
DEFAULT_STAGE = "conversation"

VISIBLE_MESSAGE_TYPE = "visible_text"
INTERNAL_STATE_MESSAGE_TYPE = "internal_state"


logger = logging.getLogger(__name__)


@dataclass(slots=True)
class AiChatSessionInitResult:
    """
    connect_init 阶段返回给路由层的最小会话结果。

    作用：
    1. 路由层不用直接依赖 ORM 实体。
    2. WebSocket 回包时可以直接复用这里的数据。
    """

    session_id: str
    student_id: str
    biz_domain: str
    session_status: str
    current_stage: str
    current_round: int
    is_new_session: bool


@dataclass(slots=True)
class AiChatMessagePersistResult:
    """
    一条消息写入完成后的返回结果。

    作用：
    1. 统一返回 message_id / sequence_no / current_round。
    2. 便于 WebSocket 继续往前驱动 assistant 回复、进度更新等后续流程。
    """

    session_id: str
    student_id: str
    message_id: int
    sequence_no: int
    current_round: int
    last_message_at: datetime


@dataclass(slots=True)
class AiChatVisibleMessage:
    """
    对外可见消息的轻量结构。

    这个结构专门给 Prompt 上下文构建使用，
    避免直接把 ORM 对象向外传。
    """

    id: int
    message_role: str
    message_type: str
    sequence_no: int
    content: str
    create_time: datetime


@dataclass(slots=True)
class AiChatProfileResultPersistResult:
    """
    最终建档结果落库后的返回结果。

    作用：
    1. 让上层链路能拿到 profile_result_id。
    2. 让 WebSocket 可以把最终结果直接回给前端。
    """

    profile_result_id: int
    session_id: str
    student_id: str
    result_status: str


@dataclass(slots=True)
class AiChatSessionQueryResult:
    """
    会话查询结果对象。

    作用：
    1. 给 REST 接口返回会话详情用。
    2. 避免接口层直接依赖 ORM 对象。
    """

    session_id: str
    student_id: str
    biz_domain: str
    session_status: str
    current_stage: str
    current_round: int
    missing_dimensions_json: list | None
    last_message_at: datetime | None
    completed_at: datetime | None
    final_profile_id: int | None
    remark: str | None


@dataclass(slots=True)
class AiChatProfileResultQueryResult:
    """
    最终结果查询对象。

    作用：
    1. 统一承载 ai_chat_profile_results 对外需要返回的核心字段。
    2. 让 REST 查询接口不直接暴露 ORM 实体。
    """

    profile_result_id: int
    session_id: str
    student_id: str
    result_status: str
    profile_json: dict[str, Any]
    radar_scores_json: dict[str, Any] | None
    summary_text: str | None
    db_payload_json: dict[str, Any] | None
    save_error_message: str | None
    create_time: datetime
    update_time: datetime


def init_or_get_session(
    db: Session,
    *,
    student_id: str,
    session_id: str | None,
    biz_domain: str | None,
) -> AiChatSessionInitResult:
    """
    初始化或获取一个可用 AI 会话。

    处理逻辑：
    1. 如果客户端带了 session_id，则优先查该 session_id。
    2. 查到后校验归属，防止 session 串号。
    3. 如果没传 session_id 或查不到，则直接创建新会话。
    """

    resolved_biz_domain = (biz_domain or DEFAULT_BIZ_DOMAIN).strip()
    resolved_session_id = (session_id or "").strip()

    if resolved_session_id:
        existing_session = _get_active_session_by_session_id(
            db,
            session_id=resolved_session_id,
        )
        if existing_session is not None:
            _ensure_session_belongs_to_student(
                session=existing_session,
                student_id=student_id,
            )
            return AiChatSessionInitResult(
                session_id=existing_session.session_id,
                student_id=existing_session.student_id,
                biz_domain=existing_session.biz_domain,
                session_status=existing_session.session_status,
                current_stage=existing_session.current_stage,
                current_round=existing_session.current_round,
                is_new_session=False,
            )

    created_session = _create_session(
        db,
        student_id=student_id,
        session_id=resolved_session_id or str(uuid4()),
        biz_domain=resolved_biz_domain,
    )
    return AiChatSessionInitResult(
        session_id=created_session.session_id,
        student_id=created_session.student_id,
        biz_domain=created_session.biz_domain,
        session_status=created_session.session_status,
        current_stage=created_session.current_stage,
        current_round=created_session.current_round,
        is_new_session=True,
    )


def append_user_message(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    content: str,
) -> AiChatMessagePersistResult:
    """
    写入一条用户消息。

    当前轮次策略：
    1. 先采用“每收到一条 user_message，current_round + 1”。
    2. 这样即使后续 assistant 流式生成还未完全接通，会话轮次也能稳定推进。
    """

    session = _get_required_active_session(
        db,
        session_id=session_id,
        student_id=student_id,
    )
    persist_result = _append_message(
        db,
        session=session,
        student_id=student_id,
        message_role="user",
        message_type=VISIBLE_MESSAGE_TYPE,
        content=content,
        content_json=None,
        is_visible=1,
        increase_round=True,
    )
    return persist_result


def append_assistant_message(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    content: str,
    parent_message_id: int | None = None,
) -> AiChatMessagePersistResult:
    """
    写入一条 assistant 消息。

    注意：
    1. assistant 消息当前不增加 current_round。
    2. 因为我们把“用户发起一次消息”视为本轮的触发点。
    """

    session = _get_required_active_session(
        db,
        session_id=session_id,
        student_id=student_id,
    )
    return _append_message(
        db,
        session=session,
        student_id=student_id,
        message_role="assistant",
        message_type=VISIBLE_MESSAGE_TYPE,
        content=content,
        content_json=None,
        is_visible=1,
        increase_round=False,
        parent_message_id=parent_message_id,
    )


def append_internal_state_message(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    content: str,
    content_json: dict[str, Any] | list | None,
) -> AiChatMessagePersistResult:
    """
    写入一条内部状态消息。

    作用：
    1. 保存 progress_extraction 等内部结构化结果。
    2. 这类消息默认对前端不可见，但能作为后续 Prompt 的上下文来源。
    """

    session = _get_required_active_session(
        db,
        session_id=session_id,
        student_id=student_id,
    )
    return _append_message(
        db,
        session=session,
        student_id=student_id,
        message_role="system",
        message_type=INTERNAL_STATE_MESSAGE_TYPE,
        content=content,
        content_json=content_json,
        is_visible=0,
        increase_round=False,
    )


def get_visible_messages(
    db: Session,
    *,
    session_id: str,
    limit: int = 20,
) -> list[AiChatVisibleMessage]:
    """
    读取最近若干条前端可见消息。

    用途：
    1. 作为 conversation_prompt 的直接上下文。
    2. 避免每次都把整段超长会话完整喂给模型。
    """

    safe_limit = max(1, min(limit, 100))
    stmt = (
        select(AiChatMessage)
        .where(AiChatMessage.session_id == session_id)
        .where(AiChatMessage.delete_flag == "1")
        .where(AiChatMessage.is_visible == 1)
        .order_by(AiChatMessage.sequence_no.desc())
        .limit(safe_limit)
    )
    rows = list(db.execute(stmt).scalars())
    rows.reverse()
    return [
        AiChatVisibleMessage(
            id=row.id,
            message_role=row.message_role,
            message_type=row.message_type,
            sequence_no=row.sequence_no,
            content=row.content,
            create_time=row.create_time,
        )
        for row in rows
    ]


def get_visible_messages_before_id(
    db: Session,
    *,
    session_id: str,
    before_id: int | None,
    limit: int,
) -> list[AiChatVisibleMessage]:
    """
    分页读取会话可见消息。

    分页规则：
    1. 按消息主键 id 倒序截取，再反转成正序返回。
    2. 这样前端追加历史消息时更容易处理。
    """

    safe_limit = max(1, min(limit, 100))
    stmt = (
        select(AiChatMessage)
        .where(AiChatMessage.session_id == session_id)
        .where(AiChatMessage.delete_flag == "1")
        .where(AiChatMessage.is_visible == 1)
    )
    if before_id is not None:
        stmt = stmt.where(AiChatMessage.id < before_id)

    stmt = stmt.order_by(AiChatMessage.id.desc()).limit(safe_limit)
    rows = list(db.execute(stmt).scalars())
    rows.reverse()
    return [
        AiChatVisibleMessage(
            id=row.id,
            message_role=row.message_role,
            message_type=row.message_type,
            sequence_no=row.sequence_no,
            content=row.content,
            create_time=row.create_time,
        )
        for row in rows
    ]


def get_all_visible_messages(
    db: Session,
    *,
    session_id: str,
) -> list[AiChatVisibleMessage]:
    """
    读取某个会话的全部可见消息。

    用途：
    1. extraction_prompt 需要完整对话时，直接走这个函数。
    2. 和 get_visible_messages 的区别是这里不做 limit 截断。
    """

    stmt = (
        select(AiChatMessage)
        .where(AiChatMessage.session_id == session_id)
        .where(AiChatMessage.delete_flag == "1")
        .where(AiChatMessage.is_visible == 1)
        .order_by(AiChatMessage.sequence_no.asc())
    )
    rows = list(db.execute(stmt).scalars())
    return [
        AiChatVisibleMessage(
            id=row.id,
            message_role=row.message_role,
            message_type=row.message_type,
            sequence_no=row.sequence_no,
            content=row.content,
            create_time=row.create_time,
        )
        for row in rows
    ]


def get_latest_progress_state(
    db: Session,
    *,
    session_id: str,
) -> dict[str, Any] | None:
    """
    读取最新一条 progress_extraction 内部状态。

    当前约定：
    1. progress_extraction 的完整结果保存在 internal_state 消息的 content_json 中。
    2. content 固定为 progress_extraction_result。
    3. 不能简单取“最后一条 internal_state”，因为后面还会继续写 extraction_result、scoring_result。
    """

    stmt = (
        select(AiChatMessage)
        .where(AiChatMessage.session_id == session_id)
        .where(AiChatMessage.delete_flag == "1")
        .where(AiChatMessage.message_role == "system")
        .where(AiChatMessage.message_type == INTERNAL_STATE_MESSAGE_TYPE)
        .where(AiChatMessage.content == "progress_extraction_result")
        .order_by(AiChatMessage.sequence_no.desc())
        .limit(1)
    )
    row = db.execute(stmt).scalar_one_or_none()
    if row is None or not row.content_json:
        return None
    if isinstance(row.content_json, dict):
        return row.content_json
    return None


def update_session_progress(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    progress_result: dict[str, Any],
) -> AiChatSession:
    """
    根据 progress_extraction 结果更新会话快照。

    当前会写入：
    1. collected_slots_json
    2. missing_dimensions_json
    3. current_stage

    说明：
    1. 这里不把完整 progress JSON 平铺到 session 表，是为了避免 session 表字段膨胀。
    2. 完整的 progress 结果仍保存在 internal_state 消息中。
    """

    session = _get_required_active_session(
        db,
        session_id=session_id,
        student_id=student_id,
    )
    session.collected_slots_json = progress_result.get("collected_slots_patch") or {}
    session.missing_dimensions_json = progress_result.get("missing_dimensions") or []

    # 中文注释：
    # 新的流程里，stop_ready=true 不再表示“系统马上自动开始 extraction”，
    # 而是表示“信息已经足够，前端可以展示‘立即建档 / 更新六维图’按钮”。
    # 因此这里把阶段设置为 build_ready，而不是旧版本里的 extraction。
    if progress_result.get("stop_ready") is True:
        session.current_stage = "build_ready"
    else:
        session.current_stage = DEFAULT_STAGE

    session.updated_by = student_id
    db.commit()
    db.refresh(session)
    return session


def update_session_stage(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    current_stage: str,
    remark: str | None = None,
    session_status: str = DEFAULT_SESSION_STATUS,
) -> AiChatSession:
    """
    仅更新会话阶段与备注。

    这个函数专门给“手动生成 / 更新六维图”的后台异步任务使用。
    设计意图：
    1. 点击“立即建档 / 更新六维图”后，要立刻把会话切到 extraction / scoring，
       这样前端才能基于 session detail 做轮询和 loading 展示。
    2. 后台任务如果失败，也需要把阶段切到 failed，并把异常摘要写到 remark，
       方便前端在不读取长堆栈的前提下给用户稳定提示。
    3. 这个函数只负责 AI 会话元数据，不负责正式业务表入库。
    """

    session = _get_required_active_session(
        db,
        session_id=session_id,
        student_id=student_id,
    )
    session.current_stage = current_stage
    session.session_status = session_status
    session.remark = (remark or None)
    session.updated_by = student_id
    db.commit()
    db.refresh(session)
    return session


def save_profile_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    profile_json: dict[str, Any],
    radar_scores_json: dict[str, Any] | None,
    summary_text: str | None,
    result_status: str,
    db_payload_json: dict[str, Any] | None = None,
    save_error_message: str | None = None,
    session_stage: str = "build_ready",
    session_remark: str | None = None,
) -> AiChatProfileResultPersistResult:
    """
    保存最终建档结果到 ai_chat_profile_results。

    当前策略：
    1. 一个 session_id 对应一条有效结果记录。
    2. 如果该 session 已经有结果，就更新；否则新建。
    3. 同时把 ai_chat_sessions 的阶段和状态一起更新。
    """

    with log_timed_step(
        logger,
        flow_name="AI建档结果",
        step_name="保存六维图结果",
        student_id=student_id,
        session_id=session_id,
    ):
        session = _get_required_active_session(
            db,
            session_id=session_id,
            student_id=student_id,
        )

        existing = _get_profile_result_by_session_id(
            db,
            session_id=session_id,
        )
        if existing is None:
            profile_result = AiChatProfileResult(
                session_id=session_id,
                student_id=student_id,
                biz_domain=biz_domain,
                result_status=result_status,
                profile_json=profile_json,
                radar_scores_json=radar_scores_json,
                summary_text=summary_text,
                db_payload_json=db_payload_json,
                save_error_message=save_error_message,
            )
            db.add(profile_result)
            db.flush()
        else:
            profile_result = existing
            profile_result.profile_json = profile_json
            profile_result.radar_scores_json = radar_scores_json
            profile_result.summary_text = summary_text
            profile_result.db_payload_json = db_payload_json
            profile_result.result_status = result_status
            profile_result.save_error_message = save_error_message

        session.final_profile_id = profile_result.id
        session.current_stage = session_stage
        session.session_status = DEFAULT_SESSION_STATUS
        session.completed_at = None
        session.remark = session_remark or None
        session.updated_by = student_id

        db.commit()
        db.refresh(profile_result)
        db.refresh(session)

        return AiChatProfileResultPersistResult(
            profile_result_id=profile_result.id,
            session_id=session_id,
            student_id=student_id,
            result_status=profile_result.result_status,
        )


def update_profile_result_status(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    result_status: str,
    save_error_message: str | None = None,
    session_stage: str = "build_ready",
    session_remark: str | None = None,
) -> AiChatProfileResultPersistResult:
    """更新已存在的六维图结果状态，并同步刷新会话阶段。"""

    with log_timed_step(
        logger,
        flow_name="AI建档结果",
        step_name="更新六维图结果状态",
        student_id=student_id,
        session_id=session_id,
    ):
        session = _get_required_active_session(
            db,
            session_id=session_id,
            student_id=student_id,
        )
        profile_result = _get_profile_result_by_session_id(
            db,
            session_id=session_id,
        )
        if profile_result is None:
            raise ValueError("当前会话还没有可更新的六维图结果")

        profile_result.result_status = result_status
        profile_result.save_error_message = save_error_message

        session.current_stage = session_stage
        session.session_status = DEFAULT_SESSION_STATUS
        session.completed_at = None
        session.remark = session_remark or None
        session.updated_by = student_id

        db.commit()
        db.refresh(profile_result)
        db.refresh(session)

        return AiChatProfileResultPersistResult(
            profile_result_id=profile_result.id,
            session_id=session_id,
            student_id=student_id,
            result_status=profile_result.result_status,
        )


def get_active_session_by_student_and_domain(
    db: Session,
    *,
    student_id: str,
    biz_domain: str,
) -> AiChatSessionQueryResult | None:
    """
    查询某个学生在指定业务域下当前 active 会话。

    用途：
    1. 前端页面进入时，先判断是否已有进行中的会话。
    2. 避免用户刷新页面后丢失当前会话上下文。
    """

    stmt = (
        select(AiChatSession)
        .where(AiChatSession.student_id == student_id)
        .where(AiChatSession.biz_domain == biz_domain)
        .where(AiChatSession.session_status == "active")
        .where(AiChatSession.delete_flag == "1")
        .order_by(AiChatSession.update_time.desc())
        .limit(1)
    )
    session = db.execute(stmt).scalar_one_or_none()
    if session is None:
        return None
    return _to_session_query_result(session)


def get_session_detail(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> AiChatSessionQueryResult | None:
    """
    查询某个 session 的详情。

    限制：
    1. 必须要求 session 归属当前 student_id。
    2. 避免用户通过拼 session_id 越权查看他人会话。
    """

    session = _get_active_session_by_session_id(
        db,
        session_id=session_id,
    )
    if session is None:
        return None
    _ensure_session_belongs_to_student(
        session=session,
        student_id=student_id,
    )
    return _to_session_query_result(session)


def get_profile_result_detail(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> AiChatProfileResultQueryResult | None:
    """
    查询某个 session 对应的最终建档结果。

    用途：
    1. 前端结果页展示。
    2. 后续 REST 接口查询 radar / summary / result_status。
    """

    profile_result = _get_profile_result_by_session_id(
        db,
        session_id=session_id,
    )
    if profile_result is None:
        return None
    if profile_result.student_id != student_id:
        raise ValueError("当前结果不属于该 student_id")

    return AiChatProfileResultQueryResult(
        profile_result_id=profile_result.id,
        session_id=profile_result.session_id,
        student_id=profile_result.student_id,
        result_status=profile_result.result_status,
        profile_json=profile_result.profile_json,
        radar_scores_json=profile_result.radar_scores_json,
        summary_text=profile_result.summary_text,
        db_payload_json=profile_result.db_payload_json,
        save_error_message=profile_result.save_error_message,
        create_time=profile_result.create_time,
        update_time=profile_result.update_time,
    )


def _append_message(
    db: Session,
    *,
    session: AiChatSession,
    student_id: str,
    message_role: str,
    message_type: str,
    content: str,
    content_json: dict[str, Any] | list | None,
    is_visible: int,
    increase_round: bool,
    parent_message_id: int | None = None,
) -> AiChatMessagePersistResult:
    """
    统一追加一条消息。

    这一层做统一处理的原因：
    1. user / assistant / internal_state 三种消息都需要 sequence_no。
    2. 都需要同步更新会话最后消息时间。
    3. 都需要统一 commit / refresh，避免重复代码。
    """

    trimmed_content = content.strip()
    if not trimmed_content:
        raise ValueError("消息内容不能为空")

    next_sequence_no = _get_next_sequence_no(
        db,
        session_id=session.session_id,
    )
    now = datetime.utcnow()

    message = AiChatMessage(
        session_id=session.session_id,
        student_id=student_id,
        message_role=message_role,
        message_type=message_type,
        sequence_no=next_sequence_no,
        parent_message_id=parent_message_id,
        content=trimmed_content,
        content_json=content_json,
        is_visible=is_visible,
    )
    db.add(message)

    if increase_round:
        session.current_round += 1
    session.last_message_at = now
    session.updated_by = student_id

    db.commit()
    db.refresh(message)
    db.refresh(session)

    return AiChatMessagePersistResult(
        session_id=session.session_id,
        student_id=student_id,
        message_id=message.id,
        sequence_no=message.sequence_no,
        current_round=session.current_round,
        last_message_at=session.last_message_at or now,
    )


def _get_profile_result_by_session_id(
    db: Session,
    *,
    session_id: str,
) -> AiChatProfileResult | None:
    """
    按 session_id 查找一条有效的 profile result。

    说明：
    1. 当前约定同一 session 只保留一条有效结果。
    2. 如果未来要支持多版结果回溯，再把这里改成版本化结构。
    """

    stmt = (
        select(AiChatProfileResult)
        .where(AiChatProfileResult.session_id == session_id)
        .where(AiChatProfileResult.delete_flag == "1")
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()


def _to_session_query_result(session: AiChatSession) -> AiChatSessionQueryResult:
    """
    把 ORM 会话对象转换成对外查询结构。

    这样接口层就不需要知道 ORM 字段细节。
    """

    return AiChatSessionQueryResult(
        session_id=session.session_id,
        student_id=session.student_id,
        biz_domain=session.biz_domain,
        session_status=session.session_status,
        current_stage=session.current_stage,
        current_round=session.current_round,
        missing_dimensions_json=session.missing_dimensions_json,
        last_message_at=session.last_message_at,
        completed_at=session.completed_at,
        final_profile_id=session.final_profile_id,
        remark=session.remark,
    )


def _get_active_session_by_session_id(
    db: Session,
    *,
    session_id: str,
) -> AiChatSession | None:
    """按 session_id 查找一条有效会话。"""

    stmt = (
        select(AiChatSession)
        .where(AiChatSession.session_id == session_id)
        .where(AiChatSession.delete_flag == "1")
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()


def _get_required_active_session(
    db: Session,
    *,
    session_id: str,
    student_id: str,
) -> AiChatSession:
    """获取一条必须存在且归属正确的会话。"""

    session = _get_active_session_by_session_id(
        db,
        session_id=session_id,
    )
    if session is None:
        raise ValueError("会话不存在或已被删除")
    _ensure_session_belongs_to_student(
        session=session,
        student_id=student_id,
    )
    return session


def _ensure_session_belongs_to_student(
    *,
    session: AiChatSession,
    student_id: str,
) -> None:
    """校验会话归属，防止不同学生串用同一 session。"""

    if session.student_id != student_id:
        raise ValueError("当前会话不属于该 student_id")


def _create_session(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> AiChatSession:
    """创建一条新的 AI 会话。"""

    now = datetime.utcnow()
    session = AiChatSession(
        session_id=session_id,
        student_id=student_id,
        biz_domain=biz_domain,
        session_status=DEFAULT_SESSION_STATUS,
        current_stage=DEFAULT_STAGE,
        current_round=0,
        collected_slots_json={},
        missing_dimensions_json=[],
        last_message_at=now,
        created_by=student_id,
        updated_by=student_id,
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return session


def _get_next_sequence_no(
    db: Session,
    *,
    session_id: str,
) -> int:
    """计算会话内下一条 sequence_no。"""

    stmt = (
        select(func.max(AiChatMessage.sequence_no))
        .where(AiChatMessage.session_id == session_id)
        .where(AiChatMessage.delete_flag == "1")
    )
    current_max = db.execute(stmt).scalar_one()
    return int(current_max or 0) + 1
