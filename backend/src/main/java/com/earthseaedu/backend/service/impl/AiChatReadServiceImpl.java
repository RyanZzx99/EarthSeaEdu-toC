package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.service.AiChatReadService;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiChatReadServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiChatReadServiceImpl implements AiChatReadService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final String DEFAULT_SESSION_STATUS = "active";
    private static final String INITIAL_STAGE = "idle";

    private final JwtService jwtService;
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final BusinessProfileFormService businessProfileFormService;

    /**
     * 创建 AiChatReadServiceImpl 实例。
     */
    public AiChatReadServiceImpl(
        JwtService jwtService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        BusinessProfileFormService businessProfileFormService
    ) {
        this.jwtService = jwtService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.businessProfileFormService = businessProfileFormService;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public AiChatResponses.MessageListResponse getMessages(
        String authorizationHeader,
        String sessionId,
        Integer limit,
        Long beforeId
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        requireOwnedSession(studentId, sessionId);

        int safeLimit = Math.max(1, Math.min(ObjectUtil.defaultIfNull(limit, 20), 100));
        List<AiChatMessageRow> rows = new ArrayList<>(aiChatPersistenceMapper.listVisibleMessages(sessionId, safeLimit, beforeId)
            .stream()
            .map(AiChatReadServiceImpl::toMessageRow)
            .toList());

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

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
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
        return toSessionRow(aiChatPersistenceMapper.findActiveSessionByStudentAndDomain(studentId, bizDomain));
    }

    private AiChatSessionRow findSessionById(String sessionId) {
        return toSessionRow(aiChatPersistenceMapper.findSessionById(sessionId));
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        return toProfileResultRow(aiChatPersistenceMapper.findProfileResultBySessionId(sessionId));
    }

    private GuidedProfileResultRow findLatestGuidedResultByStudentId(String studentId) {
        return toGuidedProfileResultRow(aiChatPersistenceMapper.findLatestGuidedResultByStudentId(studentId));
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
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
    private static Map<String, Object> parseJsonObject(Object rawJson, Map<String, Object> defaultValue) {
        if (rawJson instanceof Map<?, ?> map) {
            return toMutableMap(map);
        }
        Object parsed = parseJsonValue(jsonText(rawJson));
        if (parsed instanceof Map<?, ?> map) {
            return toMutableMap(map);
        }
        return defaultValue;
    }

    private static List<Object> parseJsonArray(Object rawJson, List<Object> defaultValue) {
        if (rawJson instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        Object parsed = parseJsonValue(jsonText(rawJson));
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
            stringValue(column(row, "session_status")),
            stringValue(column(row, "current_stage")),
            intValue(column(row, "current_round")),
            parseJsonArray(column(row, "missing_dimensions_json"), Collections.emptyList()),
            toLocalDateTime(column(row, "last_message_at")),
            toLocalDateTime(column(row, "completed_at")),
            column(row, "final_profile_id") == null ? null : longValue(column(row, "final_profile_id")),
            stringValue(column(row, "remark"))
        );
    }

    private static AiChatMessageRow toMessageRow(Map<String, Object> row) {
        return new AiChatMessageRow(
            longValue(column(row, "id")),
            stringValue(column(row, "message_role")),
            stringValue(column(row, "message_type")),
            intValue(column(row, "sequence_no")),
            stringValue(column(row, "content")),
            toLocalDateTime(column(row, "create_time"))
        );
    }

    private static AiChatProfileResultRow toProfileResultRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatProfileResultRow(
            longValue(column(row, "id")),
            stringValue(column(row, "session_id")),
            stringValue(column(row, "student_id")),
            stringValue(column(row, "result_status")),
            parseJsonObject(column(row, "profile_json"), Collections.emptyMap()),
            parseJsonObject(column(row, "radar_scores_json"), Collections.emptyMap()),
            stringValue(column(row, "summary_text")),
            parseJsonObject(column(row, "db_payload_json"), null),
            stringValue(column(row, "save_error_message")),
            toLocalDateTime(column(row, "create_time")),
            toLocalDateTime(column(row, "update_time"))
        );
    }

    private static GuidedProfileResultRow toGuidedProfileResultRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new GuidedProfileResultRow(
            stringValue(column(row, "student_id")),
            stringValue(column(row, "result_status")),
            parseJsonObject(column(row, "radar_scores_json"), Collections.emptyMap()),
            stringValue(column(row, "summary_text")),
            stringValue(column(row, "save_error_message")),
            toLocalDateTime(column(row, "create_time")),
            toLocalDateTime(column(row, "update_time"))
        );
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

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
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

    private static Map<String, Object> toMutableMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
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

}
