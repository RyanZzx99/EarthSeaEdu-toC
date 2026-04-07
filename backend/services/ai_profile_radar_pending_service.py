"""
【六维图差量重算】公共服务。

职责边界：
1. 读取字段影响规则表 ai_profile_radar_field_impact_rules。
2. 计算 changed_fields -> affected_dimensions。
3. 维护 ai_profile_radar_pending_changes。
4. 在生成六维图前判断当前应该走：
   - full：首次全量生成
   - partial：只重算受影响维度
   - reuse_only：存在旧结果，但当前改动不影响六维图，直接复用旧分数

注意：
1. 这套 pending changes 只在“已经有旧六维图结果”之后启用。
2. 首次生成前，不记录 pending，不增加额外接口耗时。
"""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from datetime import date
from datetime import datetime
from decimal import Decimal
import json
import logging
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from backend.models.ai_chat_models import AiChatProfileResult
from backend.models.ai_chat_models import AiProfileRadarFieldImpactRule
from backend.models.ai_chat_models import AiProfileRadarPendingChange


logger = logging.getLogger(__name__)

RADAR_DIMENSIONS = (
    "academic",
    "language",
    "standardized",
    "competition",
    "activity",
    "project",
)

DIFF_IGNORED_FIELD_NAMES = {
    "student_id",
    "student_academic_id",
    "student_language_id",
    "student_standardized_test_id",
    "competition_id",
    "activity_id",
    "project_id",
    "project_output_id",
    "sort_order",
    "create_time",
    "update_time",
    "delete_flag",
}

PATCH_INTERNAL_FIELD_NAMES = {"_action", "_match_key"}
SEMANTIC_CHANGED_FIELD_EXPANSIONS: dict[tuple[str, str], tuple[str, ...]] = {
    (
        "student_basic_info",
        "curriculum_system",
    ): (
        "student_basic_info_curriculum_system.curriculum_system_code",
        "student_basic_info_curriculum_system.is_primary",
    ),
}


@dataclass(slots=True)
class RadarRegenerationStrategy:
    mode: str
    previous_result: AiChatProfileResult | None
    pending_row: AiProfileRadarPendingChange | None
    changed_fields: list[str]
    affected_dimensions: list[str]
    previous_radar_scores_json: dict[str, Any]
    previous_summary_text: str | None


def get_latest_complete_profile_result(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> AiChatProfileResult | None:
    stmt = (
        select(AiChatProfileResult)
        .where(AiChatProfileResult.student_id == student_id)
        .where(AiChatProfileResult.session_id == session_id)
        .where(AiChatProfileResult.delete_flag == "1")
        .order_by(AiChatProfileResult.update_time.desc(), AiChatProfileResult.id.desc())
    )
    rows = db.execute(stmt).scalars().all()
    for row in rows:
        if _is_complete_radar_scores_json(row.radar_scores_json):
            return row
    return None


def get_pending_radar_change_row(
    db: Session,
    *,
    session_id: str,
) -> AiProfileRadarPendingChange | None:
    stmt = (
        select(AiProfileRadarPendingChange)
        .where(AiProfileRadarPendingChange.session_id == session_id)
        .where(AiProfileRadarPendingChange.delete_flag == "1")
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()


def extract_changed_fields_from_patch_json(patch_json: dict[str, Any] | None) -> list[str]:
    if not isinstance(patch_json, dict):
        return []

    changed_fields: set[str] = set()
    for table_name, patch_value in patch_json.items():
        if not isinstance(table_name, str) or not table_name or table_name.startswith("_"):
            continue

        if isinstance(patch_value, dict):
            for field_name, field_value in patch_value.items():
                if _should_skip_changed_field(field_name):
                    continue
                if not _has_meaningful_value(field_value):
                    continue
                _add_changed_field_with_semantic_expansion(
                    changed_fields,
                    table_name=table_name,
                    field_name=field_name,
                )
            continue

        if isinstance(patch_value, list):
            row_field_names: set[str] = set()
            for row in patch_value:
                if not isinstance(row, dict):
                    continue
                for field_name, field_value in row.items():
                    if _should_skip_changed_field(field_name):
                        continue
                    if not _has_meaningful_value(field_value):
                        continue
                    row_field_names.add(field_name)
            for field_name in row_field_names:
                _add_changed_field_with_semantic_expansion(
                    changed_fields,
                    table_name=table_name,
                    field_name=field_name,
                )

    return sorted(changed_fields)


def extract_changed_fields_from_profile_diff(
    *,
    previous_profile_json: dict[str, Any] | None,
    current_profile_json: dict[str, Any] | None,
) -> list[str]:
    previous_profile_json = previous_profile_json or {}
    current_profile_json = current_profile_json or {}

    changed_fields: set[str] = set()
    table_names = set(previous_profile_json.keys()) | set(current_profile_json.keys())

    for table_name in table_names:
        previous_value = previous_profile_json.get(table_name)
        current_value = current_profile_json.get(table_name)

        if isinstance(previous_value, dict) or isinstance(current_value, dict):
            previous_row = previous_value if isinstance(previous_value, dict) else {}
            current_row = current_value if isinstance(current_value, dict) else {}
            field_names = set(previous_row.keys()) | set(current_row.keys())
            for field_name in field_names:
                if _should_skip_changed_field(field_name):
                    continue
                if _normalize_scalar_for_diff(previous_row.get(field_name)) != _normalize_scalar_for_diff(
                    current_row.get(field_name)
                ):
                    _add_changed_field_with_semantic_expansion(
                        changed_fields,
                        table_name=table_name,
                        field_name=field_name,
                    )
            continue

        if isinstance(previous_value, list) or isinstance(current_value, list):
            previous_rows = previous_value if isinstance(previous_value, list) else []
            current_rows = current_value if isinstance(current_value, list) else []
            if _canonicalize_multi_rows_for_diff(previous_rows) == _canonicalize_multi_rows_for_diff(current_rows):
                continue
            row_field_names = _collect_multi_row_field_names(previous_rows) | _collect_multi_row_field_names(current_rows)
            for field_name in row_field_names:
                _add_changed_field_with_semantic_expansion(
                    changed_fields,
                    table_name=table_name,
                    field_name=field_name,
                )
            continue

        if _normalize_scalar_for_diff(previous_value) != _normalize_scalar_for_diff(current_value):
            changed_fields.add(f"{table_name}.*")

    return sorted(changed_fields)


def map_changed_fields_to_affected_dimensions(
    db: Session,
    *,
    biz_domain: str,
    changed_fields: list[str],
) -> list[str]:
    if not changed_fields:
        return []

    rules = _load_field_impact_rules(db, biz_domain=biz_domain)
    exact_rule_map: dict[tuple[str, str], AiProfileRadarFieldImpactRule] = {}
    wildcard_rule_map: dict[str, AiProfileRadarFieldImpactRule] = {}
    for rule in rules:
        key = (rule.table_name, rule.field_name)
        if rule.field_name == "*":
            wildcard_rule_map[rule.table_name] = rule
        else:
            exact_rule_map[key] = rule

    affected_dimensions: set[str] = set()
    for changed_field in changed_fields:
        table_name, field_name = _split_changed_field(changed_field)
        if not table_name:
            continue

        rule = exact_rule_map.get((table_name, field_name))
        if rule is None:
            rule = wildcard_rule_map.get(table_name)
        if rule is None or int(rule.affects_radar or 0) != 1:
            continue

        for dimension in _normalize_dimensions(rule.affected_dimensions_json):
            affected_dimensions.add(dimension)

    return sorted(affected_dimensions, key=lambda item: RADAR_DIMENSIONS.index(item) if item in RADAR_DIMENSIONS else 999)


def accumulate_pending_radar_changes(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    changed_fields: list[str],
    change_source: str,
    change_remark: str | None = None,
) -> dict[str, Any]:
    changed_fields = sorted({item for item in changed_fields if isinstance(item, str) and item.strip()})
    if not changed_fields:
        return {
            "enabled": False,
            "reason": "no_changed_fields",
            "changed_fields": [],
            "affected_dimensions": [],
        }

    previous_result = get_latest_complete_profile_result(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if previous_result is None:
        return {
            "enabled": False,
            "reason": "no_previous_radar_result",
            "changed_fields": changed_fields,
            "affected_dimensions": [],
        }

    affected_dimensions = map_changed_fields_to_affected_dimensions(
        db,
        biz_domain=biz_domain,
        changed_fields=changed_fields,
    )

    pending_row = get_pending_radar_change_row(
        db,
        session_id=session_id,
    )
    if pending_row is None:
        pending_row = AiProfileRadarPendingChange(
            session_id=session_id,
            student_id=student_id,
            biz_domain=biz_domain,
            last_profile_result_id=previous_result.id,
            pending_changed_fields_json=changed_fields,
            pending_affected_dimensions_json=affected_dimensions,
            last_change_source=change_source,
            last_change_remark=change_remark,
            version_no=1,
        )
        db.add(pending_row)
    else:
        existing_changed_fields = _normalize_string_list(pending_row.pending_changed_fields_json)
        existing_affected_dimensions = _normalize_dimensions(pending_row.pending_affected_dimensions_json)
        pending_row.student_id = student_id
        pending_row.biz_domain = biz_domain
        pending_row.last_profile_result_id = pending_row.last_profile_result_id or previous_result.id
        pending_row.pending_changed_fields_json = _normalize_string_list(existing_changed_fields + changed_fields)
        pending_row.pending_affected_dimensions_json = _normalize_dimensions(
            existing_affected_dimensions + affected_dimensions
        )
        pending_row.last_change_source = change_source
        pending_row.last_change_remark = change_remark
        pending_row.version_no = int(pending_row.version_no or 0) + 1

    db.commit()
    db.refresh(pending_row)
    return {
        "enabled": True,
        "reason": "accumulated",
        "changed_fields": _normalize_string_list(pending_row.pending_changed_fields_json),
        "affected_dimensions": _normalize_dimensions(pending_row.pending_affected_dimensions_json),
        "last_profile_result_id": pending_row.last_profile_result_id,
    }


def reset_pending_radar_changes(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    last_profile_result_id: int,
) -> AiProfileRadarPendingChange:
    pending_row = get_pending_radar_change_row(
        db,
        session_id=session_id,
    )
    if pending_row is None:
        pending_row = AiProfileRadarPendingChange(
            session_id=session_id,
            student_id=student_id,
            biz_domain=biz_domain,
            last_profile_result_id=last_profile_result_id,
            pending_changed_fields_json=[],
            pending_affected_dimensions_json=[],
            last_change_source="reset_after_generation",
            last_change_remark="六维图生成成功后清空待处理改动",
            version_no=1,
        )
        db.add(pending_row)
    else:
        pending_row.student_id = student_id
        pending_row.biz_domain = biz_domain
        pending_row.last_profile_result_id = last_profile_result_id
        pending_row.pending_changed_fields_json = []
        pending_row.pending_affected_dimensions_json = []
        pending_row.last_change_source = "reset_after_generation"
        pending_row.last_change_remark = "六维图生成成功后清空待处理改动"
        pending_row.version_no = int(pending_row.version_no or 0) + 1

    db.commit()
    db.refresh(pending_row)
    return pending_row


def build_radar_regeneration_strategy(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> RadarRegenerationStrategy:
    previous_result = get_latest_complete_profile_result(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    if previous_result is None:
        return RadarRegenerationStrategy(
            mode="full",
            previous_result=None,
            pending_row=None,
            changed_fields=[],
            affected_dimensions=[],
            previous_radar_scores_json={},
            previous_summary_text=None,
        )

    pending_row = get_pending_radar_change_row(
        db,
        session_id=session_id,
    )
    if pending_row is None:
        return RadarRegenerationStrategy(
            mode="reuse_only",
            previous_result=previous_result,
            pending_row=None,
            changed_fields=[],
            affected_dimensions=[],
            previous_radar_scores_json=deepcopy(previous_result.radar_scores_json or {}),
            previous_summary_text=previous_result.summary_text,
        )

    changed_fields = _normalize_string_list(pending_row.pending_changed_fields_json)
    affected_dimensions = _normalize_dimensions(pending_row.pending_affected_dimensions_json)
    if not changed_fields:
        return RadarRegenerationStrategy(
            mode="reuse_only",
            previous_result=previous_result,
            pending_row=pending_row,
            changed_fields=[],
            affected_dimensions=[],
            previous_radar_scores_json=deepcopy(previous_result.radar_scores_json or {}),
            previous_summary_text=previous_result.summary_text,
        )

    if not affected_dimensions:
        return RadarRegenerationStrategy(
            mode="reuse_only",
            previous_result=previous_result,
            pending_row=pending_row,
            changed_fields=changed_fields,
            affected_dimensions=[],
            previous_radar_scores_json=deepcopy(previous_result.radar_scores_json or {}),
            previous_summary_text=previous_result.summary_text,
        )

    return RadarRegenerationStrategy(
        mode="partial",
        previous_result=previous_result,
        pending_row=pending_row,
        changed_fields=changed_fields,
        affected_dimensions=affected_dimensions,
        previous_radar_scores_json=deepcopy(previous_result.radar_scores_json or {}),
        previous_summary_text=previous_result.summary_text,
    )


def build_scoring_context_for_strategy(
    *,
    student_id: str,
    session_id: str,
    profile_json: dict[str, Any],
    strategy: RadarRegenerationStrategy,
) -> dict[str, Any]:
    if strategy.mode == "partial":
        return {
            "student_id": student_id,
            "session_id": session_id,
            "profile_json": _to_json_safe(profile_json),
            "score_mode": "partial",
            "affected_dimensions": deepcopy(strategy.affected_dimensions),
            "previous_radar_scores_json": deepcopy(strategy.previous_radar_scores_json),
            "previous_summary_text": strategy.previous_summary_text,
        }

    return {
        "student_id": student_id,
        "session_id": session_id,
        "profile_json": _to_json_safe(profile_json),
        "score_mode": "full",
        "affected_dimensions": [],
        "previous_radar_scores_json": {},
        "previous_summary_text": None,
    }


def merge_partial_scoring_result(
    *,
    strategy: RadarRegenerationStrategy,
    scoring_json: dict[str, Any],
) -> dict[str, Any]:
    if strategy.mode != "partial":
        return scoring_json

    merged_scores = deepcopy(strategy.previous_radar_scores_json)
    new_scores = scoring_json.get("radar_scores_json") or {}
    for dimension in strategy.affected_dimensions:
        if dimension in new_scores:
            merged_scores[dimension] = deepcopy(new_scores[dimension])

    merged_scoring_json = deepcopy(scoring_json)
    merged_scoring_json["radar_scores_json"] = merged_scores
    return merged_scoring_json


def build_reuse_only_profile_result_payload(
    *,
    profile_json: dict[str, Any],
    strategy: RadarRegenerationStrategy,
) -> dict[str, Any]:
    return {
        "profile_json": _to_json_safe(profile_json),
        "radar_scores_json": deepcopy(strategy.previous_radar_scores_json),
        "summary_text": strategy.previous_summary_text,
        "regeneration_mode": "reuse_only",
        "affected_dimensions": deepcopy(strategy.affected_dimensions),
        "changed_fields": deepcopy(strategy.changed_fields),
    }


def _load_field_impact_rules(
    db: Session,
    *,
    biz_domain: str,
) -> list[AiProfileRadarFieldImpactRule]:
    stmt = (
        select(AiProfileRadarFieldImpactRule)
        .where(AiProfileRadarFieldImpactRule.biz_domain == biz_domain)
        .where(AiProfileRadarFieldImpactRule.status == "active")
        .where(AiProfileRadarFieldImpactRule.delete_flag == "1")
        .order_by(AiProfileRadarFieldImpactRule.sort_order.asc(), AiProfileRadarFieldImpactRule.id.asc())
    )
    return db.execute(stmt).scalars().all()


def _split_changed_field(changed_field: str) -> tuple[str, str]:
    if "." not in changed_field:
        return changed_field.strip(), "*"
    table_name, field_name = changed_field.split(".", 1)
    return table_name.strip(), field_name.strip() or "*"


def _canonicalize_multi_rows_for_diff(rows: list[Any]) -> list[dict[str, Any]]:
    normalized_rows: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        cleaned_row = {
            field_name: _normalize_scalar_for_diff(field_value)
            for field_name, field_value in row.items()
            if not _should_skip_changed_field(field_name)
        }
        if cleaned_row:
            normalized_rows.append(cleaned_row)
    return sorted(
        normalized_rows,
        key=lambda item: json.dumps(item, ensure_ascii=False, sort_keys=True),
    )


def _collect_multi_row_field_names(rows: list[Any]) -> set[str]:
    field_names: set[str] = set()
    for row in rows:
        if not isinstance(row, dict):
            continue
        for field_name in row.keys():
            if _should_skip_changed_field(field_name):
                continue
            field_names.add(field_name)
    return field_names


def _add_changed_field_with_semantic_expansion(
    changed_fields: set[str],
    *,
    table_name: str,
    field_name: str,
) -> None:
    changed_fields.add(f"{table_name}.{field_name}")
    for expanded_field in SEMANTIC_CHANGED_FIELD_EXPANSIONS.get((table_name, field_name), ()):
        changed_fields.add(expanded_field)


def _should_skip_changed_field(field_name: Any) -> bool:
    if not isinstance(field_name, str):
        return True
    if not field_name.strip():
        return True
    if field_name in PATCH_INTERNAL_FIELD_NAMES:
        return True
    return field_name in DIFF_IGNORED_FIELD_NAMES


def _normalize_scalar_for_diff(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _normalize_scalar_for_diff(item) for key, item in sorted(value.items())}
    if isinstance(value, list):
        return [_normalize_scalar_for_diff(item) for item in value]
    if isinstance(value, tuple):
        return [_normalize_scalar_for_diff(item) for item in value]
    if isinstance(value, set):
        return sorted(_normalize_scalar_for_diff(item) for item in value)
    if isinstance(value, Decimal):
        return int(value) if value == value.to_integral_value() else float(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, str):
        return value.strip()
    return value


def _normalize_string_list(values: Any) -> list[str]:
    normalized = []
    for value in values or []:
        if not isinstance(value, str):
            continue
        stripped = value.strip()
        if stripped and stripped not in normalized:
            normalized.append(stripped)
    return normalized


def _normalize_dimensions(values: Any) -> list[str]:
    normalized: list[str] = []
    for value in values or []:
        if not isinstance(value, str):
            continue
        stripped = value.strip()
        if stripped in RADAR_DIMENSIONS and stripped not in normalized:
            normalized.append(stripped)
    return normalized


def _is_complete_radar_scores_json(radar_scores_json: Any) -> bool:
    if not isinstance(radar_scores_json, dict):
        return False
    for dimension in RADAR_DIMENSIONS:
        item = radar_scores_json.get(dimension)
        if not isinstance(item, dict):
            return False
        if "score" not in item:
            return False
    return True


def _has_meaningful_value(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, str):
        return bool(value.strip())
    if isinstance(value, (list, tuple, set, dict)):
        return bool(value)
    return True


def _to_json_safe(value: Any) -> Any:
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
