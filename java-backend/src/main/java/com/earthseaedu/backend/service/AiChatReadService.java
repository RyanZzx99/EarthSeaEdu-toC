package com.earthseaedu.backend.service;

import cn.hutool.core.util.ObjectUtil;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChatReadService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final String DEFAULT_SESSION_STATUS = "active";
    private static final String INITIAL_STAGE = "idle";

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final BusinessProfileFormService businessProfileFormService;

    public AiChatReadService(
        JwtService jwtService,
        JdbcTemplate jdbcTemplate,
        BusinessProfileFormService businessProfileFormService
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.businessProfileFormService = businessProfileFormService;
    }

    public AiChatResponses.CurrentSessionEnvelope getCurrentSession(
        String authorizationHeader,
        String bizDomain,
        Integer createIfMissing
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        String resolvedBizDomain = StrUtil.blankToDefault(StrUtil.trim(bizDomain), DEFAULT_BIZ_DOMAIN);
        AiChatSessionRow session = findActiveSessionByStudentAndDomain(studentId, resolvedBizDomain);
        boolean shouldCreate = ObjectUtil.defaultIfNull(createIfMissing, 0) == 1;

        if (session == null && shouldCreate) {
            String sessionId = UUID.randomUUID().toString();
            createSession(studentId, sessionId, resolvedBizDomain);
            return new AiChatResponses.CurrentSessionEnvelope(
                true,
                new AiChatResponses.CurrentSessionSummary(
                    sessionId,
                    DEFAULT_SESSION_STATUS,
                    INITIAL_STAGE,
                    0,
                    List.of(),
                    null,
                    Boolean.TRUE
                )
            );
        }

        if (session == null) {
            return new AiChatResponses.CurrentSessionEnvelope(false, null);
        }

        return new AiChatResponses.CurrentSessionEnvelope(true, toCurrentSessionSummary(session, null));
    }

    public AiChatResponses.SessionDetailResponse getSessionDetail(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        return new AiChatResponses.SessionDetailResponse(
            session.sessionId(),
            session.studentId(),
            session.bizDomain(),
            session.sessionStatus(),
            session.currentStage(),
            session.currentRound(),
            session.missingDimensions(),
            formatDateTime(session.lastMessageAt()),
            formatDateTime(session.completedAt()),
            session.finalProfileId(),
            session.remark()
        );
    }

    public AiChatResponses.MessageListResponse getMessages(
        String authorizationHeader,
        String sessionId,
        Integer limit,
        Long beforeId
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        requireOwnedSession(studentId, sessionId);

        int safeLimit = Math.max(1, Math.min(ObjectUtil.defaultIfNull(limit, 20), 100));
        List<AiChatMessageRow> rows;
        if (beforeId == null) {
            rows = jdbcTemplate.query(
                """
                SELECT id, message_role, message_type, sequence_no, content, create_time
                FROM ai_chat_messages
                WHERE session_id = ?
                  AND delete_flag = '1'
                  AND is_visible = 1
                ORDER BY id DESC
                LIMIT ?
                """,
                AI_CHAT_MESSAGE_ROW_MAPPER,
                sessionId,
                safeLimit
            );
        } else {
            rows = jdbcTemplate.query(
                """
                SELECT id, message_role, message_type, sequence_no, content, create_time
                FROM ai_chat_messages
                WHERE session_id = ?
                  AND delete_flag = '1'
                  AND is_visible = 1
                  AND id < ?
                ORDER BY id DESC
                LIMIT ?
                """,
                AI_CHAT_MESSAGE_ROW_MAPPER,
                sessionId,
                beforeId,
                safeLimit
            );
        }

        Collections.reverse(rows);
        List<AiChatResponses.MessageItem> items = new ArrayList<>(rows.size());
        for (AiChatMessageRow row : rows) {
            items.add(
                new AiChatResponses.MessageItem(
                    row.id(),
                    row.messageRole(),
                    row.messageType(),
                    row.sequenceNo(),
                    row.content(),
                    formatDateTime(row.createTime())
                )
            );
        }

        return new AiChatResponses.MessageListResponse(items, items.size() == safeLimit);
    }

    public AiChatResponses.ProfileResultResponse getResult(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatProfileResultRow result = findProfileResultBySessionId(sessionId);
        if (result == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "profile result not found");
        }
        ensureProfileResultBelongsToStudent(result, studentId);
        return new AiChatResponses.ProfileResultResponse(
            result.sessionId(),
            result.profileResultId(),
            result.resultStatus(),
            result.profileJson(),
            result.summaryText(),
            result.radarScoresJson(),
            result.dbPayloadJson(),
            result.saveErrorMessage(),
            formatDateTime(result.createTime()),
            formatDateTime(result.updateTime())
        );
    }

    public AiChatResponses.RadarResponse getRadar(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatProfileResultRow result = findProfileResultBySessionId(sessionId);
        if (result == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "radar result not found");
        }
        ensureProfileResultBelongsToStudent(result, studentId);
        return new AiChatResponses.RadarResponse(
            result.sessionId(),
            result.profileResultId(),
            result.resultStatus(),
            result.radarScoresJson()
        );
    }

    public AiChatResponses.ArchiveFormResponse getArchiveForm(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        requireOwnedSession(studentId, sessionId);

        Map<String, Object> formBundle = businessProfileFormService.loadBusinessProfileFormBundle(studentId);
        Map<String, Object> archiveForm = toMapOrEmpty(formBundle.get("archive_form"));
        Map<String, Object> formMeta = toMapOrEmpty(formBundle.get("form_meta"));

        AiChatProfileResultRow refreshedResult = findProfileResultBySessionId(sessionId);
        if (refreshedResult != null) {
            ensureProfileResultBelongsToStudent(refreshedResult, studentId);
            return new AiChatResponses.ArchiveFormResponse(
                sessionId,
                archiveForm,
                formMeta,
                refreshedResult.resultStatus(),
                refreshedResult.summaryText(),
                refreshedResult.radarScoresJson(),
                refreshedResult.saveErrorMessage(),
                formatDateTime(refreshedResult.createTime()),
                formatDateTime(refreshedResult.updateTime())
            );
        }

        GuidedProfileResultRow guidedResult = findLatestGuidedResultByStudentId(studentId);
        return new AiChatResponses.ArchiveFormResponse(
            sessionId,
            archiveForm,
            formMeta,
            guidedResult == null ? null : guidedResult.resultStatus(),
            guidedResult == null ? null : guidedResult.summaryText(),
            guidedResult == null ? Collections.emptyMap() : guidedResult.radarScoresJson(),
            guidedResult == null ? null : guidedResult.saveErrorMessage(),
            guidedResult == null ? null : formatDateTime(guidedResult.createTime()),
            guidedResult == null ? null : formatDateTime(guidedResult.updateTime())
        );
    }

    @Transactional
    protected void createSession(String studentId, String sessionId, String bizDomain) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
            """
            INSERT INTO ai_chat_sessions (
                session_id, student_id, biz_domain, session_status, current_stage, current_round,
                collected_slots_json, missing_dimensions_json, last_message_at,
                created_by, updated_by, create_time, update_time, delete_flag
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1')
            """,
            sessionId,
            studentId,
            bizDomain,
            DEFAULT_SESSION_STATUS,
            INITIAL_STAGE,
            0,
            "{}",
            "[]",
            Timestamp.valueOf(now),
            studentId,
            studentId,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now)
        );
    }

    private AiChatResponses.CurrentSessionSummary toCurrentSessionSummary(
        AiChatSessionRow session,
        Boolean isNewSession
    ) {
        return new AiChatResponses.CurrentSessionSummary(
            session.sessionId(),
            session.sessionStatus(),
            session.currentStage(),
            session.currentRound(),
            session.missingDimensions(),
            formatDateTime(session.lastMessageAt()),
            isNewSession
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

    private AiChatSessionRow findActiveSessionByStudentAndDomain(String studentId, String bizDomain) {
        List<AiChatSessionRow> rows = jdbcTemplate.query(
            """
            SELECT session_id, student_id, biz_domain, session_status, current_stage, current_round,
                   missing_dimensions_json, last_message_at, completed_at, final_profile_id, remark
            FROM ai_chat_sessions
            WHERE student_id = ?
              AND biz_domain = ?
              AND session_status = 'active'
              AND delete_flag = '1'
            ORDER BY update_time DESC
            LIMIT 1
            """,
            AI_CHAT_SESSION_ROW_MAPPER,
            studentId,
            bizDomain
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AiChatSessionRow findSessionById(String sessionId) {
        List<AiChatSessionRow> rows = jdbcTemplate.query(
            """
            SELECT session_id, student_id, biz_domain, session_status, current_stage, current_round,
                   missing_dimensions_json, last_message_at, completed_at, final_profile_id, remark
            FROM ai_chat_sessions
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            AI_CHAT_SESSION_ROW_MAPPER,
            sessionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        List<AiChatProfileResultRow> rows = jdbcTemplate.query(
            """
            SELECT id, session_id, student_id, result_status, profile_json, radar_scores_json,
                   summary_text, db_payload_json, save_error_message, create_time, update_time
            FROM ai_chat_profile_results
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            AI_CHAT_PROFILE_RESULT_ROW_MAPPER,
            sessionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private GuidedProfileResultRow findLatestGuidedResultByStudentId(String studentId) {
        List<GuidedProfileResultRow> rows = jdbcTemplate.query(
            """
            SELECT student_id, result_status, radar_scores_json, summary_text,
                   save_error_message, create_time, update_time
            FROM student_profile_guided_results
            WHERE student_id = ?
              AND delete_flag = '1'
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """,
            GUIDED_PROFILE_RESULT_ROW_MAPPER,
            studentId
        );
        return rows.isEmpty() ? null : rows.get(0);
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
    private static List<Object> toListOrEmpty(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String rawJson, Map<String, Object> defaultValue) {
        Object parsed = parseJsonValue(rawJson);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return defaultValue;
    }

    private static List<Object> parseJsonArray(String rawJson, List<Object> defaultValue) {
        Object parsed = parseJsonValue(rawJson);
        return parsed instanceof List<?> ? toListOrEmpty(parsed) : defaultValue;
    }

    private static Object parseJsonValue(String rawJson) {
        if (StrUtil.isBlank(rawJson)) {
            return null;
        }
        try {
            Object parsed = JSONUtil.parse(rawJson);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject.toBean(LinkedHashMap.class);
            }
            if (parsed instanceof JSONArray jsonArray) {
                return jsonArray.toList(Object.class);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private record AiChatSessionRow(
        String sessionId,
        String studentId,
        String bizDomain,
        String sessionStatus,
        String currentStage,
        int currentRound,
        List<Object> missingDimensions,
        LocalDateTime lastMessageAt,
        LocalDateTime completedAt,
        Long finalProfileId,
        String remark
    ) {
    }

    private record AiChatMessageRow(
        long id,
        String messageRole,
        String messageType,
        int sequenceNo,
        String content,
        LocalDateTime createTime
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

    private record GuidedProfileResultRow(
        String studentId,
        String resultStatus,
        Map<String, Object> radarScoresJson,
        String summaryText,
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
            rs.getString("session_status"),
            rs.getString("current_stage"),
            rs.getInt("current_round"),
            parseJsonArray(rs.getString("missing_dimensions_json"), Collections.emptyList()),
            toLocalDateTime(rs, "last_message_at"),
            toLocalDateTime(rs, "completed_at"),
            (Long) rs.getObject("final_profile_id"),
            rs.getString("remark")
        );

    private static final RowMapper<AiChatMessageRow> AI_CHAT_MESSAGE_ROW_MAPPER = (rs, rowNum) ->
        new AiChatMessageRow(
            rs.getLong("id"),
            rs.getString("message_role"),
            rs.getString("message_type"),
            rs.getInt("sequence_no"),
            rs.getString("content"),
            toLocalDateTime(rs, "create_time")
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

    private static final RowMapper<GuidedProfileResultRow> GUIDED_PROFILE_RESULT_ROW_MAPPER = (rs, rowNum) ->
        new GuidedProfileResultRow(
            rs.getString("student_id"),
            rs.getString("result_status"),
            parseJsonObject(rs.getString("radar_scores_json"), Collections.emptyMap()),
            rs.getString("summary_text"),
            rs.getString("save_error_message"),
            toLocalDateTime(rs, "create_time"),
            toLocalDateTime(rs, "update_time")
        );
}
