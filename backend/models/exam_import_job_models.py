from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import Integer
from sqlalchemy import JSON
from sqlalchemy import String
from sqlalchemy import Text
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from backend.config.db_conf import Base


def utcnow() -> datetime:
    return datetime.utcnow()


class ExamImportJob(Base):
    __tablename__ = "exam_import_job"

    exam_import_job_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    job_name: Mapped[str] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="导入任务名称",
    )
    bank_name: Mapped[str | None] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="题库名",
    )
    source_mode: Mapped[str] = mapped_column(
        String(20, collation="utf8mb4_unicode_ci"),
        nullable=False,
        comment="导入模式",
    )
    storage_path: Mapped[str | None] = mapped_column(
        String(255, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="上传文件存储路径",
    )
    status: Mapped[str] = mapped_column(
        String(20, collation="utf8mb4_unicode_ci"),
        nullable=False,
        default="pending",
        comment="任务状态",
    )
    uploaded_file_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="上传文件数",
    )
    resolved_file_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="解析后文件数",
    )
    manifest_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="识别 manifest 数",
    )
    success_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="成功题包数",
    )
    failure_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="失败题包数",
    )
    imported_bank_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入题库数",
    )
    imported_paper_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入试卷数",
    )
    imported_section_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 section 数",
    )
    imported_group_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 group 数",
    )
    imported_question_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 question 数",
    )
    imported_answer_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 answer 数",
    )
    imported_blank_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 blank 数",
    )
    imported_option_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 option 数",
    )
    imported_asset_count: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        comment="导入 asset 数",
    )
    progress_message: Mapped[str | None] = mapped_column(
        String(500, collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="进度提示",
    )
    result_json: Mapped[dict | list | None] = mapped_column(
        JSON,
        nullable=True,
        comment="导入结果明细",
    )
    error_message: Mapped[str | None] = mapped_column(
        Text(collation="utf8mb4_unicode_ci"),
        nullable=True,
        comment="错误信息",
    )
    start_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="开始时间",
    )
    finish_time: Mapped[datetime | None] = mapped_column(
        DateTime,
        nullable=True,
        comment="结束时间",
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
