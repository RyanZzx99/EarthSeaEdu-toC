from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel
from pydantic import Field


class MockExamOptionsResponse(BaseModel):
    exam_category_options: list[str] = Field(default_factory=list, description="Exam categories")
    content_options_map: dict[str, list[str]] = Field(default_factory=dict, description="Exam content map")
    supported_categories: list[str] = Field(default_factory=list, description="Supported exam categories")


class MockExamQuestionBankItem(BaseModel):
    id: int = Field(..., description="Question bank ID")
    file_name: str = Field(..., description="File name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    create_time: datetime = Field(..., description="Created at")


class MockExamQuestionBankListResponse(BaseModel):
    items: list[MockExamQuestionBankItem] = Field(default_factory=list, description="Question bank list")


class MockExamQuestionBankPayloadResponse(BaseModel):
    id: int = Field(..., description="Question bank ID")
    file_name: str = Field(..., description="File name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    payload: Any = Field(..., description="Raw question bank payload")


class MockExamBetaPaperItem(BaseModel):
    exam_paper_id: int = Field(..., description="Structured paper ID")
    paper_code: str = Field(..., description="Paper code")
    paper_name: str = Field(..., description="Paper name")
    bank_name: str = Field(..., description="Bank name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    module_name: str = Field(default="", description="Module name")
    book_code: str | None = Field(default=None, description="Book code")
    test_no: int | None = Field(default=None, description="Test number")
    create_time: datetime = Field(..., description="Created at")


class MockExamBetaPaperListResponse(BaseModel):
    items: list[MockExamBetaPaperItem] = Field(default_factory=list, description="Structured paper list")


class MockExamBetaPaperPayloadResponse(BaseModel):
    exam_paper_id: int = Field(..., description="Structured paper ID")
    paper_code: str = Field(..., description="Paper code")
    paper_name: str = Field(..., description="Paper name")
    bank_name: str = Field(..., description="Bank name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    module_name: str = Field(default="", description="Module name")
    book_code: str | None = Field(default=None, description="Book code")
    test_no: int | None = Field(default=None, description="Test number")
    payload: Any = Field(..., description="Converted mock exam payload")


class MockExamExamSetItem(BaseModel):
    exam_sets_id: int = Field(..., description="Exam set ID")
    name: str = Field(..., description="Exam set name")
    mode: str = Field(..., description="Assembly mode")
    exam_category: str | None = Field(default=None, description="Exam category")
    part_count: int = Field(..., description="Part count")
    status: str = Field(..., description="Status")
    content_summary: str = Field(default="", description="Content summary")
    question_bank_names: list[str] = Field(default_factory=list, description="Question bank names")
    create_time: datetime = Field(..., description="Created at")
    update_time: datetime = Field(..., description="Updated at")


class MockExamExamSetListResponse(BaseModel):
    items: list[MockExamExamSetItem] = Field(default_factory=list, description="Exam set list")


class MockExamExamSetPayloadResponse(BaseModel):
    exam_sets_id: int = Field(..., description="Exam set ID")
    name: str = Field(..., description="Exam set name")
    mode: str = Field(..., description="Assembly mode")
    exam_category: str | None = Field(default=None, description="Exam category")
    part_count: int = Field(..., description="Part count")
    content_summary: str = Field(default="", description="Content summary")
    question_bank_names: list[str] = Field(default_factory=list, description="Question bank names")
    payload: Any = Field(..., description="Merged payload")


class MockExamExamSetCreateRequest(BaseModel):
    name: str = Field(..., description="Exam set name")
    mode: str = Field(..., description="Assembly mode")
    exam_category: str = Field(..., description="Exam category")
    question_bank_ids: list[int] = Field(default_factory=list, description="Question bank IDs for manual mode")
    exam_contents: list[str] = Field(default_factory=list, description="Exam contents for random mode")
    per_content: int = Field(default=1, description="Per-content count")
    extra_count: int = Field(default=0, description="Extra random count")
    total_count: int = Field(default=3, description="Total count")


class MockExamExamSetStatusUpdateRequest(BaseModel):
    status: str = Field(..., description="Status")


class MockExamExamSetMutationResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    item: MockExamExamSetItem = Field(..., description="Exam set item")


class MockExamExamSetDeleteResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")


class MockExamQuickPracticePickedItem(BaseModel):
    id: int = Field(..., description="Question bank ID")
    file_name: str = Field(..., description="File name")
    exam_content: str = Field(..., description="Exam content")


class MockExamQuickPracticeBuildRequest(BaseModel):
    exam_category: str = Field(..., description="Exam category")
    exam_contents: list[str] = Field(default_factory=list, description="Exam contents")
    count: int = Field(default=3, description="Question bank count")


class MockExamQuickPracticeBuildResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    exam_category: str = Field(..., description="Exam category")
    label: str = Field(..., description="Practice label")
    payload: Any = Field(..., description="Merged payload")
    picked_items: list[MockExamQuickPracticePickedItem] = Field(default_factory=list, description="Picked question banks")


class MockExamSubmitRequest(BaseModel):
    answers: dict[str, Any] = Field(default_factory=dict, description="Answer map")
    marked: dict[str, bool] = Field(default_factory=dict, description="Marked-for-review map")


class MockExamInlineSubmitRequest(MockExamSubmitRequest):
    payload: Any = Field(..., description="Inline payload to score")
    source_type: str | None = Field(default=None, description="Source type")
    source_id: str | None = Field(default=None, description="Source ID")
    source_title: str | None = Field(default=None, description="Source title")
    exam_category: str | None = Field(default=None, description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")


class MockExamSubmitDetail(BaseModel):
    question_id: str = Field(..., description="Question ID")
    type: str = Field(..., description="Question type")
    answered: bool = Field(..., description="Answered")
    correct: bool = Field(..., description="Correct")
    gradable: bool = Field(..., description="Gradable")
    stem: str = Field(..., description="Question stem preview")


class MockExamSubmitTypeBreakdownItem(BaseModel):
    question_type: str = Field(..., description="Question type")
    total_questions: int = Field(..., description="Total questions")
    answered_count: int = Field(..., description="Answered count")
    gradable_questions: int = Field(..., description="Gradable question count")
    correct_count: int = Field(..., description="Correct count")
    wrong_count: int = Field(..., description="Wrong count")
    unanswered_count: int = Field(..., description="Unanswered count")


class MockExamSubmitResult(BaseModel):
    answered_count: int = Field(..., description="Answered count")
    total_questions: int = Field(..., description="Total questions")
    gradable_questions: int = Field(..., description="Gradable question count")
    correct_count: int = Field(..., description="Correct count")
    wrong_count: int = Field(..., description="Wrong count")
    unanswered_count: int = Field(..., description="Unanswered count")
    score_percent: float | None = Field(default=None, description="Score percent")
    type_breakdown: list[MockExamSubmitTypeBreakdownItem] = Field(default_factory=list, description="Per-type breakdown")
    details: list[MockExamSubmitDetail] = Field(default_factory=list, description="Per-question results")


class MockExamSubmissionItem(BaseModel):
    submission_id: int = Field(..., description="Submission ID")
    source_type: str = Field(..., description="Source type")
    source_id: str | None = Field(default=None, description="Source ID")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    title: str = Field(..., description="Submission title")
    score_percent: float | None = Field(default=None, description="Score percent")
    total_questions: int = Field(default=0, description="Total questions")
    correct_count: int = Field(default=0, description="Correct count")
    wrong_count: int = Field(default=0, description="Wrong count")
    gradable_questions: int = Field(default=0, description="Gradable question count")
    create_time: datetime = Field(..., description="Submitted at")


class MockExamSubmissionListResponse(BaseModel):
    items: list[MockExamSubmissionItem] = Field(default_factory=list, description="Submission list")


class MockExamSubmissionDetailResponse(BaseModel):
    submission_id: int = Field(..., description="Submission ID")
    source_type: str = Field(..., description="Source type")
    source_id: str | None = Field(default=None, description="Source ID")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    title: str = Field(..., description="Submission title")
    create_time: datetime = Field(..., description="Submitted at")
    payload: Any = Field(..., description="Payload snapshot")
    answers: Any = Field(default_factory=dict, description="Answer snapshot")
    marked: Any = Field(default_factory=dict, description="Marked snapshot")
    result: MockExamSubmitResult = Field(..., description="Score result")


class MockExamSubmissionSummary(BaseModel):
    submission_id: int = Field(..., description="Submission ID")
    title: str = Field(..., description="Submission title")
    create_time: datetime = Field(..., description="Submitted at")


class MockExamSubmitResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    result: MockExamSubmitResult = Field(..., description="Score result")
    submission: MockExamSubmissionSummary | None = Field(default=None, description="Submission summary")
