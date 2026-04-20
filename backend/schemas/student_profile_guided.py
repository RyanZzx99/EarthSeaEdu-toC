from __future__ import annotations

from typing import Any

from pydantic import BaseModel
from pydantic import Field


class GuidedAnswerPayload(BaseModel):
    """固定问卷单题提交载荷。"""

    question_code: str = Field(..., min_length=1, max_length=50)
    answer: dict[str, Any] = Field(default_factory=dict)


class GuidedExitPayload(BaseModel):
    """退出问卷并生成当前版本结果。"""

    trigger_reason: str = Field(default="manual_exit", max_length=50)

