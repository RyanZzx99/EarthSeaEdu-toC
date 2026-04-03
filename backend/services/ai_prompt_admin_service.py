from __future__ import annotations

from typing import Any

from sqlalchemy import or_
from sqlalchemy.orm import Session

from backend.models.ai_chat_models import AiPromptConfig


def list_ai_prompt_configs(
    db: Session,
    biz_domain: str | None = None,
    prompt_stage: str | None = None,
    status: str | None = None,
    keyword: str | None = None,
    limit: int = 50,
) -> tuple[list[AiPromptConfig], int]:
    safe_limit = max(1, min(limit, 200))

    query = db.query(AiPromptConfig).filter(AiPromptConfig.delete_flag == "1")

    if biz_domain:
        query = query.filter(AiPromptConfig.biz_domain == biz_domain.strip())

    if prompt_stage:
        query = query.filter(AiPromptConfig.prompt_stage == prompt_stage.strip())

    if status:
        query = query.filter(AiPromptConfig.status == status.strip())

    if keyword:
        normalized_keyword = f"%{keyword.strip()}%"
        query = query.filter(
            or_(
                AiPromptConfig.prompt_key.ilike(normalized_keyword),
                AiPromptConfig.prompt_name.ilike(normalized_keyword),
                AiPromptConfig.prompt_version.ilike(normalized_keyword),
                AiPromptConfig.remark.ilike(normalized_keyword),
            )
        )

    total = query.count()
    rows = (
        query.order_by(AiPromptConfig.update_time.desc(), AiPromptConfig.id.desc())
        .limit(safe_limit)
        .all()
    )
    return rows, total


def update_ai_prompt_config(
    db: Session,
    prompt_id: int,
    prompt_name: str,
    prompt_content: str,
    prompt_version: str,
    status: str,
    output_format: str,
    model_name: str | None = None,
    temperature: float | None = None,
    top_p: float | None = None,
    max_tokens: int | None = None,
    variables_json: dict | list | None = None,
    remark: str | None = None,
    updated_by: str | None = "admin_console",
) -> AiPromptConfig:
    row = (
        db.query(AiPromptConfig)
        .filter(AiPromptConfig.id == prompt_id, AiPromptConfig.delete_flag == "1")
        .first()
    )
    if not row:
        raise ValueError("Prompt 配置不存在")

    normalized_name = prompt_name.strip()
    normalized_content = prompt_content.strip()
    normalized_version = prompt_version.strip()
    normalized_status = status.strip()
    normalized_output_format = output_format.strip()

    if not normalized_name:
        raise ValueError("Prompt 名称不能为空")
    if not normalized_content:
        raise ValueError("Prompt 内容不能为空")
    if not normalized_version:
        raise ValueError("Prompt 版本不能为空")
    if not normalized_status:
        raise ValueError("Prompt 状态不能为空")
    if not normalized_output_format:
        raise ValueError("输出格式不能为空")

    row.prompt_name = normalized_name
    row.prompt_content = normalized_content
    row.prompt_version = normalized_version
    row.status = normalized_status
    row.output_format = normalized_output_format
    row.model_name = model_name.strip() if isinstance(model_name, str) and model_name.strip() else None
    row.temperature = temperature
    row.top_p = top_p
    row.max_tokens = max_tokens
    row.variables_json = _normalize_variables_json(variables_json)
    row.remark = remark.strip() if isinstance(remark, str) and remark.strip() else None
    row.updated_by = updated_by

    db.commit()
    db.refresh(row)
    return row


def _normalize_variables_json(value: Any) -> dict | list | None:
    if value is None:
        return None
    if isinstance(value, (dict, list)):
        return value
    raise ValueError("variables_json 必须是 JSON 对象、数组或空值")
