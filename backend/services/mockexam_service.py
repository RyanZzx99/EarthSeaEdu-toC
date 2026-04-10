from __future__ import annotations

import copy
import json
import re
from typing import Any

from sqlalchemy import func
from sqlalchemy.orm import Session
from sqlalchemy.orm import load_only
from sqlalchemy.orm import selectinload

from backend.models.exam_set_models import ExamSet
from backend.models.exam_set_models import ExamSetPart
from backend.models.ielts_exam_models import ExamAsset
from backend.models.ielts_exam_models import ExamBank
from backend.models.ielts_exam_models import ExamGroup
from backend.models.ielts_exam_models import ExamGroupOption
from backend.models.ielts_exam_models import ExamPaper
from backend.models.ielts_exam_models import ExamQuestion
from backend.models.ielts_exam_models import ExamQuestionAnswer
from backend.models.ielts_exam_models import ExamQuestionBlank
from backend.models.ielts_exam_models import ExamSection
from backend.models.mockexam_record_models import MockExamSubmission
from backend.models.question_bank_models import QuestionBank


MOCKEXAM_CONTENT_OPTIONS: dict[str, list[str]] = {
    "IELTS": ["Listening", "Reading", "Speaking", "Writing"],
    "SAT": ["Reading", "Writing", "Math"],
    "ACT": ["English", "Math", "Reading", "Science", "Writing"],
    "TOEFL": ["Listening", "Reading", "Speaking", "Writing"],
}

SUPPORTED_MOCKEXAM_CATEGORIES = tuple(MOCKEXAM_CONTENT_OPTIONS.keys())

MOCKEXAM_BETA_CONTENT_OPTIONS: dict[str, list[str]] = {
    "IELTS": ["Listening", "Reading"],
}

SUPPORTED_MOCKEXAM_BETA_CATEGORIES = tuple(MOCKEXAM_BETA_CONTENT_OPTIONS.keys())
MOCKEXAM_BETA_SUBJECT_TYPE_MAP = {
    "Listening": "listening",
    "Reading": "reading",
}
MOCKEXAM_BETA_EXAM_CONTENT_MAP = {
    "listening": "Listening",
    "reading": "Reading",
}
MOCKEXAM_SUBMISSION_SOURCE_TYPES = {
    "question-bank",
    "exam-set",
    "paper-beta",
    "quick-practice",
}


def clamp_int(value: Any, *, default: int, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = default
    return max(minimum, min(parsed, maximum))


def dedupe_int_list(values: list[int]) -> list[int]:
    result: list[int] = []
    seen: set[int] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result


def dedupe_str_list(values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        normalized = (value or "").strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return result


def normalize_exam_category(value: str | None, *, allow_empty: bool = False) -> str:
    normalized = (value or "").strip().upper()
    if not normalized and allow_empty:
        return ""
    if normalized not in MOCKEXAM_CONTENT_OPTIONS:
        raise ValueError("考试类别不合法")
    return normalized


def normalize_exam_content(value: str | None, *, exam_category: str, allow_empty: bool = False) -> str:
    normalized = (value or "").strip()
    if not normalized and allow_empty:
        return ""
    if normalized not in MOCKEXAM_CONTENT_OPTIONS.get(exam_category, []):
        raise ValueError("考试内容与考试类别不匹配")
    return normalized


def normalize_exam_contents(values: list[str] | None, *, exam_category: str) -> list[str]:
    result: list[str] = []
    for value in dedupe_str_list(values or []):
        result.append(normalize_exam_content(value, exam_category=exam_category))
    return result


def normalize_mockexam_submission_source_type(value: str | None, *, allow_empty: bool = False) -> str:
    normalized = (value or "").strip().lower()
    if not normalized and allow_empty:
        return ""
    if normalized not in MOCKEXAM_SUBMISSION_SOURCE_TYPES:
        raise ValueError("Unsupported mock exam submission source type")
    return normalized


def get_mockexam_options() -> dict[str, Any]:
    return {
        "exam_category_options": list(MOCKEXAM_CONTENT_OPTIONS.keys()),
        "content_options_map": MOCKEXAM_CONTENT_OPTIONS,
        "supported_categories": list(SUPPORTED_MOCKEXAM_CATEGORIES),
    }


def list_mockexam_question_banks(
    db: Session,
    *,
    exam_category: str | None = None,
    exam_content: str | None = None,
) -> list[QuestionBank]:
    query = (
        db.query(QuestionBank)
        .filter(QuestionBank.delete_flag == "1")
        .filter(QuestionBank.status == "1")
    )

    normalized_exam_category = normalize_exam_category(exam_category, allow_empty=True)
    normalized_exam_content = ""
    if normalized_exam_category:
        query = query.filter(QuestionBank.exam_category == normalized_exam_category)
        normalized_exam_content = normalize_exam_content(
            exam_content,
            exam_category=normalized_exam_category,
            allow_empty=True,
        )
    elif exam_content:
        raise ValueError("筛选考试内容前请先选择考试类别")

    if normalized_exam_content:
        query = query.filter(QuestionBank.exam_content == normalized_exam_content)

    return (
        query.options(
            load_only(
                QuestionBank.id,
                QuestionBank.file_name,
                QuestionBank.exam_category,
                QuestionBank.exam_content,
                QuestionBank.create_time,
            )
        )
        .order_by(QuestionBank.id.desc())
        .all()
    )


def get_mockexam_question_bank(db: Session, *, question_bank_id: int) -> QuestionBank:
    row = (
        db.query(QuestionBank)
        .filter(QuestionBank.id == question_bank_id)
        .filter(QuestionBank.delete_flag == "1")
        .filter(QuestionBank.status == "1")
        .first()
    )
    if not row:
        raise LookupError("题库不存在或已停用")
    return row


def parse_mockexam_question_bank_payload(row: QuestionBank) -> Any:
    raw_bytes = row.json_text or b""
    if not raw_bytes:
        raise ValueError("题库内容为空")

    try:
        return json.loads(raw_bytes.decode("utf-8-sig"))
    except UnicodeDecodeError as exc:
        raise ValueError("题库 JSON 编码不是 UTF-8") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"题库 JSON 解析失败，第 {exc.lineno} 行第 {exc.colno} 列附近有错误") from exc


def load_mockexam_payload(db: Session, *, question_bank_id: int) -> tuple[QuestionBank, Any]:
    row = get_mockexam_question_bank(db, question_bank_id=question_bank_id)
    payload = parse_mockexam_question_bank_payload(row)
    return row, payload


def get_mockexam_beta_options() -> dict[str, Any]:
    return {
        "exam_category_options": list(MOCKEXAM_BETA_CONTENT_OPTIONS.keys()),
        "content_options_map": MOCKEXAM_BETA_CONTENT_OPTIONS,
        "supported_categories": list(SUPPORTED_MOCKEXAM_BETA_CATEGORIES),
    }


def normalize_mockexam_beta_exam_category(value: str | None, *, allow_empty: bool = False) -> str:
    normalized = (value or "").strip().upper()
    if not normalized and allow_empty:
        return ""
    if normalized not in MOCKEXAM_BETA_CONTENT_OPTIONS:
        raise ValueError("测试版模拟考试当前仅支持 IELTS")
    return normalized


def normalize_mockexam_beta_exam_content(value: str | None, *, allow_empty: bool = False) -> str:
    normalized = (value or "").strip().title()
    if not normalized and allow_empty:
        return ""
    if normalized not in MOCKEXAM_BETA_CONTENT_OPTIONS["IELTS"]:
        raise ValueError("测试版模拟考试当前仅支持 IELTS Reading / Listening")
    return normalized


def beta_exam_content_to_subject_type(exam_content: str | None) -> str:
    normalized_exam_content = normalize_mockexam_beta_exam_content(exam_content)
    return MOCKEXAM_BETA_SUBJECT_TYPE_MAP[normalized_exam_content]


def beta_exam_content_from_subject_type(subject_type: str | None) -> str:
    normalized_subject_type = str(subject_type or "").strip().lower()
    return MOCKEXAM_BETA_EXAM_CONTENT_MAP.get(normalized_subject_type, "Reading")


def list_mockexam_beta_papers(
    db: Session,
    *,
    exam_category: str | None = None,
    exam_content: str | None = None,
) -> list[ExamPaper]:
    normalized_exam_category = normalize_mockexam_beta_exam_category(exam_category, allow_empty=True)
    normalized_exam_content = ""
    if normalized_exam_category:
        normalized_exam_content = normalize_mockexam_beta_exam_content(exam_content, allow_empty=True)
    elif exam_content:
        raise ValueError("筛选考试内容前请先选择考试类别")

    query = (
        db.query(ExamPaper)
        .join(ExamPaper.bank)
        .filter(ExamPaper.delete_flag == "1")
        .filter(ExamPaper.status == 1)
        .filter(ExamBank.delete_flag == "1")
        .filter(ExamBank.status == 1)
        .filter(ExamBank.exam_type == "IELTS")
    )
    if normalized_exam_content:
        query = query.filter(func.lower(ExamPaper.subject_type) == beta_exam_content_to_subject_type(normalized_exam_content))

    return (
        query.options(
            load_only(
                ExamPaper.exam_paper_id,
                ExamPaper.paper_code,
                ExamPaper.paper_name,
                ExamPaper.module_name,
                ExamPaper.subject_type,
                ExamPaper.book_code,
                ExamPaper.test_no,
                ExamPaper.create_time,
            ),
            selectinload(ExamPaper.bank).load_only(
                ExamBank.exam_bank_id,
                ExamBank.bank_name,
                ExamBank.exam_type,
            ),
        )
        .order_by(
            ExamPaper.subject_type.asc(),
            ExamPaper.book_code.asc(),
            ExamPaper.test_no.asc(),
            ExamPaper.exam_paper_id.asc(),
        )
        .all()
    )


def get_mockexam_beta_paper(db: Session, *, exam_paper_id: int) -> ExamPaper:
    row = (
        db.query(ExamPaper)
        .join(ExamPaper.bank)
        .options(
            selectinload(ExamPaper.bank).load_only(
                ExamBank.exam_bank_id,
                ExamBank.bank_name,
                ExamBank.exam_type,
            ),
            selectinload(ExamPaper.sections)
            .selectinload(ExamSection.groups)
            .selectinload(ExamGroup.questions)
            .selectinload(ExamQuestion.answers),
            selectinload(ExamPaper.sections)
            .selectinload(ExamSection.groups)
            .selectinload(ExamGroup.questions)
            .selectinload(ExamQuestion.blanks),
            selectinload(ExamPaper.sections)
            .selectinload(ExamSection.groups)
            .selectinload(ExamGroup.questions)
            .selectinload(ExamQuestion.assets),
            selectinload(ExamPaper.sections)
            .selectinload(ExamSection.groups)
            .selectinload(ExamGroup.options),
            selectinload(ExamPaper.sections)
            .selectinload(ExamSection.groups)
            .selectinload(ExamGroup.assets),
            selectinload(ExamPaper.sections).selectinload(ExamSection.assets),
        )
        .filter(ExamPaper.exam_paper_id == exam_paper_id)
        .filter(ExamPaper.delete_flag == "1")
        .filter(ExamPaper.status == 1)
        .filter(ExamBank.delete_flag == "1")
        .filter(ExamBank.status == 1)
        .filter(ExamBank.exam_type == "IELTS")
        .first()
    )
    if not row:
        raise LookupError("测试版试卷不存在或已停用")
    return row


def load_mockexam_beta_paper_payload(db: Session, *, exam_paper_id: int) -> tuple[ExamPaper, dict[str, Any]]:
    row = get_mockexam_beta_paper(db, exam_paper_id=exam_paper_id)
    return row, build_mockexam_beta_payload(row)


def evaluate_mockexam_beta_paper_submission(
    db: Session,
    *,
    exam_paper_id: int,
    answers_map: dict[str, Any],
) -> dict[str, Any]:
    row, payload = load_mockexam_beta_paper_payload(db, exam_paper_id=exam_paper_id)
    if (row.bank.exam_type if row.bank else "IELTS") not in SUPPORTED_MOCKEXAM_BETA_CATEGORIES:
        raise ValueError("当前测试版模考尚未开放该考试类别")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    return evaluate_quiz_payload(payload, safe_answers_map)


def build_mockexam_beta_payload(paper: ExamPaper) -> dict[str, Any]:
    passages: list[dict[str, Any]] = []
    for section in get_active_sorted_records(paper.sections):
        groups = [serialize_mockexam_beta_group(section, group) for group in get_active_sorted_records(section.groups)]
        groups = [group for group in groups if group["questions"]]
        if not groups:
            continue

        section_assets = get_active_sorted_records(section.assets)
        passages.append(
            {
                "id": section.section_id or f"S{section.section_no or section.sort_order}",
                "title": section.section_title or f"Section {section.section_no or section.sort_order}",
                "instructions": section.instructions_text or "",
                "content": rewrite_html_asset_urls(section.content_html, section_assets),
                "audio": resolve_primary_asset_url(section.primary_audio_asset_id, section_assets, asset_type="audio"),
                "groups": groups,
            }
        )

    return {
        "module": paper.module_name or beta_exam_content_from_subject_type(paper.subject_type),
        "passages": passages,
    }


def serialize_mockexam_beta_group(section: ExamSection, group: ExamGroup) -> dict[str, Any]:
    group_assets = get_active_sorted_records(group.assets)
    group_options = serialize_mockexam_beta_group_options(group.options)
    questions = [
        question_payload
        for question in get_active_sorted_records(group.questions)
        if (question_payload := serialize_mockexam_beta_question(section, group, question, group_options)) is not None
    ]
    return {
        "id": group.group_id or f"{section.section_id or 'section'}-G{group.sort_order}",
        "title": group.group_title or "",
        "instructions": rewrite_html_asset_urls(group.instructions_html, group_assets),
        "type": questions[0]["type"] if questions else "",
        "options": group_options or None,
        "questions": questions,
    }


def serialize_mockexam_beta_question(
    section: ExamSection,
    group: ExamGroup,
    question: ExamQuestion,
    group_options: list[dict[str, str]],
) -> dict[str, Any] | None:
    answer_row = get_active_answer(question.answers)
    answer_values = extract_answer_values(answer_row)
    answer_text = extract_answer_text(answer_row)
    question_type = infer_mockexam_beta_question_type(group, question, group_options, answer_values)

    combined_assets = [
        *get_active_sorted_records(section.assets),
        *get_active_sorted_records(group.assets),
        *get_active_sorted_records(question.assets),
    ]
    prompt_html, prompt_with_blank_html = resolve_mockexam_beta_question_prompt_html(group, question, combined_assets)

    payload: dict[str, Any] = {
        "id": question.question_code or question.question_id or f"Q-{question.exam_question_id}",
        "type": question_type,
        "stem": prompt_with_blank_html or prompt_html or build_question_fallback_html(question),
        "content": "",
    }

    if question_type == "tfng":
        payload["answer"] = normalize_tfng_value((answer_values[0] if answer_values else answer_text))
        return payload

    if question_type == "multiple":
        payload["answer"] = answer_values
        if group_options:
            payload["options"] = group_options
        return payload

    if question_type == "single":
        payload["answer"] = answer_values[0] if answer_values else answer_text
        if group_options:
            payload["options"] = group_options
        return payload

    if question_type == "cloze_inline":
        blanks = serialize_mockexam_beta_blanks(question, answer_text, answer_values)
        if not blanks:
            return None
        payload["stem"] = ""
        payload["content"] = prompt_html or prompt_with_blank_html or build_blank_question_content(question)
        payload["blanks"] = blanks
        return payload

    payload["answer"] = answer_text
    return payload


def serialize_mockexam_beta_group_options(options: list[ExamGroupOption] | None) -> list[dict[str, str]]:
    serialized: list[dict[str, str]] = []
    for option in get_active_sorted_records(options):
        content = (option.option_text or "").strip() or (option.option_html or "").strip()
        if not content:
            continue
        serialized.append(
            {
                "label": str(option.option_key or "").strip() or chr(ord("A") + len(serialized)),
                "content": content,
            }
        )
    return serialized


def serialize_mockexam_beta_blanks(
    question: ExamQuestion,
    answer_text: str,
    answer_values: list[str],
) -> list[dict[str, Any]]:
    blank_answer = answer_text or " | ".join(answer_values)
    return [
        {
            "id": blank.blank_id,
            "answer": blank_answer,
        }
        for blank in get_active_sorted_records(question.blanks)
        if blank.blank_id
    ]


def infer_mockexam_beta_question_type(
    group: ExamGroup,
    question: ExamQuestion,
    group_options: list[dict[str, str]],
    answer_values: list[str],
) -> str:
    raw_type = str(question.raw_type or group.raw_type or "").strip().lower()
    stat_type = str(question.stat_type or group.stat_type or "").strip().lower()
    option_labels = {str(item.get("label") or "").strip().upper() for item in group_options if item.get("label")}
    answers_are_option_labels = bool(answer_values) and all(value.upper() in option_labels for value in answer_values)

    if raw_type == "tfng" or stat_type == "true_false_not_given":
        return "tfng"
    if group_options and answers_are_option_labels:
        return "multiple" if len(answer_values) > 1 else "single"
    if get_active_sorted_records(question.blanks):
        return "cloze_inline"
    if group_options:
        return "multiple" if len(answer_values) > 1 else "single"
    return "blank"


def resolve_mockexam_beta_question_prompt_html(
    group: ExamGroup,
    question: ExamQuestion,
    assets: list[ExamAsset],
) -> tuple[str, str]:
    if question.source_blank_id:
        full_content_html = group.content_html or question.content_html or question.stem_html or ""
        fragment_html = extract_precise_blank_fragment_html(full_content_html, question.source_blank_id)
        if not fragment_html:
            fragment_html = question.stem_html or question.content_html or ""
        if fragment_html:
            fragment_html = rewrite_html_asset_urls(fragment_html, assets)
            normalized_fragment_html = normalize_fragment_blank_placeholders(
                fragment_html,
                question.source_blank_id,
                keep_active_blank=True,
            )
            return (
                normalized_fragment_html,
                normalize_fragment_blank_placeholders(
                    fragment_html,
                    question.source_blank_id,
                    keep_active_blank=False,
                ),
            )

    prompt_html = rewrite_html_asset_urls(question.stem_html or question.content_html, assets)
    return prompt_html, prompt_html


def extract_precise_blank_fragment_html(full_content_html: str | None, blank_id: str | None) -> str:
    html = str(full_content_html or "").strip()
    normalized_blank_id = str(blank_id or "").strip()
    if not html or not normalized_blank_id:
        return ""

    safe_blank_id = re.escape(normalized_blank_id)
    for tag in ("p", "li", "tr", "td", "th"):
        pattern = re.compile(
            rf"<{tag}\b[^>]*>(?:(?!</{tag}>).)*\[\[{safe_blank_id}\]\](?:(?!</{tag}>).)*</{tag}>",
            re.I | re.S,
        )
        match = pattern.search(html)
        if match:
            return match.group(0)
    return ""


def replace_specific_blank_placeholder(content_html: str | None, blank_id: str | None) -> str:
    html = str(content_html or "")
    normalized_blank_id = str(blank_id or "").strip()
    if not normalized_blank_id:
        return html
    return re.sub(rf"\[\[\s*{re.escape(normalized_blank_id)}\s*\]\]", "_____", html)


def normalize_fragment_blank_placeholders(
    content_html: str | None,
    active_blank_id: str | None,
    *,
    keep_active_blank: bool,
) -> str:
    html = str(content_html or "")
    normalized_active_blank_id = str(active_blank_id or "").strip()
    if not html:
        return ""

    def replace_match(match: re.Match[str]) -> str:
        blank_id = str(match.group(1) or match.group(2) or "").strip()
        if normalized_active_blank_id and blank_id == normalized_active_blank_id:
            return match.group(0) if keep_active_blank else "_____"
        return "_____"

    return re.sub(r"\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]", replace_match, html)


def rewrite_html_asset_urls(content_html: str | None, assets: list[ExamAsset] | None) -> str:
    html = str(content_html or "").strip()
    if not html:
        return ""
    for asset in get_active_sorted_records(assets):
        source_path = str(asset.source_path or "").strip()
        asset_url = str(asset.asset_url or "").strip()
        if source_path and asset_url:
            html = html.replace(source_path, asset_url)
    return html


def resolve_primary_asset_url(
    primary_asset_id: int | None,
    assets: list[ExamAsset] | None,
    *,
    asset_type: str | None = None,
) -> str:
    for asset in get_active_sorted_records(assets):
        if primary_asset_id is not None and asset.exam_asset_id == primary_asset_id:
            if asset_type and str(asset.asset_type or "").strip().lower() != asset_type:
                continue
            asset_url = str(asset.asset_url or "").strip()
            if asset_url:
                return asset_url

    for asset in get_active_sorted_records(assets):
        if asset_type and str(asset.asset_type or "").strip().lower() != asset_type:
            continue
        asset_url = str(asset.asset_url or "").strip()
        if asset_url:
            return asset_url
    return ""


def get_active_answer(answers: list[ExamQuestionAnswer] | None) -> ExamQuestionAnswer | None:
    for answer in get_active_sorted_records(answers):
        return answer
    return None


def extract_answer_values(answer: ExamQuestionAnswer | None) -> list[str]:
    if answer is None:
        return []
    if isinstance(answer.answer_json, list):
        return [str(item).strip() for item in answer.answer_json if str(item).strip()]
    raw_text = str(answer.answer_raw or "").strip()
    return [raw_text] if raw_text else []


def extract_answer_text(answer: ExamQuestionAnswer | None) -> str:
    if answer is None:
        return ""
    return str(answer.answer_raw or "").strip()


def build_question_fallback_html(question: ExamQuestion) -> str:
    if question.question_no is not None:
        return f"<p>Question {question.question_no}</p>"
    return f"<p>{question.question_id or question.question_code or 'Question'}</p>"


def build_blank_question_content(question: ExamQuestion) -> str:
    blank = next(iter(get_active_sorted_records(question.blanks)), None)
    if question.question_no is not None and blank is not None:
        return f"<p>Question {question.question_no}: [[{blank.blank_id}]]</p>"
    if blank is not None:
        return f"<p>[[{blank.blank_id}]]</p>"
    return build_question_fallback_html(question)


def get_active_sorted_records(records: list[Any] | None) -> list[Any]:
    active_records = [
        record
        for record in (records or [])
        if getattr(record, "delete_flag", "1") == "1" and getattr(record, "status", 1) == 1
    ]
    return sorted(
        active_records,
        key=lambda item: (
            normalize_record_sort_value(getattr(item, "sort_order", 0)),
            normalize_record_sort_value(getattr(item, "question_no", 0)),
            normalize_record_sort_value(getattr(item, "exam_section_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_group_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_question_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_group_option_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_asset_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_question_blank_id", 0)),
            normalize_record_sort_value(getattr(item, "exam_question_answer_id", 0)),
        ),
    )


def normalize_record_sort_value(value: Any) -> int:
    if value is None:
        return 0
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def normalize_exam_set_rule(raw_rule: Any, *, exam_category: str) -> dict[str, Any]:
    source = raw_rule if isinstance(raw_rule, dict) else {}
    exam_contents = normalize_exam_contents(source.get("exam_contents"), exam_category=exam_category)
    per_content = clamp_int(source.get("per_content"), default=1, minimum=0, maximum=20)
    extra_count = clamp_int(source.get("extra_count"), default=0, minimum=0, maximum=20)
    total_count = clamp_int(source.get("total_count"), default=3, minimum=1, maximum=50)

    return {
        "exam_contents": exam_contents,
        "per_content": per_content,
        "extra_count": extra_count,
        "total_count": total_count,
    }


def get_exam_set_question_banks(exam_set: ExamSet) -> list[QuestionBank]:
    active_parts = [
        part
        for part in sorted(exam_set.parts or [], key=lambda item: (item.sort_order, item.paper_set_parts_id))
        if part.delete_flag == "1"
        and part.question_bank is not None
        and part.question_bank.delete_flag == "1"
        and part.question_bank.status == "1"
    ]
    return [part.question_bank for part in active_parts]


def build_exam_set_summary(exam_set: ExamSet) -> tuple[str, list[str]]:
    if exam_set.mode == "manual":
        question_banks = get_exam_set_question_banks(exam_set)
        question_bank_names = [row.file_name for row in question_banks]
        contents = dedupe_str_list([row.exam_content for row in question_banks])
        if contents:
            return " / ".join(contents), question_bank_names
        if question_bank_names:
            return "已关联题库", question_bank_names
        return "暂无有效题库", []

    rule = normalize_exam_set_rule(exam_set.rule_json, exam_category=exam_set.exam_category or "")
    exam_contents = rule["exam_contents"]
    if exam_contents and rule["extra_count"] > 0:
        return f"{' / '.join(exam_contents)} + 随机补齐", []
    if exam_contents:
        return " / ".join(exam_contents), []
    return "随机内容", []


def serialize_exam_set_item(exam_set: ExamSet) -> dict[str, Any]:
    content_summary, question_bank_names = build_exam_set_summary(exam_set)
    return {
        "exam_sets_id": exam_set.exam_sets_id,
        "name": exam_set.name,
        "mode": exam_set.mode,
        "exam_category": exam_set.exam_category,
        "part_count": exam_set.part_count,
        "status": exam_set.status,
        "content_summary": content_summary,
        "question_bank_names": question_bank_names,
        "create_time": exam_set.create_time,
        "update_time": exam_set.update_time,
    }


def exam_set_matches_content_filter(exam_set: ExamSet, *, exam_content: str) -> bool:
    if not exam_content:
        return True
    if exam_set.mode == "manual":
        contents = {row.exam_content for row in get_exam_set_question_banks(exam_set)}
        return exam_content in contents

    rule = normalize_exam_set_rule(exam_set.rule_json, exam_category=exam_set.exam_category or "")
    if not rule["exam_contents"]:
        return True
    return exam_content in set(rule["exam_contents"])


def list_mockexam_exam_sets(
    db: Session,
    *,
    exam_category: str | None = None,
    exam_content: str | None = None,
    status: str | None = None,
) -> list[ExamSet]:
    query = (
        db.query(ExamSet)
        .options(selectinload(ExamSet.parts).selectinload(ExamSetPart.question_bank))
        .filter(ExamSet.delete_flag == "1")
    )

    normalized_exam_category = normalize_exam_category(exam_category, allow_empty=True)
    normalized_exam_content = ""
    if normalized_exam_category:
        query = query.filter(ExamSet.exam_category == normalized_exam_category)
        normalized_exam_content = normalize_exam_content(
            exam_content,
            exam_category=normalized_exam_category,
            allow_empty=True,
        )
    elif exam_content:
        raise ValueError("筛选考试内容前请先选择考试类别")

    normalized_status = (status or "").strip()
    if normalized_status in {"0", "1"}:
        query = query.filter(ExamSet.status == normalized_status)

    rows = query.order_by(ExamSet.create_time.desc(), ExamSet.exam_sets_id.desc()).all()
    if not normalized_exam_content:
        return rows

    return [row for row in rows if exam_set_matches_content_filter(row, exam_content=normalized_exam_content)]


def get_mockexam_exam_set(
    db: Session,
    *,
    exam_sets_id: int,
    require_enabled: bool,
) -> ExamSet:
    query = (
        db.query(ExamSet)
        .options(selectinload(ExamSet.parts).selectinload(ExamSetPart.question_bank))
        .filter(ExamSet.exam_sets_id == exam_sets_id)
        .filter(ExamSet.delete_flag == "1")
    )
    if require_enabled:
        query = query.filter(ExamSet.status == "1")

    row = query.first()
    if not row:
        raise LookupError("组合试卷不存在或已停用")
    return row


def compose_payload_from_question_banks(question_banks: list[QuestionBank], title: str) -> Any:
    if not question_banks:
        raise ValueError("未选择任何题库")

    payloads = [parse_mockexam_question_bank_payload(row) for row in question_banks]
    if len(payloads) == 1:
        return payloads[0]

    merged = {
        "module": title or "Mixed Practice",
        "passages": [],
    }

    for bank_index, payload in enumerate(payloads, start=1):
        normalized = normalize_payload_for_merge(payload, f"Part {bank_index}")
        if not normalized:
            continue

        passages = normalized.get("passages") or []
        for passage_index, passage in enumerate(passages, start=1):
            if not isinstance(passage, dict):
                continue
            cloned = copy.deepcopy(passage)
            base_id = cloned.get("id") or f"P{passage_index}"
            cloned["id"] = f"S{bank_index}-{base_id}"
            if not cloned.get("title"):
                cloned["title"] = f"Part {bank_index} Passage {passage_index}"
            merged["passages"].append(cloned)

    if not merged["passages"]:
        raise ValueError("无法合并已选择题库，当前 JSON 结构不支持组卷")

    return merged


def pick_random_question_banks(
    db: Session,
    *,
    limit: int,
    exam_category: str | None,
    exam_content: str | None,
    exclude_ids: set[int] | None,
) -> list[QuestionBank]:
    safe_limit = clamp_int(limit, default=0, minimum=0, maximum=100)
    if safe_limit <= 0:
        return []

    query = (
        db.query(QuestionBank)
        .filter(QuestionBank.delete_flag == "1")
        .filter(QuestionBank.status == "1")
    )

    if exam_category:
        query = query.filter(QuestionBank.exam_category == exam_category)
    if exam_content:
        query = query.filter(QuestionBank.exam_content == exam_content)
    if exclude_ids:
        query = query.filter(~QuestionBank.id.in_(list(exclude_ids)))

    return query.order_by(func.rand()).limit(safe_limit).all()


def select_question_banks_for_exam_set(db: Session, *, exam_set: ExamSet) -> list[QuestionBank]:
    if exam_set.mode == "manual":
        question_banks = get_exam_set_question_banks(exam_set)
        if not question_banks:
            raise ValueError("当前组合试卷没有可用题库")
        return question_banks

    exam_category = normalize_exam_category(exam_set.exam_category)
    rule = normalize_exam_set_rule(exam_set.rule_json, exam_category=exam_category)
    selected: list[QuestionBank] = []
    selected_ids: set[int] = set()

    for exam_content in rule["exam_contents"]:
        rows = pick_random_question_banks(
            db,
            limit=rule["per_content"],
            exam_category=exam_category,
            exam_content=exam_content,
            exclude_ids=selected_ids,
        )
        for row in rows:
            if row.id in selected_ids:
                continue
            selected.append(row)
            selected_ids.add(row.id)

    if rule["extra_count"] > 0:
        rows = pick_random_question_banks(
            db,
            limit=rule["extra_count"],
            exam_category=exam_category,
            exam_content=None,
            exclude_ids=selected_ids,
        )
        for row in rows:
            if row.id in selected_ids:
                continue
            selected.append(row)
            selected_ids.add(row.id)

    if not selected:
        selected = pick_random_question_banks(
            db,
            limit=rule["total_count"],
            exam_category=exam_category,
            exam_content=None,
            exclude_ids=None,
        )

    if not selected:
        raise ValueError("当前随机规则下没有可用题库")

    return selected


def build_exam_set_payload(db: Session, *, exam_set: ExamSet) -> Any:
    question_banks = select_question_banks_for_exam_set(db, exam_set=exam_set)
    return compose_payload_from_question_banks(question_banks, exam_set.name)


def load_mockexam_exam_set_payload(db: Session, *, exam_sets_id: int) -> tuple[ExamSet, Any]:
    exam_set = get_mockexam_exam_set(db, exam_sets_id=exam_sets_id, require_enabled=True)
    payload = build_exam_set_payload(db, exam_set=exam_set)
    return exam_set, payload


def create_mockexam_exam_set(
    db: Session,
    *,
    name: str,
    mode: str,
    exam_category: str,
    question_bank_ids: list[int] | None,
    exam_contents: list[str] | None,
    per_content: int,
    extra_count: int,
    total_count: int,
) -> ExamSet:
    normalized_name = (name or "").strip()
    normalized_mode = (mode or "").strip().lower()
    normalized_exam_category = normalize_exam_category(exam_category)

    if not normalized_name:
        raise ValueError("请输入组合试卷名称")
    if len(normalized_name) > 200:
        raise ValueError("组合试卷名称长度不能超过 200 个字符")
    if normalized_mode not in {"manual", "random"}:
        raise ValueError("组卷模式不合法")

    exam_set = ExamSet(
        name=normalized_name,
        mode=normalized_mode,
        exam_category=normalized_exam_category,
        rule_json=None,
        part_count=1,
        status="1",
        delete_flag="1",
    )
    db.add(exam_set)
    db.flush()

    if normalized_mode == "manual":
        requested_ids = dedupe_int_list(
            [question_bank_id for question_bank_id in (question_bank_ids or []) if question_bank_id > 0]
        )
        if not requested_ids:
            raise ValueError("手动组卷至少选择一个题库")

        rows = (
            db.query(QuestionBank)
            .filter(QuestionBank.id.in_(requested_ids))
            .filter(QuestionBank.delete_flag == "1")
            .filter(QuestionBank.status == "1")
            .all()
        )
        row_map = {row.id: row for row in rows}

        ordered_rows: list[QuestionBank] = []
        for question_bank_id in requested_ids:
            row = row_map.get(question_bank_id)
            if row is None:
                raise ValueError(f"题库 #{question_bank_id} 不存在或已停用")
            if row.exam_category != normalized_exam_category:
                raise ValueError("手动组卷的题库考试类别必须一致")
            ordered_rows.append(row)

        for index, row in enumerate(ordered_rows):
            db.add(
                ExamSetPart(
                    exam_sets_id=exam_set.exam_sets_id,
                    question_bank_id=row.id,
                    sort_order=index,
                    delete_flag="1",
                )
            )

        exam_set.part_count = len(ordered_rows)
    else:
        normalized_exam_contents = normalize_exam_contents(exam_contents, exam_category=normalized_exam_category)
        normalized_per_content = clamp_int(per_content, default=1, minimum=0, maximum=20)
        normalized_extra_count = clamp_int(extra_count, default=0, minimum=0, maximum=20)
        normalized_total_count = clamp_int(total_count, default=3, minimum=1, maximum=50)
        computed_part_count = normalized_per_content * len(normalized_exam_contents) + normalized_extra_count
        if computed_part_count <= 0:
            computed_part_count = normalized_total_count

        exam_set.rule_json = {
            "exam_contents": normalized_exam_contents,
            "per_content": normalized_per_content,
            "extra_count": normalized_extra_count,
            "total_count": normalized_total_count,
        }
        exam_set.part_count = computed_part_count

    db.commit()
    return get_mockexam_exam_set(db, exam_sets_id=exam_set.exam_sets_id, require_enabled=False)


def update_mockexam_exam_set_status(
    db: Session,
    *,
    exam_sets_id: int,
    status: str,
) -> ExamSet:
    exam_set = get_mockexam_exam_set(db, exam_sets_id=exam_sets_id, require_enabled=False)
    normalized_status = (status or "").strip()
    if normalized_status not in {"0", "1"}:
        raise ValueError("状态值不合法")

    exam_set.status = normalized_status
    db.commit()
    return get_mockexam_exam_set(db, exam_sets_id=exam_sets_id, require_enabled=False)


def delete_mockexam_exam_set(db: Session, *, exam_sets_id: int) -> None:
    exam_set = get_mockexam_exam_set(db, exam_sets_id=exam_sets_id, require_enabled=False)
    exam_set.delete_flag = "0"
    for part in exam_set.parts or []:
        part.delete_flag = "0"
    db.commit()


def build_quick_practice_payload(
    db: Session,
    *,
    exam_category: str,
    exam_contents: list[str] | None,
    count: int,
) -> dict[str, Any]:
    normalized_exam_category = normalize_exam_category(exam_category)
    normalized_exam_contents = normalize_exam_contents(
        exam_contents,
        exam_category=normalized_exam_category,
    )
    safe_count = clamp_int(count, default=3, minimum=1, maximum=20)

    selected: list[QuestionBank] = []
    selected_ids: set[int] = set()

    if normalized_exam_contents:
        each = max(1, safe_count // max(1, len(normalized_exam_contents)))
        for exam_content in normalized_exam_contents:
            rows = pick_random_question_banks(
                db,
                limit=each,
                exam_category=normalized_exam_category,
                exam_content=exam_content,
                exclude_ids=selected_ids,
            )
            for row in rows:
                if row.id in selected_ids:
                    continue
                selected.append(row)
                selected_ids.add(row.id)

    remain = max(0, safe_count - len(selected))
    if remain > 0:
        rows = pick_random_question_banks(
            db,
            limit=remain,
            exam_category=normalized_exam_category,
            exam_content=None,
            exclude_ids=selected_ids,
        )
        for row in rows:
            if row.id in selected_ids:
                continue
            selected.append(row)
            selected_ids.add(row.id)

    if not selected:
        raise LookupError("当前筛选条件下没有可用题库")

    payload = compose_payload_from_question_banks(selected, "随堂小练")
    return {
        "status": "ok",
        "exam_category": normalized_exam_category,
        "label": "随堂小练",
        "payload": payload,
        "picked_items": [
            {
                "id": row.id,
                "file_name": row.file_name,
                "exam_content": row.exam_content,
            }
            for row in selected
        ],
    }


def build_mockexam_submission_summary(submission: MockExamSubmission) -> dict[str, Any]:
    return {
        "submission_id": submission.mockexam_submission_id,
        "title": submission.title,
        "create_time": submission.create_time,
    }


def create_mockexam_submission_record(
    db: Session,
    *,
    user_id: str,
    source_type: str,
    source_id: str | None,
    exam_category: str,
    exam_content: str | None,
    title: str,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    result: dict[str, Any],
) -> MockExamSubmission:
    submission = MockExamSubmission(
        user_id=user_id,
        source_type=normalize_mockexam_submission_source_type(source_type),
        source_id=str(source_id).strip() if source_id is not None and str(source_id).strip() else None,
        exam_category=(exam_category or "").strip() or "IELTS",
        exam_content=(exam_content or "").strip() or None,
        title=(title or "").strip() or "Mock Exam",
        payload_json=copy.deepcopy(payload),
        answers_json=copy.deepcopy(answers_map or {}),
        marked_json=copy.deepcopy(marked_map or {}),
        result_json=copy.deepcopy(result or {}),
        status=1,
        delete_flag="1",
    )
    db.add(submission)
    db.commit()
    db.refresh(submission)
    return submission


def serialize_mockexam_submission_item(submission: MockExamSubmission) -> dict[str, Any]:
    result = submission.result_json if isinstance(submission.result_json, dict) else {}
    return {
        "submission_id": submission.mockexam_submission_id,
        "source_type": submission.source_type,
        "source_id": submission.source_id,
        "exam_category": submission.exam_category,
        "exam_content": submission.exam_content,
        "title": submission.title,
        "score_percent": result.get("score_percent"),
        "total_questions": int(result.get("total_questions") or 0),
        "correct_count": int(result.get("correct_count") or 0),
        "wrong_count": int(result.get("wrong_count") or 0),
        "gradable_questions": int(result.get("gradable_questions") or 0),
        "create_time": submission.create_time,
    }


def serialize_mockexam_submission_detail(submission: MockExamSubmission) -> dict[str, Any]:
    return {
        "submission_id": submission.mockexam_submission_id,
        "source_type": submission.source_type,
        "source_id": submission.source_id,
        "exam_category": submission.exam_category,
        "exam_content": submission.exam_content,
        "title": submission.title,
        "create_time": submission.create_time,
        "payload": copy.deepcopy(submission.payload_json),
        "answers": copy.deepcopy(submission.answers_json or {}),
        "marked": copy.deepcopy(submission.marked_json or {}),
        "result": copy.deepcopy(submission.result_json or {}),
    }


def list_mockexam_submissions(
    db: Session,
    *,
    user_id: str,
    source_type: str | None = None,
    exam_category: str | None = None,
    exam_content: str | None = None,
    limit: int = 20,
) -> list[MockExamSubmission]:
    query = (
        db.query(MockExamSubmission)
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
    )

    normalized_source_type = normalize_mockexam_submission_source_type(source_type, allow_empty=True)
    if normalized_source_type:
        query = query.filter(MockExamSubmission.source_type == normalized_source_type)

    normalized_exam_category = normalize_exam_category(exam_category, allow_empty=True)
    normalized_exam_content = ""
    if normalized_exam_category:
        query = query.filter(MockExamSubmission.exam_category == normalized_exam_category)
        normalized_exam_content = normalize_exam_content(
            exam_content,
            exam_category=normalized_exam_category,
            allow_empty=True,
        )
    elif exam_content:
        normalized_exam_content = (exam_content or "").strip()

    if normalized_exam_content:
        query = query.filter(MockExamSubmission.exam_content == normalized_exam_content)

    safe_limit = clamp_int(limit, default=20, minimum=1, maximum=100)
    return (
        query.order_by(
            MockExamSubmission.create_time.desc(),
            MockExamSubmission.mockexam_submission_id.desc(),
        )
        .limit(safe_limit)
        .all()
    )


def get_mockexam_submission(
    db: Session,
    *,
    user_id: str,
    submission_id: int,
) -> MockExamSubmission:
    row = (
        db.query(MockExamSubmission)
        .filter(MockExamSubmission.mockexam_submission_id == submission_id)
        .filter(MockExamSubmission.user_id == user_id)
        .filter(MockExamSubmission.delete_flag == "1")
        .filter(MockExamSubmission.status == 1)
        .first()
    )
    if not row:
        raise LookupError("Mock exam submission not found")
    return row


def submit_mockexam_question_bank(
    db: Session,
    *,
    user_id: str,
    question_bank_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
) -> tuple[dict[str, Any], MockExamSubmission]:
    row, payload = load_mockexam_payload(db, question_bank_id=question_bank_id)
    if row.exam_category not in SUPPORTED_MOCKEXAM_CATEGORIES:
        raise ValueError("Current exam category is not enabled for mock exam scoring yet")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_mockexam_submission_record(
        db,
        user_id=user_id,
        source_type="question-bank",
        source_id=str(row.id),
        exam_category=row.exam_category,
        exam_content=row.exam_content,
        title=row.file_name,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
    )
    return result, submission


def submit_mockexam_beta_paper(
    db: Session,
    *,
    user_id: str,
    exam_paper_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
) -> tuple[dict[str, Any], MockExamSubmission]:
    row, payload = load_mockexam_beta_paper_payload(db, exam_paper_id=exam_paper_id)
    exam_category = row.bank.exam_type if row.bank and row.bank.exam_type else "IELTS"
    if exam_category not in SUPPORTED_MOCKEXAM_BETA_CATEGORIES:
        raise ValueError("Current beta exam category is not enabled")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_mockexam_submission_record(
        db,
        user_id=user_id,
        source_type="paper-beta",
        source_id=str(row.exam_paper_id),
        exam_category=exam_category,
        exam_content=beta_exam_content_from_subject_type(row.subject_type),
        title=row.paper_name or row.paper_code or f"Paper {row.exam_paper_id}",
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
    )
    return result, submission


def submit_mockexam_exam_set(
    db: Session,
    *,
    user_id: str,
    exam_sets_id: int,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
) -> tuple[dict[str, Any], MockExamSubmission]:
    exam_set, payload = load_mockexam_exam_set_payload(db, exam_sets_id=exam_sets_id)
    if exam_set.exam_category not in SUPPORTED_MOCKEXAM_CATEGORIES:
        raise ValueError("Current exam category is not enabled for mock exam scoring yet")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_mockexam_submission_record(
        db,
        user_id=user_id,
        source_type="exam-set",
        source_id=str(exam_set.exam_sets_id),
        exam_category=exam_set.exam_category or "IELTS",
        exam_content=None,
        title=exam_set.name,
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
    )
    return result, submission


def submit_inline_mockexam_payload(
    db: Session,
    *,
    user_id: str,
    payload: Any,
    answers_map: dict[str, Any],
    marked_map: dict[str, Any],
    source_type: str | None,
    source_id: str | None,
    source_title: str | None,
    exam_category: str | None,
    exam_content: str | None,
) -> tuple[dict[str, Any], MockExamSubmission]:
    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    safe_marked_map = marked_map if isinstance(marked_map, dict) else {}
    normalized_source_type = normalize_mockexam_submission_source_type(source_type or "quick-practice")
    normalized_exam_category = normalize_exam_category(exam_category or "IELTS")
    normalized_exam_content = normalize_exam_content(
        exam_content,
        exam_category=normalized_exam_category,
        allow_empty=True,
    )
    result = evaluate_quiz_payload(payload, safe_answers_map)
    submission = create_mockexam_submission_record(
        db,
        user_id=user_id,
        source_type=normalized_source_type,
        source_id=source_id,
        exam_category=normalized_exam_category,
        exam_content=normalized_exam_content or None,
        title=(source_title or "").strip() or "Quick Practice",
        payload=payload,
        answers_map=safe_answers_map,
        marked_map=safe_marked_map,
        result=result,
    )
    return result, submission


def evaluate_mockexam_submission(
    db: Session,
    *,
    question_bank_id: int,
    answers_map: dict[str, Any],
) -> dict[str, Any]:
    row, payload = load_mockexam_payload(db, question_bank_id=question_bank_id)
    if row.exam_category not in SUPPORTED_MOCKEXAM_CATEGORIES:
        raise ValueError("当前考试类型模考正在等待更新")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    return evaluate_quiz_payload(payload, safe_answers_map)


def evaluate_mockexam_exam_set_submission(
    db: Session,
    *,
    exam_sets_id: int,
    answers_map: dict[str, Any],
) -> dict[str, Any]:
    exam_set, payload = load_mockexam_exam_set_payload(db, exam_sets_id=exam_sets_id)
    if exam_set.exam_category not in SUPPORTED_MOCKEXAM_CATEGORIES:
        raise ValueError("当前考试类型模考正在等待更新")

    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    return evaluate_quiz_payload(payload, safe_answers_map)


def evaluate_inline_mockexam_payload(
    *,
    payload: Any,
    answers_map: dict[str, Any],
) -> dict[str, Any]:
    safe_answers_map = answers_map if isinstance(answers_map, dict) else {}
    return evaluate_quiz_payload(payload, safe_answers_map)


def convert_legacy_to_passages(payload: Any, fallback_module: str) -> dict[str, Any] | None:
    if not isinstance(payload, dict):
        return None

    reserved = {"module", "passages"}
    passages = []
    for section, section_value in payload.items():
        if section in reserved or not isinstance(section_value, dict):
            continue
        for difficulty, questions in section_value.items():
            if not isinstance(questions, list) or not questions:
                continue
            first = questions[0] if isinstance(questions[0], dict) else {}
            passages.append(
                {
                    "id": f"{section}-{difficulty}",
                    "title": f"{section} {difficulty}",
                    "content": first.get("stimulus", ""),
                    "questions": questions,
                }
            )

    if not passages:
        return None

    module = payload.get("module") if isinstance(payload.get("module"), str) else fallback_module
    return {
        "module": module or fallback_module,
        "passages": passages,
    }


def normalize_payload_for_merge(payload: Any, fallback_module: str) -> dict[str, Any] | None:
    if isinstance(payload, dict) and isinstance(payload.get("passages"), list):
        return {
            "module": payload.get("module") or fallback_module,
            "passages": payload.get("passages") or [],
        }

    legacy = convert_legacy_to_passages(payload, fallback_module)
    if legacy:
        return legacy

    if isinstance(payload, list):
        return {
            "module": fallback_module,
            "passages": [
                {
                    "id": f"{fallback_module}-P1",
                    "title": fallback_module,
                    "content": "",
                    "questions": payload,
                }
            ],
        }

    return None


def normalize_text_value(value: Any) -> str:
    if value is None:
        return ""
    text = str(value)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text.lower()


def normalize_label_value(value: Any) -> str:
    return normalize_text_value(value).upper()


def normalize_tfng_value(value: Any) -> str:
    text = normalize_label_value(value)
    if text in {"T", "TRUE"}:
        return "TRUE"
    if text in {"F", "FALSE"}:
        return "FALSE"
    if text in {"NG", "NOT GIVEN", "NOTGIVEN"}:
        return "NOT GIVEN"
    return ""


def parse_options_for_score(raw_options: Any) -> list[dict[str, str]]:
    if raw_options is None:
        return []

    options: list[Any] = []
    if isinstance(raw_options, list):
        options = raw_options
    elif isinstance(raw_options, dict) and isinstance(raw_options.get("options"), list):
        options = raw_options.get("options")
    elif isinstance(raw_options, str):
        text = raw_options.strip()
        if text:
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError:
                parsed = None
            if isinstance(parsed, list):
                options = parsed
            elif isinstance(parsed, dict) and isinstance(parsed.get("options"), list):
                options = parsed.get("options")

    result = []
    for index, item in enumerate(options):
        if isinstance(item, dict):
            label = str(item.get("label") or item.get("value") or "").strip()
            content = item.get("content")
            if content is None:
                content = item.get("text", item.get("label", ""))
        else:
            label = ""
            content = item

        if not label:
            label = chr(ord("A") + index)

        result.append(
            {
                "label": normalize_label_value(label),
                "content_norm": normalize_text_value(content),
                "raw_content": str(content or ""),
            }
        )
    return result


def normalize_multi_answer_values(answer: Any) -> list[str]:
    if answer is None:
        return []
    values = answer if isinstance(answer, list) else re.split(r"[,;|/]+", str(answer))
    normalized = [normalize_label_value(value) for value in values if normalize_label_value(value)]
    deduped = []
    seen = set()
    for item in normalized:
        if item not in seen:
            seen.add(item)
            deduped.append(item)
    return deduped


def split_expected_text_variants(answer: Any) -> list[str]:
    if answer is None:
        return []
    text = str(answer).strip()
    if not text:
        return []
    parts = [part.strip() for part in re.split(r"[|;/]|(?:\s+or\s+)", text, flags=re.IGNORECASE) if part.strip()]
    normalized = [normalize_text_value(part) for part in parts if normalize_text_value(part)]
    deduped = []
    seen = set()
    for item in normalized:
        if item not in seen:
            seen.add(item)
            deduped.append(item)
    return deduped


def compare_text_answer(user_answer: Any, expected_answer: Any) -> bool:
    user_norm = normalize_text_value(user_answer)
    if not user_norm:
        return False
    variants = split_expected_text_variants(expected_answer)
    if not variants:
        return False
    return user_norm in variants


def infer_question_type(question: dict[str, Any]) -> str:
    raw_type = str(question.get("type") or "").strip().lower()
    if raw_type in {"single", "multiple", "tfng", "blank", "essay", "cloze_inline", "matching"}:
        return raw_type
    if raw_type in {"multiplechoice", "single_choice"}:
        return "single"
    if raw_type in {"multi", "multiple_choice"}:
        return "multiple"
    if raw_type in {"truefalse", "true_false", "tf", "not_given"}:
        return "tfng"

    if isinstance(question.get("blanks"), list) and question.get("blanks"):
        return "cloze_inline"
    if isinstance(question.get("questions"), list) and question.get("questions"):
        return "matching"
    if isinstance(question.get("items"), list) and question.get("items"):
        return "matching"

    options = parse_options_for_score(question.get("options"))
    answer_text = str(question.get("answer") or "").strip()
    answer_upper = normalize_label_value(answer_text)
    if options:
        if answer_upper in {"TRUE", "FALSE", "NOT GIVEN"}:
            return "tfng"
        option_labels = {option["label"] for option in options}
        if isinstance(question.get("answer"), list):
            return "multiple"
        if re.search(r"[,;|/]", answer_text) and answer_upper not in option_labels:
            return "multiple"
        return "single"

    if question.get("wordLimit") or question.get("modelAnswer"):
        return "essay"
    return "blank"


def normalize_payload_for_score(payload: Any) -> dict[str, Any]:
    if isinstance(payload, dict) and isinstance(payload.get("passages"), list):
        return payload
    return normalize_payload_for_merge(payload, "Practice") or {"module": "Practice", "passages": []}


def flatten_questions_for_score(payload: Any) -> list[dict[str, Any]]:
    normalized = normalize_payload_for_score(payload)
    questions = []
    sequence = 0
    for passage in normalized.get("passages", []):
        if not isinstance(passage, dict):
            continue
        groups = passage.get("groups")
        source_groups = groups if isinstance(groups, list) and groups else [{"questions": passage.get("questions", [])}]
        for group in source_groups:
            for question in group.get("questions", []):
                if not isinstance(question, dict):
                    continue
                sequence += 1
                question_id = str(question.get("id") or f"AUTO-{sequence}")
                questions.append({"id": question_id, "question": question})
    return questions


def evaluate_single_question(question: dict[str, Any], answer_value: Any) -> dict[str, Any]:
    question_type = infer_question_type(question)
    answered = False
    correct = False
    gradable = True

    if question_type == "matching":
        items = question.get("questions")
        if not isinstance(items, list):
            items = question.get("items")
        items = items if isinstance(items, list) else []
        user_map = answer_value if isinstance(answer_value, dict) else {}
        expected_map = {str(item.get("id")): item.get("answer") for item in items if isinstance(item, dict)}
        answered = bool(expected_map) and all(normalize_text_value(user_map.get(key)) for key in expected_map.keys())
        if expected_map:
            correct = all(
                normalize_label_value(user_map.get(key)) == normalize_label_value(expected_map.get(key))
                for key in expected_map.keys()
            )
        else:
            gradable = False
        return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}

    if question_type == "cloze_inline":
        blanks = question.get("blanks") if isinstance(question.get("blanks"), list) else []
        user_map = answer_value if isinstance(answer_value, dict) else {}
        expected_map = {str(item.get("id")): item.get("answer") for item in blanks if isinstance(item, dict)}
        answered = bool(expected_map) and all(normalize_text_value(user_map.get(key)) for key in expected_map.keys())
        if expected_map:
            correct = all(compare_text_answer(user_map.get(key), expected_map.get(key)) for key in expected_map.keys())
        else:
            gradable = False
        return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}

    if question_type == "multiple":
        expected = set(normalize_multi_answer_values(question.get("answer")))
        user_values = answer_value if isinstance(answer_value, list) else [answer_value] if answer_value is not None else []
        user_set = set(normalize_multi_answer_values(user_values))
        answered = bool(user_set)
        if expected:
            correct = user_set == expected
        else:
            gradable = False
        return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}

    if question_type == "tfng":
        expected = normalize_tfng_value(question.get("answer"))
        user = normalize_tfng_value(answer_value)
        answered = bool(user)
        if expected:
            correct = user == expected
        else:
            gradable = False
        return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}

    if question_type == "single":
        options = parse_options_for_score(question.get("options"))
        answer_text = str(question.get("answer") or "").strip()
        expected_label = ""
        expected_text_norm = normalize_text_value(answer_text)
        labels = {option["label"] for option in options}
        if normalize_label_value(answer_text) in labels:
            expected_label = normalize_label_value(answer_text)
        else:
            for option in options:
                if expected_text_norm and expected_text_norm == option["content_norm"]:
                    expected_label = option["label"]
                    break

        user_label = normalize_label_value(answer_value)
        answered = bool(normalize_text_value(answer_value))
        if expected_label:
            correct = user_label == expected_label
        elif expected_text_norm:
            correct = normalize_text_value(answer_value) == expected_text_norm
        else:
            gradable = False
        return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}

    if question_type == "essay":
        answered = bool(normalize_text_value(answer_value))
        return {"type": question_type, "answered": answered, "correct": False, "gradable": False}

    expected = question.get("answer")
    answered = bool(normalize_text_value(answer_value))
    if normalize_text_value(expected):
        correct = compare_text_answer(answer_value, expected)
    else:
        gradable = False
    return {"type": question_type, "answered": answered, "correct": correct, "gradable": gradable}


def evaluate_quiz_payload(payload: Any, answers_map: dict[str, Any]) -> dict[str, Any]:
    questions = flatten_questions_for_score(payload)
    details = []
    answered_count = 0
    total_questions = 0
    gradable_questions = 0
    correct_count = 0
    wrong_count = 0
    type_stats: dict[str, dict[str, Any]] = {}

    for item in questions:
        question_id = item["id"]
        question = item["question"]
        answer_value = answers_map.get(question_id)
        result = evaluate_single_question(question, answer_value)
        question_type = result["type"]

        if result["answered"]:
            answered_count += 1
        total_questions += 1
        if result["gradable"]:
            gradable_questions += 1
            if result["correct"]:
                correct_count += 1
            elif result["answered"]:
                wrong_count += 1

        stats = type_stats.setdefault(
            question_type,
            {
                "question_type": question_type,
                "total_questions": 0,
                "answered_count": 0,
                "gradable_questions": 0,
                "correct_count": 0,
                "wrong_count": 0,
                "unanswered_count": 0,
            },
        )
        stats["total_questions"] += 1
        if result["answered"]:
            stats["answered_count"] += 1
        else:
            stats["unanswered_count"] += 1
        if result["gradable"]:
            stats["gradable_questions"] += 1
            if result["correct"]:
                stats["correct_count"] += 1
            elif result["answered"]:
                stats["wrong_count"] += 1

        details.append(
            {
                "question_id": question_id,
                "type": question_type,
                "answered": result["answered"],
                "correct": result["correct"],
                "gradable": result["gradable"],
                "stem": normalize_text_value(question.get("stem") or question.get("content"))[:180],
            }
        )

    score_percent = None
    if gradable_questions > 0:
        score_percent = round(correct_count * 100.0 / gradable_questions, 2)
    unanswered_count = max(0, total_questions - answered_count)

    return {
        "answered_count": answered_count,
        "total_questions": total_questions,
        "gradable_questions": gradable_questions,
        "correct_count": correct_count,
        "wrong_count": wrong_count,
        "unanswered_count": unanswered_count,
        "score_percent": score_percent,
        "type_breakdown": list(type_stats.values()),
        "details": details,
    }
