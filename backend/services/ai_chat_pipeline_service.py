"""
AI 对话链路编排服务。

这一层负责把多个 Prompt 串成一条完整业务链路：
1. conversation_prompt：生成 assistant 回复
2. progress_extraction_prompt：判断当前进度和是否可以停止
3. 若 stop_ready=true：
   - extraction_prompt：生成最终结构化档案
   - code_resolution：回填国家 / 专业 / 学校码值
   - scoring_prompt：生成六维图分数和中文总结
   - ai_chat_profile_results：保存最终结果

这里和 WebSocket 路由层的职责边界很明确：
1. 路由层只关心“事件收发”和“流式 token 推送”
2. 本服务只关心“Prompt 调用顺序”和“后半段业务编排”
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from backend.services.ai_chat_service import append_assistant_message
from backend.services.ai_chat_service import append_internal_state_message
from backend.services.ai_chat_service import get_all_visible_messages
from backend.services.ai_chat_service import get_latest_progress_state
from backend.services.ai_chat_service import get_visible_messages
from backend.services.ai_chat_service import save_profile_result
from backend.services.ai_chat_service import update_session_progress
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot
from backend.services.code_resolution_service import apply_code_resolution_to_payload


CONVERSATION_PROMPT_KEY = "student_profile_build.conversation"
PROGRESS_PROMPT_KEY = "student_profile_build.progress_extraction"
EXTRACTION_PROMPT_KEY = "student_profile_build.extraction"
SCORING_PROMPT_KEY = "student_profile_build.scoring"


@dataclass(slots=True)
class ConversationPipelineResult:
    """
    传统“非流式整轮执行”链路的结果对象。

    这个对象主要给旧的整轮执行入口使用：
    1. 先跑 conversation_prompt
    2. 再直接继续跑 progress / extraction / scoring
    """

    assistant_message_id: int
    assistant_sequence_no: int
    assistant_content: str
    current_round: int
    progress_result: dict[str, Any]
    final_profile_generated: bool
    profile_result_id: int | None = None
    profile_json: dict[str, Any] | None = None
    radar_scores_json: dict[str, Any] | None = None
    summary_text: str | None = None
    result_status: str | None = None
    save_error_message: str | None = None


@dataclass(slots=True)
class PostAssistantPipelineResult:
    """
    assistant 回复已经生成并落库后的后置处理结果。

    这个结果对象专门给 WebSocket 流式路由使用，作用是：
    1. 让“对话生成”和“后续 progress / extraction / scoring”两个阶段解耦。
    2. 当 conversation 走流式输出时，路由层可以先实时推 token，
       等 assistant 全文结束后，再调用这里继续跑进度更新。
    """

    progress_result: dict[str, Any]
    final_profile_generated: bool
    profile_result_id: int | None = None
    profile_json: dict[str, Any] | None = None
    radar_scores_json: dict[str, Any] | None = None
    summary_text: str | None = None
    result_status: str | None = None
    save_error_message: str | None = None


@dataclass(slots=True)
class FinalProfileGenerationResult:
    """
    学生主动点击“立即建档 / 更新六维图”后的最终结果对象。

    设计意图：
    1. 把“对话进度更新”和“最终建档生成”拆成两个显式阶段
    2. 后端只有在学生明确触发时，才运行 extraction / scoring / 正式入库
    """

    profile_result_id: int
    profile_json: dict[str, Any]
    radar_scores_json: dict[str, Any] | None
    summary_text: str | None
    result_status: str
    save_error_message: str | None


def run_conversation_and_progress_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> ConversationPipelineResult:
    """
    执行一轮完整的非流式 AI 链路。

    这个入口主要保留给：
    1. 后台脚本
    2. 临时非流式调试
    3. 不需要逐 token 推送的调用场景
    """

    conversation_context = build_conversation_context(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    conversation_result = execute_prompt_with_context(
        db,
        prompt_key=CONVERSATION_PROMPT_KEY,
        context=conversation_context,
    )

    assistant_persist_result = append_assistant_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content=conversation_result.content,
    )

    post_assistant_result = run_post_assistant_pipeline(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
    )

    return ConversationPipelineResult(
        assistant_message_id=assistant_persist_result.message_id,
        assistant_sequence_no=assistant_persist_result.sequence_no,
        assistant_content=conversation_result.content,
        current_round=assistant_persist_result.current_round,
        progress_result=post_assistant_result.progress_result,
        final_profile_generated=post_assistant_result.final_profile_generated,
        profile_result_id=post_assistant_result.profile_result_id,
        profile_json=post_assistant_result.profile_json,
        radar_scores_json=post_assistant_result.radar_scores_json,
        summary_text=post_assistant_result.summary_text,
        result_status=post_assistant_result.result_status,
        save_error_message=post_assistant_result.save_error_message,
    )


def run_post_assistant_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> PostAssistantPipelineResult:
    """
    在 assistant 回复已经生成并落库之后，继续执行后半段 AI 链路。

    处理顺序：
    1. 运行 progress_extraction_prompt，更新当前会话进度。
    2. 无论 stop_ready 是否为 true，这一轮都只做到“更新进度”。

    当前新的业务口径：
    1. `stop_ready=true` 只表示“信息已足够生成六维图”
    2. 真正的六维图生成与正式建档，必须由学生点击按钮主动触发
    """

    progress_context = _build_progress_extraction_context(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    progress_runtime_result = execute_prompt_with_context(
        db,
        prompt_key=PROGRESS_PROMPT_KEY,
        context=progress_context,
    )
    progress_json = _parse_json_object(
        raw_text=progress_runtime_result.content,
        scene_name="progress_extraction",
    )

    append_internal_state_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content="progress_extraction_result",
        content_json=progress_json,
    )
    update_session_progress(
        db,
        student_id=student_id,
        session_id=session_id,
        progress_result=progress_json,
    )

    return PostAssistantPipelineResult(
        progress_result=progress_json,
        final_profile_generated=False,
    )


def run_manual_profile_generation(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> FinalProfileGenerationResult:
    """
    在学生主动点击按钮后，显式执行最终建档链路。

    调用时机：
    1. 前端已经通过 progress_updated 知道 `stop_ready=true`
    2. 学生选择“立即建档”或“更新六维图”

    约束：
    1. 如果当前还没有最新 progress 结果，不能直接生成
    2. 如果最新 progress 里 `stop_ready` 仍不是 true，也不能直接生成
    """

    latest_progress_result = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    if not isinstance(latest_progress_result, dict):
        raise ValueError("当前还没有可用的建档进度，请先继续对话补充信息。")

    if latest_progress_result.get("stop_ready") is not True:
        raise ValueError("当前信息还不足以生成六维图，请继续补充关键信息。")

    final_result = _run_final_profile_pipeline(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
        latest_progress_result=latest_progress_result,
    )
    return FinalProfileGenerationResult(
        profile_result_id=final_result["profile_result_id"],
        profile_json=final_result["profile_json"],
        radar_scores_json=final_result["radar_scores_json"],
        summary_text=final_result["summary_text"],
        result_status=final_result["result_status"],
        save_error_message=final_result["save_error_message"],
    )


def _run_final_profile_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    latest_progress_result: dict[str, Any],
) -> dict[str, Any]:
    """
    执行最终建档链路。

    处理顺序：
    1. 调 extraction_prompt
    2. 使用 progress 中保留的原始文本执行 code_resolution
    3. 调 scoring_prompt
    4. 构建 db_payload 并尝试正式入业务表
    5. 无论成功失败，都保存 ai_chat_profile_results，方便审计和排查
    """

    extraction_context = _build_extraction_context(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    extraction_runtime_result = execute_prompt_with_context(
        db,
        prompt_key=EXTRACTION_PROMPT_KEY,
        context=extraction_context,
    )
    extraction_json = _parse_json_object(
        raw_text=extraction_runtime_result.content,
        scene_name="extraction",
    )

    append_internal_state_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content="extraction_result",
        content_json=extraction_json,
    )

    basic_info_progress = latest_progress_result.get("basic_info_progress") or {}
    target_country_text = _extract_progress_text(
        basic_info_progress,
        "target_country",
    )
    major_text = _extract_progress_text(
        basic_info_progress,
        "major_interest",
    )

    code_resolution_results = apply_code_resolution_to_payload(
        db,
        extraction_json,
        target_country_text=target_country_text,
        major_text=major_text,
        school_name_text=(extraction_json.get("student_academic", {}) or {}).get("school_name"),
    )

    append_internal_state_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content="code_resolution_result",
        content_json={
            key: {
                "raw_text": value.raw_text,
                "normalized_text": value.normalized_text,
                "mapped_code": value.mapped_code,
                "mapping_status": value.mapping_status,
                "matched_source": value.matched_source,
                "matched_label": value.matched_label,
            }
            for key, value in code_resolution_results.items()
        },
    )

    scoring_context = {
        "student_id": student_id,
        "session_id": session_id,
        "profile_json": extraction_json,
    }
    scoring_runtime_result = execute_prompt_with_context(
        db,
        prompt_key=SCORING_PROMPT_KEY,
        context=scoring_context,
    )
    scoring_json = _parse_json_object(
        raw_text=scoring_runtime_result.content,
        scene_name="scoring",
    )

    append_internal_state_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content="scoring_result",
        content_json=scoring_json,
    )

    db_payload = build_db_payload_from_profile_json(
        extraction_json,
        student_id=student_id,
    )

    result_status = "saved"
    save_error_message: str | None = None
    try:
        persist_business_profile_snapshot(
            db,
            db_payload=db_payload,
            student_id=student_id,
        )
    except Exception as exc:
        result_status = "failed"
        save_error_message = str(exc)

    saved_result = save_profile_result(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
        profile_json=extraction_json,
        radar_scores_json=scoring_json.get("radar_scores_json"),
        summary_text=scoring_json.get("summary_text"),
        result_status=result_status,
        db_payload_json=db_payload,
        save_error_message=save_error_message,
    )

    return {
        "profile_result_id": saved_result.profile_result_id,
        "profile_json": extraction_json,
        "radar_scores_json": scoring_json.get("radar_scores_json"),
        "summary_text": scoring_json.get("summary_text"),
        "result_status": result_status,
        "save_error_message": save_error_message,
    }


def build_conversation_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    """
    构建 conversation_prompt 的上下文。

    当前传入：
    1. 最近 20 条可见消息
    2. 最新 progress 快照
    """

    visible_messages = get_visible_messages(
        db,
        session_id=session_id,
        limit=20,
    )
    current_progress_json = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    return {
        "student_id": student_id,
        "session_id": session_id,
        "conversation_history": [
            {
                "message_role": item.message_role,
                "message_type": item.message_type,
                "sequence_no": item.sequence_no,
                "content": item.content,
                "create_time": item.create_time.isoformat(),
            }
            for item in visible_messages
        ],
        "current_progress_json": current_progress_json or {},
    }


def _build_progress_extraction_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    """构建 progress_extraction_prompt 的上下文。"""

    visible_messages = get_visible_messages(
        db,
        session_id=session_id,
        limit=20,
    )
    current_progress_json = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    return {
        "student_id": student_id,
        "session_id": session_id,
        "latest_round_messages": [
            {
                "message_role": item.message_role,
                "sequence_no": item.sequence_no,
                "content": item.content,
                "create_time": item.create_time.isoformat(),
            }
            for item in visible_messages
        ],
        "current_progress_json": current_progress_json or {},
    }


def _build_extraction_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    """
    构建 extraction_prompt 的上下文。

    extraction 是最终建档阶段，因此这里会传完整可见对话历史。
    """

    full_visible_messages = get_all_visible_messages(
        db,
        session_id=session_id,
    )
    current_progress_json = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    return {
        "student_id": student_id,
        "session_id": session_id,
        "full_conversation_history": [
            {
                "message_role": item.message_role,
                "message_type": item.message_type,
                "sequence_no": item.sequence_no,
                "content": item.content,
                "create_time": item.create_time.isoformat(),
            }
            for item in full_visible_messages
        ],
        "current_progress_json": current_progress_json or {},
    }


def _parse_json_object(
    *,
    raw_text: str,
    scene_name: str,
) -> dict[str, Any]:
    """
    解析模型返回的 JSON 对象。

    兜底能力：
    1. 去掉 ```json 代码块包裹
    2. 严格要求最终结果必须是 JSON 对象
    """

    cleaned = raw_text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```JSON")
        cleaned = cleaned.removeprefix("```").removesuffix("```").strip()

    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise ValueError(f"{scene_name} 返回的内容不是合法 JSON：{raw_text}") from exc

    if not isinstance(parsed, dict):
        raise ValueError(f"{scene_name} 返回结果必须是 JSON 对象")
    return parsed


def _extract_progress_text(
    basic_info_progress: dict[str, Any],
    field_name: str,
) -> str | None:
    """
    从 progress_extraction 结果里提取基础字段原始文本。

    这里的主要用途是给 code_resolution 提供国家 / 专业的原始文本。
    """

    field_value = basic_info_progress.get(field_name)
    if not isinstance(field_value, dict):
        return None
    value = field_value.get("value")
    if isinstance(value, str):
        return value.strip() or None
    return None
