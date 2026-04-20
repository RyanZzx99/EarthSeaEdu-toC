package com.earthseaedu.backend.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiProfileRadarPendingService {

    private static final List<String> RADAR_DIMENSIONS = List.of(
        "academic",
        "language",
        "standardized",
        "competition",
        "activity",
        "project"
    );

    private static final Set<String> DIFF_IGNORED_FIELD_NAMES = Set.of(
        "student_id",
        "student_academic_id",
        "student_language_id",
        "student_standardized_test_id",
        "competition_id",
        "activity_id",
        "project_id",
        "project_output_id",
        "output_id",
        "sort_order",
        "create_time",
        "update_time",
        "delete_flag"
    );

    private static final Set<String> PATCH_INTERNAL_FIELD_NAMES = Set.of("_action", "_match_key");
    private static final Map<String, List<String>> SEMANTIC_CHANGED_FIELD_EXPANSIONS = Map.of(
        "student_basic_info.curriculum_system",
        List.of(
            "student_basic_info_curriculum_system.curriculum_system_code",
            "student_basic_info_curriculum_system.is_primary"
        )
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiProfileRadarPendingService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void accumulateArchiveFormChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> previousProfileJson,
        Map<String, Object> currentProfileJson
    ) {
        List<String> changedFields = extractChangedFieldsFromProfileDiff(previousProfileJson, currentProfileJson);
        if (changedFields.isEmpty()) {
            return;
        }

        Long lastProfileResultId = findLatestCompleteProfileResultId(studentId, sessionId);
        if (lastProfileResultId == null) {
            return;
        }

        List<String> affectedDimensions = mapChangedFieldsToAffectedDimensions(bizDomain, changedFields);
        PendingRow pendingRow = findPendingRow(sessionId);
        LocalDateTime now = LocalDateTime.now();

        if (pendingRow == null) {
            jdbcTemplate.update(
                """
                INSERT INTO ai_profile_radar_pending_changes (
                    session_id, student_id, biz_domain, last_profile_result_id,
                    pending_changed_fields_json, pending_affected_dimensions_json,
                    last_change_source, last_change_remark, version_no,
                    create_time, update_time, delete_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1')
                """,
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                toJson(changedFields),
                toJson(affectedDimensions),
                "archive_form_save",
                "archive form saved in java backend",
                1,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            return;
        }

        List<String> mergedChangedFields = normalizeStringList(
            combineStringLists(pendingRow.pendingChangedFields(), changedFields)
        );
        List<String> mergedAffectedDimensions = normalizeDimensions(
            combineStringLists(pendingRow.pendingAffectedDimensions(), affectedDimensions)
        );

        jdbcTemplate.update(
            """
            UPDATE ai_profile_radar_pending_changes
            SET student_id = ?,
                biz_domain = ?,
                last_profile_result_id = ?,
                pending_changed_fields_json = ?,
                pending_affected_dimensions_json = ?,
                last_change_source = ?,
                last_change_remark = ?,
                version_no = ?,
                update_time = ?
            WHERE pending_id = ?
            """,
            studentId,
            bizDomain,
            pendingRow.lastProfileResultId() == null ? lastProfileResultId : pendingRow.lastProfileResultId(),
            toJson(mergedChangedFields),
            toJson(mergedAffectedDimensions),
            "archive_form_save",
            "archive form saved in java backend",
            pendingRow.versionNo() + 1,
            Timestamp.valueOf(now),
            pendingRow.pendingId()
        );
    }

    public Map<String, Object> accumulatePatchChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        Collection<String> changedFields,
        String changeSource,
        String changeRemark
    ) {
        List<String> normalizedChangedFields = normalizeStringList(changedFields);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changed_fields", normalizedChangedFields);

        if (normalizedChangedFields.isEmpty()) {
            result.put("affected_dimensions", List.of());
            result.put("pending_saved", false);
            result.put("reason", "empty_changed_fields");
            return result;
        }

        Long lastProfileResultId = findLatestCompleteProfileResultId(studentId, sessionId);
        if (lastProfileResultId == null) {
            result.put("affected_dimensions", List.of());
            result.put("pending_saved", false);
            result.put("reason", "no_complete_profile_result");
            return result;
        }

        List<String> affectedDimensions = mapChangedFieldsToAffectedDimensions(bizDomain, normalizedChangedFields);
        PendingRow pendingRow = findPendingRow(sessionId);
        LocalDateTime now = LocalDateTime.now();
        String resolvedChangeSource = StrUtil.blankToDefault(changeSource, "ai_dialogue_patch");
        String resolvedRemark = StrUtil.blankToDefault(changeRemark, "AI dialogue patch merged into draft");

        if (pendingRow == null) {
            jdbcTemplate.update(
                """
                INSERT INTO ai_profile_radar_pending_changes (
                    session_id, student_id, biz_domain, last_profile_result_id,
                    pending_changed_fields_json, pending_affected_dimensions_json,
                    last_change_source, last_change_remark, version_no,
                    create_time, update_time, delete_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1')
                """,
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                toJson(normalizedChangedFields),
                toJson(affectedDimensions),
                resolvedChangeSource,
                resolvedRemark,
                1,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
        } else {
            List<String> mergedChangedFields = normalizeStringList(
                combineStringLists(pendingRow.pendingChangedFields(), normalizedChangedFields)
            );
            List<String> mergedAffectedDimensions = normalizeDimensions(
                combineStringLists(pendingRow.pendingAffectedDimensions(), affectedDimensions)
            );
            jdbcTemplate.update(
                """
                UPDATE ai_profile_radar_pending_changes
                SET student_id = ?,
                    biz_domain = ?,
                    last_profile_result_id = ?,
                    pending_changed_fields_json = ?,
                    pending_affected_dimensions_json = ?,
                    last_change_source = ?,
                    last_change_remark = ?,
                    version_no = ?,
                    update_time = ?
                WHERE pending_id = ?
                """,
                studentId,
                bizDomain,
                pendingRow.lastProfileResultId() == null ? lastProfileResultId : pendingRow.lastProfileResultId(),
                toJson(mergedChangedFields),
                toJson(mergedAffectedDimensions),
                resolvedChangeSource,
                resolvedRemark,
                pendingRow.versionNo() + 1,
                Timestamp.valueOf(now),
                pendingRow.pendingId()
            );
        }

        result.put("affected_dimensions", affectedDimensions);
        result.put("pending_saved", true);
        result.put("last_profile_result_id", lastProfileResultId);
        return result;
    }

    public void resetPendingRadarChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        long lastProfileResultId
    ) {
        PendingRow pendingRow = findPendingRow(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (pendingRow == null) {
            jdbcTemplate.update(
                """
                INSERT INTO ai_profile_radar_pending_changes (
                    session_id, student_id, biz_domain, last_profile_result_id,
                    pending_changed_fields_json, pending_affected_dimensions_json,
                    last_change_source, last_change_remark, version_no,
                    create_time, update_time, delete_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1')
                """,
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                "[]",
                "[]",
                "reset_after_generation",
                "radar generated by java backend",
                1,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
            return;
        }

        jdbcTemplate.update(
            """
            UPDATE ai_profile_radar_pending_changes
            SET student_id = ?,
                biz_domain = ?,
                last_profile_result_id = ?,
                pending_changed_fields_json = ?,
                pending_affected_dimensions_json = ?,
                last_change_source = ?,
                last_change_remark = ?,
                version_no = ?,
                update_time = ?
            WHERE pending_id = ?
            """,
            studentId,
            bizDomain,
            lastProfileResultId,
            "[]",
            "[]",
            "reset_after_generation",
            "radar generated by java backend",
            pendingRow.versionNo() + 1,
            Timestamp.valueOf(now),
            pendingRow.pendingId()
        );
    }

    private Long findLatestCompleteProfileResultId(String studentId, String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, radar_scores_json
            FROM ai_chat_profile_results
            WHERE student_id = ?
              AND session_id = ?
              AND delete_flag = '1'
            ORDER BY update_time DESC, id DESC
            """,
            studentId,
            sessionId
        );

        for (Map<String, Object> row : rows) {
            Map<String, Object> radarScores = parseJsonObject(row.get("radar_scores_json"));
            if (isCompleteRadarScores(radarScores)) {
                return ((Number) row.get("id")).longValue();
            }
        }
        return null;
    }

    private PendingRow findPendingRow(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT pending_id, last_profile_result_id, pending_changed_fields_json,
                   pending_affected_dimensions_json, version_no
            FROM ai_profile_radar_pending_changes
            WHERE session_id = ?
              AND delete_flag = '1'
            LIMIT 1
            """,
            sessionId
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new PendingRow(
            ((Number) row.get("pending_id")).longValue(),
            row.get("last_profile_result_id") == null ? null : ((Number) row.get("last_profile_result_id")).longValue(),
            normalizeStringList(parseJsonStringList(row.get("pending_changed_fields_json"))),
            normalizeDimensions(parseJsonStringList(row.get("pending_affected_dimensions_json"))),
            row.get("version_no") == null ? 0 : ((Number) row.get("version_no")).intValue()
        );
    }

    private List<String> mapChangedFieldsToAffectedDimensions(String bizDomain, List<String> changedFields) {
        if (changedFields.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT table_name, field_name, affects_radar, affected_dimensions_json
            FROM ai_profile_radar_field_impact_rules
            WHERE biz_domain = ?
              AND status = 'active'
              AND delete_flag = '1'
            ORDER BY sort_order ASC, id ASC
            """,
            bizDomain
        );

        Map<String, RuleRow> exactRuleMap = new LinkedHashMap<>();
        Map<String, RuleRow> wildcardRuleMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            RuleRow rule = new RuleRow(
                String.valueOf(row.get("table_name")),
                String.valueOf(row.get("field_name")),
                row.get("affects_radar") == null ? 0 : ((Number) row.get("affects_radar")).intValue(),
                normalizeDimensions(parseJsonStringList(row.get("affected_dimensions_json")))
            );
            if ("*".equals(rule.fieldName())) {
                wildcardRuleMap.put(rule.tableName(), rule);
            } else {
                exactRuleMap.put(rule.tableName() + "." + rule.fieldName(), rule);
            }
        }

        LinkedHashSet<String> affected = new LinkedHashSet<>();
        for (String changedField : changedFields) {
            String tableName = splitChangedField(changedField)[0];
            String fieldName = splitChangedField(changedField)[1];
            RuleRow rule = exactRuleMap.get(tableName + "." + fieldName);
            if (rule == null) {
                rule = wildcardRuleMap.get(tableName);
            }
            if (rule == null || rule.affectsRadar() != 1) {
                continue;
            }
            for (String dimension : rule.affectedDimensions()) {
                affected.add(dimension);
            }
        }

        return affected.stream()
            .sorted((left, right) -> Integer.compare(radarOrder(left), radarOrder(right)))
            .toList();
    }

    private List<String> extractChangedFieldsFromProfileDiff(
        Map<String, Object> previousProfileJson,
        Map<String, Object> currentProfileJson
    ) {
        Map<String, Object> previous = previousProfileJson == null ? Collections.emptyMap() : previousProfileJson;
        Map<String, Object> current = currentProfileJson == null ? Collections.emptyMap() : currentProfileJson;

        Set<String> tableNames = new LinkedHashSet<>();
        tableNames.addAll(previous.keySet());
        tableNames.addAll(current.keySet());

        LinkedHashSet<String> changedFields = new LinkedHashSet<>();
        for (String tableName : tableNames) {
            Object previousValue = previous.get(tableName);
            Object currentValue = current.get(tableName);

            if (previousValue instanceof Map<?, ?> || currentValue instanceof Map<?, ?>) {
                Map<String, Object> previousRow = previousValue instanceof Map<?, ?> map ? castMap(map) : Collections.emptyMap();
                Map<String, Object> currentRow = currentValue instanceof Map<?, ?> map ? castMap(map) : Collections.emptyMap();

                Set<String> fieldNames = new LinkedHashSet<>();
                fieldNames.addAll(previousRow.keySet());
                fieldNames.addAll(currentRow.keySet());
                for (String fieldName : fieldNames) {
                    if (shouldSkipChangedField(fieldName)) {
                        continue;
                    }
                    Object normalizedPrevious = normalizeScalarForDiff(previousRow.get(fieldName));
                    Object normalizedCurrent = normalizeScalarForDiff(currentRow.get(fieldName));
                    if (!normalizedPrevious.equals(normalizedCurrent)) {
                        addChangedFieldWithExpansion(changedFields, tableName, fieldName);
                    }
                }
                continue;
            }

            if (previousValue instanceof List<?> || currentValue instanceof List<?>) {
                List<Object> previousRows = previousValue instanceof List<?> list ? new ArrayList<>(list) : List.of();
                List<Object> currentRows = currentValue instanceof List<?> list ? new ArrayList<>(list) : List.of();
                if (canonicalizeMultiRowsForDiff(previousRows).equals(canonicalizeMultiRowsForDiff(currentRows))) {
                    continue;
                }
                Set<String> fieldNames = new LinkedHashSet<>();
                fieldNames.addAll(collectMultiRowFieldNames(previousRows));
                fieldNames.addAll(collectMultiRowFieldNames(currentRows));
                for (String fieldName : fieldNames) {
                    addChangedFieldWithExpansion(changedFields, tableName, fieldName);
                }
                continue;
            }

            if (!normalizeScalarForDiff(previousValue).equals(normalizeScalarForDiff(currentValue))) {
                changedFields.add(tableName + ".*");
            }
        }

        return normalizeStringList(changedFields);
    }

    private List<Map<String, Object>> canonicalizeMultiRowsForDiff(List<Object> rows) {
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> cleanedRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : castMap(map).entrySet()) {
                if (shouldSkipChangedField(entry.getKey())) {
                    continue;
                }
                cleanedRow.put(entry.getKey(), normalizeScalarForDiff(entry.getValue()));
            }
            if (!cleanedRow.isEmpty()) {
                normalizedRows.add(cleanedRow);
            }
        }
        normalizedRows.sort((left, right) -> toJson(left).compareTo(toJson(right)));
        return normalizedRows;
    }

    private Set<String> collectMultiRowFieldNames(List<Object> rows) {
        LinkedHashSet<String> fieldNames = new LinkedHashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            for (String fieldName : castMap(map).keySet()) {
                if (!shouldSkipChangedField(fieldName)) {
                    fieldNames.add(fieldName);
                }
            }
        }
        return fieldNames;
    }

    private void addChangedFieldWithExpansion(Set<String> changedFields, String tableName, String fieldName) {
        changedFields.add(tableName + "." + fieldName);
        for (String expandedField : SEMANTIC_CHANGED_FIELD_EXPANSIONS.getOrDefault(tableName + "." + fieldName, List.of())) {
            changedFields.add(expandedField);
        }
    }

    private boolean shouldSkipChangedField(String fieldName) {
        if (StrUtil.isBlank(fieldName)) {
            return true;
        }
        return PATCH_INTERNAL_FIELD_NAMES.contains(fieldName) || DIFF_IGNORED_FIELD_NAMES.contains(fieldName);
    }

    private Object normalizeScalarForDiff(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            castMap(map).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> normalized.put(entry.getKey(), normalizeScalarForDiff(entry.getValue())));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : list) {
                normalized.add(normalizeScalarForDiff(item));
            }
            return normalized;
        }
        if (value instanceof Set<?> set) {
            return set.stream()
                .map(this::normalizeScalarForDiff)
                .sorted((left, right) -> toJson(left).compareTo(toJson(right)))
                .toList();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof String stringValue) {
            return stringValue.trim();
        }
        return value == null ? "" : value;
    }

    private boolean isCompleteRadarScores(Map<String, Object> radarScoresJson) {
        if (radarScoresJson == null) {
            return false;
        }
        for (String dimension : RADAR_DIMENSIONS) {
            Object item = radarScoresJson.get(dimension);
            if (!(item instanceof Map<?, ?> map)) {
                return false;
            }
            if (!castMap(map).containsKey("score")) {
                return false;
            }
        }
        return true;
    }

    private List<String> parseJsonStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(item -> item == null ? null : String.valueOf(item))
                .filter(StrUtil::isNotBlank)
                .toList();
        }
        try {
            Object parsed = objectMapper.readValue(String.valueOf(value), Object.class);
            if (parsed instanceof Collection<?> collection) {
                return collection.stream()
                    .map(item -> item == null ? null : String.valueOf(item))
                    .filter(StrUtil::isNotBlank)
                    .toList();
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private Map<String, Object> parseJsonObject(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        try {
            Object parsed = objectMapper.readValue(String.valueOf(value), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return castMap(map);
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return Collections.emptyMap();
    }

    private List<String> normalizeStringList(Collection<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                normalized.add(value.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeDimensions(Collection<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            String trimmed = value.trim();
            if (RADAR_DIMENSIONS.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> combineStringLists(Collection<String> left, Collection<String> right) {
        List<String> merged = new ArrayList<>();
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }

    private int radarOrder(String dimension) {
        int index = RADAR_DIMENSIONS.indexOf(dimension);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private String[] splitChangedField(String changedField) {
        String normalized = StrUtil.nullToEmpty(changedField);
        int separatorIndex = normalized.indexOf('.');
        if (separatorIndex < 0) {
            return new String[]{normalized.trim(), "*"};
        }
        return new String[]{
            normalized.substring(0, separatorIndex).trim(),
            StrUtil.blankToDefault(normalized.substring(separatorIndex + 1).trim(), "*")
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize json", exception);
        }
    }

    private record PendingRow(
        long pendingId,
        Long lastProfileResultId,
        List<String> pendingChangedFields,
        List<String> pendingAffectedDimensions,
        int versionNo
    ) {
    }

    private record RuleRow(
        String tableName,
        String fieldName,
        int affectsRadar,
        List<String> affectedDimensions
    ) {
    }
}
