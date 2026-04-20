from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.student_profile_guided import GuidedAnswerPayload
from backend.schemas.student_profile_guided import GuidedExitPayload
from backend.services.student_profile_guided_service import QUESTIONS
from backend.services.student_profile_guided_service import exit_guided_session
from backend.services.student_profile_guided_service import generate_guided_result
from backend.services.student_profile_guided_service import get_or_create_current_bundle
from backend.services.student_profile_guided_service import get_session_bundle
from backend.services.student_profile_guided_service import restart_current_session
from backend.services.student_profile_guided_service import submit_guided_answer


router = APIRouter(prefix="/api/v1/student-profile/guided", tags=["student-profile-guided"])


def _current_student_id(authorization: Annotated[str | None, Header()] = None) -> str:
    return get_current_user_id(authorization)


@router.get("/questions")
def list_guided_questions() -> dict[str, object]:
    return {"questions": QUESTIONS}


@router.get("/current")
def get_current_guided_session(
    create_if_missing: Annotated[int, Query()] = 1,
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    return get_or_create_current_bundle(
        db,
        student_id=student_id,
        create_if_missing=bool(create_if_missing),
    )


@router.post("/restart")
def restart_guided_session(
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    return restart_current_session(db, student_id=student_id)


@router.get("/sessions/{session_id}")
def get_guided_session(
    session_id: str,
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    try:
        return get_session_bundle(db, student_id=student_id, session_id=session_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@router.post("/sessions/{session_id}/answers")
def submit_answer(
    session_id: str,
    payload: GuidedAnswerPayload,
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    try:
        return submit_guided_answer(
            db,
            student_id=student_id,
            session_id=session_id,
            question_code=payload.question_code,
            raw_answer=payload.answer,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@router.post("/sessions/{session_id}/exit")
def exit_session(
    session_id: str,
    payload: GuidedExitPayload,
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    try:
        return exit_guided_session(
            db,
            student_id=student_id,
            session_id=session_id,
            trigger_reason=payload.trigger_reason,
        )
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@router.post("/sessions/{session_id}/result")
def regenerate_result(
    session_id: str,
    db: Session = Depends(get_db),
    student_id: str = Depends(_current_student_id),
) -> dict[str, object]:
    try:
        result = generate_guided_result(
            db,
            student_id=student_id,
            session_id=session_id,
            trigger_reason="manual_regenerate",
        )
        return {"result": result}
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

