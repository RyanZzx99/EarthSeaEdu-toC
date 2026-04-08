from __future__ import annotations

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.teacher import TeacherStudentArchiveLookupResponse
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
