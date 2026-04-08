from __future__ import annotations

from sqlalchemy import or_
from sqlalchemy.orm import Session

from backend.models.ai_chat_models import AiChatProfileResult
from backend.models.auth_models import User
from backend.services.auth_service import get_active_user_by_id
from backend.services.business_profile_form_service import load_business_profile_form_bundle


def _get_active_teacher_user(db: Session, user_id: str) -> User:
    teacher = get_active_user_by_id(db, user_id)
    if not teacher or teacher.status != "active":
        raise PermissionError("当前账号不可用")
    if getattr(teacher, "is_teacher", "0") != "1":
        raise PermissionError("当前账号未开通教师端")
    return teacher


def _get_target_student(db: Session, keyword: str) -> User:
    normalized_keyword = (keyword or "").strip()
    if not normalized_keyword:
        raise ValueError("请输入学生ID或手机号")

    student = (
        db.query(User)
        .filter(
            User.delete_flag == "1",
            or_(User.id == normalized_keyword, User.mobile == normalized_keyword),
        )
        .first()
    )
    if not student:
        raise LookupError("未找到对应学生")
    return student


def _get_latest_profile_result(db: Session, student_id: str) -> AiChatProfileResult | None:
    return (
        db.query(AiChatProfileResult)
        .filter(
            AiChatProfileResult.student_id == student_id,
            AiChatProfileResult.delete_flag == "1",
        )
        .order_by(
            AiChatProfileResult.update_time.desc(),
            AiChatProfileResult.create_time.desc(),
            AiChatProfileResult.id.desc(),
        )
        .first()
    )


def load_teacher_student_archive_bundle(
    db: Session,
    *,
    teacher_user_id: str,
    keyword: str,
) -> dict:
    _get_active_teacher_user(db, teacher_user_id)
    student = _get_target_student(db, keyword)
    form_bundle = load_business_profile_form_bundle(db, student_id=student.id)
    latest_result = _get_latest_profile_result(db, student.id)

    return {
        "student": {
            "user_id": student.id,
            "mobile": student.mobile,
            "nickname": student.nickname,
            "status": student.status,
        },
        "session_id": latest_result.session_id if latest_result else None,
        "archive_form": form_bundle.get("archive_form") or {},
        "form_meta": form_bundle.get("form_meta") or {"table_order": [], "tables": {}},
        "result_status": latest_result.result_status if latest_result else None,
        "summary_text": latest_result.summary_text if latest_result else None,
        "radar_scores_json": latest_result.radar_scores_json or {} if latest_result else {},
        "save_error_message": latest_result.save_error_message if latest_result else None,
        "create_time": latest_result.create_time if latest_result else None,
        "update_time": latest_result.update_time if latest_result else None,
    }
