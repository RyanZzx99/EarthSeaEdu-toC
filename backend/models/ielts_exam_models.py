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
from sqlalchemy import UniqueConstraint
from sqlalchemy.dialects.mysql import LONGTEXT
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column
from sqlalchemy.orm import relationship

from backend.config.db_conf import Base

if TYPE_CHECKING:
    from backend.models.question_bank_models import QuestionBank


class ExamBank(Base):
    __tablename__ = "exam_bank"
    __table_args__ = (
        UniqueConstraint("bank_code", name="uk_exam_bank_code"),
    )

    exam_bank_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    bank_code: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="题库编码",
    )
    bank_name: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="题库名称",
    )
    exam_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="IELTS",
        comment="考试类型",
    )
    subject_scope: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="题库范围：reading / listening / reading,listening",
    )
    source_name: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="导入来源名称",
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

    papers: Mapped[list["ExamPaper"]] = relationship(
        "ExamPaper",
        back_populates="bank",
        cascade="all, delete-orphan",
    )


class ExamPaper(Base):
    __tablename__ = "exam_paper"
    __table_args__ = (
        UniqueConstraint("paper_code", name="uk_exam_paper_code"),
        Index("idx_exam_paper_exam_bank_id", "exam_bank_id"),
        Index("idx_exam_paper_subject_type", "subject_type"),
    )

    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_bank_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_bank.exam_bank_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属题库ID",
    )
    paper_code: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="试卷编码",
    )
    paper_name: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        comment="试卷名称",
    )
    module_name: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="模块名称",
    )
    subject_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="科目类型：reading / listening",
    )
    book_code: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="题源册号",
    )
    test_no: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="Test 编号",
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

    bank: Mapped["ExamBank"] = relationship("ExamBank", back_populates="papers")
    sections: Mapped[list["ExamSection"]] = relationship(
        "ExamSection",
        back_populates="paper",
        cascade="all, delete-orphan",
    )


class ExamSection(Base):
    __tablename__ = "exam_section"
    __table_args__ = (
        Index("idx_exam_section_exam_paper_id", "exam_paper_id"),
        Index("idx_exam_section_section_id", "section_id"),
        Index("idx_exam_section_sort_order", "sort_order"),
    )

    exam_section_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_paper_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_paper.exam_paper_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属试卷ID",
    )
    section_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="原始 JSON 中的 section id",
    )
    section_no: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="section 顺序号",
    )
    section_title: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="section 标题",
    )
    content_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="section 级正文 HTML",
    )
    content_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="section 级正文纯文本",
    )
    instructions_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="section 级说明 HTML",
    )
    instructions_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="section 级说明纯文本",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="section 在试卷中的排序",
    )
    source_file: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="对应原始 JSON 文件名",
    )
    primary_audio_asset_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="主音频资源ID",
    )
    primary_image_asset_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="主图片资源ID",
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

    paper: Mapped["ExamPaper"] = relationship("ExamPaper", back_populates="sections")
    groups: Mapped[list["ExamGroup"]] = relationship(
        "ExamGroup",
        back_populates="section",
        cascade="all, delete-orphan",
    )
    assets: Mapped[list["ExamAsset"]] = relationship(
        "ExamAsset",
        back_populates="section",
        cascade="all, delete-orphan",
    )


class ExamGroup(Base):
    __tablename__ = "exam_group"
    __table_args__ = (
        Index("idx_exam_group_exam_section_id", "exam_section_id"),
        Index("idx_exam_group_group_id", "group_id"),
        Index("idx_exam_group_raw_type", "raw_type"),
        Index("idx_exam_group_stat_type", "stat_type"),
        Index("idx_exam_group_sort_order", "sort_order"),
    )

    exam_group_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_section_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_section.exam_section_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属 section ID",
    )
    group_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="原始 JSON 中的 group id",
    )
    group_title: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
        comment="题组标题",
    )
    raw_type: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="原始 JSON 题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="业务统计题型",
    )
    instructions_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题组说明 HTML",
    )
    instructions_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题组说明纯文本",
    )
    content_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题组材料 HTML",
    )
    content_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题组材料纯文本",
    )
    has_shared_options: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否有共享选项池",
    )
    has_blanks: Mapped[int] = mapped_column(
        SmallInteger,
        nullable=False,
        default=0,
        comment="是否包含 blank 占位",
    )
    primary_image_asset_id: Mapped[int | None] = mapped_column(
        BigInteger,
        nullable=True,
        comment="题组主图资源ID",
    )
    structure_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="题组结构补充信息",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="在 section 中的排序",
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

    section: Mapped["ExamSection"] = relationship("ExamSection", back_populates="groups")
    questions: Mapped[list["ExamQuestion"]] = relationship(
        "ExamQuestion",
        back_populates="group",
        cascade="all, delete-orphan",
    )
    options: Mapped[list["ExamGroupOption"]] = relationship(
        "ExamGroupOption",
        back_populates="group",
        cascade="all, delete-orphan",
    )
    assets: Mapped[list["ExamAsset"]] = relationship(
        "ExamAsset",
        back_populates="group",
        cascade="all, delete-orphan",
    )


class ExamQuestion(Base):
    __tablename__ = "exam_question"
    __table_args__ = (
        UniqueConstraint("question_code", name="uk_exam_question_code"),
        Index("idx_exam_question_exam_group_id", "exam_group_id"),
        Index("idx_exam_question_question_no", "question_no"),
        Index("idx_exam_question_raw_type", "raw_type"),
        Index("idx_exam_question_stat_type", "stat_type"),
    )

    exam_question_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_group_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_group.exam_group_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属题组ID",
    )
    question_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="原始或系统题目标识",
    )
    question_code: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="系统统一题目标识",
    )
    question_no: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
        comment="题号",
    )
    raw_type: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="原始 JSON 题型",
    )
    stat_type: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="业务统计题型",
    )
    stem_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题干 HTML",
    )
    stem_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题干纯文本",
    )
    content_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题块 HTML",
    )
    content_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="题块纯文本",
    )
    source_blank_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
        comment="若本题来自 blank，则记录 blank id",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="在 group 中的排序",
    )
    score: Mapped[float] = mapped_column(
        Numeric(10, 2),
        nullable=False,
        default=1.00,
        comment="题目分值",
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

    group: Mapped["ExamGroup"] = relationship("ExamGroup", back_populates="questions")
    answers: Mapped[list["ExamQuestionAnswer"]] = relationship(
        "ExamQuestionAnswer",
        back_populates="question",
        cascade="all, delete-orphan",
    )
    blanks: Mapped[list["ExamQuestionBlank"]] = relationship(
        "ExamQuestionBlank",
        back_populates="question",
        cascade="all, delete-orphan",
    )
    assets: Mapped[list["ExamAsset"]] = relationship(
        "ExamAsset",
        back_populates="question",
        cascade="all, delete-orphan",
    )


class ExamQuestionAnswer(Base):
    __tablename__ = "exam_question_answer"
    __table_args__ = (
        Index("idx_exam_question_answer_exam_question_id", "exam_question_id"),
    )

    exam_question_answer_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_question_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_question.exam_question_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属题目ID",
    )
    answer_raw: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="原始答案字符串",
    )
    answer_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="结构化答案",
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

    question: Mapped["ExamQuestion"] = relationship("ExamQuestion", back_populates="answers")


class ExamQuestionBlank(Base):
    __tablename__ = "exam_question_blank"
    __table_args__ = (
        Index("idx_exam_question_blank_exam_question_id", "exam_question_id"),
        Index("idx_exam_question_blank_blank_id", "blank_id"),
    )

    exam_question_blank_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_question_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_question.exam_question_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属题目ID",
    )
    blank_id: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        comment="原始 blank id",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="blank 在题块中的顺序",
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

    question: Mapped["ExamQuestion"] = relationship("ExamQuestion", back_populates="blanks")


class ExamGroupOption(Base):
    __tablename__ = "exam_group_option"
    __table_args__ = (
        Index("idx_exam_group_option_exam_group_id", "exam_group_id"),
        Index("idx_exam_group_option_sort_order", "sort_order"),
    )

    exam_group_option_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_group_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("exam_group.exam_group_id", ondelete="CASCADE"),
        nullable=False,
        comment="所属题组ID",
    )
    option_key: Mapped[str | None] = mapped_column(
        String(20),
        nullable=True,
        comment="选项标识",
    )
    option_html: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="选项 HTML",
    )
    option_text: Mapped[str | None] = mapped_column(
        LONGTEXT,
        nullable=True,
        comment="选项纯文本",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="选项排序",
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

    group: Mapped["ExamGroup"] = relationship("ExamGroup", back_populates="options")


class ExamAsset(Base):
    __tablename__ = "exam_asset"
    __table_args__ = (
        Index("idx_exam_asset_exam_section_id", "exam_section_id"),
        Index("idx_exam_asset_exam_group_id", "exam_group_id"),
        Index("idx_exam_asset_exam_question_id", "exam_question_id"),
        Index("idx_exam_asset_owner_type", "owner_type"),
        Index("idx_exam_asset_asset_type", "asset_type"),
        Index("idx_exam_asset_asset_role", "asset_role"),
    )

    exam_asset_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    exam_section_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("exam_section.exam_section_id", ondelete="CASCADE"),
        nullable=True,
        comment="所属 section ID",
    )
    exam_group_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("exam_group.exam_group_id", ondelete="CASCADE"),
        nullable=True,
        comment="所属题组ID",
    )
    exam_question_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("exam_question.exam_question_id", ondelete="CASCADE"),
        nullable=True,
        comment="所属题目ID",
    )
    owner_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="资源归属层级：section / group / question",
    )
    asset_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        comment="资源类型：audio / image / pdf / other",
    )
    asset_role: Mapped[str | None] = mapped_column(
        String(50),
        nullable=True,
        comment="资源角色：primary_audio / primary_image / inline_image / attachment",
    )
    asset_name: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
        comment="资源名称或文件名",
    )
    source_path: Mapped[str | None] = mapped_column(
        String(1000),
        nullable=True,
        comment="原始资源路径",
    )
    storage_path: Mapped[str | None] = mapped_column(
        String(1000),
        nullable=True,
        comment="存储路径",
    )
    asset_url: Mapped[str | None] = mapped_column(
        String(1000),
        nullable=True,
        comment="资源访问 URL",
    )
    sort_order: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="资源排序",
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

    section: Mapped["ExamSection | None"] = relationship("ExamSection", back_populates="assets")
    group: Mapped["ExamGroup | None"] = relationship("ExamGroup", back_populates="assets")
    question: Mapped["ExamQuestion | None"] = relationship("ExamQuestion", back_populates="assets")
