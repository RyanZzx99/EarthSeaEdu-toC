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
import logging
from typing import Any

from sqlalchemy.orm import Session

from backend.config.db_conf import SessionLocal
from backend.services.ai_chat_service import append_assistant_message
from backend.services.ai_chat_service import append_internal_state_message
from backend.services.ai_chat_service import get_all_visible_messages
from backend.services.ai_chat_service import get_latest_progress_state
from backend.services.ai_chat_service import get_profile_result_detail
from backend.services.ai_chat_service import get_session_detail
from backend.services.ai_chat_service import get_visible_messages
from backend.services.ai_chat_service import save_profile_result
from backend.services.ai_chat_service import update_profile_result_status
from backend.services.ai_chat_service import update_session_stage
from backend.services.ai_chat_service import update_session_progress
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot
from backend.services.code_resolution_service import apply_code_resolution_to_payload
from backend.services.code_resolution_service import apply_subject_code_resolution_to_payload
from backend.utils.flow_logging import log_flow_info
from backend.utils.flow_logging import log_timed_step


CONVERSATION_PROMPT_KEY = "student_profile_build.conversation"
PROGRESS_PROMPT_KEY = "student_profile_build.progress_extraction"
EXTRACTION_PROMPT_KEY = "student_profile_build.extraction"
SCORING_PROMPT_KEY = "student_profile_build.scoring"
PROFILE_SAVING_STAGE = "profile_saving"

NEGATIVE_GUARD_DIMENSION_KEYWORDS: dict[str, tuple[str, ...]] = {
    "language": (
        "语言",
        "雅思",
        "托福",
        "toefl",
        "ielts",
        "多邻国",
        "duolingo",
        "pte",
        "languagecert",
    ),
    "standardized": (
        "标化",
        "sat",
        "act",
        "gre",
        "gmat",
    ),
    "competition": (
        "竞赛",
        "比赛",
        "奖项",
        "amc",
        "bpho",
        "usaco",
        "nec",
    ),
    "activity": (
        "活动",
        "社团",
        "学生会",
        "班干部",
        "班级职务",
        "志愿",
        "领导力",
    ),
    "project": (
        "项目",
        "实践",
        "科研",
        "研究",
        "课题",
        "实习",
        "作品",
        "编程",
        "小实验",
        "制作",
    ),
}
NEGATIVE_REPLY_EXACT_TEXTS = {
    "没有",
    "没有过",
    "没有做过",
    "没做过",
    "没参加过",
    "没经历",
    "没项目",
    "没活动",
    "没竞赛",
    "暂时没有",
    "还没有",
    "都没有",
    "完全没有",
    "暂无",
    "无",
}
NEGATIVE_REPLY_PREFIXES = (
    "没有",
    "没",
    "暂时没有",
    "还没有",
    "都没有",
    "完全没有",
)
NEGATIVE_REPLY_BLOCK_MARKERS = (
    "但是",
    "不过",
    "但我",
    "但有",
    "不过我",
    "不过有",
    "自己做过",
    "做过一点",
    "参加过",
    "有一个",
    "有做过",
)


logger = logging.getLogger(__name__)


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


def run_manual_profile_generation_background(
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> None:
    """
    在后台线程里执行“生成 / 更新六维图”整条链路。

    设计意图：
    1. 前端点击“立即建档 / 更新六维图”后，接口应立即返回，不同步阻塞等待。
    2. 真正耗时的 extraction / scoring / 正式业务表入库放到后台任务里执行。
    3. 无论任务成功还是失败，都要把 ai_chat_sessions.current_stage 回写到可见状态，
       否则前端轮询会一直卡在 loading。
    """

    db = SessionLocal()
    try:
        run_manual_profile_generation_v2(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
        # 说明：
        # 1. 正常走完整生成链路时，save_profile_result() 内部已经会把阶段切回 build_ready。
        # 2. 但如果本次命中了缓存复用路径，则不会再走 save_profile_result()，
        #    因此这里再补一次兜底，把阶段显式切回 build_ready。
        update_session_stage(
            db,
            student_id=student_id,
            session_id=session_id,
            current_stage="build_ready",
            remark=None,
        )
    except Exception as exc:
        # 这里不把长异常堆栈直接暴露给前端，只把摘要写到 session.remark。
        # 前端轮询到 failed 后，可展示一条稳定、可读的错误提示。
        update_session_stage(
            db,
            student_id=student_id,
            session_id=session_id,
            current_stage="failed",
            remark=str(exc)[:500],
        )
    finally:
        db.close()


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

    # 中文注释：
    # 先更新 progress，再生成 assistant。
    # 这样 conversation_prompt 读取到的就是用户刚回答完后的最新状态，
    # 可以避免“用户明明已经回答了，但 assistant 还在重复追问同一槽位”。
    progress_result = run_progress_update_pipeline(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
    )
    conversation_context = build_conversation_context(
        db,
        student_id=student_id,
        session_id=session_id,
        current_progress_json=progress_result,
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

    return ConversationPipelineResult(
        assistant_message_id=assistant_persist_result.message_id,
        assistant_sequence_no=assistant_persist_result.sequence_no,
        assistant_content=conversation_result.content,
        current_round=assistant_persist_result.current_round,
        progress_result=progress_result,
        final_profile_generated=False,
        profile_result_id=None,
        profile_json=None,
        radar_scores_json=None,
        summary_text=None,
        result_status=None,
        save_error_message=None,
    )


def run_progress_update_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str | None = None,
) -> dict[str, Any]:
    """
    在 assistant 回复已经生成并落库之后，继续执行后半段 AI 链路。

    处理顺序：
    1. 运行 progress_extraction_prompt，更新当前会话进度。
    2. 无论 stop_ready 是否为 true，这一轮都只做到“更新进度”。

    当前新的业务口径：
    1. `stop_ready=true` 只表示“信息已足够生成六维图”
    2. 真正的六维图生成与正式建档，必须由学生点击按钮主动触发
    """

    with log_timed_step(
        logger,
        flow_name="AI对话",
        step_name="更新建档进度",
        student_id=student_id,
        session_id=session_id,
    ):
        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="构建进度提取上下文",
            student_id=student_id,
            session_id=session_id,
        ):
            progress_context = _build_progress_extraction_context(
                db,
                student_id=student_id,
                session_id=session_id,
            )
        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="执行进度提取模型",
            student_id=student_id,
            session_id=session_id,
        ):
            progress_runtime_result = execute_prompt_with_context(
                db,
                prompt_key=PROGRESS_PROMPT_KEY,
                context=progress_context,
            )
        with log_timed_step(
            logger,
            flow_name="AI对话",
            step_name="解析并保存进度结果",
            student_id=student_id,
            session_id=session_id,
        ):
            progress_json = _parse_json_object(
                raw_text=progress_runtime_result.content,
                scene_name="progress_extraction",
            )
            progress_json = _apply_recent_turn_progress_guards(
                progress_result=progress_json,
                progress_context=progress_context,
                student_id=student_id,
                session_id=session_id,
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

        return progress_json


def run_post_assistant_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> PostAssistantPipelineResult:
    """
    在 assistant 回复已经生成并落库之后，统一读取当前轮的最新 progress 结果。

    中文注释：
    1. 新流程里，progress_extraction 已经前置到用户消息入库之后执行。
    2. 因此 assistant 结束后默认直接复用最新 progress 快照，不再重复跑同一轮提取。
    3. 这里只保留一个兜底：如果库里还没有 progress_extraction_result，
       再补跑一次 progress_update，避免上层拿不到进度结果。
    """

    progress_json = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    if not isinstance(progress_json, dict):
        progress_json = run_progress_update_pipeline(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
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

    cached_result = _try_reuse_existing_profile_result(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if cached_result is not None:
        return cached_result

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


def run_manual_profile_generation_v2(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> FinalProfileGenerationResult:
    """新版本手动生成流程：六维图结果先返回，正式业务表入库后置。"""

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="手动生成六维图",
        student_id=student_id,
        session_id=session_id,
    ):
        latest_progress_result = get_latest_progress_state(
            db,
            session_id=session_id,
        )
        if not isinstance(latest_progress_result, dict):
            raise ValueError("当前还没有可用的建档进度，请先继续对话补充信息。")

        if latest_progress_result.get("stop_ready") is not True:
            raise ValueError("当前信息还不足以生成六维图，请继续补充关键信息。")

        cached_result = _try_reuse_existing_profile_result(
            db,
            student_id=student_id,
            session_id=session_id,
        )
        if cached_result is not None:
            log_flow_info(
                logger,
                flow_name="AI建档",
                step_name="复用已有六维图结果",
                student_id=student_id,
                session_id=session_id,
                message="检测到当前会话没有新增对话，直接复用最近一次六维图结果。",
            )
            return cached_result

        final_result = _run_final_profile_pipeline_v3(
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


def _run_final_profile_pipeline_v2(
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

    # 走到这里说明 extraction 与码值映射已经完成，阶段切换到 scoring，
    # 方便前端在轮询时给出更准确的加载提示。
    update_session_stage(
        db,
        student_id=student_id,
        session_id=session_id,
        current_stage="scoring",
        remark=None,
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

    subject_code_resolution_results = apply_subject_code_resolution_to_payload(
        db,
        extraction_json,
    )
    subject_resolution_summary = _summarize_subject_code_resolution_results(
        subject_code_resolution_results
    )
    log_flow_info(
        logger,
        flow_name="AI建档",
        step_name="本地学术科目码值映射",
        student_id=student_id,
        session_id=session_id,
        message=(
            "本地科目码值映射完成："
            f"处理行数={subject_resolution_summary['total_rows']}；"
            f"按名称解析={subject_resolution_summary['resolved_by_name']}；"
            f"按名称纠错={subject_resolution_summary['corrected_by_name']}；"
            f"沿用已有码值={subject_resolution_summary['resolved_by_provided_code']}；"
            f"未解析={subject_resolution_summary['unresolved']}"
        ),
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


def _run_final_profile_pipeline_v3(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    latest_progress_result: dict[str, Any],
) -> dict[str, Any]:
    """新版本最终建档流程：先保存六维图结果，再继续正式业务表入库。"""

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="最终建档主流程",
        student_id=student_id,
        session_id=session_id,
    ):
        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="结构化提取",
            student_id=student_id,
            session_id=session_id,
        ):
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

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="保存结构化提取结果",
            student_id=student_id,
            session_id=session_id,
        ):
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

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="执行码值映射",
            student_id=student_id,
            session_id=session_id,
        ):
            code_resolution_results = apply_code_resolution_to_payload(
                db,
                extraction_json,
                target_country_text=target_country_text,
                major_text=major_text,
                school_name_text=(extraction_json.get("student_academic", {}) or {}).get("school_name"),
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="保存码值映射结果",
            student_id=student_id,
            session_id=session_id,
        ):
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

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="切换到六维评分阶段",
            student_id=student_id,
            session_id=session_id,
        ):
            update_session_stage(
                db,
                student_id=student_id,
                session_id=session_id,
                current_stage="scoring",
                remark=None,
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="执行六维评分",
            student_id=student_id,
            session_id=session_id,
        ):
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

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="保存六维评分结果",
            student_id=student_id,
            session_id=session_id,
        ):
            append_internal_state_message(
                db,
                student_id=student_id,
                session_id=session_id,
                content="scoring_result",
                content_json=scoring_json,
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="本地学术科目码值映射",
            student_id=student_id,
            session_id=session_id,
        ):
            subject_code_resolution_results = apply_subject_code_resolution_to_payload(
                db,
                extraction_json,
            )
            subject_resolution_summary = _summarize_subject_code_resolution_results(
                subject_code_resolution_results
            )
            log_flow_info(
                logger,
                flow_name="AI建档",
                step_name="本地学术科目码值映射",
                student_id=student_id,
                session_id=session_id,
                message=(
                    "本地科目码值映射完成："
                    f"处理行数={subject_resolution_summary['total_rows']}；"
                    f"按名称解析={subject_resolution_summary['resolved_by_name']}；"
                    f"按名称纠错={subject_resolution_summary['corrected_by_name']}；"
                    f"沿用已有码值={subject_resolution_summary['resolved_by_provided_code']}；"
                    f"未解析={subject_resolution_summary['unresolved']}"
                ),
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="构建正式入库载荷",
            student_id=student_id,
            session_id=session_id,
        ):
            db_payload = build_db_payload_from_profile_json(
                extraction_json,
                student_id=student_id,
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="提前保存六维图结果",
            student_id=student_id,
            session_id=session_id,
        ):
            saved_result = save_profile_result(
                db,
                student_id=student_id,
                session_id=session_id,
                biz_domain=biz_domain,
                profile_json=extraction_json,
                radar_scores_json=scoring_json.get("radar_scores_json"),
                summary_text=scoring_json.get("summary_text"),
                result_status="generated",
                db_payload_json=db_payload,
                save_error_message=None,
                session_stage=PROFILE_SAVING_STAGE,
                session_remark=None,
            )

        result_status = "saved"
        save_error_message: str | None = None
        try:
            with log_timed_step(
                logger,
                flow_name="AI建档",
                step_name="正式业务表入库",
                student_id=student_id,
                session_id=session_id,
            ):
                persist_business_profile_snapshot(
                    db,
                    db_payload=db_payload,
                    student_id=student_id,
                )
        except Exception as exc:
            result_status = "failed"
            save_error_message = str(exc)
            with log_timed_step(
                logger,
                flow_name="AI建档",
                step_name="回写建档失败状态",
                student_id=student_id,
                session_id=session_id,
            ):
                update_profile_result_status(
                    db,
                    student_id=student_id,
                    session_id=session_id,
                    result_status=result_status,
                    save_error_message=save_error_message,
                    session_stage="build_ready",
                    session_remark=save_error_message[:500],
                )
        else:
            with log_timed_step(
                logger,
                flow_name="AI建档",
                step_name="回写建档完成状态",
                student_id=student_id,
                session_id=session_id,
            ):
                update_profile_result_status(
                    db,
                    student_id=student_id,
                    session_id=session_id,
                    result_status=result_status,
                    save_error_message=None,
                    session_stage="build_ready",
                    session_remark=None,
                )

        return {
            "profile_result_id": saved_result.profile_result_id,
            "profile_json": extraction_json,
            "radar_scores_json": scoring_json.get("radar_scores_json"),
            "summary_text": scoring_json.get("summary_text"),
            "result_status": result_status,
            "save_error_message": save_error_message,
        }


def _try_reuse_existing_profile_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> FinalProfileGenerationResult | None:
    """
    判断当前是否可以直接复用上一版六维图结果。

    设计目的：
    1. 如果学生在没有新增对话内容的情况下重复点击“更新六维图”，
       没必要再次执行 extraction、scoring 和正式入库。
    2. 这类重复点击本质上是“查看最新结果”，更适合直接返回缓存结果。

    判定规则：
    1. 当前 session 已经存在一份有效的 profile result。
    2. 这份 result 至少包含 profile_json。
    3. session.last_message_at 没有晚于 result.update_time，说明上次生成后没有新增对话消息。
    """

    session_detail = get_session_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    profile_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )

    if session_detail is None or profile_result is None:
        return None
    if not isinstance(profile_result.profile_json, dict) or not profile_result.profile_json:
        return None
    if profile_result.result_status != "saved":
        # 中文注释：
        # 失败结果绝不能参与缓存复用。
        # 否则即使后端代码已经修复，用户再次点击“更新六维图”时，
        # 仍然可能直接拿到上一次失败快照里的旧错误信息，
        # 看起来就像“明明修了，为什么还是同一个报错”。
        return None

    last_message_at = session_detail.last_message_at
    result_updated_at = profile_result.update_time
    if last_message_at is not None and result_updated_at < last_message_at:
        return None

    return FinalProfileGenerationResult(
        profile_result_id=profile_result.profile_result_id,
        profile_json=profile_result.profile_json,
        radar_scores_json=profile_result.radar_scores_json,
        summary_text=profile_result.summary_text,
        result_status=profile_result.result_status,
        save_error_message=profile_result.save_error_message,
    )


def build_conversation_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    current_progress_json: dict[str, Any] | None = None,
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
    # 中文注释：
    # 这里允许调用方把“刚刚计算出的最新 progress”直接传进来。
    # 这样在同一轮里就不用再读库里的旧快照，避免 assistant 看到过期上下文。
    resolved_progress_json = current_progress_json
    if not isinstance(resolved_progress_json, dict):
        resolved_progress_json = get_latest_progress_state(
            db,
            session_id=session_id,
        )
    conversation_control = _build_conversation_control(
        current_progress_json=resolved_progress_json or {},
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
        "current_progress_json": resolved_progress_json or {},
        "conversation_control": conversation_control,
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


def _build_conversation_control(
    *,
    current_progress_json: dict[str, Any],
) -> dict[str, Any]:
    """
    构建 conversation_prompt 的显式控制信息。

    设计原因：
    1. 仅把原始 progress JSON 整包传给模型时，模型有时会“看到了，但没严格遵守”。
    2. 尤其在基础信息已经收集完成后，仍可能重复追问专业方向、国家、课程体系等。
    3. 因此这里额外整理一份更直接的控制信息，明确告诉模型：
       - 哪些基础字段已经确认
       - 哪些字段禁止重复追问
       - 下一轮优先追问哪一个维度
       - 当前仍缺哪些维度
    """

    basic_info_progress = current_progress_json.get("basic_info_progress") or {}
    missing_dimensions = _normalize_dimension_list(
        current_progress_json.get("missing_dimensions") or []
    )
    do_not_repeat_dimensions = _normalize_dimension_list(
        current_progress_json.get("negative_answered_dimensions") or []
    )
    next_question_focus = current_progress_json.get("next_question_focus")
    stop_ready = current_progress_json.get("stop_ready") is True

    if next_question_focus in do_not_repeat_dimensions:
        next_question_focus = _pick_next_question_focus(
            missing_dimensions,
            blocked_dimensions=do_not_repeat_dimensions,
        )

    if do_not_repeat_dimensions:
        missing_dimensions = [
            item for item in missing_dimensions if item not in do_not_repeat_dimensions
        ]

    confirmed_basic_values: dict[str, Any] = {}
    do_not_repeat_basic_fields: list[str] = []

    for field_name in ["current_grade", "target_country", "major_interest", "curriculum_system"]:
        field_value = basic_info_progress.get(field_name)
        if not isinstance(field_value, dict):
            continue
        if field_value.get("collected") is True:
            do_not_repeat_basic_fields.append(field_name)
            confirmed_basic_values[field_name] = field_value.get("value")

    instruction_parts: list[str] = []
    if do_not_repeat_basic_fields:
        instruction_parts.append(
            "以下基础信息已经确认，不要重复追问："
            + "、".join(do_not_repeat_basic_fields)
        )
    if do_not_repeat_dimensions:
        instruction_parts.append(
            "以下维度学生已经明确回答过“暂时没有”或“没有做过”，不要换一种问法继续追问："
            + "、".join(do_not_repeat_dimensions)
        )
    if stop_ready:
        instruction_parts.append(
            "当前信息已经足够生成六维图，本轮不要继续追问新的基础槽位；"
            "只需简短说明已经可以建档，如果学生还想补充，再自然引导他补弱项。"
        )
    elif next_question_focus:
        instruction_parts.append(
            f"下一轮优先围绕 {next_question_focus} 维度追问，不要偏回已收集完成的基础信息。"
        )
    elif missing_dimensions:
        instruction_parts.append(
            "优先追问仍缺失的维度："
            + "、".join(str(item) for item in missing_dimensions)
        )

    return {
        "confirmed_basic_values": confirmed_basic_values,
        "do_not_repeat_basic_fields": do_not_repeat_basic_fields,
        "do_not_repeat_dimensions": do_not_repeat_dimensions,
        "missing_dimensions": missing_dimensions,
        "next_question_focus": next_question_focus,
        "stop_ready": stop_ready,
        "instruction_text": " ".join(instruction_parts) if instruction_parts else None,
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


def _summarize_subject_code_resolution_results(
    resolution_results: dict[str, list[dict[str, Any]]],
) -> dict[str, int]:
    summary = {
        "total_rows": 0,
        "resolved_by_name": 0,
        "corrected_by_name": 0,
        "resolved_by_provided_code": 0,
        "unresolved": 0,
    }

    for table_rows in resolution_results.values():
        for item in table_rows:
            status = item.get("resolution_status")
            summary["total_rows"] += 1
            if status == "resolved_by_name":
                summary["resolved_by_name"] += 1
            elif status == "corrected_by_name":
                summary["corrected_by_name"] += 1
            elif status == "resolved_by_provided_code":
                summary["resolved_by_provided_code"] += 1
            else:
                summary["unresolved"] += 1

    return summary


def _apply_recent_turn_progress_guards(
    *,
    progress_result: dict[str, Any],
    progress_context: dict[str, Any],
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    """把明确的“没有相关经历”回答，归一化为已回答维度。"""

    missing_dimensions = _normalize_dimension_list(progress_result.get("missing_dimensions") or [])
    previous_progress = progress_context.get("current_progress_json") or {}
    inherited_negative_dimensions = set(
        _normalize_dimension_list(
            previous_progress.get("negative_answered_dimensions") or []
        )
    )
    recent_negative_dimensions = _infer_recent_negative_answered_dimensions(
        progress_context.get("latest_round_messages") or []
    )

    active_negative_dimensions = (
        inherited_negative_dimensions | recent_negative_dimensions
    ) & set(missing_dimensions)
    if not active_negative_dimensions:
        progress_result.pop("negative_answered_dimensions", None)
        return progress_result

    filtered_missing_dimensions = [
        item for item in missing_dimensions if item not in active_negative_dimensions
    ]
    progress_result["negative_answered_dimensions"] = sorted(active_negative_dimensions)
    progress_result["missing_dimensions"] = filtered_missing_dimensions

    next_question_focus = progress_result.get("next_question_focus")
    if next_question_focus in active_negative_dimensions:
        progress_result["next_question_focus"] = _pick_next_question_focus(
            filtered_missing_dimensions
        )

    if not filtered_missing_dimensions:
        progress_result["stop_ready"] = True

    log_flow_info(
        logger,
        flow_name="AI对话",
        step_name="否定回答归一化",
        student_id=student_id,
        session_id=session_id,
        message=(
            "识别到学生已明确回答暂无相关经历，跳过重复追问维度："
            + "、".join(sorted(active_negative_dimensions))
        ),
    )
    return progress_result


def _infer_recent_negative_answered_dimensions(
    latest_round_messages: list[dict[str, Any]],
) -> set[str]:
    """识别最近一问一答里，是否出现明确的否定回答。"""

    latest_user_message, previous_assistant_message = _find_latest_turn_pair(
        latest_round_messages
    )
    if latest_user_message is None:
        return set()

    user_text = _normalize_guard_text(latest_user_message.get("content"))
    if not _is_explicit_negative_reply(user_text):
        return set()

    assistant_text = _normalize_guard_text(
        previous_assistant_message.get("content") if previous_assistant_message else ""
    )
    dimensions = _detect_guard_dimensions(assistant_text)
    if not dimensions:
        dimensions = _detect_guard_dimensions(user_text)
    return set(dimensions)


def _find_latest_turn_pair(
    latest_round_messages: list[dict[str, Any]],
) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    latest_user_message: dict[str, Any] | None = None
    previous_assistant_message: dict[str, Any] | None = None

    for index in range(len(latest_round_messages) - 1, -1, -1):
        item = latest_round_messages[index]
        if item.get("message_role") == "user":
            latest_user_message = item
            for previous_index in range(index - 1, -1, -1):
                previous_item = latest_round_messages[previous_index]
                if previous_item.get("message_role") == "assistant":
                    previous_assistant_message = previous_item
                    break
            break

    return latest_user_message, previous_assistant_message


def _detect_guard_dimensions(text: str) -> list[str]:
    normalized_text = _normalize_guard_text(text)
    if not normalized_text:
        return []

    matched_dimensions: list[str] = []
    for dimension, keywords in NEGATIVE_GUARD_DIMENSION_KEYWORDS.items():
        if any(keyword in normalized_text for keyword in keywords):
            matched_dimensions.append(dimension)
    return matched_dimensions


def _is_explicit_negative_reply(text: str) -> bool:
    if not text:
        return False
    if any(marker in text for marker in NEGATIVE_REPLY_BLOCK_MARKERS):
        return False
    if text in NEGATIVE_REPLY_EXACT_TEXTS:
        return True
    return len(text) <= 12 and any(text.startswith(prefix) for prefix in NEGATIVE_REPLY_PREFIXES)


def _normalize_guard_text(value: Any) -> str:
    if not isinstance(value, str):
        return ""

    translation_table = str.maketrans("", "", " \n\r\t，。！？、,.!?；;：:（）()“”\"'")
    return value.strip().lower().translate(translation_table)


def _normalize_dimension_list(values: list[Any]) -> list[str]:
    normalized_values: list[str] = []
    for item in values:
        if isinstance(item, str):
            normalized_item = item.strip()
            if normalized_item:
                normalized_values.append(normalized_item)
    return normalized_values


def _pick_next_question_focus(
    missing_dimensions: list[str],
    blocked_dimensions: list[str] | set[str] | tuple[str, ...] = (),
) -> str | None:
    blocked_dimension_set = {item for item in blocked_dimensions if isinstance(item, str)}
    for item in missing_dimensions:
        if item not in blocked_dimension_set:
            return item
    return None
