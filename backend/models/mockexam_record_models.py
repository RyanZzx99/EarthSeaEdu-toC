from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import ForeignKey
from sqlalchemy import Index
from sqlalchemy import Integer
from sqlalchemy import JSON
from sqlalchemy import Numeric
from sqlalchemy import SmallInteger
from sqlalchemy import String
from sqlalchemy import Text
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from backend.config.db_conf import Base

if TYPE_CHECKING:
    from backend.models.ielts_exam_models import ExamQuestion


def utcnow() -> datetime:
    return datetime.utcnow()


class MockExamSubmission(Base):
    __tablename__ = "mockexam_submission"
    __table_args__ = (
        Index("idx_mockexam_submission_user_time", "user_id", "create_time"),
        Index("idx_mockexam_submission_user_paper_time", "user_id", "exam_paper_id", "create_time"),
        Index("idx_mockexam_submission_exam_paper_id", "exam_paper_id"),
        Index("idx_mockexam_submission_paper_code", "paper_code"),
        Index("idx_mockexam_submission_exam_content", "exam_content"),
    )

    mockexam_submission_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    user_id: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="用户ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="试卷ID",
    )
    paper_code: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="试卷编码快照",
    )
    title: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="试卷标题快照",
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
    payload_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="交卷时的试卷快照",
    )
    answered_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="已答题数",
    )
    total_questions: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="总题数",
    )
    gradable_questions: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="可判分题数",
    )
    elapsed_seconds: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="Elapsed seconds",
    )
    correct_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="答对题数",
    )
    wrong_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="答错题数",
    )
    unanswered_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="未答题数",
    )
    score_percent: Mapped[float | None] = mapped_column(
        Numeric(6, 2),
        nullable=True,
        comment="得分百分比",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
        comment="状态",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="交卷时间",
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

    answers: Mapped[list["MockExamSubmissionAnswer"]] = relationship(
        "MockExamSubmissionAnswer",
        back_populates="submission",
        cascade="all, delete-orphan",
    )
    question_states: Mapped[list["MockExamSubmissionQuestionState"]] = relationship(
        "MockExamSubmissionQuestionState",
        back_populates="submission",
        cascade="all, delete-orphan",
    )
    progresses: Mapped[list["MockExamProgress"]] = relationship(
        "MockExamProgress",
        back_populates="submission",
    )


class MockExamSubmissionAnswer(Base):
    __tablename__ = "mockexam_submission_answer"
    __table_args__ = (
        Index("idx_mockexam_submission_answer_submission_id", "mockexam_submission_id"),
        Index("idx_mockexam_submission_answer_submission_question", "mockexam_submission_id", "question_id"),
        Index(
            "idx_mockexam_submission_answer_submission_exam_question",
            "mockexam_submission_id",
            "exam_question_id",
        ),
    )

    mockexam_submission_answer_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    mockexam_submission_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_submission.mockexam_submission_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="交卷记录ID",
    )
    exam_question_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题目ID快照",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="业务题目ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="显示题号",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="渲染题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="统计题型",
    )
    blank_id: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="blank ID",
    )
    item_id: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="matching item ID",
    )
    answer_value: Mapped[str | None] = mapped_column(
        Text(collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="原子答案值",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="顺序",
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

    submission: Mapped["MockExamSubmission"] = relationship(
        "MockExamSubmission",
        back_populates="answers",
    )


class MockExamSubmissionQuestionState(Base):
    __tablename__ = "mockexam_submission_question_state"
    __table_args__ = (
        Index("idx_mockexam_submission_question_state_submission_id", "mockexam_submission_id"),
        Index("idx_mockexam_submission_question_state_submission_type", "mockexam_submission_id", "stat_type"),
        Index("idx_mockexam_submission_question_state_submission_correct", "mockexam_submission_id", "is_correct"),
        Index("idx_mockexam_submission_question_state_submission_marked", "mockexam_submission_id", "is_marked"),
    )

    mockexam_submission_question_state_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    mockexam_submission_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_submission.mockexam_submission_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="交卷记录ID",
    )
    exam_question_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题目ID快照",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="业务题目ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="显示题号",
    )
    question_index: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="试卷内顺序",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="渲染题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="统计题型",
    )
    is_marked: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否标记复查",
    )
    is_answered: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否已作答",
    )
    is_correct: Mapped[int | None] = mapped_column(
        SmallInteger,
        nullable=True,
        comment="是否答对",
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

    submission: Mapped["MockExamSubmission"] = relationship(
        "MockExamSubmission",
        back_populates="question_states",
    )


class MockExamProgress(Base):
    __tablename__ = "mockexam_progress"
    __table_args__ = (
        Index("idx_mockexam_progress_user_status_active", "user_id", "status", "last_active_time"),
        Index("idx_mockexam_progress_user_paper", "user_id", "exam_paper_id"),
        Index("idx_mockexam_progress_exam_paper_id", "exam_paper_id"),
        Index("idx_mockexam_progress_submission_id", "mockexam_submission_id"),
    )

    mockexam_progress_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    user_id: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="用户ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="试卷ID",
    )
    paper_code: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="试卷编码快照",
    )
    title: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="试卷标题快照",
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
    payload_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="保存时的试卷快照",
    )
    current_question_id: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="当前停留的业务题目ID",
    )
    current_question_index: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="当前停留题目序号",
    )
    current_question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="当前停留显示题号",
    )
    answered_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="已答题数",
    )
    total_questions: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="总题数",
    )
    elapsed_seconds: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="Elapsed seconds",
    )
    mockexam_submission_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_submission.mockexam_submission_id", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
        comment="最终交卷记录ID",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
        comment="状态",
    )
    last_active_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="最后活跃时间",
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

    answers: Mapped[list["MockExamProgressAnswer"]] = relationship(
        "MockExamProgressAnswer",
        back_populates="progress",
        cascade="all, delete-orphan",
    )
    question_states: Mapped[list["MockExamProgressQuestionState"]] = relationship(
        "MockExamProgressQuestionState",
        back_populates="progress",
        cascade="all, delete-orphan",
    )
    submission: Mapped["MockExamSubmission | None"] = relationship(
        "MockExamSubmission",
        back_populates="progresses",
    )


class MockExamProgressAnswer(Base):
    __tablename__ = "mockexam_progress_answer"
    __table_args__ = (
        Index("idx_mockexam_progress_answer_progress_id", "mockexam_progress_id"),
        Index("idx_mockexam_progress_answer_progress_question", "mockexam_progress_id", "question_id"),
        Index("idx_mockexam_progress_answer_progress_exam_question", "mockexam_progress_id", "exam_question_id"),
    )

    mockexam_progress_answer_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    mockexam_progress_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_progress.mockexam_progress_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="进行中记录ID",
    )
    exam_question_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题目ID快照",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="业务题目ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="显示题号",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="渲染题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="统计题型",
    )
    blank_id: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="blank ID",
    )
    item_id: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="matching item ID",
    )
    answer_value: Mapped[str | None] = mapped_column(
        Text(collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="原子答案值",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=1,
        comment="顺序",
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

    progress: Mapped["MockExamProgress"] = relationship(
        "MockExamProgress",
        back_populates="answers",
    )


class MockExamProgressQuestionState(Base):
    __tablename__ = "mockexam_progress_question_state"
    __table_args__ = (
        Index("idx_mockexam_progress_question_state_progress_id", "mockexam_progress_id"),
        Index("idx_mockexam_progress_question_state_progress_type", "mockexam_progress_id", "stat_type"),
        Index("idx_mockexam_progress_question_state_progress_marked", "mockexam_progress_id", "is_marked"),
    )

    mockexam_progress_question_state_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    mockexam_progress_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("mockexam_progress.mockexam_progress_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="进行中记录ID",
    )
    exam_question_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题目ID快照",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="业务题目ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="显示题号",
    )
    question_index: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="试卷内顺序",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="渲染题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="统计题型",
    )
    is_marked: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否标记复查",
    )
    is_answered: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否已作答",
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

    progress: Mapped["MockExamProgress"] = relationship(
        "MockExamProgress",
        back_populates="question_states",
    )


class MockExamQuestionFavorite(Base):
    __tablename__ = "mockexam_question_favorite"
    __table_args__ = (
        Index("idx_mockexam_question_favorite_user_time", "user_id", "create_time"),
        Index("idx_mockexam_question_favorite_exam_paper_id", "exam_paper_id"),
        Index("idx_mockexam_question_favorite_exam_section_id", "exam_section_id"),
        Index("idx_mockexam_question_favorite_exam_group_id", "exam_group_id"),
    )

    mockexam_question_favorite_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    user_id: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="用户ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="试卷ID快照",
    )
    paper_code: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="试卷编码快照",
    )
    paper_title: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="试卷标题快照",
    )
    exam_section_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="section ID",
    )
    exam_group_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="group ID",
    )
    exam_question_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_question.exam_question_id", ondelete="CASCADE", onupdate="CASCADE"),
        nullable=False,
        comment="题目ID",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="业务题目ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="显示题号",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="渲染题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="统计题型",
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

    question: Mapped["ExamQuestion"] = relationship("ExamQuestion")


class MockExamEntityFavorite(Base):
    __tablename__ = "mockexam_entity_favorite"
    __table_args__ = (
        Index(
            "uk_mockexam_entity_favorite_user_target",
            "user_id",
            "target_type",
            "target_id",
            unique=True,
        ),
        Index("idx_mockexam_entity_favorite_user_time", "user_id", "create_time"),
        Index("idx_mockexam_entity_favorite_target_type", "target_type"),
        Index("idx_mockexam_entity_favorite_exam_paper_id", "exam_paper_id"),
        Index("idx_mockexam_entity_favorite_paper_set_id", "paper_set_id"),
    )

    mockexam_entity_favorite_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    user_id: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="用户ID",
    )
    target_type: Mapped[str] = mapped_column(
        String(20, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="收藏类型：paper/paper_set",
    )
    target_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="收藏目标ID",
    )
    exam_paper_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题包ID快照",
    )
    paper_set_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="试卷ID快照",
    )
    paper_code: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="编码快照",
    )
    title: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="标题快照",
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
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
        comment="状态",
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


class MockExamWrongQuestionStat(Base):
    __tablename__ = "mockexam_wrong_question_stat"
    __table_args__ = (
        Index(
            "uk_mockexam_wrong_question_stat_user_question",
            "user_id",
            "exam_question_id",
            unique=True,
        ),
        Index(
            "idx_mockexam_wrong_question_stat_user_group_time",
            "user_id",
            "exam_group_id",
            "latest_wrong_time",
        ),
        Index(
            "idx_mockexam_wrong_question_stat_user_paper_time",
            "user_id",
            "exam_paper_id",
            "latest_wrong_time",
        ),
        Index(
            "idx_mockexam_wrong_question_stat_user_time",
            "user_id",
            "latest_wrong_time",
        ),
    )

    mockexam_wrong_question_stat_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="涓婚敭ID",
    )
    user_id: Mapped[str] = mapped_column(
        CHAR(36, collation="utf8mb4_unicode_ci"),
        ForeignKey("users.id", ondelete="RESTRICT", onupdate="CASCADE"),
        nullable=False,
        comment="鐢ㄦ埛ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="璇曞嵎ID蹇収",
    )
    paper_code: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="璇曞嵎缂栫爜蹇収",
    )
    paper_title: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="璇曞嵎鏍囬蹇収",
    )
    exam_section_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="section ID",
    )
    section_title: Mapped[str | None] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="section鏍囬蹇収",
    )
    exam_group_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="group ID",
    )
    group_title: Mapped[str | None] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="group鏍囬蹇収",
    )
    exam_question_id: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
        comment="棰樼洰ID",
    )
    question_id: Mapped[str] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="涓氬姟棰樼洰ID",
    )
    question_no: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="鏄剧ず棰樺彿",
    )
    question_type: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="棰樺瀷蹇収",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="缁熻棰樺瀷蹇収",
    )
    exam_category: Mapped[str] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="IELTS",
        comment="鑰冭瘯绫诲埆",
    )
    exam_content: Mapped[str | None] = mapped_column(
        String(50, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="鑰冭瘯鍐呭",
    )
    preview_text: Mapped[str | None] = mapped_column(
        String(500, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="棰樼洰棰勮鏂囨湰蹇収",
    )
    wrong_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="绱绛旈敊娆℃暟",
    )
    latest_wrong_submission_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="鏈€杩戜竴娆＄瓟閿檚ubmission ID",
    )
    latest_wrong_question_state_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="鏈€杩戜竴娆＄瓟閿檡uestion_state ID",
    )
    latest_wrong_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="最近一次答错时间",
    )
    latest_user_answer_json: Mapped[dict | list | str | int | float | bool | None] = mapped_column(
        JSON,
        nullable=True,
        comment="鏈€杩戜竴娆＄瓟閿欐椂鐢ㄦ埛绛旀蹇収",
    )
    latest_marked: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="鏈€杩戜竴娆＄瓟閿欐椂鏄惁鏍囪",
    )
    status: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=1,
        comment="状态",
    )
    create_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        comment="鍒涘缓鏃堕棿",
    )
    update_time: Mapped[datetime] = mapped_column(
        DateTime,
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
        comment="鏇存柊鏃堕棿",
    )
    delete_flag: Mapped[str] = mapped_column(
        CHAR(1, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="1",
        comment="閫昏緫鍒犻櫎鏍囪",
    )
