from __future__ import annotations

import json
import re

from fastapi import APIRouter
from fastapi import Depends
from fastapi import Header
from fastapi import HTTPException
from fastapi import Query
from sqlalchemy.orm import Session

from backend.config.db_conf import get_db
from backend.routers.auth import get_current_user_id
from backend.schemas.mockexam import MockExamSelectionTranslateRequest
from backend.schemas.mockexam import MockExamSelectionTranslateResponse
from backend.schemas.mockexam import MockExamFavoriteListResponse
from backend.schemas.mockexam import MockExamFavoriteBatchToggleRequest
from backend.schemas.mockexam import MockExamFavoriteBatchToggleResponse
from backend.schemas.mockexam import MockExamEntityFavoriteListResponse
from backend.schemas.mockexam import MockExamFavoriteToggleRequest
from backend.schemas.mockexam import MockExamFavoriteToggleResponse
from backend.schemas.mockexam import MockExamOptionsResponse
from backend.schemas.mockexam import MockExamPaperListResponse
from backend.schemas.mockexam import MockExamPaperPayloadResponse
from backend.schemas.mockexam import MockExamPaperSetListResponse
from backend.schemas.mockexam import MockExamPaperSetPayloadResponse
from backend.schemas.mockexam import MockExamProgressDetailResponse
from backend.schemas.mockexam import MockExamProgressListResponse
from backend.schemas.mockexam import MockExamProgressMutationResponse
from backend.schemas.mockexam import MockExamProgressSaveRequest
from backend.schemas.mockexam import MockExamQuestionDetailResponse
from backend.schemas.mockexam import MockExamSubmissionDetailResponse
from backend.schemas.mockexam import MockExamSubmissionListResponse
from backend.schemas.mockexam import MockExamSubmitRequest
from backend.schemas.mockexam import MockExamSubmitResponse
from backend.schemas.mockexam import MockExamWrongQuestionListResponse
from backend.schemas.mockexam import MockExamWrongQuestionResolveRequest
from backend.schemas.mockexam import MockExamWrongQuestionResolveResponse
from backend.services.ai_prompt_runtime_service import PromptRuntimeError
from backend.services.ai_prompt_runtime_service import execute_prompt_with_context
from backend.services.mockexam_beta_service import discard_beta_mockexam_progress
from backend.services.mockexam_beta_service import get_active_beta_mockexam_progress
from backend.services.mockexam_beta_service import get_beta_mockexam_question_detail
from backend.services.mockexam_beta_service import get_beta_mockexam_submission
from backend.services.mockexam_beta_service import list_beta_mockexam_favorites
from backend.services.mockexam_beta_service import list_beta_mockexam_entity_favorites
from backend.services.mockexam_beta_service import list_beta_mockexam_progresses
from backend.services.mockexam_beta_service import list_beta_mockexam_submissions
from backend.services.mockexam_beta_service import list_beta_mockexam_wrong_questions
from backend.services.mockexam_beta_service import resolve_beta_mockexam_wrong_questions
from backend.services.mockexam_beta_service import save_beta_mockexam_progress
from backend.services.mockexam_beta_service import save_beta_mockexam_paper_set_progress
from backend.services.mockexam_beta_service import serialize_beta_mockexam_progress_detail
from backend.services.mockexam_beta_service import serialize_beta_mockexam_progress_item
from backend.services.mockexam_beta_service import serialize_beta_mockexam_submission_detail
from backend.services.mockexam_beta_service import serialize_beta_mockexam_submission_item
from backend.services.mockexam_beta_service import serialize_beta_mockexam_submission_summary
from backend.services.mockexam_beta_service import submit_beta_mockexam_paper
from backend.services.mockexam_beta_service import submit_beta_mockexam_paper_set
from backend.services.mockexam_beta_service import toggle_beta_mockexam_question_favorite
from backend.services.mockexam_beta_service import toggle_beta_mockexam_entity_paper_favorite
from backend.services.mockexam_beta_service import toggle_beta_mockexam_entity_paper_set_favorite
from backend.services.mockexam_paper_set_service import list_student_mockexam_paper_sets
from backend.services.mockexam_paper_set_service import load_mockexam_paper_set_payload
from backend.services.mockexam_service import get_mockexam_beta_options
from backend.services.mockexam_service import list_mockexam_beta_papers
from backend.services.mockexam_service import load_mockexam_beta_paper_payload


router = APIRouter()
VALID_SELECTION_SCOPE_TYPES = {"material", "question"}
TRANSLATION_FIELD_PATTERN = re.compile(r'"translation"\s*:\s*"((?:\\.|[^"\\])*)', re.S)


def require_mockexam_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
) -> str:
    return get_current_user_id(authorization)


def parse_mockexam_translate_content(raw_content: str) -> dict[str, object]:
    cleaned = str(raw_content or "").strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```JSON")
        cleaned = cleaned.removeprefix("```").removesuffix("```").strip()

    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError:
        first_brace = cleaned.find("{")
        last_brace = cleaned.rfind("}")
        if first_brace >= 0 and last_brace > first_brace:
            parsed = json.loads(cleaned[first_brace : last_brace + 1])
        elif match := TRANSLATION_FIELD_PATTERN.search(cleaned):
            raw_translation = match.group(1)
            try:
                translation = json.loads(f'"{raw_translation}"')
            except json.JSONDecodeError:
                translation = raw_translation.replace('\\"', '"').replace("\\n", "\n").strip()
            parsed = {
                "translation": translation,
                "source_language": "en",
                "target_language": "zh-CN",
                "confidence": "low",
            }
        elif cleaned and not cleaned.startswith("{") and '"translation"' not in cleaned:
            parsed = {
                "translation": cleaned,
                "source_language": "en",
                "target_language": "zh-CN",
                "confidence": "medium",
            }
        else:
            raise
    if not isinstance(parsed, dict):
        raise ValueError("翻译结果必须是 JSON 对象")
    return parsed


@router.get("/options", response_model=MockExamOptionsResponse)
def get_mockexam_options_api(
    _user_id: str = Depends(require_mockexam_user),
):
    return get_mockexam_beta_options()


@router.get("/papers", response_model=MockExamPaperListResponse)
def list_mockexam_papers_api(
    exam_category: str | None = Query(default=None, description="Exam category"),
    exam_content: str | None = Query(default=None, description="Exam content"),
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        rows = list_mockexam_beta_papers(
            db,
            exam_category=exam_category,
            exam_content=exam_content,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "items": [
            {
                "exam_paper_id": row.exam_paper_id,
                "paper_code": row.paper_code,
                "paper_name": row.paper_name,
                "bank_name": row.bank.bank_name if row.bank else "",
                "exam_category": row.bank.exam_type if row.bank and row.bank.exam_type else "IELTS",
                "exam_content": "Listening" if str(row.subject_type or "").lower() == "listening" else "Reading",
                "module_name": row.module_name or "",
                "book_code": row.book_code,
                "test_no": row.test_no,
                "create_time": row.create_time,
            }
            for row in rows
        ]
    }


@router.get("/papers/{exam_paper_id}", response_model=MockExamPaperPayloadResponse)
def get_mockexam_paper_api(
    exam_paper_id: int,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row, payload = load_mockexam_beta_paper_payload(db, exam_paper_id=exam_paper_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "exam_paper_id": row.exam_paper_id,
        "paper_code": row.paper_code,
        "paper_name": row.paper_name,
        "bank_name": row.bank.bank_name if row.bank else "",
        "exam_category": row.bank.exam_type if row.bank and row.bank.exam_type else "IELTS",
        "exam_content": "Listening" if str(row.subject_type or "").lower() == "listening" else "Reading",
        "module_name": row.module_name or "",
        "book_code": row.book_code,
        "test_no": row.test_no,
        "payload": payload,
    }


@router.get("/paper-sets", response_model=MockExamPaperSetListResponse)
def list_mockexam_paper_sets_api(
    exam_category: str | None = Query(default=None, description="Exam category"),
    exam_content: str | None = Query(default=None, description="Exam content"),
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        items = list_student_mockexam_paper_sets(
            db,
            exam_category=exam_category,
            exam_content=exam_content,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"items": items}


@router.get("/paper-sets/{mockexam_paper_set_id}", response_model=MockExamPaperSetPayloadResponse)
def get_mockexam_paper_set_api(
    mockexam_paper_set_id: int,
    _user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        paper_set, payload = load_mockexam_paper_set_payload(
            db,
            mockexam_paper_set_id=mockexam_paper_set_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "mockexam_paper_set_id": paper_set.mockexam_paper_set_id,
        "set_name": paper_set.set_name,
        "exam_category": paper_set.exam_category,
        "exam_content": paper_set.exam_content,
        "paper_count": int(paper_set.paper_count or 0),
        "payload": payload,
    }


@router.post("/papers/{exam_paper_id}/submit", response_model=MockExamSubmitResponse)
def submit_mockexam_paper_api(
    exam_paper_id: int,
    payload: MockExamSubmitRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        result, submission, wrongbook_review = submit_beta_mockexam_paper(
            db,
            user_id=user_id,
            exam_paper_id=exam_paper_id,
            answers_map=payload.answers,
            marked_map=payload.marked,
            elapsed_seconds=payload.elapsed_seconds,
            progress_id=payload.progress_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "result": result,
        "submission": serialize_beta_mockexam_submission_summary(submission),
        "wrongbook_review": wrongbook_review,
    }


@router.post("/paper-sets/{mockexam_paper_set_id}/submit", response_model=MockExamSubmitResponse)
def submit_mockexam_paper_set_api(
    mockexam_paper_set_id: int,
    payload: MockExamSubmitRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        result, submission, wrongbook_review = submit_beta_mockexam_paper_set(
            db,
            user_id=user_id,
            mockexam_paper_set_id=mockexam_paper_set_id,
            answers_map=payload.answers,
            marked_map=payload.marked,
            elapsed_seconds=payload.elapsed_seconds,
            progress_id=payload.progress_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "status": "ok",
        "result": result,
        "submission": serialize_beta_mockexam_submission_summary(submission),
        "wrongbook_review": wrongbook_review,
    }


@router.get("/submissions", response_model=MockExamSubmissionListResponse)
def list_mockexam_submissions_api(
    exam_content: str | None = Query(default=None, description="Exam content"),
    limit: int = Query(default=20, ge=1, le=100, description="Record count"),
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        rows = list_beta_mockexam_submissions(
            db,
            user_id=user_id,
            exam_content=exam_content,
            limit=limit,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"items": [serialize_beta_mockexam_submission_item(row) for row in rows]}


@router.get("/submissions/{submission_id}", response_model=MockExamSubmissionDetailResponse)
def get_mockexam_submission_api(
    submission_id: int,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = get_beta_mockexam_submission(db, user_id=user_id, submission_id=submission_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return serialize_beta_mockexam_submission_detail(row)


@router.get("/progress", response_model=MockExamProgressListResponse)
def list_mockexam_progresses_api(
    limit: int = Query(default=10, ge=1, le=50, description="Record count"),
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    rows = list_beta_mockexam_progresses(db, user_id=user_id, limit=limit)
    return {"items": [serialize_beta_mockexam_progress_item(row) for row in rows]}


@router.get("/progress/{progress_id}", response_model=MockExamProgressDetailResponse)
def get_mockexam_progress_api(
    progress_id: int,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return serialize_beta_mockexam_progress_detail(row)


@router.post("/papers/{exam_paper_id}/progress", response_model=MockExamProgressMutationResponse)
def save_mockexam_progress_api(
    exam_paper_id: int,
    payload: MockExamProgressSaveRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = save_beta_mockexam_progress(
            db,
            user_id=user_id,
            exam_paper_id=exam_paper_id,
            payload=payload.payload,
            answers_map=payload.answers,
            marked_map=payload.marked,
            current_question_id=payload.current_question_id,
            current_question_index=payload.current_question_index,
            current_question_no=payload.current_question_no,
            elapsed_seconds=payload.elapsed_seconds,
            progress_id=payload.progress_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"status": "ok", "item": serialize_beta_mockexam_progress_item(row)}


@router.post("/paper-sets/{mockexam_paper_set_id}/progress", response_model=MockExamProgressMutationResponse)
def save_mockexam_paper_set_progress_api(
    mockexam_paper_set_id: int,
    payload: MockExamProgressSaveRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = save_beta_mockexam_paper_set_progress(
            db,
            user_id=user_id,
            mockexam_paper_set_id=mockexam_paper_set_id,
            payload=payload.payload,
            answers_map=payload.answers,
            marked_map=payload.marked,
            current_question_id=payload.current_question_id,
            current_question_index=payload.current_question_index,
            current_question_no=payload.current_question_no,
            elapsed_seconds=payload.elapsed_seconds,
            progress_id=payload.progress_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"status": "ok", "item": serialize_beta_mockexam_progress_item(row)}


@router.post("/progress/{progress_id}/discard", response_model=MockExamProgressMutationResponse)
def discard_mockexam_progress_api(
    progress_id: int,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = discard_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return {"status": "ok", "item": serialize_beta_mockexam_progress_item(row)}


@router.get("/favorites", response_model=MockExamFavoriteListResponse)
def list_mockexam_favorites_api(
    exam_paper_id: int | None = Query(default=None, description="Paper ID"),
    limit: int = Query(default=50, ge=1, le=200, description="Record count"),
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    return {
        "items": list_beta_mockexam_favorites(
            db,
            user_id=user_id,
            exam_paper_id=exam_paper_id,
            limit=limit,
        )
    }


@router.get("/entity-favorites", response_model=MockExamEntityFavoriteListResponse)
def list_mockexam_entity_favorites_api(
    limit: int = Query(default=200, ge=1, le=200, description="Record count"),
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    return {
        "items": list_beta_mockexam_entity_favorites(
            db,
            user_id=user_id,
            limit=limit,
        )
    }


@router.post("/translate-selection", response_model=MockExamSelectionTranslateResponse)
def translate_mockexam_selection_api(
    payload: MockExamSelectionTranslateRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    selected_text = str(payload.selected_text or "").strip()
    if not selected_text:
        raise HTTPException(status_code=400, detail="selected_text 不能为空")

    scope_type = str(payload.scope_type or "").strip().lower()
    if scope_type not in VALID_SELECTION_SCOPE_TYPES:
        raise HTTPException(status_code=400, detail="scope_type 仅支持 material 或 question")

    target_lang = str(payload.target_lang or "zh-CN").strip() or "zh-CN"
    context = {
        "student_id": user_id,
        "selected_text": selected_text,
        "scope_type": scope_type,
        "module_name": str(payload.module_name or "").strip(),
        "passage_id": str(payload.passage_id or "").strip(),
        "question_id": str(payload.question_id or "").strip(),
        "question_type": str(payload.question_type or "").strip(),
        "surrounding_text_before": str(payload.surrounding_text_before or "").strip(),
        "surrounding_text_after": str(payload.surrounding_text_after or "").strip(),
        "target_lang": target_lang,
    }

    try:
        runtime_result = execute_prompt_with_context(
            db,
            prompt_key="mockexam.selection_translate",
            context=context,
        )
        parsed = parse_mockexam_translate_content(runtime_result.content)
    except PromptRuntimeError as exc:
        raise HTTPException(status_code=503, detail=f"翻译服务暂不可用：{exc}") from exc
    except (json.JSONDecodeError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=502, detail=f"翻译结果解析失败：{exc}") from exc

    translation = str(parsed.get("translation") or "").strip()
    if not translation:
        raise HTTPException(status_code=502, detail="翻译结果缺少 translation 字段")

    source_language = str(parsed.get("source_language") or "en").strip() or "en"
    response_target_lang = str(parsed.get("target_language") or target_lang).strip() or target_lang
    confidence = str(parsed.get("confidence") or "").strip() or None
    cached_raw = parsed.get("cached", False)
    cached = cached_raw if isinstance(cached_raw, bool) else str(cached_raw).strip().lower() in {
        "1",
        "true",
        "yes",
    }

    return {
        "translation": translation,
        "source_language": source_language,
        "target_language": response_target_lang,
        "confidence": confidence,
        "cached": cached,
    }


@router.post("/questions/{exam_question_id}/favorite", response_model=MockExamFavoriteToggleResponse)
def toggle_mockexam_question_favorite_api(
    exam_question_id: int,
    payload: MockExamFavoriteToggleRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        toggle_beta_mockexam_question_favorite(
            db,
            user_id=user_id,
            exam_question_id=exam_question_id,
            is_favorite=payload.is_favorite,
            source_kind=payload.source_kind,
            paper_set_id=payload.paper_set_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return {"status": "ok", "is_favorite": payload.is_favorite}


@router.post("/papers/{exam_paper_id}/favorite", response_model=MockExamFavoriteBatchToggleResponse)
def toggle_mockexam_paper_favorite_api(
    exam_paper_id: int,
    payload: MockExamFavoriteBatchToggleRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = toggle_beta_mockexam_entity_paper_favorite(
            db,
            user_id=user_id,
            exam_paper_id=exam_paper_id,
            is_favorite=payload.is_favorite,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {
        "status": "ok",
        "is_favorite": payload.is_favorite,
        "affected_count": 1 if row is not None or not payload.is_favorite else 0,
    }


@router.post("/paper-sets/{mockexam_paper_set_id}/favorite", response_model=MockExamFavoriteBatchToggleResponse)
def toggle_mockexam_paper_set_favorite_api(
    mockexam_paper_set_id: int,
    payload: MockExamFavoriteBatchToggleRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        row = toggle_beta_mockexam_entity_paper_set_favorite(
            db,
            user_id=user_id,
            mockexam_paper_set_id=mockexam_paper_set_id,
            is_favorite=payload.is_favorite,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {
        "status": "ok",
        "is_favorite": payload.is_favorite,
        "affected_count": 1 if row is not None or not payload.is_favorite else 0,
    }


@router.get("/wrong-questions", response_model=MockExamWrongQuestionListResponse)
def list_mockexam_wrong_questions_api(
    limit: int = Query(default=50, ge=1, le=200, description="Record count"),
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    return list_beta_mockexam_wrong_questions(db, user_id=user_id, limit=limit)


@router.post("/wrong-questions/resolve", response_model=MockExamWrongQuestionResolveResponse)
def resolve_mockexam_wrong_questions_api(
    payload: MockExamWrongQuestionResolveRequest,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    removed_count = resolve_beta_mockexam_wrong_questions(
        db,
        user_id=user_id,
        exam_question_ids=payload.exam_question_ids,
    )
    return {
        "status": "ok",
        "removed_count": removed_count,
    }


@router.get("/questions/{exam_question_id}", response_model=MockExamQuestionDetailResponse)
def get_mockexam_question_detail_api(
    exam_question_id: int,
    user_id: str = Depends(require_mockexam_user),
    db: Session = Depends(get_db),
):
    try:
        return get_beta_mockexam_question_detail(
            db,
            user_id=user_id,
            exam_question_id=exam_question_id,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
