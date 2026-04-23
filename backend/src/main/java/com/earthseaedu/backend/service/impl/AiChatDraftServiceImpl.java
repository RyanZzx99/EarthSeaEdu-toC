package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.service.AiChatDraftService;
import com.earthseaedu.backend.service.AiProfileRadarPendingService;
import com.earthseaedu.backend.service.AiPromptRuntimeService;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.BusinessProfilePersistenceService;
import com.earthseaedu.backend.service.JwtService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiChatDraftServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class AiChatDraftServiceImpl implements AiChatDraftService {

    private static final String DEFAULT_STAGE = "idle";
    private static final String BUILD_READY_STAGE = "build_ready";
    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final String DRAFT_PATCH_PROMPT_KEY = "student_profile_build.draft_patch_extraction";
    private static final String SCORING_PROMPT_KEY = "student_profile_build.scoring";
    private static final Set<String> BUSY_STAGES = Set.of(
        "progress_updating",
        "extraction",
        "scoring",
        "profile_saving"
    );
    private static final List<String> DIMENSION_ORDER = List.of(
        "academic",
        "language",
        "standardized",
        "competition",
        "activity",
        "project"
    );
    private static final Set<String> ARCHIVE_PROGRESS_IGNORED_FIELDS = Set.of(
        "student_id",
        "student_academic_id",
        "student_language_id",
        "student_standardized_test_id",
        "project_id"
    );
    private static final Set<String> PROFILE_TABLE_NAMES = Set.of(
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
        "student_projects_experience",
        "student_basic_info_curriculum_system",
        "student_basic_info_target_country_entries",
        "student_basic_info_target_major_entries",
        "student_academic_curriculum_gpa",
        "student_academic_a_level_subject",
        "student_academic_ap_course",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
        "student_academic_us_high_school_course",
        "student_academic_ossd_subject",
        "student_academic_other_curriculum_subject",
        "student_language_ielts",
        "student_language_toefl_ibt",
        "student_language_toefl_home",
        "student_language_det",
        "student_language_pte",
        "student_language_languagecert_academic",
        "student_language_cambridge",
        "student_language_other",
        "student_standardized_test_records",
        "student_competition_entries",
        "student_activity_entries",
        "student_project_entries",
        "student_project_outputs"
    );
    private static final Set<String> PATCH_INTERNAL_FIELD_NAMES = Set.of("_action", "_match_key");

    private final JwtService jwtService;
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final AiProfileRadarPendingService aiProfileRadarPendingService;
    private final AiPromptRuntimeService aiPromptRuntimeService;

    /**
     * 创建 AiChatDraftServiceImpl 实例。
     */
    public AiChatDraftServiceImpl(
        JwtService jwtService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        BusinessProfileFormService businessProfileFormService,
        BusinessProfilePersistenceService businessProfilePersistenceService,
        AiProfileRadarPendingService aiProfileRadarPendingService,
        AiPromptRuntimeService aiPromptRuntimeService
    ) {
        this.jwtService = jwtService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.aiProfileRadarPendingService = aiProfileRadarPendingService;
        this.aiPromptRuntimeService = aiPromptRuntimeService;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> getDraftDetail(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        AiChatDraftRow draftRow = findDraftBySessionId(sessionId);
        if (draftRow != null) {
            return serializeDraftRow(draftRow);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("student_id", studentId);
        result.put("biz_domain", StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN));
        result.put("draft_exists", false);
        result.put("source_round", 0);
        result.put("version_no", 0);
        result.put("draft_json", businessProfileFormService.loadBusinessProfileSnapshot(studentId));
        result.put("last_patch_json", null);
        result.put("create_time", null);
        result.put("update_time", null);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Map<String, Object> syncFromOfficialSnapshot(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        if (BUSY_STAGES.contains(StrUtil.nullToEmpty(session.currentStage()))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "current session is still processing, please retry after it finishes"
            );
        }

        Map<String, Object> officialSnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);
        Map<String, Object> lastPatchJson = new LinkedHashMap<>();
        lastPatchJson.put("sync_source", "official_snapshot");
        lastPatchJson.put("remark", "official snapshot synced into draft by java backend");

        upsertProfileDraft(
            studentId,
            sessionId,
            session.bizDomain(),
            officialSnapshot,
            lastPatchJson,
            session.currentRound()
        );
        updateSessionProgress(studentId, sessionId, buildArchiveSnapshotProgressHint(officialSnapshot));

        AiChatDraftRow refreshedDraft = findDraftBySessionId(sessionId);
        if (refreshedDraft == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "profile draft was not persisted");
        }
        return serializeDraftRow(refreshedDraft);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Map<String, Object> extractLatestPatch(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        ensureNotBusy(session, "current session is still processing, please retry after it finishes");

        Map<String, Object> currentDraft = loadOrInitializeDraft(studentId, sessionId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("student_id", studentId);
        context.put("session_id", sessionId);
        context.put("latest_round_messages", loadRecentVisibleMessages(sessionId, 12));
        context.put("current_progress_json", loadLatestProgressState(sessionId));
        context.put("current_draft_json", currentDraft);

        AiPromptRuntimeService.PromptRuntimeResult runtimeResult = aiPromptRuntimeService.executePrompt(
            DRAFT_PATCH_PROMPT_KEY,
            context,
            List.of("active", "draft")
        );
        Map<String, Object> rawPatchJson = aiPromptRuntimeService.parseJsonObject(
            runtimeResult.content(),
            "draft_patch_extraction"
        );
        Map<String, Object> patchJson = extractProfilePatch(rawPatchJson);
        Map<String, Object> mergedDraftJson = mergeDraftPatchDocument(currentDraft, patchJson, studentId);
        upsertProfileDraft(
            studentId,
            sessionId,
            StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
            mergedDraftJson,
            rawPatchJson,
            session.currentRound()
        );
        Map<String, Object> progressHint = buildArchiveSnapshotProgressHint(mergedDraftJson);
        updateSessionProgress(studentId, sessionId, progressHint);

        List<String> changedFields = extractChangedFieldsFromPatchJson(patchJson);
        Map<String, Object> pendingChangeResult = aiProfileRadarPendingService.accumulatePatchChanges(
            studentId,
            sessionId,
            StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
            changedFields,
            "ai_dialogue_patch",
            "AI dialogue patch merged into draft by java backend"
        );

        AiChatDraftRow refreshedDraft = findDraftBySessionId(sessionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", sessionId);
        response.put("student_id", studentId);
        response.put("biz_domain", StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN));
        response.put("prompt_key", DRAFT_PATCH_PROMPT_KEY);
        response.put("prompt_allowed_statuses", List.of("active", "draft"));
        response.put("patch_json", rawPatchJson);
        response.put("draft_json", refreshedDraft == null ? mergedDraftJson : refreshedDraft.draftJson());
        response.put("source_round", refreshedDraft == null ? session.currentRound() : refreshedDraft.sourceRound());
        response.put("version_no", refreshedDraft == null ? 1 : refreshedDraft.versionNo());
        response.put("create_time", refreshedDraft == null ? null : formatDateTime(refreshedDraft.createTime()));
        response.put("update_time", refreshedDraft == null ? null : formatDateTime(refreshedDraft.updateTime()));
        response.put("changed_fields", changedFields);
        response.put("pending_change_result", pendingChangeResult);
        response.put("progress_hint", progressHint);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Map<String, Object> regenerateDraftRadar(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        ensureNotBusy(session, "current session is still processing, please retry after it finishes");

        updateSessionStage(studentId, sessionId, "scoring", null);
        try {
            Map<String, Object> draftJson = loadOrInitializeDraft(studentId, sessionId);
            Map<String, Object> officialProfileJson = deepCopyMap(draftJson);
            Map<String, Object> scoringJson = executeScoring(
                studentId,
                sessionId,
                officialProfileJson,
                "full",
                List.of(),
                Map.of(),
                null
            );

            businessProfilePersistenceService.persistArchiveFormSnapshot(officialProfileJson, studentId);
            Map<String, Object> persistedSnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);
            long profileResultId = saveProfileResult(
                studentId,
                sessionId,
                StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
                persistedSnapshot,
                toMutableMap(scoringJson.get("radar_scores_json")),
                stringOrNull(scoringJson.get("summary_text")),
                "saved",
                persistedSnapshot,
                null,
                BUILD_READY_STAGE,
                "radar regenerated from draft by java backend"
            );
            aiProfileRadarPendingService.resetPendingRadarChanges(
                studentId,
                sessionId,
                StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
                profileResultId
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("session_id", sessionId);
            response.put("student_id", studentId);
            response.put("biz_domain", StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN));
            response.put("profile_result_id", profileResultId);
            response.put("result_status", "saved");
            response.put("profile_json", persistedSnapshot);
            response.put("radar_scores_json", toMutableMap(scoringJson.get("radar_scores_json")));
            response.put("summary_text", stringOrNull(scoringJson.get("summary_text")));
            response.put("regeneration_mode", "full");
            response.put("affected_dimensions", List.of());
            response.put("changed_fields", List.of());
            return response;
        } catch (RuntimeException exception) {
            updateSessionStage(studentId, sessionId, BUILD_READY_STAGE, trimRemark(exception.getMessage()));
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public Map<String, Object> regenerateArchiveRadar(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        ensureNotBusy(session, "current session is still processing, please retry after it finishes");

        updateSessionStage(studentId, sessionId, "scoring", null);
        try {
            Map<String, Object> officialSnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);
            Map<String, Object> scoringJson = executeScoring(
                studentId,
                sessionId,
                officialSnapshot,
                "full",
                List.of(),
                Map.of(),
                null
            );
            long profileResultId = saveProfileResult(
                studentId,
                sessionId,
                StrUtil.blankToDefault(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
                officialSnapshot,
                toMutableMap(scoringJson.get("radar_scores_json")),
                stringOrNull(scoringJson.get("summary_text")),
                "saved",
                officialSnapshot,
                null,
                BUILD_READY_STAGE,
                null
            );

            Map<String, Object> formBundle = businessProfileFormService.loadBusinessProfileFormBundle(studentId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("session_id", sessionId);
            response.put("archive_form", toMutableMap(formBundle.get("archive_form")));
            response.put("form_meta", toMutableMap(formBundle.get("form_meta")));
            response.put("profile_result_id", profileResultId);
            response.put("result_status", "saved");
            response.put("profile_json", officialSnapshot);
            response.put("summary_text", stringOrNull(scoringJson.get("summary_text")));
            response.put("radar_scores_json", toMutableMap(scoringJson.get("radar_scores_json")));
            response.put("db_payload_json", officialSnapshot);
            response.put("save_error_message", null);
            response.put("regeneration_mode", "full");
            response.put("affected_dimensions", List.of());
            response.put("changed_fields", List.of());
            AiChatProfileResultRow refreshedResult = findProfileResultBySessionId(sessionId);
            response.put("create_time", refreshedResult == null ? null : formatDateTime(refreshedResult.createTime()));
            response.put("update_time", refreshedResult == null ? null : formatDateTime(refreshedResult.updateTime()));
            return response;
        } catch (RuntimeException exception) {
            updateSessionStage(studentId, sessionId, BUILD_READY_STAGE, trimRemark(exception.getMessage()));
            throw exception;
        }
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

    private AiChatSessionRow findSessionById(String sessionId) {
        return toSessionRow(aiChatPersistenceMapper.findSessionById(sessionId));
    }

    private AiChatDraftRow findDraftBySessionId(String sessionId) {
        return toDraftRow(aiChatPersistenceMapper.findDraftBySessionId(sessionId));
    }

    private void upsertProfileDraft(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> draftJson,
        Map<String, Object> lastPatchJson,
        int sourceRound
    ) {
        AiChatDraftRow existingDraft = findDraftBySessionId(sessionId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (existingDraft == null) {
            aiChatPersistenceMapper.insertProfileDraft(profileDraftMutationRow(
                studentId,
                sessionId,
                bizDomain,
                draftJson,
                lastPatchJson,
                sourceRound,
                1,
                now,
                null
            ));
            return;
        }

        aiChatPersistenceMapper.updateProfileDraft(profileDraftMutationRow(
            studentId,
            sessionId,
            bizDomain,
            draftJson,
            lastPatchJson,
            sourceRound,
            existingDraft.versionNo() + 1,
            now,
            existingDraft.draftId()
        ));
    }

    private void updateSessionProgress(String studentId, String sessionId, Map<String, Object> progressHint) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Object missingDimensions = progressHint.get("missing_dimensions");
        String currentStage = Boolean.TRUE.equals(progressHint.get("stop_ready")) ? BUILD_READY_STAGE : DEFAULT_STAGE;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("collectedSlotsJson", "{}");
        row.put("missingDimensionsJson", JSONUtil.toJsonStr(missingDimensions == null ? List.of() : missingDimensions));
        row.put("currentStage", currentStage);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionProgress(row);
    }

    private void ensureNotBusy(AiChatSessionRow session, String message) {
        if (BUSY_STAGES.contains(StrUtil.nullToEmpty(session.currentStage()))) {
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
    }

    private Map<String, Object> loadOrInitializeDraft(String studentId, String sessionId) {
        AiChatDraftRow draftRow = findDraftBySessionId(sessionId);
        if (draftRow != null && draftRow.draftJson() instanceof Map<?, ?> map) {
            return deepCopyMap(toMutableMap(map));
        }
        return businessProfileFormService.loadBusinessProfileSnapshot(studentId);
    }

    private List<Map<String, Object>> loadRecentVisibleMessages(String sessionId, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>(aiChatPersistenceMapper.listRecentVisibleMessages(
            sessionId,
            Math.max(1, Math.min(limit, 50))
        ));
        Collections.reverse(rows);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("message_role", column(row, "message_role"));
            item.put("message_type", column(row, "message_type"));
            item.put("sequence_no", column(row, "sequence_no"));
            item.put("content", column(row, "content"));
            item.put("create_time", column(row, "create_time") == null ? null : String.valueOf(column(row, "create_time")));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> loadLatestProgressState(String sessionId) {
        Map<String, Object> row = aiChatPersistenceMapper.findLatestProgressState(sessionId);
        if (row == null) {
            return Collections.emptyMap();
        }
        return toMutableMap(parseJsonValue(jsonText(column(row, "content_json"))));
    }

    private Map<String, Object> extractProfilePatch(Map<String, Object> rawPatchJson) {
        Object candidate = firstMapValue(
            rawPatchJson,
            List.of("patch_json", "draft_patch_json", "draft_patch", "profile_patch", "changes")
        );
        Map<String, Object> source = candidate instanceof Map<?, ?> map ? toMutableMap(map) : rawPatchJson;
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (PROFILE_TABLE_NAMES.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }

    private Object firstMapValue(Map<String, Object> source, List<String> keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map<?, ?>) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> mergeDraftPatchDocument(
        Map<String, Object> currentDraftJson,
        Map<String, Object> patchJson,
        String studentId
    ) {
        Map<String, Object> merged = deepCopyMap(currentDraftJson);
        for (Map.Entry<String, Object> entry : patchJson.entrySet()) {
            if (!PROFILE_TABLE_NAMES.contains(entry.getKey())) {
                continue;
            }
            Object currentValue = merged.get(entry.getKey());
            Object patchValue = entry.getValue();
            if (patchValue instanceof Map<?, ?> patchMap) {
                merged.put(entry.getKey(), mergeMapValue(toMutableMap(currentValue), toMutableMap(patchMap)));
            } else if (patchValue instanceof List<?> patchList) {
                merged.put(entry.getKey(), mergeListValue(toObjectList(currentValue), patchList));
            } else {
                merged.put(entry.getKey(), patchValue);
            }
        }
        Map<String, Object> basicInfo = toMutableMap(merged.get("student_basic_info"));
        basicInfo.put("student_id", studentId);
        merged.put("student_basic_info", basicInfo);
        return merged;
    }

    private Map<String, Object> mergeMapValue(Map<String, Object> currentValue, Map<String, Object> patchValue) {
        Map<String, Object> result = new LinkedHashMap<>(currentValue);
        for (Map.Entry<String, Object> entry : patchValue.entrySet()) {
            if (PATCH_INTERNAL_FIELD_NAMES.contains(entry.getKey())) {
                continue;
            }
            Object existingValue = result.get(entry.getKey());
            Object nextValue = entry.getValue();
            if (existingValue instanceof Map<?, ?> && nextValue instanceof Map<?, ?> nextMap) {
                result.put(entry.getKey(), mergeMapValue(toMutableMap(existingValue), toMutableMap(nextMap)));
            } else {
                result.put(entry.getKey(), nextValue);
            }
        }
        return result;
    }

    private List<Object> mergeListValue(List<Object> currentRows, List<?> patchRows) {
        List<Object> result = new ArrayList<>();
        for (Object row : currentRows) {
            if (row instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>(toMutableMap(map)));
            } else {
                result.add(row);
            }
        }

        for (Object patchRowValue : patchRows) {
            if (!(patchRowValue instanceof Map<?, ?> patchRowMap)) {
                result.add(patchRowValue);
                continue;
            }
            Map<String, Object> patchRow = toMutableMap(patchRowMap);
            String action = StrUtil.nullToEmpty(String.valueOf(patchRow.get("_action"))).trim().toLowerCase();
            Object matchKey = patchRow.get("_match_key");
            int matchIndex = findMatchingRowIndex(result, matchKey, patchRow);
            if ("delete".equals(action)) {
                if (matchIndex >= 0) {
                    result.remove(matchIndex);
                }
                continue;
            }
            patchRow.remove("_action");
            patchRow.remove("_match_key");
            if (matchIndex >= 0 && result.get(matchIndex) instanceof Map<?, ?> existingRow) {
                result.set(matchIndex, mergeMapValue(toMutableMap(existingRow), patchRow));
            } else if (!patchRow.isEmpty()) {
                result.add(patchRow);
            }
        }
        return result;
    }

    private int findMatchingRowIndex(List<Object> rows, Object matchKey, Map<String, Object> patchRow) {
        Map<String, Object> matchFields = matchKey instanceof Map<?, ?> map ? toMutableMap(map) : inferRowIdentity(patchRow);
        if (matchFields.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < rows.size(); index++) {
            if (!(rows.get(index) instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> currentRow = toMutableMap(row);
            boolean matched = true;
            for (Map.Entry<String, Object> entry : matchFields.entrySet()) {
                if (!StrUtil.equals(String.valueOf(currentRow.get(entry.getKey())), String.valueOf(entry.getValue()))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, Object> inferRowIdentity(Map<String, Object> row) {
        for (String key : List.of(
            "student_academic_id",
            "student_language_id",
            "student_standardized_test_id",
            "competition_id",
            "activity_id",
            "project_id",
            "output_id",
            "project_output_id"
        )) {
            Object value = row.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                return Map.of(key, value);
            }
        }
        return Collections.emptyMap();
    }

    private List<String> extractChangedFieldsFromPatchJson(Map<String, Object> patchJson) {
        LinkedHashSet<String> changedFields = new LinkedHashSet<>();
        for (Map.Entry<String, Object> tableEntry : patchJson.entrySet()) {
            String tableName = tableEntry.getKey();
            if (!PROFILE_TABLE_NAMES.contains(tableName)) {
                continue;
            }
            Object value = tableEntry.getValue();
            if (value instanceof Map<?, ?> map) {
                collectChangedFieldsFromRow(changedFields, tableName, toMutableMap(map));
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> row) {
                        collectChangedFieldsFromRow(changedFields, tableName, toMutableMap(row));
                    }
                }
            } else {
                changedFields.add(tableName + ".*");
            }
        }
        return new ArrayList<>(changedFields);
    }

    private void collectChangedFieldsFromRow(Set<String> changedFields, String tableName, Map<String, Object> row) {
        for (String fieldName : row.keySet()) {
            if (PATCH_INTERNAL_FIELD_NAMES.contains(fieldName) || ARCHIVE_PROGRESS_IGNORED_FIELDS.contains(fieldName)) {
                continue;
            }
            changedFields.add(tableName + "." + fieldName);
        }
    }

    private Map<String, Object> executeScoring(
        String studentId,
        String sessionId,
        Map<String, Object> profileJson,
        String scoreMode,
        List<Object> affectedDimensions,
        Map<String, Object> previousRadarScoresJson,
        String previousSummaryText
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("student_id", studentId);
        context.put("session_id", sessionId);
        context.put("profile_json", profileJson);
        context.put("score_mode", scoreMode);
        context.put("affected_dimensions", affectedDimensions == null ? List.of() : affectedDimensions);
        context.put("previous_radar_scores_json", previousRadarScoresJson == null ? Map.of() : previousRadarScoresJson);
        context.put("previous_summary_text", previousSummaryText);

        AiPromptRuntimeService.PromptRuntimeResult runtimeResult = aiPromptRuntimeService.executePrompt(
            SCORING_PROMPT_KEY,
            context,
            List.of("active")
        );
        Map<String, Object> scoringJson = aiPromptRuntimeService.parseJsonObject(runtimeResult.content(), "profile_scoring");
        Map<String, Object> normalized = new LinkedHashMap<>(scoringJson);
        if (!normalized.containsKey("radar_scores_json") && normalized.containsKey("radar_scores")) {
            normalized.put("radar_scores_json", normalized.get("radar_scores"));
        }
        if (!normalized.containsKey("radar_scores_json")) {
            normalized.put("radar_scores_json", Collections.emptyMap());
        }
        return normalized;
    }

    private long saveProfileResult(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> profileJson,
        Map<String, Object> radarScoresJson,
        String summaryText,
        String resultStatus,
        Map<String, Object> dbPayloadJson,
        String saveErrorMessage,
        String sessionStage,
        String sessionRemark
    ) {
        AiChatProfileResultRow existing = findProfileResultBySessionId(sessionId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (existing == null) {
            aiChatPersistenceMapper.insertProfileResult(profileResultMutationRow(
                studentId,
                sessionId,
                bizDomain,
                profileJson,
                radarScoresJson,
                summaryText,
                resultStatus,
                dbPayloadJson,
                saveErrorMessage,
                now,
                null
            ));
        } else {
            aiChatPersistenceMapper.updateProfileResult(profileResultMutationRow(
                studentId,
                sessionId,
                bizDomain,
                profileJson,
                radarScoresJson,
                summaryText,
                resultStatus,
                dbPayloadJson,
                saveErrorMessage,
                now,
                existing.profileResultId()
            ));
        }

        AiChatProfileResultRow refreshed = findProfileResultBySessionId(sessionId);
        if (refreshed == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "profile result was not persisted");
        }
        Map<String, Object> sessionRow = new LinkedHashMap<>();
        sessionRow.put("sessionId", sessionId);
        sessionRow.put("studentId", studentId);
        sessionRow.put("finalProfileId", refreshed.profileResultId());
        sessionRow.put("currentStage", sessionStage);
        sessionRow.put("remark", sessionRemark);
        sessionRow.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionFinalProfile(sessionRow);
        return refreshed.profileResultId();
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        return toProfileResultRow(aiChatPersistenceMapper.findLatestProfileResultBySessionId(sessionId));
    }

    private void updateSessionStage(String studentId, String sessionId, String currentStage, String remark) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("currentStage", currentStage);
        row.put("remark", remark);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionStage(row);
    }

    private Map<String, Object> buildArchiveSnapshotProgressHint(Map<String, Object> archiveSnapshot) {
        Map<String, Object> basicInfo = toMapOrEmpty(archiveSnapshot.get("student_basic_info"));
        List<Object> curriculumRows = toListOrEmpty(archiveSnapshot.get("student_basic_info_curriculum_system"));

        Map<String, Object> basicInfoProgress = new LinkedHashMap<>();
        basicInfoProgress.put("current_grade", progressEntry(false, null));
        basicInfoProgress.put("target_country", progressEntry(false, null));
        basicInfoProgress.put("major_interest", progressEntry(false, null));
        basicInfoProgress.put("curriculum_system", progressEntry(false, null));

        setProgressEntryIfNotBlank(basicInfoProgress, "current_grade", basicInfo.get("current_grade"));
        setProgressEntryIfNotBlank(basicInfoProgress, "target_country", basicInfo.get("CTRY_CODE_VAL"));
        Object majorInterest = firstNonBlank(basicInfo.get("MAJ_INTEREST_TEXT"), basicInfo.get("MAJ_CODE_VAL"));
        setProgressEntryIfNotBlank(basicInfoProgress, "major_interest", majorInterest);
        setProgressEntryIfNotBlank(basicInfoProgress, "curriculum_system", firstCurriculumCode(curriculumRows));

        boolean academicHasContent = anyTableHasContent(
            archiveSnapshot,
            List.of(
                "student_academic",
                "student_academic_curriculum_gpa",
                "student_academic_chinese_high_school_subject",
                "student_academic_a_level_subject",
                "student_academic_ap_profile",
                "student_academic_ap_course",
                "student_academic_ib_profile",
                "student_academic_ib_subject",
                "student_academic_us_high_school_course",
                "student_academic_ossd_subject",
                "student_academic_other_curriculum_subject"
            )
        );
        boolean languageHasContent = anyTableHasContent(
            archiveSnapshot,
            List.of(
                "student_language",
                "student_language_ielts",
                "student_language_toefl_ibt",
                "student_language_toefl_home",
                "student_language_det",
                "student_language_pte",
                "student_language_languagecert_academic",
                "student_language_cambridge",
                "student_language_other"
            )
        );
        boolean standardizedHasContent = archiveValueHasMeaningfulContent(archiveSnapshot.get("student_standardized_tests"))
            || archiveValueHasMeaningfulContent(archiveSnapshot.get("student_standardized_test_records"));
        boolean standardizedIsNotApplicable = isFalseLike(toMapOrEmpty(archiveSnapshot.get("student_standardized_tests")).get("is_applicable"));
        boolean competitionHasContent = anyTableHasContent(archiveSnapshot, List.of("student_competitions", "student_competition_entries"));
        boolean activityHasContent = anyTableHasContent(archiveSnapshot, List.of("student_activities", "student_activity_entries"));
        boolean projectHasContent = anyTableHasContent(
            archiveSnapshot,
            List.of("student_projects_experience", "student_project_entries", "student_project_outputs")
        );

        Map<String, Object> dimensionProgress = new LinkedHashMap<>();
        dimensionProgress.put("academic", dimensionEntry(academicHasContent ? "sufficient" : "missing", academicHasContent));
        dimensionProgress.put("language", dimensionEntry(languageHasContent ? "sufficient" : "missing", languageHasContent));
        dimensionProgress.put(
            "standardized",
            dimensionEntry(standardizedIsNotApplicable ? "not_applicable" : (standardizedHasContent ? "sufficient" : "missing"), standardizedHasContent)
        );
        dimensionProgress.put("competition", dimensionEntry(competitionHasContent ? "sufficient" : "missing", competitionHasContent));
        dimensionProgress.put("activity", dimensionEntry(activityHasContent ? "sufficient" : "missing", activityHasContent));
        dimensionProgress.put("project", dimensionEntry(projectHasContent ? "sufficient" : "missing", projectHasContent));

        List<String> missingDimensions = new ArrayList<>();
        for (String dimension : DIMENSION_ORDER) {
            Map<String, Object> entry = toMapOrEmpty(dimensionProgress.get(dimension));
            if ("missing".equals(entry.get("status"))) {
                missingDimensions.add(dimension);
            }
        }
        boolean stopReady = computeBuildReadyState(basicInfoProgress, missingDimensions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basic_info_progress", basicInfoProgress);
        result.put("dimension_progress", dimensionProgress);
        result.put("missing_dimensions", missingDimensions);
        result.put("next_question_focus", stopReady || missingDimensions.isEmpty() ? null : missingDimensions.get(0));
        result.put("stop_ready", stopReady);
        return result;
    }

    private Map<String, Object> serializeDraftRow(AiChatDraftRow draftRow) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", draftRow.sessionId());
        result.put("student_id", draftRow.studentId());
        result.put("biz_domain", draftRow.bizDomain());
        result.put("draft_exists", true);
        result.put("source_round", draftRow.sourceRound());
        result.put("version_no", draftRow.versionNo());
        result.put("draft_json", draftRow.draftJson());
        result.put("last_patch_json", draftRow.lastPatchJson());
        result.put("create_time", formatDateTime(draftRow.createTime()));
        result.put("update_time", formatDateTime(draftRow.updateTime()));
        return result;
    }

    private boolean anyTableHasContent(Map<String, Object> archiveSnapshot, List<String> tableNames) {
        for (String tableName : tableNames) {
            if (archiveValueHasMeaningfulContent(archiveSnapshot.get(tableName))) {
                return true;
            }
        }
        return false;
    }

    private boolean archiveValueHasMeaningfulContent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return StrUtil.isNotBlank(stringValue);
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (archiveValueHasMeaningfulContent(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || ARCHIVE_PROGRESS_IGNORED_FIELDS.contains(String.valueOf(entry.getKey()))) {
                    continue;
                }
                if (archiveValueHasMeaningfulContent(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean computeBuildReadyState(Map<String, Object> basicInfoProgress, List<String> missingDimensions) {
        for (String fieldName : List.of("current_grade", "target_country", "major_interest", "curriculum_system")) {
            Map<String, Object> entry = toMapOrEmpty(basicInfoProgress.get(fieldName));
            if (!Boolean.TRUE.equals(entry.get("collected"))) {
                return false;
            }
        }
        return !missingDimensions.contains("academic");
    }

    private Object firstCurriculumCode(List<Object> curriculumRows) {
        Object firstCode = null;
        for (Object item : curriculumRows) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object code = map.get("curriculum_system_code");
            if (StrUtil.isBlank(String.valueOf(code == null ? "" : code))) {
                continue;
            }
            if (firstCode == null) {
                firstCode = code;
            }
            if (Integer.valueOf(1).equals(toInt(map.get("is_primary")))) {
                return code;
            }
        }
        return firstCode;
    }

    private Object firstNonBlank(Object left, Object right) {
        return StrUtil.isNotBlank(String.valueOf(left == null ? "" : left)) ? left : right;
    }

    private void setProgressEntryIfNotBlank(Map<String, Object> progress, String fieldName, Object value) {
        if (StrUtil.isBlank(String.valueOf(value == null ? "" : value))) {
            return;
        }
        progress.put(fieldName, progressEntry(true, String.valueOf(value).trim()));
    }

    private Map<String, Object> progressEntry(boolean collected, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collected", collected);
        result.put("value", value);
        return result;
    }

    private Map<String, Object> dimensionEntry(String status, boolean hasContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("reason", hasContent ? "official profile contains this dimension" : "");
        return result;
    }

    private boolean isFalseLike(Object value) {
        String normalized = String.valueOf(value == null ? "" : value).trim();
        return "0".equals(normalized) || "false".equalsIgnoreCase(normalized);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> toMutableMap(Object value) {
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

    private static List<Object> toObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Object parsed = parseJsonValue(JSONUtil.toJsonStr(source == null ? Collections.emptyMap() : source));
        return toMutableMap(parsed);
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StrUtil.isBlank(text) ? null : text;
    }

    private static String trimRemark(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static List<Object> toListOrEmpty(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return Collections.emptyList();
    }

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
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> profileDraftMutationRow(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> draftJson,
        Map<String, Object> lastPatchJson,
        int sourceRound,
        int versionNo,
        LocalDateTime now,
        Long draftId
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("draftId", draftId);
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("bizDomain", bizDomain);
        row.put("draftJson", JSONUtil.toJsonStr(draftJson));
        row.put("lastPatchJson", JSONUtil.toJsonStr(lastPatchJson));
        row.put("sourceRound", sourceRound);
        row.put("versionNo", versionNo);
        row.put("now", Timestamp.valueOf(now));
        return row;
    }

    private Map<String, Object> profileResultMutationRow(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> profileJson,
        Map<String, Object> radarScoresJson,
        String summaryText,
        String resultStatus,
        Map<String, Object> dbPayloadJson,
        String saveErrorMessage,
        LocalDateTime now,
        Long profileResultId
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("profileResultId", profileResultId);
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("bizDomain", bizDomain);
        row.put("resultStatus", resultStatus);
        row.put("profileJson", JSONUtil.toJsonStr(profileJson));
        row.put("radarScoresJson", JSONUtil.toJsonStr(radarScoresJson == null ? Collections.emptyMap() : radarScoresJson));
        row.put("summaryText", summaryText);
        row.put("dbPayloadJson", dbPayloadJson == null ? null : JSONUtil.toJsonStr(dbPayloadJson));
        row.put("saveErrorMessage", saveErrorMessage);
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
            stringValue(column(row, "current_stage")),
            intValue(column(row, "current_round"))
        );
    }

    private static AiChatDraftRow toDraftRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatDraftRow(
            longValue(column(row, "draft_id")),
            stringValue(column(row, "session_id")),
            stringValue(column(row, "student_id")),
            stringValue(column(row, "biz_domain")),
            parseJsonValue(jsonText(column(row, "draft_json"))),
            parseJsonValue(jsonText(column(row, "last_patch_json"))),
            intValue(column(row, "source_round")),
            intValue(column(row, "version_no")),
            toLocalDateTime(column(row, "create_time")),
            toLocalDateTime(column(row, "update_time"))
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

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
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

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private record AiChatSessionRow(
        String sessionId,
        String studentId,
        String bizDomain,
        String currentStage,
        int currentRound
    ) {
    }

    private record AiChatDraftRow(
        long draftId,
        String sessionId,
        String studentId,
        String bizDomain,
        Object draftJson,
        Object lastPatchJson,
        int sourceRound,
        int versionNo,
        LocalDateTime createTime,
        LocalDateTime updateTime
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
