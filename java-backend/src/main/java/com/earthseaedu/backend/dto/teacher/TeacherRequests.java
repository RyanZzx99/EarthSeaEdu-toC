package com.earthseaedu.backend.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class TeacherRequests {

    private TeacherRequests() {
    }

    public record TeacherMockExamPaperSetCreateRequest(
        @JsonProperty("set_name")
        @NotBlank(message = "set_name is required")
        @Size(max = 255, message = "set_name must be <= 255 chars")
        String setName,
        @JsonProperty("exam_paper_ids")
        @NotEmpty(message = "exam_paper_ids must not be empty")
        List<@NotNull(message = "exam_paper_ids must not contain null") Long> examPaperIds,
        @Size(max = 500, message = "remark must be <= 500 chars")
        String remark
    ) {
    }

    public record TeacherMockExamPaperSetStatusUpdateRequest(
        @NotNull(message = "status is required")
        @Min(value = 0, message = "status must be 0 or 1")
        @Max(value = 1, message = "status must be 0 or 1")
        Integer status
    ) {
    }
}
