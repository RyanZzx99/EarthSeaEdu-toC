from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel
from pydantic import Field


class MockExamOptionsResponse(BaseModel):
    exam_category_options: list[str] = Field(default_factory=list, description="考试类别选项")
    content_options_map: dict[str, list[str]] = Field(default_factory=dict, description="考试内容选项映射")
    supported_categories: list[str] = Field(default_factory=list, description="当前已开放模考的考试类别")


class MockExamQuestionBankItem(BaseModel):
    id: int = Field(..., description="题库ID")
    file_name: str = Field(..., description="文件名")
    exam_category: str = Field(..., description="考试类别")
    exam_content: str = Field(..., description="考试内容")
    create_time: datetime = Field(..., description="创建时间")


class MockExamQuestionBankListResponse(BaseModel):
    items: list[MockExamQuestionBankItem] = Field(default_factory=list, description="题库列表")


class MockExamQuestionBankPayloadResponse(BaseModel):
    id: int = Field(..., description="题库ID")
    file_name: str = Field(..., description="文件名")
    exam_category: str = Field(..., description="考试类别")
    exam_content: str = Field(..., description="考试内容")
    payload: Any = Field(..., description="原始题库 JSON")


class MockExamSubmitRequest(BaseModel):
    answers: dict[str, Any] = Field(default_factory=dict, description="用户答案")
    marked: dict[str, bool] = Field(default_factory=dict, description="标记题目")


class MockExamSubmitDetail(BaseModel):
    question_id: str = Field(..., description="题目ID")
    type: str = Field(..., description="题型")
    answered: bool = Field(..., description="是否作答")
    correct: bool = Field(..., description="是否正确")
    gradable: bool = Field(..., description="是否可自动判分")
    stem: str = Field(..., description="题干摘要")


class MockExamSubmitResult(BaseModel):
    answered_count: int = Field(..., description="已答题数")
    total_questions: int = Field(..., description="总题数")
    gradable_questions: int = Field(..., description="可判分题数")
    correct_count: int = Field(..., description="答对题数")
    score_percent: float | None = Field(default=None, description="百分比得分")
    details: list[MockExamSubmitDetail] = Field(default_factory=list, description="逐题结果")


class MockExamSubmitResponse(BaseModel):
    status: str = Field(default="ok", description="响应状态")
    result: MockExamSubmitResult = Field(..., description="评分结果")
