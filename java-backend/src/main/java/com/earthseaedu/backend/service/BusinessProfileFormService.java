package com.earthseaedu.backend.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BusinessProfileFormService {

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

    private static final Map<String, String> TABLE_LABELS = Map.ofEntries(
        Map.entry("student_basic_info", "Basic Info"),
        Map.entry("student_basic_info_curriculum_system", "Curriculum System"),
        Map.entry("student_academic", "Academic"),
        Map.entry("student_academic_us_high_school_profile", "US High School Profile"),
        Map.entry("student_academic_us_high_school_course", "US High School Courses"),
        Map.entry("student_academic_other_curriculum_profile", "Other Curriculum Profile"),
        Map.entry("student_academic_a_level_profile", "A-Level Profile"),
        Map.entry("student_academic_a_level_subject", "A-Level Subjects"),
        Map.entry("student_academic_ap_profile", "AP Profile"),
        Map.entry("student_academic_ap_course", "AP Courses"),
        Map.entry("student_academic_ib_profile", "IB Profile"),
        Map.entry("student_academic_ib_subject", "IB Subjects"),
        Map.entry("student_academic_chinese_high_school_profile", "Chinese High School Profile"),
        Map.entry("student_academic_chinese_high_school_subject", "Chinese High School Subjects"),
        Map.entry("student_language_ielts", "IELTS"),
        Map.entry("student_language_toefl_ibt", "TOEFL iBT"),
        Map.entry("student_language_toefl_essentials", "TOEFL Essentials"),
        Map.entry("student_language_det", "DET"),
        Map.entry("student_language_pte", "PTE"),
        Map.entry("student_language_languagecert", "LanguageCert"),
        Map.entry("student_language_cambridge", "Cambridge"),
        Map.entry("student_language_other", "Other Language Test"),
        Map.entry("student_standardized_test_records", "Standardized Tests"),
        Map.entry("student_competition_entries", "Competitions"),
        Map.entry("student_activity_entries", "Activities"),
        Map.entry("student_project_entries", "Projects")
    );

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

    private static final Map<String, String> TABLE_ORDER_BY = Map.ofEntries(
        Map.entry("student_basic_info_curriculum_system", "ORDER BY is_primary DESC, curriculum_system_code ASC"),
        Map.entry("student_academic_us_high_school_course", "ORDER BY school_year_label DESC, term_code ASC, student_us_high_school_course_record_id ASC"),
        Map.entry("student_academic_a_level_subject", "ORDER BY al_subject_id ASC, stage_code ASC, is_predicted ASC, exam_series ASC"),
        Map.entry("student_academic_ap_course", "ORDER BY year_taken DESC, ap_course_id ASC"),
        Map.entry("student_academic_ib_subject", "ORDER BY ib_subject_id ASC, level_code ASC, is_predicted ASC"),
        Map.entry("student_academic_chinese_high_school_subject", "ORDER BY chs_subject_id ASC"),
        Map.entry("student_language_ielts", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_toefl_ibt", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_toefl_essentials", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_det", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_pte", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_languagecert", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_cambridge", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_language_other", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_standardized_test_records", "ORDER BY test_date DESC, is_best_score DESC"),
        Map.entry("student_competition_entries", "ORDER BY sort_order ASC, competition_year DESC"),
        Map.entry("student_activity_entries", "ORDER BY sort_order ASC"),
        Map.entry("student_project_entries", "ORDER BY sort_order ASC"),
        Map.entry("student_project_outputs", "ORDER BY project_id ASC, sort_order ASC")
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

    private static final List<Map<String, String>> CURRENT_GRADE_OPTIONS = List.of(
        option("G7", "G7"),
        option("G8", "G8"),
        option("G9", "G9"),
        option("G10", "G10"),
        option("G11", "G11"),
        option("G12", "G12"),
        option("Year 1", "Year 1"),
        option("Year 2", "Year 2"),
        option("Year 3", "Year 3"),
        option("Year 4", "Year 4"),
        option("Gap Year", "Gap Year"),
        option("TRANSFER_YEAR_1", "Transfer Year 1"),
        option("Graduated", "Graduated"),
        option("OTHER", "Other")
    );

    private static final List<Map<String, String>> SCHOOL_CITY_OPTIONS = buildStaticOptions(List.of(
        "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Hangzhou", "Nanjing",
        "Suzhou", "Chengdu", "Chongqing", "Wuhan", "Xian", "Tianjin", "Qingdao",
        "Ningbo", "Changsha", "Zhengzhou", "Hefei", "Xiamen", "Fuzhou", "Nanchang",
        "Kunming", "Haikou", "Hong Kong", "Macau", "Taipei", "Singapore", "London",
        "Manchester", "Edinburgh", "New York", "Boston", "Los Angeles", "Chicago",
        "Toronto", "Vancouver", "Melbourne", "Sydney", "Other"
    ));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> editableColumnsCache = new ConcurrentHashMap<>();

    public BusinessProfileFormService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadBusinessProfileFormBundle(String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("archive_form", loadBusinessProfileSnapshot(studentId));
        payload.put("form_meta", buildBusinessProfileFormMeta());
        return payload;
    }

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
                field.put("label", humanizeFieldName(column.name()));
                field.put("input_type", inferInputType(column.name(), column.type(), options));
                field.put("hidden", isFieldHidden(column.name()));
                field.put("options", options);
                field.put("helper_text", null);
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
        List<ColumnMeta> columns = getEditableColumns(tableName);
        if (CollUtil.isEmpty(columns)) {
            return Map.of();
        }

        String sql = """
            SELECT %s
            FROM `%s`
            WHERE `student_id` = ?
              AND `delete_flag` = '1'
            LIMIT 1
            """.formatted(joinSelectColumns(columns), tableName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, studentId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        return normalizeRow(rows.get(0), columns);
    }

    private List<Map<String, Object>> loadMultiRows(String tableName, String studentId) {
        List<ColumnMeta> columns = getEditableColumns(tableName);
        if (CollUtil.isEmpty(columns)) {
            return List.of();
        }

        String sql;
        if ("student_project_outputs".equals(tableName)) {
            sql = """
                SELECT %s
                FROM `student_project_outputs`
                WHERE `project_id` IN (
                    SELECT `project_id`
                    FROM `student_project_entries`
                    WHERE `student_id` = ?
                      AND `delete_flag` = '1'
                )
                  AND `delete_flag` = '1'
                %s
                """.formatted(joinSelectColumns(columns), TABLE_ORDER_BY.getOrDefault(tableName, ""));
        } else {
            sql = """
                SELECT %s
                FROM `%s`
                WHERE `student_id` = ?
                  AND `delete_flag` = '1'
                %s
                """.formatted(joinSelectColumns(columns), tableName, TABLE_ORDER_BY.getOrDefault(tableName, ""));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, studentId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(normalizeRow(row, columns));
        }
        return result;
    }

    private List<Map<String, Object>> loadGuidedTargetCountryEntries(String studentId) {
        String sql = """
            SELECT country_code, sort_order, is_primary, remark
            FROM student_basic_info_target_country_entries
            WHERE student_id = ?
              AND delete_flag = '1'
            ORDER BY is_primary DESC, sort_order ASC, id ASC
            """;
        List<ColumnMeta> columns = List.of(
            new ColumnMeta("country_code", "varchar"),
            new ColumnMeta("sort_order", "int"),
            new ColumnMeta("is_primary", "tinyint"),
            new ColumnMeta("remark", "varchar")
        );
        return normalizeRows(jdbcTemplate.queryForList(sql, studentId), columns);
    }

    private List<Map<String, Object>> loadGuidedTargetMajorEntries(String studentId) {
        String sql = """
            SELECT major_direction_code, major_direction_label, major_code, sort_order, is_primary
            FROM student_basic_info_target_major_entries
            WHERE student_id = ?
              AND delete_flag = '1'
            ORDER BY is_primary DESC, sort_order ASC, id ASC
            """;
        List<ColumnMeta> columns = List.of(
            new ColumnMeta("major_direction_code", "varchar"),
            new ColumnMeta("major_direction_label", "varchar"),
            new ColumnMeta("major_code", "varchar"),
            new ColumnMeta("sort_order", "int"),
            new ColumnMeta("is_primary", "tinyint")
        );
        return normalizeRows(jdbcTemplate.queryForList(sql, studentId), columns);
    }

    private List<ColumnMeta> getEditableColumns(String tableName) {
        return editableColumnsCache.computeIfAbsent(tableName, this::loadEditableColumns);
    }

    private List<ColumnMeta> loadEditableColumns(String tableName) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW COLUMNS FROM `" + tableName + "`");
        Set<String> excludedColumns = new LinkedHashSet<>(EXCLUDED_COLUMNS_BY_TABLE.getOrDefault(tableName, Set.of()));
        excludedColumns.add("create_time");
        excludedColumns.add("update_time");
        excludedColumns.add("delete_flag");

        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(row.get("Field"));
            if (excludedColumns.contains(fieldName)) {
                continue;
            }
            columns.add(new ColumnMeta(fieldName, String.valueOf(row.get("Type"))));
        }
        return columns;
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

    private String joinSelectColumns(List<ColumnMeta> columns) {
        List<String> names = new ArrayList<>();
        for (ColumnMeta column : columns) {
            names.add("`" + column.name() + "`");
        }
        return String.join(", ", names);
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
        return parseEnumOptions(columnType);
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
        String sql = """
            SELECT `%s` AS value, `%s` AS label_cn, `%s` AS label_en
            FROM `%s`
            WHERE `delete_flag` = '1'
            ORDER BY value ASC
            """.formatted(valueColumn, labelCnColumn, labelEnColumn, tableName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = String.valueOf(row.get("value"));
            String labelCn = row.get("label_cn") == null ? null : String.valueOf(row.get("label_cn"));
            String labelEn = row.get("label_en") == null ? null : String.valueOf(row.get("label_en"));
            result.add(option(value, CharSequenceUtil.blankToDefault(labelCn, CharSequenceUtil.blankToDefault(labelEn, value))));
        }
        return result;
    }

    private List<Map<String, String>> parseEnumOptions(String columnType) {
        String normalized = CharSequenceUtil.nullToEmpty(columnType).trim();
        if (!normalized.startsWith("enum(") || !normalized.endsWith(")")) {
            return null;
        }
        String inner = normalized.substring("enum(".length(), normalized.length() - 1);
        if (CharSequenceUtil.isBlank(inner)) {
            return null;
        }
        List<Map<String, String>> options = new ArrayList<>();
        for (String rawItem : inner.split(",")) {
            String value = rawItem.trim();
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            value = value.replace("''", "'");
            options.add(option(value, value));
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

    private boolean isFieldHidden(String fieldName) {
        return HIDDEN_FIELDS.contains(fieldName);
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
            options.add(option(year + " Spring", year + " Spring"));
            options.add(option(year + " Fall", year + " Fall"));
        }
        return options;
    }

    private static List<Map<String, String>> buildStaticOptions(List<String> values) {
        List<Map<String, String>> options = new ArrayList<>();
        for (String value : values) {
            options.add(option(value, value));
        }
        return options;
    }

    private static Map<String, String> option(String value, String label) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    private static String key(String tableName, String fieldName) {
        return tableName + "." + fieldName;
    }

    private record ColumnMeta(String name, String type) {
    }
}
