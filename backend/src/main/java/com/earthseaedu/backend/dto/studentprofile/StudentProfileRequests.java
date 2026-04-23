package com.earthseaedu.backend.dto.studentprofile;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 学生档案新链路请求 DTO。
 */
public final class StudentProfileRequests {

    private StudentProfileRequests() {
    }

    /**
     * 语言考试新档案保存请求。
     *
     * @param archiveForm 语言考试相关表单数据
     */
    public record LanguageArchiveSavePayload(
        @JsonProperty("archive_form") Map<String, Object> archiveForm
    ) {
    }

    /**
     * 全量档案保存请求。
     *
     * @param sessionId AI 建档会话 ID
     * @param archiveForm 全量档案表单数据
     */
    public record ArchiveSavePayload(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("archive_form") Map<String, Object> archiveForm
    ) {
    }

    /**
     * 档案会话动作请求。
     *
     * @param sessionId AI 建档会话 ID
     */
    public record SessionActionPayload(
        @JsonProperty("session_id") String sessionId
    ) {
    }

    /**
     * 课程体系新档案保存请求。
     *
     * @param archiveForm 课程体系相关表单数据
     */
    public record CurriculumArchiveSavePayload(
        @JsonProperty("archive_form") Map<String, Object> archiveForm
    ) {
    }
}
