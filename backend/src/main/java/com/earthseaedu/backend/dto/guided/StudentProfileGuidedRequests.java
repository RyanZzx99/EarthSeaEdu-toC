package com.earthseaedu.backend.dto.guided;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * AI 建档引导问卷请求 DTO 集合。
 */
public final class StudentProfileGuidedRequests {

    private StudentProfileGuidedRequests() {
    }

    /**
     * 单题答案提交请求。
     *
     * @param questionCode 题目编码，必填，最大 50 个字符
     * @param answer 答案内容，按题型传入结构化键值
     */
    public record GuidedAnswerPayload(
        @JsonProperty("question_code") @NotBlank @Size(max = 50) String questionCode,
        @JsonProperty("answer") Map<String, Object> answer
    ) {
    }

    /**
     * 退出引导问卷请求。
     *
     * @param triggerReason 退出原因，选填，最大 50 个字符
     */
    public record GuidedExitPayload(
        @JsonProperty("trigger_reason") @Size(max = 50) String triggerReason
    ) {
    }
}
