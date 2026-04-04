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
from backend.models.question_bank_models import QuestionBank


MOCKEXAM_CONTENT_OPTIONS: dict[str, list[str]] = {
    "IELTS": ["Listening", "Reading", "Speaking", "Writing"],
    "SAT": ["Reading", "Writing", "Math"],
    "ACT": ["English", "Math", "Reading", "Science", "Writing"],
    "TOEFL": ["Listening", "Reading", "Speaking", "Writing"],
}

SUPPORTED_MOCKEXAM_CATEGORIES = tuple(MOCKEXAM_CONTENT_OPTIONS.keys())


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
