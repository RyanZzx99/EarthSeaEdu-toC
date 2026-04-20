package com.earthseaedu.backend.dto.guided;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class StudentProfileGuidedRequests {

    private StudentProfileGuidedRequests() {
    }

    public record GuidedAnswerPayload(
        @JsonProperty("question_code") @NotBlank @Size(max = 50) String questionCode,
        @JsonProperty("answer") Map<String, Object> answer
    ) {
    }

    public record GuidedExitPayload(
        @JsonProperty("trigger_reason") @Size(max = 50) String triggerReason
    ) {
    }
}
