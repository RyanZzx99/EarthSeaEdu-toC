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
from backend.models.mockexam_record_models import MockExamQuestionFavorite
from backend.models.mockexam_record_models import MockExamSubmission
from backend.models.mockexam_record_models import MockExamSubmissionAnswer
from backend.models.mockexam_record_models import MockExamSubmissionQuestionState
from backend.models.mockexam_paper_set_models import MockExamPaperSet
from backend.services.mockexam_paper_set_service import PAPER_SET_SOURCE_KIND
from backend.services.mockexam_paper_set_service import build_paper_set_paper_code
from backend.services.mockexam_paper_set_service import extract_record_source_meta
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


def safe_float_value(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return round(float(value), 2)
    except (TypeError, ValueError):
        return None


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
    progress_id: int | None = None,
) -> tuple[dict[str, Any], MockExamSubmission]:
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
    )

    if progress is not None:
        progress.status = 2
        progress.mockexam_submission_id = submission.mockexam_submission_id
        progress.last_active_time = current_utc_time()

    db.commit()
    db.refresh(submission)
    return result, submission


def submit_beta_mockexam_paper_set(
    db: Session,
    *,
    user_id: str,
    mockexam_paper_set_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    progress_id: int | None = None,
) -> tuple[dict[str, Any], MockExamSubmission]:
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
    )

    if progress is not None:
        progress.status = 2
        progress.mockexam_submission_id = submission.mockexam_submission_id
        progress.last_active_time = current_utc_time()

    db.commit()
    db.refresh(submission)
    return result, submission


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


def toggle_beta_mockexam_question_favorite(
    db: Session,
    *,
    user_id: str,
    exam_question_id: int,
    is_favorite: bool,
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
    return [
        {
            "exam_question_id": row.exam_question_id,
            "exam_paper_id": row.exam_paper_id,
            "paper_code": row.paper_code,
            "paper_title": row.paper_title,
            "exam_content": row.exam_content,
            "question_id": row.question_id,
            "question_no": row.question_no,
            "question_type": row.question_type,
            "stat_type": row.stat_type,
            "preview_text": preview_map.get(row.exam_question_id, ""),
            "create_time": row.create_time,
        }
        for row in rows
    ]


def list_beta_mockexam_wrong_questions(db: Session, *, user_id: str, limit: int = 50) -> list[dict[str, Any]]:
    rows = (
        db.query(MockExamSubmissionQuestionState, MockExamSubmission)
        .join(
            MockExamSubmission,
            MockExamSubmission.mockexam_submission_id == MockExamSubmissionQuestionState.mockexam_submission_id,
        )
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
        .filter(MockExamSubmissionQuestionState.delete_flag == "1")
        .filter(MockExamSubmissionQuestionState.is_answered == 1)
        .filter(MockExamSubmissionQuestionState.is_correct == 0)
        .order_by(
            MockExamSubmission.create_time.desc(),
            MockExamSubmission.mockexam_submission_id.desc(),
            MockExamSubmissionQuestionState.question_index.asc(),
        )
        .all()
    )

    latest_map: dict[str, tuple[MockExamSubmissionQuestionState, MockExamSubmission]] = {}
    count_map: dict[str, int] = {}
    for state_row, submission_row in rows:
        key = str(state_row.exam_question_id or state_row.question_id or "").strip()
        if not key:
            continue
        count_map[key] = count_map.get(key, 0) + 1
        latest_map.setdefault(key, (state_row, submission_row))

    selected = list(latest_map.values())[: clamp_int(limit, default=50, minimum=1, maximum=200)]
    preview_map = extract_exam_question_preview_map(
        db,
        [state_row.exam_question_id for state_row, _ in selected if state_row.exam_question_id is not None],
    )
    return [
        {
            "exam_question_id": state_row.exam_question_id,
            "exam_paper_id": submission_row.exam_paper_id,
            "paper_code": submission_row.paper_code,
            "paper_title": submission_row.title,
            "exam_content": submission_row.exam_content,
            "question_id": state_row.question_id,
            "question_no": state_row.question_no,
            "question_type": state_row.question_type,
            "stat_type": state_row.stat_type,
            "preview_text": preview_map.get(state_row.exam_question_id, ""),
            "wrong_count": count_map.get(str(state_row.exam_question_id or state_row.question_id), 0),
            "last_wrong_time": submission_row.create_time,
        }
        for state_row, submission_row in selected
    ]


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
    wrong_count = (
        db.query(func.count(MockExamSubmissionQuestionState.mockexam_submission_question_state_id))
        .join(
            MockExamSubmission,
            MockExamSubmission.mockexam_submission_id == MockExamSubmissionQuestionState.mockexam_submission_id,
        )
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
        .filter(MockExamSubmissionQuestionState.delete_flag == "1")
        .filter(MockExamSubmissionQuestionState.is_answered == 1)
        .filter(MockExamSubmissionQuestionState.is_correct == 0)
        .filter(MockExamSubmissionQuestionState.exam_question_id == exam_question_id)
        .scalar()
        or 0
    )

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
