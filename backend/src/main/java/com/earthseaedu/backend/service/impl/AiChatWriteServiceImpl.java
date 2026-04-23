package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.service.AiChatWriteService;
import com.earthseaedu.backend.service.AiProfileRadarPendingService;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.BusinessProfilePersistenceService;
import com.earthseaedu.backend.service.JwtService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiChatWriteServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiChatWriteServiceImpl implements AiChatWriteService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final Set<String> BUSY_STAGES = Set.of(
        "progress_updating",
        "extraction",
        "scoring",
        "profile_saving"
    );

    private final JwtService jwtService;
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final AiProfileRadarPendingService aiProfileRadarPendingService;

    /**
     * 创建 AiChatWriteServiceImpl 实例。
     */
    public AiChatWriteServiceImpl(
        JwtService jwtService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        BusinessProfileFormService businessProfileFormService,
        BusinessProfilePersistenceService businessProfilePersistenceService,
        AiProfileRadarPendingService aiProfileRadarPendingService
    ) {
        this.jwtService = jwtService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.aiProfileRadarPendingService = aiProfileRadarPendingService;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
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
        return toSessionRow(aiChatPersistenceMapper.findSessionById(sessionId));
    }

    private void updateSessionStage(String studentId, String sessionId, String currentStage) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("currentStage", currentStage);
        row.put("remark", null);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionStage(row);
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        return toProfileResultRow(aiChatPersistenceMapper.findLatestProfileResultBySessionId(sessionId));
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
    private static Map<String, Object> parseJsonObject(Object rawJson, Map<String, Object> defaultValue) {
        if (rawJson instanceof Map<?, ?> map) {
            return toMutableMap(map);
        }
        String jsonText = jsonText(rawJson);
        if (StrUtil.isBlank(jsonText)) {
            return defaultValue;
        }
        try {
            Object parsed = JSONUtil.parse(jsonText);
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

    private static AiChatSessionRow toSessionRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatSessionRow(
            stringValue(column(row, "session_id")),
            stringValue(column(row, "student_id")),
            stringValue(column(row, "biz_domain")),
            stringValue(column(row, "current_stage"))
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

}
