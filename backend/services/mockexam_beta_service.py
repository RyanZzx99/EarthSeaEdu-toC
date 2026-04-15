from __future__ import annotations

import copy
import html
import re
from datetime import datetime
from typing import Any

from sqlalchemy import func
from sqlalchemy.orm import Session
from sqlalchemy.orm import selectinload

from backend.models.ielts_exam_models import ExamGroup
from backend.models.ielts_exam_models import ExamPaper
from backend.models.ielts_exam_models import ExamQuestion
from backend.models.ielts_exam_models import ExamSection
from backend.models.mockexam_record_models import MockExamProgress
from backend.models.mockexam_record_models import MockExamProgressAnswer
from backend.models.mockexam_record_models import MockExamProgressQuestionState
from backend.models.mockexam_record_models import MockExamEntityFavorite
from backend.models.mockexam_record_models import MockExamQuestionFavorite
from backend.models.mockexam_record_models import MockExamSubmission
from backend.models.mockexam_record_models import MockExamSubmissionAnswer
from backend.models.mockexam_record_models import MockExamSubmissionQuestionState
from backend.models.mockexam_record_models import MockExamWrongQuestionStat
from backend.models.mockexam_paper_set_models import MockExamPaperSet
from backend.services.mockexam_paper_set_service import PAPER_SET_SOURCE_KIND
from backend.services.mockexam_paper_set_service import build_paper_set_paper_code
from backend.services.mockexam_paper_set_service import extract_record_source_meta
from backend.services.mockexam_paper_set_service import get_mockexam_paper_set
from backend.services.mockexam_paper_set_service import load_mockexam_paper_set_payload
from backend.services.mockexam_service import SUPPORTED_MOCKEXAM_BETA_CATEGORIES
from backend.services.mockexam_service import beta_exam_content_from_subject_type
from backend.services.mockexam_service import build_mockexam_beta_payload
from backend.services.mockexam_service import clamp_int
from backend.services.mockexam_service import evaluate_quiz_payload
from backend.services.mockexam_service import evaluate_single_question
from backend.services.mockexam_service import flatten_questions_for_score
from backend.services.mockexam_service import get_mockexam_beta_paper
from backend.services.mockexam_service import infer_question_type
from backend.services.mockexam_service import normalize_mockexam_beta_exam_content
from backend.services.mockexam_service import repair_mockexam_payload
from backend.services.mockexam_service import serialize_mockexam_beta_group
from backend.services.mockexam_service import serialize_mockexam_beta_group_options
from backend.services.mockexam_service import serialize_mockexam_beta_passage
from backend.services.mockexam_service import serialize_mockexam_beta_question


def current_utc_time() -> datetime:
    return datetime.utcnow()


def normalize_favorite_source_kind(value: Any) -> str:
    raw_value = str(value or "").strip().lower()
    if raw_value in {"paper-set", "paper_set"}:
        return PAPER_SET_SOURCE_KIND
    return "paper"


def safe_float_value(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


def normalize_elapsed_seconds(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def extract_preview_text(value: Any, *, limit: int = 180) -> str:
    if value is None:
        return ""
    text = html.unescape(str(value))
    text = re.sub(r"\[\[\s*[^\]]+\s*\]\]", "_____", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) <= limit:
        return text
    return f"{text[: max(0, limit - 3)].rstrip()}..."


def has_meaningful_html_content(value: Any) -> bool:
    html_content = str(value or "").strip()
    if not html_content:
        return False
    if re.search(r"<(img|audio|video|table|svg|iframe)\b", html_content, re.I):
        return True
    text = re.sub(r"<[^>]+>", " ", html_content)
    text = re.sub(r"&nbsp;", " ", text, flags=re.I)
    text = re.sub(r"\s+", " ", text).strip()
    return bool(text)


def render_plain_text_to_html(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    lines = [line.strip() for line in re.split(r"\r?\n+", text) if line.strip()]
    return "".join(f"<p>{html.escape(line)}</p>" for line in lines)


def build_question_detail_material_html(
    section_payload: dict[str, Any],
    group_payload: dict[str, Any],
) -> str:
    material_parts: list[str] = []
    section_instructions_html = render_plain_text_to_html(section_payload.get("instructions"))
    section_content_html = str(section_payload.get("content") or "").strip()
    group_instructions_html = str(group_payload.get("instructions") or "").strip()

    if has_meaningful_html_content(section_instructions_html):
        material_parts.append(section_instructions_html)
    if has_meaningful_html_content(section_content_html):
        material_parts.append(section_content_html)
    if not material_parts and has_meaningful_html_content(group_instructions_html):
        material_parts.append(group_instructions_html)
    if not material_parts and section_payload.get("audio"):
        material_parts.append("<p>本题仅提供听力音频材料，请播放上方音频后作答。</p>")

    return "".join(material_parts)


def normalize_exam_question_id_for_record(question: dict[str, Any]) -> int | None:
    value = question.get("exam_question_id")
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def extract_question_no_for_record(question: dict[str, Any], sequence: int) -> str | None:
    for key in ("question_no", "displayNo", "display_no"):
        value = question.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return str(sequence)


def get_matching_items_for_record(question: dict[str, Any]) -> list[dict[str, Any]]:
    raw_items = question.get("questions")
    if not isinstance(raw_items, list):
        raw_items = question.get("items")
    return [item for item in (raw_items or []) if isinstance(item, dict)]


def flatten_questions_for_record_ops(payload: Any) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for index, item in enumerate(flatten_questions_for_score(payload)):
        question = item["question"]
        question_type = infer_question_type(question)
        records.append(
            {
                "id": item["id"],
                "question": question,
                "question_index": index,
                "question_no": extract_question_no_for_record(question, index + 1),
                "question_type": question_type,
                "stat_type": str(question.get("stat_type") or question_type or "").strip(),
                "exam_question_id": normalize_exam_question_id_for_record(question),
            }
        )
    return records


def answer_value_present(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, str):
        return bool(value.strip())
    if isinstance(value, list):
        return any(answer_value_present(item) for item in value)
    if isinstance(value, dict):
        return any(answer_value_present(item) for item in value.values())
    return bool(str(value).strip())


def build_atomic_answer_entries(question_record: dict[str, Any], answer_value: Any) -> list[dict[str, Any]]:
    question = question_record["question"]
    question_type = question_record["question_type"]
    base_entry = {
        "exam_question_id": question_record["exam_question_id"],
        "question_id": question_record["id"],
        "question_no": question_record["question_no"],
        "question_type": question_type,
        "stat_type": question_record["stat_type"],
    }
    entries: list[dict[str, Any]] = []

    if question_type == "multiple":
        values = answer_value if isinstance(answer_value, list) else [answer_value]
        for sort_order, value in enumerate(values, start=1):
            if not answer_value_present(value):
                continue
            entries.append(
                {
                    **base_entry,
                    "blank_id": None,
                    "item_id": None,
                    "answer_value": str(value).strip(),
                    "sort_order": sort_order,
                }
            )
        return entries

    if question_type == "cloze_inline":
        answer_map = answer_value if isinstance(answer_value, dict) else {}
        for sort_order, blank in enumerate(question.get("blanks") or [], start=1):
            if not isinstance(blank, dict):
                continue
            blank_id = str(blank.get("id") or "").strip()
            if not blank_id:
                continue
            value = answer_map.get(blank_id)
            if not answer_value_present(value):
                continue
            entries.append(
                {
                    **base_entry,
                    "blank_id": blank_id,
                    "item_id": None,
                    "answer_value": str(value).strip(),
                    "sort_order": sort_order,
                }
            )
        return entries

    if question_type == "matching":
        answer_map = answer_value if isinstance(answer_value, dict) else {}
        for sort_order, item in enumerate(get_matching_items_for_record(question), start=1):
            item_id = str(item.get("id") or "").strip()
            if not item_id:
                continue
            value = answer_map.get(item_id)
            if not answer_value_present(value):
                continue
            entries.append(
                {
                    **base_entry,
                    "blank_id": None,
                    "item_id": item_id,
                    "answer_value": str(value).strip(),
                    "sort_order": sort_order,
                }
            )
        return entries

    if not answer_value_present(answer_value):
        return []

    return [
        {
            **base_entry,
            "blank_id": None,
            "item_id": None,
            "answer_value": str(answer_value).strip(),
            "sort_order": 1,
        }
    ]


def build_question_state_entries(
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    *,
    include_correct: bool,
) -> list[dict[str, Any]]:
    states: list[dict[str, Any]] = []
    for question_record in flatten_questions_for_record_ops(payload):
        question = question_record["question"]
        question_id = question_record["id"]
        evaluation = evaluate_single_question(question, answers_map.get(question_id))
        entry = {
            "exam_question_id": question_record["exam_question_id"],
            "question_id": question_id,
            "question_no": question_record["question_no"],
            "question_index": question_record["question_index"],
            "question_type": question_record["question_type"],
            "stat_type": question_record["stat_type"],
            "is_marked": 1 if bool(marked_map.get(question_id)) else 0,
            "is_answered": 1 if evaluation["answered"] else 0,
        }
        if include_correct:
            entry["is_correct"] = 1 if evaluation["correct"] else 0 if evaluation["gradable"] else None
        states.append(entry)
    return states


def build_mockexam_answer_entries(payload: Any, answers_map: dict[str, Any]) -> list[dict[str, Any]]:
    answer_entries: list[dict[str, Any]] = []
    for question_record in flatten_questions_for_record_ops(payload):
        answer_entries.extend(build_atomic_answer_entries(question_record, answers_map.get(question_record["id"])))
    return answer_entries


def build_answers_map_from_rows(rows: list[Any] | None) -> dict[str, Any]:
    grouped: dict[str, list[Any]] = {}
    for row in sorted(
        rows or [],
        key=lambda item: (
            str(getattr(item, "question_id", "")),
            int(getattr(item, "sort_order", 0) or 0),
            int(
                getattr(item, "mockexam_progress_answer_id", 0)
                or getattr(item, "mockexam_submission_answer_id", 0)
                or 0
            ),
        ),
    ):
        question_id = str(getattr(row, "question_id", "") or "").strip()
        if question_id:
            grouped.setdefault(question_id, []).append(row)

    answers_map: dict[str, Any] = {}
    for question_id, items in grouped.items():
        question_type = str(getattr(items[0], "question_type", "") or "").strip().lower()
        if question_type == "multiple":
            answers_map[question_id] = [
                str(item.answer_value or "").strip()
                for item in items
                if answer_value_present(getattr(item, "answer_value", None))
            ]
        elif question_type == "cloze_inline":
            answers_map[question_id] = {
                str(item.blank_id): str(item.answer_value or "").strip()
                for item in items
                if str(getattr(item, "blank_id", "") or "").strip()
            }
        elif question_type == "matching":
            answers_map[question_id] = {
                str(item.item_id): str(item.answer_value or "").strip()
                for item in items
                if str(getattr(item, "item_id", "") or "").strip()
            }
        else:
            answers_map[question_id] = str(items[0].answer_value or "").strip()
    return answers_map


def build_marked_map_from_rows(rows: list[Any] | None) -> dict[str, bool]:
    result: dict[str, bool] = {}
    for row in rows or []:
        question_id = str(getattr(row, "question_id", "") or "").strip()
        if question_id:
            result[question_id] = bool(getattr(row, "is_marked", 0))
    return result


def replace_progress_children(
    db: Session,
    *,
    progress_id: int,
    answer_entries: list[dict[str, Any]],
    state_entries: list[dict[str, Any]],
) -> None:
    db.query(MockExamProgressAnswer).filter(
        MockExamProgressAnswer.mockexam_progress_id == progress_id
    ).delete(synchronize_session=False)
    db.query(MockExamProgressQuestionState).filter(
        MockExamProgressQuestionState.mockexam_progress_id == progress_id
    ).delete(synchronize_session=False)

    if answer_entries:
        db.add_all(
            [
                MockExamProgressAnswer(mockexam_progress_id=progress_id, delete_flag="1", **entry)
                for entry in answer_entries
            ]
        )
    if state_entries:
        db.add_all(
            [
                MockExamProgressQuestionState(mockexam_progress_id=progress_id, delete_flag="1", **entry)
                for entry in state_entries
            ]
        )


def replace_submission_children(
    db: Session,
    *,
    submission_id: int,
    answer_entries: list[dict[str, Any]],
    state_entries: list[dict[str, Any]],
) -> None:
    db.query(MockExamSubmissionAnswer).filter(
        MockExamSubmissionAnswer.mockexam_submission_id == submission_id
    ).delete(synchronize_session=False)
    db.query(MockExamSubmissionQuestionState).filter(
        MockExamSubmissionQuestionState.mockexam_submission_id == submission_id
    ).delete(synchronize_session=False)

    if answer_entries:
        db.add_all(
            [
                MockExamSubmissionAnswer(mockexam_submission_id=submission_id, delete_flag="1", **entry)
                for entry in answer_entries
            ]
        )
    if state_entries:
        db.add_all(
            [
                MockExamSubmissionQuestionState(mockexam_submission_id=submission_id, delete_flag="1", **entry)
                for entry in state_entries
            ]
        )


def create_beta_mockexam_submission_record(
    db: Session,
    *,
    user_id: str,
    paper: ExamPaper,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    result: dict[str, Any],
    elapsed_seconds: int = 0,
) -> MockExamSubmission:
    submission = MockExamSubmission(
        user_id=user_id,
        exam_paper_id=paper.exam_paper_id,
        paper_code=paper.paper_code,
        title=paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}",
        exam_category=(paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS"),
        exam_content=beta_exam_content_from_subject_type(paper.subject_type),
        payload_json=copy.deepcopy(payload),
        answered_count=int(result.get("answered_count") or 0),
        total_questions=int(result.get("total_questions") or 0),
        gradable_questions=int(result.get("gradable_questions") or 0),
        elapsed_seconds=normalize_elapsed_seconds(elapsed_seconds),
        correct_count=int(result.get("correct_count") or 0),
        wrong_count=int(result.get("wrong_count") or 0),
        unanswered_count=int(result.get("unanswered_count") or 0),
        score_percent=result.get("score_percent"),
        status=1,
        delete_flag="1",
    )
    db.add(submission)
    db.flush()
    replace_submission_children(
        db,
        submission_id=submission.mockexam_submission_id,
        answer_entries=build_mockexam_answer_entries(payload, answers_map),
        state_entries=build_question_state_entries(payload, answers_map, marked_map, include_correct=True),
    )
    return submission


def create_beta_mockexam_progress_record(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int,
    paper_code: str | None,
    title: str,
    exam_category: str,
    exam_content: str | None,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    current_question_id: str | None,
    current_question_index: int | None,
    current_question_no: str | None,
    elapsed_seconds: int = 0,
) -> MockExamProgress:
    result = evaluate_quiz_payload(payload, answers_map)
    progress = MockExamProgress(
        user_id=user_id,
        exam_paper_id=exam_paper_id,
        paper_code=paper_code,
        title=title,
        exam_category=exam_category,
        exam_content=exam_content,
        payload_json=copy.deepcopy(payload),
        current_question_id=(current_question_id or "").strip() or None,
        current_question_index=current_question_index,
        current_question_no=(current_question_no or "").strip() or None,
        answered_count=int(result.get("answered_count") or 0),
        total_questions=int(result.get("total_questions") or 0),
        elapsed_seconds=normalize_elapsed_seconds(elapsed_seconds),
        last_active_time=current_utc_time(),
        status=1,
        delete_flag="1",
    )
    db.add(progress)
    db.flush()
    replace_progress_children(
        db,
        progress_id=progress.mockexam_progress_id,
        answer_entries=build_mockexam_answer_entries(payload, answers_map),
        state_entries=build_question_state_entries(payload, answers_map, marked_map, include_correct=False),
    )
    return progress


def create_beta_mockexam_paper_set_submission_record(
    db: Session,
    *,
    user_id: str,
    paper_set: MockExamPaperSet,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    result: dict[str, Any],
    elapsed_seconds: int = 0,
) -> MockExamSubmission:
    submission = MockExamSubmission(
        user_id=user_id,
        exam_paper_id=0,
        paper_code=build_paper_set_paper_code(paper_set.mockexam_paper_set_id),
        title=paper_set.set_name,
        exam_category=paper_set.exam_category or "IELTS",
        exam_content=paper_set.exam_content,
        payload_json=copy.deepcopy(payload),
        answered_count=int(result.get("answered_count") or 0),
        total_questions=int(result.get("total_questions") or 0),
        gradable_questions=int(result.get("gradable_questions") or 0),
        elapsed_seconds=normalize_elapsed_seconds(elapsed_seconds),
        correct_count=int(result.get("correct_count") or 0),
        wrong_count=int(result.get("wrong_count") or 0),
        unanswered_count=int(result.get("unanswered_count") or 0),
        score_percent=result.get("score_percent"),
        status=1,
        delete_flag="1",
    )
    db.add(submission)
    db.flush()
    replace_submission_children(
        db,
        submission_id=submission.mockexam_submission_id,
        answer_entries=build_mockexam_answer_entries(payload, answers_map),
        state_entries=build_question_state_entries(payload, answers_map, marked_map, include_correct=True),
    )
    return submission


def serialize_beta_mockexam_submission_summary(submission: MockExamSubmission) -> dict[str, Any]:
    return {
        "submission_id": submission.mockexam_submission_id,
        "title": submission.title,
        "elapsed_seconds": int(submission.elapsed_seconds or 0),
        "create_time": submission.create_time,
    }


def serialize_beta_mockexam_submission_item(submission: MockExamSubmission) -> dict[str, Any]:
    source_meta = extract_record_source_meta(
        paper_code=submission.paper_code,
        payload_json=submission.payload_json,
    )
    return {
        "submission_id": submission.mockexam_submission_id,
        "exam_paper_id": submission.exam_paper_id,
        "source_kind": source_meta["source_kind"],
        "paper_set_id": source_meta["paper_set_id"],
        "paper_code": submission.paper_code,
        "title": submission.title,
        "exam_category": submission.exam_category,
        "exam_content": submission.exam_content,
        "score_percent": safe_float_value(submission.score_percent),
        "total_questions": int(submission.total_questions or 0),
        "correct_count": int(submission.correct_count or 0),
        "wrong_count": int(submission.wrong_count or 0),
        "gradable_questions": int(submission.gradable_questions or 0),
        "elapsed_seconds": int(submission.elapsed_seconds or 0),
        "create_time": submission.create_time,
    }


def serialize_beta_mockexam_submission_detail(submission: MockExamSubmission) -> dict[str, Any]:
    source_meta = extract_record_source_meta(
        paper_code=submission.paper_code,
        payload_json=submission.payload_json,
    )
    answers_map = build_answers_map_from_rows(submission.answers)
    payload = repair_mockexam_payload(submission.payload_json)
    return {
        "submission_id": submission.mockexam_submission_id,
        "exam_paper_id": submission.exam_paper_id,
        "source_kind": source_meta["source_kind"],
        "paper_set_id": source_meta["paper_set_id"],
        "paper_code": submission.paper_code,
        "title": submission.title,
        "exam_category": submission.exam_category,
        "exam_content": submission.exam_content,
        "elapsed_seconds": int(submission.elapsed_seconds or 0),
        "create_time": submission.create_time,
        "payload": payload,
        "answers": answers_map,
        "marked": build_marked_map_from_rows(submission.question_states),
        "result": evaluate_quiz_payload(payload, answers_map),
    }


def list_beta_mockexam_submissions(
    db: Session,
    *,
    user_id: str,
    exam_content: str | None = None,
    limit: int = 20,
) -> list[MockExamSubmission]:
    query = (
        db.query(MockExamSubmission)
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
    )
    normalized_exam_content = normalize_mockexam_beta_exam_content(exam_content, allow_empty=True)
    if normalized_exam_content:
        query = query.filter(MockExamSubmission.exam_content == normalized_exam_content)
    return (
        query.order_by(MockExamSubmission.create_time.desc(), MockExamSubmission.mockexam_submission_id.desc())
        .limit(clamp_int(limit, default=20, minimum=1, maximum=100))
        .all()
    )


def get_beta_mockexam_submission(db: Session, *, user_id: str, submission_id: int) -> MockExamSubmission:
    row = (
        db.query(MockExamSubmission)
        .options(
            selectinload(MockExamSubmission.answers),
            selectinload(MockExamSubmission.question_states),
        )
        .filter(MockExamSubmission.mockexam_submission_id == submission_id)
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
        .first()
    )
    if not row:
        raise LookupError("未找到该模考成绩记录")
    return row


def get_active_beta_mockexam_progress(db: Session, *, user_id: str, progress_id: int) -> MockExamProgress:
    row = (
        db.query(MockExamProgress)
        .options(
            selectinload(MockExamProgress.answers),
            selectinload(MockExamProgress.question_states),
        )
        .filter(MockExamProgress.mockexam_progress_id == progress_id)
        .filter(MockExamProgress.user_id == user_id)
        .filter(MockExamProgress.delete_flag == "1")
        .filter(MockExamProgress.status == 1)
        .first()
    )
    if not row:
        raise LookupError("未找到该未完成模考记录")
    return row


def serialize_beta_mockexam_progress_item(progress: MockExamProgress) -> dict[str, Any]:
    source_meta = extract_record_source_meta(
        paper_code=progress.paper_code,
        payload_json=progress.payload_json,
    )
    return {
        "progress_id": progress.mockexam_progress_id,
        "exam_paper_id": progress.exam_paper_id,
        "source_kind": source_meta["source_kind"],
        "paper_set_id": source_meta["paper_set_id"],
        "paper_code": progress.paper_code,
        "title": progress.title,
        "exam_category": progress.exam_category,
        "exam_content": progress.exam_content,
        "answered_count": int(progress.answered_count or 0),
        "total_questions": int(progress.total_questions or 0),
        "elapsed_seconds": int(progress.elapsed_seconds or 0),
        "current_question_id": progress.current_question_id,
        "current_question_index": progress.current_question_index,
        "current_question_no": progress.current_question_no,
        "last_active_time": progress.last_active_time,
        "status": int(progress.status or 0),
    }


def serialize_beta_mockexam_progress_detail(progress: MockExamProgress) -> dict[str, Any]:
    payload = repair_mockexam_payload(progress.payload_json)
    return {
        **serialize_beta_mockexam_progress_item(progress),
        "payload": payload,
        "answers": build_answers_map_from_rows(progress.answers),
        "marked": build_marked_map_from_rows(progress.question_states),
    }


def list_beta_mockexam_progresses(db: Session, *, user_id: str, limit: int = 10) -> list[MockExamProgress]:
    return (
        db.query(MockExamProgress)
        .filter(MockExamProgress.user_id == user_id)
        .filter(MockExamProgress.delete_flag == "1")
        .filter(MockExamProgress.status == 1)
        .order_by(MockExamProgress.last_active_time.desc(), MockExamProgress.mockexam_progress_id.desc())
        .limit(clamp_int(limit, default=10, minimum=1, maximum=50))
        .all()
    )


def save_beta_mockexam_progress(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    current_question_id: str | None,
    current_question_index: int | None,
    current_question_no: str | None,
    elapsed_seconds: int = 0,
    progress_id: int | None = None,
) -> MockExamProgress:
    paper = get_mockexam_beta_paper(db, exam_paper_id=exam_paper_id)
    safe_payload = repair_mockexam_payload(payload) if payload is not None else build_mockexam_beta_payload(paper)
    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(safe_payload, safe_answers_map)

    progress: MockExamProgress | None = None
    if progress_id:
        progress = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
        if progress.exam_paper_id != exam_paper_id:
            raise ValueError("未完成记录与当前试卷不匹配")
    if progress is None:
        progress = (
            db.query(MockExamProgress)
            .filter(MockExamProgress.user_id == user_id)
            .filter(MockExamProgress.exam_paper_id == exam_paper_id)
            .filter(MockExamProgress.delete_flag == "1")
            .filter(MockExamProgress.status == 1)
            .order_by(MockExamProgress.last_active_time.desc(), MockExamProgress.mockexam_progress_id.desc())
            .first()
        )
    if progress is None:
        progress = MockExamProgress(
            user_id=user_id,
            exam_paper_id=paper.exam_paper_id,
            paper_code=paper.paper_code,
            title=paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}",
            exam_category=(paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS"),
            exam_content=beta_exam_content_from_subject_type(paper.subject_type),
            elapsed_seconds=normalize_elapsed_seconds(elapsed_seconds),
            status=1,
            delete_flag="1",
        )
        db.add(progress)
        db.flush()

    progress.paper_code = paper.paper_code
    progress.title = paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}"
    progress.exam_category = paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS"
    progress.exam_content = beta_exam_content_from_subject_type(paper.subject_type)
    progress.payload_json = safe_payload
    progress.current_question_id = (current_question_id or "").strip() or None
    progress.current_question_index = current_question_index
    progress.current_question_no = (current_question_no or "").strip() or None
    progress.answered_count = int(result.get("answered_count") or 0)
    progress.total_questions = int(result.get("total_questions") or 0)
    progress.elapsed_seconds = normalize_elapsed_seconds(elapsed_seconds)
    progress.last_active_time = current_utc_time()
    progress.status = 1

    replace_progress_children(
        db,
        progress_id=progress.mockexam_progress_id,
        answer_entries=build_mockexam_answer_entries(safe_payload, safe_answers_map),
        state_entries=build_question_state_entries(safe_payload, safe_answers_map, safe_marked_map, include_correct=False),
    )
    db.commit()
    db.refresh(progress)
    return get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress.mockexam_progress_id)


def save_beta_mockexam_paper_set_progress(
    db: Session,
    *,
    user_id: str,
    mockexam_paper_set_id: int,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    current_question_id: str | None,
    current_question_index: int | None,
    current_question_no: str | None,
    elapsed_seconds: int = 0,
    progress_id: int | None = None,
) -> MockExamProgress:
    paper_set, default_payload = load_mockexam_paper_set_payload(
        db,
        mockexam_paper_set_id=mockexam_paper_set_id,
    )
    safe_payload = repair_mockexam_payload(payload) if payload is not None else repair_mockexam_payload(default_payload)
    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    safe_paper_code = build_paper_set_paper_code(mockexam_paper_set_id)
    result = evaluate_quiz_payload(safe_payload, safe_answers_map)

    progress: MockExamProgress | None = None
    if progress_id:
        progress = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
        source_meta = extract_record_source_meta(
            paper_code=progress.paper_code,
            payload_json=progress.payload_json,
        )
        if source_meta["source_kind"] != PAPER_SET_SOURCE_KIND or source_meta["paper_set_id"] != mockexam_paper_set_id:
            raise ValueError("未完成记录与当前组合试卷不匹配")
    if progress is None:
        progress = (
            db.query(MockExamProgress)
            .filter(MockExamProgress.user_id == user_id)
            .filter(MockExamProgress.paper_code == safe_paper_code)
            .filter(MockExamProgress.delete_flag == "1")
            .filter(MockExamProgress.status == 1)
            .order_by(MockExamProgress.last_active_time.desc(), MockExamProgress.mockexam_progress_id.desc())
            .first()
        )
    if progress is None:
        progress = create_beta_mockexam_progress_record(
            db,
            user_id=user_id,
            exam_paper_id=0,
            paper_code=safe_paper_code,
            title=paper_set.set_name,
            exam_category=paper_set.exam_category or "IELTS",
            exam_content=paper_set.exam_content,
            payload=safe_payload,
            answers_map=safe_answers_map,
            marked_map=safe_marked_map,
            current_question_id=current_question_id,
            current_question_index=current_question_index,
            current_question_no=current_question_no,
            elapsed_seconds=elapsed_seconds,
        )
        db.commit()
        db.refresh(progress)
        return get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress.mockexam_progress_id)

    progress.exam_paper_id = 0
    progress.paper_code = safe_paper_code
    progress.title = paper_set.set_name
    progress.exam_category = paper_set.exam_category or "IELTS"
    progress.exam_content = paper_set.exam_content
    progress.payload_json = safe_payload
    progress.current_question_id = (current_question_id or "").strip() or None
    progress.current_question_index = current_question_index
    progress.current_question_no = (current_question_no or "").strip() or None
    progress.answered_count = int(result.get("answered_count") or 0)
    progress.total_questions = int(result.get("total_questions") or 0)
    progress.elapsed_seconds = normalize_elapsed_seconds(elapsed_seconds)
    progress.last_active_time = current_utc_time()
    progress.status = 1

    replace_progress_children(
        db,
        progress_id=progress.mockexam_progress_id,
        answer_entries=build_mockexam_answer_entries(safe_payload, safe_answers_map),
        state_entries=build_question_state_entries(safe_payload, safe_answers_map, safe_marked_map, include_correct=False),
    )
    db.commit()
    db.refresh(progress)
    return get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress.mockexam_progress_id)


def discard_beta_mockexam_progress(db: Session, *, user_id: str, progress_id: int) -> MockExamProgress:
    progress = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
    progress.status = 0
    progress.last_active_time = current_utc_time()
    db.commit()
    db.refresh(progress)
    return progress


def submit_beta_mockexam_paper(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    elapsed_seconds: int = 0,
    progress_id: int | None = None,
) -> tuple[dict[str, Any], MockExamSubmission, dict[str, Any]]:
    paper = get_mockexam_beta_paper(db, exam_paper_id=exam_paper_id)
    exam_category = paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS"
    if exam_category not in SUPPORTED_MOCKEXAM_BETA_CATEGORIES:
        raise ValueError("当前模拟考试暂未开放该考试类别")

    progress: MockExamProgress | None = None
    payload = build_mockexam_beta_payload(paper)
    if progress_id:
        progress = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
        if progress.exam_paper_id != exam_paper_id:
            raise ValueError("未完成记录与当前试卷不匹配")
        if progress.payload_json:
            payload = repair_mockexam_payload(progress.payload_json)
    effective_elapsed_seconds = normalize_elapsed_seconds(elapsed_seconds)
    if progress is not None:
        effective_elapsed_seconds = max(effective_elapsed_seconds, int(progress.elapsed_seconds or 0))

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_beta_mockexam_submission_record(
        db,
        user_id=user_id,
        paper=paper,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
        elapsed_seconds=effective_elapsed_seconds,
    )
    db.flush()
    wrongbook_review = sync_beta_mockexam_wrong_question_stats(
        db,
        user_id=user_id,
        submission=submission,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
    )

    if progress is not None:
        progress.status = 2
        progress.mockexam_submission_id = submission.mockexam_submission_id
        progress.elapsed_seconds = effective_elapsed_seconds
        progress.last_active_time = current_utc_time()

    db.commit()
    db.refresh(submission)
    return result, submission, wrongbook_review


def submit_beta_mockexam_paper_set(
    db: Session,
    *,
    user_id: str,
    mockexam_paper_set_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    elapsed_seconds: int = 0,
    progress_id: int | None = None,
) -> tuple[dict[str, Any], MockExamSubmission, dict[str, Any]]:
    paper_set, default_payload = load_mockexam_paper_set_payload(
        db,
        mockexam_paper_set_id=mockexam_paper_set_id,
    )
    if (paper_set.exam_category or "IELTS") not in SUPPORTED_MOCKEXAM_BETA_CATEGORIES:
        raise ValueError("当前模拟考试暂未开放该考试类别")

    progress: MockExamProgress | None = None
    payload = repair_mockexam_payload(default_payload)
    if progress_id:
        progress = get_active_beta_mockexam_progress(db, user_id=user_id, progress_id=progress_id)
        source_meta = extract_record_source_meta(
            paper_code=progress.paper_code,
            payload_json=progress.payload_json,
        )
        if source_meta["source_kind"] != PAPER_SET_SOURCE_KIND or source_meta["paper_set_id"] != mockexam_paper_set_id:
            raise ValueError("未完成记录与当前组合试卷不匹配")
        if progress.payload_json:
            payload = repair_mockexam_payload(progress.payload_json)
    effective_elapsed_seconds = normalize_elapsed_seconds(elapsed_seconds)
    if progress is not None:
        effective_elapsed_seconds = max(effective_elapsed_seconds, int(progress.elapsed_seconds or 0))

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_beta_mockexam_paper_set_submission_record(
        db,
        user_id=user_id,
        paper_set=paper_set,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
        elapsed_seconds=effective_elapsed_seconds,
    )
    db.flush()
    wrongbook_review = sync_beta_mockexam_wrong_question_stats(
        db,
        user_id=user_id,
        submission=submission,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
    )

    if progress is not None:
        progress.status = 2
        progress.mockexam_submission_id = submission.mockexam_submission_id
        progress.elapsed_seconds = effective_elapsed_seconds
        progress.last_active_time = current_utc_time()

    db.commit()
    db.refresh(submission)
    return result, submission, wrongbook_review


def extract_exam_question_preview_map(db: Session, exam_question_ids: list[int]) -> dict[int, str]:
    if not exam_question_ids:
        return {}
    rows = (
        db.query(ExamQuestion)
        .filter(ExamQuestion.exam_question_id.in_(exam_question_ids))
        .filter(ExamQuestion.delete_flag == "1")
        .all()
    )
    return {
        row.exam_question_id: (
            (row.stem_text or "").strip()
            or (row.content_text or "").strip()
            or extract_preview_text(row.stem_html or row.content_html)
        )
        for row in rows
    }


def extract_sortable_question_number(value: Any) -> tuple[int, str]:
    text = str(value or "").strip()
    matched = re.search(r"\d+", text)
    if not matched:
        return (10**9, text)
    try:
        return (int(matched.group(0)), text)
    except ValueError:
        return (10**9, text)


def load_exam_question_snapshot_map(
    db: Session,
    *,
    exam_question_ids: list[int],
) -> dict[int, ExamQuestion]:
    normalized_ids = sorted({int(value) for value in exam_question_ids if value is not None})
    if not normalized_ids:
        return {}

    rows = (
        db.query(ExamQuestion)
        .options(
            selectinload(ExamQuestion.group)
            .selectinload(ExamGroup.section)
            .selectinload(ExamSection.paper)
            .selectinload(ExamPaper.bank)
        )
        .filter(ExamQuestion.exam_question_id.in_(normalized_ids))
        .filter(ExamQuestion.delete_flag == "1")
        .all()
    )
    return {int(row.exam_question_id): row for row in rows}


def extract_wrong_question_preview_from_record(question_record: dict[str, Any]) -> str:
    question = question_record.get("question") if isinstance(question_record, dict) else {}
    candidates = []
    if isinstance(question, dict):
        candidates.extend(
            [
                question.get("stem_text"),
                question.get("content_text"),
                question.get("stem"),
                question.get("content"),
            ]
        )
    for candidate in candidates:
        preview_text = extract_preview_text(candidate, limit=240)
        if preview_text:
            return preview_text
    return ""


def build_wrong_question_snapshot(
    *,
    question_record: dict[str, Any],
    question_row: ExamQuestion | None,
    submission: MockExamSubmission,
) -> dict[str, Any]:
    group = question_row.group if question_row and question_row.group else None
    section = group.section if group and group.section else None
    paper = section.paper if section and section.paper else None
    bank = paper.bank if paper and paper.bank else None

    preview_text = ""
    if question_row:
        preview_text = (
            (question_row.stem_text or "").strip()
            or (question_row.content_text or "").strip()
            or extract_preview_text(question_row.stem_html or question_row.content_html, limit=240)
        )
    if not preview_text:
        preview_text = extract_wrong_question_preview_from_record(question_record)

    return {
        "exam_paper_id": int(paper.exam_paper_id) if paper else int(submission.exam_paper_id or 0),
        "paper_code": (paper.paper_code if paper else submission.paper_code) or None,
        "paper_title": (
            (paper.paper_name if paper else None)
            or (paper.paper_code if paper else None)
            or submission.title
            or f"Paper {submission.exam_paper_id}"
        ),
        "exam_section_id": int(section.exam_section_id) if section else 0,
        "section_title": (section.section_title if section else None) or None,
        "exam_group_id": int(group.exam_group_id) if group else 0,
        "group_title": (group.group_title if group else None) or None,
        "question_id": (
            (question_row.question_code if question_row else None)
            or str(question_record.get("id") or "").strip()
            or f"Q-{question_record.get('exam_question_id')}"
        ),
        "question_no": (
            str(question_row.question_no) if question_row and question_row.question_no is not None else question_record.get("question_no")
        ),
        "question_type": (
            (str(question_row.raw_type or "").strip() if question_row else "")
            or str(question_record.get("question_type") or "").strip()
            or None
        ),
        "stat_type": (
            (str(question_row.stat_type or "").strip() if question_row else "")
            or str(question_record.get("stat_type") or "").strip()
            or None
        ),
        "exam_category": (
            (bank.exam_type if bank and bank.exam_type else None)
            or submission.exam_category
            or "IELTS"
        ),
        "exam_content": (
            beta_exam_content_from_subject_type(paper.subject_type) if paper and paper.subject_type else submission.exam_content
        ),
        "preview_text": preview_text or "",
    }


def extract_submission_question_state_id_map(
    db: Session,
    *,
    submission_id: int,
) -> dict[tuple[str, str], int]:
    rows = (
        db.query(MockExamSubmissionQuestionState)
        .filter(MockExamSubmissionQuestionState.mockexam_submission_id == submission_id)
        .filter(MockExamSubmissionQuestionState.delete_flag == "1")
        .all()
    )
    state_map: dict[tuple[str, str], int] = {}
    for row in rows:
        if row.exam_question_id is not None:
            state_map[("exam_question_id", str(int(row.exam_question_id)))] = int(
                row.mockexam_submission_question_state_id
            )
        question_id = str(row.question_id or "").strip()
        if question_id:
            state_map[("question_id", question_id)] = int(row.mockexam_submission_question_state_id)
    return state_map


def count_active_beta_wrong_questions(
    db: Session,
    *,
    user_id: str,
    exam_question_ids: list[int],
) -> int:
    normalized_ids = sorted({int(value) for value in exam_question_ids if value is not None})
    if not normalized_ids:
        return 0
    return int(
        db.query(func.count(MockExamWrongQuestionStat.mockexam_wrong_question_stat_id))
        .filter(MockExamWrongQuestionStat.user_id == user_id)
        .filter(MockExamWrongQuestionStat.exam_question_id.in_(normalized_ids))
        .filter(MockExamWrongQuestionStat.delete_flag == "1")
        .filter(MockExamWrongQuestionStat.status == 1)
        .scalar()
        or 0
    )


def sync_beta_mockexam_wrong_question_stats(
    db: Session,
    *,
    user_id: str,
    submission: MockExamSubmission,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
) -> dict[str, Any]:
    question_records = flatten_questions_for_record_ops(payload)
    session_exam_question_ids = sorted(
        {
            int(record["exam_question_id"])
            for record in question_records
            if record.get("exam_question_id") is not None
        }
    )
    if not question_records or not session_exam_question_ids:
        return {
            "all_correct": True,
            "wrong_count": 0,
            "active_wrong_question_count": 0,
            "exam_question_ids": [],
        }

    snapshot_map = load_exam_question_snapshot_map(db, exam_question_ids=session_exam_question_ids)
    existing_rows = (
        db.query(MockExamWrongQuestionStat)
        .filter(MockExamWrongQuestionStat.user_id == user_id)
        .filter(MockExamWrongQuestionStat.exam_question_id.in_(session_exam_question_ids))
        .all()
    )
    existing_map = {int(row.exam_question_id): row for row in existing_rows}
    state_id_map = extract_submission_question_state_id_map(
        db,
        submission_id=int(submission.mockexam_submission_id),
    )

    wrong_question_ids: list[int] = []
    for question_record in question_records:
        exam_question_id = question_record.get("exam_question_id")
        if exam_question_id is None:
            continue

        question = question_record["question"]
        question_id = str(question_record.get("id") or "").strip()
        evaluation = evaluate_single_question(question, answers_map.get(question_id))
        if not evaluation.get("gradable") or not evaluation.get("answered") or evaluation.get("correct"):
            continue

        normalized_exam_question_id = int(exam_question_id)
        wrong_question_ids.append(normalized_exam_question_id)

        stat_row = existing_map.get(normalized_exam_question_id)
        if stat_row is None:
            stat_row = MockExamWrongQuestionStat(
                user_id=user_id,
                exam_question_id=normalized_exam_question_id,
                delete_flag="1",
            )
            db.add(stat_row)
            existing_map[normalized_exam_question_id] = stat_row

        snapshot = build_wrong_question_snapshot(
            question_record=question_record,
            question_row=snapshot_map.get(normalized_exam_question_id),
            submission=submission,
        )
        for key, value in snapshot.items():
            setattr(stat_row, key, value)

        state_id = state_id_map.get(("exam_question_id", str(normalized_exam_question_id))) or state_id_map.get(
            ("question_id", question_id)
        )
        stat_row.wrong_count = int(stat_row.wrong_count or 0) + 1
        stat_row.latest_wrong_submission_id = int(submission.mockexam_submission_id)
        stat_row.latest_wrong_question_state_id = state_id
        stat_row.latest_wrong_time = submission.create_time or current_utc_time()
        stat_row.latest_user_answer_json = copy.deepcopy(answers_map.get(question_id))
        stat_row.latest_marked = 1 if bool(marked_map.get(question_id)) else 0
        stat_row.status = 1
        stat_row.delete_flag = "1"

    active_wrong_question_count = count_active_beta_wrong_questions(
        db,
        user_id=user_id,
        exam_question_ids=session_exam_question_ids,
    )
    return {
        "all_correct": len(wrong_question_ids) == 0,
        "wrong_count": len(wrong_question_ids),
        "active_wrong_question_count": active_wrong_question_count,
        "exam_question_ids": session_exam_question_ids,
    }


def resolve_beta_mockexam_wrong_questions(
    db: Session,
    *,
    user_id: str,
    exam_question_ids: list[int],
) -> int:
    normalized_ids = sorted({int(value) for value in exam_question_ids if value is not None})
    if not normalized_ids:
        return 0

    rows = (
        db.query(MockExamWrongQuestionStat)
        .filter(MockExamWrongQuestionStat.user_id == user_id)
        .filter(MockExamWrongQuestionStat.exam_question_id.in_(normalized_ids))
        .filter(MockExamWrongQuestionStat.delete_flag == "1")
        .filter(MockExamWrongQuestionStat.status == 1)
        .all()
    )
    removed_count = 0
    for row in rows:
        row.delete_flag = "0"
        row.status = 0
        removed_count += 1
    db.commit()
    return removed_count


def toggle_beta_mockexam_question_favorite(
    db: Session,
    *,
    user_id: str,
    exam_question_id: int,
    is_favorite: bool,
    source_kind: str | None = None,
    paper_set_id: int | None = None,
) -> MockExamQuestionFavorite | None:
    question = (
        db.query(ExamQuestion)
        .options(selectinload(ExamQuestion.group).selectinload(ExamGroup.section).selectinload(ExamSection.paper))
        .filter(ExamQuestion.exam_question_id == exam_question_id)
        .filter(ExamQuestion.delete_flag == "1")
        .filter(ExamQuestion.status == 1)
        .first()
    )
    if not question or not question.group or not question.group.section or not question.group.section.paper:
        raise LookupError("未找到该题目")

    group = question.group
    section = group.section
    paper = section.paper
    favorite = (
        db.query(MockExamQuestionFavorite)
        .filter(MockExamQuestionFavorite.user_id == user_id)
        .filter(MockExamQuestionFavorite.exam_question_id == exam_question_id)
        .first()
    )
    if not is_favorite:
        if favorite:
            favorite.delete_flag = "0"
            db.commit()
        return None

    normalized_source_kind = normalize_favorite_source_kind(source_kind)
    payload = {
        "exam_paper_id": paper.exam_paper_id,
        "paper_code": paper.paper_code,
        "paper_title": paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}",
        "exam_section_id": section.exam_section_id,
        "exam_group_id": group.exam_group_id,
        "question_id": question.question_code or question.question_id or f"Q-{question.exam_question_id}",
        "question_no": str(question.question_no) if question.question_no is not None else None,
        "question_type": str(question.raw_type or "").strip() or None,
        "stat_type": str(question.stat_type or group.stat_type or "").strip() or None,
        "exam_category": paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS",
        "exam_content": beta_exam_content_from_subject_type(paper.subject_type),
    }
    if normalized_source_kind == PAPER_SET_SOURCE_KIND and paper_set_id:
        paper_set = get_mockexam_paper_set(
            db,
            mockexam_paper_set_id=int(paper_set_id),
            require_enabled=False,
        )
        payload["paper_code"] = build_paper_set_paper_code(paper_set.mockexam_paper_set_id)
        payload["paper_title"] = paper_set.set_name
        payload["exam_content"] = paper_set.exam_content or payload["exam_content"]
        payload["exam_category"] = paper_set.exam_category or payload["exam_category"]

    if favorite:
        for key, value in payload.items():
            setattr(favorite, key, value)
        favorite.delete_flag = "1"
        favorite.create_time = current_utc_time()
    else:
        favorite = MockExamQuestionFavorite(
            user_id=user_id,
            exam_question_id=question.exam_question_id,
            delete_flag="1",
            **payload,
        )
        db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


def list_beta_mockexam_favorites(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int | None = None,
    limit: int = 50,
) -> list[dict[str, Any]]:
    query = (
        db.query(MockExamQuestionFavorite)
        .filter(MockExamQuestionFavorite.user_id == user_id)
        .filter(MockExamQuestionFavorite.delete_flag == "1")
    )
    if exam_paper_id is not None:
        query = query.filter(MockExamQuestionFavorite.exam_paper_id == exam_paper_id)
    rows = (
        query.order_by(
            MockExamQuestionFavorite.create_time.desc(),
            MockExamQuestionFavorite.mockexam_question_favorite_id.desc(),
        )
        .limit(clamp_int(limit, default=50, minimum=1, maximum=200))
        .all()
    )
    preview_map = extract_exam_question_preview_map(
        db,
        [row.exam_question_id for row in rows if row.exam_question_id is not None],
    )
    section_ids = [int(row.exam_section_id) for row in rows if row.exam_section_id is not None]
    group_ids = [int(row.exam_group_id) for row in rows if row.exam_group_id is not None]
    section_map = {
        row.exam_section_id: row
        for row in (
            db.query(ExamSection)
            .filter(ExamSection.exam_section_id.in_(section_ids))
            .all()
            if section_ids
            else []
        )
    }
    group_map = {
        row.exam_group_id: row
        for row in (
            db.query(ExamGroup)
            .filter(ExamGroup.exam_group_id.in_(group_ids))
            .all()
            if group_ids
            else []
        )
    }
    return [
        {
            "exam_question_id": row.exam_question_id,
            "exam_paper_id": row.exam_paper_id,
            "paper_code": row.paper_code,
            "paper_title": row.paper_title,
            "source_kind": extract_record_source_meta(
                paper_code=row.paper_code,
                payload_json=None,
            )["source_kind"],
            "paper_set_id": extract_record_source_meta(
                paper_code=row.paper_code,
                payload_json=None,
            )["paper_set_id"],
            "exam_content": row.exam_content,
            "exam_section_id": row.exam_section_id,
            "section_title": (
                section_map.get(int(row.exam_section_id)).section_title
                if row.exam_section_id is not None and section_map.get(int(row.exam_section_id))
                else None
            ),
            "exam_group_id": row.exam_group_id,
            "group_title": (
                group_map.get(int(row.exam_group_id)).group_title
                if row.exam_group_id is not None and group_map.get(int(row.exam_group_id))
                else None
            ),
            "question_id": row.question_id,
            "question_no": row.question_no,
            "question_type": row.question_type,
            "stat_type": row.stat_type,
            "preview_text": preview_map.get(row.exam_question_id, ""),
            "create_time": row.create_time,
        }
        for row in rows
    ]


def toggle_beta_mockexam_entity_paper_favorite(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int,
    is_favorite: bool,
) -> MockExamEntityFavorite | None:
    paper = get_mockexam_beta_paper(db, exam_paper_id=exam_paper_id)
    favorite = (
        db.query(MockExamEntityFavorite)
        .filter(MockExamEntityFavorite.user_id == user_id)
        .filter(MockExamEntityFavorite.target_type == "paper")
        .filter(MockExamEntityFavorite.target_id == exam_paper_id)
        .first()
    )
    if not is_favorite:
        if favorite:
            favorite.delete_flag = "0"
            favorite.status = 0
            db.commit()
        return None

    payload = {
        "exam_paper_id": paper.exam_paper_id,
        "paper_set_id": None,
        "paper_code": paper.paper_code,
        "title": paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}",
        "exam_category": paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS",
        "exam_content": beta_exam_content_from_subject_type(paper.subject_type),
        "status": 1,
    }
    if favorite:
        for key, value in payload.items():
            setattr(favorite, key, value)
        favorite.delete_flag = "1"
        favorite.create_time = current_utc_time()
    else:
        favorite = MockExamEntityFavorite(
            user_id=user_id,
            target_type="paper",
            target_id=paper.exam_paper_id,
            delete_flag="1",
            **payload,
        )
        db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


def toggle_beta_mockexam_entity_paper_set_favorite(
    db: Session,
    *,
    user_id: str,
    mockexam_paper_set_id: int,
    is_favorite: bool,
) -> MockExamEntityFavorite | None:
    paper_set = get_mockexam_paper_set(
        db,
        mockexam_paper_set_id=mockexam_paper_set_id,
        require_enabled=False,
    )
    favorite = (
        db.query(MockExamEntityFavorite)
        .filter(MockExamEntityFavorite.user_id == user_id)
        .filter(MockExamEntityFavorite.target_type == PAPER_SET_SOURCE_KIND)
        .filter(MockExamEntityFavorite.target_id == mockexam_paper_set_id)
        .first()
    )
    if not is_favorite:
        if favorite:
            favorite.delete_flag = "0"
            favorite.status = 0
            db.commit()
        return None

    payload = {
        "exam_paper_id": None,
        "paper_set_id": paper_set.mockexam_paper_set_id,
        "paper_code": build_paper_set_paper_code(paper_set.mockexam_paper_set_id),
        "title": paper_set.set_name,
        "exam_category": paper_set.exam_category or "IELTS",
        "exam_content": paper_set.exam_content,
        "status": 1,
    }
    if favorite:
        for key, value in payload.items():
            setattr(favorite, key, value)
        favorite.delete_flag = "1"
        favorite.create_time = current_utc_time()
    else:
        favorite = MockExamEntityFavorite(
            user_id=user_id,
            target_type=PAPER_SET_SOURCE_KIND,
            target_id=paper_set.mockexam_paper_set_id,
            delete_flag="1",
            **payload,
        )
        db.add(favorite)
    db.commit()
    db.refresh(favorite)
    return favorite


def list_beta_mockexam_entity_favorites(
    db: Session,
    *,
    user_id: str,
    limit: int = 200,
) -> list[dict[str, Any]]:
    rows = (
        db.query(MockExamEntityFavorite)
        .filter(MockExamEntityFavorite.user_id == user_id)
        .filter(MockExamEntityFavorite.delete_flag == "1")
        .filter(MockExamEntityFavorite.status == 1)
        .order_by(
            MockExamEntityFavorite.create_time.desc(),
            MockExamEntityFavorite.mockexam_entity_favorite_id.desc(),
        )
        .limit(clamp_int(limit, default=200, minimum=1, maximum=200))
        .all()
    )
    return [
        {
            "target_type": row.target_type,
            "target_id": int(row.target_id),
            "exam_paper_id": int(row.exam_paper_id) if row.exam_paper_id is not None else None,
            "paper_set_id": int(row.paper_set_id) if row.paper_set_id is not None else None,
            "paper_code": row.paper_code,
            "title": row.title,
            "exam_category": row.exam_category,
            "exam_content": row.exam_content,
            "create_time": row.create_time,
        }
        for row in rows
    ]


def list_beta_mockexam_wrong_questions(db: Session, *, user_id: str, limit: int = 50) -> dict[str, Any]:
    all_rows = (
        db.query(MockExamWrongQuestionStat)
        .filter(MockExamWrongQuestionStat.user_id == user_id)
        .filter(MockExamWrongQuestionStat.delete_flag == "1")
        .filter(MockExamWrongQuestionStat.status == 1)
        .order_by(
            MockExamWrongQuestionStat.latest_wrong_time.desc(),
            MockExamWrongQuestionStat.update_time.desc(),
            MockExamWrongQuestionStat.mockexam_wrong_question_stat_id.desc(),
        )
        .all()
    )

    total_questions = len(all_rows)
    total_wrong_count = sum(int(row.wrong_count or 0) for row in all_rows)
    type_counter: dict[str, int] = {}
    for row in all_rows:
        key = str(row.stat_type or row.question_type or "").strip()
        if key:
            type_counter[key] = type_counter.get(key, 0) + 1
    most_common_type = None
    if type_counter:
        most_common_type = sorted(type_counter.items(), key=lambda item: (-item[1], item[0]))[0][0]

    limited_rows = all_rows[: clamp_int(limit, default=50, minimum=1, maximum=200)]
    group_map: dict[str, dict[str, Any]] = {}
    for row in limited_rows:
        group_key = f"{int(row.exam_paper_id or 0)}:{int(row.exam_group_id or 0)}"
        item = {
            "exam_question_id": int(row.exam_question_id) if row.exam_question_id is not None else None,
            "exam_paper_id": int(row.exam_paper_id or 0),
            "paper_code": row.paper_code,
            "paper_title": row.paper_title,
            "exam_section_id": int(row.exam_section_id) if row.exam_section_id is not None else None,
            "section_title": row.section_title,
            "exam_group_id": int(row.exam_group_id) if row.exam_group_id is not None else None,
            "group_title": row.group_title,
            "exam_content": row.exam_content,
            "question_id": row.question_id,
            "question_no": row.question_no,
            "question_type": row.question_type,
            "stat_type": row.stat_type,
            "preview_text": row.preview_text or "",
            "wrong_count": int(row.wrong_count or 0),
            "last_wrong_time": row.latest_wrong_time,
        }
        if group_key not in group_map:
            group_map[group_key] = {
                "exam_paper_id": int(row.exam_paper_id or 0),
                "paper_code": row.paper_code,
                "paper_title": row.paper_title,
                "exam_section_id": int(row.exam_section_id) if row.exam_section_id is not None else None,
                "section_title": row.section_title,
                "exam_group_id": int(row.exam_group_id) if row.exam_group_id is not None else None,
                "group_title": row.group_title,
                "exam_content": row.exam_content,
                "wrong_question_count": 0,
                "total_wrong_count": 0,
                "latest_wrong_time": row.latest_wrong_time,
                "questions": [],
            }
        group_entry = group_map[group_key]
        group_entry["questions"].append(item)
        group_entry["wrong_question_count"] += 1
        group_entry["total_wrong_count"] += int(row.wrong_count or 0)
        if row.latest_wrong_time and (
            group_entry["latest_wrong_time"] is None or row.latest_wrong_time > group_entry["latest_wrong_time"]
        ):
            group_entry["latest_wrong_time"] = row.latest_wrong_time

    groups = []
    for group_entry in group_map.values():
        group_entry["questions"] = sorted(
            group_entry["questions"],
            key=lambda item: extract_sortable_question_number(item.get("question_no")),
        )
        groups.append(group_entry)

    groups.sort(
        key=lambda item: (
            item.get("latest_wrong_time") or datetime.min,
            item.get("total_wrong_count") or 0,
        ),
        reverse=True,
    )

    total_groups = len(group_map)
    average_wrong_count = round(total_wrong_count / total_groups, 1) if total_groups else 0
    return {
        "summary": {
            "total_questions": total_questions,
            "total_wrong_count": total_wrong_count,
            "average_wrong_count": average_wrong_count,
            "most_common_type": most_common_type,
        },
        "groups": groups,
    }


def get_beta_mockexam_question_detail(
    db: Session,
    *,
    user_id: str,
    exam_question_id: int,
) -> dict[str, Any]:
    question = (
        db.query(ExamQuestion)
        .options(
            selectinload(ExamQuestion.answers),
            selectinload(ExamQuestion.blanks),
            selectinload(ExamQuestion.assets),
            selectinload(ExamQuestion.group).selectinload(ExamGroup.options),
            selectinload(ExamQuestion.group).selectinload(ExamGroup.assets),
            selectinload(ExamQuestion.group).selectinload(ExamGroup.section).selectinload(ExamSection.assets),
        )
        .filter(ExamQuestion.exam_question_id == exam_question_id)
        .filter(ExamQuestion.delete_flag == "1")
        .filter(ExamQuestion.status == 1)
        .first()
    )
    if not question or not question.group or not question.group.section or not question.group.section.paper:
        raise LookupError("未找到该题目详情")

    group = question.group
    section = group.section
    paper = section.paper
    question_payload = serialize_mockexam_beta_question(
        section,
        group,
        question,
        serialize_mockexam_beta_group_options(group.options),
    )
    if question_payload is None:
        raise LookupError("题目详情暂不可用")

    section_payload = serialize_mockexam_beta_passage(section) or {}
    section_payload = {
        key: value
        for key, value in section_payload.items()
        if key in {"id", "title", "instructions", "content", "audio"}
    }
    group_payload = serialize_mockexam_beta_group(section, group)
    group_payload = {
        key: value
        for key, value in group_payload.items()
        if key in {"id", "title", "instructions", "type", "options"}
    }
    is_favorite = (
        db.query(MockExamQuestionFavorite)
        .filter(MockExamQuestionFavorite.user_id == user_id)
        .filter(MockExamQuestionFavorite.exam_question_id == exam_question_id)
        .filter(MockExamQuestionFavorite.delete_flag == "1")
        .first()
        is not None
    )
    wrong_stat_row = (
        db.query(MockExamWrongQuestionStat)
        .filter(MockExamWrongQuestionStat.user_id == user_id)
        .filter(MockExamWrongQuestionStat.exam_question_id == exam_question_id)
        .filter(MockExamWrongQuestionStat.delete_flag == "1")
        .filter(MockExamWrongQuestionStat.status == 1)
        .first()
    )
    wrong_count = int(wrong_stat_row.wrong_count or 0) if wrong_stat_row else 0

    return {
        "exam_question_id": question.exam_question_id,
        "exam_paper_id": paper.exam_paper_id,
        "paper_code": paper.paper_code,
        "paper_name": paper.paper_name,
        "exam_category": paper.bank.exam_type if paper.bank and paper.bank.exam_type else "IELTS",
        "exam_content": beta_exam_content_from_subject_type(paper.subject_type),
        "is_favorite": is_favorite,
        "wrong_count": int(wrong_count),
        "material_html": build_question_detail_material_html(section_payload, group_payload),
        "section": section_payload,
        "group": group_payload,
        "question": question_payload,
    }
