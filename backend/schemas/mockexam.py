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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")


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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")
    create_time: datetime = Field(..., description="Submitted at")


class MockExamWrongQuestionReviewSummary(BaseModel):
    all_correct: bool = Field(default=False, description="Whether the current redo is fully correct")
    wrong_count: int = Field(default=0, description="Wrong question count in the current submit")
    active_wrong_question_count: int = Field(
        default=0,
        description="Active wrong-book question count for the current redo range",
    )
    exam_question_ids: list[int] = Field(default_factory=list, description="Exam question IDs in current redo range")


class MockExamSubmitResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    result: MockExamSubmitResult = Field(..., description="Score result")
    submission: MockExamSubmissionSummary | None = Field(default=None, description="Submission summary")
    wrongbook_review: MockExamWrongQuestionReviewSummary | None = Field(
        default=None,
        description="Wrong-book review summary for redo flow",
    )


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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")
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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")
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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")


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
    elapsed_seconds: int = Field(default=0, description="Elapsed seconds")
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
    source_kind: str | None = Field(default=None, description="Favorite source kind")
    paper_set_id: int | None = Field(default=None, description="Paper set ID when source is paper_set")


class MockExamFavoriteToggleResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    is_favorite: bool = Field(..., description="Favorite state")


class MockExamFavoriteBatchToggleRequest(BaseModel):
    is_favorite: bool = Field(..., description="Whether the target should be favorited")


class MockExamFavoriteBatchToggleResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    is_favorite: bool = Field(..., description="Favorite state")
    affected_count: int = Field(default=0, description="Affected entity count")


class MockExamFavoriteItem(BaseModel):
    exam_question_id: int = Field(..., description="Question ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_title: str = Field(..., description="Paper title")
    source_kind: str = Field(default="paper", description="Source kind")
    paper_set_id: int | None = Field(default=None, description="Paper set ID")
    exam_content: str | None = Field(default=None, description="Exam content")
    exam_section_id: int | None = Field(default=None, description="Section ID")
    section_title: str | None = Field(default=None, description="Section title")
    exam_group_id: int | None = Field(default=None, description="Group ID")
    group_title: str | None = Field(default=None, description="Group title")
    question_id: str = Field(..., description="Question code")
    question_no: str | None = Field(default=None, description="Question number")
    question_type: str | None = Field(default=None, description="Question type")
    stat_type: str | None = Field(default=None, description="Stat type")
    preview_text: str = Field(default="", description="Preview text")
    create_time: datetime = Field(..., description="Create time")


class MockExamFavoriteListResponse(BaseModel):
    items: list[MockExamFavoriteItem] = Field(default_factory=list, description="Favorite list")


class MockExamEntityFavoriteItem(BaseModel):
    target_type: str = Field(..., description="Favorite target type")
    target_id: int = Field(..., description="Favorite target ID")
    exam_paper_id: int | None = Field(default=None, description="Paper ID")
    paper_set_id: int | None = Field(default=None, description="Paper set ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    title: str = Field(..., description="Favorite title")
    exam_category: str = Field(..., description="Exam category")
    exam_content: str | None = Field(default=None, description="Exam content")
    create_time: datetime = Field(..., description="Create time")


class MockExamEntityFavoriteListResponse(BaseModel):
    items: list[MockExamEntityFavoriteItem] = Field(default_factory=list, description="Entity favorite list")


class MockExamWrongQuestionItem(BaseModel):
    exam_question_id: int | None = Field(default=None, description="Question ID")
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_title: str = Field(..., description="Paper title")
    exam_section_id: int | None = Field(default=None, description="Section ID")
    section_title: str | None = Field(default=None, description="Section title")
    exam_group_id: int | None = Field(default=None, description="Group ID")
    group_title: str | None = Field(default=None, description="Group title")
    exam_content: str | None = Field(default=None, description="Exam content")
    question_id: str = Field(..., description="Question code")
    question_no: str | None = Field(default=None, description="Question number")
    question_type: str | None = Field(default=None, description="Question type")
    stat_type: str | None = Field(default=None, description="Stat type")
    preview_text: str = Field(default="", description="Preview text")
    wrong_count: int = Field(default=0, description="Wrong count")
    last_wrong_time: datetime = Field(..., description="Last wrong time")


class MockExamWrongQuestionSummary(BaseModel):
    total_questions: int = Field(default=0, description="Active wrong question count")
    total_wrong_count: int = Field(default=0, description="Total accumulated wrong count")
    average_wrong_count: float = Field(default=0, description="Average wrong count per group")
    most_common_type: str | None = Field(default=None, description="Most common question type")


class MockExamWrongQuestionGroupItem(BaseModel):
    exam_paper_id: int = Field(..., description="Paper ID")
    paper_code: str | None = Field(default=None, description="Paper code")
    paper_title: str = Field(..., description="Paper title")
    exam_section_id: int | None = Field(default=None, description="Section ID")
    section_title: str | None = Field(default=None, description="Section title")
    exam_group_id: int | None = Field(default=None, description="Group ID")
    group_title: str | None = Field(default=None, description="Group title")
    exam_content: str | None = Field(default=None, description="Exam content")
    wrong_question_count: int = Field(default=0, description="Wrong question count in this group")
    total_wrong_count: int = Field(default=0, description="Total accumulated wrong count in this group")
    latest_wrong_time: datetime | None = Field(default=None, description="Latest wrong time in this group")
    questions: list[MockExamWrongQuestionItem] = Field(default_factory=list, description="Wrong question list")


class MockExamWrongQuestionListResponse(BaseModel):
    summary: MockExamWrongQuestionSummary = Field(
        default_factory=MockExamWrongQuestionSummary,
        description="Wrong-book summary",
    )
    groups: list[MockExamWrongQuestionGroupItem] = Field(default_factory=list, description="Wrong question groups")


class MockExamWrongQuestionResolveRequest(BaseModel):
    exam_question_ids: list[int] = Field(default_factory=list, description="Exam question IDs to resolve")


class MockExamWrongQuestionResolveResponse(BaseModel):
    status: str = Field(default="ok", description="Response status")
    removed_count: int = Field(default=0, description="Resolved wrong-question count")


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
