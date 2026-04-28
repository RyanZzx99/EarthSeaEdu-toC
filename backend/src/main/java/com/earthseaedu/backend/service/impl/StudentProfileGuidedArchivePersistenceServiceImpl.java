package com.earthseaedu.backend.service.impl;

import com.earthseaedu.backend.mapper.StudentProfileMapper;
import com.earthseaedu.backend.service.BusinessProfilePersistenceService;
import com.earthseaedu.backend.service.StudentProfileGuidedArchivePersistenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首页标准问卷建档结果落正式档案表的新表持久化实现。
 */
@Service
public class StudentProfileGuidedArchivePersistenceServiceImpl implements StudentProfileGuidedArchivePersistenceService {

    private static final String CURRICULUM_SYSTEM_TABLE = "student_basic_info_curriculum_system";
    private static final String LANGUAGE_PARENT_TABLE = "student_language";
    private static final String LANGUAGE_TEST_RECORD_TABLE = "student_language_test_record";
    private static final String LANGUAGE_TEST_SCORE_ITEM_TABLE = "student_language_test_score_item";
    private static final String STANDARDIZED_SAT_TABLE = "student_standardized_sat";
    private static final String STANDARDIZED_ACT_TABLE = "student_standardized_act";
    private static final String ACTIVITY_EXPERIENCE_TABLE = "student_activity_experience";
    private static final String ENTERPRISE_INTERNSHIP_TABLE = "student_enterprise_internship";
    private static final String RESEARCH_EXPERIENCE_TABLE = "student_research_experience";
    private static final String COMPETITION_RECORD_TABLE = "student_competition_record";

    private static final List<String> SHARED_BASE_TABLES = List.of(
        "student_basic_info",
        "student_basic_info_target_country_entries",
        "student_basic_info_target_major_entries",
        "student_academic"
    );

    private static final List<String> CURRICULUM_TABLE_ORDER = List.of(
        CURRICULUM_SYSTEM_TABLE,
        "student_academic_curriculum_gpa",
        "student_academic_us_high_school_subject",
        "student_academic_other_curriculum_subject",
        "student_academic_a_level_subject",
        "student_academic_ap_subject",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
        "student_academic_ossd_subject"
    );

    private static final List<String> STANDARDIZED_TABLE_ORDER = List.of(
        STANDARDIZED_SAT_TABLE,
        STANDARDIZED_ACT_TABLE
    );

    private static final List<String> EXPERIENCE_TABLE_ORDER = List.of(
        ACTIVITY_EXPERIENCE_TABLE,
        ENTERPRISE_INTERNSHIP_TABLE,
        RESEARCH_EXPERIENCE_TABLE,
        COMPETITION_RECORD_TABLE
    );

    private static final Set<String> SUPPORTED_TABLES = Set.of(
        LANGUAGE_PARENT_TABLE,
        CURRICULUM_SYSTEM_TABLE,
        "student_academic_curriculum_gpa",
        "student_academic_us_high_school_subject",
        "student_academic_other_curriculum_subject",
        "student_academic_a_level_subject",
        "student_academic_ap_subject",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
        "student_academic_ossd_subject",
        LANGUAGE_TEST_RECORD_TABLE,
        LANGUAGE_TEST_SCORE_ITEM_TABLE,
        STANDARDIZED_SAT_TABLE,
        STANDARDIZED_ACT_TABLE,
        ACTIVITY_EXPERIENCE_TABLE,
        ENTERPRISE_INTERNSHIP_TABLE,
        RESEARCH_EXPERIENCE_TABLE,
        COMPETITION_RECORD_TABLE
    );
    private static final Map<String, Set<String>> REMOVED_COLUMNS_BY_TABLE = Map.ofEntries(
        Map.entry("student_academic_a_level_subject", Set.of("exam_series")),
        Map.entry("student_academic_ossd_subject", Set.of("school_year_label", "term_code", "score_text", "score_scale_code"))
    );

    private static final Map<String, String> CURRICULUM_ROW_SCOPE_FIELDS = Map.of(
        "student_academic_curriculum_gpa", "curriculum_system_code",
        "student_academic_other_curriculum_subject", "curriculum_system_code"
    );

    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final StudentProfileMapper studentProfileMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> tableColumnsCache = new ConcurrentHashMap<>();

    /**
     * 创建首页标准问卷新表持久化服务实现。
     */
    public StudentProfileGuidedArchivePersistenceServiceImpl(
        BusinessProfilePersistenceService businessProfilePersistenceService,
        StudentProfileMapper studentProfileMapper,
        ObjectMapper objectMapper
    ) {
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.studentProfileMapper = studentProfileMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void persistGuidedArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId) {
        persistSnapshotInternal(archiveForm, studentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncGuidedArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId) {
        persistSnapshotInternal(archiveForm, studentId);
    }

    private void persistSnapshotInternal(Map<String, Object> archiveForm, String studentId) {
        Map<String, Object> payload = clonePayload(archiveForm);
        businessProfilePersistenceService.persistArchiveFormSnapshot(pickSharedBasePayload(payload), studentId);
        persistCurriculumArchiveForm(studentId, payload);
        persistLanguageArchiveForm(studentId, payload);
        persistStandardizedArchiveForm(studentId, payload);
        persistExperienceArchiveForm(studentId, payload);
    }

    private Map<String, Object> pickSharedBasePayload(Map<String, Object> archiveForm) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String tableName : SHARED_BASE_TABLES) {
            Object value = archiveForm.get(tableName);
            if (value instanceof Map<?, ?> mapValue) {
                payload.put(tableName, new LinkedHashMap<>(objectMapper.convertValue(mapValue, MAP_TYPE)));
            } else if (value instanceof List<?> listValue) {
                payload.put(tableName, objectMapper.convertValue(listValue, ROWS_TYPE));
            }
        }
        return payload;
    }

    private void persistCurriculumArchiveForm(String studentId, Map<String, Object> archiveForm) {
        Map<String, Object> normalizedPayload = normalizeCurriculumArchiveForm(archiveForm, studentId);
        softDeleteTablesByStudent(studentId, reverse(CURRICULUM_TABLE_ORDER));
        insertDynamicRowsByStudent(normalizedPayload, CURRICULUM_TABLE_ORDER, this::isMeaningfulCurriculumRow);
    }

    private void persistLanguageArchiveForm(String studentId, Map<String, Object> archiveForm) {
        ensureLanguageProfileParent(studentId);
        if (tableExists(LANGUAGE_TEST_SCORE_ITEM_TABLE)) {
            studentProfileMapper.softDeleteLanguageTestScoreItemsByStudentId(studentId);
        }
        if (tableExists(LANGUAGE_TEST_RECORD_TABLE)) {
            studentProfileMapper.softDeleteLanguageTestRecordsByStudentId(studentId);
        }

        List<Map<String, Object>> languageTestRecords = toMutableRows(archiveForm.get(LANGUAGE_TEST_RECORD_TABLE));
        List<Map<String, Object>> languageTestScoreItems = toMutableRows(archiveForm.get(LANGUAGE_TEST_SCORE_ITEM_TABLE));
        Map<Long, Long> recordIdMapping = insertLanguageTestRecords(studentId, languageTestRecords);
        insertLanguageTestScoreItems(languageTestScoreItems, recordIdMapping);
    }

    private void ensureLanguageProfileParent(String studentId) {
        if (!tableExists(LANGUAGE_PARENT_TABLE)) {
            return;
        }
        Integer activeCount = studentProfileMapper.countActiveLanguageParentsByStudentId(studentId);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentId", studentId);
        row.put("studentLanguageId", "lang_" + UUID.randomUUID());
        studentProfileMapper.insertLanguageParent(row);
    }

    private void persistStandardizedArchiveForm(String studentId, Map<String, Object> archiveForm) {
        softDeleteTablesByStudent(studentId, reverse(STANDARDIZED_TABLE_ORDER));
        insertDynamicRowsByStudent(archiveForm, STANDARDIZED_TABLE_ORDER, this::isMeaningfulStandardizedRow);
    }

    private void persistExperienceArchiveForm(String studentId, Map<String, Object> archiveForm) {
        softDeleteTablesByStudent(studentId, reverse(EXPERIENCE_TABLE_ORDER));
        insertDynamicRowsByStudent(archiveForm, EXPERIENCE_TABLE_ORDER, this::isMeaningfulExperienceRow);
    }

    private Map<String, Object> normalizeCurriculumArchiveForm(Map<String, Object> archiveForm, String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String tableName : CURRICULUM_TABLE_ORDER) {
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                row.put("student_id", studentId);
                String scopeField = CURRICULUM_ROW_SCOPE_FIELDS.get(tableName);
                if (scopeField != null) {
                    String curriculumCode = normalizeCurriculumCode(row.get(scopeField));
                    if (curriculumCode != null) {
                        row.put(scopeField, curriculumCode);
                    }
                }
            }
            payload.put(tableName, rows);
        }
        normalizeCurriculumSystemPrimaryFlags(payload);
        return payload;
    }

    private void normalizeCurriculumSystemPrimaryFlags(Map<String, Object> archiveForm) {
        List<Map<String, Object>> rows = toMutableRows(archiveForm.get(CURRICULUM_SYSTEM_TABLE));
        if (rows.isEmpty()) {
            archiveForm.put(CURRICULUM_SYSTEM_TABLE, rows);
            return;
        }

        List<Map<String, Object>> dedupedRows = new ArrayList<>();
        Map<String, Map<String, Object>> rowByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String curriculumCode = normalizeCurriculumCode(row.get("curriculum_system_code"));
            if (curriculumCode == null) {
                continue;
            }
            Map<String, Object> existing = rowByCode.get(curriculumCode);
            if (existing == null) {
                row.put("curriculum_system_code", curriculumCode);
                rowByCode.put(curriculumCode, row);
                dedupedRows.add(row);
                continue;
            }
            if (toTinyInt(row.get("is_primary")) == 1) {
                existing.put("is_primary", 1);
            }
        }

        int primaryIndex = 0;
        for (int index = 0; index < dedupedRows.size(); index++) {
            if (toTinyInt(dedupedRows.get(index).get("is_primary")) == 1) {
                primaryIndex = index;
                break;
            }
        }
        for (int index = 0; index < dedupedRows.size(); index++) {
            dedupedRows.get(index).put("is_primary", index == primaryIndex ? 1 : 0);
        }
        archiveForm.put(CURRICULUM_SYSTEM_TABLE, dedupedRows);
    }

    private void softDeleteTablesByStudent(String studentId, List<String> tableNames) {
        for (String tableName : tableNames) {
            if (!tableExists(tableName) || !hasColumn(tableName, "student_id") || !hasColumn(tableName, "delete_flag")) {
                continue;
            }
            studentProfileMapper.softDeleteByStudent(safeProfileTableName(tableName), studentId);
        }
    }

    private void insertDynamicRowsByStudent(
        Map<String, Object> archiveForm,
        List<String> tableNames,
        TableRowMeaningfulnessChecker checker
    ) {
        for (String tableName : tableNames) {
            if (!tableExists(tableName)) {
                continue;
            }
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                if (!checker.isMeaningful(tableName, row)) {
                    continue;
                }
                insertDynamicRow(tableName, row);
            }
        }
    }

    private Map<Long, Long> insertLanguageTestRecords(String studentId, List<Map<String, Object>> rows) {
        Map<Long, Long> idMapping = new LinkedHashMap<>();
        if (!tableExists(LANGUAGE_TEST_RECORD_TABLE)) {
            return idMapping;
        }
        for (Map<String, Object> row : rows) {
            row.put("student_id", studentId);
            if (!isMeaningfulLanguageTestRecordRow(row)) {
                continue;
            }

            String testTypeCode = nullableString(row.get("test_type_code"));
            if (testTypeCode == null) {
                continue;
            }

            Long clientRecordId = toLong(row.get("student_language_test_record_id"));
            Map<String, Object> insertRow = new LinkedHashMap<>();
            insertRow.put("studentId", studentId);
            insertRow.put("testTypeCode", testTypeCode);
            insertRow.put("statusCode", nullableString(row.get("status_code")));
            insertRow.put("testDate", nullableString(row.get("test_date")));
            insertRow.put("examNameText", nullableString(row.get("exam_name_text")));
            insertRow.put("totalScore", toBigDecimal(row.get("total_score")));
            insertRow.put("scoreScaleText", nullableString(row.get("score_scale_text")));
            insertRow.put("cefrLevelCode", nullableString(row.get("cefr_level_code")));
            insertRow.put("evidenceLevelCode", nullableString(row.get("evidence_level_code")));
            insertRow.put("isBestScore", toTinyInt(row.get("is_best_score")));
            insertRow.put("notes", nullableString(row.get("notes")));
            studentProfileMapper.insertLanguageTestRecord(insertRow);

            Long actualRecordId = toLong(insertRow.get("studentLanguageTestRecordId"));
            if (clientRecordId != null && actualRecordId != null) {
                idMapping.put(clientRecordId, actualRecordId);
            }
            if (actualRecordId != null) {
                row.put("student_language_test_record_id", actualRecordId);
            }
        }
        return idMapping;
    }

    private void insertLanguageTestScoreItems(
        List<Map<String, Object>> rows,
        Map<Long, Long> recordIdMapping
    ) {
        if (!tableExists(LANGUAGE_TEST_SCORE_ITEM_TABLE)) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (!isMeaningfulLanguageTestScoreItemRow(row)) {
                continue;
            }

            String scoreItemCode = nullableString(row.get("score_item_code"));
            if (scoreItemCode == null) {
                continue;
            }

            Long clientRecordId = toLong(row.get("student_language_test_record_id"));
            Long actualRecordId = clientRecordId == null ? null : recordIdMapping.get(clientRecordId);
            if (actualRecordId == null) {
                continue;
            }

            Map<String, Object> insertRow = new LinkedHashMap<>();
            insertRow.put("studentLanguageTestRecordId", actualRecordId);
            insertRow.put("scoreItemCode", scoreItemCode);
            insertRow.put("scoreValue", toBigDecimal(row.get("score_value")));
            insertRow.put("scoreScaleText", nullableString(row.get("score_scale_text")));
            studentProfileMapper.insertLanguageTestScoreItem(insertRow);
        }
    }

    private boolean isMeaningfulCurriculumRow(String tableName, Map<String, Object> row) {
        return hasAnyMeaningfulValue(row, ignoredFieldsForTable(tableName, Set.of()));
    }

    private boolean isMeaningfulStandardizedRow(String tableName, Map<String, Object> row) {
        return hasAnyMeaningfulValue(row, ignoredFieldsForTable(tableName, Set.of("is_best_score")));
    }

    private boolean isMeaningfulExperienceRow(String tableName, Map<String, Object> row) {
        return hasAnyMeaningfulValue(row, ignoredFieldsForTable(tableName, Set.of()));
    }

    private boolean isMeaningfulLanguageTestRecordRow(Map<String, Object> row) {
        return hasAnyMeaningfulValue(
            row,
            ignoredFieldsForTable(LANGUAGE_TEST_RECORD_TABLE, Set.of("is_best_score"))
        );
    }

    private boolean isMeaningfulLanguageTestScoreItemRow(Map<String, Object> row) {
        return hasAnyMeaningfulValue(
            row,
            ignoredFieldsForTable(LANGUAGE_TEST_SCORE_ITEM_TABLE, Set.of("student_language_test_record_id"))
        );
    }

    private Set<String> ignoredFieldsForTable(String tableName, Set<String> extraIgnoredFields) {
        Set<String> ignored = new LinkedHashSet<>(Set.of("student_id", "delete_flag", "create_time", "update_time"));
        ignored.addAll(autoIncrementFields(tableName));
        ignored.addAll(extraIgnoredFields);
        return ignored;
    }

    private Set<String> autoIncrementFields(String tableName) {
        Set<String> fields = new LinkedHashSet<>();
        for (ColumnMeta column : getTableColumns(tableName)) {
            if (column.autoIncrement()) {
                fields.add(column.name());
            }
        }
        return fields;
    }

    private boolean hasAnyMeaningfulValue(Map<String, Object> row, Set<String> ignoredFields) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (ignoredFields.contains(entry.getKey())) {
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
            return !stringValue.isBlank();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
    }


    private Long insertDynamicRow(String tableName, Map<String, Object> row) {
        Map<String, Object> insertRow = prepareInsertRow(tableName, row);
        if (insertRow.isEmpty()) {
            return null;
        }

        List<String> columns = new ArrayList<>(insertRow.keySet());
        String columnList = columns.stream()
            .map(column -> "`" + column + "`")
            .collect(java.util.stream.Collectors.joining(", "));
        List<Object> values = columns.stream().map(insertRow::get).toList();

        Map<String, Object> mutation = new LinkedHashMap<>();
        mutation.put("tableName", safeProfileTableName(tableName));
        mutation.put("columnList", columnList);
        mutation.put("values", values);
        studentProfileMapper.insertDynamicRow(mutation);
        return toLong(mutation.get("generatedId"));
    }

    private Map<String, Object> prepareInsertRow(String tableName, Map<String, Object> row) {
        Map<String, ColumnMeta> columnMap = getColumnMap(tableName);
        Map<String, Object> insertRow = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            ColumnMeta column = columnMap.get(entry.getKey());
            if (column == null || column.autoIncrement() || "create_time".equals(column.name()) || "update_time".equals(column.name())) {
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
        String normalizedType = columnType == null ? "" : columnType.toLowerCase();
        if (value instanceof Boolean boolValue) {
            return normalizedType.contains("int") || normalizedType.contains("bit") ? (boolValue ? 1 : 0) : boolValue;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("invalid json field value", exception);
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
        if (!tableExists(tableName)) {
            tableColumnsCache.remove(tableName);
            return List.of();
        }
        List<ColumnMeta> cached = tableColumnsCache.get(tableName);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<Map<String, Object>> rows = studentProfileMapper.showColumns(safeProfileTableName(tableName));
        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(rowValue(row, "Field", "field"));
            if (isRemovedColumn(tableName, fieldName)) {
                continue;
            }
            columns.add(
                new ColumnMeta(
                    fieldName,
                    String.valueOf(rowValue(row, "Type", "type")),
                    isAutoIncrementColumn(row)
                )
            );
        }
        if (columns.isEmpty()) {
            tableColumnsCache.remove(tableName);
        } else {
            tableColumnsCache.put(tableName, columns);
        }
        return columns;
    }

    private boolean isRemovedColumn(String tableName, String fieldName) {
        return REMOVED_COLUMNS_BY_TABLE.getOrDefault(tableName, Set.of()).contains(fieldName);
    }

    private boolean isAutoIncrementColumn(Map<String, Object> row) {
        Object extraValue = rowValue(row, "Extra", "extra");
        return extraValue != null && String.valueOf(extraValue).toLowerCase().contains("auto_increment");
    }

    private boolean tableExists(String tableName) {
        if (Boolean.TRUE.equals(tableExistsCache.get(tableName))) {
            return true;
        }
        Integer count = studentProfileMapper.countTable(safeProfileTableName(tableName));
        boolean exists = count != null && count > 0;
        if (exists) {
            tableExistsCache.put(tableName, true);
        } else {
            tableExistsCache.remove(tableName);
            tableColumnsCache.remove(tableName);
        }
        return exists;
    }

    private boolean hasColumn(String tableName, String columnName) {
        for (ColumnMeta column : getTableColumns(tableName)) {
            if (column.name().equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    private String safeProfileTableName(String tableName) {
        if (!SUPPORTED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("Unsupported profile table: " + tableName);
        }
        return safeIdentifier(tableName);
    }

    private String safeIdentifier(String value) {
        if (value == null || value.isBlank() || !SQL_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        }
        return value;
    }

    private Map<String, Object> clonePayload(Map<String, Object> archiveForm) {
        if (archiveForm == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(archiveForm, MAP_TYPE);
    }

    private List<String> reverse(List<String> tableNames) {
        List<String> reversed = new ArrayList<>(tableNames);
        Collections.reverse(reversed);
        return reversed;
    }

    private List<Map<String, Object>> toMutableRows(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapValue) {
                rows.add(new LinkedHashMap<>(objectMapper.convertValue(mapValue, MAP_TYPE)));
            }
        }
        return rows;
    }

    private String normalizeCurriculumCode(Object value) {
        String curriculumCode = nullableString(value);
        return curriculumCode == null ? null : curriculumCode.toUpperCase();
    }

    private String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer toTinyInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0 ? 1 : 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0;
        }
        if ("true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
            return 1;
        }
        if ("false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
            return 0;
        }
        try {
            return Integer.parseInt(text) != 0 ? 1 : 0;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Object rowValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private record ColumnMeta(String name, String type, boolean autoIncrement) {
    }

    @FunctionalInterface
    private interface TableRowMeaningfulnessChecker {

        boolean isMeaningful(String tableName, Map<String, Object> row);
    }

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> ROWS_TYPE = new TypeReference<>() {
    };
}
