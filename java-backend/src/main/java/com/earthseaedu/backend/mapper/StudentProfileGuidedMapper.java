package com.earthseaedu.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentProfileGuidedMapper {

    int expireSessionsForRestart(
        @Param("studentId") String studentId,
        @Param("questionnaireCode") String questionnaireCode
    );

    int completeSession(Map<String, Object> row);

    int activateSession(Map<String, Object> row);

    int exitSession(@Param("sessionId") String sessionId);

    int insertSession(Map<String, Object> row);

    int upsertResult(Map<String, Object> row);

    int updateSessionGenerationStage(Map<String, Object> row);

    Map<String, Object> findLatestSession(
        @Param("studentId") String studentId,
        @Param("questionnaireCode") String questionnaireCode
    );

    Map<String, Object> findOwnedSession(
        @Param("studentId") String studentId,
        @Param("sessionId") String sessionId
    );

    List<Map<String, Object>> listAnswerJson(@Param("sessionId") String sessionId);

    List<Map<String, Object>> listAnswerRows(@Param("sessionId") String sessionId);

    List<Map<String, Object>> listMessages(@Param("sessionId") String sessionId);

    int upsertAnswer(Map<String, Object> row);

    Integer nextMessageSequenceNo(@Param("sessionId") String sessionId);

    int insertMessage(Map<String, Object> row);

    int updateMessage(Map<String, Object> row);

    Long findLatestMessageId(
        @Param("sessionId") String sessionId,
        @Param("role") String role,
        @Param("kind") String kind,
        @Param("questionCode") String questionCode
    );

    Map<String, Object> findLatestResult(
        @Param("studentId") String studentId,
        @Param("sessionId") String sessionId
    );
}
