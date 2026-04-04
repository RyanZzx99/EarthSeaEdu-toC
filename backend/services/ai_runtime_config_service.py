from __future__ import annotations

from collections import OrderedDict
from typing import Any

from sqlalchemy.orm import Session

from backend.config.db_conf import settings
from backend.models.ai_chat_models import AiRuntimeConfig


AI_RUNTIME_CONFIG_SPECS: OrderedDict[str, dict[str, Any]] = OrderedDict(
    [
        (
            "AI_MODEL_BASE_URL",
            {
                "config_group": "ai_model",
                "config_name": "模型基础地址",
                "value_type": "url",
                "is_secret": 0,
                "sort_order": 10,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_BASE_URL",
                "settings_attr": "ai_model_base_url",
            },
        ),
        (
            "AI_MODEL_API_KEY",
            {
                "config_group": "ai_model",
                "config_name": "模型 API Key",
                "value_type": "secret",
                "is_secret": 1,
                "sort_order": 20,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_API_KEY",
                "settings_attr": "ai_model_api_key",
            },
        ),
        (
            "AI_MODEL_DEFAULT_NAME",
            {
                "config_group": "ai_model",
                "config_name": "默认模型名称",
                "value_type": "model_name",
                "is_secret": 0,
                "sort_order": 30,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_DEFAULT_NAME",
                "settings_attr": "ai_model_default_name",
            },
        ),
        (
            "AI_MODEL_CONNECT_TIMEOUT_SECONDS",
            {
                "config_group": "ai_model",
                "config_name": "连接超时（秒）",
                "value_type": "float",
                "is_secret": 0,
                "sort_order": 40,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_CONNECT_TIMEOUT_SECONDS",
                "settings_attr": "ai_model_connect_timeout_seconds",
            },
        ),
        (
            "AI_MODEL_READ_TIMEOUT_SECONDS",
            {
                "config_group": "ai_model",
                "config_name": "非流式读取超时（秒）",
                "value_type": "float",
                "is_secret": 0,
                "sort_order": 50,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_READ_TIMEOUT_SECONDS",
                "settings_attr": "ai_model_read_timeout_seconds",
            },
        ),
        (
            "AI_MODEL_STREAM_READ_TIMEOUT_SECONDS",
            {
                "config_group": "ai_model",
                "config_name": "流式读取超时（秒）",
                "value_type": "float",
                "is_secret": 0,
                "sort_order": 60,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_STREAM_READ_TIMEOUT_SECONDS",
                "settings_attr": "ai_model_stream_read_timeout_seconds",
            },
        ),
        (
            "AI_MODEL_DEFAULT_TEMPERATURE",
            {
                "config_group": "ai_model",
                "config_name": "默认 Temperature",
                "value_type": "float",
                "is_secret": 0,
                "sort_order": 70,
                "remark": "为空时回退到 backend/.env 的 AI_MODEL_DEFAULT_TEMPERATURE",
                "settings_attr": "ai_model_default_temperature",
            },
        ),
    ]
)


def list_ai_runtime_configs(db: Session) -> list[dict[str, Any]]:
    rows = (
        db.query(AiRuntimeConfig)
        .filter(AiRuntimeConfig.delete_flag == "1")
        .order_by(
            AiRuntimeConfig.config_group.asc(),
            AiRuntimeConfig.sort_order.asc(),
            AiRuntimeConfig.id.asc(),
        )
        .all()
    )
    row_map = {row.config_key: row for row in rows}
    defaults = get_default_ai_runtime_config_map()
    items: list[dict[str, Any]] = []

    for config_key, spec in AI_RUNTIME_CONFIG_SPECS.items():
        items.append(
            _serialize_ai_runtime_config_row(
                row=row_map.get(config_key),
                config_key=config_key,
                spec=spec,
                default_value=defaults.get(config_key),
            )
        )

    known_keys = set(AI_RUNTIME_CONFIG_SPECS.keys())
    for row in rows:
        if row.config_key in known_keys:
            continue
        items.append(
            _serialize_ai_runtime_config_row(
                row=row,
                config_key=row.config_key,
                spec={
                    "config_group": row.config_group,
                    "config_name": row.config_name,
                    "value_type": row.value_type,
                    "is_secret": row.is_secret,
                    "sort_order": row.sort_order,
                    "remark": row.remark,
                },
                default_value=None,
            )
        )
    return items


def update_ai_runtime_config(
    db: Session,
    *,
    config_key: str,
    config_value: str | None = None,
    status: str = "active",
    remark: str | None = None,
    clear_override: bool = False,
    updated_by: str | None = "admin_console",
) -> dict[str, Any]:
    normalized_key = (config_key or "").strip()
    if normalized_key not in AI_RUNTIME_CONFIG_SPECS:
        raise ValueError("运行时配置键不存在")

    normalized_status = (status or "").strip() or "active"
    if normalized_status not in {"active", "disabled"}:
        raise ValueError("运行时配置状态仅支持 active 或 disabled")

    spec = AI_RUNTIME_CONFIG_SPECS[normalized_key]
    row = (
        db.query(AiRuntimeConfig)
        .filter(
            AiRuntimeConfig.config_key == normalized_key,
            AiRuntimeConfig.delete_flag == "1",
        )
        .first()
    )
    if row is None:
        row = AiRuntimeConfig(
            config_group=spec["config_group"],
            config_key=normalized_key,
            config_name=spec["config_name"],
            value_type=spec["value_type"],
            is_secret=int(spec["is_secret"]),
            status="active",
            sort_order=int(spec["sort_order"]),
            remark=spec.get("remark"),
            created_by=updated_by,
            updated_by=updated_by,
            delete_flag="1",
        )
        db.add(row)

    row.config_group = spec["config_group"]
    row.config_name = spec["config_name"]
    row.value_type = spec["value_type"]
    row.is_secret = int(spec["is_secret"])
    row.sort_order = int(spec["sort_order"])
    row.status = normalized_status
    row.updated_by = updated_by
    if isinstance(remark, str) and remark.strip():
        row.remark = remark.strip()
    elif row.remark is None:
        row.remark = spec.get("remark")

    if clear_override:
        row.config_value = None
    elif config_value is not None:
        normalized_value = str(config_value).strip()
        if normalized_value:
            row.config_value = normalized_value

    db.commit()
    db.refresh(row)
    return _serialize_ai_runtime_config_row(
        row=row,
        config_key=normalized_key,
        spec=spec,
        default_value=get_default_ai_runtime_config_map().get(normalized_key),
    )


def get_effective_ai_runtime_config_map(db: Session) -> dict[str, Any]:
    defaults = get_default_ai_runtime_config_map()
    rows = (
        db.query(AiRuntimeConfig)
        .filter(
            AiRuntimeConfig.delete_flag == "1",
            AiRuntimeConfig.status == "active",
            AiRuntimeConfig.config_key.in_(tuple(AI_RUNTIME_CONFIG_SPECS.keys())),
        )
        .all()
    )
    row_map = {row.config_key: row for row in rows}
    effective: dict[str, Any] = {}
    for config_key, spec in AI_RUNTIME_CONFIG_SPECS.items():
        row = row_map.get(config_key)
        override_value = _normalize_db_override_value(row.config_value if row else None)
        source_value = override_value if override_value is not None else defaults.get(config_key)
        effective[config_key] = _coerce_runtime_value(source_value, spec["value_type"])
    return effective


def get_default_ai_runtime_config_map() -> dict[str, Any]:
    return {
        config_key: getattr(settings, spec["settings_attr"])
        for config_key, spec in AI_RUNTIME_CONFIG_SPECS.items()
    }


def _serialize_ai_runtime_config_row(
    *,
    row: AiRuntimeConfig | None,
    config_key: str,
    spec: dict[str, Any],
    default_value: Any,
) -> dict[str, Any]:
    row_status = row.status if row else "active"
    raw_override_value = _normalize_db_override_value(row.config_value if row else None)
    has_override = bool(raw_override_value)
    using_default = not has_override or row_status != "active"
    effective_source_value = (
        raw_override_value
        if has_override and row_status == "active"
        else default_value
    )
    is_secret = bool((row.is_secret if row else spec.get("is_secret")) or 0)

    return {
        "id": row.id if row else None,
        "config_group": row.config_group if row else spec["config_group"],
        "config_key": config_key,
        "config_name": row.config_name if row else spec["config_name"],
        "config_value": "" if is_secret else (raw_override_value or ""),
        "effective_value_display": _format_runtime_value_for_display(
            effective_source_value,
            is_secret=is_secret,
        ),
        "default_value_display": _format_runtime_value_for_display(
            default_value,
            is_secret=is_secret,
        ),
        "value_type": row.value_type if row else spec["value_type"],
        "is_secret": 1 if is_secret else 0,
        "status": row_status,
        "sort_order": row.sort_order if row else spec["sort_order"],
        "remark": row.remark if row and row.remark is not None else spec.get("remark"),
        "has_override": has_override,
        "using_default": using_default,
        "update_time": row.update_time if row else None,
    }


def _normalize_db_override_value(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized or None


def _coerce_runtime_value(value: Any, value_type: str) -> Any:
    if value is None:
        return None
    if value_type == "float":
        return float(value)
    if value_type == "int":
        return int(value)
    return str(value)


def _format_runtime_value_for_display(value: Any, *, is_secret: bool) -> str:
    if value is None:
        return ""
    text = str(value)
    if is_secret:
        return _mask_secret_value(text)
    return text


def _mask_secret_value(value: str) -> str:
    normalized = (value or "").strip()
    if not normalized:
        return ""
    if len(normalized) <= 8:
        return "*" * len(normalized)
    return f"{normalized[:4]}{'*' * (len(normalized) - 8)}{normalized[-4:]}"
