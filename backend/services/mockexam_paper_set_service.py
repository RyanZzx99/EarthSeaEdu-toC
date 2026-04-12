from __future__ import annotations

import copy
import re
from typing import Any

from sqlalchemy.orm import Session
from sqlalchemy.orm import selectinload

from backend.models.auth_models import User
from backend.models.ielts_exam_models import ExamBank
from backend.models.ielts_exam_models import ExamPaper
from backend.models.mockexam_paper_set_models import MockExamPaperSet
from backend.models.mockexam_paper_set_models import MockExamPaperSetItem
from backend.services.mockexam_service import build_mockexam_beta_payload
from backend.services.mockexam_service import get_mockexam_beta_paper
from backend.services.mockexam_service import normalize_mockexam_beta_exam_category
from backend.services.mockexam_service import normalize_mockexam_beta_exam_content

PAPER_SET_SOURCE_KIND = "paper_set"
PAPER_SOURCE_KIND = "paper"
PAPER_SET_PAPER_CODE_PREFIX = "paper_set_"
PAPER_SET_ALLOWED_CONTENTS = {"Listening", "Reading", "Mixed"}


def get_active_teacher_user(db: Session, user_id: str) -> User:
    user = (
        db.query(User)
        .filter(User.id == user_id)
        .filter(User.delete_flag == "1")
        .first()
    )
    if not user or user.status != "active":
        raise PermissionError("当前账号不可用")
    if getattr(user, "is_teacher", "0") != "1":
        raise PermissionError("当前账号未开通教师端")
    return user


def normalize_mockexam_paper_set_content(value: str | None, *, allow_empty: bool = False) -> str:
    normalized = (value or "").strip().title()
    if not normalized and allow_empty:
        return ""
    if normalized not in PAPER_SET_ALLOWED_CONTENTS:
        raise ValueError("组合试卷内容筛选仅支持 Listening / Reading / Mixed")
    return normalized


def dedupe_int_list(values: list[int]) -> list[int]:
    result: list[int] = []
    seen: set[int] = set()
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result


def build_paper_set_paper_code(mockexam_paper_set_id: int) -> str:
    return f"{PAPER_SET_PAPER_CODE_PREFIX}{mockexam_paper_set_id}"


def build_paper_set_payload_meta(paper_set: MockExamPaperSet) -> dict[str, Any]:
    return {
        "kind": PAPER_SET_SOURCE_KIND,
        "paper_set_id": paper_set.mockexam_paper_set_id,
        "paper_set_name": paper_set.set_name,
        "exam_category": paper_set.exam_category,
        "exam_content": paper_set.exam_content,
    }


def extract_record_source_meta(
    *,
    paper_code: str | None,
    payload_json: Any,
) -> dict[str, Any]:
    payload = payload_json if isinstance(payload_json, dict) else {}
    raw_meta = payload.get("_meta") if isinstance(payload.get("_meta"), dict) else {}
    paper_set_id = raw_meta.get("paper_set_id")
    try:
        normalized_paper_set_id = int(paper_set_id) if paper_set_id is not None else None
    except (TypeError, ValueError):
        normalized_paper_set_id = None

    if normalized_paper_set_id is None:
        matched = re.match(rf"^{re.escape(PAPER_SET_PAPER_CODE_PREFIX)}(\d+)$", str(paper_code or "").strip())
        if matched:
            normalized_paper_set_id = int(matched.group(1))

    if normalized_paper_set_id is not None:
        return {
            "source_kind": PAPER_SET_SOURCE_KIND,
            "paper_set_id": normalized_paper_set_id,
        }

    return {
        "source_kind": PAPER_SOURCE_KIND,
        "paper_set_id": None,
    }


def get_mockexam_paper_set(
    db: Session,
    *,
    mockexam_paper_set_id: int,
    require_enabled: bool,
) -> MockExamPaperSet:
    query = (
        db.query(MockExamPaperSet)
        .options(selectinload(MockExamPaperSet.items))
        .filter(MockExamPaperSet.mockexam_paper_set_id == mockexam_paper_set_id)
        .filter(MockExamPaperSet.delete_flag == "1")
    )
    if require_enabled:
        query = query.filter(MockExamPaperSet.status == 1)
    row = query.first()
    if not row:
        raise LookupError("组合试卷不存在或已停用")
    return row


def load_exam_paper_map(db: Session, exam_paper_ids: list[int], *, require_enabled: bool) -> dict[int, ExamPaper]:
    normalized_ids = dedupe_int_list([int(item) for item in exam_paper_ids if int(item) > 0])
    if not normalized_ids:
        return {}
    query = (
        db.query(ExamPaper)
        .join(ExamPaper.bank)
        .filter(ExamPaper.exam_paper_id.in_(normalized_ids))
        .filter(ExamPaper.delete_flag == "1")
        .filter(ExamBank.delete_flag == "1")
        .filter(ExamBank.exam_type == "IELTS")
    )
    if require_enabled:
        query = query.filter(ExamPaper.status == 1).filter(ExamBank.status == 1)
    rows = query.all()
    return {row.exam_paper_id: row for row in rows}


def get_active_paper_set_items(paper_set: MockExamPaperSet) -> list[MockExamPaperSetItem]:
    return [
        item
        for item in sorted(
            paper_set.items or [],
            key=lambda current: (
                int(current.sort_order or 0),
                int(current.mockexam_paper_set_item_id or 0),
            ),
        )
        if item.delete_flag == "1"
    ]


def compute_paper_set_exam_content(papers: list[ExamPaper]) -> str:
    contents = {
        normalize_mockexam_beta_exam_content(
            "Listening" if str(paper.subject_type or "").strip().lower() == "listening" else "Reading"
        )
        for paper in papers
    }
    if len(contents) == 1:
        return next(iter(contents))
    return "Mixed"


def serialize_mockexam_paper_set_item(
    paper_set: MockExamPaperSet,
    *,
    paper_map: dict[int, ExamPaper],
) -> dict[str, Any]:
    items = get_active_paper_set_items(paper_set)
    ordered_papers = [paper_map[item.exam_paper_id] for item in items if item.exam_paper_id in paper_map]
    return {
        "mockexam_paper_set_id": paper_set.mockexam_paper_set_id,
        "set_name": paper_set.set_name,
        "exam_category": paper_set.exam_category,
        "exam_content": paper_set.exam_content,
        "paper_count": paper_set.paper_count,
        "status": int(paper_set.status or 0),
        "remark": paper_set.remark,
        "paper_ids": [paper.exam_paper_id for paper in ordered_papers],
        "paper_names": [paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}" for paper in ordered_papers],
        "create_time": paper_set.create_time,
        "update_time": paper_set.update_time,
    }


def list_student_mockexam_paper_sets(
    db: Session,
    *,
    exam_category: str | None = None,
    exam_content: str | None = None,
) -> list[dict[str, Any]]:
    normalized_exam_category = normalize_mockexam_beta_exam_category(exam_category, allow_empty=True)
    normalized_exam_content = normalize_mockexam_paper_set_content(exam_content, allow_empty=True)
    query = (
        db.query(MockExamPaperSet)
        .options(selectinload(MockExamPaperSet.items))
        .filter(MockExamPaperSet.delete_flag == "1")
        .filter(MockExamPaperSet.status == 1)
    )
    if normalized_exam_category:
        query = query.filter(MockExamPaperSet.exam_category == normalized_exam_category)
    if normalized_exam_content:
        query = query.filter(MockExamPaperSet.exam_content == normalized_exam_content)

    rows = (
        query.order_by(MockExamPaperSet.create_time.desc(), MockExamPaperSet.mockexam_paper_set_id.desc())
        .all()
    )
    paper_map = load_exam_paper_map(
        db,
        [item.exam_paper_id for row in rows for item in get_active_paper_set_items(row)],
        require_enabled=True,
    )
    return [serialize_mockexam_paper_set_item(row, paper_map=paper_map) for row in rows]


def list_teacher_mockexam_paper_sets(
    db: Session,
    *,
    teacher_user_id: str,
) -> list[dict[str, Any]]:
    get_active_teacher_user(db, teacher_user_id)
    rows = (
        db.query(MockExamPaperSet)
        .options(selectinload(MockExamPaperSet.items))
        .filter(MockExamPaperSet.created_by == teacher_user_id)
        .filter(MockExamPaperSet.delete_flag == "1")
        .order_by(MockExamPaperSet.create_time.desc(), MockExamPaperSet.mockexam_paper_set_id.desc())
        .all()
    )
    paper_map = load_exam_paper_map(
        db,
        [item.exam_paper_id for row in rows for item in get_active_paper_set_items(row)],
        require_enabled=False,
    )
    return [serialize_mockexam_paper_set_item(row, paper_map=paper_map) for row in rows]


def create_teacher_mockexam_paper_set(
    db: Session,
    *,
    teacher_user_id: str,
    set_name: str,
    exam_paper_ids: list[int],
    remark: str | None,
) -> dict[str, Any]:
    get_active_teacher_user(db, teacher_user_id)
    normalized_name = (set_name or "").strip()
    if not normalized_name:
        raise ValueError("请输入组合试卷名称")
    if len(normalized_name) > 255:
        raise ValueError("组合试卷名称长度不能超过 255 个字符")

    requested_ids = dedupe_int_list([int(item) for item in (exam_paper_ids or []) if int(item) > 0])
    if len(requested_ids) < 2:
        raise ValueError("组合试卷至少选择两张试卷")

    paper_map = load_exam_paper_map(db, requested_ids, require_enabled=True)
    ordered_papers: list[ExamPaper] = []
    for exam_paper_id in requested_ids:
        paper = paper_map.get(exam_paper_id)
        if paper is None:
            raise ValueError(f"试卷 #{exam_paper_id} 不存在或已停用")
        ordered_papers.append(paper)

    paper_set = MockExamPaperSet(
        set_name=normalized_name,
        exam_category="IELTS",
        exam_content=compute_paper_set_exam_content(ordered_papers),
        paper_count=len(ordered_papers),
        status=1,
        created_by=teacher_user_id,
        remark=(remark or "").strip() or None,
        delete_flag="1",
    )
    db.add(paper_set)
    db.flush()

    db.add_all(
        [
            MockExamPaperSetItem(
                mockexam_paper_set_id=paper_set.mockexam_paper_set_id,
                exam_paper_id=paper.exam_paper_id,
                sort_order=index,
                delete_flag="1",
            )
            for index, paper in enumerate(ordered_papers, start=1)
        ]
    )
    db.commit()
    db.refresh(paper_set)
    return serialize_mockexam_paper_set_item(
        get_mockexam_paper_set(
            db,
            mockexam_paper_set_id=paper_set.mockexam_paper_set_id,
            require_enabled=False,
        ),
        paper_map=paper_map,
    )


def update_teacher_mockexam_paper_set_status(
    db: Session,
    *,
    teacher_user_id: str,
    mockexam_paper_set_id: int,
    status: int,
) -> dict[str, Any]:
    get_active_teacher_user(db, teacher_user_id)
    paper_set = get_mockexam_paper_set(
        db,
        mockexam_paper_set_id=mockexam_paper_set_id,
        require_enabled=False,
    )
    if paper_set.created_by != teacher_user_id:
        raise PermissionError("无权操作该组合试卷")
    if int(status) not in {0, 1}:
        raise ValueError("状态仅支持 0 或 1")

    paper_set.status = int(status)
    db.commit()
    db.refresh(paper_set)
    paper_map = load_exam_paper_map(
        db,
        [item.exam_paper_id for item in get_active_paper_set_items(paper_set)],
        require_enabled=False,
    )
    return serialize_mockexam_paper_set_item(paper_set, paper_map=paper_map)


def build_mockexam_paper_set_payload(db: Session, *, paper_set: MockExamPaperSet) -> dict[str, Any]:
    items = get_active_paper_set_items(paper_set)
    if not items:
        raise ValueError("当前组合试卷没有有效试卷")

    ordered_papers = [
        get_mockexam_beta_paper(db, exam_paper_id=item.exam_paper_id)
        for item in items
    ]
    merged_passages: list[dict[str, Any]] = []
    for paper_index, paper in enumerate(ordered_papers, start=1):
        paper_payload = build_mockexam_beta_payload(paper)
        for passage_index, raw_passage in enumerate(paper_payload.get("passages") or [], start=1):
            if not isinstance(raw_passage, dict):
                continue
            passage = copy.deepcopy(raw_passage)
            base_passage_id = str(passage.get("id") or f"P{passage_index}").strip() or f"P{passage_index}"
            passage["id"] = f"PS{paper_set.mockexam_paper_set_id}-P{paper_index}-{base_passage_id}"
            base_title = str(passage.get("title") or "").strip()
            paper_title = paper.paper_name or paper.paper_code or f"Paper {paper.exam_paper_id}"
            passage["title"] = f"{paper_title} - {base_title}" if base_title else paper_title
            merged_passages.append(passage)

    if not merged_passages:
        raise ValueError("当前组合试卷没有可用题目")

    return {
        "_meta": build_paper_set_payload_meta(paper_set),
        "module": paper_set.set_name,
        "passages": merged_passages,
    }


def load_mockexam_paper_set_payload(
    db: Session,
    *,
    mockexam_paper_set_id: int,
) -> tuple[MockExamPaperSet, dict[str, Any]]:
    paper_set = get_mockexam_paper_set(
        db,
        mockexam_paper_set_id=mockexam_paper_set_id,
        require_enabled=True,
    )
    return paper_set, build_mockexam_paper_set_payload(db, paper_set=paper_set)
