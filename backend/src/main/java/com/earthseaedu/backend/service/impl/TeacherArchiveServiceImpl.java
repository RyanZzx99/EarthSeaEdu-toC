package com.earthseaedu.backend.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.earthseaedu.backend.dto.teacher.TeacherResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.mapper.UserMapper;
import com.earthseaedu.backend.model.auth.User;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.TeacherArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * TeacherArchiveServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class TeacherArchiveServiceImpl implements TeacherArchiveService {

    private final UserMapper userMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 TeacherArchiveServiceImpl 实例。
     */
    public TeacherArchiveServiceImpl(
        UserMapper userMapper,
        BusinessProfileFormService businessProfileFormService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    public TeacherResponses.TeacherStudentArchiveLookupResponse loadTeacherStudentArchiveBundle(
        String teacherUserId,
        String keyword
    ) {
        requireActiveTeacherUser(teacherUserId);
        User student = resolveTargetStudent(keyword);
        Map<String, Object> formBundle = businessProfileFormService.loadBusinessProfileFormBundle(student.getId());
        Map<String, Object> latestResult = loadLatestProfileResult(student.getId());

        return new TeacherResponses.TeacherStudentArchiveLookupResponse(
            new TeacherResponses.TeacherStudentSummary(
                student.getId(),
                student.getMobile(),
                student.getNickname(),
                student.getStatus()
            ),
            stringValue(latestResult.get("session_id")),
            castMap(formBundle.get("archive_form")),
            castMap(formBundle.get("form_meta")),
            stringValue(latestResult.get("result_status")),
            stringValue(latestResult.get("summary_text")),
            castMap(latestResult.get("radar_scores_json")),
            stringValue(latestResult.get("save_error_message")),
            stringValue(latestResult.get("create_time")),
            stringValue(latestResult.get("update_time"))
        );
    }

    private void requireActiveTeacherUser(String teacherUserId) {
        User user = userMapper.findActiveById(teacherUserId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Current account is unavailable");
        }
        if (!"1".equals(user.getIsTeacher())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Teacher portal is not enabled");
        }
    }

    private User resolveTargetStudent(String keyword) {
        String normalizedKeyword = CharSequenceUtil.trim(keyword);
        if (CharSequenceUtil.isBlank(normalizedKeyword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "keyword is required");
        }

        User student = userMapper.findActiveById(normalizedKeyword);
        if (student == null) {
            student = userMapper.findActiveByMobile(normalizedKeyword);
        }
        if (student == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Student not found");
        }
        return student;
    }

    private Map<String, Object> loadLatestProfileResult(String studentId) {
        Map<String, Object> resultRow = aiChatPersistenceMapper.findLatestProfileResultByStudentId(studentId);
        if (resultRow == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("radar_scores_json", Map.of());
            return empty;
        }

        Map<String, Object> row = new LinkedHashMap<>(resultRow);
        row.put("session_id", column(row, "session_id"));
        row.put("result_status", column(row, "result_status"));
        row.put("summary_text", column(row, "summary_text"));
        row.put("save_error_message", column(row, "save_error_message"));
        row.put("radar_scores_json", parseJsonValue(column(row, "radar_scores_json")));
        row.put("create_time", normalizeScalar(column(row, "create_time")));
        row.put("update_time", normalizeScalar(column(row, "update_time")));
        return row;
    }

    private Object column(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        return row.get(snakeToCamel(columnName));
    }

    private String snakeToCamel(String value) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Map.of();
    }

    private Object parseJsonValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        String text = String.valueOf(value);
        if (CharSequenceUtil.isBlank(text)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        Object normalized = normalizeScalar(value);
        return normalized == null ? null : String.valueOf(normalized);
    }

    private Object normalizeScalar(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }
}
