from __future__ import annotations

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.mockexam import MockExamOptionsResponse
from backend.schemas.mockexam import MockExamQuestionBankListResponse
from backend.schemas.mockexam import MockExamQuestionBankPayloadResponse
from backend.schemas.mockexam import MockExamSubmitRequest
from backend.schemas.mockexam import MockExamSubmitResponse
from backend.services.mockexam_service import evaluate_mockexam_submission
from backend.services.mockexam_service import get_mockexam_options
from backend.services.mockexam_service import load_mockexam_payload
from backend.services.mockexam_service import list_mockexam_question_banks


router = APIRouter()


def require_mockexam_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> str:
    return get_current_user_id(authorization)


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
    rows = list_mockexam_question_banks(
        db,
        exam_category=exam_category,
        exam_content=exam_content,
    )
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
