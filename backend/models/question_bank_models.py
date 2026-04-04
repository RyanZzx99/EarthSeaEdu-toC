from __future__ import annotations

from datetime import datetime

from sqlalchemy import BigInteger
from sqlalchemy import CHAR
from sqlalchemy import DateTime
from sqlalchemy import String
from sqlalchemy.dialects.mysql import LONGBLOB
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from backend.config.db_conf import Base


class QuestionBank(Base):
    __tablename__ = "question_bank"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
        comment="主键ID",
    )
    file_name: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="文件名",
    )
    exam_category: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="考试类别",
    )
    exam_content: Mapped[str] = mapped_column(
        String(200),
        nullable=False,
        comment="考试内容",
    )
    json_text: Mapped[bytes] = mapped_column(
        LONGBLOB,
        nullable=False,
        comment="原始 JSON 文件内容",
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
