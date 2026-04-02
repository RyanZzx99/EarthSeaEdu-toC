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

import copy
import json
import re
import threading
from dataclasses import dataclass
import logging
from typing import Any, Callable

from sqlalchemy.orm import Session

from backend.config.db_conf import SessionLocal
from backend.services.ai_chat_service import append_assistant_message
from backend.services.ai_chat_service import append_internal_state_message
from backend.services.ai_chat_service import get_all_visible_messages
from backend.services.ai_chat_service import get_latest_progress_state
from backend.services.ai_chat_service import get_session_summary_json
from backend.services.ai_chat_service import get_profile_result_detail
from backend.services.ai_chat_service import get_session_detail
from backend.services.ai_chat_service import get_visible_messages
from backend.services.ai_chat_service import save_profile_result
from backend.services.ai_chat_service import update_profile_result_status
from backend.services.ai_chat_service import update_session_stage
from backend.services.ai_chat_service import update_session_progress
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.business_profile_form_service import load_business_profile_snapshot
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot
from backend.services.code_resolution_service import apply_code_resolution_to_payload
from backend.services.code_resolution_service import apply_subject_code_resolution_to_payload
from backend.utils.flow_logging import log_flow_info
from backend.utils.flow_logging import log_timed_step


CONVERSATION_PROMPT_KEY = "student_profile_build.conversation"
CONVERSATION_WITH_PROGRESS_PROMPT_KEY = "student_profile_build.conversation_with_progress"
PROGRESS_PROMPT_KEY = "student_profile_build.progress_extraction"
EXTRACTION_PROMPT_KEY = "student_profile_build.extraction"
SCORING_PROMPT_KEY = "student_profile_build.scoring"
PROFILE_SAVING_STAGE = "profile_saving"
EXTRACTION_RECENT_MESSAGE_LIMIT = 12
ARCHIVE_PROGRESS_DIMENSION_ORDER = [
    "academic",
    "language",
    "standardized",
    "competition",
    "activity",
    "project",
]
ARCHIVE_PROGRESS_IGNORED_FIELDS = {
    "student_id",
    "student_academic_id",
    "student_language_id",
    "student_standardized_test_id",
    "project_id",
}

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

POSITIVE_PROGRESS_DIMENSION_KEYWORDS: dict[str, tuple[str, ...]] = {
    "academic": (
        "高一",
        "高二",
        "高三",
        "g9",
        "g10",
        "g11",
        "g12",
        "ib1",
        "ib2",
        "as",
        "a2",
        "排名",
        "前5",
        "前10",
        "gpa",
        "预测总分",
        "预估",
        "成绩",
        "mathaa",
    ),
    "language": (
        "雅思",
        "托福",
        "toefl",
        "ielts",
        "det",
        "多邻国",
        "duolingo",
        "pte",
        "languagecert",
        "剑桥",
        "cambridge",
    ),
    "standardized": (
        "sat",
        "act",
    ),
    "competition": (
        "竞赛",
        "比赛",
        "论文",
        "入围",
        "银级",
        "gold",
        "silver",
        "usaco",
        "amc",
        "bpho",
        "nec",
    ),
    "activity": (
        "学生会",
        "部长",
        "队长",
        "班长",
        "社团",
        "志愿",
        "机器人队",
        "宣传部长",
    ),
    "project": (
        "项目",
        "科研",
        "研究",
        "实验室",
        "summerprogram",
        "summercamp",
        "实习",
        "帮忙",
        "小程序",
        "program",
        "课题",
        "作品",
    ),
}

POSITIVE_DIMENSION_REASON_MAP: dict[str, str] = {
    "academic": "当前轮已提供课程体系、成绩或排名信息",
    "language": "当前轮已提供语言考试或模考信息",
    "standardized": "当前轮已提供 SAT/ACT 信息",
    "competition": "当前轮已提供竞赛经历",
    "activity": "当前轮已提供活动经历",
    "project": "当前轮已提供项目、实践或研究经历",
}

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


@dataclass(slots=True)
class ManualProfileGenerationDispatchResult:
    """
    手动生成六维图后台任务的调度结果。

    这里单独拆一个结果对象，是为了把“六维图结果生成”和“正式业务表保存”拆成两个阶段：
    1. 第一阶段只负责尽快产出六维图结果，让前端可以先展示雷达图和总结。
    2. 第二阶段再异步完成正式业务表入库，避免把用户可感知等待时间拖长。
    """

    final_result: FinalProfileGenerationResult
    should_start_profile_persistence: bool
    reused_cached_result: bool


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

    dispatch_result: ManualProfileGenerationDispatchResult | None = None
    db = SessionLocal()
    try:
        dispatch_result = run_manual_profile_generation_v2(
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
            previous_session_summary = progress_context.get("current_session_summary")
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
            progress_json = persist_progress_result(
                db,
                student_id=student_id,
                session_id=session_id,
                progress_result=progress_json,
                progress_context=progress_context,
            )

        return progress_json


def persist_progress_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    progress_result: dict[str, Any],
    progress_context: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """
    统一归一化并保存一份 progress 结果。

    中文注释：
    1. 旧链路里，这份 progress 来自 progress_extraction_prompt。
    2. 新的 conversation_with_progress 链路里，这份 progress 来自合并 prompt 的隐藏 JSON 段。
    3. 不管来源是哪一种，最终都走同一套“归一化 -> internal_state -> session 快照”保存逻辑。
    """

    resolved_progress_context = progress_context
    if not isinstance(resolved_progress_context, dict):
        resolved_progress_context = _build_progress_extraction_context(
            db,
            student_id=student_id,
            session_id=session_id,
        )

    archive_snapshot = resolved_progress_context.get("current_archive_form_snapshot")
    if isinstance(archive_snapshot, dict) and archive_snapshot:
        # 中文注释：
        # progress_extraction prompt 虽然已经在上下文里看到了正式档案/草稿快照，
        # 但模型返回结果时仍可能“忘记继承”某些已存在字段，最常见的就是标化、
        # 语言或课程体系又被写回 missing。
        # 这里在真正落库前，再把快照信息强制并回 progress_result，
        # 保证“档案里已明确存在的信息”不会被 prompt 回退掉。
        progress_result = _merge_progress_with_archive_snapshot(
            progress_result,
            archive_snapshot,
        )

    normalized_progress_result = _apply_recent_turn_progress_guards(
        progress_result=progress_result,
        progress_context=resolved_progress_context,
        student_id=student_id,
        session_id=session_id,
    )
    # 中文注释：
    # 这次调整后，progress_extraction 只负责进度判断，不再负责维护 session_summary。
    # 即使模型历史 prompt 里仍然偶尔返回了 session_summary，这里也统一丢弃，避免旧字段继续影响后续链路。
    normalized_progress_result.pop("session_summary", None)

    append_internal_state_message(
        db,
        student_id=student_id,
        session_id=session_id,
        content="progress_extraction_result",
        content_json=normalized_progress_result,
    )
    update_session_progress(
        db,
        student_id=student_id,
        session_id=session_id,
        progress_result=normalized_progress_result,
    )
    return normalized_progress_result


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


# 中文注释：
# 以下这一组函数是“六维图先返回前端、正式保存后置”的新版实现。
# 由于这个文件里保留了历史实现用于对照，这里通过同名函数覆盖旧实现，
# 让路由入口默认走下面这套新的拆分链路。
def _get_reusable_profile_result_detail(
    db: Session,
    *,
    student_id: str,
    session_id: str,
):
    """
    读取当前会话是否可以直接复用的六维图结果。

    复用条件只有一个核心判断：
    只要上一次生成结果之后，没有新的对话消息进入本会话，
    就不应该再重复跑 extraction / scoring。
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

    last_message_at = session_detail.last_message_at
    result_updated_at = profile_result.update_time
    if last_message_at is not None and result_updated_at < last_message_at:
        return None

    return profile_result


def _build_final_profile_generation_result_from_query(profile_result) -> FinalProfileGenerationResult:
    """把查询结果统一转换成前后端都能直接复用的六维图结果对象。"""

    return FinalProfileGenerationResult(
        profile_result_id=profile_result.profile_result_id,
        profile_json=profile_result.profile_json,
        radar_scores_json=profile_result.radar_scores_json,
        summary_text=profile_result.summary_text,
        result_status=profile_result.result_status,
        save_error_message=profile_result.save_error_message,
    )


def _start_profile_persistence_background_task(
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> None:
    """
    启动正式保存后台线程。

    中文注释：
    这里故意使用独立线程，而不是继续把保存逻辑留在同一个后台任务里，
    目的是让“六维图先返回前端”这件事真正落地。
    """

    persistence_thread = threading.Thread(
        target=_run_profile_persistence_background_task,
        kwargs={
            "student_id": student_id,
            "session_id": session_id,
            "biz_domain": biz_domain,
        },
        name=f"profile-persist-{session_id[:8]}",
        daemon=True,
    )
    persistence_thread.start()
    log_flow_info(
        logger,
        flow_name="AI建档",
        step_name="启动正式保存后台线程",
        student_id=student_id,
        session_id=session_id,
        message="六维图结果已可展示，正式业务表保存改由独立后台线程继续执行。",
    )


def _run_profile_persistence_background_task(
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> None:
    """
    独立后台线程：负责正式业务表保存。

    这一段只做“后台善后”：
    1. 本地 subject code resolution
    2. 构建正式入库 payload
    3. delete/upsert/insert 正式业务表
    4. 回写 ai_chat_profile_results 的最终状态
    """

    db = SessionLocal()
    try:
        _run_profile_persistence_pipeline(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
    except Exception as exc:
        logger.exception(
            "正式保存后台线程失败，student_id=%s, session_id=%s, error=%s",
            student_id,
            session_id,
            exc,
        )

        existing_result = get_profile_result_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
        if existing_result is not None and isinstance(existing_result.profile_json, dict):
            save_profile_result(
                db,
                student_id=student_id,
                session_id=session_id,
                biz_domain=biz_domain,
                profile_json=existing_result.profile_json,
                radar_scores_json=existing_result.radar_scores_json,
                summary_text=existing_result.summary_text,
                result_status="failed",
                db_payload_json=existing_result.db_payload_json,
                save_error_message=str(exc),
                session_stage="build_ready",
                session_remark=str(exc)[:500],
            )
        else:
            update_session_stage(
                db,
                student_id=student_id,
                session_id=session_id,
                current_stage="build_ready",
                remark=str(exc)[:500],
            )
    finally:
        db.close()


def run_manual_profile_generation_background(
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> None:
    """
    新版后台入口：
    1. 先生成六维图结果
    2. 再异步启动正式保存
    """

    dispatch_result: ManualProfileGenerationDispatchResult | None = None
    db = SessionLocal()
    try:
        # 中文注释：
        # 这一段只负责把雷达图和总结先产出来。
        # 用户看到六维图之后，正式业务表保存再去后台慢慢做。
        dispatch_result = run_manual_profile_generation_v2(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )
    except Exception as exc:
        update_session_stage(
            db,
            student_id=student_id,
            session_id=session_id,
            current_stage="failed",
            remark=str(exc)[:500],
        )
    finally:
        db.close()

    if dispatch_result is None:
        return

    if dispatch_result.should_start_profile_persistence:
        _start_profile_persistence_background_task(
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
        )


def run_manual_profile_generation_v2(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> ManualProfileGenerationDispatchResult:
    """
    新版手动生成流程：
    1. 如果没有新增对话，优先复用已有六维图结果
    2. 如果已有结果但正式保存未完成，只重试正式保存，不重跑 extraction / scoring
    3. 如果没有可复用结果，再执行 extraction / scoring
    """

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

        reusable_profile_result = _get_reusable_profile_result_detail(
            db,
            student_id=student_id,
            session_id=session_id,
        )
        if reusable_profile_result is not None:
            reusable_final_result = _build_final_profile_generation_result_from_query(
                reusable_profile_result
            )
            log_flow_info(
                logger,
                flow_name="AI建档",
                step_name="复用已有六维图结果",
                student_id=student_id,
                session_id=session_id,
                message=(
                    "检测到上一次生成结果之后没有新增对话，"
                    "本次不再重复执行 extraction / scoring。"
                ),
            )

            if reusable_profile_result.result_status == "saved":
                # 中文注释：
                # 上一次不仅六维图生成过，而且正式保存也已经完成。
                # 这种情况直接复用最终结果，不再启动任何后台保存任务。
                update_session_stage(
                    db,
                    student_id=student_id,
                    session_id=session_id,
                    current_stage="build_ready",
                    remark=None,
                )
                return ManualProfileGenerationDispatchResult(
                    final_result=reusable_final_result,
                    should_start_profile_persistence=False,
                    reused_cached_result=True,
                )

            # 中文注释：
            # 走到这里说明六维图结果本身已经有了，但正式保存还没成功完成。
            # 这次点击“更新六维图”只需要重试正式保存阶段，不需要再跑模型。
            save_profile_result(
                db,
                student_id=student_id,
                session_id=session_id,
                biz_domain=biz_domain,
                profile_json=copy.deepcopy(reusable_profile_result.profile_json),
                radar_scores_json=reusable_profile_result.radar_scores_json,
                summary_text=reusable_profile_result.summary_text,
                result_status="generated",
                db_payload_json=reusable_profile_result.db_payload_json,
                save_error_message=None,
                session_stage=PROFILE_SAVING_STAGE,
                session_remark=None,
            )
            reusable_final_result.result_status = "generated"
            reusable_final_result.save_error_message = None
            return ManualProfileGenerationDispatchResult(
                final_result=reusable_final_result,
                should_start_profile_persistence=True,
                reused_cached_result=True,
            )

        final_result = _run_final_profile_pipeline_v3(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            latest_progress_result=latest_progress_result,
        )
        return ManualProfileGenerationDispatchResult(
            final_result=final_result,
            should_start_profile_persistence=True,
            reused_cached_result=False,
        )


def _run_final_profile_pipeline_v3(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    latest_progress_result: dict[str, Any],
) -> FinalProfileGenerationResult:
    """
    新版六维图主流程：
    1. extraction
    2. 国家/专业/学校码值映射
    3. scoring
    4. 立即保存六维图结果给前端

    中文注释：
    学术科目码值映射和正式业务表入库已经被挪到独立后台线程，
    这里不再等待那部分慢操作完成。
    """

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
                db_payload_json=None,
                save_error_message=None,
                session_stage=PROFILE_SAVING_STAGE,
                session_remark=None,
            )

        return FinalProfileGenerationResult(
            profile_result_id=saved_result.profile_result_id,
            profile_json=extraction_json,
            radar_scores_json=scoring_json.get("radar_scores_json"),
            summary_text=scoring_json.get("summary_text"),
            result_status="generated",
            save_error_message=None,
        )


def _run_profile_persistence_pipeline(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> None:
    """
    正式保存阶段。

    中文注释：
    这里故意只依赖已经保存好的 profile_json，不再重新调用 AI。
    这样既不会增加新的模型耗时，也能保证点击“更新六维图”时优先看到结果。
    """

    profile_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if profile_result is None:
        raise ValueError("当前会话还没有可用于正式保存的六维图结果。")
    if not isinstance(profile_result.profile_json, dict) or not profile_result.profile_json:
        raise ValueError("当前六维图结果缺少 profile_json，无法继续正式保存。")

    working_profile_json = copy.deepcopy(profile_result.profile_json)

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="本地学术科目码值映射",
        student_id=student_id,
        session_id=session_id,
    ):
        subject_code_resolution_results = apply_subject_code_resolution_to_payload(
            db,
            working_profile_json,
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
                "正式保存前学术科目码值修正完成："
                f"处理行数={subject_resolution_summary['total_rows']}，"
                f"按名称解析={subject_resolution_summary['resolved_by_name']}，"
                f"按名称纠错={subject_resolution_summary['corrected_by_name']}，"
                f"沿用已有码值={subject_resolution_summary['resolved_by_provided_code']}，"
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
            working_profile_json,
            student_id=student_id,
        )

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

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="回写档案创建完成状态",
        student_id=student_id,
        session_id=session_id,
    ):
        save_profile_result(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            profile_json=working_profile_json,
            radar_scores_json=profile_result.radar_scores_json,
            summary_text=profile_result.summary_text,
            result_status="saved",
            db_payload_json=db_payload,
            save_error_message=None,
            session_stage="build_ready",
            session_remark=None,
        )


def _try_reuse_existing_profile_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> FinalProfileGenerationResult | None:
    """
    新版缓存复用判断。

    中文注释：
    这里不再要求 result_status 必须是 saved。
    只要上一次六维图已经生成过、且之后没有新增对话，就说明 extraction / scoring 可以直接复用。
    """

    reusable_profile_result = _get_reusable_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if reusable_profile_result is None:
        return None

    return _build_final_profile_generation_result_from_query(
        reusable_profile_result
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
    3. 当前会话摘要 session_summary
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
    archive_snapshot = load_business_profile_snapshot(
        db,
        student_id=student_id,
    )
    resolved_progress_json = _merge_progress_with_archive_snapshot(
        resolved_progress_json,
        archive_snapshot,
    )
    current_session_summary = get_session_summary_json(
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
        "current_session_summary": current_session_summary or {},
        "current_archive_form_snapshot": archive_snapshot or {},
        "conversation_control": conversation_control,
    }


def _build_progress_extraction_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    """
    构建 progress_extraction_prompt 的上下文。

    中文注释：
    1. latest_round_messages 负责提供本轮新增信息。
    2. current_progress_json 负责告诉模型当前已收集进度。
    3. 这次调整后不再传 current_session_summary，避免 progress_extraction 继续承担摘要职责。
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
    archive_snapshot = load_business_profile_snapshot(
        db,
        student_id=student_id,
    )
    current_progress_json = _merge_progress_with_archive_snapshot(
        current_progress_json,
        archive_snapshot,
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
        "current_archive_form_snapshot": archive_snapshot or {},
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
    missing_basic_fields: list[str] = []
    basic_field_priority = [
        "current_grade",
        "curriculum_system",
        "target_country",
        "major_interest",
    ]

    for field_name in basic_field_priority:
        field_value = basic_info_progress.get(field_name)
        if not isinstance(field_value, dict):
            missing_basic_fields.append(field_name)
            continue
        if field_value.get("collected") is True:
            do_not_repeat_basic_fields.append(field_name)
            confirmed_basic_values[field_name] = field_value.get("value")
        else:
            missing_basic_fields.append(field_name)

    next_basic_question_field = missing_basic_fields[0] if missing_basic_fields else None

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
    elif next_basic_question_field:
        # 中文注释：
        # 只要基础槽位还没收齐，下一轮就必须先补基础槽位。
        # 维度追问只能排在基础槽位之后，否则 stop_ready 会长期起不来。
        instruction_parts.append(
            f"基础必填信息还未收齐，下一轮必须优先追问基础槽位：{next_basic_question_field}。"
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
        "missing_basic_fields": missing_basic_fields,
        "next_basic_question_field": next_basic_question_field,
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

    中文注释：
    1. 恢复为始终传入 full_conversation_history。
    2. current_progress_json 继续保留，方便 extraction 参考当前进度。
    """

    current_progress_json = get_latest_progress_state(
        db,
        session_id=session_id,
    )
    # 中文注释：
    # progress_extraction 不再维护 session_summary。
    # 这里显式关闭摘要分支，统一回到全量对话 extraction。
    session_summary_json = None
    if False:
        recent_visible_messages = get_visible_messages(
            db,
            session_id=session_id,
            limit=EXTRACTION_RECENT_MESSAGE_LIMIT,
        )
        return {
            "student_id": student_id,
            "session_id": session_id,
            # 中文注释：
            # extraction 优先使用 session_summary 承接长历史，再配合最近若干条原始消息补足最新细节。
            # 这样能显著缩短上下文，而不必再把整段长对话完整喂给模型。
            "session_summary": session_summary_json,
            "recent_conversation_history": [
                {
                    "message_role": item.message_role,
                    "message_type": item.message_type,
                    "sequence_no": item.sequence_no,
                    "content": item.content,
                    "create_time": item.create_time.isoformat(),
                }
                for item in recent_visible_messages
            ],
            "current_progress_json": current_progress_json or {},
        }

    full_visible_messages = get_all_visible_messages(
        db,
        session_id=session_id,
    )
    return {
        "student_id": student_id,
        "session_id": session_id,
        # 中文注释：
        # 旧会话尚未积累 session_summary 时，继续回退到全量历史，保证 extraction 行为兼容。
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


def _normalize_session_summary_json(
    raw_summary: Any,
    *,
    fallback_summary: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    """
    归一化 progress_extraction 返回的 session_summary。

    中文注释：
    1. 模型返回的新摘要如果结构完整，就按统一结构压平保存。
    2. 如果模型本轮没给出合法摘要，则沿用上一版摘要，避免把会话摘要写空。
    """

    if not isinstance(raw_summary, dict):
        return fallback_summary

    basic_profile = raw_summary.get("basic_profile") or {}
    dimension_snapshot = raw_summary.get("dimension_snapshot") or {}
    open_questions = raw_summary.get("open_questions") or []

    normalized_summary = {
        "summary_version": str(raw_summary.get("summary_version") or "v1"),
        "basic_profile": {
            "current_grade": _normalize_optional_text(basic_profile.get("current_grade")),
            "target_countries": _normalize_text_list(basic_profile.get("target_countries")),
            "major_interests": _normalize_text_list(basic_profile.get("major_interests")),
            "curriculum_systems": _normalize_text_list(basic_profile.get("curriculum_systems")),
        },
        "dimension_snapshot": {
            dimension: _normalize_optional_text(dimension_snapshot.get(dimension))
            for dimension in SESSION_SUMMARY_DIMENSIONS
        },
        "open_questions": _normalize_text_list(open_questions, limit=3),
    }
    return normalized_summary


def _normalize_optional_text(value: Any) -> str | None:
    if value is None:
        return None
    normalized_value = str(value).strip()
    return normalized_value or None


def _normalize_text_list(values: Any, *, limit: int | None = None) -> list[str]:
    if not isinstance(values, list):
        return []

    normalized_values: list[str] = []
    for item in values:
        normalized_item = _normalize_optional_text(item)
        if normalized_item:
            normalized_values.append(normalized_item)
        if limit is not None and len(normalized_values) >= limit:
            break
    return normalized_values


def regenerate_profile_result_from_manual_profile_json(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    profile_json: dict[str, Any],
) -> dict[str, Any]:
    """
    基于用户手动编辑后的 profile_json，重新生成六维图并正式保存档案。

    中文注释：
    这里故意不重新跑 extraction。
    因为档案页提交上来的已经是结构化结果，本次只需要重新评分并重新落业务表。
    """

    existing_result = get_profile_result_detail(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if existing_result is None:
        raise ValueError("当前会话还没有可编辑的建档结果。")

    if not isinstance(profile_json, dict) or not profile_json:
        raise ValueError("手动保存的档案内容不能为空。")

    working_profile_json = copy.deepcopy(profile_json)

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="手动档案重新评分与保存",
        student_id=student_id,
        session_id=session_id,
    ):
        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="手动档案基础码值修正",
            student_id=student_id,
            session_id=session_id,
        ):
            apply_code_resolution_to_payload(
                db,
                working_profile_json,
                target_country_text=None,
                major_text=None,
                school_name_text=(working_profile_json.get("student_academic", {}) or {}).get("school_name"),
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="手动档案学术科目码值修正",
            student_id=student_id,
            session_id=session_id,
        ):
            subject_code_resolution_results = apply_subject_code_resolution_to_payload(
                db,
                working_profile_json,
            )
            subject_resolution_summary = _summarize_subject_code_resolution_results(
                subject_code_resolution_results
            )
            log_flow_info(
                logger,
                flow_name="AI建档",
                step_name="手动档案学术科目码值修正",
                student_id=student_id,
                session_id=session_id,
                message=(
                    "手动保存前学术科目码值修正完成："
                    f"处理行数={subject_resolution_summary['total_rows']}，"
                    f"按名称解析={subject_resolution_summary['resolved_by_name']}，"
                    f"按名称纠错={subject_resolution_summary['corrected_by_name']}，"
                    f"沿用已有码值={subject_resolution_summary['resolved_by_provided_code']}，"
                    f"未解析={subject_resolution_summary['unresolved']}"
                ),
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="手动档案重新评分",
            student_id=student_id,
            session_id=session_id,
        ):
            scoring_runtime_result = execute_prompt_with_context(
                db,
                prompt_key=SCORING_PROMPT_KEY,
                context={
                    "student_id": student_id,
                    "session_id": session_id,
                    "profile_json": working_profile_json,
                },
            )
            scoring_json = _parse_json_object(
                raw_text=scoring_runtime_result.content,
                scene_name="manual_scoring",
            )

        with log_timed_step(
            logger,
            flow_name="AI建档",
            step_name="构建手动档案入库载荷",
            student_id=student_id,
            session_id=session_id,
        ):
            db_payload = build_db_payload_from_profile_json(
                working_profile_json,
                student_id=student_id,
            )

        result_status = "saved"
        save_error_message: str | None = None
        try:
            with log_timed_step(
                logger,
                flow_name="AI建档",
                step_name="手动档案正式业务表入库",
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
            flow_name="AI建档结果",
            step_name="回写手动档案结果",
            student_id=student_id,
            session_id=session_id,
        ):
            saved_result = save_profile_result(
                db,
                student_id=student_id,
                session_id=session_id,
                biz_domain=biz_domain,
                profile_json=working_profile_json,
                radar_scores_json=scoring_json.get("radar_scores_json"),
                summary_text=scoring_json.get("summary_text"),
                result_status=result_status,
                db_payload_json=db_payload,
                save_error_message=save_error_message,
                session_stage="build_ready",
                session_remark=None,
            )

    return {
        "session_id": session_id,
        "profile_result_id": saved_result.profile_result_id,
        "profile_json": working_profile_json,
        "radar_scores_json": scoring_json.get("radar_scores_json") or {},
        "summary_text": scoring_json.get("summary_text"),
        "result_status": result_status,
        "save_error_message": save_error_message,
        "db_payload_json": db_payload,
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
    """
    对本轮 progress 结果做程序侧硬校验。

    中文注释：
    1. 这里不再从文本里反向提取基础槽位或维度信息。
    2. 程序侧只保留“安全收束”职责：
       - 归一化字段形状
       - 继承上一轮已确认值，避免被模型写丢
       - 清理明显错误值
       - 重算 stop_ready / next_question_focus
    3. 用户文本语义判断尽量交给 AI 自己完成。
    """

    previous_progress = progress_context.get("current_progress_json") or {}
    basic_info_progress = progress_result.setdefault("basic_info_progress", {})
    progress_result.setdefault("dimension_progress", {})
    _normalize_basic_progress_value_shapes(basic_info_progress)
    _clear_suspicious_current_grade_value(
        basic_info_progress=basic_info_progress,
    )
    _inherit_previous_basic_progress_fields(
        basic_info_progress=basic_info_progress,
        previous_basic_info_progress=previous_progress.get("basic_info_progress") or {},
    )

    filtered_missing_dimensions = _normalize_dimension_list(
        progress_result.get("missing_dimensions") or []
    )
    negative_answered_dimensions = _normalize_dimension_list(
        progress_result.get("negative_answered_dimensions") or []
    )
    if negative_answered_dimensions:
        progress_result["negative_answered_dimensions"] = negative_answered_dimensions
    else:
        progress_result.pop("negative_answered_dimensions", None)

    progress_result["missing_dimensions"] = filtered_missing_dimensions

    progress_result["stop_ready"] = _compute_build_ready_state(
        basic_info_progress=basic_info_progress,
        filtered_missing_dimensions=filtered_missing_dimensions,
    )
    progress_result["next_question_focus"] = (
        None
        if progress_result["stop_ready"]
        else _pick_next_question_focus(
            filtered_missing_dimensions,
            blocked_dimensions=negative_answered_dimensions,
        )
    )

    return progress_result


def _apply_recent_turn_basic_info_guards(
    *,
    basic_info_progress: dict[str, Any],
    latest_user_text: str,
    recent_user_context_text: str,
    recent_visible_context_text: str,
    previous_basic_info_progress: dict[str, Any],
) -> None:
    """把最近上下文里明确提到的基础槽位，强制回填到 progress 结果里。"""

    _normalize_basic_progress_value_shapes(basic_info_progress)

    # 中文注释：
    # current_grade / major_interest 优先从用户最近输入中提取，
    # 若最后一句没有，再回退到最近若干轮用户上下文。
    # curriculum_system 额外允许从最近全可见上下文中兜底，
    # 这样像“你是在国内普通高中就读对吗？”这类 assistant 确认语句也能帮助回填。
    current_grade = _extract_first_scalar_from_contexts(
        _extract_explicit_current_grade,
        latest_user_text,
        recent_user_context_text,
    )
    if current_grade:
        _set_basic_progress_field(
            basic_info_progress,
            field_name="current_grade",
            value=current_grade,
        )
    else:
        _clear_suspicious_current_grade_value(
            basic_info_progress=basic_info_progress,
        )

    major_interest = _extract_first_scalar_from_contexts(
        _extract_explicit_major_interest,
        latest_user_text,
        recent_user_context_text,
    )
    if major_interest:
        _set_basic_progress_field(
            basic_info_progress,
            field_name="major_interest",
            value=major_interest,
        )

    curriculum_systems = _extract_first_list_from_contexts(
        _extract_explicit_curriculum_systems,
        latest_user_text,
        recent_user_context_text,
        recent_visible_context_text,
    )
    if curriculum_systems:
        # 中文注释：
        # 当前基础槽位还是单值字段；若本轮明确出现多个体系，就先取第一个作为主体系。
        _set_basic_progress_field(
            basic_info_progress,
            field_name="curriculum_system",
            value=curriculum_systems[0],
        )

    _inherit_previous_basic_progress_fields(
        basic_info_progress=basic_info_progress,
        previous_basic_info_progress=previous_basic_info_progress,
    )


def _apply_recent_turn_positive_dimension_guards(
    *,
    progress_result: dict[str, Any],
    latest_user_text: str,
    recent_user_context_text: str,
    student_id: str,
    session_id: str,
) -> list[str]:
    """
    把本轮明确出现过信息的维度，从 missing 中移除。

    中文注释：
    1. 这里只修正“当前轮显式提到”的信息。
    2. 一旦命中高置信关键词，就至少把该维度提升到 partial，
       避免模型继续把它判成 missing。
    """

    # 中文注释：
    # 维度兜底同样不能只看最后一句。
    # 这里优先保留最近用户消息的强信号，同时允许从最近多轮用户上下文补回已经明确回答过的维度。
    detected_dimensions = (
        _detect_recent_turn_positive_dimensions(latest_user_text)
        | _detect_recent_turn_positive_dimensions(recent_user_context_text)
    )
    dimension_progress = progress_result.setdefault("dimension_progress", {})
    missing_dimensions = _normalize_dimension_list(progress_result.get("missing_dimensions") or [])

    for dimension in detected_dimensions:
        dimension_entry = dimension_progress.setdefault(
            dimension,
            {
                "status": "missing",
                "reason": "",
            },
        )
        if dimension_entry.get("status") == "missing":
            dimension_entry["status"] = "partial"
            dimension_entry["reason"] = POSITIVE_DIMENSION_REASON_MAP.get(dimension, "User provided explicit info")
        if dimension in missing_dimensions:
            missing_dimensions = [item for item in missing_dimensions if item != dimension]

    # 中文注释：
    # 目标国家不再由程序从文本中兜底识别，只信 AI 返回的 basic_info_progress。
    target_countries: list[str] = []
    target_country_progress = (progress_result.get("basic_info_progress") or {}).get("target_country") or {}
    target_country_value = str(target_country_progress.get("value") or "")
    if "英国" in target_country_value:
        target_countries = ["英国"]

    if (
        target_countries == ["英国"]
        and "standardized" in missing_dimensions
        and not _detect_sat_act_signal(recent_user_context_text or latest_user_text)
    ):
        standardized_entry = dimension_progress.setdefault(
            "standardized",
            {
                "status": "missing",
                "reason": "",
            },
        )
        standardized_entry["status"] = "not_applicable"
        standardized_entry["reason"] = "英国方向通常不强制 SAT/ACT"
        missing_dimensions = [item for item in missing_dimensions if item != "standardized"]

    if detected_dimensions:
        log_flow_info(
            logger,
            flow_name="AI对话",
            step_name="显式事实回填",
            student_id=student_id,
            session_id=session_id,
            message="本轮根据用户显式输入回填维度：" + "、".join(sorted(detected_dimensions)),
        )

    return missing_dimensions


def _build_recent_context_text(
    recent_messages: list[dict[str, Any]],
    *,
    user_only: bool,
) -> str:
    """按最近优先拼接上下文文本，供程序侧高置信兜底使用。"""

    if not isinstance(recent_messages, list):
        return ""

    collected_texts: list[str] = []
    seen_texts: set[str] = set()
    for item in reversed(recent_messages):
        if not isinstance(item, dict):
            continue
        if user_only and item.get("message_role") != "user":
            continue
        content = str(item.get("content") or "").strip()
        if not content or content in seen_texts:
            continue
        seen_texts.add(content)
        collected_texts.append(content)
    return "\n".join(collected_texts)


def _extract_first_scalar_from_contexts(
    extractor: Callable[[str], str | None],
    *texts: str,
) -> str | None:
    """按优先级从多个文本来源中提取单值结果。"""

    seen_texts: set[str] = set()
    for text in texts:
        if not isinstance(text, str):
            continue
        normalized_text = text.strip()
        if not normalized_text or normalized_text in seen_texts:
            continue
        seen_texts.add(normalized_text)
        value = extractor(normalized_text)
        if value:
            return value
    return None


def _extract_first_list_from_contexts(
    extractor: Callable[[str], list[str]],
    *texts: str,
) -> list[str]:
    """按优先级从多个文本来源中提取列表结果。"""

    seen_texts: set[str] = set()
    for text in texts:
        if not isinstance(text, str):
            continue
        normalized_text = text.strip()
        if not normalized_text or normalized_text in seen_texts:
            continue
        seen_texts.add(normalized_text)
        values = extractor(normalized_text)
        if values:
            return values
    return []


def _inherit_previous_basic_progress_fields(
    *,
    basic_info_progress: dict[str, Any],
    previous_basic_info_progress: dict[str, Any],
) -> None:
    """
    继承上一轮已经明确收集到的基础槽位。

    中文注释：
    1. progress_extraction 是增量更新，不应该把上一轮已确认的值轻易写丢。
    2. 只有当前轮既没有显式新值、当前结果里也没收上来时，才沿用上一轮值。
    """

    for field_name in [
        "current_grade",
        "target_country",
        "major_interest",
        "curriculum_system",
    ]:
        if _is_basic_progress_field_collected(basic_info_progress, field_name):
            continue
        previous_entry = previous_basic_info_progress.get(field_name)
        if not isinstance(previous_entry, dict):
            continue
        if previous_entry.get("collected") is not True:
            continue
        previous_value = str(previous_entry.get("value") or "").strip()
        if not previous_value:
            continue
        _set_basic_progress_field(
            basic_info_progress,
            field_name=field_name,
            value=previous_value,
        )


def _set_basic_progress_field(
    basic_info_progress: dict[str, Any],
    *,
    field_name: str,
    value: str,
) -> None:
    field_entry = basic_info_progress.setdefault(
        field_name,
        {
            "collected": False,
            "value": None,
        },
    )
    field_entry["collected"] = True
    field_entry["value"] = value


def _normalize_basic_progress_value_shapes(
    basic_info_progress: dict[str, Any],
) -> None:
    """
    把模型偶尔产出的 list 形态收口成字符串。

    中文注释：
    basic_info_progress 最终是给程序做门控和 code_resolution 用的，
    这里统一成字符串，避免后续读取时拿到 list 导致无法解析。
    """

    separator_map = {
        "target_country": "、",
        "major_interest": " / ",
        "curriculum_system": "、",
        "current_grade": "、",
    }
    for field_name, separator in separator_map.items():
        field_entry = basic_info_progress.get(field_name)
        if not isinstance(field_entry, dict):
            continue
        raw_value = field_entry.get("value")
        if isinstance(raw_value, list):
            normalized_items = [
                str(item).strip()
                for item in raw_value
                if str(item).strip()
            ]
            if normalized_items:
                field_entry["value"] = separator.join(normalized_items)


def _clear_suspicious_current_grade_value(
    *,
    basic_info_progress: dict[str, Any],
) -> None:
    """
    清理被模型误判成“年级”的课程体系词。

    中文注释：
    用户没说当前年级时，模型有时会把 IB / AP / A-Level 误塞进 current_grade。
    这类值会直接干扰 stop_ready，因此在程序侧清空。
    """

    current_grade_entry = basic_info_progress.get("current_grade")
    if not isinstance(current_grade_entry, dict):
        return

    raw_value = current_grade_entry.get("value")
    suspicious_values = {
        "IB",
        "AP",
        "A_LEVEL",
        "A-LEVEL",
        "CHINESE_HIGH_SCHOOL",
        "REGULAR HIGH SCHOOL",
        "普高",
        "中国普高",
    }
    if isinstance(raw_value, str) and raw_value.strip().upper() in suspicious_values:
        current_grade_entry["collected"] = False
        current_grade_entry["value"] = None


def _ensure_session_summary_dict(progress_result: dict[str, Any]) -> dict[str, Any]:
    raw_summary = progress_result.get("session_summary")
    if isinstance(raw_summary, dict):
        raw_summary.setdefault("summary_version", "v1")
        raw_summary.setdefault("basic_profile", {})
        raw_summary.setdefault("dimension_snapshot", {})
        raw_summary.setdefault("open_questions", [])
        return raw_summary

    summary = {
        "summary_version": "v1",
        "basic_profile": {
            "current_grade": None,
            "target_countries": [],
            "major_interests": [],
            "curriculum_systems": [],
        },
        "dimension_snapshot": {
            "academic": None,
            "language": None,
            "standardized": None,
            "competition": None,
            "activity": None,
            "project": None,
        },
        "open_questions": [],
    }
    progress_result["session_summary"] = summary
    return summary


def _extract_explicit_current_grade(text: str) -> str | None:
    if not isinstance(text, str) or not text.strip():
        return None

    patterns = [
        (r"(高一|高二|高三)", False),
        (r"(初一|初二|初三)", False),
        (r"\b(G(?:9|10|11|12))\b", True),
        (r"(?<![A-Za-z0-9])(IB(?:11|12|1|2))(?![A-Za-z0-9])", True),
        (r"((?:9|10|11|12)年级)", False),
        (r"(大一|大二|大三|大四)", False),
        (r"\b(AS|A2)\b", True),
        (r"(gap[\s-]*year)", True),
    ]
    for pattern, should_upper in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if not match:
            continue
        value = match.group(1).strip()
        if should_upper:
            normalized_value = value.upper().replace("-", " ")
            if "GAP" in normalized_value:
                return "GAP YEAR"
            return normalized_value
        return value
    return None


def _strip_major_prefix(candidate: str) -> str:
    cleaned_candidate = candidate.strip(" ：:，,；;、")
    cleaned_candidate = re.sub(
        r"^(?:去|申|申请|读|学|走|做|冲|考虑|偏向|偏|方向|专业|目标)+",
        "",
        cleaned_candidate,
        flags=re.IGNORECASE,
    ).strip(" ：:，,；;、")

    while True:
        updated_candidate = re.sub(
            r"^(?:英国|美国|加拿大|澳大利亚|澳洲|新加坡|中国香港|香港|日本|英本|美本)(?:和|与|及|/|、|或)?",
            "",
            cleaned_candidate,
            flags=re.IGNORECASE,
        ).strip(" ：:，,；;、")
        updated_candidate = re.sub(
            r"^(?:G5|G8|TOP\s*\d+|QS前\s*\d+|港前三|藤校)(?:和|与|及|/|、|或)?",
            "",
            updated_candidate,
            flags=re.IGNORECASE,
        ).strip(" ：:，,；;、")
        if updated_candidate == cleaned_candidate:
            break
        cleaned_candidate = updated_candidate

    return cleaned_candidate.strip(" ：:，,；;、")


def _looks_like_country_only_text(candidate: str) -> bool:
    return bool(
        re.fullmatch(
            r"(?:英国|美国|加拿大|澳大利亚|澳洲|新加坡|中国香港|香港|日本|英本|美本)"
            r"(?:(?:和|与|及|/|、|或)(?:英国|美国|加拿大|澳大利亚|澳洲|新加坡|中国香港|香港|日本|英本|美本))*",
            candidate,
            flags=re.IGNORECASE,
        )
    )


def _extract_explicit_major_interest(text: str) -> str | None:
    if not isinstance(text, str) or not text.strip():
        return None

    major_patterns = [
        r"目标专业(?:方向)?(?:是|为|想走|考虑|偏向|可能是)?(?P<value>[^，,；;。！？\n]+)",
        r"申请专业(?:方向)?(?:是|为|想走|考虑|偏向|可能是)?(?P<value>[^，,；;。！？\n]+)",
        r"专业(?:方向)?(?:是|为|想走|考虑|偏向|可能是)?(?P<value>[^，,；;。！？\n]+)",
        r"想申(?:请)?(?P<value>[^，,；;。！？\n]+)",
        r"方向(?:是|为|想走|考虑|偏向|可能是)?(?P<value>[^，,；;。！？\n]+)",
        r"目标(?P<value>(?:英国|美国|加拿大|澳大利亚|澳洲|新加坡|中国香港|香港|日本)[^，,；;。！？\n]+)",
    ]
    for pattern in major_patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if not match:
            continue
        candidate = _strip_major_prefix(match.group("value"))
        if not candidate:
            continue
        if _looks_like_country_only_text(candidate):
            continue
        if candidate.startswith("国家") or candidate.startswith("课程体系"):
            continue
        return candidate
    return None


def _extract_explicit_curriculum_systems(text: str) -> list[str]:
    if not isinstance(text, str) or not text.strip():
        return []

    normalized_text = text.lower()

    curriculum_rule_map = {
        "CHINESE_HIGH_SCHOOL": {
            "positive_patterns": [
                r"普高体系",
                r"中国普高",
                r"国内普高",
                r"我是普高",
                r"之前是国内普高",
                r"普高转国际部",
                r"普高",
            ],
            "negative_patterns": [],
        },
        "IB": {
            "positive_patterns": [
                r"ib体系",
                r"我是ib",
                r"读ib",
                r"ib课程",
                r"ib学生",
                r"\bib1\b",
                r"\bib2\b",
                r"\bib11\b",
                r"\bib12\b",
            ],
            "negative_patterns": [
                r"(?:没|没有|无|不是|并非|不读|没学|没上|没修)[^。；\n]{0,20}\bib\b",
                r"\bib\b[^。；\n]{0,12}(?:都没有|都没|没有|没)",
            ],
        },
        "A_LEVEL": {
            "positive_patterns": [
                r"a-level体系",
                r"a level体系",
                r"读a-level",
                r"读a level",
                r"学a-level",
                r"学a level",
                r"转去a-level",
                r"转去 a-level",
                r"我是a-level",
                r"读alevel",
                r"alevel体系",
            ],
            "negative_patterns": [
                r"(?:没|没有|无|不是|并非|不读|没学|没上|没修)[^。；\n]{0,20}(?:a-level|a level|alevel)",
                r"(?:a-level|a level|alevel)[^。；\n]{0,12}(?:都没有|都没|没有|没)",
            ],
        },
        "AP": {
            "positive_patterns": [
                r"美高\s*\+\s*ap",
                r"ap体系",
                r"我是ap",
                r"读ap",
                r"上过ap",
                r"修ap",
                r"修了ap",
                r"ap课程",
                r"学ap",
            ],
            "negative_patterns": [
                r"(?:没|没有|无|不是|并非|不读|没学|没上|没修)[^。；\n]{0,20}(?:美高\s*\+\s*ap|\bap\b)",
                r"(?:美高\s*\+\s*ap|\bap\b)[^。；\n]{0,12}(?:都没有|都没|没有|没)",
            ],
        },
    }

    matched_positions: list[tuple[int, str]] = []
    for curriculum_code, rule_config in curriculum_rule_map.items():
        positive_positions = [
            match.start()
            for pattern in rule_config["positive_patterns"]
            for match in [re.search(pattern, normalized_text, flags=re.IGNORECASE)]
            if match is not None
        ]
        if not positive_positions:
            continue

        if any(
            re.search(pattern, normalized_text, flags=re.IGNORECASE)
            for pattern in rule_config["negative_patterns"]
        ):
            continue

        matched_positions.append((min(positive_positions), curriculum_code))

    matched_positions.sort(key=lambda item: item[0])
    return [curriculum_code for _, curriculum_code in matched_positions]


def _detect_recent_turn_positive_dimensions(text: str) -> set[str]:
    normalized_text = _normalize_guard_text(text)
    if not normalized_text:
        return set()

    detected_dimensions: set[str] = set()
    for dimension, keywords in POSITIVE_PROGRESS_DIMENSION_KEYWORDS.items():
        if any(keyword in normalized_text for keyword in keywords):
            detected_dimensions.add(dimension)
    return detected_dimensions


def _detect_sat_act_signal(text: str) -> bool:
    normalized_text = _normalize_guard_text(text)
    return any(keyword in normalized_text for keyword in ["sat", "act"])


def _build_progress_open_questions(
    *,
    basic_info_progress: dict[str, Any],
    missing_dimensions: list[str],
    stop_ready: bool,
) -> list[str]:
    if stop_ready:
        return []

    open_questions: list[str] = []
    basic_field_question_map = {
        "current_grade": "请补充当前年级",
        "target_country": "请补充目标国家",
        "major_interest": "请补充目标专业方向",
        "curriculum_system": "请确认当前课程体系",
    }
    for field_name in ["current_grade", "target_country", "major_interest", "curriculum_system"]:
        field_value = basic_info_progress.get(field_name) or {}
        if field_value.get("collected") is not True:
            open_questions.append(basic_field_question_map[field_name])

    dimension_question_map = {
        "academic": "请补充学术表现信息",
        "language": "请补充语言成绩信息",
        "standardized": "请补充 SAT/ACT 情况",
        "competition": "请补充竞赛经历",
        "activity": "请补充活动经历",
        "project": "请补充项目或实践经历",
    }
    for dimension in missing_dimensions:
        question = dimension_question_map.get(dimension)
        if question:
            open_questions.append(question)

    return open_questions[:3]


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


def _is_basic_progress_field_collected(
    basic_info_progress: dict[str, Any],
    field_name: str,
) -> bool:
    field_value = basic_info_progress.get(field_name)
    return isinstance(field_value, dict) and field_value.get("collected") is True


def _compute_build_ready_state(
    *,
    basic_info_progress: dict[str, Any],
    filtered_missing_dimensions: list[str],
) -> bool:
    """
    计算“是否已经大致足够生成六维图”。

    中文注释：
    1. 这里使用当前对话链路的正式门槛：
       - 4 个基础槽位必须全部收齐
       - academic 不能仍然缺失
    2. 其它维度如 competition / activity / project / standardized 可以继续补，
       但不会单独阻塞六维图生成。
    """

    required_basic_fields = [
        "current_grade",
        "target_country",
        "major_interest",
        "curriculum_system",
    ]
    all_required_basic_fields_collected = all(
        _is_basic_progress_field_collected(basic_info_progress, field_name)
        for field_name in required_basic_fields
    )
    if not all_required_basic_fields_collected:
        return False

    blocking_missing_dimensions = {"academic"}
    return not any(
        dimension_name in blocking_missing_dimensions
        for dimension_name in filtered_missing_dimensions
    )


def _archive_value_has_meaningful_content(value: Any) -> bool:
    """判断正式档案快照里的某个值是否包含真实业务内容。"""

    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ""
    if isinstance(value, list):
        return any(_archive_value_has_meaningful_content(item) for item in value)
    if isinstance(value, dict):
        return any(
            field_name not in ARCHIVE_PROGRESS_IGNORED_FIELDS
            and _archive_value_has_meaningful_content(field_value)
            for field_name, field_value in value.items()
        )
    return True


def _archive_first_curriculum_code(curriculum_rows: list[dict[str, Any]]) -> str | None:
    primary_row = next(
        (
            row
            for row in curriculum_rows
            if isinstance(row, dict) and str(row.get("curriculum_system_code") or "").strip() and row.get("is_primary") == 1
        ),
        None,
    )
    selected_row = primary_row or next(
        (
            row
            for row in curriculum_rows
            if isinstance(row, dict) and str(row.get("curriculum_system_code") or "").strip()
        ),
        None,
    )
    if selected_row is None:
        return None
    return str(selected_row.get("curriculum_system_code")).strip()


def _build_archive_snapshot_progress_hint(
    archive_snapshot: dict[str, Any] | None,
) -> dict[str, Any]:
    """
    从正式档案快照构建一份轻量 progress 提示。

    中文注释：
    1. 这里只做“数据库里已经明确存在什么”的状态映射，不做复杂语义推断。
    2. 这份提示不会直接落库，只用于让 conversation / progress_extraction 看到学生已经手填的正式档案。
    """

    if not isinstance(archive_snapshot, dict):
        archive_snapshot = {}

    basic_info = archive_snapshot.get("student_basic_info") or {}
    curriculum_rows = archive_snapshot.get("student_basic_info_curriculum_system") or []

    basic_info_progress: dict[str, Any] = {
        "current_grade": {"collected": False, "value": None},
        "target_country": {"collected": False, "value": None},
        "major_interest": {"collected": False, "value": None},
        "curriculum_system": {"collected": False, "value": None},
    }

    current_grade = str(basic_info.get("current_grade") or "").strip()
    if current_grade:
        basic_info_progress["current_grade"] = {"collected": True, "value": current_grade}

    target_country = str(basic_info.get("CTRY_CODE_VAL") or "").strip()
    if target_country:
        basic_info_progress["target_country"] = {"collected": True, "value": target_country}

    major_interest = str(
        basic_info.get("MAJ_INTEREST_TEXT")
        or basic_info.get("MAJ_CODE_VAL")
        or ""
    ).strip()
    if major_interest:
        basic_info_progress["major_interest"] = {"collected": True, "value": major_interest}

    curriculum_code = _archive_first_curriculum_code(curriculum_rows if isinstance(curriculum_rows, list) else [])
    if curriculum_code:
        basic_info_progress["curriculum_system"] = {"collected": True, "value": curriculum_code}

    academic_has_content = any(
        _archive_value_has_meaningful_content(archive_snapshot.get(table_name))
        for table_name in [
            "student_academic",
            "student_academic_chinese_high_school_subject",
            "student_academic_a_level_subject",
            "student_academic_ap_profile",
            "student_academic_ap_course",
            "student_academic_ib_profile",
            "student_academic_ib_subject",
        ]
    )
    language_has_content = any(
        _archive_value_has_meaningful_content(archive_snapshot.get(table_name))
        for table_name in [
            "student_language",
            "student_language_ielts",
            "student_language_toefl_ibt",
            "student_language_toefl_essentials",
            "student_language_det",
            "student_language_pte",
            "student_language_languagecert",
            "student_language_cambridge",
            "student_language_other",
        ]
    )

    standardized_summary = archive_snapshot.get("student_standardized_tests") or {}
    standardized_records = archive_snapshot.get("student_standardized_test_records") or []
    standardized_has_content = _archive_value_has_meaningful_content(standardized_summary) or _archive_value_has_meaningful_content(
        standardized_records
    )
    standardized_is_not_applicable = (
        isinstance(standardized_summary, dict)
        and str(standardized_summary.get("is_applicable")).strip() in {"0", "False", "false"}
    )
    competition_has_content = any(
        _archive_value_has_meaningful_content(archive_snapshot.get(table_name))
        for table_name in ["student_competitions", "student_competition_entries"]
    )
    activity_has_content = any(
        _archive_value_has_meaningful_content(archive_snapshot.get(table_name))
        for table_name in ["student_activities", "student_activity_entries"]
    )
    project_has_content = any(
        _archive_value_has_meaningful_content(archive_snapshot.get(table_name))
        for table_name in ["student_projects_experience", "student_project_entries", "student_project_outputs"]
    )

    dimension_progress: dict[str, Any] = {
        "academic": {
            "status": "sufficient" if academic_has_content else "missing",
            "reason": "正式档案中已存在学术信息" if academic_has_content else "",
        },
        "language": {
            "status": "sufficient" if language_has_content else "missing",
            "reason": "正式档案中已存在语言信息" if language_has_content else "",
        },
        "standardized": {
            "status": "not_applicable" if standardized_is_not_applicable else ("sufficient" if standardized_has_content else "missing"),
            "reason": "正式档案标记为不适用" if standardized_is_not_applicable else ("正式档案中已存在标化信息" if standardized_has_content else ""),
        },
        "competition": {
            "status": "sufficient" if competition_has_content else "missing",
            "reason": "正式档案中已存在竞赛信息" if competition_has_content else "",
        },
        "activity": {
            "status": "sufficient" if activity_has_content else "missing",
            "reason": "正式档案中已存在活动信息" if activity_has_content else "",
        },
        "project": {
            "status": "sufficient" if project_has_content else "missing",
            "reason": "正式档案中已存在项目或实践信息" if project_has_content else "",
        },
    }

    missing_dimensions = [
        dimension_name
        for dimension_name in ARCHIVE_PROGRESS_DIMENSION_ORDER
        if (dimension_progress.get(dimension_name) or {}).get("status") == "missing"
    ]
    stop_ready = _compute_build_ready_state(
        basic_info_progress=basic_info_progress,
        filtered_missing_dimensions=missing_dimensions,
    )

    return {
        "basic_info_progress": basic_info_progress,
        "dimension_progress": dimension_progress,
        "missing_dimensions": missing_dimensions,
        "next_question_focus": None if stop_ready else _pick_next_question_focus(missing_dimensions),
        "stop_ready": stop_ready,
    }


def _merge_progress_with_archive_snapshot(
    current_progress_json: dict[str, Any] | None,
    archive_snapshot: dict[str, Any] | None,
) -> dict[str, Any]:
    """把正式档案快照里已经明确存在的信息并入当前 progress。"""

    merged_progress = copy.deepcopy(current_progress_json or {})
    archive_hint = _build_archive_snapshot_progress_hint(archive_snapshot)

    basic_info_progress = merged_progress.setdefault("basic_info_progress", {})
    archive_basic_info_progress = archive_hint.get("basic_info_progress") or {}
    for field_name, archive_entry in archive_basic_info_progress.items():
        if _is_basic_progress_field_collected(basic_info_progress, field_name):
            continue
        if not isinstance(archive_entry, dict):
            continue
        if archive_entry.get("collected") is not True:
            continue
        archive_value = str(archive_entry.get("value") or "").strip()
        if not archive_value:
            continue
        basic_info_progress[field_name] = copy.deepcopy(archive_entry)

    _normalize_basic_progress_value_shapes(basic_info_progress)
    _clear_suspicious_current_grade_value(basic_info_progress=basic_info_progress)

    dimension_progress = merged_progress.setdefault("dimension_progress", {})
    archive_dimension_progress = archive_hint.get("dimension_progress") or {}
    for dimension_name in ARCHIVE_PROGRESS_DIMENSION_ORDER:
        archive_entry = archive_dimension_progress.get(dimension_name)
        current_entry = dimension_progress.get(dimension_name)
        current_status = current_entry.get("status") if isinstance(current_entry, dict) else None
        if current_status in {"partial", "sufficient", "not_applicable"}:
            continue
        if isinstance(archive_entry, dict):
            dimension_progress[dimension_name] = copy.deepcopy(archive_entry)
        elif not isinstance(current_entry, dict):
            dimension_progress[dimension_name] = {"status": "missing", "reason": ""}

    negative_answered_dimensions = _normalize_dimension_list(
        merged_progress.get("negative_answered_dimensions") or []
    )
    if negative_answered_dimensions:
        merged_progress["negative_answered_dimensions"] = negative_answered_dimensions
    else:
        merged_progress.pop("negative_answered_dimensions", None)

    missing_dimensions = [
        dimension_name
        for dimension_name in ARCHIVE_PROGRESS_DIMENSION_ORDER
        if (dimension_progress.get(dimension_name) or {}).get("status") == "missing"
        and dimension_name not in negative_answered_dimensions
    ]
    merged_progress["missing_dimensions"] = missing_dimensions
    merged_progress["stop_ready"] = _compute_build_ready_state(
        basic_info_progress=basic_info_progress,
        filtered_missing_dimensions=missing_dimensions,
    )
    merged_progress["next_question_focus"] = (
        None
        if merged_progress["stop_ready"]
        else _pick_next_question_focus(
            missing_dimensions,
            blocked_dimensions=negative_answered_dimensions,
        )
    )
    return merged_progress
