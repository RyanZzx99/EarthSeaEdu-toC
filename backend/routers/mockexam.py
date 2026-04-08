from __future__ import annotations

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.mockexam import MockExamExamSetCreateRequest
from backend.schemas.mockexam import MockExamExamSetDeleteResponse
from backend.schemas.mockexam import MockExamExamSetListResponse
from backend.schemas.mockexam import MockExamExamSetMutationResponse
from backend.schemas.mockexam import MockExamExamSetPayloadResponse
from backend.schemas.mockexam import MockExamExamSetStatusUpdateRequest
from backend.schemas.mockexam import MockExamInlineSubmitRequest
from backend.schemas.mockexam import MockExamOptionsResponse
from backend.schemas.mockexam import MockExamQuestionBankListResponse
from backend.schemas.mockexam import MockExamQuestionBankPayloadResponse
from backend.schemas.mockexam import MockExamQuickPracticeBuildRequest
from backend.schemas.mockexam import MockExamQuickPracticeBuildResponse
from backend.schemas.mockexam import MockExamSubmitRequest
from backend.schemas.mockexam import MockExamSubmitResponse
from backend.services.mockexam_service import build_quick_practice_payload
from backend.services.mockexam_service import create_mockexam_exam_set
from backend.services.mockexam_service import delete_mockexam_exam_set
from backend.services.mockexam_service import evaluate_inline_mockexam_payload
from backend.services.mockexam_service import evaluate_mockexam_exam_set_submission
from backend.services.mockexam_service import evaluate_mockexam_submission
from backend.services.mockexam_service import get_mockexam_options
from backend.services.mockexam_service import list_mockexam_exam_sets
from backend.services.mockexam_service import list_mockexam_question_banks
from backend.services.mockexam_service import load_mockexam_exam_set_payload
from backend.services.mockexam_service import load_mockexam_payload
from backend.services.mockexam_service import serialize_exam_set_item
from backend.services.mockexam_service import update_mockexam_exam_set_status
from backend.services.auth_service import get_active_user_by_id


router = APIRouter()


def require_mockexam_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> str:
    return get_current_user_id(authorization)


def require_mockexam_teacher_user(
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
) -> str:
    user = get_active_user_by_id(db, user_id)
    if not user or user.status != "active":
        raise HTTPException(status_code=401, detail="当前登录状态无效")
    if getattr(user, "is_teacher", "0") != "1":
        raise HTTPException(status_code=403, detail="当前账号未开通教师端")
    return user_id


@router.get("/options", response_model=MockExamOptionsResponse)
def get_mockexam_options_api(
    _user_id: str = Depends(require_mockexam_user),
):
    return get_mockexam_options()


@router.get("/question-banks", response_model=MockExamQuestionBankListResponse)
def list_mockexam_question_banks_api(
    exam_category: str | None = Query(default=None, description="考试类别"),
    exam_content: str | None = Query(default=None, description="考试内容"),
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        rows = list_mockexam_question_banks(
            db,
            exam_category=exam_category,
            exam_content=exam_content,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "items": [
            {
                "id": row.id,
                "file_name": row.file_name,
                "exam_category": row.exam_category,
                "exam_content": row.exam_content,
                "create_time": row.create_time,
            }
            for row in rows
        ]
    }


@router.get("/question-banks/{question_bank_id}", response_model=MockExamQuestionBankPayloadResponse)
def get_mockexam_question_bank_api(
    question_bank_id: int,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row, payload = load_mockexam_payload(db, question_bank_id=question_bank_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "id": row.id,
        "file_name": row.file_name,
        "exam_category": row.exam_category,
        "exam_content": row.exam_content,
        "payload": payload,
    }


@router.post("/question-banks/{question_bank_id}/submit", response_model=MockExamSubmitResponse)
def submit_mockexam_question_bank_api(
    question_bank_id: int,
    payload: MockExamSubmitRequest,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        result = evaluate_mockexam_submission(
            db,
            question_bank_id=question_bank_id,
            answers_map=payload.answers,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "result": result,
    }


@router.get("/exam-sets", response_model=MockExamExamSetListResponse)
def list_mockexam_exam_sets_api(
    exam_category: str | None = Query(default=None, description="考试类别"),
    exam_content: str | None = Query(default=None, description="考试内容"),
    status: str | None = Query(default=None, description="状态过滤：1启用，0停用"),
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        rows = list_mockexam_exam_sets(
            db,
            exam_category=exam_category,
            exam_content=exam_content,
            status=status,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "items": [serialize_exam_set_item(row) for row in rows],
    }


@router.get("/exam-sets/{exam_sets_id}", response_model=MockExamExamSetPayloadResponse)
def get_mockexam_exam_set_api(
    exam_sets_id: int,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        exam_set, payload = load_mockexam_exam_set_payload(db, exam_sets_id=exam_sets_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    item = serialize_exam_set_item(exam_set)
    return {
        **item,
        "payload": payload,
    }


@router.post("/exam-sets", response_model=MockExamExamSetMutationResponse)
def create_mockexam_exam_set_api(
    payload: MockExamExamSetCreateRequest,
    _user_id: str = Depends(require_mockexam_teacher_user),
    db: Session = Depends(get_db),
):
    try:
        row = create_mockexam_exam_set(
            db,
            name=payload.name,
            mode=payload.mode,
            exam_category=payload.exam_category,
            question_bank_ids=payload.question_bank_ids,
            exam_contents=payload.exam_contents,
            per_content=payload.per_content,
            extra_count=payload.extra_count,
            total_count=payload.total_count,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "item": serialize_exam_set_item(row),
    }


@router.post("/exam-sets/{exam_sets_id}/status", response_model=MockExamExamSetMutationResponse)
def update_mockexam_exam_set_status_api(
    exam_sets_id: int,
    payload: MockExamExamSetStatusUpdateRequest,
    _user_id: str = Depends(require_mockexam_teacher_user),
    db: Session = Depends(get_db),
):
    try:
        row = update_mockexam_exam_set_status(
            db,
            exam_sets_id=exam_sets_id,
            status=payload.status,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "item": serialize_exam_set_item(row),
    }


@router.delete("/exam-sets/{exam_sets_id}", response_model=MockExamExamSetDeleteResponse)
def delete_mockexam_exam_set_api(
    exam_sets_id: int,
    _user_id: str = Depends(require_mockexam_teacher_user),
    db: Session = Depends(get_db),
):
    try:
        delete_mockexam_exam_set(db, exam_sets_id=exam_sets_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    return {"status": "ok"}


@router.post("/exam-sets/{exam_sets_id}/submit", response_model=MockExamSubmitResponse)
def submit_mockexam_exam_set_api(
    exam_sets_id: int,
    payload: MockExamSubmitRequest,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        result = evaluate_mockexam_exam_set_submission(
            db,
            exam_sets_id=exam_sets_id,
            answers_map=payload.answers,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "result": result,
    }


@router.post("/quick-practice/build", response_model=MockExamQuickPracticeBuildResponse)
def build_mockexam_quick_practice_api(
    payload: MockExamQuickPracticeBuildRequest,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        return build_quick_practice_payload(
            db,
            exam_category=payload.exam_category,
            exam_contents=payload.exam_contents,
            count=payload.count,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post("/evaluate", response_model=MockExamSubmitResponse)
def evaluate_inline_mockexam_payload_api(
    payload: MockExamInlineSubmitRequest,
    _user_id: str = Depends(require_mockexam_user),
    _db: Session = Depends(get_db),
):
    try:
        result = evaluate_inline_mockexam_payload(
            payload=payload.payload,
            answers_map=payload.answers,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "result": result,
    }
