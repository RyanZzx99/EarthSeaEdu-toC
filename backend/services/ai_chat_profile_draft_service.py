"""
智能建档 draft 链路服务。

设计目标：
1. 不修改现有首页 / 档案页接口行为。
2. 通过一套新接口单独验证 draft 方案。
3. 统一 draft 链路的命名、日志和返回结构，方便后续维护。
"""

from __future__ import annotations

from copy import deepcopy
from datetime import date
from datetime import datetime
from decimal import Decimal
import json
import logging
from typing import Any

from sqlalchemy import select
from sqlalchemy import text
from sqlalchemy.orm import Session

from backend.models.ai_chat_models import AiChatProfileDraft
from backend.services.ai_profile_radar_pending_service import accumulate_pending_radar_changes
from backend.services.ai_profile_radar_pending_service import build_radar_regeneration_strategy
from backend.services.ai_profile_radar_pending_service import build_reuse_only_profile_result_payload
from backend.services.ai_profile_radar_pending_service import build_scoring_context_for_strategy
from backend.services.ai_profile_radar_pending_service import extract_changed_fields_from_patch_json
from backend.services.ai_profile_radar_pending_service import merge_partial_scoring_result
from backend.services.ai_profile_radar_pending_service import reset_pending_radar_changes
from backend.services.ai_chat_service import get_latest_progress_state
from backend.services.ai_chat_service import save_profile_result
from backend.services.ai_chat_service import get_visible_messages
from backend.services.ai_chat_service import update_session_progress
from backend.services.ai_chat_pipeline_service import _build_archive_snapshot_progress_hint
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context_by_statuses
from backend.services.business_profile_form_service import load_business_profile_snapshot
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import MULTI_ROW_TABLES
from backend.services.business_profile_persistence_service import prune_academic_payload_by_curriculum_system
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot
from backend.services.business_profile_persistence_service import SINGLE_ROW_TABLES
from backend.services.code_resolution_service import apply_code_resolution_to_payload
from backend.utils.flow_logging import log_flow_info
from backend.utils.flow_logging import log_timed_step


logger = logging.getLogger(__name__)

PROFILE_DRAFT_FLOW_NAME = "智能建档"
PROFILE_DRAFT_PROMPT_KEY = "student_profile_build.draft_patch_extraction"
PROFILE_DRAFT_ALLOWED_PROMPT_STATUSES = ("active", "draft")
PROFILE_DRAFT_SCORING_PROMPT_KEY = "student_profile_build.scoring"
PROFILE_DRAFT_CONTEXT_MESSAGE_LIMIT = 8
PROFILE_DRAFT_INTERNAL_KEYS = {"_action", "_match_key"}
PROFILE_DRAFT_MULTI_TABLES_WITH_STUDENT_ID = set(MULTI_ROW_TABLES) - {"student_project_outputs"}
PROFILE_DRAFT_SINGLE_TABLES_WITH_STUDENT_ID = set(SINGLE_ROW_TABLES)
PROFILE_DRAFT_TABLE_COLUMNS_CACHE: dict[str, set[str]] = {}


def get_ai_chat_profile_draft_detail(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> dict[str, Any]:
    """
    读取当前 session 对应的 draft 详情。

    说明：
    1. 如果还没有持久化 draft，会回退到正式业务表快照作为初始展示值。
    2. 这样实验接口在首轮调用时也有稳定返回。
    """

    draft_row = _get_profile_draft_row(
        db,
        session_id=session_id,
    )
    if draft_row is None:
        initial_draft = _build_initial_draft_document(
            db,
            student_id=student_id,
        )
        return {
            "session_id": session_id,
            "student_id": student_id,
            "biz_domain": biz_domain,
            "draft_exists": False,
            "source_round": 0,
            "version_no": 0,
            "draft_json": initial_draft,
            "last_patch_json": None,
            "create_time": None,
            "update_time": None,
        }

    return _serialize_profile_draft_row(draft_row)


def sync_ai_chat_profile_draft_from_official_snapshot(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    source_round: int,
) -> dict[str, Any]:
    """
    用正式业务表快照直接覆盖 draft。

    这是实验方案里的关键同步点：
    档案页保存成功后，可以额外调用这条接口把正式表快照同步回 draft。
    """

    with log_timed_step(
        logger,
        flow_name=PROFILE_DRAFT_FLOW_NAME,
        step_name="正式表快照覆盖 draft",
        student_id=student_id,
        session_id=session_id,
    ):
        official_snapshot = _build_initial_draft_document(
            db,
            student_id=student_id,
        )
        last_patch_json = {
            "sync_source": "official_snapshot",
            "remark": f"{PROFILE_DRAFT_FLOW_NAME} 正式表快照覆盖 draft",
        }
        draft_row = _upsert_profile_draft_row(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            draft_json=official_snapshot,
            last_patch_json=last_patch_json,
            source_round=source_round,
        )
        # 中文注释：
        # 这里不能再用“把正式档案并到旧 progress 上”的策略，
        # 因为那样只能补进新增信息，删掉的信息不会把旧的 sufficient 状态冲掉。
        # 档案页保存成功后，正式业务表应当成为当前会话最权威的已确认状态，
        # 所以这里直接用正式表快照重新构建一份 progress 覆盖回 session。
        merged_progress = _build_archive_snapshot_progress_hint(official_snapshot)
        update_session_progress(
            db,
            student_id=student_id,
            session_id=session_id,
            progress_result=merged_progress,
        )
        return _serialize_profile_draft_row(draft_row)


def extract_latest_patch_into_ai_chat_profile_draft(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    source_round: int,
) -> dict[str, Any]:
    """
    执行一轮 draft patch 提取，并把 patch merge 到 draft。

    说明：
    1. 这里不会改正式业务表。
    2. 这里只更新 ai_chat_profile_drafts。
    """

    current_draft = _load_or_initialize_draft_document(
        db,
        student_id=student_id,
        session_id=session_id,
    )
    draft_context = _build_draft_patch_extraction_context(
        db,
        student_id=student_id,
        session_id=session_id,
        current_draft_json=current_draft,
    )

    with log_timed_step(
        logger,
        flow_name=PROFILE_DRAFT_FLOW_NAME,
        step_name="提取最新 draft patch",
        student_id=student_id,
        session_id=session_id,
    ):
        runtime_result = execute_prompt_with_context_by_statuses(
            db,
            prompt_key=PROFILE_DRAFT_PROMPT_KEY,
            context=draft_context,
            allowed_statuses=PROFILE_DRAFT_ALLOWED_PROMPT_STATUSES,
        )
        patch_json = _parse_json_object(
            raw_text=runtime_result.content,
            scene_name="draft_patch_extraction",
        )
        merged_draft_json = _merge_draft_patch_document(
            current_draft_json=current_draft,
            patch_json=patch_json,
            student_id=student_id,
        )
        draft_row = _upsert_profile_draft_row(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            draft_json=merged_draft_json,
            last_patch_json=patch_json,
            source_round=source_round,
        )
        changed_fields = extract_changed_fields_from_patch_json(patch_json)
        pending_change_result = accumulate_pending_radar_changes(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            changed_fields=changed_fields,
            change_source="ai_dialogue_patch",
            change_remark=f"{PROFILE_DRAFT_FLOW_NAME} 对话 patch 已并入 draft",
        )
        return {
            "session_id": session_id,
            "student_id": student_id,
            "biz_domain": biz_domain,
            "prompt_key": PROFILE_DRAFT_PROMPT_KEY,
            "prompt_allowed_statuses": list(PROFILE_DRAFT_ALLOWED_PROMPT_STATUSES),
            "patch_json": patch_json,
            "draft_json": deepcopy(draft_row.draft_json),
            "source_round": draft_row.source_round,
            "version_no": draft_row.version_no,
            "create_time": draft_row.create_time.isoformat(),
            "update_time": draft_row.update_time.isoformat(),
            "changed_fields": changed_fields,
            "pending_change_result": pending_change_result,
        }


def regenerate_profile_result_from_ai_chat_profile_draft(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> dict[str, Any]:
    """
    基于 draft 直接重算六维图。

    说明：
    1. 这里故意不再跑 extraction。
    2. 这条正式 draft 链路会把结果回写到 ai_chat_profile_results，便于首页和档案页统一读取。
    """

    draft_detail = get_ai_chat_profile_draft_detail(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
    )
    draft_json = deepcopy(draft_detail["draft_json"])
    official_profile_json = _prepare_draft_for_official_persistence(
        db,
        draft_json=draft_json,
        student_id=student_id,
    )
    strategy = build_radar_regeneration_strategy(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain=biz_domain,
    )
    scoring_context = build_scoring_context_for_strategy(
        student_id=student_id,
        session_id=session_id,
        profile_json=official_profile_json,
        strategy=strategy,
    )

    with log_timed_step(
        logger,
        flow_name=PROFILE_DRAFT_FLOW_NAME,
        step_name="基于 draft 重算六维图",
        student_id=student_id,
        session_id=session_id,
    ):
        if strategy.mode == "reuse_only":
            scoring_json = build_reuse_only_profile_result_payload(
                profile_json=official_profile_json,
                strategy=strategy,
            )
        else:
            log_flow_info(
                logger,
                flow_name=PROFILE_DRAFT_FLOW_NAME,
                step_name="基于 draft 重算六维图",
                student_id=student_id,
                session_id=session_id,
                prompt_key=PROFILE_DRAFT_SCORING_PROMPT_KEY,
                message=f"{PROFILE_DRAFT_FLOW_NAME} 传给 scoring 的上下文={json.dumps(scoring_context, ensure_ascii=False)}",
            )
            scoring_runtime_result = execute_prompt_with_context_by_statuses(
                db,
                prompt_key=PROFILE_DRAFT_SCORING_PROMPT_KEY,
                context=scoring_context,
                allowed_statuses=("active",),
            )
            scoring_json = _parse_json_object(
                raw_text=scoring_runtime_result.content,
                scene_name="draft_scoring",
            )
            scoring_json = merge_partial_scoring_result(
                strategy=strategy,
                scoring_json=scoring_json,
            )

        with log_timed_step(
            logger,
            flow_name=PROFILE_DRAFT_FLOW_NAME,
            step_name="草稿同步正式业务表",
            student_id=student_id,
            session_id=session_id,
        ):
            db_payload = build_db_payload_from_profile_json(
                official_profile_json,
                student_id=student_id,
            )
            persistence_result = persist_business_profile_snapshot(
                db,
                db_payload=db_payload,
                student_id=student_id,
            )
        saved_result = save_profile_result(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            profile_json=official_profile_json,
            radar_scores_json=scoring_json.get("radar_scores_json"),
            summary_text=scoring_json.get("summary_text"),
            result_status="saved",
            db_payload_json=persistence_result.db_payload,
            save_error_message=None,
            session_stage="build_ready",
            session_remark=f"{PROFILE_DRAFT_FLOW_NAME} 基于 draft 重新生成六维图",
        )
        reset_pending_radar_changes(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            last_profile_result_id=saved_result.profile_result_id,
        )
        return {
            "session_id": session_id,
            "student_id": student_id,
            "biz_domain": biz_domain,
            "profile_result_id": saved_result.profile_result_id,
            "result_status": saved_result.result_status,
            "profile_json": official_profile_json,
            "radar_scores_json": scoring_json.get("radar_scores_json") or {},
            "summary_text": scoring_json.get("summary_text"),
            "regeneration_mode": strategy.mode,
            "affected_dimensions": strategy.affected_dimensions,
            "changed_fields": strategy.changed_fields,
        }


def _build_draft_patch_extraction_context(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    current_draft_json: dict[str, Any],
) -> dict[str, Any]:
    latest_round_messages = get_visible_messages(
        db,
        session_id=session_id,
        limit=PROFILE_DRAFT_CONTEXT_MESSAGE_LIMIT,
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
                "message_type": item.message_type,
                "sequence_no": item.sequence_no,
                "content": item.content,
                "create_time": item.create_time.isoformat(),
            }
            for item in latest_round_messages
        ],
        "current_progress_json": current_progress_json or {},
        "current_draft_json": current_draft_json,
    }


def _load_or_initialize_draft_document(
    db: Session,
    *,
    student_id: str,
    session_id: str,
) -> dict[str, Any]:
    draft_row = _get_profile_draft_row(
        db,
        session_id=session_id,
    )
    if draft_row is not None and isinstance(draft_row.draft_json, dict):
        return deepcopy(draft_row.draft_json)
    return _build_initial_draft_document(
        db,
        student_id=student_id,
    )


def _build_initial_draft_document(
    db: Session,
    *,
    student_id: str,
) -> dict[str, Any]:
    """
    以正式业务表快照作为 draft 初始骨架。

    这样做的好处是：
    1. top-level key 结构天然齐全。
    2. 用户手工保存过的正式档案也能自然成为 AI 后续对话的基础。
    """

    return _to_json_safe(
        load_business_profile_snapshot(
            db,
            student_id=student_id,
        )
    )


def _get_profile_draft_row(
    db: Session,
    *,
    session_id: str,
) -> AiChatProfileDraft | None:
    stmt = (
        select(AiChatProfileDraft)
        .where(AiChatProfileDraft.session_id == session_id)
        .where(AiChatProfileDraft.delete_flag == "1")
        .limit(1)
    )
    return db.execute(stmt).scalar_one_or_none()


def _upsert_profile_draft_row(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    draft_json: dict[str, Any],
    last_patch_json: dict[str, Any] | list[str] | None,
    source_round: int,
) -> AiChatProfileDraft:
    draft_row = _get_profile_draft_row(
        db,
        session_id=session_id,
    )
    if draft_row is None:
        draft_row = AiChatProfileDraft(
            session_id=session_id,
            student_id=student_id,
            biz_domain=biz_domain,
            draft_json=_to_json_safe(deepcopy(draft_json)),
            last_patch_json=_to_json_safe(deepcopy(last_patch_json)),
            source_round=source_round,
            version_no=1,
        )
        db.add(draft_row)
    else:
        draft_row.student_id = student_id
        draft_row.biz_domain = biz_domain
        draft_row.draft_json = _to_json_safe(deepcopy(draft_json))
        draft_row.last_patch_json = _to_json_safe(deepcopy(last_patch_json))
        draft_row.source_round = source_round
        draft_row.version_no = int(draft_row.version_no or 0) + 1

    db.commit()
    db.refresh(draft_row)
    return draft_row


def _serialize_profile_draft_row(draft_row: AiChatProfileDraft) -> dict[str, Any]:
    return {
        "session_id": draft_row.session_id,
        "student_id": draft_row.student_id,
        "biz_domain": draft_row.biz_domain,
        "draft_exists": True,
        "source_round": draft_row.source_round,
        "version_no": draft_row.version_no,
        "draft_json": deepcopy(draft_row.draft_json),
        "last_patch_json": deepcopy(draft_row.last_patch_json),
        "create_time": draft_row.create_time.isoformat(),
        "update_time": draft_row.update_time.isoformat(),
    }


def _prepare_draft_for_official_persistence(
    db: Session,
    *,
    draft_json: dict[str, Any],
    student_id: str,
) -> dict[str, Any]:
    """
    把 draft 文档转换成正式业务表可接受的 profile_json。

    说明：
    1. draft prompt 允许在 student_basic_info 中保留 raw 语义字段，如 target_country / major_interest。
    2. 正式业务表只接受真实列名，因此这里要先做：
       - 国家 / 专业码值映射
       - 课程体系从 raw 字段回写到 curriculum_system 明细表
       - 过滤掉数据库不存在的草稿字段
    """

    raw_basic_info = draft_json.get("student_basic_info") or {}
    if not isinstance(raw_basic_info, dict):
        raw_basic_info = {}

    raw_target_country = _normalize_optional_text(raw_basic_info.get("target_country"))
    raw_major_interest = _normalize_optional_text(
        raw_basic_info.get("major_interest") or raw_basic_info.get("MAJ_INTEREST_TEXT")
    )
    raw_curriculum_system = raw_basic_info.get("curriculum_system")

    official_profile_json = _filter_profile_json_to_existing_columns(
        db,
        profile_json=draft_json,
    )

    student_basic_info = official_profile_json.setdefault("student_basic_info", {})
    if raw_target_country:
        student_basic_info["CTRY_CODE_VAL"] = None
    if raw_major_interest:
        student_basic_info["MAJ_CODE_VAL"] = None

    _sync_curriculum_system_rows_from_raw_basic_info(
        official_profile_json,
        student_id=student_id,
        raw_curriculum_system=raw_curriculum_system,
    )
    prune_academic_payload_by_curriculum_system(official_profile_json)

    apply_code_resolution_to_payload(
        db,
        official_profile_json,
        target_country_text=raw_target_country,
        major_text=raw_major_interest,
        school_name_text=(official_profile_json.get("student_academic") or {}).get("school_name"),
    )

    return official_profile_json


def _filter_profile_json_to_existing_columns(
    db: Session,
    *,
    profile_json: dict[str, Any],
) -> dict[str, Any]:
    filtered_payload: dict[str, Any] = {}
    table_columns_map = _get_profile_draft_table_columns_map(db)

    for table_name in SINGLE_ROW_TABLES:
        table_value = profile_json.get(table_name)
        allowed_columns = table_columns_map.get(table_name, set())
        if isinstance(table_value, dict):
            filtered_payload[table_name] = {
                field_name: deepcopy(field_value)
                for field_name, field_value in table_value.items()
                if field_name in allowed_columns
            }
        else:
            filtered_payload[table_name] = {}

    for table_name in MULTI_ROW_TABLES:
        table_value = profile_json.get(table_name)
        allowed_columns = table_columns_map.get(table_name, set())
        filtered_rows: list[dict[str, Any]] = []
        if isinstance(table_value, list):
            for row in table_value:
                if not isinstance(row, dict):
                    continue
                filtered_rows.append(
                    {
                        field_name: deepcopy(field_value)
                        for field_name, field_value in row.items()
                        if field_name in allowed_columns
                    }
                )
        filtered_payload[table_name] = filtered_rows

    return filtered_payload


def _get_profile_draft_table_columns_map(db: Session) -> dict[str, set[str]]:
    target_tables = tuple(SINGLE_ROW_TABLES + MULTI_ROW_TABLES)
    missing_tables = [table_name for table_name in target_tables if table_name not in PROFILE_DRAFT_TABLE_COLUMNS_CACHE]
    if missing_tables:
        query_params = {f"table_{index}": table_name for index, table_name in enumerate(missing_tables)}
        placeholders = ", ".join(f":{param_name}" for param_name in query_params)
        rows = db.execute(
            text(
                f"""
                SELECT TABLE_NAME, COLUMN_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ({placeholders})
                """
            ),
            query_params,
        ).mappings().all()
        for table_name in missing_tables:
            PROFILE_DRAFT_TABLE_COLUMNS_CACHE[table_name] = set()
        for row in rows:
            PROFILE_DRAFT_TABLE_COLUMNS_CACHE.setdefault(row["TABLE_NAME"], set()).add(row["COLUMN_NAME"])

    return {
        table_name: set(PROFILE_DRAFT_TABLE_COLUMNS_CACHE.get(table_name, set()))
        for table_name in target_tables
    }


def _sync_curriculum_system_rows_from_raw_basic_info(
    profile_json: dict[str, Any],
    *,
    student_id: str,
    raw_curriculum_system: Any,
) -> None:
    curriculum_codes = _normalize_curriculum_system_codes(raw_curriculum_system)
    if not curriculum_codes:
        return

    profile_json["student_basic_info_curriculum_system"] = [
        {
            "student_id": student_id,
            "curriculum_system_code": curriculum_code,
            "is_primary": 1 if index == 0 else 0,
        }
        for index, curriculum_code in enumerate(curriculum_codes)
    ]


def _normalize_curriculum_system_codes(raw_value: Any) -> list[str]:
    if isinstance(raw_value, str):
        stripped = raw_value.strip()
        return [stripped] if stripped else []
    if isinstance(raw_value, list):
        normalized_codes: list[str] = []
        for item in raw_value:
            if not isinstance(item, str):
                continue
            stripped = item.strip()
            if stripped:
                normalized_codes.append(stripped)
        return normalized_codes
    return []


def _normalize_optional_text(value: Any) -> str | None:
    if isinstance(value, str):
        stripped = value.strip()
        return stripped or None
    if value is None:
        return None
    stringified = str(value).strip()
    return stringified or None


def _merge_draft_patch_document(
    *,
    current_draft_json: dict[str, Any],
    patch_json: dict[str, Any],
    student_id: str,
) -> dict[str, Any]:
    merged_draft = deepcopy(current_draft_json)
    for table_name, patch_value in patch_json.items():
        if table_name.startswith("_"):
            continue

        if table_name in SINGLE_ROW_TABLES and isinstance(patch_value, dict):
            current_value = merged_draft.get(table_name)
            if not isinstance(current_value, dict):
                current_value = {}
            merged_draft[table_name] = _merge_single_row_patch(
                table_name=table_name,
                current_row=current_value,
                patch_row=patch_value,
                student_id=student_id,
            )
            continue

        if table_name in MULTI_ROW_TABLES and isinstance(patch_value, list):
            current_rows = merged_draft.get(table_name)
            if not isinstance(current_rows, list):
                current_rows = []
            merged_draft[table_name] = _merge_multi_row_patch(
                table_name=table_name,
                current_rows=current_rows,
                patch_rows=patch_value,
                student_id=student_id,
            )

    return merged_draft


def _merge_single_row_patch(
    *,
    table_name: str,
    current_row: dict[str, Any],
    patch_row: dict[str, Any],
    student_id: str,
) -> dict[str, Any]:
    merged_row = deepcopy(current_row)
    for field_name, field_value in patch_row.items():
        if field_name in PROFILE_DRAFT_INTERNAL_KEYS:
            continue
        if field_value is None:
            continue
        merged_row[field_name] = deepcopy(field_value)

    if table_name in PROFILE_DRAFT_SINGLE_TABLES_WITH_STUDENT_ID:
        merged_row["student_id"] = student_id
    return merged_row


def _merge_multi_row_patch(
    *,
    table_name: str,
    current_rows: list[Any],
    patch_rows: list[Any],
    student_id: str,
) -> list[dict[str, Any]]:
    merged_rows: list[dict[str, Any]] = [
        deepcopy(row) for row in current_rows if isinstance(row, dict)
    ]

    for raw_patch_row in patch_rows:
        if not isinstance(raw_patch_row, dict):
            continue

        action = str(raw_patch_row.get("_action") or "append").strip().lower()
        match_key = raw_patch_row.get("_match_key")
        if not isinstance(match_key, dict):
            match_key = {}

        clean_patch_row = {
            field_name: deepcopy(field_value)
            for field_name, field_value in raw_patch_row.items()
            if field_name not in PROFILE_DRAFT_INTERNAL_KEYS
        }
        if not _is_meaningful_patch_row(
            clean_patch_row,
            table_name=table_name,
        ):
            continue

        if table_name in PROFILE_DRAFT_MULTI_TABLES_WITH_STUDENT_ID:
            clean_patch_row["student_id"] = student_id

        if action == "update" and match_key:
            matched_index = _find_multi_row_match_index(
                rows=merged_rows,
                match_key=match_key,
            )
            if matched_index >= 0:
                merged_rows[matched_index] = _merge_multi_row_values(
                    current_row=merged_rows[matched_index],
                    patch_row=clean_patch_row,
                    table_name=table_name,
                    student_id=student_id,
                )
                continue

        merged_rows.append(clean_patch_row)

    return merged_rows


def _merge_multi_row_values(
    *,
    current_row: dict[str, Any],
    patch_row: dict[str, Any],
    table_name: str,
    student_id: str,
) -> dict[str, Any]:
    merged_row = deepcopy(current_row)
    for field_name, field_value in patch_row.items():
        if field_value is None:
            continue
        merged_row[field_name] = deepcopy(field_value)

    if table_name in PROFILE_DRAFT_MULTI_TABLES_WITH_STUDENT_ID:
        merged_row["student_id"] = student_id
    return merged_row


def _find_multi_row_match_index(
    *,
    rows: list[dict[str, Any]],
    match_key: dict[str, Any],
) -> int:
    normalized_match_key = {
        field_name: field_value
        for field_name, field_value in match_key.items()
        if field_value is not None
    }
    if not normalized_match_key:
        return -1

    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            continue
        if all(row.get(field_name) == field_value for field_name, field_value in normalized_match_key.items()):
            return index
    return -1


def _is_meaningful_patch_row(
    patch_row: dict[str, Any],
    *,
    table_name: str,
) -> bool:
    for field_name, field_value in patch_row.items():
        if field_name == "student_id":
            continue
        if field_value is None:
            continue
        if isinstance(field_value, str) and not field_value.strip():
            continue
        if isinstance(field_value, list) and not field_value:
            continue
        if isinstance(field_value, dict) and not field_value:
            continue
        return True

    # 中文注释：
    # 竞赛 / 活动 / 项目这三类目前只依赖 notes。
    if table_name in {
        "student_competition_entries",
        "student_activity_entries",
        "student_project_entries",
    }:
        return bool(str(patch_row.get("notes") or "").strip())
    return False


def _parse_json_object(
    *,
    raw_text: str,
    scene_name: str,
) -> dict[str, Any]:
    cleaned = raw_text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```JSON")
        cleaned = cleaned.removeprefix("```").removesuffix("```").strip()

    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise ValueError(f"{scene_name} 返回内容不是合法 JSON") from exc

    if not isinstance(parsed, dict):
        raise ValueError(f"{scene_name} 返回结果必须是 JSON 对象")
    return parsed


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
