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


class MockExamExamSetItem(BaseModel):
    exam_sets_id: int = Field(..., description="组合试卷ID")
    name: str = Field(..., description="组合试卷名称")
    mode: str = Field(..., description="组卷模式")
    exam_category: str | None = Field(default=None, description="考试类别")
    part_count: int = Field(..., description="题库份数")
    status: str = Field(..., description="状态")
    content_summary: str = Field(default="", description="内容摘要")
    question_bank_names: list[str] = Field(default_factory=list, description="手动组卷题库名称列表")
    create_time: datetime = Field(..., description="创建时间")
    update_time: datetime = Field(..., description="更新时间")


class MockExamExamSetListResponse(BaseModel):
    items: list[MockExamExamSetItem] = Field(default_factory=list, description="组合试卷列表")


class MockExamExamSetPayloadResponse(BaseModel):
    exam_sets_id: int = Field(..., description="组合试卷ID")
    name: str = Field(..., description="组合试卷名称")
    mode: str = Field(..., description="组卷模式")
    exam_category: str | None = Field(default=None, description="考试类别")
    part_count: int = Field(..., description="题库份数")
    content_summary: str = Field(default="", description="内容摘要")
    question_bank_names: list[str] = Field(default_factory=list, description="手动组卷题库名称列表")
    payload: Any = Field(..., description="组合后的试卷 JSON")


class MockExamExamSetCreateRequest(BaseModel):
    name: str = Field(..., description="组合试卷名称")
    mode: str = Field(..., description="组卷模式：manual/random")
    exam_category: str = Field(..., description="考试类别")
    question_bank_ids: list[int] = Field(default_factory=list, description="手动组卷的题库ID列表")
    exam_contents: list[str] = Field(default_factory=list, description="随机组卷的考试内容列表")
    per_content: int = Field(default=1, description="每个内容随机份数")
    extra_count: int = Field(default=0, description="额外随机份数")
    total_count: int = Field(default=3, description="兜底总份数")


class MockExamExamSetStatusUpdateRequest(BaseModel):
    status: str = Field(..., description="状态：1启用，0停用")


class MockExamExamSetMutationResponse(BaseModel):
    status: str = Field(default="ok", description="响应状态")
    item: MockExamExamSetItem = Field(..., description="组合试卷信息")


class MockExamExamSetDeleteResponse(BaseModel):
    status: str = Field(default="ok", description="响应状态")


class MockExamQuickPracticePickedItem(BaseModel):
    id: int = Field(..., description="题库ID")
    file_name: str = Field(..., description="文件名")
    exam_content: str = Field(..., description="考试内容")


class MockExamQuickPracticeBuildRequest(BaseModel):
    exam_category: str = Field(..., description="考试类别")
    exam_contents: list[str] = Field(default_factory=list, description="筛选的考试内容")
    count: int = Field(default=3, description="组合份数")


class MockExamQuickPracticeBuildResponse(BaseModel):
    status: str = Field(default="ok", description="响应状态")
    exam_category: str = Field(..., description="考试类别")
    label: str = Field(..., description="试卷标题")
    payload: Any = Field(..., description="组合后的试卷 JSON")
    picked_items: list[MockExamQuickPracticePickedItem] = Field(default_factory=list, description="本次选中的题库")


class MockExamSubmitRequest(BaseModel):
    answers: dict[str, Any] = Field(default_factory=dict, description="用户答案")
    marked: dict[str, bool] = Field(default_factory=dict, description="标记题目")


class MockExamInlineSubmitRequest(MockExamSubmitRequest):
    payload: Any = Field(..., description="用于即时判分的试卷 JSON")


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
