"""
正式档案表单保存与六维图重算服务。

中文注释：
1. 这条链路面向档案页的“查看并手动修改正式业务表”场景。
2. 用户提交的是正式业务表结构化表单，而不是 profile_json 文本。
3. 当前拆成两条链路：
   - 保存档案：只更新正式业务表，不调用 AI。
   - 重新生成六维图：读取数据库最新快照，只重跑 scoring。
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
    """

    if not isinstance(archive_form, dict):
        raise ValueError("档案表单数据格式错误。")

    canonical_profile_json = _persist_archive_form_and_load_snapshot(
        db,
        student_id=student_id,
        session_id=session_id,
        archive_form=archive_form,
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
    基于数据库最新正式档案快照重新生成六维图。

    中文注释：
    1. 这里不再跑 extraction。
    2. 六维图完全基于正式业务表快照重跑 scoring。
    3. 调 AI 前会把 scoring 上下文完整打到日志里，方便控制台排查。
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

        scoring_context = {
            "student_id": student_id,
            "session_id": session_id,
            "profile_json": _to_json_safe(canonical_profile_json),
        }

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

        with log_timed_step(
            logger,
            flow_name="AI建档结果",
            step_name="回写重算后的六维图结果",
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
    }


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
    """
    解析模型返回的 JSON 对象。

    中文注释：
    这里保留一层轻量兜底，避免模型偶尔带上 markdown 包裹导致重新评分失败。
    """

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
    """把数据库快照里的非常规类型转成可 JSON 序列化的安全对象。"""

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
