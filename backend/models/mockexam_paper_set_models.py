from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Index
from sqlalchemy import Integer
from sqlalchemy import SmallInteger
from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from backend.config.db_conf import Base

if TYPE_CHECKING:
    from backend.models.auth_models import User


def utcnow() -> datetime:
    return datetime.utcnow()


class MockExamPaperSet(Base):
    __tablename__ = "mockexam_paper_set"
    __table_args__ = (
        Index("idx_mockexam_paper_set_status", "status", "delete_flag"),
        Index("idx_mockexam_paper_set_category_content", "exam_category", "exam_content"),
        Index("idx_mockexam_paper_set_created_by", "created_by"),
    )

    mockexam_paper_set_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    set_name: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="组合试卷名称",
    )
    exam_category: Mapped[str] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="IELTS",
        comment="考试类别",
    )
    exam_content: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="考试内容",
    )
    paper_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="包含试卷数",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
        comment="状态",
    )
    created_by: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="创建人用户ID",
    )
    remark: Mapped[str | None] = mapped_column(
        String(500, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="备注",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="1",
        comment="逻辑删除标记",
    )

    items: Mapped[list["MockExamPaperSetItem"]] = relationship(
        "MockExamPaperSetItem",
        back_populates="paper_set",
        cascade="all, delete-orphan",
        order_by="MockExamPaperSetItem.sort_order.asc()",
    )
    creator: Mapped["User"] = relationship("User")


class MockExamPaperSetItem(Base):
    __tablename__ = "mockexam_paper_set_item"
    __table_args__ = (
        Index("idx_mockexam_paper_set_item_set_order", "mockexam_paper_set_id", "sort_order"),
        Index("idx_mockexam_paper_set_item_paper", "exam_paper_id"),
    )

    mockexam_paper_set_item_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    mockexam_paper_set_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_paper_set.mockexam_paper_set_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="组合试卷ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="试卷ID",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="排序",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="创建时间",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        comment="更新时间",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="1",
        comment="逻辑删除标记",
    )

    paper_set: Mapped["MockExamPaperSet"] = relationship(
        "MockExamPaperSet",
        back_populates="items",
    )
