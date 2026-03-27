from __future__ import annotations

import logging
from contextlib import contextmanager
from time import perf_counter
from typing import Iterator


def build_flow_prefix(
    *,
    flow_name: str,
    step_name: str | None = None,
    student_id: str | None = None,
    session_id: str | None = None,
    prompt_key: str | None = None,
) -> str:
    """构建统一的中文流程日志前缀。"""

    parts = [f"[{flow_name}]"]
    if step_name:
        parts.append(f"[步骤:{step_name}]")
    if student_id:
        parts.append(f"[学生:{student_id}]")
    if session_id:
        parts.append(f"[会话:{session_id}]")
    if prompt_key:
        parts.append(f"[Prompt:{prompt_key}]")
    return "".join(parts)


def log_flow_info(
    logger: logging.Logger,
    *,
    flow_name: str,
    message: str,
    step_name: str | None = None,
    student_id: str | None = None,
    session_id: str | None = None,
    prompt_key: str | None = None,
) -> None:
    """输出一条统一格式的流程日志。"""

    logger.info(
        "%s %s",
        build_flow_prefix(
            flow_name=flow_name,
            step_name=step_name,
            student_id=student_id,
            session_id=session_id,
            prompt_key=prompt_key,
        ),
        message,
    )


@contextmanager
def log_timed_step(
    logger: logging.Logger,
    *,
    flow_name: str,
    step_name: str,
    student_id: str | None = None,
    session_id: str | None = None,
    prompt_key: str | None = None,
) -> Iterator[None]:
    """记录步骤开始、结束和耗时。"""

    prefix = build_flow_prefix(
        flow_name=flow_name,
        step_name=step_name,
        student_id=student_id,
        session_id=session_id,
        prompt_key=prompt_key,
    )
    start_time = perf_counter()
    logger.info("%s 开始", prefix)
    try:
        yield
    except Exception:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.exception("%s 失败，耗时 %.2f ms", prefix, elapsed_ms)
        raise
    else:
        elapsed_ms = (perf_counter() - start_time) * 1000
        logger.info("%s 完成，耗时 %.2f ms", prefix, elapsed_ms)
