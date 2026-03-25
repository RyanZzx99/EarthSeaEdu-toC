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

import json
from collections.abc import Callable
from collections.abc import Iterator
from dataclasses import dataclass
from typing import Any

import requests
from sqlalchemy import select
from sqlalchemy.orm import Session

from backend.config.db_conf import settings
from backend.models.ai_chat_models import AiPromptConfig


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

    prompt = get_active_prompt_config(
        db,
        prompt_key=prompt_key,
    )
    model_name = _resolve_model_name(prompt)
    request_body = _build_chat_completion_body(
        prompt=prompt,
        context=context,
        model_name=model_name,
        stream=False,
    )

    response_json = _post_chat_completion(request_body)
    content = _extract_assistant_content(response_json)
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

    prompt = get_active_prompt_config(
        db,
        prompt_key=prompt_key,
    )
    model_name = _resolve_model_name(prompt)
    request_body = _build_chat_completion_body(
        prompt=prompt,
        context=context,
        model_name=model_name,
        stream=True,
    )
    response = _open_chat_completion_stream(request_body)
    return PromptStreamSession(
        model_name=model_name,
        iterator=_iter_chat_completion_stream(response),
        close=response.close,
    )


def _build_chat_completion_body(
    *,
    prompt: AiPromptConfig,
    context: dict[str, Any],
    model_name: str,
    stream: bool,
) -> dict[str, Any]:
    """
    构建统一的 chat/completions 请求体。

    统一策略：
    1. system 消息放 Prompt 正文。
    2. user 消息放程序构建好的 JSON 上下文。
    3. stream 参数由调用方决定是否开启流式。
    """

    request_body = {
        "model": model_name,
        "messages": [
            {
                "role": "system",
                "content": prompt.prompt_content,
            },
            {
                "role": "user",
                "content": json.dumps(context, ensure_ascii=False),
            },
        ],
        "temperature": _resolve_temperature(prompt),
        "stream": stream,
    }

    if prompt.max_tokens is not None:
        request_body["max_tokens"] = prompt.max_tokens

    return request_body


def _resolve_model_name(prompt: AiPromptConfig) -> str:
    """决定本次调用使用的模型名称。"""

    model_name = (prompt.model_name or settings.ai_model_default_name or "").strip()
    if not model_name:
        raise PromptRuntimeError("未配置可用的 AI 模型名称")
    return model_name


def _resolve_temperature(prompt: AiPromptConfig) -> float:
    """决定本次调用使用的 temperature。"""

    if prompt.temperature is not None:
        return float(prompt.temperature)
    return float(settings.ai_model_default_temperature)


def _post_chat_completion(request_body: dict[str, Any]) -> dict[str, Any]:
    """
    调用非流式 chat/completions 接口。

    当前约束：
    1. 需要已配置 AI_MODEL_BASE_URL。
    2. 需要已配置 AI_MODEL_API_KEY。
    """

    url, headers = _build_request_target()
    try:
        response = requests.post(
            url,
            headers=headers,
            json=request_body,
            timeout=settings.ai_model_timeout_seconds,
        )
    except requests.RequestException as exc:
        raise PromptRuntimeError(f"调用 AI 模型接口失败：{exc}") from exc

    if response.status_code >= 400:
        raise PromptRuntimeError(
            f"AI 模型接口返回异常状态码：{response.status_code}，响应内容：{response.text}"
        )

    try:
        return response.json()
    except ValueError as exc:
        raise PromptRuntimeError("AI 模型接口未返回合法 JSON") from exc


def _open_chat_completion_stream(request_body: dict[str, Any]) -> requests.Response:
    """
    发起一次真正的流式 HTTP 请求，并返回底层响应对象。

    单独拆出这个函数的原因：
    1. 让上层能拿到 `response.close()`，用于用户主动取消生成。
    2. 让“建立流连接”和“解析流内容”两个职责分离，后续更容易定位问题。
    """

    url, headers = _build_request_target()
    try:
        response = requests.post(
            url,
            headers=headers,
            json=request_body,
            timeout=settings.ai_model_timeout_seconds,
            stream=True,
        )
    except requests.RequestException as exc:
        raise PromptRuntimeError(f"调用 AI 模型流式接口失败：{exc}") from exc

    if response.status_code >= 400:
        raise PromptRuntimeError(
            f"AI 模型流式接口返回异常状态码：{response.status_code}，响应内容：{response.text}"
        )

    return response


def _iter_chat_completion_stream(response: requests.Response) -> Iterator[PromptStreamChunk]:
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
    try:
        for raw_line in response.iter_lines(decode_unicode=True):
            if not raw_line:
                continue

            line = raw_line.strip()
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
            yield PromptStreamChunk(
                delta_text=delta_text,
                accumulated_text=accumulated_text,
            )
    finally:
        # 中文注释：无论是自然结束、异常结束，还是外部主动调用 close()，
        # 都在这里统一关闭底层 HTTP 响应，避免连接泄漏。
        response.close()


def _build_request_target() -> tuple[str, dict[str, str]]:
    """
    构建模型接口地址和请求头。

    当前统一走 OpenAI 兼容 `chat/completions`。
    """

    base_url = settings.ai_model_base_url.strip().rstrip("/")
    api_key = settings.ai_model_api_key.strip()
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
