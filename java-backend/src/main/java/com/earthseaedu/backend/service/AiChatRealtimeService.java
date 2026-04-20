package com.earthseaedu.backend.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChatRealtimeService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final String DEFAULT_SESSION_STATUS = "active";
    private static final String INITIAL_STAGE = "idle";
    private static final String CONVERSATION_PROMPT_KEY = "student_profile_build.conversation";
    private static final Set<String> BUSY_STAGES = Set.of(
        "progress_updating",
        "extraction",
        "scoring",
        "profile_saving"
    );

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final BusinessProfileFormService businessProfileFormService;
    private final AiChatDraftService aiChatDraftService;
    private final AiPromptRuntimeService aiPromptRuntimeService;

    public AiChatRealtimeService(
        JwtService jwtService,
        JdbcTemplate jdbcTemplate,
        BusinessProfileFormService businessProfileFormService,
        AiChatDraftService aiChatDraftService,
        AiPromptRuntimeService aiPromptRuntimeService
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.businessProfileFormService = businessProfileFormService;
        this.aiChatDraftService = aiChatDraftService;
        this.aiPromptRuntimeService = aiPromptRuntimeService;
    }

    @Transactional
    public ConnectionInitResult connect(
        String accessToken,
        String requestedSessionId,
        String requestedStudentId,
        String bizDomain
    ) {
        if (StrUtil.isBlank(accessToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "access_token is required");
        }
        String authorizationHeader = "Bearer " + accessToken;
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        if (StrUtil.isNotBlank(requestedStudentId) && !StrUtil.equals(studentId, requestedStudentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "student_id does not match access token");
        }

        String resolvedBizDomain = StrUtil.blankToDefault(StrUtil.trim(bizDomain), DEFAULT_BIZ_DOMAIN);
        AiChatSessionRow session;
        if (StrUtil.isNotBlank(requestedSessionId)) {
            session = findSessionById(requestedSessionId);
            if (session == null) {
                createSession(studentId, requestedSessionId, resolvedBizDomain);
                session = findSessionById(requestedSessionId);
            }
        } else {
            session = findActiveSessionByStudentAndDomain(studentId, resolvedBizDomain);
            if (session == null) {
                String sessionId = UUID.randomUUID().toString();
                createSession(studentId, sessionId, resolvedBizDomain);
                session = findSessionById(sessionId);
            }
        }
        if (session == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI chat session was not initialized");
        }
        if (!StrUtil.equals(session.studentId(), studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "session does not belong to current student");
        }

        ConnectionContext context = new ConnectionContext(
            authorizationHeader,
            session.sessionId(),
            studentId,
            StrUtil.blankToDefault(session.bizDomain(), resolvedBizDomain)
        );
        return new ConnectionInitResult(
            context,
            session.currentStage(),
            session.currentRound(),
            session.missingDimensions()
        );
    }

    @Transactional
    public SavedMessage saveUserMessage(ConnectionContext context, String content) {
        String trimmedContent = StrUtil.trim(content);
        if (StrUtil.isBlank(trimmedContent)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "message content is required");
        }
        AiChatSessionRow session = requireOwnedSession(context.studentId(), context.sessionId());
        if (BUSY_STAGES.contains(StrUtil.nullToEmpty(session.currentStage()))) {
            throw new ApiException(HttpStatus.CONFLICT, "current session is still processing");
        }

        int nextSequenceNo = nextSequenceNo(context.sessionId());
        long messageId = insertMessage(
            context.sessionId(),
            context.studentId(),
            "user",
            "visible_text",
            nextSequenceNo,
            trimmedContent,
            null,
            1,
            null
        );
        int nextRound = session.currentRound() + 1;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
            """
            UPDATE ai_chat_sessions
            SET current_round = ?,
                current_stage = 'conversation',
                last_message_at = ?,
                remark = NULL,
                updated_by = ?,
                update_time = ?
            WHERE session_id = ?
              AND student_id = ?
              AND delete_flag = '1'
            """,
            nextRound,
            Timestamp.valueOf(now),
            context.studentId(),
            Timestamp.valueOf(now),
            context.sessionId(),
            context.studentId()
        );
        return new SavedMessage(messageId, nextSequenceNo, nextRound);
    }

    public String generateAssistant(ConnectionContext context, String latestUserText) {
        Map<String, Object> draftDetail = aiChatDraftService.getDraftDetail(context.authorizationHeader(), context.sessionId());
        Map<String, Object> sessionPayload = currentSessionPayload(context.sessionId());
        Map<String, Object> promptContext = new LinkedHashMap<>();
        promptContext.put("student_id", context.studentId());
        promptContext.put("session_id", context.sessionId());
        promptContext.put("biz_domain", context.bizDomain());
        promptContext.put("latest_user_text", StrUtil.trim(latestUserText));
        promptContext.put("conversation_history", loadRecentVisibleMessages(context.sessionId(), 30));
        promptContext.put("current_archive_form_snapshot", businessProfileFormService.loadBusinessProfileSnapshot(context.studentId()));
        promptContext.put("current_draft_json", draftDetail.getOrDefault("draft_json", Map.of()));
        promptContext.put("current_progress_json", loadLatestProgressState(context.sessionId()));
        Map<String, Object> conversationControl = new LinkedHashMap<>();
        Object currentStage = sessionPayload.get("current_stage");
        conversationControl.put("current_stage", StrUtil.blankToDefault(currentStage == null ? null : String.valueOf(currentStage), INITIAL_STAGE));
        conversationControl.put("current_round", sessionPayload.getOrDefault("current_round", 0));
        conversationControl.put("missing_dimensions", sessionPayload.getOrDefault("missing_dimensions", List.of()));
        promptContext.put("conversation_control", conversationControl);

        AiPromptRuntimeService.PromptRuntimeResult runtimeResult = aiPromptRuntimeService.executePrompt(
            CONVERSATION_PROMPT_KEY,
            promptContext,
            List.of("active")
        );
        return StrUtil.trimToEmpty(runtimeResult.content());
    }

    @Transactional
    public SavedMessage saveAssistantMessage(ConnectionContext context, String content, int streamChunkCount) {
        String trimmedContent = StrUtil.blankToDefault(StrUtil.trim(content), "我已收到你的补充信息，可以继续告诉我更多申请背景。");
        int nextSequenceNo = nextSequenceNo(context.sessionId());
        long messageId = insertMessage(
            context.sessionId(),
            context.studentId(),
            "assistant",
            "visible_text",
            nextSequenceNo,
            trimmedContent,
            null,
            1,
            Math.max(1, streamChunkCount)
        );
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
            """
            UPDATE ai_chat_sessions
            SET last_message_at = ?,
                updated_by = ?,
                update_time = ?
            WHERE session_id = ?
              AND student_id = ?
              AND delete_flag = '1'
            """,
            Timestamp.valueOf(now),
            context.studentId(),
            Timestamp.valueOf(now),
            context.sessionId(),
            context.studentId()
        );
        return new SavedMessage(messageId, nextSequenceNo, currentRound(context.sessionId()));
    }

    public Map<String, Object> extractLatestPatch(ConnectionContext context) {
        return aiChatDraftService.extractLatestPatch(context.authorizationHeader(), context.sessionId());
    }

    @Transactional
    public Map<String, Object> setStage(
        ConnectionContext context,
        String stage,
        String conversationPhase
    ) {
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
            stage,
            context.studentId(),
            Timestamp.valueOf(now),
            context.sessionId(),
            context.studentId()
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current_stage", stage);
        payload.put("conversation_phase", conversationPhase);
        payload.put("current_round", currentRound(context.sessionId()));
        return payload;
    }

    public Map<String, Object> currentSessionPayload(String sessionId) {
        AiChatSessionRow session = findSessionById(sessionId);
        if (session == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current_stage", session.currentStage());
        payload.put("current_round", session.currentRound());
        payload.put("missing_dimensions", session.missingDimensions());
        return payload;
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

    private AiChatSessionRow findActiveSessionByStudentAndDomain(String studentId, String bizDomain) {
        List<AiChatSessionRow> rows = jdbcTemplate.query(
            """
            SELECT session_id, student_id, biz_domain, current_stage, current_round, missing_dimensions_json
            FROM ai_chat_sessions
            WHERE student_id = ?
              AND biz_domain = ?
              AND session_status = 'active'
              AND delete_flag = '1'
            ORDER BY update_time DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new AiChatSessionRow(
                rs.getString("session_id"),
                rs.getString("student_id"),
                rs.getString("biz_domain"),
                rs.getString("current_stage"),
                rs.getInt("current_round"),
                parseJsonArray(rs.getString("missing_dimensions_json"))
            ),
            studentId,
            bizDomain
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AiChatSessionRow findSessionById(String sessionId) {
        List<AiChatSessionRow> rows = jdbcTemplate.query(
            """
            SELECT session_id, student_id, biz_domain, current_stage, current_round, missing_dimensions_json
            FROM ai_chat_sessions
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            (rs, rowNum) -> new AiChatSessionRow(
                rs.getString("session_id"),
                rs.getString("student_id"),
                rs.getString("biz_domain"),
                rs.getString("current_stage"),
                rs.getInt("current_round"),
                parseJsonArray(rs.getString("missing_dimensions_json"))
            ),
            sessionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void createSession(String studentId, String sessionId, String bizDomain) {
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

    private long insertMessage(
        String sessionId,
        String studentId,
        String role,
        String messageType,
        int sequenceNo,
        String content,
        Object contentJson,
        int isVisible,
        Integer streamChunkCount
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO ai_chat_messages (
                    session_id, student_id, message_role, message_type, sequence_no,
                    content, content_json, is_visible, stream_chunk_count,
                    create_time, update_time, delete_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1')
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, sessionId);
            statement.setString(2, studentId);
            statement.setString(3, role);
            statement.setString(4, messageType);
            statement.setInt(5, sequenceNo);
            statement.setString(6, content);
            statement.setString(7, contentJson == null ? null : JSONUtil.toJsonStr(contentJson));
            statement.setInt(8, isVisible);
            if (streamChunkCount == null) {
                statement.setObject(9, null);
            } else {
                statement.setInt(9, streamChunkCount);
            }
            statement.setTimestamp(10, Timestamp.valueOf(now));
            statement.setTimestamp(11, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private int nextSequenceNo(String sessionId) {
        Integer value = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(MAX(sequence_no), 0) + 1
            FROM ai_chat_messages
            WHERE session_id = ?
              AND delete_flag = '1'
            """,
            Integer.class,
            sessionId
        );
        return value == null ? 1 : value;
    }

    private int currentRound(String sessionId) {
        Integer value = jdbcTemplate.queryForObject(
            """
            SELECT current_round
            FROM ai_chat_sessions
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            Integer.class,
            sessionId
        );
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> loadRecentVisibleMessages(String sessionId, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT message_role, message_type, sequence_no, content, create_time
            FROM ai_chat_messages
            WHERE session_id = ?
              AND delete_flag = '1'
              AND is_visible = 1
            ORDER BY id DESC
            LIMIT ?
            """,
            sessionId,
            Math.max(1, Math.min(limit, 100))
        );
        Collections.reverse(rows);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("message_role", row.get("message_role"));
            item.put("message_type", row.get("message_type"));
            item.put("sequence_no", row.get("sequence_no"));
            item.put("content", row.get("content"));
            item.put("create_time", row.get("create_time") == null ? null : String.valueOf(row.get("create_time")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> loadLatestProgressState(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT content_json
            FROM ai_chat_messages
            WHERE session_id = ?
              AND delete_flag = '1'
              AND message_role = 'system'
              AND message_type = 'internal_state'
              AND content = 'progress_extraction_result'
            ORDER BY sequence_no DESC, id DESC
            LIMIT 1
            """,
            sessionId
        );
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Object parsed = parseJsonValue(String.valueOf(rows.get(0).get("content_json")));
        return parsed instanceof Map<?, ?> map ? toMutableMap(map) : Collections.emptyMap();
    }

    private static List<Object> parseJsonArray(String rawJson) {
        Object parsed = parseJsonValue(rawJson);
        if (parsed instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return Collections.emptyList();
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
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> toMutableMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    public record ConnectionContext(
        String authorizationHeader,
        String sessionId,
        String studentId,
        String bizDomain
    ) {
    }

    public record ConnectionInitResult(
        ConnectionContext context,
        String currentStage,
        int currentRound,
        List<Object> missingDimensions
    ) {
    }

    public record SavedMessage(
        long messageId,
        int sequenceNo,
        int currentRound
    ) {
    }

    private record AiChatSessionRow(
        String sessionId,
        String studentId,
        String bizDomain,
        String currentStage,
        int currentRound,
        List<Object> missingDimensions
    ) {
    }
}
