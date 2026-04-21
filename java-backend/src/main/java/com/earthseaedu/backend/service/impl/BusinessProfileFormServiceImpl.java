package com.earthseaedu.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.earthseaedu.backend.mapper.BusinessProfileFormMapper;
import com.earthseaedu.backend.service.BusinessProfileFormMetadata;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 正式档案表单读取服务实现。
 */
@Service
public class BusinessProfileFormServiceImpl implements BusinessProfileFormService {

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

    private static final List<String> TABLE_ORDER = List.of(
        "student_basic_info",
        "student_basic_info_curriculum_system",
        "student_academic",
        "student_academic_us_high_school_profile",
        "student_academic_us_high_school_course",
        "student_academic_other_curriculum_profile",
        "student_academic_a_level_profile",
        "student_academic_a_level_subject",
        "student_academic_ap_profile",
        "student_academic_ap_course",
        "student_academic_ib_profile",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_profile",
        "student_academic_chinese_high_school_subject",
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
        "student_project_entries"
    );

    private static final Map<String, String> TABLE_LABELS = BusinessProfileFormMetadata.TABLE_LABELS;
    private static final Map<String, String> FIELD_LABELS = BusinessProfileFormMetadata.FIELD_LABELS;
    private static final Map<String, String> FIELD_HELPERS = BusinessProfileFormMetadata.FIELD_HELPERS;
    private static final Map<String, Set<String>> HIDDEN_FIELDS_BY_TABLE = BusinessProfileFormMetadata.HIDDEN_FIELDS_BY_TABLE;
    private static final Map<String, List<String>> ENUM_FIELD_OPTIONS = BusinessProfileFormMetadata.ENUM_FIELD_OPTIONS;
    private static final Map<String, Map<String, String>> ENUM_OPTION_LABELS = BusinessProfileFormMetadata.ENUM_OPTION_LABELS;

    private static final Set<String> TEXTAREA_FIELDS = Set.of(
        "notes",
        "score_breakdown_json",
        "t1_examples_text",
        "t2_examples_text",
        "t3_examples_text",
        "t4_examples_text",
        "curriculum_system_notes",
        "other_curriculum_notes",
        "curriculum_combination_notes",
        "language_index_method_notes",
        "not_applicable_reason",
        "tier_reference_notes",
        "quality_rules_notes",
        "strictness_rules_notes"
    );

    private static final Set<String> HIDDEN_FIELDS = Set.of(
        "student_id",
        "student_academic_id",
        "student_language_id",
        "student_standardized_test_id",
        "schema_version",
        "profile_type",
        "project_id"
    );

    private static final Map<String, Set<String>> EXCLUDED_COLUMNS_BY_TABLE = Map.ofEntries(
        Map.entry("student_competitions", Set.of("student_competition_id")),
        Map.entry("student_competition_entries", Set.of("competition_id")),
        Map.entry("student_academic_us_high_school_course", Set.of("student_us_high_school_course_record_id")),
        Map.entry("student_academic_ap_course", Set.of("student_ap_course_record_id")),
        Map.entry("student_academic_ib_subject", Set.of("student_ib_subject_record_id")),
        Map.entry("student_academic_chinese_high_school_subject", Set.of("student_chs_subject_record_id")),
        Map.entry("student_language_ielts", Set.of("ielts_record_id")),
        Map.entry("student_language_toefl_ibt", Set.of("toefl_ibt_record_id")),
        Map.entry("student_language_toefl_essentials", Set.of("toefl_essentials_record_id")),
        Map.entry("student_language_det", Set.of("det_record_id")),
        Map.entry("student_language_pte", Set.of("pte_record_id")),
        Map.entry("student_language_languagecert", Set.of("languagecert_record_id")),
        Map.entry("student_language_cambridge", Set.of("cambridge_record_id")),
        Map.entry("student_language_other", Set.of("other_language_record_id")),
        Map.entry("student_standardized_test_records", Set.of("test_record_id")),
        Map.entry("student_activities", Set.of("student_activity_id")),
        Map.entry("student_activity_entries", Set.of("student_activity_entry_id")),
        Map.entry("student_projects_experience", Set.of("student_project_experience_id")),
        Map.entry("student_project_outputs", Set.of("output_id"))
    );

    private static final Map<String, String> STATIC_FIELD_OPTIONS = Map.ofEntries(
        Map.entry(key("student_basic_info", "current_grade"), "current_grade"),
        Map.entry(key("student_basic_info", "graduation_year"), "graduation_year"),
        Map.entry(key("student_basic_info", "target_entry_term"), "target_entry_term"),
        Map.entry(key("student_basic_info", "CTRY_CODE_VAL"), "country_code"),
        Map.entry(key("student_basic_info", "MAJ_CODE_VAL"), "major_code"),
        Map.entry(key("student_basic_info_curriculum_system", "curriculum_system_code"), "curriculum_system"),
        Map.entry(key("student_academic_other_curriculum_profile", "curriculum_scope_code"), "curriculum_system"),
        Map.entry(key("student_academic", "school_city"), "school_city"),
        Map.entry(key("student_academic_us_high_school_course", "us_high_school_course_id"), "us_high_school_course"),
        Map.entry(key("student_academic_a_level_subject", "al_subject_id"), "a_level_subject"),
        Map.entry(key("student_academic_ap_course", "ap_course_id"), "ap_course"),
        Map.entry(key("student_academic_ib_subject", "ib_subject_id"), "ib_subject"),
        Map.entry(key("student_academic_chinese_high_school_subject", "chs_subject_id"), "chs_subject"),
        Map.entry(key("student_language", "best_test_type_code"), "language_test_type")
    );

    private static final List<Map<String, String>> CURRENT_GRADE_OPTIONS = BusinessProfileFormMetadata.CURRENT_GRADE_OPTIONS;
    private static final List<Map<String, String>> SCHOOL_CITY_OPTIONS = BusinessProfileFormMetadata.SCHOOL_CITY_OPTIONS;
    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> DICTIONARY_TABLES = Set.of(
        "CTRY_CODE_VAL",
        "MAJ_CODE_VAL",
        "dict_curriculum_system",
        "dict_language_test_type",
        "dict_us_high_school_course",
        "dict_a_level_subject",
        "dict_ap_course",
        "dict_ib_subject",
        "dict_chinese_high_school_subject"
    );

    private final BusinessProfileFormMapper businessProfileFormMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> editableColumnsCache = new ConcurrentHashMap<>();

    /**
     * 创建 BusinessProfileFormServiceImpl 实例。
     */
    public BusinessProfileFormServiceImpl(BusinessProfileFormMapper businessProfileFormMapper, ObjectMapper objectMapper) {
        this.businessProfileFormMapper = businessProfileFormMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> loadBusinessProfileFormBundle(String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("archive_form", loadBusinessProfileSnapshot(studentId));
        payload.put("form_meta", buildBusinessProfileFormMeta());
        return payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> loadBusinessProfileSnapshot(String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();

        for (String tableName : SINGLE_ROW_TABLES) {
            payload.put(tableName, loadSingleRow(tableName, studentId));
        }

        for (String tableName : MULTI_ROW_TABLES) {
            if ("student_basic_info_target_country_entries".equals(tableName)) {
                payload.put(tableName, loadGuidedTargetCountryEntries(studentId));
                continue;
            }
            if ("student_basic_info_target_major_entries".equals(tableName)) {
                payload.put(tableName, loadGuidedTargetMajorEntries(studentId));
                continue;
            }
            payload.put(tableName, loadMultiRows(tableName, studentId));
        }

        return payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> buildBusinessProfileFormMeta() {
        Map<String, List<Map<String, String>>> cachedOptions = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();

        for (String tableName : TABLE_ORDER) {
            List<ColumnMeta> columns = getEditableColumns(tableName);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (ColumnMeta column : columns) {
                List<Map<String, String>> options = resolveFieldOptions(
                    tableName,
                    column.name(),
                    column.type(),
                    cachedOptions
                );

                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", column.name());
                field.put("label", FIELD_LABELS.getOrDefault(column.name(), humanizeFieldName(column.name())));
                field.put("input_type", inferInputType(column.name(), column.type(), options));
                field.put("hidden", isFieldHidden(tableName, column.name()));
                field.put("options", options);
                field.put("helper_text", FIELD_HELPERS.get(key(tableName, column.name())));
                fields.add(field);
            }

            Map<String, Object> tableMeta = new LinkedHashMap<>();
            tableMeta.put("label", TABLE_LABELS.getOrDefault(tableName, humanizeFieldName(tableName)));
            tableMeta.put("kind", SINGLE_ROW_TABLES.contains(tableName) ? "single" : "multi");
            tableMeta.put("fields", fields);
            tables.put(tableName, tableMeta);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table_order", TABLE_ORDER);
        result.put("tables", tables);
        return result;
    }

    private Map<String, Object> loadSingleRow(String tableName, String studentId) {
        String safeTableName = safeTableName(tableName);
        List<ColumnMeta> columns = getEditableColumns(tableName);
        if (CollUtil.isEmpty(columns)) {
            return Map.of();
        }

        List<Map<String, Object>> rows = businessProfileFormMapper.selectSingleRow(
            safeTableName,
            safeColumnNames(columns),
            studentId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        return normalizeRow(rows.get(0), columns);
    }

    private List<Map<String, Object>> loadMultiRows(String tableName, String studentId) {
        String safeTableName = safeTableName(tableName);
        List<ColumnMeta> columns = getEditableColumns(tableName);
        if (CollUtil.isEmpty(columns)) {
            return List.of();
        }

        List<String> columnNames = safeColumnNames(columns);
        List<Map<String, Object>> rows = "student_project_outputs".equals(tableName)
            ? businessProfileFormMapper.selectProjectOutputRows(columnNames, studentId)
            : businessProfileFormMapper.selectMultiRows(safeTableName, columnNames, studentId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(normalizeRow(row, columns));
        }
        return result;
    }

    private List<Map<String, Object>> loadGuidedTargetCountryEntries(String studentId) {
        List<ColumnMeta> columns = List.of(
            new ColumnMeta("country_code", "varchar"),
            new ColumnMeta("sort_order", "int"),
            new ColumnMeta("is_primary", "tinyint"),
            new ColumnMeta("remark", "varchar")
        );
        return normalizeRows(businessProfileFormMapper.selectGuidedTargetCountryEntries(studentId), columns);
    }

    private List<Map<String, Object>> loadGuidedTargetMajorEntries(String studentId) {
        List<ColumnMeta> columns = List.of(
            new ColumnMeta("major_direction_code", "varchar"),
            new ColumnMeta("major_direction_label", "varchar"),
            new ColumnMeta("major_code", "varchar"),
            new ColumnMeta("sort_order", "int"),
            new ColumnMeta("is_primary", "tinyint")
        );
        return normalizeRows(businessProfileFormMapper.selectGuidedTargetMajorEntries(studentId), columns);
    }

    private List<ColumnMeta> getEditableColumns(String tableName) {
        return editableColumnsCache.computeIfAbsent(tableName, this::loadEditableColumns);
    }

    private List<ColumnMeta> loadEditableColumns(String tableName) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<Map<String, Object>> rows = businessProfileFormMapper.showColumns(safeTableName(tableName));
        Set<String> excludedColumns = new LinkedHashSet<>(EXCLUDED_COLUMNS_BY_TABLE.getOrDefault(tableName, Set.of()));
        excludedColumns.add("create_time");
        excludedColumns.add("update_time");
        excludedColumns.add("delete_flag");

        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(rowValue(row, "Field", "field"));
            if (excludedColumns.contains(fieldName)) {
                continue;
            }
            columns.add(new ColumnMeta(fieldName, String.valueOf(rowValue(row, "Type", "type"))));
        }
        return columns;
    }

    private boolean tableExists(String tableName) {
        return tableExistsCache.computeIfAbsent(tableName, current -> {
            Integer count = businessProfileFormMapper.countTable(safeTableName(current));
            return count != null && count > 0;
        });
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows, List<ColumnMeta> columns) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(normalizeRow(row, columns));
        }
        return result;
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row, List<ColumnMeta> columns) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ColumnMeta column : columns) {
            result.put(column.name(), normalizeValue(row.get(column.name()), column.type()));
        }
        return result;
    }

    private Object normalizeValue(Object value, String columnType) {
        if (value == null) {
            return null;
        }
        String normalizedColumnType = CharSequenceUtil.nullToEmpty(columnType).toLowerCase();
        if (normalizedColumnType.contains("json")) {
            return parseJsonValue(value);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
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

    private Object parseJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        String text = String.valueOf(value);
        if (CharSequenceUtil.isBlank(text)) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception exception) {
            return text;
        }
    }

    private List<Map<String, String>> resolveFieldOptions(
        String tableName,
        String fieldName,
        String columnType,
        Map<String, List<Map<String, String>>> cachedOptions
    ) {
        String staticKey = STATIC_FIELD_OPTIONS.get(key(tableName, fieldName));
        if (CharSequenceUtil.isNotBlank(staticKey)) {
            return cachedOptions.computeIfAbsent(staticKey, this::loadStaticOptionsByKey);
        }
        List<String> explicitOptions = ENUM_FIELD_OPTIONS.get(key(tableName, fieldName));
        if (CollUtil.isNotEmpty(explicitOptions)) {
            return buildEnumOptions(tableName, fieldName, explicitOptions);
        }
        return parseEnumOptions(tableName, fieldName, columnType);
    }

    private List<Map<String, String>> loadStaticOptionsByKey(String optionKey) {
        return switch (optionKey) {
            case "current_grade" -> CURRENT_GRADE_OPTIONS;
            case "graduation_year" -> buildGraduationYearOptions();
            case "target_entry_term" -> buildEntryTermOptions();
            case "country_code" -> loadSimpleDictionaryOptions("CTRY_CODE_VAL", "CTRY_CODE_VAL", "CN_CODE_NAME", "EN_CODE_NAME");
            case "major_code" -> loadSimpleDictionaryOptions("MAJ_CODE_VAL", "MAJ_CODE_VAL", "MAJ_CN_CODE_NAME", "MAJ_EN_CODE_NAME");
            case "curriculum_system" -> loadSimpleDictionaryOptions("dict_curriculum_system", "curriculum_system_code", "curriculum_system_name_cn", "curriculum_system_name_en");
            case "language_test_type" -> loadSimpleDictionaryOptions("dict_language_test_type", "test_type_code", "test_type_name_cn", "test_type_name_en");
            case "school_city" -> SCHOOL_CITY_OPTIONS;
            case "us_high_school_course" -> loadSimpleDictionaryOptions("dict_us_high_school_course", "us_high_school_course_id", "course_name_cn", "course_name_en");
            case "a_level_subject" -> loadSimpleDictionaryOptions("dict_a_level_subject", "al_subject_id", "subject_name_cn", "subject_name_en");
            case "ap_course" -> loadSimpleDictionaryOptions("dict_ap_course", "ap_course_id", "course_name_cn", "course_name_en");
            case "ib_subject" -> loadSimpleDictionaryOptions("dict_ib_subject", "ib_subject_id", "subject_name_cn", "subject_name_en");
            case "chs_subject" -> loadSimpleDictionaryOptions("dict_chinese_high_school_subject", "chs_subject_id", "subject_name_cn", "subject_name_en");
            default -> List.of();
        };
    }

    private List<Map<String, String>> loadSimpleDictionaryOptions(
        String tableName,
        String valueColumn,
        String labelCnColumn,
        String labelEnColumn
    ) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<Map<String, Object>> rows = businessProfileFormMapper.selectDictionaryOptions(
            safeDictionaryTableName(tableName),
            safeIdentifier(valueColumn),
            safeIdentifier(labelCnColumn),
            safeIdentifier(labelEnColumn)
        );
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = String.valueOf(rowValue(row, "value"));
            Object labelCnValue = rowValue(row, "label_cn", "labelCn");
            Object labelEnValue = rowValue(row, "label_en", "labelEn");
            String labelCn = labelCnValue == null ? null : String.valueOf(labelCnValue);
            String labelEn = labelEnValue == null ? null : String.valueOf(labelEnValue);
            result.add(option(value, CharSequenceUtil.blankToDefault(labelCn, CharSequenceUtil.blankToDefault(labelEn, value))));
        }
        return result;
    }

    private List<Map<String, String>> buildEnumOptions(String tableName, String fieldName, List<String> values) {
        Map<String, String> labelMap = ENUM_OPTION_LABELS.getOrDefault(key(tableName, fieldName), Map.of());
        List<Map<String, String>> options = new ArrayList<>();
        for (String value : values) {
            options.add(option(value, labelMap.getOrDefault(value, value)));
        }
        return options;
    }

    private List<Map<String, String>> parseEnumOptions(String tableName, String fieldName, String columnType) {
        String normalized = CharSequenceUtil.nullToEmpty(columnType).trim();
        if (!normalized.startsWith("enum(") || !normalized.endsWith(")")) {
            return null;
        }
        String inner = normalized.substring("enum(".length(), normalized.length() - 1);
        if (CharSequenceUtil.isBlank(inner)) {
            return null;
        }
        Map<String, String> labelMap = ENUM_OPTION_LABELS.getOrDefault(key(tableName, fieldName), Map.of());
        List<Map<String, String>> options = new ArrayList<>();
        for (String rawItem : inner.split(",")) {
            String value = rawItem.trim();
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            value = value.replace("''", "'");
            options.add(option(value, labelMap.getOrDefault(value, value)));
        }
        return options;
    }

    private String inferInputType(String fieldName, String columnType, List<Map<String, String>> options) {
        String normalizedType = CharSequenceUtil.nullToEmpty(columnType).toLowerCase();
        if (CollUtil.isNotEmpty(options)) {
            return "select";
        }
        if (TEXTAREA_FIELDS.contains(fieldName) || normalizedType.contains("json") || normalizedType.contains("text")) {
            return "textarea";
        }
        if (normalizedType.startsWith("date") || normalizedType.startsWith("datetime") || normalizedType.startsWith("timestamp")) {
            return "date";
        }
        if (normalizedType.startsWith("tinyint(1)") || fieldName.startsWith("is_") || fieldName.startsWith("prefer_")) {
            return "checkbox";
        }
        if (normalizedType.contains("int") || normalizedType.contains("decimal") || normalizedType.contains("float") || normalizedType.contains("double")) {
            return "number";
        }
        return "text";
    }

    private boolean isFieldHidden(String tableName, String fieldName) {
        if (HIDDEN_FIELDS.contains(fieldName)) {
            return true;
        }
        return HIDDEN_FIELDS_BY_TABLE.getOrDefault(tableName, Set.of()).contains(fieldName);
    }

    private String humanizeFieldName(String fieldName) {
        String[] parts = CharSequenceUtil.nullToEmpty(fieldName).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (CharSequenceUtil.isBlank(part)) {
                continue;
            }
            words.add(StrUtil.upperFirst(part.toLowerCase()));
        }
        return words.isEmpty() ? fieldName : String.join(" ", words);
    }

    private static List<Map<String, String>> buildGraduationYearOptions() {
        int currentYear = Year.now().getValue();
        List<Map<String, String>> options = new ArrayList<>();
        for (int year = currentYear - 1; year < currentYear + 9; year++) {
            options.add(option(String.valueOf(year), String.valueOf(year)));
        }
        return options;
    }

    private static List<Map<String, String>> buildEntryTermOptions() {
        int currentYear = Year.now().getValue();
        List<Map<String, String>> options = new ArrayList<>();
        for (int year = currentYear; year < currentYear + 6; year++) {
            options.add(option(year + "春季入学", year + "春季入学"));
            options.add(option(year + "秋季入学", year + "秋季入学"));
        }
        return options;
    }

    private static Map<String, String> option(String value, String label) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    private static List<String> safeColumnNames(List<ColumnMeta> columns) {
        List<String> names = new ArrayList<>();
        for (ColumnMeta column : columns) {
            names.add(safeIdentifier(column.name()));
        }
        return names;
    }

    private static String safeTableName(String tableName) {
        if (!SINGLE_ROW_TABLES.contains(tableName) && !MULTI_ROW_TABLES.contains(tableName) && !DICTIONARY_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("Unsupported table: " + tableName);
        }
        return safeIdentifier(tableName);
    }

    private static String safeDictionaryTableName(String tableName) {
        if (!DICTIONARY_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("Unsupported dictionary table: " + tableName);
        }
        return safeIdentifier(tableName);
    }

    private static String safeIdentifier(String value) {
        if (CharSequenceUtil.isBlank(value) || !SQL_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        }
        return value;
    }

    private static Object rowValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static String key(String tableName, String fieldName) {
        return tableName + "." + fieldName;
    }

    private record ColumnMeta(String name, String type) {
    }
}
