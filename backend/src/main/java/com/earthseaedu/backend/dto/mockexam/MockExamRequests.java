package com.earthseaedu.backend.dto.mockexam;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** 模考接口请求 DTO 集合。 */
public final class MockExamRequests {

    private MockExamRequests() {
    }

    /**
     * 提交模考试卷请求。
     *
     * @param answers 答案映射，按题目 ID 或题号组织，选填时按空答案处理
     * @param marked 标记映射，选填，用于记录用户标记状态
     * @param progressId 未完成进度 ID，选填，用于提交并清理对应进度
     * @param elapsedSeconds 答题耗时秒数，选填
     */
    public record SubmitRequest(
        Map<String, Object> answers,
        Map<String, Object> marked,
        Object payload,
        @JsonProperty("progress_id") Long progressId,
        @JsonProperty("elapsed_seconds") Integer elapsedSeconds
    ) {
    }

    /**
     * 保存模考进度请求。
     *
     * @param progressId 进度 ID，选填，为空时创建新进度
     * @param payload 前端完整载荷，选填，用于恢复答题现场
     * @param answers 答案映射，选填
     * @param marked 标记映射，选填
     * @param currentQuestionId 当前题目 ID，选填
     * @param currentQuestionIndex 当前题目索引，选填
     * @param currentQuestionNo 当前题号，选填
     * @param elapsedSeconds 已用秒数，选填
     */
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

    /**
     * 切换单题收藏请求。
     *
     * @param isFavorite 是否收藏，必填
     * @param sourceKind 来源类型，选填
     * @param paperSetId 套卷 ID，选填，套卷场景下传入
     */
    public record FavoriteToggleRequest(
        @NotNull
        @JsonProperty("is_favorite")
        Boolean isFavorite,
        @JsonProperty("source_kind") String sourceKind,
        @JsonProperty("paper_set_id") Long paperSetId
    ) {
    }

    /**
     * 切换试卷或套卷收藏请求。
     *
     * @param isFavorite 是否收藏，必填
     */
    public record EntityFavoriteToggleRequest(
        @NotNull
        @JsonProperty("is_favorite")
        Boolean isFavorite
    ) {
    }

    /**
     * 批量处理错题请求。
     *
     * @param examQuestionIds 题目 ID 列表，选填，为空时不处理
     */
    public record WrongQuestionResolveRequest(
        @JsonProperty("exam_question_ids") List<Long> examQuestionIds
    ) {
    }

    /**
     * 选中文本翻译请求。
     *
     * @param selectedText 选中文本，选填，由服务层校验有效内容
     * @param scopeType 场景类型，选填
     * @param moduleName 模块名称，选填
     * @param passageId 篇章 ID，选填
     * @param questionId 题目 ID，选填
     * @param questionType 题型，选填
     * @param surroundingTextBefore 前文上下文，选填
     * @param surroundingTextAfter 后文上下文，选填
     * @param targetLang 目标语言，选填
     */
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
