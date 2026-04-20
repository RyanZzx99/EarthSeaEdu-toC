package com.earthseaedu.backend.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.earthseaedu.backend.exception.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfilePersistenceService {

    private static final List<String> SINGLE_ROW_TABLES = List.of(
        "student_basic_info",
        "student_academic",
        "student_academic_a_level_profile",
        "student_academic_ap_profile",
        "student_academic_ib_profile",
        "student_academic_chinese_high_school_profile",
        "student_academic_us_high_school_profile",
        "student_academic_other_curriculum_profile",
        "student_language",
        "student_standardized_tests",
        "student_competitions",
        "student_activities",
        "student_projects_experience"
    );

    private static final List<String> MULTI_ROW_TABLES = List.of(
        "student_basic_info_curriculum_system",
        "student_basic_info_target_country_entries",
        "student_basic_info_target_major_entries",
        "student_academic_a_level_subject",
        "student_academic_ap_course",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
        "student_academic_us_high_school_course",
        "student_language_ielts",
        "student_language_toefl_ibt",
        "student_language_toefl_essentials",
        "student_language_det",
        "student_language_pte",
        "student_language_languagecert",
        "student_language_cambridge",
        "student_language_other",
        "student_standardized_test_records",
        "student_competition_entries",
        "student_activity_entries",
        "student_project_entries",
        "student_project_outputs"
    );

    private static final Map<String, String> CURRICULUM_SINGLE_TABLE_BY_CODE = Map.of(
        "A_LEVEL", "student_academic_a_level_profile",
        "AP", "student_academic_ap_profile",
        "IB", "student_academic_ib_profile",
        "CHINESE_HIGH_SCHOOL", "student_academic_chinese_high_school_profile",
        "US_HIGH_SCHOOL", "student_academic_us_high_school_profile",
        "INTERNATIONAL_OTHER", "student_academic_other_curriculum_profile",
        "OTHER", "student_academic_other_curriculum_profile"
    );

    private static final Map<String, String> CURRICULUM_MULTI_TABLE_BY_CODE = Map.of(
        "A_LEVEL", "student_academic_a_level_subject",
        "AP", "student_academic_ap_course",
        "IB", "student_academic_ib_subject",
        "CHINESE_HIGH_SCHOOL", "student_academic_chinese_high_school_subject",
        "US_HIGH_SCHOOL", "student_academic_us_high_school_course"
    );

    private static final Map<String, List<String>> SINGLE_ROW_CHILD_DEPENDENCIES = Map.of(
        "student_academic",
        List.of(
            "student_academic_a_level_profile",
            "student_academic_ap_profile",
            "student_academic_ib_profile",
            "student_academic_chinese_high_school_profile",
            "student_academic_us_high_school_profile",
            "student_academic_other_curriculum_profile",
            "student_academic_a_level_subject",
            "student_academic_ap_course",
            "student_academic_ib_subject",
            "student_academic_chinese_high_school_subject",
            "student_academic_us_high_school_course"
        ),
        "student_language",
        List.of(
            "student_language_ielts",
            "student_language_toefl_ibt",
            "student_language_toefl_essentials",
            "student_language_det",
            "student_language_pte",
            "student_language_languagecert",
            "student_language_cambridge",
            "student_language_other"
        ),
        "student_standardized_tests",
        List.of("student_standardized_test_records"),
        "student_competitions",
        List.of("student_competition_entries"),
        "student_activities",
        List.of("student_activity_entries"),
        "student_projects_experience",
        List.of("student_project_entries", "student_project_outputs")
    );

    private static final Set<String> MEANINGFULNESS_IGNORED_FIELDS = Set.of(
        "student_id",
        "student_academic_id",
        "student_language_id",
        "student_standardized_test_id",
        "competition_id",
        "activity_id",
        "project_id",
        "output_id",
        "project_output_id",
        "schema_version",
        "profile_type",
        "sort_order",
        "is_primary",
        "is_best_score",
        "use_best_single_test",
        "delete_flag",
        "create_time",
        "update_time"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> tableColumnsCache = new ConcurrentHashMap<>();

    public BusinessProfilePersistenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void persistArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId) {
        if (archiveForm == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "archive_form must be an object");
        }

        Map<String, Object> payload = clonePayload(archiveForm);
        ensureTableShapes(payload);
        injectStudentId(payload, studentId);
        fillProgramGeneratedIds(payload, studentId);
        normalizeCurriculumSystemPrimaryFlags(payload);
        pruneAcademicPayloadByCurriculumSystem(payload);
        normalizeProjectIdMapping(payload);

        deleteExistingSnapshot(studentId);
        insertSingleRowTables(payload);
        insertMultiRowTables(payload);
    }

    private void deleteExistingSnapshot(String studentId) {
        if (tableExists("student_project_outputs") && tableExists("student_project_entries")) {
            jdbcTemplate.update(
                """
                DELETE spo
                FROM `student_project_outputs` spo
                INNER JOIN `student_project_entries` spe
                  ON spo.project_id = spe.project_id
                WHERE spe.student_id = ?
                """,
                studentId
            );
        }

        for (String tableName : MULTI_ROW_TABLES) {
            if ("student_project_outputs".equals(tableName) || !tableExists(tableName) || !hasColumn(tableName, "student_id")) {
                continue;
            }
            jdbcTemplate.update("DELETE FROM `" + tableName + "` WHERE student_id = ?", studentId);
        }

        List<String> reversedSingleTables = new ArrayList<>(SINGLE_ROW_TABLES);
        Collections.reverse(reversedSingleTables);
        for (String tableName : reversedSingleTables) {
            if (!tableExists(tableName) || !hasColumn(tableName, "student_id")) {
                continue;
            }
            jdbcTemplate.update("DELETE FROM `" + tableName + "` WHERE student_id = ?", studentId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> clonePayload(Map<String, Object> archiveForm) {
        return objectMapper.convertValue(
            archiveForm,
            new TypeReference<LinkedHashMap<String, Object>>() {
            }
        );
    }

    private void ensureTableShapes(Map<String, Object> payload) {
        for (String tableName : SINGLE_ROW_TABLES) {
            if (!(payload.get(tableName) instanceof Map<?, ?>)) {
                payload.put(tableName, new LinkedHashMap<String, Object>());
            }
        }
        for (String tableName : MULTI_ROW_TABLES) {
            if (!(payload.get(tableName) instanceof List<?>)) {
                payload.put(tableName, new ArrayList<>());
            }
        }
    }

    private void injectStudentId(Map<String, Object> payload, String studentId) {
        for (String tableName : SINGLE_ROW_TABLES) {
            Map<String, Object> row = asMutableMap(payload.get(tableName));
            row.put("student_id", studentId);
            payload.put(tableName, row);
        }

        for (String tableName : MULTI_ROW_TABLES) {
            List<Map<String, Object>> rows = asMutableRows(payload.get(tableName));
            for (Map<String, Object> row : rows) {
                row.put("student_id", studentId);
            }
            payload.put(tableName, rows);
        }
    }

    private void fillProgramGeneratedIds(Map<String, Object> payload, String studentId) {
        Map<String, Object> academic = asMutableMap(payload.get("student_academic"));
        if (isBlankValue(academic.get("student_academic_id"))) {
            academic.put("student_academic_id", "acad_" + UUID.randomUUID());
        }
        payload.put("student_academic", academic);

        Map<String, Object> language = asMutableMap(payload.get("student_language"));
        if (isBlankValue(language.get("student_language_id"))) {
            language.put("student_language_id", "lang_" + UUID.randomUUID());
        }
        payload.put("student_language", language);

        Map<String, Object> standardized = asMutableMap(payload.get("student_standardized_tests"));
        if (isBlankValue(standardized.get("student_standardized_test_id"))) {
            standardized.put("student_standardized_test_id", "std_" + UUID.randomUUID());
        }
        payload.put("student_standardized_tests", standardized);

        Map<String, Object> basicInfo = asMutableMap(payload.get("student_basic_info"));
        basicInfo.put("student_id", studentId);
        if (isBlankValue(basicInfo.get("schema_version"))) {
            basicInfo.put("schema_version", "v1");
        }
        if (isBlankValue(basicInfo.get("profile_type"))) {
            basicInfo.put("profile_type", "student_profile_build");
        }
        payload.put("student_basic_info", basicInfo);
    }

    private void normalizeProjectIdMapping(Map<String, Object> payload) {
        List<Map<String, Object>> projectEntries = asMutableRows(payload.get("student_project_entries"));
        List<Map<String, Object>> projectOutputs = asMutableRows(payload.get("student_project_outputs"));
        if (projectEntries.isEmpty() || projectOutputs.isEmpty()) {
            return;
        }

        List<Long> projectIds = new ArrayList<>();
        for (int index = 0; index < projectEntries.size(); index++) {
            Map<String, Object> row = projectEntries.get(index);
            Long projectId = toLong(row.get("project_id"));
            if (projectId == null) {
                projectId = -1L * (index + 1);
                row.put("project_id", projectId);
            }
            projectIds.add(projectId);
        }

        if (projectIds.size() == 1) {
            Long onlyProjectId = projectIds.get(0);
            for (Map<String, Object> row : projectOutputs) {
                if (toLong(row.get("project_id")) == null) {
                    row.put("project_id", onlyProjectId);
                }
            }
        }
        payload.put("student_project_entries", projectEntries);
        payload.put("student_project_outputs", projectOutputs);
    }

    private void normalizeCurriculumSystemPrimaryFlags(Map<String, Object> payload) {
        List<Map<String, Object>> rows = asMutableRows(payload.get("student_basic_info_curriculum_system"));
        if (rows.isEmpty()) {
            return;
        }

        List<Map<String, Object>> dedupedRows = new ArrayList<>();
        Map<String, Map<String, Object>> rowByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = StrUtil.trimToEmpty(stringValue(row.get("curriculum_system_code")));
            if (StrUtil.isBlank(code)) {
                continue;
            }
            String normalizedCode = code.toUpperCase();
            Map<String, Object> existing = rowByCode.get(normalizedCode);
            if (existing == null) {
                row.put("curriculum_system_code", code);
                rowByCode.put(normalizedCode, row);
                dedupedRows.add(row);
                continue;
            }
            if (toInt(row.get("is_primary")) == 1) {
                existing.put("is_primary", 1);
            }
        }
        payload.put("student_basic_info_curriculum_system", dedupedRows);

        if (dedupedRows.size() == 1 && toInt(dedupedRows.get(0).get("is_primary")) != 1) {
            dedupedRows.get(0).put("is_primary", 1);
        }
    }

    private void pruneAcademicPayloadByCurriculumSystem(Map<String, Object> payload) {
        List<Map<String, Object>> rows = asMutableRows(payload.get("student_basic_info_curriculum_system"));
        Set<String> effectiveCodes = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String code = StrUtil.trimToEmpty(stringValue(row.get("curriculum_system_code"))).toUpperCase();
            if (CURRICULUM_SINGLE_TABLE_BY_CODE.containsKey(code)) {
                effectiveCodes.add(code);
            }
        }
        if (effectiveCodes.isEmpty()) {
            return;
        }

        Set<String> allowedSingleTables = new LinkedHashSet<>();
        Set<String> allowedMultiTables = new LinkedHashSet<>();
        for (String code : effectiveCodes) {
            if (CURRICULUM_SINGLE_TABLE_BY_CODE.containsKey(code)) {
                allowedSingleTables.add(CURRICULUM_SINGLE_TABLE_BY_CODE.get(code));
            }
            if (CURRICULUM_MULTI_TABLE_BY_CODE.containsKey(code)) {
                allowedMultiTables.add(CURRICULUM_MULTI_TABLE_BY_CODE.get(code));
            }
        }

        for (String tableName : new LinkedHashSet<>(CURRICULUM_SINGLE_TABLE_BY_CODE.values())) {
            if (!allowedSingleTables.contains(tableName)) {
                payload.put(tableName, new LinkedHashMap<String, Object>());
            }
        }
        for (String tableName : new LinkedHashSet<>(CURRICULUM_MULTI_TABLE_BY_CODE.values())) {
            if (!allowedMultiTables.contains(tableName)) {
                payload.put(tableName, new ArrayList<>());
            }
        }
    }

    private void insertSingleRowTables(Map<String, Object> payload) {
        for (String tableName : SINGLE_ROW_TABLES) {
            if (!tableExists(tableName)) {
                continue;
            }
            Map<String, Object> row = asMutableMap(payload.get(tableName));
            if (!shouldPersistSingleRow(tableName, row, payload)) {
                continue;
            }
            insertRow(tableName, row);
        }
    }

    private void insertMultiRowTables(Map<String, Object> payload) {
        Map<Long, Long> projectIdMapping = new LinkedHashMap<>();

        for (String tableName : MULTI_ROW_TABLES) {
            if (!tableExists(tableName)) {
                continue;
            }
            List<Map<String, Object>> rows = asMutableRows(payload.get(tableName));
            if (rows.isEmpty()) {
                continue;
            }

            if ("student_project_entries".equals(tableName)) {
                projectIdMapping.putAll(insertProjectEntries(rows));
                continue;
            }
            if ("student_project_outputs".equals(tableName)) {
                insertProjectOutputs(rows, projectIdMapping);
                continue;
            }

            for (Map<String, Object> row : rows) {
                if (isMeaningfulMultiRow(row)) {
                    insertRow(tableName, row);
                }
            }
        }
    }

    private Map<Long, Long> insertProjectEntries(List<Map<String, Object>> rows) {
        Map<Long, Long> projectIdMapping = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!isMeaningfulMultiRow(row)) {
                continue;
            }
            Long originalProjectId = toLong(row.get("project_id"));
            Map<String, Object> insertRow = new LinkedHashMap<>(row);
            boolean shouldAutoGenerateProjectId = originalProjectId == null || originalProjectId < 0;
            if (shouldAutoGenerateProjectId) {
                insertRow.remove("project_id");
            }

            Number generatedKey = insertRowAndReturnGeneratedKey("student_project_entries", insertRow);
            if (shouldAutoGenerateProjectId && generatedKey != null) {
                long actualProjectId = generatedKey.longValue();
                if (originalProjectId != null) {
                    projectIdMapping.put(originalProjectId, actualProjectId);
                }
                row.put("project_id", actualProjectId);
            } else if (originalProjectId != null) {
                projectIdMapping.put(originalProjectId, originalProjectId);
            }
        }
        return projectIdMapping;
    }

    private void insertProjectOutputs(List<Map<String, Object>> rows, Map<Long, Long> projectIdMapping) {
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalizedRow = new LinkedHashMap<>(row);
            Long rawProjectId = toLong(normalizedRow.get("project_id"));
            if (rawProjectId != null && projectIdMapping.containsKey(rawProjectId)) {
                normalizedRow.put("project_id", projectIdMapping.get(rawProjectId));
            } else if (rawProjectId != null && rawProjectId < 0) {
                normalizedRow.put("project_id", null);
            }

            if (toLong(normalizedRow.get("project_id")) == null || !isMeaningfulMultiRow(normalizedRow)) {
                continue;
            }
            insertRow("student_project_outputs", normalizedRow);
        }
    }

    private boolean shouldPersistSingleRow(String tableName, Map<String, Object> row, Map<String, Object> payload) {
        if ("student_basic_info".equals(tableName)) {
            return true;
        }
        if (hasMeaningfulSingleRowContent(row)) {
            return true;
        }
        for (String childTableName : SINGLE_ROW_CHILD_DEPENDENCIES.getOrDefault(tableName, List.of())) {
            Object childValue = payload.get(childTableName);
            if (childValue instanceof Map<?, ?> map && hasMeaningfulSingleRowContent(asMutableMap(map))) {
                return true;
            }
            if (childValue instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && isMeaningfulMultiRow(asMutableMap(map))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasMeaningfulSingleRowContent(Map<String, Object> row) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (MEANINGFULNESS_IGNORED_FIELDS.contains(entry.getKey())) {
                continue;
            }
            if (hasMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMeaningfulMultiRow(Map<String, Object> row) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (MEANINGFULNESS_IGNORED_FIELDS.contains(entry.getKey())) {
                continue;
            }
            if (hasMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return StrUtil.isNotBlank(stringValue);
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    private void insertRow(String tableName, Map<String, Object> row) {
        insertRowAndReturnGeneratedKey(tableName, row);
    }

    private Number insertRowAndReturnGeneratedKey(String tableName, Map<String, Object> row) {
        Map<String, Object> insertRow = prepareInsertRow(tableName, row);
        if (insertRow.isEmpty()) {
            return null;
        }

        List<String> columns = new ArrayList<>(insertRow.keySet());
        String escapedColumns = columns.stream().map(column -> "`" + column + "`").collect(java.util.stream.Collectors.joining(", "));
        String placeholders = columns.stream().map(column -> "?").collect(java.util.stream.Collectors.joining(", "));
        String sql = "INSERT INTO `" + tableName + "` (" + escapedColumns + ") VALUES (" + placeholders + ")";
        List<Object> values = columns.stream().map(insertRow::get).toList();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey();
    }

    private Map<String, Object> prepareInsertRow(String tableName, Map<String, Object> row) {
        Map<String, ColumnMeta> columnMap = getColumnMap(tableName);
        Map<String, Object> insertRow = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            ColumnMeta column = columnMap.get(entry.getKey());
            if (column == null || "create_time".equals(column.name()) || "update_time".equals(column.name())) {
                continue;
            }
            insertRow.put(column.name(), normalizeWriteValue(entry.getValue(), column.type()));
        }
        if (columnMap.containsKey("delete_flag") && !insertRow.containsKey("delete_flag")) {
            insertRow.put("delete_flag", "1");
        }
        return insertRow;
    }

    private Object normalizeWriteValue(Object value, String columnType) {
        if (value == null) {
            return null;
        }
        String normalizedType = StrUtil.nullToEmpty(columnType).toLowerCase();
        if (value instanceof Boolean bool) {
            return normalizedType.contains("int") || normalizedType.contains("bit") ? (bool ? 1 : 0) : bool;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "invalid json field value");
            }
        }
        return value;
    }

    private Map<String, ColumnMeta> getColumnMap(String tableName) {
        Map<String, ColumnMeta> result = new LinkedHashMap<>();
        for (ColumnMeta column : getTableColumns(tableName)) {
            result.put(column.name(), column);
        }
        return result;
    }

    private List<ColumnMeta> getTableColumns(String tableName) {
        return tableColumnsCache.computeIfAbsent(tableName, current -> {
            if (!tableExists(current)) {
                return List.of();
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW COLUMNS FROM `" + current + "`");
            List<ColumnMeta> columns = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                columns.add(new ColumnMeta(String.valueOf(row.get("Field")), String.valueOf(row.get("Type"))));
            }
            return columns;
        });
    }

    private boolean hasColumn(String tableName, String columnName) {
        return getColumnMap(tableName).containsKey(columnName);
    }

    private boolean tableExists(String tableName) {
        return tableExistsCache.computeIfAbsent(tableName, current ->
            !jdbcTemplate.queryForList(
                """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                LIMIT 1
                """,
                current
            ).isEmpty()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> asMutableRows(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return rows;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(asMutableMap(map));
            }
        }
        return rows;
    }

    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String stringValue && StrUtil.isBlank(stringValue));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && StrUtil.isNotBlank(stringValue)) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && StrUtil.isNotBlank(stringValue)) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ColumnMeta(String name, String type) {
    }
}
