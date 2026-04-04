"""
正式档案页保存与六维图重算服务。

这条链路保持旧接口不变，但内部已经支持：
1. 保存正式档案后累计 pending changed fields
2. 更新六维图时区分 full / partial / reuse_only
3. 档案页更新六维图始终基于正式业务表，不读取 draft
"""

from __future__ import annotations

from datetime import date
from datetime import datetime
from decimal import Decimal
import json
import logging
from typing import Any

from sqlalchemy.orm import Session

from backend.services.ai_chat_service import save_profile_result
from backend.services.ai_chat_service import update_session_stage
from backend.services.ai_profile_radar_pending_service import accumulate_pending_radar_changes
from backend.services.ai_profile_radar_pending_service import build_reuse_only_profile_result_payload
from backend.services.ai_profile_radar_pending_service import build_scoring_context_for_strategy
from backend.services.ai_profile_radar_pending_service import extract_changed_fields_from_profile_diff
from backend.services.ai_profile_radar_pending_service import get_latest_complete_profile_result
from backend.services.ai_profile_radar_pending_service import map_changed_fields_to_affected_dimensions
from backend.services.ai_profile_radar_pending_service import merge_partial_scoring_result
from backend.services.ai_profile_radar_pending_service import RadarRegenerationStrategy
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.business_profile_form_service import load_business_profile_snapshot
from backend.services.business_profile_persistence_service import build_db_payload_from_profile_json
from backend.services.business_profile_persistence_service import persist_business_profile_snapshot
from backend.utils.flow_logging import log_timed_step


logger = logging.getLogger(__name__)

SCORING_PROMPT_KEY = "student_profile_build.scoring"


def save_business_profile_form_only(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    archive_form: dict[str, Any],
) -> dict[str, Any]:
    """
    只保存正式档案表单，不重算六维图。

    说明：
    1. 保存前后会做正式档案快照 diff。
    2. 只有在该会话已经存在至少一版完整六维图时，才会累计 pending changes。
    """

    if not isinstance(archive_form, dict):
        raise ValueError("档案表单数据格式错误。")

    previous_profile_json = load_business_profile_snapshot(
        db,
        student_id=student_id,
    )
    canonical_profile_json = _persist_archive_form_and_load_snapshot(
        db,
        student_id=student_id,
        session_id=session_id,
        archive_form=archive_form,
    )

    changed_fields = extract_changed_fields_from_profile_diff(
        previous_profile_json=previous_profile_json,
        current_profile_json=canonical_profile_json,
    )
    accumulate_pending_radar_changes(
        db,
        student_id=student_id,
        session_id=session_id,
        biz_domain="student_profile_build",
        changed_fields=changed_fields,
        change_source="archive_form_save",
        change_remark="正式档案页保存后累计待处理六维图改动",
    )

    update_session_stage(
        db,
        student_id=student_id,
        session_id=session_id,
        current_stage="build_ready",
        remark=None,
    )
    return canonical_profile_json


def regenerate_profile_result_from_business_profile_snapshot(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
) -> dict[str, Any]:
    """
    基于正式业务表快照更新六维图。

    规则：
    1. 没有旧六维图结果时，走 full scoring。
    2. 已有旧结果，但本次改动不影响六维图时，直接复用旧分数。
    3. 已有旧结果，且本次改动影响部分维度时，只重算受影响维度。

    注意：
    档案页更新六维图不会清空共享的 pending changes，
    因为这些 pending 还可能包含仅存在于 draft 的改动。
    """

    try:
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
            step_name="回读正式档案快照",
            student_id=student_id,
            session_id=session_id,
        ):
            canonical_profile_json = load_business_profile_snapshot(
                db,
                student_id=student_id,
            )

        strategy = _build_archive_regeneration_strategy(
            db,
            student_id=student_id,
            session_id=session_id,
            biz_domain=biz_domain,
            canonical_profile_json=canonical_profile_json,
        )
        scoring_context = build_scoring_context_for_strategy(
            student_id=student_id,
            session_id=session_id,
            profile_json=canonical_profile_json,
            strategy=strategy,
        )

        if strategy.mode == "reuse_only":
            scoring_json = build_reuse_only_profile_result_payload(
                profile_json=canonical_profile_json,
                strategy=strategy,
            )
        else:
            with log_timed_step(
                logger,
                flow_name="AI建档",
                step_name="基于正式档案重算六维图",
                student_id=student_id,
                session_id=session_id,
            ):
                logger.info(
                    "[AI建档][步骤:基于正式档案重算六维图][学生:%s][会话:%s] 传给AI的scoring上下文=%s",
                    student_id,
                    session_id,
                    json.dumps(scoring_context, ensure_ascii=False, indent=2),
                )
                scoring_runtime_result = execute_prompt_with_context(
                    db,
                    prompt_key=SCORING_PROMPT_KEY,
                    context=scoring_context,
                )
                scoring_json = _parse_json_object(
                    raw_text=scoring_runtime_result.content,
                    scene_name="archive_form_scoring",
                )
                scoring_json = merge_partial_scoring_result(
                    strategy=strategy,
                    scoring_json=scoring_json,
                )

        with log_timed_step(
            logger,
            flow_name="AI建档结果",
            step_name="回写正式档案六维图结果",
            student_id=student_id,
            session_id=session_id,
        ):
            saved_result = save_profile_result(
                db,
                student_id=student_id,
                session_id=session_id,
                biz_domain=biz_domain,
                profile_json=scoring_context["profile_json"],
                radar_scores_json=scoring_json.get("radar_scores_json"),
                summary_text=scoring_json.get("summary_text"),
                result_status="saved",
                db_payload_json=scoring_context["profile_json"],
                save_error_message=None,
                session_stage="build_ready",
                session_remark=None,
            )
    except Exception as exc:
        try:
            db.rollback()
        except Exception:
            logger.exception(
                "[AI建档][步骤:基于正式档案重算六维图][学生:%s][会话:%s] 回滚事务失败",
                student_id,
                session_id,
            )
        update_session_stage(
            db,
            student_id=student_id,
            session_id=session_id,
            current_stage="build_ready",
            remark=str(exc)[:500],
        )
        raise ValueError("六维图重新生成失败，请稍后重试。") from exc

    return {
        "session_id": session_id,
        "profile_result_id": saved_result.profile_result_id,
        "profile_json": scoring_context["profile_json"],
        "radar_scores_json": scoring_json.get("radar_scores_json") or {},
        "summary_text": scoring_json.get("summary_text"),
        "result_status": "saved",
        "save_error_message": None,
        "db_payload_json": scoring_context["profile_json"],
        "regeneration_mode": strategy.mode,
        "affected_dimensions": strategy.affected_dimensions,
        "changed_fields": strategy.changed_fields,
    }


def _build_archive_regeneration_strategy(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    biz_domain: str,
    canonical_profile_json: dict[str, Any],
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

    changed_fields = extract_changed_fields_from_profile_diff(
        previous_profile_json=previous_result.profile_json or {},
        current_profile_json=canonical_profile_json,
    )
    affected_dimensions = map_changed_fields_to_affected_dimensions(
        db,
        biz_domain=biz_domain,
        changed_fields=changed_fields,
    )
    if not changed_fields or not affected_dimensions:
        return RadarRegenerationStrategy(
            mode="reuse_only",
            previous_result=previous_result,
            pending_row=None,
            changed_fields=changed_fields,
            affected_dimensions=affected_dimensions,
            previous_radar_scores_json=_to_json_safe(previous_result.radar_scores_json or {}),
            previous_summary_text=previous_result.summary_text,
        )

    return RadarRegenerationStrategy(
        mode="partial",
        previous_result=previous_result,
        pending_row=None,
        changed_fields=changed_fields,
        affected_dimensions=affected_dimensions,
        previous_radar_scores_json=_to_json_safe(previous_result.radar_scores_json or {}),
        previous_summary_text=previous_result.summary_text,
    )


def _persist_archive_form_and_load_snapshot(
    db: Session,
    *,
    student_id: str,
    session_id: str,
    archive_form: dict[str, Any],
) -> dict[str, Any]:
    """
    保存正式业务表并回读数据库最新快照。
    """

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="保存正式档案表单",
        student_id=student_id,
        session_id=session_id,
    ):
        db_payload = build_db_payload_from_profile_json(
            archive_form,
            student_id=student_id,
        )
        persist_business_profile_snapshot(
            db,
            db_payload=db_payload,
            student_id=student_id,
        )

    with log_timed_step(
        logger,
        flow_name="AI建档",
        step_name="回读正式档案快照",
        student_id=student_id,
        session_id=session_id,
    ):
        return load_business_profile_snapshot(
            db,
            student_id=student_id,
        )


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
        raise ValueError(f"{scene_name} 返回内容不是合法 JSON。") from exc

    if not isinstance(parsed, dict):
        raise ValueError(f"{scene_name} 返回结果必须是 JSON 对象。")
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
