package com.earthseaedu.backend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import com.earthseaedu.backend.exception.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChatWriteService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final Set<String> BUSY_STAGES = Set.of(
        "progress_updating",
        "extraction",
        "scoring",
        "profile_saving"
    );

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final BusinessProfileFormService businessProfileFormService;
    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final AiProfileRadarPendingService aiProfileRadarPendingService;

    public AiChatWriteService(
        JwtService jwtService,
        JdbcTemplate jdbcTemplate,
        BusinessProfileFormService businessProfileFormService,
        BusinessProfilePersistenceService businessProfilePersistenceService,
        AiProfileRadarPendingService aiProfileRadarPendingService
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.businessProfileFormService = businessProfileFormService;
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.aiProfileRadarPendingService = aiProfileRadarPendingService;
    }

    @Transactional
    public AiChatResponses.ArchiveFormMutationResponse saveArchiveForm(
        String authorizationHeader,
        String sessionId,
        Map<String, Object> archiveForm
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        if (BUSY_STAGES.contains(StrUtil.nullToEmpty(session.currentStage()))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "current profile is still processing, please retry after it finishes"
            );
        }

        Map<String, Object> previousSnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);
        businessProfilePersistenceService.persistArchiveFormSnapshot(archiveForm, studentId);
        Map<String, Object> currentSnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);

        aiProfileRadarPendingService.accumulateArchiveFormChanges(
            studentId,
            sessionId,
            StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
            previousSnapshot,
            currentSnapshot
        );
        updateSessionStage(studentId, sessionId, "build_ready");

        Map<String, Object> formBundle = businessProfileFormService.loadBusinessProfileFormBundle(studentId);
        Map<String, Object> archiveFormSnapshot = toMapOrEmpty(formBundle.get("archive_form"));
        Map<String, Object> formMeta = toMapOrEmpty(formBundle.get("form_meta"));
        AiChatProfileResultRow refreshedResult = findProfileResultBySessionId(sessionId);
        if (refreshedResult != null) {
            ensureProfileResultBelongsToStudent(refreshedResult, studentId);
        }

        return new AiChatResponses.ArchiveFormMutationResponse(
            sessionId,
            archiveFormSnapshot,
            formMeta,
            refreshedResult == null ? null : refreshedResult.profileResultId(),
            refreshedResult == null ? null : refreshedResult.resultStatus(),
            refreshedResult == null ? null : refreshedResult.profileJson(),
            refreshedResult == null ? null : refreshedResult.summaryText(),
            refreshedResult == null ? Collections.emptyMap() : refreshedResult.radarScoresJson(),
            refreshedResult == null ? null : refreshedResult.dbPayloadJson(),
            refreshedResult == null ? null : refreshedResult.saveErrorMessage(),
            refreshedResult == null ? null : formatDateTime(refreshedResult.createTime()),
            refreshedResult == null ? null : formatDateTime(refreshedResult.updateTime())
        );
    }

    private AiChatSessionRow requireOwnedSession(String studentId, String sessionId) {
        AiChatSessionRow session = findSessionById(sessionId);
        if (session == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "session not found");
        }
        if (!StrUtil.equals(session.studentId(), studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "session does not belong to current student");
        }
        return session;
    }

    private void ensureProfileResultBelongsToStudent(AiChatProfileResultRow result, String studentId) {
        if (!StrUtil.equals(result.studentId(), studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "profile result does not belong to current student");
        }
    }

    private AiChatSessionRow findSessionById(String sessionId) {
        return jdbcTemplate.query(
            """
            SELECT session_id, student_id, biz_domain, current_stage
            FROM ai_chat_sessions
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            AI_CHAT_SESSION_ROW_MAPPER,
            sessionId
        ).stream().findFirst().orElse(null);
    }

    private void updateSessionStage(String studentId, String sessionId, String currentStage) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
            """
            UPDATE ai_chat_sessions
            SET current_stage = ?,
                session_status = 'active',
                remark = NULL,
                updated_by = ?,
                update_time = ?
            WHERE session_id = ?
              AND student_id = ?
              AND delete_flag = '1'
            """,
            currentStage,
            studentId,
            Timestamp.valueOf(now),
            sessionId,
            studentId
        );
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        return jdbcTemplate.query(
            """
            SELECT id, session_id, student_id, result_status, profile_json, radar_scores_json,
                   summary_text, db_payload_json, save_error_message, create_time, update_time
            FROM ai_chat_profile_results
            WHERE session_id = ?
              AND delete_flag = '1'
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """,
            AI_CHAT_PROFILE_RESULT_ROW_MAPPER,
            sessionId
        ).stream().findFirst().orElse(null);
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp value = rs.getTimestamp(columnName);
        return value == null ? null : value.toLocalDateTime();
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String rawJson, Map<String, Object> defaultValue) {
        if (StrUtil.isBlank(rawJson)) {
            return defaultValue;
        }
        try {
            Object parsed = JSONUtil.parse(rawJson);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject.toBean(LinkedHashMap.class);
            }
            if (parsed instanceof JSONArray) {
                return defaultValue;
            }
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {
            return defaultValue;
        }
        return defaultValue;
    }

    private record AiChatSessionRow(
        String sessionId,
        String studentId,
        String bizDomain,
        String currentStage
    ) {
    }

    private record AiChatProfileResultRow(
        long profileResultId,
        String sessionId,
        String studentId,
        String resultStatus,
        Map<String, Object> profileJson,
        Map<String, Object> radarScoresJson,
        String summaryText,
        Map<String, Object> dbPayloadJson,
        String saveErrorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    private static final RowMapper<AiChatSessionRow> AI_CHAT_SESSION_ROW_MAPPER = (rs, rowNum) ->
        new AiChatSessionRow(
            rs.getString("session_id"),
            rs.getString("student_id"),
            rs.getString("biz_domain"),
            rs.getString("current_stage")
        );

    private static final RowMapper<AiChatProfileResultRow> AI_CHAT_PROFILE_RESULT_ROW_MAPPER = (rs, rowNum) ->
        new AiChatProfileResultRow(
            rs.getLong("id"),
            rs.getString("session_id"),
            rs.getString("student_id"),
            rs.getString("result_status"),
            parseJsonObject(rs.getString("profile_json"), Collections.emptyMap()),
            parseJsonObject(rs.getString("radar_scores_json"), Collections.emptyMap()),
            rs.getString("summary_text"),
            parseJsonObject(rs.getString("db_payload_json"), null),
            rs.getString("save_error_message"),
            toLocalDateTime(rs, "create_time"),
            toLocalDateTime(rs, "update_time")
        );
}
