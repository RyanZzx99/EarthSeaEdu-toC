"""
AI Prompt 运行时服务。

这一层的职责是：
1. 从 ai_prompt_configs 表读取当前 active Prompt。
2. 统一调用 OpenAI 兼容协议的模型接口。
3. 同时支持：
   - 一次性返回完整文本
   - 逐块流式返回文本

为什么把“模型调用细节”单独放在这一层：
1. WebSocket 路由只应该关心事件协议，不应该自己解析上游 SSE。
2. pipeline service 只应该关心业务流程，不应该自己拼 HTTP 请求。
3. 后面如果你更换模型供应商，这一层是唯一主要改动点。
"""

from __future__ import annotations

from datetime import date
from datetime import datetime
from decimal import Decimal
import json
from collections.abc import Callable
from collections.abc import Iterator
from dataclasses import dataclass
import logging
from time import perf_counter
from time import sleep
from typing import Any

import requests
from sqlalchemy import case
from sqlalchemy import select
from sqlalchemy.orm import Session

from backend.config.db_conf import settings
from backend.models.ai_chat_models import AiPromptConfig
from backend.services.ai_runtime_config_service import get_effective_ai_runtime_config_map
from backend.utils.flow_logging import build_flow_prefix
from backend.utils.flow_logging import log_flow_info


logger = logging.getLogger(__name__)

_AI_RESPONSE_ENCODING = "utf-8"
_MISCONFIGURED_AI_BASE_URL_SUFFIXES = (
    "/chat/completions",
    "/responses",
)


@dataclass(slots=True)
class PromptExecutionResult:
    """
    一次 Prompt 非流式调用后的最小结果。

    作用：
    1. 返回模型最终正文。
    2. 保留原始响应，便于后续排查供应商返回格式问题。
    """

    prompt_key: str
    model_name: str
    content: str
    raw_response: dict[str, Any]


@dataclass(slots=True)
class PromptStreamChunk:
    """
    流式调用时的一段输出。

    字段说明：
    1. delta_text：本次新收到的文本片段。
    2. accumulated_text：截至当前为止已经累积的完整文本。

    这样设计的好处：
    1. WebSocket 可以直接把 delta_text 发给前端做逐字展示。
    2. 调用方也可以直接拿 accumulated_text 做日志和结束态处理。
    """

    delta_text: str
    accumulated_text: str


@dataclass(slots=True)
class PromptStreamSession:
    """
    一次流式 Prompt 调用返回的可控会话对象。

    这个对象把“文本片段迭代器”和“主动关闭流”的能力绑在一起，作用是：
    1. WebSocket 可以边消费 `iterator`，边把 `delta_text` 推给前端。
    2. 当用户点击“停止生成”时，路由可以调用 `close()`，主动关闭上游 HTTP 流。
    3. 这样 cancel_generation 就不只是前端本地停显，而是会尽快中断上游生成。
    """

    model_name: str
    iterator: Iterator[PromptStreamChunk]
    close: Callable[[], None]


class PromptRuntimeError(RuntimeError):
    """Prompt 运行时错误。"""


def get_active_prompt_config(
    db: Session,
    *,
    prompt_key: str,
) -> AiPromptConfig:
    """
    读取一条当前可用的 active Prompt 配置。

    规则：
    1. 只读取 delete_flag='1' 的有效记录。
    2. 只读取 status='active' 的启用记录。
    3. 若未找到，直接抛错，不做静默回退。
    """

    stmt = (
        select(AiPromptConfig)
        .where(AiPromptConfig.prompt_key == prompt_key)
        .where(AiPromptConfig.status == "active")
        .where(AiPromptConfig.delete_flag == "1")
        .order_by(AiPromptConfig.id.desc())
        .limit(1)
    )
    prompt = db.execute(stmt).scalar_one_or_none()
    if prompt is None:
        raise PromptRuntimeError(f"未找到可用的 active Prompt：{prompt_key}")
    return prompt


def get_prompt_config_by_statuses(
    db: Session,
    *,
    prompt_key: str,
    allowed_statuses: tuple[str, ...],
) -> AiPromptConfig:
    """
    按允许状态读取 Prompt 配置。

    作用：
    1. 旧链路仍然只读 active Prompt。
    2. draft 链路允许读取 draft 状态的 Prompt，方便新 Prompt 先入库再启用。
    """

    normalized_statuses = tuple(
        status.strip() for status in allowed_statuses if isinstance(status, str) and status.strip()
    )
    if not normalized_statuses:
        raise PromptRuntimeError("allowed_statuses 不能为空")

    status_priority = case(
        *[(AiPromptConfig.status == status, index) for index, status in enumerate(normalized_statuses)],
        else_=len(normalized_statuses),
    )
    stmt = (
        select(AiPromptConfig)
        .where(AiPromptConfig.prompt_key == prompt_key)
        .where(AiPromptConfig.status.in_(normalized_statuses))
        .where(AiPromptConfig.delete_flag == "1")
        .order_by(status_priority.asc(), AiPromptConfig.id.desc())
        .limit(1)
    )
    prompt = db.execute(stmt).scalar_one_or_none()
    if prompt is None:
        raise PromptRuntimeError(
            f"未找到可用 Prompt：prompt_key={prompt_key}, allowed_statuses={','.join(normalized_statuses)}"
        )
    return prompt


def execute_prompt_with_context(
    db: Session,
    *,
    prompt_key: str,
    context: dict[str, Any],
) -> PromptExecutionResult:
    """
    非流式执行一条 Prompt。

    适用场景：
    1. progress_extraction_prompt
    2. extraction_prompt
    3. scoring_prompt

    这些 Prompt 更关注结构化、稳定性，没必要走流式逐字展示。
    """

    student_id = str(context.get("student_id") or "") or None
    session_id = str(context.get("session_id") or "") or None

    prompt = get_active_prompt_config(
        db,
        prompt_key=prompt_key,
    )
    runtime_config = get_effective_ai_runtime_config_map(db)
    model_name = _resolve_model_name(prompt, runtime_config)
    safe_context = _to_json_safe(context)
    context_length = _measure_context_length(safe_context)
    timeout_config = _build_timeout_config(runtime_config, stream=False)
    timeout_summary = _format_timeout_config(timeout_config, stream=False)
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="非流式模型调用",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"准备调用模型：{model_name}；"
            f"context长度={context_length} 字符；"
            f"超时配置={timeout_summary}"
        ),
    )
    request_body = _build_chat_completion_body(
        prompt=prompt,
        context=safe_context,
        model_name=model_name,
        runtime_config=runtime_config,
        stream=False,
    )
    start_time = perf_counter()
    try:
        response_json = _post_chat_completion(
            request_body,
            runtime_config=runtime_config,
            timeout_config=timeout_config,
            prompt_key=prompt_key,
            student_id=student_id,
            session_id=session_id,
        )
        content = _extract_assistant_content(response_json)
    except Exception:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.exception(
            "%s 模型调用失败：context长度=%s 字符，超时配置=%s，实际耗时 %.2f ms",
            build_flow_prefix(
                flow_name="AI模型",
                step_name="非流式模型调用",
                student_id=student_id,
                session_id=session_id,
                prompt_key=prompt_key,
            ),
            context_length,
            timeout_summary,
            elapsed_ms,
        )
        raise
    elapsed_ms = (perf_counter() - start_time) * 1000
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="非流式模型调用",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"模型调用完成：context长度={context_length} 字符；"
            f"超时配置={timeout_summary}；"
            f"实际耗时={elapsed_ms:.2f} ms"
        ),
    )
    return PromptExecutionResult(
        prompt_key=prompt_key,
        model_name=model_name,
        content=content,
        raw_response=response_json,
    )


def execute_prompt_with_context_by_statuses(
    db: Session,
    *,
    prompt_key: str,
    context: dict[str, Any],
    allowed_statuses: tuple[str, ...],
) -> PromptExecutionResult:
    """
    按指定状态集合执行 Prompt。

    作用：
    1. 给智能建档 draft 链路单独使用。
    2. 旧 execute_prompt_with_context 保持不动，避免影响现有业务接口。
    """

    student_id = str(context.get("student_id") or "") or None
    session_id = str(context.get("session_id") or "") or None

    prompt = get_prompt_config_by_statuses(
        db,
        prompt_key=prompt_key,
        allowed_statuses=allowed_statuses,
    )
    runtime_config = get_effective_ai_runtime_config_map(db)
    model_name = _resolve_model_name(prompt, runtime_config)
    safe_context = _to_json_safe(context)
    context_length = _measure_context_length(safe_context)
    timeout_config = _build_timeout_config(runtime_config, stream=False)
    timeout_summary = _format_timeout_config(timeout_config, stream=False)
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="多状态 Prompt 非流式调用",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"准备调用模型：{model_name}，"
            f"context长度={context_length} 字符，"
            f"允许状态={','.join(allowed_statuses)}，"
            f"超时配置={timeout_summary}"
        ),
    )
    request_body = _build_chat_completion_body(
        prompt=prompt,
        context=safe_context,
        model_name=model_name,
        runtime_config=runtime_config,
        stream=False,
    )
    start_time = perf_counter()
    try:
        response_json = _post_chat_completion(
            request_body,
            runtime_config=runtime_config,
            timeout_config=timeout_config,
            prompt_key=prompt_key,
            student_id=student_id,
            session_id=session_id,
        )
        content = _extract_assistant_content(response_json)
    except Exception:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.exception(
            "%s 多状态 Prompt 调用失败，context长度=%s 字符，允许状态=%s，超时配置=%s，实际耗时 %.2f ms",
            build_flow_prefix(
                flow_name="AI模型",
                step_name="多状态 Prompt 非流式调用",
                student_id=student_id,
                session_id=session_id,
                prompt_key=prompt_key,
            ),
            context_length,
            ",".join(allowed_statuses),
            timeout_summary,
            elapsed_ms,
        )
        raise
    elapsed_ms = (perf_counter() - start_time) * 1000
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="多状态 Prompt 非流式调用",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"模型调用完成，context长度={context_length} 字符，"
            f"允许状态={','.join(allowed_statuses)}，"
            f"超时配置={timeout_summary}，"
            f"实际耗时={elapsed_ms:.2f} ms"
        ),
    )
    return PromptExecutionResult(
        prompt_key=prompt_key,
        model_name=model_name,
        content=content,
        raw_response=response_json,
    )


def stream_prompt_with_context(
    db: Session,
    *,
    prompt_key: str,
    context: dict[str, Any],
) -> PromptStreamSession:
    """
    流式执行一条 Prompt，并返回一个文本片段迭代器。

    当前主要用于：
    1. conversation_prompt 的 assistant 回复流式展示。

    返回值说明：
    1. 第一个值是本次实际使用的 model_name。
    2. 第二个值是一个同步迭代器，逐段返回 PromptStreamChunk。

    为什么这里返回迭代器而不是一次性 list：
    1. 这样 WebSocket 可以边收到上游 chunk，边立刻推给前端。
    2. 不需要等完整回答全部结束后再统一发出。
    """

    student_id = str(context.get("student_id") or "") or None
    session_id = str(context.get("session_id") or "") or None

    prompt = get_active_prompt_config(
        db,
        prompt_key=prompt_key,
    )
    runtime_config = get_effective_ai_runtime_config_map(db)
    model_name = _resolve_model_name(prompt, runtime_config)
    safe_context = _to_json_safe(context)
    context_length = _measure_context_length(safe_context)
    timeout_config = _build_timeout_config(runtime_config, stream=True)
    timeout_summary = _format_timeout_config(timeout_config, stream=True)
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="建立流式模型连接",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"准备调用模型：{model_name}；"
            f"context长度={context_length} 字符；"
            f"超时配置={timeout_summary}"
        ),
    )
    request_body = _build_chat_completion_body(
        prompt=prompt,
        context=safe_context,
        model_name=model_name,
        runtime_config=runtime_config,
        stream=True,
    )
    start_time = perf_counter()
    try:
        response = _open_chat_completion_stream(
            request_body,
            runtime_config=runtime_config,
            timeout_config=timeout_config,
        )
    except Exception:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.exception(
            "%s 建立流式连接失败：context长度=%s 字符，超时配置=%s，实际耗时 %.2f ms",
            build_flow_prefix(
                flow_name="AI模型",
                step_name="建立流式模型连接",
                student_id=student_id,
                session_id=session_id,
                prompt_key=prompt_key,
            ),
            context_length,
            timeout_summary,
            elapsed_ms,
        )
        raise
    elapsed_ms = (perf_counter() - start_time) * 1000
    log_flow_info(
        logger,
        flow_name="AI模型",
        step_name="建立流式模型连接",
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
        message=(
            f"流式连接建立完成：context长度={context_length} 字符；"
            f"超时配置={timeout_summary}；"
            f"实际耗时={elapsed_ms:.2f} ms"
        ),
    )
    return PromptStreamSession(
        model_name=model_name,
        iterator=_iter_chat_completion_stream(
            response,
            student_id=student_id,
            session_id=session_id,
            prompt_key=prompt_key,
            context_length=context_length,
            timeout_summary=timeout_summary,
        ),
        close=response.close,
    )


def _build_chat_completion_body(
    *,
    prompt: AiPromptConfig,
    context: dict[str, Any],
    model_name: str,
    runtime_config: dict[str, Any],
    stream: bool,
) -> dict[str, Any]:
    """
    构建统一的 chat/completions 请求体。

    统一策略：
    1. system 消息放 Prompt 正文。
    2. user 消息放程序构建好的 JSON 上下文。
    3. stream 参数由调用方决定是否开启流式。
    """

    messages: list[dict[str, str]] = [
        {
            "role": "system",
            "content": prompt.prompt_content,
        }
    ]

    extra_system_message = _build_runtime_system_control_message(
        prompt_key=prompt.prompt_key,
        context=context,
    )
    if extra_system_message:
        messages.append(
            {
                "role": "system",
                "content": extra_system_message,
            }
        )

    messages.append(
        {
            "role": "user",
            "content": _dump_json_text(context),
        }
    )

    request_body = {
        "model": model_name,
        "messages": messages,
        "temperature": _resolve_temperature(prompt, runtime_config),
        "stream": stream,
    }

    if prompt.max_tokens is not None:
        request_body["max_tokens"] = prompt.max_tokens

    return request_body


def _build_runtime_system_control_message(
    *,
    prompt_key: str,
    context: dict[str, Any],
) -> str | None:
    """
    按 prompt 类型生成“程序侧强控制 system message”。

    设计原因：
    1. 单纯把 progress JSON 放进 user context 里，模型有时会看到但不严格遵守。
    2. conversation 场景最需要硬约束，否则容易重复追问已经确认过的基础信息。
    3. 因此这里额外插入一条更高优先级的 system message，把本轮必须遵守的规则直接讲清楚。
    """

    if prompt_key == "student_profile_build.extraction":
        return _build_extraction_runtime_control_message()

    if prompt_key not in {
        "student_profile_build.conversation",
    }:
        return None

    conversation_control = context.get("conversation_control")
    if not isinstance(conversation_control, dict):
        return None

    confirmed_basic_values = conversation_control.get("confirmed_basic_values") or {}
    do_not_repeat_basic_fields = conversation_control.get("do_not_repeat_basic_fields") or []
    missing_basic_fields = conversation_control.get("missing_basic_fields") or []
    next_basic_question_field = conversation_control.get("next_basic_question_field")
    do_not_repeat_dimensions = conversation_control.get("do_not_repeat_dimensions") or []
    missing_dimensions = conversation_control.get("missing_dimensions") or []
    next_question_focus = conversation_control.get("next_question_focus")
    stop_ready = conversation_control.get("stop_ready") is True

    lines = [
        "你必须优先服从下面这组程序侧对话控制规则。",
        "如果这些规则与泛化习惯冲突，以这里为准。",
    ]

    if confirmed_basic_values:
        lines.append(
            "以下基础信息已经确认："
            + json.dumps(confirmed_basic_values, ensure_ascii=False)
        )
    if do_not_repeat_basic_fields:
        lines.append(
            "以下字段已经收集完成，禁止重复追问："
            + "、".join(str(item) for item in do_not_repeat_basic_fields)
        )
    if missing_basic_fields:
        lines.append(
            "以下基础必填字段仍未收集完成："
            + "、".join(str(item) for item in missing_basic_fields)
        )
    if do_not_repeat_dimensions:
        lines.append(
            "以下维度学生已经明确回答过没有相关经历，本轮禁止继续追问："
            + "、".join(str(item) for item in do_not_repeat_dimensions)
        )
    if stop_ready:
        lines.append("当前信息已经足够生成六维图，本轮不要继续追问新的基础槽位；只需简短说明现在可以建档，如学生还想补充，再引导补弱项。")
    elif next_basic_question_field:
        lines.append(
            f"下一轮必须优先追问基础槽位：{next_basic_question_field}。"
        )
    elif next_question_focus:
        lines.append(f"下一轮必须优先追问维度：{next_question_focus}。")
    if missing_dimensions:
        lines.append(
            "当前仍缺失的维度："
            + "、".join(str(item) for item in missing_dimensions)
        )

    lines.extend(
        [
            "如果用户刚刚已经回答了某个问题，绝不能在同一轮回复里再次追问同一个槽位。",
            "只要还有基础必填槽位未收齐，就必须先追问基础槽位；只有基础槽位全部收齐后，才能追问维度信息。",
            "当基础槽位已全部收齐时，再优先围绕 next_question_focus 追问一个最关键的问题，不要绕回已确认基础信息。",
        ]
    )
    lines.append(
        "如果学生刚刚对某个维度回答了“没有”“没做过”“暂时没有”，这也算已经回答，不要换一种说法重复追问同一维度。"
    )
    return "\n".join(lines)


def _build_extraction_runtime_control_message() -> str:
    """
    为 extraction_prompt 追加程序侧强约束。

    这层约束的目的很明确：
    1. 经历类枚举字段必须优先由模型直接输出标准码值
    2. 不允许输出中文自然语言枚举值，例如“校内”“负责人”“科研”
    3. 如果无法确定，就直接输出 null，不要编造，也不要输出中文占位

    这样可以把“语义理解”留在 prompt 侧完成，后端只做 allowed set 校验。
    """

    # 中文注释：
    # 数据库正文里只保留任务、schema 和最少量抽取规则；
    # 这里专门保留“程序强约束”，避免和数据库正文重复叠加。
    return "\n".join(
        [
            "以下是程序侧强约束：只对码值、枚举、学术科目字段施加硬限制；不确定就填 null，不得猜测。",
            "经历类字段只能输出标准码值，不能输出中文枚举值。",
            "student_competition_entries: competition_field={MATH,CS,PHYSICS,CHEM,BIO,ECON,DEBATE,WRITING,OTHER}; competition_tier={T1,T2,T3,T4,UNKNOWN}; competition_level={SCHOOL,CITY,PROVINCE,NATIONAL,INTERNATIONAL}; evidence_type={CERTIFICATE,LINK,SCHOOL_CONFIRMATION,NONE}",
            "student_activity_entries: activity_category={LEADERSHIP,ACADEMIC,SPORTS,ARTS,COMMUNITY,ENTREPRENEURSHIP,OTHER}; activity_role={FOUNDER,PRESIDENT,CORE_MEMBER,MEMBER,OTHER}; evidence_type={LINK,SCHOOL_CONFIRMATION,PHOTO,NONE}",
            "student_project_entries: project_type={RESEARCH,INTERNSHIP,ENGINEERING_PROJECT,STARTUP,CREATIVE_PROJECT,VOLUNTEER_WORK,OTHER}; project_field={CS,ECON,FIN,BIO,PHYS,DESIGN,OTHER}; relevance_to_major={HIGH,MEDIUM,LOW}; evidence_type={LINK,MENTOR_LETTER,EMPLOYER_CONFIRMATION,NONE}",
            "student_project_outputs.output_type={PAPER,REPORT,CODE,DEMO,PRODUCT,PORTFOLIO,PRESENTATION,OTHER}",
            "四类学术科目明细都要尽量同时输出 subject_name_text 和对应 id；科目名不要放进 notes。",
            "A-Level: al_subject_id={AL_MATH,AL_FURTHER_MATH,AL_PHYSICS,AL_CHEMISTRY,AL_BIOLOGY,AL_ECONOMICS,AL_HISTORY,AL_GEOGRAPHY,AL_ENGLISH_LANGUAGE,AL_ENGLISH_LITERATURE,AL_CHINESE,AL_COMPUTER_SCIENCE,AL_BUSINESS,AL_PSYCHOLOGY,AL_ART_DESIGN}",
            "AP: ap_course_id={AP_CALCULUS_AB,AP_CALCULUS_BC,AP_STATISTICS,AP_PHYSICS_1,AP_PHYSICS_C_MECH,AP_CHEMISTRY,AP_BIOLOGY,AP_COMPUTER_SCIENCE_A,AP_MICROECONOMICS,AP_MACROECONOMICS,AP_WORLD_HISTORY,AP_US_HISTORY,AP_ENGLISH_LANGUAGE,AP_ENGLISH_LITERATURE,AP_CHINESE_LANGUAGE_CULTURE,AP_PSYCHOLOGY,AP_ART_HISTORY}",
            "IB: ib_subject_id={IB_LANG_A_LIT,IB_LANG_A_LANG_LIT,IB_ENGLISH_B,IB_CHINESE_B,IB_ECONOMICS,IB_HISTORY,IB_GEOGRAPHY,IB_PSYCHOLOGY,IB_BUSINESS_MANAGEMENT,IB_PHYSICS,IB_CHEMISTRY,IB_BIOLOGY,IB_COMPUTER_SCIENCE,IB_MATH_AA,IB_MATH_AI,IB_VISUAL_ARTS,IB_TOK,IB_EE}",
            "中国普高: chs_subject_id={CHS_CHINESE,CHS_MATH,CHS_ENGLISH,CHS_PHYSICS,CHS_CHEMISTRY,CHS_BIOLOGY,CHS_HISTORY,CHS_GEOGRAPHY,CHS_POLITICS}",
            "A-Level 明细若输出，必须尽量同时给出 al_subject_id 和 stage_code；stage_code={AS,A2,FULL_A_LEVEL,IN_PROGRESS}; grade_code={A*,A,B,C,D,E,U,NA}; board_code={CAIE,EDEXCEL,AQA,OCR,OTHER,UNKNOWN}; session_code={MAY_JUNE,OCT_NOV,JAN,OTHER,UNKNOWN}。缺 stage_code 时不要输出该行。",
            "IB 明细若输出，必须尽量同时给出 ib_subject_id 和 level_code；level_code={HL,SL}。缺 level_code 时不要输出该行。",
        ]
    )


def _resolve_model_name(
    prompt: AiPromptConfig,
    runtime_config: dict[str, Any],
) -> str:
    """决定本次调用使用的模型名称。"""

    model_name = (
        prompt.model_name
        or runtime_config.get("AI_MODEL_DEFAULT_NAME")
        or settings.ai_model_default_name
        or ""
    ).strip()
    if not model_name:
        raise PromptRuntimeError("未配置可用的 AI 模型名称")
    return model_name


def _resolve_temperature(
    prompt: AiPromptConfig,
    runtime_config: dict[str, Any],
) -> float:
    """决定本次调用使用的 temperature。"""

    if prompt.temperature is not None:
        return float(prompt.temperature)
    return float(
        runtime_config.get("AI_MODEL_DEFAULT_TEMPERATURE")
        or settings.ai_model_default_temperature
    )


def _should_retry_request_exception(exc: requests.RequestException) -> bool:
    """
    判断这次请求异常是否适合自动重试。

    中文注释：
    1. 这里只兜底网络层抖动，不对业务层参数错误做重试。
    2. Timeout / ConnectionError 通常属于“重试一次有机会成功”的类型。
    """

    return isinstance(exc, (requests.Timeout, requests.ConnectionError))


def _should_retry_status_code(status_code: int) -> bool:
    """
    判断状态码是否适合自动重试。

    中文注释：
    1. 5xx、408、429 更像临时性失败。
    2. 普通 4xx 往往是请求本身有问题，不应自动重试。
    """

    return status_code >= 500 or status_code in {408, 429}


def _post_chat_completion(
    request_body: dict[str, Any],
    *,
    runtime_config: dict[str, Any],
    timeout_config: tuple[float, float],
    prompt_key: str | None = None,
    student_id: str | None = None,
    session_id: str | None = None,
) -> dict[str, Any]:
    """
    调用非流式 chat/completions 接口。

    当前约束：
    1. 需要已配置 AI_MODEL_BASE_URL。
    2. 需要已配置 AI_MODEL_API_KEY。
    """

    url, headers = _build_request_target(runtime_config)
    retry_count = max(int(settings.ai_model_non_stream_retry_count), 0)
    backoff_seconds = max(
        float(settings.ai_model_non_stream_retry_backoff_seconds),
        0.0,
    )

    for attempt_index in range(retry_count + 1):
        attempt_no = attempt_index + 1
        try:
            response = requests.post(
                url,
                headers=headers,
                json=request_body,
                timeout=timeout_config,
            )
        except requests.RequestException as exc:
            if attempt_index < retry_count and _should_retry_request_exception(exc):
                logger.warning(
                    "%s 非流式模型调用第 %s 次尝试失败，%.2f 秒后重试：%s",
                    build_flow_prefix(
                        flow_name="AI模型",
                        step_name="非流式模型调用重试",
                        student_id=student_id,
                        session_id=session_id,
                        prompt_key=prompt_key,
                    ),
                    attempt_no,
                    backoff_seconds,
                    exc,
                )
                if backoff_seconds > 0:
                    sleep(backoff_seconds)
                continue
            raise PromptRuntimeError(
                f"调用 AI 模型接口失败（超时配置：{_format_timeout_config(timeout_config, stream=False)}）：{exc}"
            ) from exc

        _force_ai_response_encoding(response)
        if response.status_code >= 400:
            response_text = _read_ai_response_text(response)
            response.close()
            if attempt_index < retry_count and _should_retry_status_code(response.status_code):
                logger.warning(
                    "%s 非流式模型调用返回状态码 %s，第 %s 次尝试失败，%.2f 秒后重试。",
                    build_flow_prefix(
                        flow_name="AI模型",
                        step_name="非流式模型调用重试",
                        student_id=student_id,
                        session_id=session_id,
                        prompt_key=prompt_key,
                    ),
                    response.status_code,
                    attempt_no,
                    backoff_seconds,
                )
                if backoff_seconds > 0:
                    sleep(backoff_seconds)
                continue
            raise PromptRuntimeError(
                f"AI 模型接口返回异常状态码：{response.status_code}，响应内容：{response_text}"
            )

        try:
            return _load_ai_response_json(response)
        except UnicodeDecodeError as exc:
            raise PromptRuntimeError("AI 模型接口返回内容不是合法 UTF-8 JSON") from exc
        except json.JSONDecodeError as exc:
            raise PromptRuntimeError("AI 模型接口未返回合法 JSON") from exc
        finally:
            response.close()

    raise PromptRuntimeError("AI 模型接口重试后仍然失败")


def _open_chat_completion_stream(
    request_body: dict[str, Any],
    *,
    runtime_config: dict[str, Any],
    timeout_config: tuple[float, float],
) -> requests.Response:
    """
    发起一次真正的流式 HTTP 请求，并返回底层响应对象。

    单独拆出这个函数的原因：
    1. 让上层能拿到 `response.close()`，用于用户主动取消生成。
    2. 让“建立流连接”和“解析流内容”两个职责分离，后续更容易定位问题。
    """

    url, headers = _build_request_target(runtime_config)
    try:
        response = requests.post(
            url,
            headers=headers,
            json=request_body,
            timeout=timeout_config,
            stream=True,
        )
    except requests.RequestException as exc:
        raise PromptRuntimeError(
            f"调用 AI 模型流式接口失败（超时配置：{_format_timeout_config(timeout_config, stream=True)}）：{exc}"
        ) from exc

    _force_ai_response_encoding(response)
    if response.status_code >= 400:
        response_text = _read_ai_response_text(response)
        response.close()
        raise PromptRuntimeError(
            f"AI 模型流式接口返回异常状态码：{response.status_code}，响应内容：{response_text}"
        )

    return response


def _iter_chat_completion_stream(
    response: requests.Response,
    *,
    student_id: str | None,
    session_id: str | None,
    prompt_key: str,
    context_length: int,
    timeout_summary: str,
) -> Iterator[PromptStreamChunk]:
    """
    逐行解析上游 SSE 流式响应。

    当前假设供应商兼容 OpenAI chat/completions 的 stream 协议：
    1. 每行以 `data:` 开头。
    2. 最终以 `data: [DONE]` 结束。
    3. 文本增量位于 `choices[0].delta.content`。

    注意：
    1. 这是一个同步迭代器，因为当前底层仍使用 requests。
    2. 对当前项目先够用，后续如果并发上来，可换成异步 HTTP 客户端。
    """

    accumulated_text = ""
    chunk_count = 0
    start_time = perf_counter()
    try:
        for raw_line in response.iter_lines(decode_unicode=False):
            if not raw_line:
                continue

            line = _decode_ai_response_line(raw_line).strip()
            if not line.startswith("data:"):
                continue

            data = line.removeprefix("data:").strip()
            if data == "[DONE]":
                break

            try:
                event_json = json.loads(data)
            except json.JSONDecodeError:
                # 中文注释：某些兼容供应商在流式返回里可能插入非 JSON 行。
                # 这里先忽略异常行，避免整个会话直接中断。
                continue

            delta_text = _extract_stream_delta_text(event_json)
            if not delta_text:
                continue

            accumulated_text += delta_text
            chunk_count += 1
            yield PromptStreamChunk(
                delta_text=delta_text,
                accumulated_text=accumulated_text,
            )
    except Exception:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.exception(
            "%s 流式模型调用失败：context长度=%s 字符，超时配置=%s，实际耗时 %.2f ms",
            build_flow_prefix(
                flow_name="AI模型",
                step_name="流式模型调用",
                student_id=student_id,
                session_id=session_id,
                prompt_key=prompt_key,
            ),
            context_length,
            timeout_summary,
            elapsed_ms,
        )
        raise
    else:
        elapsed_ms = (perf_counter() - start_time) * 1000
        log_flow_info(
            logger,
            flow_name="AI模型",
            step_name="流式模型调用",
            student_id=student_id,
            session_id=session_id,
            prompt_key=prompt_key,
            message=(
                f"流式模型调用完成：context长度={context_length} 字符；"
                f"超时配置={timeout_summary}；"
                f"实际耗时={elapsed_ms:.2f} ms；"
                f"输出片段数={chunk_count}"
            ),
        )
    finally:
        # 中文注释：无论是自然结束、异常结束，还是外部主动调用 close()，
        # 都在这里统一关闭底层 HTTP 响应，避免连接泄漏。
        response.close()


def _build_request_target(
    runtime_config: dict[str, Any],
) -> tuple[str, dict[str, str]]:
    """
    构建模型接口地址和请求头。

    当前统一走 OpenAI 兼容 `chat/completions`。
    """

    raw_base_url = str(
        runtime_config.get("AI_MODEL_BASE_URL")
        or settings.ai_model_base_url
        or ""
    ).strip()
    base_url = _normalize_chat_completion_base_url(raw_base_url)
    api_key = str(
        runtime_config.get("AI_MODEL_API_KEY")
        or settings.ai_model_api_key
        or ""
    ).strip()
    if not base_url:
        raise PromptRuntimeError("未配置 AI 模型基础地址 ai_model_base_url")
    if not api_key:
        raise PromptRuntimeError("未配置 AI 模型 API Key")

    url = f"{base_url}/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    return url, headers


def _normalize_chat_completion_base_url(base_url: str) -> str:
    """Normalize API root URLs that were mistakenly configured with endpoint paths."""

    normalized_base_url = (base_url or "").strip().rstrip("/")
    for suffix in _MISCONFIGURED_AI_BASE_URL_SUFFIXES:
        if normalized_base_url.endswith(suffix):
            corrected_base_url = normalized_base_url[: -len(suffix)].rstrip("/")
            if corrected_base_url:
                logger.warning(
                    "AI_MODEL_BASE_URL should point to the API root. "
                    "Auto-corrected %s to %s before requesting chat/completions.",
                    normalized_base_url,
                    corrected_base_url,
                )
                return corrected_base_url
    return normalized_base_url


def _force_ai_response_encoding(response: requests.Response) -> None:
    """OpenAI-compatible JSON/SSE payloads are UTF-8; force that instead of vendor headers."""

    response.encoding = _AI_RESPONSE_ENCODING


def _read_ai_response_text(response: requests.Response) -> str:
    """Decode AI HTTP responses as UTF-8 so Chinese content is not mangled by wrong charset headers."""

    return response.content.decode(_AI_RESPONSE_ENCODING, errors="replace")


def _load_ai_response_json(response: requests.Response) -> dict[str, Any]:
    """Parse AI JSON responses from UTF-8 bytes instead of requests' charset guess."""

    return json.loads(response.content.decode(_AI_RESPONSE_ENCODING))


def _decode_ai_response_line(raw_line: bytes | str) -> str:
    """Decode one SSE line from UTF-8 bytes."""

    if isinstance(raw_line, bytes):
        return raw_line.decode(_AI_RESPONSE_ENCODING)
    return raw_line


def _measure_context_length(context: dict[str, Any]) -> int:
    """统计传给模型的 context JSON 字符长度。"""

    return len(_dump_json_text(context))


def _dump_json_text(value: Any) -> str:
    """把上下文统一转成 JSON 安全文本。"""

    return json.dumps(_to_json_safe(value), ensure_ascii=False)


def _to_json_safe(value: Any) -> Any:
    """把非 JSON 原生类型转换成安全值。"""

    if isinstance(value, dict):
        return {key: _to_json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_to_json_safe(item) for item in value]
    if isinstance(value, tuple):
        return [_to_json_safe(item) for item in value]
    if isinstance(value, set):
        return [_to_json_safe(item) for item in value]
    if isinstance(value, Decimal):
        return int(value) if value == value.to_integral_value() else float(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, date):
        return value.isoformat()
    return value


def _build_timeout_config(
    runtime_config: dict[str, Any],
    *,
    stream: bool,
) -> tuple[float, float]:
    """生成 requests 需要的 (connect_timeout, read_timeout) 配置。"""

    connect_timeout = float(
        runtime_config.get("AI_MODEL_CONNECT_TIMEOUT_SECONDS")
        or settings.ai_model_connect_timeout_seconds
    )
    read_timeout_value = (
        runtime_config.get("AI_MODEL_STREAM_READ_TIMEOUT_SECONDS")
        if stream
        else runtime_config.get("AI_MODEL_READ_TIMEOUT_SECONDS")
    )
    if read_timeout_value in (None, ""):
        read_timeout_value = (
            settings.ai_model_stream_read_timeout_seconds
            if stream
            else settings.ai_model_read_timeout_seconds
        )
    read_timeout = float(read_timeout_value)
    return connect_timeout, read_timeout


def _format_timeout_config(
    timeout_config: tuple[float, float],
    *,
    stream: bool,
) -> str:
    """把超时配置格式化成便于日志阅读的文本。"""

    connect_timeout, read_timeout = timeout_config
    read_label = "stream_read" if stream else "read"
    return f"connect={connect_timeout:g}s, {read_label}={read_timeout:g}s"


def _extract_assistant_content(response_json: dict[str, Any]) -> str:
    """
    从非流式响应中提取 assistant 正文。

    标准结构：
    response_json["choices"][0]["message"]["content"]
    """

    choices = response_json.get("choices")
    if not isinstance(choices, list) or not choices:
        raise PromptRuntimeError("AI 模型返回结果缺少 choices")

    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        raise PromptRuntimeError("AI 模型返回 choices[0] 结构不合法")

    message = first_choice.get("message")
    if not isinstance(message, dict):
        raise PromptRuntimeError("AI 模型返回结果缺少 message")

    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise PromptRuntimeError("AI 模型返回内容为空")

    return content.strip()


def _extract_stream_delta_text(event_json: dict[str, Any]) -> str:
    """
    从流式 chunk 中提取文本增量。

    兼容两种常见情况：
    1. delta.content 是字符串
    2. delta.content 是内容数组（部分兼容实现会这么返回）
    """

    choices = event_json.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""

    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        return ""

    delta = first_choice.get("delta")
    if not isinstance(delta, dict):
        return ""

    content = delta.get("content")
    if isinstance(content, str):
        return content

    if isinstance(content, list):
        texts: list[str] = []
        for item in content:
            if isinstance(item, dict):
                text_part = item.get("text")
                if isinstance(text_part, str):
                    texts.append(text_part)
        return "".join(texts)

    return ""
