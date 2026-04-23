package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiChatPersistenceMapper {

    Map<String, Object> findSessionById(@Param("sessionId") String sessionId);

    Map<String, Object> findActiveSessionByStudentAndDomain(
        @Param("studentId") String studentId,
        @Param("bizDomain") String bizDomain
    );

    int insertSession(Map<String, Object> row);

    int updateSessionAfterUserMessage(Map<String, Object> row);

    int updateSessionAfterAssistantMessage(Map<String, Object> row);

    int updateSessionStage(Map<String, Object> row);

    int updateSessionProgress(Map<String, Object> row);

    int updateSessionFinalProfile(Map<String, Object> row);

    List<Map<String, Object>> listVisibleMessages(
        @Param("sessionId") String sessionId,
        @Param("limit") int limit,
        @Param("beforeId") Long beforeId
    );

    List<Map<String, Object>> listRecentVisibleMessages(
        @Param("sessionId") String sessionId,
        @Param("limit") int limit
    );

    Map<String, Object> findLatestProgressState(@Param("sessionId") String sessionId);

    int insertMessage(Map<String, Object> row);

    Integer selectNextSequenceNo(@Param("sessionId") String sessionId);

    Integer selectCurrentRound(@Param("sessionId") String sessionId);

    Map<String, Object> findProfileResultBySessionId(@Param("sessionId") String sessionId);

    Map<String, Object> findLatestProfileResultBySessionId(@Param("sessionId") String sessionId);

    Map<String, Object> findLatestProfileResultByStudentId(@Param("studentId") String studentId);

    int insertProfileResult(Map<String, Object> row);

    int updateProfileResult(Map<String, Object> row);

    Map<String, Object> findLatestGuidedResultByStudentId(@Param("studentId") String studentId);

    Map<String, Object> findDraftBySessionId(@Param("sessionId") String sessionId);

    int insertProfileDraft(Map<String, Object> row);

    int updateProfileDraft(Map<String, Object> row);
}
