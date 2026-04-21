package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.earthseaedu.backend.mapper.AiProfileRadarPendingMapper;
import com.earthseaedu.backend.service.AiProfileRadarPendingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
import org.springframework.stereotype.Service;

/**
 * AiProfileRadarPendingServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiProfileRadarPendingServiceImpl implements AiProfileRadarPendingService {

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

    private final AiProfileRadarPendingMapper pendingMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 AiProfileRadarPendingServiceImpl 实例。
     */
    public AiProfileRadarPendingServiceImpl(AiProfileRadarPendingMapper pendingMapper, ObjectMapper objectMapper) {
        this.pendingMapper = pendingMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
            pendingMapper.insertPendingChange(pendingMutationRow(
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                changedFields,
                affectedDimensions,
                "archive_form_save",
                "archive form saved in java backend",
                1,
                now,
                null
            ));
            return;
        }

        List<String> mergedChangedFields = normalizeStringList(
            combineStringLists(pendingRow.pendingChangedFields(), changedFields)
        );
        List<String> mergedAffectedDimensions = normalizeDimensions(
            combineStringLists(pendingRow.pendingAffectedDimensions(), affectedDimensions)
        );

        pendingMapper.updatePendingChange(pendingMutationRow(
            sessionId,
            studentId,
            bizDomain,
            pendingRow.lastProfileResultId() == null ? lastProfileResultId : pendingRow.lastProfileResultId(),
            mergedChangedFields,
            mergedAffectedDimensions,
            "archive_form_save",
            "archive form saved in java backend",
            pendingRow.versionNo() + 1,
            now,
            pendingRow.pendingId()
        ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
            pendingMapper.insertPendingChange(pendingMutationRow(
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                normalizedChangedFields,
                affectedDimensions,
                resolvedChangeSource,
                resolvedRemark,
                1,
                now,
                null
            ));
        } else {
            List<String> mergedChangedFields = normalizeStringList(
                combineStringLists(pendingRow.pendingChangedFields(), normalizedChangedFields)
            );
            List<String> mergedAffectedDimensions = normalizeDimensions(
                combineStringLists(pendingRow.pendingAffectedDimensions(), affectedDimensions)
            );
            pendingMapper.updatePendingChange(pendingMutationRow(
                sessionId,
                studentId,
                bizDomain,
                pendingRow.lastProfileResultId() == null ? lastProfileResultId : pendingRow.lastProfileResultId(),
                mergedChangedFields,
                mergedAffectedDimensions,
                resolvedChangeSource,
                resolvedRemark,
                pendingRow.versionNo() + 1,
                now,
                pendingRow.pendingId()
            ));
        }

        result.put("affected_dimensions", affectedDimensions);
        result.put("pending_saved", true);
        result.put("last_profile_result_id", lastProfileResultId);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetPendingRadarChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        long lastProfileResultId
    ) {
        PendingRow pendingRow = findPendingRow(sessionId);
        LocalDateTime now = LocalDateTime.now();
        if (pendingRow == null) {
            pendingMapper.insertPendingChange(pendingMutationRow(
                sessionId,
                studentId,
                bizDomain,
                lastProfileResultId,
                List.of(),
                List.of(),
                "reset_after_generation",
                "radar generated by java backend",
                1,
                now,
                null
            ));
            return;
        }

        pendingMapper.updatePendingChange(pendingMutationRow(
            sessionId,
            studentId,
            bizDomain,
            lastProfileResultId,
            List.of(),
            List.of(),
            "reset_after_generation",
            "radar generated by java backend",
            pendingRow.versionNo() + 1,
            now,
            pendingRow.pendingId()
        ));
    }

    private Map<String, Object> pendingMutationRow(
        String sessionId,
        String studentId,
        String bizDomain,
        Long lastProfileResultId,
        Collection<String> changedFields,
        Collection<String> affectedDimensions,
        String changeSource,
        String changeRemark,
        int versionNo,
        LocalDateTime now,
        Long pendingId
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pendingId", pendingId);
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("bizDomain", bizDomain);
        row.put("lastProfileResultId", lastProfileResultId);
        row.put("pendingChangedFieldsJson", toJson(changedFields));
        row.put("pendingAffectedDimensionsJson", toJson(affectedDimensions));
        row.put("lastChangeSource", changeSource);
        row.put("lastChangeRemark", changeRemark);
        row.put("versionNo", versionNo);
        row.put("now", Timestamp.valueOf(now));
        return row;
    }

    private Long findLatestCompleteProfileResultId(String studentId, String sessionId) {
        List<Map<String, Object>> rows = pendingMapper.listProfileResults(studentId, sessionId);

        for (Map<String, Object> row : rows) {
            Map<String, Object> radarScores = parseJsonObject(column(row, "radar_scores_json"));
            if (isCompleteRadarScores(radarScores)) {
                return longValue(column(row, "id"));
            }
        }
        return null;
    }

    private PendingRow findPendingRow(String sessionId) {
        Map<String, Object> row = pendingMapper.findPendingRow(sessionId);
        if (row == null) {
            return null;
        }
        return new PendingRow(
            longValue(column(row, "pending_id")),
            column(row, "last_profile_result_id") == null ? null : longValue(column(row, "last_profile_result_id")),
            normalizeStringList(parseJsonStringList(column(row, "pending_changed_fields_json"))),
            normalizeDimensions(parseJsonStringList(column(row, "pending_affected_dimensions_json"))),
            intValue(column(row, "version_no"))
        );
    }

    private List<String> mapChangedFieldsToAffectedDimensions(String bizDomain, List<String> changedFields) {
        if (changedFields.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = pendingMapper.listImpactRules(bizDomain);

        Map<String, RuleRow> exactRuleMap = new LinkedHashMap<>();
        Map<String, RuleRow> wildcardRuleMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            RuleRow rule = new RuleRow(
                String.valueOf(column(row, "table_name")),
                String.valueOf(column(row, "field_name")),
                intValue(column(row, "affects_radar")),
                normalizeDimensions(parseJsonStringList(column(row, "affected_dimensions_json")))
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
            Object parsed = objectMapper.readValue(jsonText(value), Object.class);
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
            Object parsed = objectMapper.readValue(jsonText(value), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return castMap(map);
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return Collections.emptyMap();
    }

    private String jsonText(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
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

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
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
