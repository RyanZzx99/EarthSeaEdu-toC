package com.earthseaedu.backend.dto.teacher;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 教师端请求 DTO 集合。 */
public final class TeacherRequests {

    private TeacherRequests() {
    }

    /**
     * 创建教师模考套卷请求。
     *
     * @param setName 套卷名称，必填，最长 255 个字符
     * @param examPaperIds 试卷 ID 列表，必填，元素不能为 null
     * @param remark 备注，选填，最长 500 个字符
     */
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

    /**
     * 更新教师模考套卷状态请求。
     *
     * @param status 目标状态，必填，0 表示停用，1 表示启用
     */
    public record TeacherMockExamPaperSetStatusUpdateRequest(
        @NotNull(message = "status is required")
        @Min(value = 0, message = "status must be 0 or 1")
        @Max(value = 1, message = "status must be 0 or 1")
        Integer status
    ) {
    }
}
