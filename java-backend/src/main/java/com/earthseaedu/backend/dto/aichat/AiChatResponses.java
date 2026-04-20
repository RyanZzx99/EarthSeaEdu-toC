package com.earthseaedu.backend.dto.aichat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public final class AiChatResponses {

    private AiChatResponses() {
    }

    public record CurrentSessionEnvelope(
        @JsonProperty("has_active_session")
        boolean hasActiveSession,
        CurrentSessionSummary session
    ) {
    }

    public record CurrentSessionSummary(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("session_status")
        String sessionStatus,
        @JsonProperty("current_stage")
        String currentStage,
        @JsonProperty("current_round")
        int currentRound,
        @JsonProperty("missing_dimensions")
        List<Object> missingDimensions,
        @JsonProperty("last_message_at")
        String lastMessageAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("is_new_session")
        Boolean isNewSession
    ) {
    }

    public record SessionDetailResponse(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("student_id")
        String studentId,
        @JsonProperty("biz_domain")
        String bizDomain,
        @JsonProperty("session_status")
        String sessionStatus,
        @JsonProperty("current_stage")
        String currentStage,
        @JsonProperty("current_round")
        int currentRound,
        @JsonProperty("missing_dimensions")
        List<Object> missingDimensions,
        @JsonProperty("last_message_at")
        String lastMessageAt,
        @JsonProperty("completed_at")
        String completedAt,
        @JsonProperty("final_profile_id")
        Long finalProfileId,
        String remark
    ) {
    }

    public record MessageItem(
        long id,
        @JsonProperty("message_role")
        String messageRole,
        @JsonProperty("message_type")
        String messageType,
        @JsonProperty("sequence_no")
        int sequenceNo,
        String content,
        @JsonProperty("create_time")
        String createTime
    ) {
    }

    public record MessageListResponse(
        List<MessageItem> items,
        @JsonProperty("has_more")
        boolean hasMore
    ) {
    }

    public record ProfileResultResponse(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("profile_result_id")
        long profileResultId,
        @JsonProperty("result_status")
        String resultStatus,
        @JsonProperty("profile_json")
        Map<String, Object> profileJson,
        @JsonProperty("summary_text")
        String summaryText,
        @JsonProperty("radar_scores_json")
        Map<String, Object> radarScoresJson,
        @JsonProperty("db_payload_json")
        Map<String, Object> dbPayloadJson,
        @JsonProperty("save_error_message")
        String saveErrorMessage,
        @JsonProperty("create_time")
        String createTime,
        @JsonProperty("update_time")
        String updateTime
    ) {
    }

    public record RadarResponse(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("profile_result_id")
        long profileResultId,
        @JsonProperty("result_status")
        String resultStatus,
        @JsonProperty("radar_scores_json")
        Map<String, Object> radarScoresJson
    ) {
    }

    public record ArchiveFormResponse(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("archive_form")
        Map<String, Object> archiveForm,
        @JsonProperty("form_meta")
        Map<String, Object> formMeta,
        @JsonProperty("result_status")
        String resultStatus,
        @JsonProperty("summary_text")
        String summaryText,
        @JsonProperty("radar_scores_json")
        Map<String, Object> radarScoresJson,
        @JsonProperty("save_error_message")
        String saveErrorMessage,
        @JsonProperty("create_time")
        String createTime,
        @JsonProperty("update_time")
        String updateTime
    ) {
    }

    public record ArchiveFormMutationResponse(
        @JsonProperty("session_id")
        String sessionId,
        @JsonProperty("archive_form")
        Map<String, Object> archiveForm,
        @JsonProperty("form_meta")
        Map<String, Object> formMeta,
        @JsonProperty("profile_result_id")
        Long profileResultId,
        @JsonProperty("result_status")
        String resultStatus,
        @JsonProperty("profile_json")
        Map<String, Object> profileJson,
        @JsonProperty("summary_text")
        String summaryText,
        @JsonProperty("radar_scores_json")
        Map<String, Object> radarScoresJson,
        @JsonProperty("db_payload_json")
        Map<String, Object> dbPayloadJson,
        @JsonProperty("save_error_message")
        String saveErrorMessage,
        @JsonProperty("create_time")
        String createTime,
        @JsonProperty("update_time")
        String updateTime
    ) {
    }
}
