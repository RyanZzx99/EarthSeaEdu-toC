from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel
from pydantic import Field


class MockExamOptionsResponse(BaseModel):
    exam_category_options: list[str] = Field(default_factory=list, description="Exam categories")
    content_options_map: dict[str, list[str]] = Field(default_factory=dict, description="Exam content map")
    supported_categories: list[str] = Field(default_factory=list, description="Supported exam categories")


class MockExamPaperItem(BaseModel):
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str = Field(..., description="Paper code")
    paper_name: str = Field(..., description="Paper name")
    bank_name: str = Field(..., description="Bank name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    module_name: str = Field(default="", description="Module name")
    book_code: str | None = Field(default=None, description="Book code")
    test_no: int | None = Field(default=None, description="Test number")
    create_time: datetime = Field(..., description="Created at")


class MockExamPaperListResponse(BaseModel):
    items: list[MockExamPaperItem] = Field(default_factory=list, description="Paper list")


class MockExamPaperSetItem(BaseModel):
    mockexam_paper_set_id: int = Field(..., description="Paper set ID")
    set_name: str = Field(..., description="Paper set name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    paper_count: int = Field(default=0, description="Paper count")
    status: int = Field(default=1, description="Status")
    remark: str | None = Field(default=None, description="Remark")
    paper_ids: list[int] = Field(default_factory=list, description="Paper IDs")
    paper_names: list[str] = Field(default_factory=list, description="Paper names")
    create_time: datetime = Field(..., description="Created at")
    update_time: datetime = Field(..., description="Updated at")


class MockExamPaperSetListResponse(BaseModel):
    items: list[MockExamPaperSetItem] = Field(default_factory=list, description="Paper set list")


class MockExamPaperSetPayloadResponse(BaseModel):
    mockexam_paper_set_id: int = Field(..., description="Paper set ID")
    set_name: str = Field(..., description="Paper set name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    paper_count: int = Field(default=0, description="Paper count")
    payload: Any = Field(..., description="Mock exam payload")


class TeacherMockExamPaperSetCreateRequest(BaseModel):
    set_name: str = Field(..., min_length=1, max_length=255, description="Paper set name")
    exam_paper_ids: list[int] = Field(default_factory=list, description="Selected paper IDs")
    remark: str | None = Field(default=None, description="Remark")


class TeacherMockExamPaperSetStatusUpdateRequest(BaseModel):
    status: int = Field(..., description="Status: 0 or 1")


class TeacherMockExamPaperSetMutationResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    item: MockExamPaperSetItem = Field(..., description="Paper set item")


class MockExamPaperPayloadResponse(BaseModel):
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str = Field(..., description="Paper code")
    paper_name: str = Field(..., description="Paper name")
    bank_name: str = Field(..., description="Bank name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str = Field(..., description="Exam content")
    module_name: str = Field(default="", description="Module name")
    book_code: str | None = Field(default=None, description="Book code")
    test_no: int | None = Field(default=None, description="Test number")
    payload: Any = Field(..., description="Mock exam payload")


class MockExamSubmitRequest(BaseModel):
    answers: dict[str, Any] = Field(default_factory=dict, description="Answer map")
    marked: dict[str, bool] = Field(default_factory=dict, description="Marked-for-review map")
    progress_id: int | None = Field(default=None, description="Progress ID")


class MockExamSubmitDetail(BaseModel):
    question_id: str = Field(..., description="Question ID")
    exam_question_id: int | None = Field(default=None, description="Exam question ID")
    question_no: str | int | None = Field(default=None, description="Question number")
    type: str = Field(..., description="Question type")
    stat_type: str | None = Field(default=None, description="Stat type")
    answered: bool = Field(..., description="Answered")
    correct: bool = Field(..., description="Correct")
    gradable: bool = Field(..., description="Gradable")
    stem: str = Field(..., description="Question preview")


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


class MockExamSubmissionSummary(BaseModel):
    submission_id: int = Field(..., description="Submission ID")
    title: str = Field(..., description="Submission title")
    create_time: datetime = Field(..., description="Submitted at")


class MockExamSubmitResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    result: MockExamSubmitResult = Field(..., description="Score result")
    submission: MockExamSubmissionSummary | None = Field(default=None, description="Submission summary")


class MockExamSubmissionItem(BaseModel):
    submission_id: int = Field(..., description="Submission ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    source_kind: str = Field(default="paper", description="Source kind")
    paper_set_id: int | None = Field(default=None, description="Paper set ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    title: str = Field(..., description="Paper title")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
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
    exam_paper_id: int = Field(..., description="Paper ID")
    source_kind: str = Field(default="paper", description="Source kind")
    paper_set_id: int | None = Field(default=None, description="Paper set ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    title: str = Field(..., description="Paper title")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    create_time: datetime = Field(..., description="Submitted at")
    payload: Any = Field(..., description="Payload snapshot")
    answers: dict[str, Any] = Field(default_factory=dict, description="Answers")
    marked: dict[str, bool] = Field(default_factory=dict, description="Marked state")
    result: MockExamSubmitResult = Field(..., description="Result")


class MockExamProgressSaveRequest(BaseModel):
    progress_id: int | None = Field(default=None, description="Progress ID")
    payload: Any = Field(default=None, description="Payload snapshot")
    answers: dict[str, Any] = Field(default_factory=dict, description="Answers")
    marked: dict[str, bool] = Field(default_factory=dict, description="Marked state")
    current_question_id: str | None = Field(default=None, description="Current question ID")
    current_question_index: int | None = Field(default=None, description="Current question index")
    current_question_no: str | None = Field(default=None, description="Current question number")


class MockExamProgressItem(BaseModel):
    progress_id: int = Field(..., description="Progress ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    source_kind: str = Field(default="paper", description="Source kind")
    paper_set_id: int | None = Field(default=None, description="Paper set ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    title: str = Field(..., description="Paper title")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    answered_count: int = Field(default=0, description="Answered count")
    total_questions: int = Field(default=0, description="Total questions")
    current_question_id: str | None = Field(default=None, description="Current question ID")
    current_question_index: int | None = Field(default=None, description="Current question index")
    current_question_no: str | None = Field(default=None, description="Current question number")
    last_active_time: datetime = Field(..., description="Last active time")
    status: int = Field(..., description="Status")


class MockExamProgressListResponse(BaseModel):
    items: list[MockExamProgressItem] = Field(default_factory=list, description="Progress list")


class MockExamProgressDetailResponse(MockExamProgressItem):
    payload: Any = Field(..., description="Payload snapshot")
    answers: dict[str, Any] = Field(default_factory=dict, description="Answers")
    marked: dict[str, bool] = Field(default_factory=dict, description="Marked state")


class MockExamProgressMutationResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    item: MockExamProgressItem = Field(..., description="Progress item")


class MockExamFavoriteToggleRequest(BaseModel):
    is_favorite: bool = Field(..., description="Whether the question should be favorited")


class MockExamFavoriteToggleResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    is_favorite: bool = Field(..., description="Favorite state")


class MockExamFavoriteItem(BaseModel):
    exam_question_id: int = Field(..., description="Question ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_title: str = Field(..., description="Paper title")
    exam_content: str | None = Field(default=None, description="Exam content")
    question_id: str = Field(..., description="Question code")
    question_no: str | None = Field(default=None, description="Question number")
    question_type: str | None = Field(default=None, description="Question type")
    stat_type: str | None = Field(default=None, description="Stat type")
    preview_text: str = Field(default="", description="Preview text")
    create_time: datetime = Field(..., description="Create time")


class MockExamFavoriteListResponse(BaseModel):
    items: list[MockExamFavoriteItem] = Field(default_factory=list, description="Favorite list")


class MockExamWrongQuestionItem(BaseModel):
    exam_question_id: int | None = Field(default=None, description="Question ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_title: str = Field(..., description="Paper title")
    exam_content: str | None = Field(default=None, description="Exam content")
    question_id: str = Field(..., description="Question code")
    question_no: str | None = Field(default=None, description="Question number")
    question_type: str | None = Field(default=None, description="Question type")
    stat_type: str | None = Field(default=None, description="Stat type")
    preview_text: str = Field(default="", description="Preview text")
    wrong_count: int = Field(default=0, description="Wrong count")
    last_wrong_time: datetime = Field(..., description="Last wrong time")


class MockExamWrongQuestionListResponse(BaseModel):
    items: list[MockExamWrongQuestionItem] = Field(default_factory=list, description="Wrong question list")


class MockExamQuestionDetailResponse(BaseModel):
    exam_question_id: int = Field(..., description="Question ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_name: str = Field(..., description="Paper name")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    is_favorite: bool = Field(default=False, description="Favorite state")
    wrong_count: int = Field(default=0, description="Wrong count")
    section: Any = Field(..., description="Section data")
    group: Any = Field(..., description="Group data")
    question: Any = Field(..., description="Question data")
