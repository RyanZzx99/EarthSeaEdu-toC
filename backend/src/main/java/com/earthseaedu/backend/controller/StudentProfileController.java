package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.studentprofile.StudentProfileRequests;
import com.earthseaedu.backend.service.StudentProfileService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生档案新链路接口。
 */
@RestController
@RequestMapping("/api/v1/student-profile")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    /**
     * 获取当前学生的当前建档会话，可按需自动创建。
     */
    @GetMapping("/sessions/current")
    public Map<String, Object> getCurrentSession(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "create_if_missing", required = false, defaultValue = "0") Integer createIfMissing
    ) {
        return studentProfileService.loadCurrentSession(authorizationHeader, createIfMissing);
    }

    /**
     * 获取当前学生的全量新档案数据。
     */
    @GetMapping("/archive")
    public Map<String, Object> getArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestParam(value = "session_id", required = false) String sessionId
    ) {
        return studentProfileService.loadArchiveBundle(authorizationHeader, sessionId);
    }

    /**
     * 获取当前学生的语言考试新档案数据。
     */
    @GetMapping("/archive/language")
    public Map<String, Object> getLanguageArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return studentProfileService.loadLanguageArchiveBundle(authorizationHeader);
    }

    /**
     * 获取当前学生的课程体系新档案数据。
     */
    @GetMapping("/archive/curriculum")
    public Map<String, Object> getCurriculumArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return studentProfileService.loadCurriculumArchiveBundle(authorizationHeader);
    }

    /**
     * 保存当前学生的全量新档案数据。
     */
    @PostMapping("/archive")
    public Map<String, Object> saveArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody StudentProfileRequests.ArchiveSavePayload payload
    ) {
        return studentProfileService.saveArchiveBundle(authorizationHeader, payload);
    }

    /**
     * 将正式档案同步到当前 AI 建档草稿。
     */
    @PostMapping("/archive/draft/sync")
    public Map<String, Object> syncArchiveDraftFromOfficial(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody StudentProfileRequests.SessionActionPayload payload
    ) {
        return studentProfileService.syncArchiveDraftFromOfficial(authorizationHeader, payload);
    }

    /**
     * 基于正式档案重新生成六维图结果。
     */
    @PostMapping("/archive/regenerate-radar")
    public Map<String, Object> regenerateArchiveRadar(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody StudentProfileRequests.SessionActionPayload payload
    ) {
        return studentProfileService.regenerateArchiveRadar(authorizationHeader, payload);
    }

    /**
     * 保存当前学生的语言考试新档案数据。
     */
    @PostMapping("/archive/language")
    public Map<String, Object> saveLanguageArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody StudentProfileRequests.LanguageArchiveSavePayload payload
    ) {
        return studentProfileService.saveLanguageArchiveBundle(authorizationHeader, payload);
    }

    /**
     * 保存当前学生的课程体系新档案数据。
     */
    @PostMapping("/archive/curriculum")
    public Map<String, Object> saveCurriculumArchiveBundle(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
        @RequestBody StudentProfileRequests.CurriculumArchiveSavePayload payload
    ) {
        return studentProfileService.saveCurriculumArchiveBundle(authorizationHeader, payload);
    }
}
