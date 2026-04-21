package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.service.AiChatDraftService;
import com.earthseaedu.backend.service.AiChatRealtimeService;
import com.earthseaedu.backend.service.AiChatRealtimeService.ConnectionContext;
import com.earthseaedu.backend.service.AiChatRealtimeService.ConnectionInitResult;
import com.earthseaedu.backend.service.AiChatRealtimeService.SavedMessage;
import com.earthseaedu.backend.service.AiPromptRuntimeService;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.JwtService;
import java.nio.charset.StandardCharsets;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiChatRealtimeServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiChatRealtimeServiceImpl implements AiChatRealtimeService {

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
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final AiChatDraftService aiChatDraftService;
    private final AiPromptRuntimeService aiPromptRuntimeService;

    /**
     * 创建 AiChatRealtimeServiceImpl 实例。
     */
    public AiChatRealtimeServiceImpl(
        JwtService jwtService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        BusinessProfileFormService businessProfileFormService,
        AiChatDraftService aiChatDraftService,
        AiPromptRuntimeService aiPromptRuntimeService
    ) {
        this.jwtService = jwtService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.aiChatDraftService = aiChatDraftService;
        this.aiPromptRuntimeService = aiPromptRuntimeService;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
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
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", context.sessionId());
        row.put("studentId", context.studentId());
        row.put("currentRound", nextRound);
        row.put("lastMessageAt", Timestamp.valueOf(now));
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionAfterUserMessage(row);
        return new SavedMessage(messageId, nextSequenceNo, nextRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
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
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", context.sessionId());
        row.put("studentId", context.studentId());
        row.put("lastMessageAt", Timestamp.valueOf(now));
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionAfterAssistantMessage(row);
        return new SavedMessage(messageId, nextSequenceNo, currentRound(context.sessionId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> extractLatestPatch(ConnectionContext context) {
        return aiChatDraftService.extractLatestPatch(context.authorizationHeader(), context.sessionId());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Map<String, Object> setStage(
        ConnectionContext context,
        String stage,
        String conversationPhase
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", context.sessionId());
        row.put("studentId", context.studentId());
        row.put("currentStage", stage);
        row.put("remark", null);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionStage(row);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current_stage", stage);
        payload.put("conversation_phase", conversationPhase);
        payload.put("current_round", currentRound(context.sessionId()));
        return payload;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
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
        return toSessionRow(aiChatPersistenceMapper.findActiveSessionByStudentAndDomain(studentId, bizDomain));
    }

    private AiChatSessionRow findSessionById(String sessionId) {
        return toSessionRow(aiChatPersistenceMapper.findSessionById(sessionId));
    }

    private void createSession(String studentId, String sessionId, String bizDomain) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        aiChatPersistenceMapper.insertSession(sessionMutationRow(
            studentId,
            sessionId,
            bizDomain,
            DEFAULT_SESSION_STATUS,
            INITIAL_STAGE,
            0,
            now
        ));
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
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("messageRole", role);
        row.put("messageType", messageType);
        row.put("sequenceNo", sequenceNo);
        row.put("content", content);
        row.put("contentJson", contentJson == null ? null : JSONUtil.toJsonStr(contentJson));
        row.put("isVisible", isVisible);
        row.put("streamChunkCount", streamChunkCount);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.insertMessage(row);
        Number key = row.get("id") instanceof Number number ? number : null;
        return key == null ? 0L : key.longValue();
    }

    private int nextSequenceNo(String sessionId) {
        Integer value = aiChatPersistenceMapper.selectNextSequenceNo(sessionId);
        return value == null ? 1 : value;
    }

    private int currentRound(String sessionId) {
        Integer value = aiChatPersistenceMapper.selectCurrentRound(sessionId);
        return value == null ? 0 : value;
    }

    private List<Map<String, Object>> loadRecentVisibleMessages(String sessionId, int limit) {
        List<Map<String, Object>> rows = aiChatPersistenceMapper.listRecentVisibleMessages(
            sessionId,
            Math.max(1, Math.min(limit, 100))
        );
        Collections.reverse(rows);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("message_role", column(row, "message_role"));
            item.put("message_type", column(row, "message_type"));
            item.put("sequence_no", column(row, "sequence_no"));
            item.put("content", column(row, "content"));
            item.put("create_time", column(row, "create_time") == null ? null : String.valueOf(column(row, "create_time")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> loadLatestProgressState(String sessionId) {
        Map<String, Object> row = aiChatPersistenceMapper.findLatestProgressState(sessionId);
        if (row == null) {
            return Collections.emptyMap();
        }
        Object parsed = parseJsonValue(jsonText(column(row, "content_json")));
        return parsed instanceof Map<?, ?> map ? toMutableMap(map) : Collections.emptyMap();
    }

    private Map<String, Object> sessionMutationRow(
        String studentId,
        String sessionId,
        String bizDomain,
        String sessionStatus,
        String currentStage,
        int currentRound,
        LocalDateTime now
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("bizDomain", bizDomain);
        row.put("sessionStatus", sessionStatus);
        row.put("currentStage", currentStage);
        row.put("currentRound", currentRound);
        row.put("collectedSlotsJson", "{}");
        row.put("missingDimensionsJson", "[]");
        row.put("lastMessageAt", Timestamp.valueOf(now));
        row.put("createdBy", studentId);
        row.put("updatedBy", studentId);
        row.put("now", Timestamp.valueOf(now));
        return row;
    }

    private static AiChatSessionRow toSessionRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatSessionRow(
            stringValue(column(row, "session_id")),
            stringValue(column(row, "student_id")),
            stringValue(column(row, "biz_domain")),
            stringValue(column(row, "current_stage")),
            intValue(column(row, "current_round")),
            parseJsonArray(column(row, "missing_dimensions_json"))
        );
    }

    private static List<Object> parseJsonArray(Object rawJson) {
        if (rawJson instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        Object parsed = parseJsonValue(jsonText(rawJson));
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

    private static Object column(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        return row.get(snakeToCamel(columnName));
    }

    private static String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String jsonText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
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
