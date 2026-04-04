from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Integer
from sqlalchemy import JSON
from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from backend.config.db_conf import Base

if TYPE_CHECKING:
    from backend.models.question_bank_models import QuestionBank


class ExamSet(Base):
    __tablename__ = "exam_sets"

    exam_sets_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="组合试卷ID",
    )
    name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="组合试卷名称",
    )
    mode: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        comment="组卷模式：manual=手动，random=随机",
    )
    exam_category: Mapped[str | None] = mapped_column(
        String(200),
        nullable=True,
        comment="考试类别",
    )
    rule_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="随机组卷规则 JSON",
    )
    part_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="试卷包含的题库份数",
    )
    status: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="状态：1启用，0停用",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记：1有效，0删除",
    )

    parts: Mapped[list["ExamSetPart"]] = relationship(
        "ExamSetPart",
        back_populates="exam_set",
        cascade="all, delete-orphan",
    )


class ExamSetPart(Base):
    __tablename__ = "exam_set_parts"

    paper_set_parts_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="组合试卷组成项ID",
    )
    exam_sets_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_sets.exam_sets_id", ondelete="CASCADE"),
        nullable=False,
        comment="组合试卷ID",
    )
    question_bank_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("question_bank.id", ondelete="CASCADE"),
        nullable=False,
        comment="题库ID",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="排序",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1),
        nullable=False,
        default="1",
        comment="逻辑删除标记：1有效，0删除",
    )

    exam_set: Mapped["ExamSet"] = relationship(
        "ExamSet",
        back_populates="parts",
    )
    question_bank: Mapped["QuestionBank"] = relationship("QuestionBank")
