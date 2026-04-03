from __future__ import annotations

import json
import re
from typing import Any

from sqlalchemy.orm import Session
from sqlalchemy.orm import load_only

from backend.models.question_bank_models import QuestionBank


MOCKEXAM_CONTENT_OPTIONS: dict[str, list[str]] = {
    "IELTS": ["Listening", "Reading", "Speaking", "Writing"],
    "SAT": ["Reading", "Writing", "Math"],
    "ACT": ["English", "Math", "Reading", "Science", "Writing"],
    "TOEFL": ["Listening", "Reading", "Speaking", "Writing"],
}

SUPPORTED_MOCKEXAM_CATEGORIES = ("IELTS",)


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

    normalized_exam_category = (exam_category or "").strip().upper()
    normalized_exam_content = (exam_content or "").strip()

    if normalized_exam_category:
        query = query.filter(QuestionBank.exam_category == normalized_exam_category)
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

    for item in questions:
        question_id = item["id"]
        question = item["question"]
        answer_value = answers_map.get(question_id)
        result = evaluate_single_question(question, answer_value)

        if result["answered"]:
            answered_count += 1
        total_questions += 1
        if result["gradable"]:
            gradable_questions += 1
            if result["correct"]:
                correct_count += 1

        details.append(
            {
                "question_id": question_id,
                "type": result["type"],
                "answered": result["answered"],
                "correct": result["correct"],
                "gradable": result["gradable"],
                "stem": normalize_text_value(question.get("stem") or question.get("content"))[:180],
            }
        )

    score_percent = None
    if gradable_questions > 0:
        score_percent = round(correct_count * 100.0 / gradable_questions, 2)

    return {
        "answered_count": answered_count,
        "total_questions": total_questions,
        "gradable_questions": gradable_questions,
        "correct_count": correct_count,
        "score_percent": score_percent,
        "details": details,
    }
