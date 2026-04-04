from __future__ import annotations

import json

from sqlalchemy.orm import Session
from sqlalchemy.orm import load_only

from backend.models.question_bank_models import QuestionBank

QUESTION_BANK_CONTENT_OPTIONS = {
    "IELTS": ["Listening", "Reading", "Speaking", "Writing"],
    "SAT": ["Reading", "Writing", "Math"],
    "ACT": ["English", "Math", "Reading", "Science", "Writing"],
    "TOEFL": ["Listening", "Reading", "Speaking", "Writing"],
}


def create_question_bank(
    db: Session,
    *,
    title: str | None,
    upload_file_name: str,
    exam_category: str,
    exam_content: str,
    file_bytes: bytes,
) -> QuestionBank:
    normalized_title = (title or "").strip()
    normalized_file_name = normalized_title or (upload_file_name or "").strip()
    normalized_exam_category = (exam_category or "").strip().upper()
    normalized_exam_content = (exam_content or "").strip()

    if not normalized_file_name:
        raise ValueError("标题和文件名不能同时为空")
    if len(normalized_file_name) > 200:
        raise ValueError("标题长度不能超过 200 个字符")
    if normalized_exam_category not in QUESTION_BANK_CONTENT_OPTIONS:
        raise ValueError("考试类别不合法")
    if normalized_exam_content not in QUESTION_BANK_CONTENT_OPTIONS[normalized_exam_category]:
        raise ValueError("考试内容与考试类别不匹配")
    if not file_bytes:
        raise ValueError("请上传 JSON 文件")

    try:
        json.loads(file_bytes.decode("utf-8-sig"))
    except UnicodeDecodeError as exc:
        raise ValueError("JSON 文件必须使用 UTF-8 编码") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"上传文件不是合法 JSON：第 {exc.lineno} 行第 {exc.colno} 列附近有错误") from exc

    row = QuestionBank(
        file_name=normalized_file_name,
        exam_category=normalized_exam_category,
        exam_content=normalized_exam_content,
        json_text=file_bytes,
        status="1",
        delete_flag="1",
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return row


def list_question_banks(
    db: Session,
    *,
    page: int = 1,
    page_size: int = 10,
) -> tuple[list[QuestionBank], int]:
    safe_page = max(1, page)
    safe_page_size = max(1, min(page_size, 100))

    query = db.query(QuestionBank).filter(QuestionBank.delete_flag == "1")
    total = query.count()
    rows = (
        query.options(
            load_only(
                QuestionBank.id,
                QuestionBank.file_name,
                QuestionBank.exam_category,
                QuestionBank.exam_content,
                QuestionBank.status,
                QuestionBank.create_time,
                QuestionBank.update_time,
            )
        )
        .order_by(QuestionBank.id.desc())
        .offset((safe_page - 1) * safe_page_size)
        .limit(safe_page_size)
        .all()
    )
    return rows, total
