package com.earthseaedu.backend.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.earthseaedu.backend.dto.teacher.TeacherResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.UserMapper;
import com.earthseaedu.backend.model.auth.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TeacherArchiveService {

    private final UserMapper userMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TeacherArchiveService(
        UserMapper userMapper,
        BusinessProfileFormService businessProfileFormService,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT session_id, result_status, radar_scores_json, summary_text, save_error_message, create_time, update_time
            FROM ai_chat_profile_results
            WHERE student_id = ?
              AND delete_flag = '1'
            ORDER BY update_time DESC, create_time DESC, id DESC
            LIMIT 1
            """,
            studentId
        );
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("radar_scores_json", Map.of());
            return empty;
        }

        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        row.put("radar_scores_json", parseJsonValue(row.get("radar_scores_json")));
        row.put("create_time", normalizeScalar(row.get("create_time")));
        row.put("update_time", normalizeScalar(row.get("update_time")));
        return row;
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
