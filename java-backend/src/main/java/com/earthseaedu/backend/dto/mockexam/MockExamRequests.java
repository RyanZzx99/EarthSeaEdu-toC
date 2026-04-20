package com.earthseaedu.backend.dto.mockexam;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public final class MockExamRequests {

    private MockExamRequests() {
    }

    public record SubmitRequest(
        Map<String, Object> answers,
        Map<String, Object> marked,
        @JsonProperty("progress_id") Long progressId,
        @JsonProperty("elapsed_seconds") Integer elapsedSeconds
    ) {
    }

    public record ProgressSaveRequest(
        @JsonProperty("progress_id") Long progressId,
        Object payload,
        Map<String, Object> answers,
        Map<String, Object> marked,
        @JsonProperty("current_question_id") String currentQuestionId,
        @JsonProperty("current_question_index") Integer currentQuestionIndex,
        @JsonProperty("current_question_no") String currentQuestionNo,
        @JsonProperty("elapsed_seconds") Integer elapsedSeconds
    ) {
    }

    public record FavoriteToggleRequest(
        @NotNull
        @JsonProperty("is_favorite")
        Boolean isFavorite,
        @JsonProperty("source_kind") String sourceKind,
        @JsonProperty("paper_set_id") Long paperSetId
    ) {
    }

    public record EntityFavoriteToggleRequest(
        @NotNull
        @JsonProperty("is_favorite")
        Boolean isFavorite
    ) {
    }

    public record WrongQuestionResolveRequest(
        @JsonProperty("exam_question_ids") List<Long> examQuestionIds
    ) {
    }

    public record SelectionTranslateRequest(
        @JsonProperty("selected_text") String selectedText,
        @JsonProperty("scope_type") String scopeType,
        @JsonProperty("module_name") String moduleName,
        @JsonProperty("passage_id") String passageId,
        @JsonProperty("question_id") String questionId,
        @JsonProperty("question_type") String questionType,
        @JsonProperty("surrounding_text_before") String surroundingTextBefore,
        @JsonProperty("surrounding_text_after") String surroundingTextAfter,
        @JsonProperty("target_lang") String targetLang
    ) {
    }
}
