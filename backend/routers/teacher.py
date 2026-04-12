from __future__ import annotations

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.mockexam import MockExamPaperSetListResponse
from backend.schemas.mockexam import TeacherMockExamPaperSetCreateRequest
from backend.schemas.mockexam import TeacherMockExamPaperSetMutationResponse
from backend.schemas.mockexam import TeacherMockExamPaperSetStatusUpdateRequest
from backend.schemas.teacher import TeacherStudentArchiveLookupResponse
from backend.services.mockexam_paper_set_service import create_teacher_mockexam_paper_set
from backend.services.mockexam_paper_set_service import list_teacher_mockexam_paper_sets
from backend.services.mockexam_paper_set_service import update_teacher_mockexam_paper_set_status
from backend.services.teacher_service import load_teacher_student_archive_bundle


router = APIRouter()


def require_teacher_user_id(
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> str:
    return get_current_user_id(authorization)


@router.get("/students/archive", response_model=TeacherStudentArchiveLookupResponse)
def get_teacher_student_archive_api(
    keyword: str = Query(..., min_length=1, description="学生ID或手机号"),
    teacher_user_id: str = Depends(require_teacher_user_id),
    db: Session = Depends(get_db),
):
    try:
        return load_teacher_student_archive_bundle(
            db,
            teacher_user_id=teacher_user_id,
            keyword=keyword,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/mockexam/paper-sets", response_model=MockExamPaperSetListResponse)
def list_teacher_mockexam_paper_sets_api(
    teacher_user_id: str = Depends(require_teacher_user_id),
    db: Session = Depends(get_db),
):
    try:
        return {
            "items": list_teacher_mockexam_paper_sets(
                db,
                teacher_user_id=teacher_user_id,
            )
        }
    except PermissionError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc


@router.post("/mockexam/paper-sets", response_model=TeacherMockExamPaperSetMutationResponse)
def create_teacher_mockexam_paper_set_api(
    payload: TeacherMockExamPaperSetCreateRequest,
    teacher_user_id: str = Depends(require_teacher_user_id),
    db: Session = Depends(get_db),
):
    try:
        item = create_teacher_mockexam_paper_set(
            db,
            teacher_user_id=teacher_user_id,
            set_name=payload.set_name,
            exam_paper_ids=payload.exam_paper_ids,
            remark=payload.remark,
        )
        return {"status": "ok", "item": item}
    except PermissionError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post(
    "/mockexam/paper-sets/{mockexam_paper_set_id}/status",
    response_model=TeacherMockExamPaperSetMutationResponse,
)
def update_teacher_mockexam_paper_set_status_api(
    mockexam_paper_set_id: int,
    payload: TeacherMockExamPaperSetStatusUpdateRequest,
    teacher_user_id: str = Depends(require_teacher_user_id),
    db: Session = Depends(get_db),
):
    try:
        item = update_teacher_mockexam_paper_set_status(
            db,
            teacher_user_id=teacher_user_id,
            mockexam_paper_set_id=mockexam_paper_set_id,
            status=payload.status,
        )
        return {"status": "ok", "item": item}
    except PermissionError as exc:
        raise HTTPException(status_code=403, detail=str(exc)) from exc
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
