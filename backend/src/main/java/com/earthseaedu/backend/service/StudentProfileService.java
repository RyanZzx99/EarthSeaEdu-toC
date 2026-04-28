package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.studentprofile.StudentProfileRequests;
import java.util.Map;

/**
 * 学生档案新链路服务接口。
 */
public interface StudentProfileService {

    /**
     * 获取当前学生的当前建档会话，可按需自动创建。
     */
    Map<String, Object> loadCurrentSession(String authorizationHeader, Integer createIfMissing);

    /**
     * 获取当前学生的全量新档案数据。
     */
    Map<String, Object> loadArchiveBundle(String authorizationHeader, String sessionId);

    /**
     * 获取当前学生的语言考试新档案数据。
     */
    Map<String, Object> loadLanguageArchiveBundle(String authorizationHeader);

    /**
     * 获取当前学生的课程体系新档案数据。
     */
    Map<String, Object> loadCurriculumArchiveBundle(String authorizationHeader);

    /**
     * 保存当前学生的全量新档案数据。
     */
    Map<String, Object> saveArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.ArchiveSavePayload payload
    );

    /**
     * 将正式档案同步到当前 AI 建档草稿。
     */
    Map<String, Object> syncArchiveDraftFromOfficial(
        String authorizationHeader,
        StudentProfileRequests.SessionActionPayload payload
    );

    /**
     * 基于正式档案重新生成六维图结果。
     */
    Map<String, Object> regenerateArchiveRadar(
        String authorizationHeader,
        StudentProfileRequests.SessionActionPayload payload
    );

    /**
     * 保存当前学生的语言考试新档案数据。
     */
    Map<String, Object> saveLanguageArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.LanguageArchiveSavePayload payload
    );

    /**
     * 保存当前学生的课程体系新档案数据。
     */
    Map<String, Object> saveCurriculumArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.CurriculumArchiveSavePayload payload
    );
}
