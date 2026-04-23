package com.earthseaedu.backend.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class TeacherResponses {

    private TeacherResponses() {
    }

    public record MockExamPaperSetItem(
        @JsonProperty("mockexam_paper_set_id")
        Long mockexamPaperSetId,
        @JsonProperty("set_name")
        String setName,
        @JsonProperty("exam_category")
        String examCategory,
        @JsonProperty("exam_content")
        String examContent,
        @JsonProperty("paper_count")
        Integer paperCount,
        Integer status,
        String remark,
        @JsonProperty("paper_ids")
        List<Long> paperIds,
        @JsonProperty("paper_names")
        List<String> paperNames,
        @JsonProperty("create_time")
        LocalDateTime createTime,
        @JsonProperty("update_time")
        LocalDateTime updateTime
    ) {
    }

    public record MockExamPaperSetListResponse(List<MockExamPaperSetItem> items) {
    }

    public record TeacherMockExamPaperSetMutationResponse(String status, MockExamPaperSetItem item) {
    }

    public record TeacherStudentSummary(
        @JsonProperty("user_id")
        String userId,
        String mobile,
        String nickname,
        String status
    ) {
    }

    public record TeacherStudentArchiveLookupResponse(
        TeacherStudentSummary student,
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
}
