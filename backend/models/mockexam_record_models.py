from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Index
from sqlalchemy import JSON
from sqlalchemy import SmallInteger
from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from backend.config.db_conf import Base


class MockExamSubmission(Base):
    __tablename__ = "mockexam_submission"
    __table_args__ = (
        Index("idx_mockexam_submission_user_id", "user_id"),
        Index("idx_mockexam_submission_source_type", "source_type"),
        Index("idx_mockexam_submission_source_id", "source_id"),
        Index("idx_mockexam_submission_exam_category", "exam_category"),
        Index("idx_mockexam_submission_exam_content", "exam_content"),
        Index("idx_mockexam_submission_create_time", "create_time"),
    )

    mockexam_submission_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="提交用户ID",
    )
    source_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="来源类型：question-bank / exam-set / quick-practice / paper-beta",
    )
    source_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="来源ID",
    )
    exam_category: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="考试类别",
    )
    exam_content: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="考试内容",
    )
    title: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="试卷标题",
    )
    payload_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="提交时的试卷快照",
    )
    answers_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="用户答案快照",
    )
    marked_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="用户标记快照",
    )
    result_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="判分结果快照",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
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
