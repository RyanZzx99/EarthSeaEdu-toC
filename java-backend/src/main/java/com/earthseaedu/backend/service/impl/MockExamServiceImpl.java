package com.earthseaedu.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.dto.mockexam.MockExamRequests;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.MockExamMapper;
import com.earthseaedu.backend.service.MockExamService;
import com.earthseaedu.backend.support.AiHttpSupport;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * 模考业务服务实现。
 */
@Service
public class MockExamServiceImpl implements MockExamService {

    private static final String PAPER_SOURCE_KIND = "paper";
    private static final String PAPER_SET_SOURCE_KIND = "paper_set";
    private static final String PAPER_SET_CODE_PREFIX = "paper_set_";
    private static final List<String> IELTS_CONTENTS = List.of("Listening", "Reading");
    private static final Set<String> PAPER_SET_CONTENTS = Set.of("Listening", "Reading", "Mixed");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern BLANK_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+)\\s*\\}\\}|\\[\\[\\s*([^\\]]+)\\s*\\]\\]");
    private static final Pattern PAPER_SET_CODE_PATTERN = Pattern.compile("^" + PAPER_SET_CODE_PREFIX + "(\\d+)$");
    private static final Pattern TRANSLATION_FIELD_PATTERN = Pattern.compile("\"translation\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);

    private final MockExamMapper mockExamMapper;
    private final EarthSeaProperties properties;
    private final RestClient restClient;

    /**
     * 创建 MockExamServiceImpl 实例。
     */
    public MockExamServiceImpl(MockExamMapper mockExamMapper, EarthSeaProperties properties) {
        this.mockExamMapper = mockExamMapper;
        this.properties = properties;
        this.restClient = AiHttpSupport.createNonStreamRestClient(properties);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getOptions() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exam_category_options", List.of("IELTS"));
        response.put("content_options_map", Map.of("IELTS", IELTS_CONTENTS));
        response.put("supported_categories", List.of("IELTS"));
        return response;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listPapers(String examCategory, String examContent) {
        String normalizedCategory = normalizeExamCategory(examCategory, true);
        String normalizedContent = "";
        if (CharSequenceUtil.isNotBlank(normalizedCategory)) {
            normalizedContent = normalizeExamContent(examContent, true);
        } else if (CharSequenceUtil.isNotBlank(examContent)) {
            throw badRequest("exam_category is required when filtering exam_content");
        }

        String subjectType = CharSequenceUtil.isBlank(normalizedContent)
            ? null
            : subjectTypeFromExamContent(normalizedContent);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : mockExamMapper.listPapers(subjectType)) {
            items.add(serializePaperListItem(row));
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getPaper(long examPaperId) {
        PaperBundle bundle = loadPaperBundle(examPaperId);
        Map<String, Object> response = serializePaperListItem(bundle.paper());
        response.put("payload", bundle.payload());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listPaperSets(String examCategory, String examContent) {
        String normalizedCategory = normalizeExamCategory(examCategory, true);
        String normalizedContent = normalizePaperSetContent(examContent, true);
        List<Map<String, Object>> paperSets = mockExamMapper.listPaperSets(
            trimToNull(normalizedCategory),
            trimToNull(normalizedContent)
        );
        Map<Long, List<Map<String, Object>>> itemMap = loadPaperSetItems(paperSets);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> paperSet : paperSets) {
            items.add(serializePaperSetListItem(paperSet, itemMap.getOrDefault(longValue(paperSet.get("mockexam_paper_set_id")), List.of())));
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getPaperSet(long paperSetId) {
        PaperSetBundle bundle = loadPaperSetBundle(paperSetId, true);
        Map<String, Object> paperSet = bundle.paperSet();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mockexam_paper_set_id", longValue(paperSet.get("mockexam_paper_set_id")));
        response.put("set_name", stringValue(paperSet.get("set_name")));
        response.put("exam_category", blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"));
        response.put("exam_content", trimToNull(stringValue(paperSet.get("exam_content"))));
        response.put("paper_count", intValue(paperSet.get("paper_count")));
        response.put("payload", bundle.payload());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitPaper(String userId, long examPaperId, MockExamRequests.SubmitRequest request) {
        PaperBundle bundle = loadPaperBundle(examPaperId);
        Map<String, Object> progress = null;
        Map<String, Object> payload = bundle.payload();
        if (request.progressId() != null) {
            progress = requireActiveProgress(userId, request.progressId());
            if (longValue(progress.get("exam_paper_id")) != examPaperId) {
                throw badRequest("progress does not belong to current paper");
            }
            Object progressPayload = parseJson(progress.get("payload_json"));
            if (progressPayload instanceof Map<?, ?>) {
                payload = safeMap(progressPayload);
            }
        }

        Map<String, Object> answers = safeMap(request.answers());
        Map<String, Object> marked = safeMap(request.marked());
        Map<String, Object> result = evaluateQuizPayload(payload, answers);
        int elapsedSeconds = normalizeElapsedSeconds(request.elapsedSeconds());
        if (progress != null) {
            elapsedSeconds = Math.max(elapsedSeconds, intValue(progress.get("elapsed_seconds")));
        }

        long submissionId = insertSubmission(
            userId,
            longValue(bundle.paper().get("exam_paper_id")),
            trimToNull(stringValue(bundle.paper().get("paper_code"))),
            paperTitle(bundle.paper()),
            "IELTS",
            examContentFromSubjectType(stringValue(bundle.paper().get("subject_type"))),
            payload,
            result,
            elapsedSeconds
        );
        replaceSubmissionChildren(submissionId, payload, answers, marked);
        Map<String, Object> submission = requireSubmission(userId, submissionId);
        Map<String, Object> wrongbookReview = syncWrongQuestionStats(userId, submission, payload, answers, marked);
        if (progress != null) {
            mockExamMapper.markProgressSubmitted(progressSubmittedRow(request.progressId(), userId, submissionId, elapsedSeconds));
        }
        return submitResponse(result, submission, wrongbookReview);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitPaperSet(String userId, long paperSetId, MockExamRequests.SubmitRequest request) {
        PaperSetBundle bundle = loadPaperSetBundle(paperSetId, true);
        Map<String, Object> progress = null;
        Map<String, Object> payload = bundle.payload();
        String paperCode = buildPaperSetPaperCode(paperSetId);
        if (request.progressId() != null) {
            progress = requireActiveProgress(userId, request.progressId());
            SourceMeta sourceMeta = extractSourceMeta(stringValue(progress.get("paper_code")), parseJson(progress.get("payload_json")));
            if (!PAPER_SET_SOURCE_KIND.equals(sourceMeta.sourceKind()) || !Objects.equals(sourceMeta.paperSetId(), paperSetId)) {
                throw badRequest("progress does not belong to current paper set");
            }
            Object progressPayload = parseJson(progress.get("payload_json"));
            if (progressPayload instanceof Map<?, ?>) {
                payload = safeMap(progressPayload);
            }
        }

        Map<String, Object> answers = safeMap(request.answers());
        Map<String, Object> marked = safeMap(request.marked());
        Map<String, Object> result = evaluateQuizPayload(payload, answers);
        int elapsedSeconds = normalizeElapsedSeconds(request.elapsedSeconds());
        if (progress != null) {
            elapsedSeconds = Math.max(elapsedSeconds, intValue(progress.get("elapsed_seconds")));
        }

        Map<String, Object> paperSet = bundle.paperSet();
        long submissionId = insertSubmission(
            userId,
            0,
            paperCode,
            stringValue(paperSet.get("set_name")),
            blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"),
            trimToNull(stringValue(paperSet.get("exam_content"))),
            payload,
            result,
            elapsedSeconds
        );
        replaceSubmissionChildren(submissionId, payload, answers, marked);
        Map<String, Object> submission = requireSubmission(userId, submissionId);
        Map<String, Object> wrongbookReview = syncWrongQuestionStats(userId, submission, payload, answers, marked);
        if (progress != null) {
            mockExamMapper.markProgressSubmitted(progressSubmittedRow(request.progressId(), userId, submissionId, elapsedSeconds));
        }
        return submitResponse(result, submission, wrongbookReview);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listSubmissions(String userId, String examContent, Integer limit) {
        String normalizedContent = normalizeExamContent(examContent, true);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : mockExamMapper.listSubmissions(
            userId,
            trimToNull(normalizedContent),
            clampInt(limit, 20, 1, 100)
        )) {
            items.add(serializeSubmissionItem(row));
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getSubmission(String userId, long submissionId) {
        Map<String, Object> row = requireSubmission(userId, submissionId);
        Map<String, Object> payload = safeMap(parseJson(row.get("payload_json")));
        Map<String, Object> answers = buildAnswersMap(loadSubmissionAnswers(submissionId));
        Map<String, Object> response = serializeSubmissionItem(row);
        response.put("elapsed_seconds", intValue(row.get("elapsed_seconds")));
        response.put("create_time", row.get("create_time"));
        response.put("payload", repairPayload(payload));
        response.put("answers", answers);
        response.put("marked", buildMarkedMap(loadSubmissionStates(submissionId)));
        response.put("result", evaluateQuizPayload(payload, answers));
        return response;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listProgresses(String userId, Integer limit) {
        List<Map<String, Object>> rows = mockExamMapper.listProgresses(userId, clampInt(limit, 10, 1, 50));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(serializeProgressItem(row));
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getProgress(String userId, long progressId) {
        Map<String, Object> row = requireActiveProgress(userId, progressId);
        Map<String, Object> response = serializeProgressItem(row);
        response.put("payload", repairPayload(safeMap(parseJson(row.get("payload_json")))));
        response.put("answers", buildAnswersMap(loadProgressAnswers(progressId)));
        response.put("marked", buildMarkedMap(loadProgressStates(progressId)));
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> savePaperProgress(String userId, long examPaperId, MockExamRequests.ProgressSaveRequest request) {
        PaperBundle bundle = loadPaperBundle(examPaperId);
        Map<String, Object> payload = request.payload() instanceof Map<?, ?>
            ? repairPayload(safeMap(request.payload()))
            : bundle.payload();
        Map<String, Object> answers = safeMap(request.answers());
        Map<String, Object> marked = safeMap(request.marked());
        Map<String, Object> result = evaluateQuizPayload(payload, answers);
        Long progressId = resolvePaperProgressId(userId, request.progressId(), examPaperId, null);
        if (progressId == null) {
            progressId = insertProgress(
                userId,
                examPaperId,
                trimToNull(stringValue(bundle.paper().get("paper_code"))),
                paperTitle(bundle.paper()),
                "IELTS",
                examContentFromSubjectType(stringValue(bundle.paper().get("subject_type"))),
                payload,
                request,
                result
            );
        } else {
            updateProgress(
                progressId,
                userId,
                examPaperId,
                trimToNull(stringValue(bundle.paper().get("paper_code"))),
                paperTitle(bundle.paper()),
                "IELTS",
                examContentFromSubjectType(stringValue(bundle.paper().get("subject_type"))),
                payload,
                request,
                result
            );
        }
        replaceProgressChildren(progressId, payload, answers, marked);
        Map<String, Object> item = serializeProgressItem(requireActiveProgress(userId, progressId));
        return mutationResponse(item);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> savePaperSetProgress(String userId, long paperSetId, MockExamRequests.ProgressSaveRequest request) {
        PaperSetBundle bundle = loadPaperSetBundle(paperSetId, true);
        Map<String, Object> payload = request.payload() instanceof Map<?, ?>
            ? repairPayload(safeMap(request.payload()))
            : bundle.payload();
        Map<String, Object> answers = safeMap(request.answers());
        Map<String, Object> marked = safeMap(request.marked());
        Map<String, Object> result = evaluateQuizPayload(payload, answers);
        String paperCode = buildPaperSetPaperCode(paperSetId);
        Long progressId = resolvePaperProgressId(userId, request.progressId(), 0, paperCode);
        Map<String, Object> paperSet = bundle.paperSet();
        if (progressId == null) {
            progressId = insertProgress(
                userId,
                0,
                paperCode,
                stringValue(paperSet.get("set_name")),
                blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"),
                trimToNull(stringValue(paperSet.get("exam_content"))),
                payload,
                request,
                result
            );
        } else {
            updateProgress(
                progressId,
                userId,
                0,
                paperCode,
                stringValue(paperSet.get("set_name")),
                blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"),
                trimToNull(stringValue(paperSet.get("exam_content"))),
                payload,
                request,
                result
            );
        }
        replaceProgressChildren(progressId, payload, answers, marked);
        Map<String, Object> item = serializeProgressItem(requireActiveProgress(userId, progressId));
        return mutationResponse(item);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> discardProgress(String userId, long progressId) {
        Map<String, Object> row = requireActiveProgress(userId, progressId);
        Timestamp now = timestampNow();
        mockExamMapper.discardProgress(progressStatusRow(progressId, userId, now));
        row.put("status", 0);
        row.put("last_active_time", now);
        return mutationResponse(serializeProgressItem(row));
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listQuestionFavorites(String userId, Long examPaperId, Integer limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : mockExamMapper.listQuestionFavorites(
            userId,
            examPaperId,
            clampInt(limit, 50, 1, 200)
        )) {
            SourceMeta sourceMeta = extractSourceMeta(stringValue(row.get("paper_code")), null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("exam_question_id", longValue(row.get("exam_question_id")));
            item.put("exam_paper_id", longValue(row.get("exam_paper_id")));
            item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
            item.put("paper_title", stringValue(row.get("paper_title")));
            item.put("source_kind", sourceMeta.sourceKind());
            item.put("paper_set_id", sourceMeta.paperSetId());
            item.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
            item.put("exam_section_id", nullableLong(row.get("exam_section_id")));
            item.put("section_title", trimToNull(stringValue(row.get("section_title"))));
            item.put("exam_group_id", nullableLong(row.get("exam_group_id")));
            item.put("group_title", trimToNull(stringValue(row.get("group_title"))));
            item.put("question_id", stringValue(row.get("question_id")));
            item.put("question_no", trimToNull(stringValue(row.get("question_no"))));
            item.put("question_type", trimToNull(stringValue(row.get("question_type"))));
            item.put("stat_type", trimToNull(stringValue(row.get("stat_type"))));
            item.put("preview_text", previewText(stringValue(row.get("preview_source")), 240));
            item.put("create_time", row.get("create_time"));
            items.add(item);
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listEntityFavorites(String userId, Integer limit) {
        List<Map<String, Object>> rows = mockExamMapper.listEntityFavorites(userId, clampInt(limit, 200, 1, 200));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("target_type", stringValue(row.get("target_type")));
            item.put("target_id", longValue(row.get("target_id")));
            item.put("exam_paper_id", nullableLong(row.get("exam_paper_id")));
            item.put("paper_set_id", nullableLong(row.get("paper_set_id")));
            item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
            item.put("title", stringValue(row.get("title")));
            item.put("exam_category", blankToDefault(stringValue(row.get("exam_category")), "IELTS"));
            item.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
            item.put("create_time", row.get("create_time"));
            items.add(item);
        }
        return Map.of("items", items);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> translateSelection(String userId, MockExamRequests.SelectionTranslateRequest request) {
        String selectedText = trimToNull(request.selectedText());
        if (selectedText == null) {
            throw badRequest("selected_text is required");
        }
        String scopeType = CharSequenceUtil.trim(CharSequenceUtil.nullToDefault(request.scopeType(), "")).toLowerCase(Locale.ROOT);
        if (!Set.of("material", "question").contains(scopeType)) {
            throw badRequest("scope_type only supports material or question");
        }
        String targetLang = blankToDefault(request.targetLang(), "zh-CN");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("student_id", userId);
        context.put("selected_text", selectedText);
        context.put("scope_type", scopeType);
        context.put("module_name", blankToDefault(request.moduleName(), ""));
        context.put("passage_id", blankToDefault(request.passageId(), ""));
        context.put("question_id", blankToDefault(request.questionId(), ""));
        context.put("question_type", blankToDefault(request.questionType(), ""));
        context.put("surrounding_text_before", blankToDefault(request.surroundingTextBefore(), ""));
        context.put("surrounding_text_after", blankToDefault(request.surroundingTextAfter(), ""));
        context.put("target_lang", targetLang);

        Map<String, Object> parsed = parseTranslateContent(executePrompt("mockexam.selection_translate", context));
        String translation = trimToNull(stringValue(parsed.get("translation")));
        if (translation == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "translation result missing translation field");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("translation", translation);
        response.put("source_language", blankToDefault(stringValue(parsed.get("source_language")), "en"));
        response.put("target_language", blankToDefault(stringValue(parsed.get("target_language")), targetLang));
        response.put("confidence", trimToNull(stringValue(parsed.get("confidence"))));
        response.put("cached", boolValue(parsed.get("cached")));
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggleQuestionFavorite(
        String userId,
        long examQuestionId,
        MockExamRequests.FavoriteToggleRequest request
    ) {
        Map<String, Object> question = requireQuestionSnapshot(examQuestionId);
        if (!Boolean.TRUE.equals(request.isFavorite())) {
            mockExamMapper.deleteQuestionFavorite(userId, examQuestionId, timestampNow());
            return favoriteResponse(false, 1);
        }

        String paperCode = trimToNull(stringValue(question.get("paper_code")));
        String paperTitle = paperTitle(question);
        String examCategory = blankToDefault(stringValue(question.get("exam_category")), "IELTS");
        String examContent = examContentFromSubjectType(stringValue(question.get("subject_type")));
        String normalizedSourceKind = normalizeFavoriteSourceKind(request.sourceKind());
        if (PAPER_SET_SOURCE_KIND.equals(normalizedSourceKind) && request.paperSetId() != null) {
            Map<String, Object> paperSet = requirePaperSet(request.paperSetId(), false);
            paperCode = buildPaperSetPaperCode(request.paperSetId());
            paperTitle = stringValue(paperSet.get("set_name"));
            examCategory = blankToDefault(stringValue(paperSet.get("exam_category")), examCategory);
            examContent = blankToDefault(stringValue(paperSet.get("exam_content")), examContent);
        }

        Map<String, Object> favoriteRow = questionFavoriteRow(userId, examQuestionId, question, paperCode, paperTitle, examCategory, examContent);
        Map<String, Object> existing = mockExamMapper.findQuestionFavorite(userId, examQuestionId);
        if (existing == null) {
            mockExamMapper.insertQuestionFavorite(favoriteRow);
        } else {
            favoriteRow.put("favoriteId", existing.get("mockexam_question_favorite_id"));
            mockExamMapper.updateQuestionFavorite(favoriteRow);
        }
        return favoriteResponse(true, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> togglePaperFavorite(String userId, long examPaperId, MockExamRequests.EntityFavoriteToggleRequest request) {
        Map<String, Object> paper = requirePaperRow(examPaperId);
        boolean favorite = Boolean.TRUE.equals(request.isFavorite());
        upsertEntityFavorite(
            userId,
            PAPER_SOURCE_KIND,
            examPaperId,
            longValue(paper.get("exam_paper_id")),
            null,
            trimToNull(stringValue(paper.get("paper_code"))),
            paperTitle(paper),
            "IELTS",
            examContentFromSubjectType(stringValue(paper.get("subject_type"))),
            favorite
        );
        return favoriteResponse(favorite, 1);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> togglePaperSetFavorite(String userId, long paperSetId, MockExamRequests.EntityFavoriteToggleRequest request) {
        Map<String, Object> paperSet = requirePaperSet(paperSetId, false);
        boolean favorite = Boolean.TRUE.equals(request.isFavorite());
        upsertEntityFavorite(
            userId,
            PAPER_SET_SOURCE_KIND,
            paperSetId,
            null,
            paperSetId,
            buildPaperSetPaperCode(paperSetId),
            stringValue(paperSet.get("set_name")),
            blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"),
            trimToNull(stringValue(paperSet.get("exam_content"))),
            favorite
        );
        return favoriteResponse(favorite, 1);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> listWrongQuestions(String userId, Integer limit) {
        List<Map<String, Object>> rows = mockExamMapper.listWrongQuestions(userId);
        int totalQuestions = rows.size();
        int totalWrongCount = rows.stream().mapToInt(row -> intValue(row.get("wrong_count"))).sum();
        String mostCommonType = mostCommonType(rows);
        int safeLimit = clampInt(limit, 50, 1, 200);
        List<Map<String, Object>> limitedRows = rows.subList(0, Math.min(safeLimit, rows.size()));
        Map<String, Map<String, Object>> groupMap = new LinkedHashMap<>();
        for (Map<String, Object> row : limitedRows) {
            String groupKey = longValue(row.get("exam_paper_id")) + ":" + longValue(row.get("exam_group_id"));
            Map<String, Object> group = groupMap.computeIfAbsent(groupKey, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("exam_paper_id", longValue(row.get("exam_paper_id")));
                created.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
                created.put("paper_title", stringValue(row.get("paper_title")));
                created.put("exam_section_id", nullableLong(row.get("exam_section_id")));
                created.put("section_title", trimToNull(stringValue(row.get("section_title"))));
                created.put("exam_group_id", nullableLong(row.get("exam_group_id")));
                created.put("group_title", trimToNull(stringValue(row.get("group_title"))));
                created.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
                created.put("wrong_question_count", 0);
                created.put("total_wrong_count", 0);
                created.put("latest_wrong_time", row.get("latest_wrong_time"));
                created.put("questions", new ArrayList<Map<String, Object>>());
                return created;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) group.get("questions");
            questions.add(serializeWrongQuestionItem(row));
            group.put("wrong_question_count", intValue(group.get("wrong_question_count")) + 1);
            group.put("total_wrong_count", intValue(group.get("total_wrong_count")) + intValue(row.get("wrong_count")));
            if (compareDate(row.get("latest_wrong_time"), group.get("latest_wrong_time")) > 0) {
                group.put("latest_wrong_time", row.get("latest_wrong_time"));
            }
        }
        List<Map<String, Object>> groups = new ArrayList<>(groupMap.values());
        for (Map<String, Object> group : groups) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) group.get("questions");
            questions.sort(Comparator.comparingInt(item -> sortableQuestionNumber(item.get("question_no"))));
        }
        groups.sort((left, right) -> {
            int timeCompare = compareDate(right.get("latest_wrong_time"), left.get("latest_wrong_time"));
            if (timeCompare != 0) {
                return timeCompare;
            }
            return Integer.compare(intValue(right.get("total_wrong_count")), intValue(left.get("total_wrong_count")));
        });

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_questions", totalQuestions);
        summary.put("total_wrong_count", totalWrongCount);
        summary.put("average_wrong_count", groups.isEmpty() ? 0 : roundOne((double) totalWrongCount / groups.size()));
        summary.put("most_common_type", mostCommonType);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("groups", groups);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resolveWrongQuestions(String userId, MockExamRequests.WrongQuestionResolveRequest request) {
        List<Long> ids = dedupePositiveLongs(request.examQuestionIds());
        if (CollUtil.isEmpty(ids)) {
            return Map.of("status", "ok", "removed_count", 0);
        }
        int removed = mockExamMapper.resolveWrongQuestions(userId, ids, timestampNow());
        return Map.of("status", "ok", "removed_count", removed);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getQuestionDetail(String userId, long examQuestionId) {
        Map<String, Object> question = requireQuestionSnapshot(examQuestionId);
        Map<String, Object> section = serializeQuestionDetailSection(question);
        List<Map<String, Object>> options = loadOptionsForGroup(longValue(question.get("exam_group_id")));
        Map<String, Object> group = serializeQuestionDetailGroup(question, options);
        List<Map<String, Object>> answers = loadQuestionAnswers(examQuestionId);
        List<Map<String, Object>> blanks = loadQuestionBlanks(examQuestionId);
        List<Map<String, Object>> assets = loadQuestionAssets(question);
        Map<String, Object> questionPayload = serializeQuestionPayload(question, options, answers, blanks, assets);
        if (questionPayload == null) {
            throw notFound("question detail unavailable");
        }
        boolean isFavorite = mockExamMapper.findActiveQuestionFavorite(userId, examQuestionId) != null;
        Map<String, Object> wrongRow = mockExamMapper.findQuestionWrongCount(userId, examQuestionId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exam_question_id", examQuestionId);
        response.put("exam_paper_id", longValue(question.get("exam_paper_id")));
        response.put("paper_code", trimToNull(stringValue(question.get("paper_code"))));
        response.put("paper_name", paperTitle(question));
        response.put("exam_category", blankToDefault(stringValue(question.get("exam_category")), "IELTS"));
        response.put("exam_content", examContentFromSubjectType(stringValue(question.get("subject_type"))));
        response.put("is_favorite", isFavorite);
        response.put("wrong_count", wrongRow == null ? 0 : intValue(wrongRow.get("wrong_count")));
        response.put("material_html", buildMaterialHtml(section, group));
        response.put("section", section);
        response.put("group", group);
        response.put("question", questionPayload);
        return response;
    }

    private PaperBundle loadPaperBundle(long examPaperId) {
        Map<String, Object> paper = requirePaperRow(examPaperId);
        return new PaperBundle(paper, buildPaperPayload(paper));
    }

    private Map<String, Object> requirePaperRow(long examPaperId) {
        Map<String, Object> row = mockExamMapper.findPaperRow(examPaperId);
        if (row == null) {
            throw notFound("paper not found or disabled");
        }
        return row;
    }

    private Map<String, Object> buildPaperPayload(Map<String, Object> paper) {
        long examPaperId = longValue(paper.get("exam_paper_id"));
        List<Map<String, Object>> sections = mockExamMapper.listSectionsByPaper(examPaperId);
        List<Long> sectionIds = ids(sections, "exam_section_id");
        List<Map<String, Object>> groups = sectionIds.isEmpty()
            ? List.of()
            : mockExamMapper.listGroupsBySections(sectionIds);
        List<Long> groupIds = ids(groups, "exam_group_id");
        List<Map<String, Object>> questions = groupIds.isEmpty()
            ? List.of()
            : mockExamMapper.listQuestionsByGroups(groupIds);
        List<Long> questionIds = ids(questions, "exam_question_id");
        List<Map<String, Object>> options = groupIds.isEmpty()
            ? List.of()
            : mockExamMapper.listOptionsByGroups(groupIds);
        List<Map<String, Object>> answers = questionIds.isEmpty()
            ? List.of()
            : mockExamMapper.listAnswersByQuestions(questionIds);
        List<Map<String, Object>> blanks = questionIds.isEmpty()
            ? List.of()
            : mockExamMapper.listBlanksByQuestions(questionIds);
        List<Map<String, Object>> assets = loadAssets(sectionIds, groupIds, questionIds);

        Map<Long, List<Map<String, Object>>> groupsBySection = groupByLong(groups, "exam_section_id");
        Map<Long, List<Map<String, Object>>> questionsByGroup = groupByLong(questions, "exam_group_id");
        Map<Long, List<Map<String, Object>>> optionsByGroup = groupByLong(options, "exam_group_id");
        Map<Long, List<Map<String, Object>>> answersByQuestion = groupByLong(answers, "exam_question_id");
        Map<Long, List<Map<String, Object>>> blanksByQuestion = groupByLong(blanks, "exam_question_id");
        Map<Long, List<Map<String, Object>>> sectionAssets = groupByLong(assets, "exam_section_id");
        Map<Long, List<Map<String, Object>>> groupAssets = groupByLong(assets, "exam_group_id");
        Map<Long, List<Map<String, Object>>> questionAssets = groupByLong(assets, "exam_question_id");

        List<Map<String, Object>> passages = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            Map<String, Object> passage = serializePassage(
                section,
                groupsBySection.getOrDefault(longValue(section.get("exam_section_id")), List.of()),
                questionsByGroup,
                optionsByGroup,
                answersByQuestion,
                blanksByQuestion,
                sectionAssets,
                groupAssets,
                questionAssets
            );
            if (passage != null) {
                passages.add(passage);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", blankToDefault(stringValue(paper.get("module_name")), examContentFromSubjectType(stringValue(paper.get("subject_type")))));
        payload.put("passages", passages);
        return repairPayload(payload);
    }

    private Map<String, Object> serializePassage(
        Map<String, Object> section,
        List<Map<String, Object>> groups,
        Map<Long, List<Map<String, Object>>> questionsByGroup,
        Map<Long, List<Map<String, Object>>> optionsByGroup,
        Map<Long, List<Map<String, Object>>> answersByQuestion,
        Map<Long, List<Map<String, Object>>> blanksByQuestion,
        Map<Long, List<Map<String, Object>>> sectionAssets,
        Map<Long, List<Map<String, Object>>> groupAssets,
        Map<Long, List<Map<String, Object>>> questionAssets
    ) {
        long sectionId = longValue(section.get("exam_section_id"));
        List<Map<String, Object>> serializedGroups = new ArrayList<>();
        for (Map<String, Object> group : groups) {
            Map<String, Object> serialized = serializeGroup(
                section,
                group,
                questionsByGroup.getOrDefault(longValue(group.get("exam_group_id")), List.of()),
                optionsByGroup.getOrDefault(longValue(group.get("exam_group_id")), List.of()),
                answersByQuestion,
                blanksByQuestion,
                sectionAssets.getOrDefault(sectionId, List.of()),
                groupAssets.getOrDefault(longValue(group.get("exam_group_id")), List.of()),
                questionAssets
            );
            if (serialized != null) {
                serializedGroups.add(serialized);
            }
        }
        if (serializedGroups.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> assets = sectionAssets.getOrDefault(sectionId, List.of());
        Map<String, Object> passage = new LinkedHashMap<>();
        passage.put("id", blankToDefault(stringValue(section.get("section_id")), "S" + blankToDefault(stringValue(section.get("section_no")), stringValue(section.get("sort_order")))));
        passage.put("title", blankToDefault(stringValue(section.get("section_title")), "Section " + blankToDefault(stringValue(section.get("section_no")), stringValue(section.get("sort_order")))));
        passage.put("instructions", blankToDefault(stringValue(section.get("instructions_text")), ""));
        passage.put("content", rewriteHtmlAssetUrls(stringValue(section.get("content_html")), assets));
        passage.put("audio", resolvePrimaryAssetUrl(nullableLong(section.get("primary_audio_asset_id")), assets, "audio"));
        passage.put("groups", serializedGroups);
        return passage;
    }

    private Map<String, Object> serializeGroup(
        Map<String, Object> section,
        Map<String, Object> group,
        List<Map<String, Object>> questions,
        List<Map<String, Object>> optionRows,
        Map<Long, List<Map<String, Object>>> answersByQuestion,
        Map<Long, List<Map<String, Object>>> blanksByQuestion,
        List<Map<String, Object>> sectionAssets,
        List<Map<String, Object>> groupAssets,
        Map<Long, List<Map<String, Object>>> questionAssets
    ) {
        List<Map<String, Object>> options = serializeGroupOptions(optionRows);
        List<Map<String, Object>> serializedQuestions = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            List<Map<String, Object>> assets = new ArrayList<>();
            assets.addAll(sectionAssets);
            assets.addAll(groupAssets);
            assets.addAll(questionAssets.getOrDefault(longValue(question.get("exam_question_id")), List.of()));
            Map<String, Object> serialized = serializeQuestionPayload(
                mergeParentSnapshot(section, group, question),
                options,
                answersByQuestion.getOrDefault(longValue(question.get("exam_question_id")), List.of()),
                blanksByQuestion.getOrDefault(longValue(question.get("exam_question_id")), List.of()),
                assets
            );
            if (serialized != null) {
                serializedQuestions.add(serialized);
            }
        }
        if (serializedQuestions.isEmpty()) {
            return null;
        }
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("id", blankToDefault(stringValue(group.get("group_id")), passageGroupFallbackId(section, group)));
        serialized.put("title", blankToDefault(stringValue(group.get("group_title")), ""));
        serialized.put("instructions", rewriteHtmlAssetUrls(blankToDefault(stringValue(group.get("instructions_html")), stringValue(group.get("instructions_text"))), groupAssets));
        serialized.put("type", stringValue(serializedQuestions.get(0).get("type")));
        serialized.put("options", options.isEmpty() ? null : options);
        serialized.put("questions", serializedQuestions);
        return serialized;
    }

    private Map<String, Object> serializeQuestionPayload(
        Map<String, Object> question,
        List<Map<String, Object>> options,
        List<Map<String, Object>> answers,
        List<Map<String, Object>> blanks,
        List<Map<String, Object>> assets
    ) {
        Map<String, Object> answerRow = answers.isEmpty() ? null : answers.get(0);
        List<String> answerValues = extractAnswerValues(answerRow);
        String answerText = answerRow == null ? "" : trimToEmpty(stringValue(answerRow.get("answer_raw")));
        String questionType = inferQuestionType(question, options, answerValues, blanks);
        PromptHtml promptHtml = resolveQuestionPromptHtml(question, assets);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", questionBusinessId(question));
        payload.put("exam_question_id", nullableLong(question.get("exam_question_id")));
        payload.put("question_no", nullableQuestionNo(question.get("question_no")));
        payload.put("stat_type", blankToDefault(stringValue(question.get("stat_type")), stringValue(question.get("group_stat_type"))));
        payload.put("group_id", blankToDefault(stringValue(question.get("group_id")), passageGroupFallbackId(question, question)));
        payload.put("exam_group_id", nullableLong(question.get("exam_group_id")));
        payload.put("exam_section_id", nullableLong(question.get("exam_section_id")));
        payload.put("exam_paper_id", nullableLong(question.get("exam_paper_id")));
        payload.put("type", questionType);
        payload.put("stem", blankToDefault(promptHtml.promptWithBlankHtml(), blankToDefault(promptHtml.promptHtml(), buildQuestionFallbackHtml(question))));
        payload.put("content", "");

        if ("tfng".equals(questionType)) {
            payload.put("answer", normalizeTfngValue(answerValues.isEmpty() ? answerText : answerValues.get(0)));
            return payload;
        }
        if ("multiple".equals(questionType)) {
            payload.put("answer", answerValues);
            if (!options.isEmpty()) {
                payload.put("options", options);
            }
            return payload;
        }
        if ("single".equals(questionType)) {
            payload.put("answer", answerValues.isEmpty() ? answerText : answerValues.get(0));
            if (!options.isEmpty()) {
                payload.put("options", options);
            }
            return payload;
        }
        if ("cloze_inline".equals(questionType)) {
            List<Map<String, Object>> blankPayload = serializeBlanks(blanks, answerText, answerValues);
            if (blankPayload.isEmpty()) {
                return null;
            }
            payload.put("stem", "");
            payload.put("content", blankToDefault(promptHtml.promptHtml(), blankToDefault(promptHtml.promptWithBlankHtml(), buildBlankQuestionContent(question, blanks))));
            payload.put("blanks", blankPayload);
            return payload;
        }
        payload.put("answer", answerText);
        return payload;
    }

    private PaperSetBundle loadPaperSetBundle(long paperSetId, boolean requireEnabled) {
        Map<String, Object> paperSet = requirePaperSet(paperSetId, requireEnabled);
        List<Map<String, Object>> items = loadPaperSetItems(List.of(paperSet)).getOrDefault(paperSetId, List.of());
        if (items.isEmpty()) {
            throw badRequest("paper set has no active papers");
        }
        List<Map<String, Object>> passages = new ArrayList<>();
        int paperIndex = 1;
        for (Map<String, Object> item : items) {
            long examPaperId = longValue(item.get("exam_paper_id"));
            PaperBundle bundle = loadPaperBundle(examPaperId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paperPassages = (List<Map<String, Object>>) bundle.payload().getOrDefault("passages", List.of());
            int passageIndex = 1;
            for (Map<String, Object> rawPassage : paperPassages) {
                Map<String, Object> passage = deepCopyMap(rawPassage);
                String basePassageId = blankToDefault(stringValue(passage.get("id")), "P" + passageIndex);
                passage.put("id", "PS" + paperSetId + "-P" + paperIndex + "-" + basePassageId);
                String baseTitle = trimToEmpty(stringValue(passage.get("title")));
                String paperTitle = paperTitle(bundle.paper());
                passage.put("title", CharSequenceUtil.isBlank(baseTitle) ? paperTitle : paperTitle + " - " + baseTitle);
                passages.add(passage);
                passageIndex++;
            }
            paperIndex++;
        }
        if (passages.isEmpty()) {
            throw badRequest("paper set has no available questions");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", PAPER_SET_SOURCE_KIND);
        meta.put("paper_set_id", paperSetId);
        meta.put("paper_set_name", stringValue(paperSet.get("set_name")));
        meta.put("exam_category", blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"));
        meta.put("exam_content", trimToNull(stringValue(paperSet.get("exam_content"))));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("_meta", meta);
        payload.put("module", stringValue(paperSet.get("set_name")));
        payload.put("passages", passages);
        return new PaperSetBundle(paperSet, repairPayload(payload));
    }

    private Map<String, Object> requirePaperSet(long paperSetId, boolean requireEnabled) {
        Map<String, Object> row = mockExamMapper.findPaperSet(paperSetId, requireEnabled);
        if (row == null) {
            throw notFound("paper set not found or disabled");
        }
        return row;
    }

    private Map<Long, List<Map<String, Object>>> loadPaperSetItems(List<Map<String, Object>> paperSets) {
        List<Long> paperSetIds = ids(paperSets, "mockexam_paper_set_id");
        if (paperSetIds.isEmpty()) {
            return Map.of();
        }
        return groupByLong(mockExamMapper.listPaperSetItems(paperSetIds), "mockexam_paper_set_id");
    }

    private Map<String, Object> serializePaperSetListItem(Map<String, Object> paperSet, List<Map<String, Object>> items) {
        List<Long> paperIds = new ArrayList<>();
        List<String> paperNames = new ArrayList<>();
        for (Map<String, Object> item : items) {
            paperIds.add(longValue(item.get("exam_paper_id")));
            paperNames.add(paperTitle(item));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mockexam_paper_set_id", longValue(paperSet.get("mockexam_paper_set_id")));
        response.put("set_name", stringValue(paperSet.get("set_name")));
        response.put("exam_category", blankToDefault(stringValue(paperSet.get("exam_category")), "IELTS"));
        response.put("exam_content", trimToNull(stringValue(paperSet.get("exam_content"))));
        response.put("paper_count", intValue(paperSet.get("paper_count")));
        response.put("status", intValue(paperSet.get("status")));
        response.put("remark", trimToNull(stringValue(paperSet.get("remark"))));
        response.put("paper_ids", paperIds);
        response.put("paper_names", paperNames);
        response.put("create_time", paperSet.get("create_time"));
        response.put("update_time", paperSet.get("update_time"));
        return response;
    }

    private Map<String, Object> serializePaperListItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("exam_paper_id", longValue(row.get("exam_paper_id")));
        item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
        item.put("paper_name", paperTitle(row));
        item.put("bank_name", blankToDefault(stringValue(row.get("bank_name")), ""));
        item.put("exam_category", blankToDefault(stringValue(row.get("exam_category")), "IELTS"));
        item.put("exam_content", examContentFromSubjectType(stringValue(row.get("subject_type"))));
        item.put("module_name", blankToDefault(stringValue(row.get("module_name")), ""));
        item.put("book_code", trimToNull(stringValue(row.get("book_code"))));
        item.put("test_no", nullableInteger(row.get("test_no")));
        item.put("create_time", row.get("create_time"));
        return item;
    }

    private Map<String, Object> serializeSubmissionItem(Map<String, Object> row) {
        SourceMeta sourceMeta = extractSourceMeta(stringValue(row.get("paper_code")), parseJson(row.get("payload_json")));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("submission_id", longValue(row.get("mockexam_submission_id")));
        item.put("exam_paper_id", longValue(row.get("exam_paper_id")));
        item.put("source_kind", sourceMeta.sourceKind());
        item.put("paper_set_id", sourceMeta.paperSetId());
        item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
        item.put("title", stringValue(row.get("title")));
        item.put("exam_category", blankToDefault(stringValue(row.get("exam_category")), "IELTS"));
        item.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
        item.put("score_percent", row.get("score_percent") == null ? null : doubleValue(row.get("score_percent")));
        item.put("total_questions", intValue(row.get("total_questions")));
        item.put("correct_count", intValue(row.get("correct_count")));
        item.put("wrong_count", intValue(row.get("wrong_count")));
        item.put("gradable_questions", intValue(row.get("gradable_questions")));
        item.put("elapsed_seconds", intValue(row.get("elapsed_seconds")));
        item.put("create_time", row.get("create_time"));
        return item;
    }

    private Map<String, Object> serializeProgressItem(Map<String, Object> row) {
        SourceMeta sourceMeta = extractSourceMeta(stringValue(row.get("paper_code")), parseJson(row.get("payload_json")));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("progress_id", longValue(row.get("mockexam_progress_id")));
        item.put("exam_paper_id", longValue(row.get("exam_paper_id")));
        item.put("source_kind", sourceMeta.sourceKind());
        item.put("paper_set_id", sourceMeta.paperSetId());
        item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
        item.put("title", stringValue(row.get("title")));
        item.put("exam_category", blankToDefault(stringValue(row.get("exam_category")), "IELTS"));
        item.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
        item.put("answered_count", intValue(row.get("answered_count")));
        item.put("total_questions", intValue(row.get("total_questions")));
        item.put("elapsed_seconds", intValue(row.get("elapsed_seconds")));
        item.put("current_question_id", trimToNull(stringValue(row.get("current_question_id"))));
        item.put("current_question_index", nullableInteger(row.get("current_question_index")));
        item.put("current_question_no", trimToNull(stringValue(row.get("current_question_no"))));
        item.put("last_active_time", row.get("last_active_time"));
        item.put("status", intValue(row.get("status")));
        return item;
    }

    private Map<String, Object> requireSubmission(String userId, long submissionId) {
        Map<String, Object> row = mockExamMapper.findSubmission(userId, submissionId);
        if (row == null) {
            throw notFound("submission not found");
        }
        return row;
    }

    private Map<String, Object> requireActiveProgress(String userId, long progressId) {
        Map<String, Object> row = mockExamMapper.findActiveProgress(userId, progressId);
        if (row == null) {
            throw notFound("active progress not found");
        }
        return row;
    }

    private long insertSubmission(
        String userId,
        long examPaperId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        Map<String, Object> payload,
        Map<String, Object> result,
        int elapsedSeconds
    ) {
        Map<String, Object> row = submissionMutationRow(
            userId,
            examPaperId,
            paperCode,
            title,
            examCategory,
            examContent,
            payload,
            result,
            elapsedSeconds
        );
        mockExamMapper.insertSubmission(row);
        return longValue(row.get("id"));
    }

    private Long insertProgress(
        String userId,
        long examPaperId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        Map<String, Object> payload,
        MockExamRequests.ProgressSaveRequest request,
        Map<String, Object> result
    ) {
        Map<String, Object> row = progressMutationRow(
            null,
            userId,
            examPaperId,
            paperCode,
            title,
            examCategory,
            examContent,
            payload,
            request,
            result
        );
        mockExamMapper.insertProgress(row);
        return longValue(row.get("id"));
    }

    private void updateProgress(
        long progressId,
        String userId,
        long examPaperId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        Map<String, Object> payload,
        MockExamRequests.ProgressSaveRequest request,
        Map<String, Object> result
    ) {
        mockExamMapper.updateProgress(progressMutationRow(
            progressId,
            userId,
            examPaperId,
            paperCode,
            title,
            examCategory,
            examContent,
            payload,
            request,
            result
        ));
    }

    private Map<String, Object> progressSubmittedRow(Long progressId, String userId, long submissionId, int elapsedSeconds) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("progressId", progressId);
        row.put("userId", userId);
        row.put("submissionId", submissionId);
        row.put("elapsedSeconds", elapsedSeconds);
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> progressStatusRow(long progressId, String userId, Timestamp now) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("progressId", progressId);
        row.put("userId", userId);
        row.put("now", now);
        return row;
    }

    private Map<String, Object> questionFavoriteRow(
        String userId,
        long examQuestionId,
        Map<String, Object> question,
        String paperCode,
        String paperTitle,
        String examCategory,
        String examContent
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("examPaperId", longValue(question.get("exam_paper_id")));
        row.put("paperCode", paperCode);
        row.put("paperTitle", paperTitle);
        row.put("examSectionId", longValue(question.get("exam_section_id")));
        row.put("examGroupId", longValue(question.get("exam_group_id")));
        row.put("examQuestionId", examQuestionId);
        row.put("questionId", questionBusinessId(question));
        row.put("questionNo", trimToNull(stringValue(question.get("question_no"))));
        row.put("questionType", trimToNull(stringValue(question.get("raw_type"))));
        row.put("statType", trimToNull(blankToDefault(stringValue(question.get("stat_type")), stringValue(question.get("group_stat_type")))));
        row.put("examCategory", examCategory);
        row.put("examContent", examContent);
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> submissionMutationRow(
        String userId,
        long examPaperId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        Map<String, Object> payload,
        Map<String, Object> result,
        int elapsedSeconds
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("examPaperId", examPaperId);
        row.put("paperCode", paperCode);
        row.put("title", blankToDefault(title, "Mock Exam"));
        row.put("examCategory", blankToDefault(examCategory, "IELTS"));
        row.put("examContent", examContent);
        row.put("payloadJson", JSONUtil.toJsonStr(payload));
        row.put("answeredCount", intValue(result.get("answered_count")));
        row.put("totalQuestions", intValue(result.get("total_questions")));
        row.put("gradableQuestions", intValue(result.get("gradable_questions")));
        row.put("elapsedSeconds", elapsedSeconds);
        row.put("correctCount", intValue(result.get("correct_count")));
        row.put("wrongCount", intValue(result.get("wrong_count")));
        row.put("unansweredCount", intValue(result.get("unanswered_count")));
        row.put("scorePercent", result.get("score_percent") == null ? null : doubleValue(result.get("score_percent")));
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> progressMutationRow(
        Long progressId,
        String userId,
        long examPaperId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        Map<String, Object> payload,
        MockExamRequests.ProgressSaveRequest request,
        Map<String, Object> result
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("progressId", progressId);
        row.put("userId", userId);
        row.put("examPaperId", examPaperId);
        row.put("paperCode", paperCode);
        row.put("title", blankToDefault(title, "Mock Exam"));
        row.put("examCategory", blankToDefault(examCategory, "IELTS"));
        row.put("examContent", examContent);
        row.put("payloadJson", JSONUtil.toJsonStr(payload));
        row.put("currentQuestionId", trimToNull(request.currentQuestionId()));
        row.put("currentQuestionIndex", request.currentQuestionIndex());
        row.put("currentQuestionNo", trimToNull(request.currentQuestionNo()));
        row.put("answeredCount", intValue(result.get("answered_count")));
        row.put("totalQuestions", intValue(result.get("total_questions")));
        row.put("elapsedSeconds", normalizeElapsedSeconds(request.elapsedSeconds()));
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> answerRow(String ownerIdKey, long ownerId, AnswerEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(ownerIdKey, ownerId);
        row.put("examQuestionId", entry.examQuestionId());
        row.put("questionId", entry.questionId());
        row.put("questionNo", entry.questionNo());
        row.put("questionType", entry.questionType());
        row.put("statType", entry.statType());
        row.put("blankId", entry.blankId());
        row.put("itemId", entry.itemId());
        row.put("answerValue", entry.answerValue());
        row.put("sortOrder", entry.sortOrder());
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> questionStateRow(String ownerIdKey, long ownerId, QuestionStateEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(ownerIdKey, ownerId);
        row.put("examQuestionId", entry.examQuestionId());
        row.put("questionId", entry.questionId());
        row.put("questionNo", entry.questionNo());
        row.put("questionIndex", entry.questionIndex());
        row.put("questionType", entry.questionType());
        row.put("statType", entry.statType());
        row.put("marked", entry.marked());
        row.put("answered", entry.answered());
        row.put("correct", entry.correct());
        row.put("now", timestampNow());
        return row;
    }

    private Map<String, Object> wrongQuestionStatRow(
        String userId,
        Map<String, Object> submission,
        QuestionRecord record,
        Map<String, Object> snapshot,
        Object latestAnswer,
        boolean latestMarked,
        Long stateId,
        Timestamp now
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("examPaperId", longValue(snapshot.get("exam_paper_id")));
        row.put("paperCode", trimToNull(stringValue(snapshot.get("paper_code"))));
        row.put("paperTitle", stringValue(snapshot.get("paper_title")));
        row.put("examSectionId", longValue(snapshot.get("exam_section_id")));
        row.put("sectionTitle", trimToNull(stringValue(snapshot.get("section_title"))));
        row.put("examGroupId", longValue(snapshot.get("exam_group_id")));
        row.put("groupTitle", trimToNull(stringValue(snapshot.get("group_title"))));
        row.put("examQuestionId", record.examQuestionId());
        row.put("questionId", stringValue(snapshot.get("question_id")));
        row.put("questionNo", trimToNull(stringValue(snapshot.get("question_no"))));
        row.put("questionType", trimToNull(stringValue(snapshot.get("question_type"))));
        row.put("statType", trimToNull(stringValue(snapshot.get("stat_type"))));
        row.put("examCategory", blankToDefault(stringValue(snapshot.get("exam_category")), "IELTS"));
        row.put("examContent", trimToNull(stringValue(snapshot.get("exam_content"))));
        row.put("previewText", trimToEmpty(stringValue(snapshot.get("preview_text"))));
        row.put("latestWrongSubmissionId", longValue(submission.get("mockexam_submission_id")));
        row.put("latestWrongQuestionStateId", stateId);
        row.put("latestWrongTime", submission.get("create_time") == null ? now : submission.get("create_time"));
        row.put("latestUserAnswerJson", JSONUtil.toJsonStr(latestAnswer));
        row.put("latestMarked", latestMarked ? 1 : 0);
        row.put("now", now);
        return row;
    }

    private Map<String, Object> entityFavoriteRow(
        String userId,
        String targetType,
        long targetId,
        Long examPaperId,
        Long paperSetId,
        String paperCode,
        String title,
        String examCategory,
        String examContent
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("targetType", targetType);
        row.put("targetId", targetId);
        row.put("examPaperId", examPaperId);
        row.put("paperSetId", paperSetId);
        row.put("paperCode", paperCode);
        row.put("title", blankToDefault(title, "Mock Exam"));
        row.put("examCategory", blankToDefault(examCategory, "IELTS"));
        row.put("examContent", examContent);
        row.put("now", timestampNow());
        return row;
    }

    private Long resolvePaperProgressId(String userId, Long requestedProgressId, long examPaperId, String paperCode) {
        if (requestedProgressId != null) {
            Map<String, Object> progress = requireActiveProgress(userId, requestedProgressId);
            if (paperCode != null) {
                if (!paperCode.equals(stringValue(progress.get("paper_code")))) {
                    throw badRequest("progress does not belong to current target");
                }
            } else if (longValue(progress.get("exam_paper_id")) != examPaperId) {
                throw badRequest("progress does not belong to current target");
            }
            return requestedProgressId;
        }
        Map<String, Object> row = paperCode == null
            ? mockExamMapper.findActiveProgressIdByPaper(userId, examPaperId)
            : mockExamMapper.findActiveProgressIdByPaperCode(userId, paperCode);
        return row == null ? null : longValue(row.get("mockexam_progress_id"));
    }

    private void replaceSubmissionChildren(long submissionId, Map<String, Object> payload, Map<String, Object> answers, Map<String, Object> marked) {
        mockExamMapper.deleteSubmissionAnswers(submissionId);
        mockExamMapper.deleteSubmissionStates(submissionId);
        for (AnswerEntry entry : buildAnswerEntries(payload, answers)) {
            mockExamMapper.insertSubmissionAnswer(answerRow("submissionId", submissionId, entry));
        }
        for (QuestionStateEntry entry : buildQuestionStateEntries(payload, answers, marked, true)) {
            mockExamMapper.insertSubmissionState(questionStateRow("submissionId", submissionId, entry));
        }
    }

    private void replaceProgressChildren(long progressId, Map<String, Object> payload, Map<String, Object> answers, Map<String, Object> marked) {
        mockExamMapper.deleteProgressAnswers(progressId);
        mockExamMapper.deleteProgressStates(progressId);
        for (AnswerEntry entry : buildAnswerEntries(payload, answers)) {
            mockExamMapper.insertProgressAnswer(answerRow("progressId", progressId, entry));
        }
        for (QuestionStateEntry entry : buildQuestionStateEntries(payload, answers, marked, false)) {
            mockExamMapper.insertProgressState(questionStateRow("progressId", progressId, entry));
        }
    }

    private List<Map<String, Object>> loadSubmissionAnswers(long submissionId) {
        return mockExamMapper.listSubmissionAnswers(submissionId);
    }

    private List<Map<String, Object>> loadSubmissionStates(long submissionId) {
        return mockExamMapper.listSubmissionStates(submissionId);
    }

    private List<Map<String, Object>> loadProgressAnswers(long progressId) {
        return mockExamMapper.listProgressAnswers(progressId);
    }

    private List<Map<String, Object>> loadProgressStates(long progressId) {
        return mockExamMapper.listProgressStates(progressId);
    }

    private Map<String, Object> buildAnswersMap(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String questionId = trimToNull(stringValue(row.get("question_id")));
            if (questionId != null) {
                grouped.computeIfAbsent(questionId, key -> new ArrayList<>()).add(row);
            }
        }
        Map<String, Object> answers = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            List<Map<String, Object>> items = entry.getValue();
            String type = trimToEmpty(stringValue(items.get(0).get("question_type"))).toLowerCase(Locale.ROOT);
            if ("multiple".equals(type)) {
                List<String> values = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    String value = trimToNull(stringValue(item.get("answer_value")));
                    if (value != null) {
                        values.add(value);
                    }
                }
                answers.put(entry.getKey(), values);
            } else if ("cloze_inline".equals(type)) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map<String, Object> item : items) {
                    String blankId = trimToNull(stringValue(item.get("blank_id")));
                    if (blankId != null) {
                        values.put(blankId, trimToEmpty(stringValue(item.get("answer_value"))));
                    }
                }
                answers.put(entry.getKey(), values);
            } else if ("matching".equals(type)) {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map<String, Object> item : items) {
                    String itemId = trimToNull(stringValue(item.get("item_id")));
                    if (itemId != null) {
                        values.put(itemId, trimToEmpty(stringValue(item.get("answer_value"))));
                    }
                }
                answers.put(entry.getKey(), values);
            } else {
                answers.put(entry.getKey(), trimToEmpty(stringValue(items.get(0).get("answer_value"))));
            }
        }
        return answers;
    }

    private Map<String, Object> buildMarkedMap(List<Map<String, Object>> rows) {
        Map<String, Object> marked = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String questionId = trimToNull(stringValue(row.get("question_id")));
            if (questionId != null) {
                marked.put(questionId, intValue(row.get("is_marked")) == 1);
            }
        }
        return marked;
    }

    private List<AnswerEntry> buildAnswerEntries(Map<String, Object> payload, Map<String, Object> answers) {
        List<AnswerEntry> entries = new ArrayList<>();
        for (QuestionRecord record : flattenQuestions(payload)) {
            Object value = answers.get(record.id());
            if (!answerValuePresent(value)) {
                continue;
            }
            String questionType = record.questionType();
            if (value instanceof Map<?, ?> mapValue) {
                int sortOrder = 1;
                for (Map.Entry<?, ?> item : mapValue.entrySet()) {
                    Object itemValue = item.getValue();
                    if (!answerValuePresent(itemValue)) {
                        continue;
                    }
                    String key = stringValue(item.getKey());
                    entries.add(new AnswerEntry(
                        record.examQuestionId(),
                        record.id(),
                        record.questionNo(),
                        questionType,
                        record.statType(),
                        "cloze_inline".equals(questionType) ? key : null,
                        "cloze_inline".equals(questionType) ? null : key,
                        answerValueToString(itemValue),
                        sortOrder++
                    ));
                }
                continue;
            }
            if (value instanceof Collection<?> collection) {
                int sortOrder = 1;
                for (Object itemValue : collection) {
                    if (!answerValuePresent(itemValue)) {
                        continue;
                    }
                    entries.add(new AnswerEntry(
                        record.examQuestionId(),
                        record.id(),
                        record.questionNo(),
                        questionType,
                        record.statType(),
                        null,
                        null,
                        answerValueToString(itemValue),
                        sortOrder++
                    ));
                }
                continue;
            }
            entries.add(new AnswerEntry(
                record.examQuestionId(),
                record.id(),
                record.questionNo(),
                questionType,
                record.statType(),
                null,
                null,
                answerValueToString(value),
                1
            ));
        }
        return entries;
    }

    private List<QuestionStateEntry> buildQuestionStateEntries(
        Map<String, Object> payload,
        Map<String, Object> answers,
        Map<String, Object> marked,
        boolean includeCorrect
    ) {
        List<QuestionStateEntry> entries = new ArrayList<>();
        for (QuestionRecord record : flattenQuestions(payload)) {
            Evaluation evaluation = evaluateSingleQuestion(record.question(), answers.get(record.id()));
            Integer correct = includeCorrect && evaluation.gradable() ? (evaluation.correct() ? 1 : 0) : null;
            entries.add(new QuestionStateEntry(
                record.examQuestionId(),
                record.id(),
                record.questionNo(),
                record.questionIndex(),
                record.questionType(),
                record.statType(),
                boolValue(marked.get(record.id())) ? 1 : 0,
                evaluation.answered() ? 1 : 0,
                correct
            ));
        }
        return entries;
    }

    private Map<String, Object> evaluateQuizPayload(Map<String, Object> payload, Map<String, Object> answers) {
        List<QuestionRecord> questionRecords = flattenQuestions(payload);
        int totalQuestions = questionRecords.size();
        int answeredCount = 0;
        int gradableQuestions = 0;
        int correctCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, TypeCounter> counters = new LinkedHashMap<>();
        for (QuestionRecord record : questionRecords) {
            Evaluation evaluation = evaluateSingleQuestion(record.question(), answers.get(record.id()));
            if (evaluation.answered()) {
                answeredCount++;
            }
            if (evaluation.gradable()) {
                gradableQuestions++;
                if (evaluation.correct()) {
                    correctCount++;
                }
            }
            String typeKey = blankToDefault(record.questionType(), "unknown");
            counters.computeIfAbsent(typeKey, key -> new TypeCounter(typeKey)).accept(evaluation);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question_id", record.id());
            detail.put("exam_question_id", record.examQuestionId());
            detail.put("question_no", record.questionNo());
            detail.put("type", record.questionType());
            detail.put("stat_type", record.statType());
            detail.put("answered", evaluation.answered());
            detail.put("correct", evaluation.correct());
            detail.put("gradable", evaluation.gradable());
            detail.put("stem", previewText(blankToDefault(stringValue(record.question().get("stem")), stringValue(record.question().get("content"))), 160));
            details.add(detail);
        }
        int wrongCount = Math.max(0, gradableQuestions - correctCount);
        int unansweredCount = Math.max(0, totalQuestions - answeredCount);
        List<Map<String, Object>> typeBreakdown = new ArrayList<>();
        for (TypeCounter counter : counters.values()) {
            typeBreakdown.add(counter.toMap());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answered_count", answeredCount);
        result.put("total_questions", totalQuestions);
        result.put("gradable_questions", gradableQuestions);
        result.put("correct_count", correctCount);
        result.put("wrong_count", wrongCount);
        result.put("unanswered_count", unansweredCount);
        result.put("score_percent", gradableQuestions == 0 ? null : roundTwo(correctCount * 100.0 / gradableQuestions));
        result.put("type_breakdown", typeBreakdown);
        result.put("details", details);
        return result;
    }

    private Evaluation evaluateSingleQuestion(Map<String, Object> question, Object answerValue) {
        String questionType = inferPayloadQuestionType(question);
        boolean answered = answerValuePresent(answerValue);
        if ("cloze_inline".equals(questionType)) {
            List<Map<String, Object>> blanks = listOfMaps(question.get("blanks"));
            boolean gradable = !blanks.isEmpty() && blanks.stream().anyMatch(blank -> CharSequenceUtil.isNotBlank(stringValue(blank.get("answer"))));
            if (!answered || !gradable || !(answerValue instanceof Map<?, ?> mapAnswer)) {
                return new Evaluation(answered, false, gradable);
            }
            boolean correct = true;
            for (Map<String, Object> blank : blanks) {
                String blankId = stringValue(blank.get("id"));
                Object expected = blank.get("answer");
                Object actual = mapAnswer.get(blankId);
                if (!compareAnswer(expected, actual, null)) {
                    correct = false;
                    break;
                }
            }
            return new Evaluation(true, correct, true);
        }
        Object expected = question.get("answer");
        boolean gradable = answerValuePresent(expected);
        if (!answered || !gradable) {
            return new Evaluation(answered, false, gradable);
        }
        if ("multiple".equals(questionType)) {
            return new Evaluation(true, compareMultipleAnswer(expected, answerValue), true);
        }
        if ("tfng".equals(questionType)) {
            return new Evaluation(true, normalizeTfngValue(stringValue(expected)).equals(normalizeTfngValue(stringValue(answerValue))), true);
        }
        return new Evaluation(true, compareAnswer(expected, answerValue, listOfMaps(question.get("options"))), true);
    }

    private boolean compareMultipleAnswer(Object expected, Object actual) {
        Set<String> expectedSet = new LinkedHashSet<>();
        for (Object value : toObjectList(expected)) {
            String normalized = normalizeAnswerToken(value, null);
            if (CharSequenceUtil.isNotBlank(normalized)) {
                expectedSet.add(normalized);
            }
        }
        Set<String> actualSet = new LinkedHashSet<>();
        for (Object value : toObjectList(actual)) {
            String normalized = normalizeAnswerToken(value, null);
            if (CharSequenceUtil.isNotBlank(normalized)) {
                actualSet.add(normalized);
            }
        }
        return !expectedSet.isEmpty() && expectedSet.equals(actualSet);
    }

    private boolean compareAnswer(Object expected, Object actual, List<Map<String, Object>> options) {
        if (!answerValuePresent(expected) || !answerValuePresent(actual)) {
            return false;
        }
        Set<String> expectedVariants = answerVariants(expected);
        Set<String> actualVariants = answerVariants(actual);
        for (String expectedValue : expectedVariants) {
            for (String actualValue : actualVariants) {
                if (normalizeAnswerToken(expectedValue, options).equals(normalizeAnswerToken(actualValue, options))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> answerVariants(Object value) {
        Set<String> result = new LinkedHashSet<>();
        for (Object item : toObjectList(value)) {
            String text = trimToEmpty(stringValue(item));
            if (CharSequenceUtil.isBlank(text)) {
                continue;
            }
            for (String part : text.split("\\s*(?:\\||/|;|,|\\bor\\b)\\s*")) {
                String normalized = trimToNull(part);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private String normalizeAnswerToken(Object value, List<Map<String, Object>> options) {
        String text = previewText(stringValue(value), 500).toLowerCase(Locale.ROOT);
        text = text.replaceAll("[\\u2018\\u2019]", "'");
        text = text.replaceAll("[\\u201c\\u201d]", "\"");
        text = text.replaceAll("[\\p{Punct}\\s]+", " ").trim();
        if (options != null) {
            for (Map<String, Object> option : options) {
                String label = previewText(stringValue(option.get("label")), 100).toLowerCase(Locale.ROOT);
                String content = previewText(stringValue(option.get("content")), 500)
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[\\p{Punct}\\s]+", " ")
                    .trim();
                if (text.equals(label) || text.equals(content)) {
                    return label;
                }
            }
        }
        return text;
    }

    private String normalizeTfngValue(String value) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        if (Set.of("true", "t", "yes", "y").contains(normalized)) {
            return "true";
        }
        if (Set.of("false", "f", "no", "n").contains(normalized)) {
            return "false";
        }
        if (normalized.equals("not given") || normalized.equals("ng")) {
            return "not given";
        }
        return normalized;
    }

    private List<QuestionRecord> flattenQuestions(Map<String, Object> payload) {
        List<QuestionRecord> records = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> passage : listOfMaps(payload.get("passages"))) {
            appendQuestionRecords(records, listOfMaps(passage.get("questions")), index);
            index = records.size();
            for (Map<String, Object> group : listOfMaps(passage.get("groups"))) {
                appendQuestionRecords(records, listOfMaps(group.get("questions")), index);
                index = records.size();
            }
        }
        return records;
    }

    private void appendQuestionRecords(List<QuestionRecord> records, List<Map<String, Object>> questions, int startIndex) {
        int index = startIndex;
        for (Map<String, Object> question : questions) {
            String questionId = trimToNull(stringValue(question.get("id")));
            if (questionId == null) {
                continue;
            }
            records.add(new QuestionRecord(
                question,
                questionId,
                nullableLong(question.get("exam_question_id")),
                trimToNull(stringValue(question.get("question_no"))),
                index,
                inferPayloadQuestionType(question),
                trimToNull(stringValue(question.get("stat_type")))
            ));
            index++;
        }
    }

    private String inferPayloadQuestionType(Map<String, Object> question) {
        String type = trimToEmpty(stringValue(question.get("type"))).toLowerCase(Locale.ROOT);
        if (CharSequenceUtil.isNotBlank(type)) {
            return type;
        }
        if (!listOfMaps(question.get("blanks")).isEmpty()) {
            return "cloze_inline";
        }
        if (question.get("answer") instanceof Collection<?> collection && collection.size() > 1) {
            return "multiple";
        }
        if (!listOfMaps(question.get("options")).isEmpty()) {
            return "single";
        }
        return "blank";
    }

    private String inferQuestionType(
        Map<String, Object> question,
        List<Map<String, Object>> options,
        List<String> answerValues,
        List<Map<String, Object>> blanks
    ) {
        String rawType = trimToEmpty(blankToDefault(stringValue(question.get("raw_type")), stringValue(question.get("group_raw_type")))).toLowerCase(Locale.ROOT);
        String statType = trimToEmpty(blankToDefault(stringValue(question.get("stat_type")), stringValue(question.get("group_stat_type")))).toLowerCase(Locale.ROOT);
        Set<String> optionLabels = new LinkedHashSet<>();
        for (Map<String, Object> option : options) {
            String label = trimToNull(stringValue(option.get("label")));
            if (label != null) {
                optionLabels.add(label.toUpperCase(Locale.ROOT));
            }
        }
        boolean answersAreOptionLabels = !answerValues.isEmpty()
            && answerValues.stream().allMatch(value -> optionLabels.contains(value.toUpperCase(Locale.ROOT)));
        if ("tfng".equals(rawType) || "true_false_not_given".equals(statType)) {
            return "tfng";
        }
        if (!options.isEmpty() && answersAreOptionLabels) {
            return answerValues.size() > 1 ? "multiple" : "single";
        }
        if (!blanks.isEmpty()) {
            return "cloze_inline";
        }
        if (!options.isEmpty()) {
            return answerValues.size() > 1 ? "multiple" : "single";
        }
        return "blank";
    }

    private Map<String, Object> syncWrongQuestionStats(
        String userId,
        Map<String, Object> submission,
        Map<String, Object> payload,
        Map<String, Object> answers,
        Map<String, Object> marked
    ) {
        List<QuestionRecord> records = flattenQuestions(payload);
        List<Long> sessionQuestionIds = records.stream()
            .map(QuestionRecord::examQuestionId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        if (records.isEmpty() || sessionQuestionIds.isEmpty()) {
            return wrongbookReview(true, 0, 0, List.of());
        }

        int wrongCount = 0;
        for (QuestionRecord record : records) {
            if (record.examQuestionId() == null) {
                continue;
            }
            Evaluation evaluation = evaluateSingleQuestion(record.question(), answers.get(record.id()));
            if (!evaluation.gradable() || !evaluation.answered() || evaluation.correct()) {
                continue;
            }
            wrongCount++;
            Map<String, Object> snapshot = loadWrongQuestionSnapshot(record, submission);
            Long stateId = findSubmissionQuestionStateId(longValue(submission.get("mockexam_submission_id")), record);
            upsertWrongQuestionStat(userId, submission, record, snapshot, answers.get(record.id()), boolValue(marked.get(record.id())), stateId);
        }
        int activeCount = countActiveWrongQuestions(userId, sessionQuestionIds);
        return wrongbookReview(wrongCount == 0, wrongCount, activeCount, sessionQuestionIds);
    }

    private void upsertWrongQuestionStat(
        String userId,
        Map<String, Object> submission,
        QuestionRecord record,
        Map<String, Object> snapshot,
        Object latestAnswer,
        boolean latestMarked,
        Long stateId
    ) {
        Timestamp now = timestampNow();
        mockExamMapper.upsertWrongQuestionStat(wrongQuestionStatRow(
            userId,
            submission,
            record,
            snapshot,
            latestAnswer,
            latestMarked,
            stateId,
            now
        ));
    }

    private Map<String, Object> loadWrongQuestionSnapshot(QuestionRecord record, Map<String, Object> submission) {
        Map<String, Object> question = mockExamMapper.findWrongQuestionSnapshot(record.examQuestionId());
        Map<String, Object> source = question == null ? Map.of() : question;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("exam_paper_id", valueOrDefault(source.get("exam_paper_id"), submission.get("exam_paper_id")));
        snapshot.put("paper_code", blankToDefault(stringValue(source.get("paper_code")), stringValue(submission.get("paper_code"))));
        snapshot.put("paper_title", blankToDefault(paperTitle(source), stringValue(submission.get("title"))));
        snapshot.put("exam_section_id", valueOrDefault(source.get("exam_section_id"), 0));
        snapshot.put("section_title", trimToNull(stringValue(source.get("section_title"))));
        snapshot.put("exam_group_id", valueOrDefault(source.get("exam_group_id"), 0));
        snapshot.put("group_title", trimToNull(stringValue(source.get("group_title"))));
        snapshot.put("question_id", blankToDefault(questionBusinessId(source), record.id()));
        snapshot.put("question_no", blankToDefault(stringValue(source.get("question_no")), record.questionNo()));
        snapshot.put("question_type", blankToDefault(stringValue(source.get("raw_type")), record.questionType()));
        snapshot.put("stat_type", blankToDefault(stringValue(source.get("stat_type")), record.statType()));
        snapshot.put("exam_category", blankToDefault(stringValue(source.get("exam_category")), stringValue(submission.get("exam_category"))));
        snapshot.put("exam_content", CharSequenceUtil.isNotBlank(stringValue(source.get("subject_type")))
            ? examContentFromSubjectType(stringValue(source.get("subject_type")))
            : trimToNull(stringValue(submission.get("exam_content"))));
        snapshot.put("preview_text", previewText(blankToDefault(stringValue(source.get("stem_text")), blankToDefault(stringValue(source.get("content_text")), blankToDefault(stringValue(source.get("stem_html")), stringValue(source.get("content_html"))))), 240));
        if (CharSequenceUtil.isBlank(stringValue(snapshot.get("preview_text")))) {
            snapshot.put("preview_text", previewText(blankToDefault(stringValue(record.question().get("stem")), stringValue(record.question().get("content"))), 240));
        }
        return snapshot;
    }

    private Long findSubmissionQuestionStateId(long submissionId, QuestionRecord record) {
        Map<String, Object> row = mockExamMapper.findSubmissionQuestionStateId(submissionId, record.examQuestionId(), record.id());
        return row == null ? null : longValue(row.get("mockexam_submission_question_state_id"));
    }

    private int countActiveWrongQuestions(String userId, List<Long> examQuestionIds) {
        if (examQuestionIds.isEmpty()) {
            return 0;
        }
        Map<String, Object> row = mockExamMapper.countActiveWrongQuestions(userId, examQuestionIds);
        return row == null ? 0 : intValue(row.get("total"));
    }

    private Map<String, Object> wrongbookReview(boolean allCorrect, int wrongCount, int activeCount, List<Long> examQuestionIds) {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("all_correct", allCorrect);
        review.put("wrong_count", wrongCount);
        review.put("active_wrong_question_count", activeCount);
        review.put("exam_question_ids", examQuestionIds);
        return review;
    }

    private void upsertEntityFavorite(
        String userId,
        String targetType,
        long targetId,
        Long examPaperId,
        Long paperSetId,
        String paperCode,
        String title,
        String examCategory,
        String examContent,
        boolean favorite
    ) {
        Map<String, Object> row = entityFavoriteRow(
            userId,
            targetType,
            targetId,
            examPaperId,
            paperSetId,
            paperCode,
            title,
            examCategory,
            examContent
        );
        if (!favorite) {
            mockExamMapper.deleteEntityFavorite(row);
            return;
        }
        mockExamMapper.upsertEntityFavorite(row);
    }

    private Map<String, Object> requireQuestionSnapshot(long examQuestionId) {
        Map<String, Object> row = mockExamMapper.findQuestionSnapshot(examQuestionId);
        if (row == null) {
            throw notFound("question not found");
        }
        return row;
    }

    private Map<String, Object> serializeQuestionDetailSection(Map<String, Object> question) {
        List<Map<String, Object>> assets = loadSectionAssets(longValue(question.get("exam_section_id")));
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", blankToDefault(stringValue(question.get("section_id")), "S" + blankToDefault(stringValue(question.get("section_no")), "1")));
        section.put("title", blankToDefault(stringValue(question.get("section_title")), "Section"));
        section.put("instructions", blankToDefault(stringValue(question.get("section_instructions_text")), ""));
        section.put("content", rewriteHtmlAssetUrls(stringValue(question.get("section_content_html")), assets));
        section.put("audio", resolvePrimaryAssetUrl(nullableLong(question.get("primary_audio_asset_id")), assets, "audio"));
        return section;
    }

    private Map<String, Object> serializeQuestionDetailGroup(Map<String, Object> question, List<Map<String, Object>> options) {
        List<Map<String, Object>> assets = loadGroupAssets(longValue(question.get("exam_group_id")));
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", blankToDefault(stringValue(question.get("group_id")), "G" + longValue(question.get("exam_group_id"))));
        group.put("title", blankToDefault(stringValue(question.get("group_title")), ""));
        group.put("instructions", rewriteHtmlAssetUrls(blankToDefault(stringValue(question.get("group_instructions_html")), stringValue(question.get("group_instructions_text"))), assets));
        group.put("type", inferQuestionType(question, options, extractAnswerValues(loadQuestionAnswers(longValue(question.get("exam_question_id"))).stream().findFirst().orElse(null)), loadQuestionBlanks(longValue(question.get("exam_question_id")))));
        group.put("options", options.isEmpty() ? null : options);
        return group;
    }

    private List<Map<String, Object>> loadOptionsForGroup(long examGroupId) {
        return serializeGroupOptions(mockExamMapper.listOptionsForGroup(examGroupId));
    }

    private List<Map<String, Object>> loadQuestionAnswers(long examQuestionId) {
        return mockExamMapper.listQuestionAnswers(examQuestionId);
    }

    private List<Map<String, Object>> loadQuestionBlanks(long examQuestionId) {
        return mockExamMapper.listQuestionBlanks(examQuestionId);
    }

    private List<Map<String, Object>> loadQuestionAssets(Map<String, Object> question) {
        List<Map<String, Object>> assets = new ArrayList<>();
        assets.addAll(loadSectionAssets(longValue(question.get("exam_section_id"))));
        assets.addAll(loadGroupAssets(longValue(question.get("exam_group_id"))));
        assets.addAll(mockExamMapper.listQuestionAssets(longValue(question.get("exam_question_id"))));
        return assets;
    }

    private List<Map<String, Object>> loadSectionAssets(long sectionId) {
        return mockExamMapper.listSectionAssets(sectionId);
    }

    private List<Map<String, Object>> loadGroupAssets(long groupId) {
        return mockExamMapper.listGroupAssets(groupId);
    }

    private String buildMaterialHtml(Map<String, Object> section, Map<String, Object> group) {
        List<String> parts = new ArrayList<>();
        addIfNotBlank(parts, stringValue(section.get("instructions")));
        addIfNotBlank(parts, stringValue(section.get("content")));
        addIfNotBlank(parts, stringValue(group.get("instructions")));
        return String.join("\n", parts);
    }

    private String executePrompt(String promptKey, Map<String, Object> context) {
        Map<String, Object> prompt = mockExamMapper.findPromptConfig(promptKey);
        if (prompt == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI prompt config not found: " + promptKey);
        }
        String baseUrl = trimToNull(effectiveRuntimeValue("AI_MODEL_BASE_URL", properties.getAiRuntime().getModelBaseUrl()));
        String apiKey = trimToNull(effectiveRuntimeValue("AI_MODEL_API_KEY", properties.getAiRuntime().getModelApiKey()));
        String model = blankToDefault(stringValue(prompt.get("model_name")), effectiveRuntimeValue("AI_MODEL_DEFAULT_NAME", properties.getAiRuntime().getModelDefaultName()));
        if (baseUrl == null || apiKey == null || CharSequenceUtil.isBlank(model)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI runtime config is incomplete");
        }
        String promptContent = renderPrompt(stringValue(prompt.get("prompt_content")), context);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", prompt.get("temperature") == null ? properties.getAiRuntime().getModelDefaultTemperature() : doubleValue(prompt.get("temperature")));
        if (prompt.get("top_p") != null) {
            requestBody.put("top_p", doubleValue(prompt.get("top_p")));
        }
        if (prompt.get("max_tokens") != null) {
            requestBody.put("max_tokens", intValue(prompt.get("max_tokens")));
        }
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", "You are a precise educational translation assistant. Return JSON only."),
            Map.of("role", "user", "content", promptContent + "\n\nContext JSON:\n" + JSONUtil.toJsonStr(context))
        ));
        String rawResponse = AiHttpSupport.postJsonWithRetry(
            restClient,
            properties,
            resolveChatCompletionsUrl(baseUrl),
            apiKey,
            requestBody
        );
        Object parsed = parseJson(rawResponse);
        if (parsed instanceof Map<?, ?> responseMap) {
            List<Object> choices = toObjectList(responseMap.get("choices"));
            if (!choices.isEmpty() && choices.get(0) instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> messageMap) {
                    return stringValue(messageMap.get("content"));
                }
                return stringValue(choice.get("text"));
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "AI runtime returned an empty response");
    }

    private Map<String, Object> parseTranslateContent(String rawContent) {
        String cleaned = trimToEmpty(rawContent);
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json|JSON)?", "").replaceFirst("```$", "").trim();
        }
        try {
            Object parsed = parseJson(cleaned);
            if (parsed instanceof Map<?, ?> map) {
                return safeMap(map);
            }
        } catch (Exception ignored) {
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                Object parsed = parseJson(cleaned.substring(firstBrace, lastBrace + 1));
                if (parsed instanceof Map<?, ?> map) {
                    return safeMap(map);
                }
            }
            Matcher matcher = TRANSLATION_FIELD_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("translation", matcher.group(1).replace("\\\"", "\"").replace("\\n", "\n").trim());
                fallback.put("source_language", "en");
                fallback.put("target_language", "zh-CN");
                fallback.put("confidence", "low");
                return fallback;
            }
        }
        if (CharSequenceUtil.isNotBlank(cleaned) && !cleaned.startsWith("{")) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("translation", cleaned);
            fallback.put("source_language", "en");
            fallback.put("target_language", "zh-CN");
            fallback.put("confidence", "medium");
            return fallback;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "AI translation result parse failed");
    }

    private List<Map<String, Object>> loadAssets(List<Long> sectionIds, List<Long> groupIds, List<Long> questionIds) {
        if (sectionIds.isEmpty() && groupIds.isEmpty() && questionIds.isEmpty()) {
            return List.of();
        }
        return mockExamMapper.listAssets(sectionIds, groupIds, questionIds);
    }

    private List<Map<String, Object>> serializeGroupOptions(List<Map<String, Object>> rows) {
        List<Map<String, Object>> options = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> row : rows) {
            String content = trimToNull(blankToDefault(stringValue(row.get("option_text")), stringValue(row.get("option_html"))));
            if (content == null) {
                continue;
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("label", blankToDefault(stringValue(row.get("option_key")), String.valueOf((char) ('A' + index))));
            option.put("content", content);
            options.add(option);
            index++;
        }
        return options;
    }

    private PromptHtml resolveQuestionPromptHtml(Map<String, Object> question, List<Map<String, Object>> assets) {
        String sourceBlankId = trimToNull(stringValue(question.get("source_blank_id")));
        if (sourceBlankId != null) {
            String fullHtml = blankToDefault(stringValue(question.get("group_content_html")), blankToDefault(stringValue(question.get("content_html")), stringValue(question.get("stem_html"))));
            String fragmentHtml = extractPreciseBlankFragmentHtml(fullHtml, sourceBlankId);
            if (CharSequenceUtil.isBlank(fragmentHtml)) {
                fragmentHtml = blankToDefault(stringValue(question.get("stem_html")), stringValue(question.get("content_html")));
            }
            if (CharSequenceUtil.isNotBlank(fragmentHtml)) {
                fragmentHtml = normalizeBlankQuestionFragmentHtml(rewriteHtmlAssetUrls(fragmentHtml, assets));
                return new PromptHtml(
                    normalizeFragmentBlankPlaceholders(fragmentHtml, sourceBlankId, true),
                    normalizeFragmentBlankPlaceholders(fragmentHtml, sourceBlankId, false)
                );
            }
        }
        String promptHtml = normalizeBlankQuestionFragmentHtml(
            rewriteHtmlAssetUrls(blankToDefault(stringValue(question.get("stem_html")), stringValue(question.get("content_html"))), assets)
        );
        return new PromptHtml(promptHtml, promptHtml);
    }

    private List<Map<String, Object>> serializeBlanks(List<Map<String, Object>> blanks, String answerText, List<String> answerValues) {
        String blankAnswer = CharSequenceUtil.isNotBlank(answerText) ? answerText : String.join(" | ", answerValues);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> blank : blanks) {
            String blankId = trimToNull(stringValue(blank.get("blank_id")));
            if (blankId == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", blankId);
            item.put("answer", blankAnswer);
            result.add(item);
        }
        return result;
    }

    private String extractPreciseBlankFragmentHtml(String contentHtml, String blankId) {
        String html = trimToEmpty(contentHtml);
        String normalizedBlankId = trimToEmpty(blankId);
        if (CharSequenceUtil.isBlank(html) || CharSequenceUtil.isBlank(normalizedBlankId)) {
            return "";
        }
        String safeBlankId = Pattern.quote(normalizedBlankId);
        for (String tag : List.of("p", "li", "tr", "td", "th")) {
            Pattern pattern = Pattern.compile("<" + tag + "\\b[^>]*>(?:(?!</" + tag + ">).)*\\[\\[" + safeBlankId + "\\]\\](?:(?!</" + tag + ">).)*</" + tag + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        return "";
    }

    private String normalizeBlankQuestionFragmentHtml(String contentHtml) {
        String html = trimToEmpty(contentHtml);
        if (Pattern.compile("<(?:tr|td|th)\\b", Pattern.CASE_INSENSITIVE).matcher(html).find()) {
            Matcher matcher = Pattern.compile("<t[dh]\\b[^>]*>(.*?)</t[dh]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            List<String> cells = new ArrayList<>();
            while (matcher.find()) {
                String cell = trimToNull(matcher.group(1));
                if (cell != null) {
                    cells.add(cell);
                }
            }
            if (!cells.isEmpty()) {
                html = String.join(" ", cells);
            }
        }
        html = html.replaceAll("(?<=[A-Za-z0-9])(\\{\\{\\s*[^}]+\\s*\\}\\}|\\[\\[\\s*[^]]+\\s*\\]\\])", " $1");
        html = html.replaceAll("(\\{\\{\\s*[^}]+\\s*\\}\\}|\\[\\[\\s*[^]]+\\s*\\]\\])(?=[A-Za-z0-9])", "$1 ");
        return html.trim();
    }

    private String normalizeFragmentBlankPlaceholders(String contentHtml, String activeBlankId, boolean keepActiveBlank) {
        String html = trimToEmpty(contentHtml);
        String normalizedActiveBlankId = trimToEmpty(activeBlankId);
        Matcher matcher = BLANK_PLACEHOLDER_PATTERN.matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String blankId = trimToEmpty(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
            String replacement = keepActiveBlank && blankId.equals(normalizedActiveBlankId) ? matcher.group(0) : "_____";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return normalizeBlankQuestionFragmentHtml(buffer.toString());
    }

    private String rewriteHtmlAssetUrls(String contentHtml, List<Map<String, Object>> assets) {
        String html = trimToEmpty(contentHtml);
        if (CharSequenceUtil.isBlank(html)) {
            return "";
        }
        for (Map<String, Object> asset : assets) {
            String sourcePath = trimToNull(stringValue(asset.get("source_path")));
            String assetUrl = resolveAssetUrl(asset);
            if (sourcePath != null && CharSequenceUtil.isNotBlank(assetUrl)) {
                html = html.replace(sourcePath, assetUrl);
            }
        }
        return html;
    }

    private String resolvePrimaryAssetUrl(Long primaryAssetId, List<Map<String, Object>> assets, String assetType) {
        if (primaryAssetId != null) {
            for (Map<String, Object> asset : assets) {
                if (longValue(asset.get("exam_asset_id")) == primaryAssetId
                    && (assetType == null || assetType.equalsIgnoreCase(stringValue(asset.get("asset_type"))))) {
                    String url = resolveAssetUrl(asset);
                    if (CharSequenceUtil.isNotBlank(url)) {
                        return url;
                    }
                }
            }
        }
        for (Map<String, Object> asset : assets) {
            if (assetType == null || assetType.equalsIgnoreCase(stringValue(asset.get("asset_type")))) {
                String url = resolveAssetUrl(asset);
                if (CharSequenceUtil.isNotBlank(url)) {
                    return url;
                }
            }
        }
        return "";
    }

    private String resolveAssetUrl(Map<String, Object> asset) {
        String url = trimToNull(stringValue(asset.get("asset_url")));
        if (url != null) {
            return url;
        }
        String storagePath = trimToNull(stringValue(asset.get("storage_path")));
        if (storagePath == null) {
            return "";
        }
        storagePath = storagePath.replace("\\", "/");
        if (storagePath.startsWith("http://") || storagePath.startsWith("https://") || storagePath.startsWith("/")) {
            return storagePath;
        }
        if (storagePath.startsWith("exam-assets/")) {
            return "/" + storagePath;
        }
        return "/exam-assets/" + storagePath;
    }

    private Map<String, Object> repairPayload(Map<String, Object> payload) {
        for (Map<String, Object> passage : listOfMaps(payload.get("passages"))) {
            normalizeClozeQuestionFragments(listOfMaps(passage.get("questions")));
            for (Map<String, Object> group : listOfMaps(passage.get("groups"))) {
                normalizeClozeQuestionFragments(listOfMaps(group.get("questions")));
            }
        }
        return payload;
    }

    private void normalizeClozeQuestionFragments(List<Map<String, Object>> questions) {
        for (Map<String, Object> question : questions) {
            if (!"cloze_inline".equals(inferPayloadQuestionType(question))) {
                continue;
            }
            question.put("stem", normalizeBlankQuestionFragmentHtml(stringValue(question.get("stem"))));
            question.put("content", normalizeBlankQuestionFragmentHtml(stringValue(question.get("content"))));
        }
    }

    private List<String> extractAnswerValues(Map<String, Object> answerRow) {
        if (answerRow == null) {
            return List.of();
        }
        Object answerJson = parseJson(answerRow.get("answer_json"));
        if (answerJson instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                String value = trimToNull(stringValue(item));
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }
        String raw = trimToNull(stringValue(answerRow.get("answer_raw")));
        return raw == null ? List.of() : List.of(raw);
    }

    private Map<String, Object> mergeParentSnapshot(Map<String, Object> section, Map<String, Object> group, Map<String, Object> question) {
        Map<String, Object> merged = new LinkedHashMap<>(question);
        merged.put("exam_section_id", section.get("exam_section_id"));
        merged.put("exam_paper_id", section.get("exam_paper_id"));
        merged.put("section_id", section.get("section_id"));
        merged.put("section_no", section.get("section_no"));
        merged.put("exam_group_id", group.get("exam_group_id"));
        merged.put("group_id", group.get("group_id"));
        merged.put("group_raw_type", group.get("raw_type"));
        merged.put("group_stat_type", group.get("stat_type"));
        merged.put("group_content_html", group.get("content_html"));
        return merged;
    }

    private Map<String, Object> serializeWrongQuestionItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("exam_question_id", nullableLong(row.get("exam_question_id")));
        item.put("exam_paper_id", longValue(row.get("exam_paper_id")));
        item.put("paper_code", trimToNull(stringValue(row.get("paper_code"))));
        item.put("paper_title", stringValue(row.get("paper_title")));
        item.put("exam_section_id", nullableLong(row.get("exam_section_id")));
        item.put("section_title", trimToNull(stringValue(row.get("section_title"))));
        item.put("exam_group_id", nullableLong(row.get("exam_group_id")));
        item.put("group_title", trimToNull(stringValue(row.get("group_title"))));
        item.put("exam_content", trimToNull(stringValue(row.get("exam_content"))));
        item.put("question_id", stringValue(row.get("question_id")));
        item.put("question_no", trimToNull(stringValue(row.get("question_no"))));
        item.put("question_type", trimToNull(stringValue(row.get("question_type"))));
        item.put("stat_type", trimToNull(stringValue(row.get("stat_type"))));
        item.put("preview_text", trimToEmpty(stringValue(row.get("preview_text"))));
        item.put("wrong_count", intValue(row.get("wrong_count")));
        item.put("last_wrong_time", row.get("latest_wrong_time"));
        return item;
    }

    private Map<String, Object> submitResponse(Map<String, Object> result, Map<String, Object> submission, Map<String, Object> wrongbookReview) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("submission_id", longValue(submission.get("mockexam_submission_id")));
        summary.put("title", stringValue(submission.get("title")));
        summary.put("elapsed_seconds", intValue(submission.get("elapsed_seconds")));
        summary.put("create_time", submission.get("create_time"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("result", result);
        response.put("submission", summary);
        response.put("wrongbook_review", wrongbookReview);
        return response;
    }

    private Map<String, Object> mutationResponse(Map<String, Object> item) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("item", item);
        return response;
    }

    private Map<String, Object> favoriteResponse(boolean favorite, int affectedCount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("is_favorite", favorite);
        response.put("affected_count", affectedCount);
        return response;
    }

    private String normalizeExamCategory(String value, boolean allowEmpty) {
        String normalized = trimToEmpty(value).toUpperCase(Locale.ROOT);
        if (CharSequenceUtil.isBlank(normalized) && allowEmpty) {
            return "";
        }
        if (!"IELTS".equals(normalized)) {
            throw badRequest("mock exam only supports IELTS");
        }
        return normalized;
    }

    private String normalizeExamContent(String value, boolean allowEmpty) {
        String normalized = titleCase(trimToEmpty(value));
        if (CharSequenceUtil.isBlank(normalized) && allowEmpty) {
            return "";
        }
        if (!IELTS_CONTENTS.contains(normalized)) {
            throw badRequest("mock exam only supports IELTS Reading / Listening");
        }
        return normalized;
    }

    private String normalizePaperSetContent(String value, boolean allowEmpty) {
        String normalized = titleCase(trimToEmpty(value));
        if (CharSequenceUtil.isBlank(normalized) && allowEmpty) {
            return "";
        }
        if (!PAPER_SET_CONTENTS.contains(normalized)) {
            throw badRequest("paper set content only supports Listening / Reading / Mixed");
        }
        return normalized;
    }

    private String normalizeFavoriteSourceKind(String value) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (CharSequenceUtil.isBlank(normalized)) {
            return PAPER_SOURCE_KIND;
        }
        if (!PAPER_SOURCE_KIND.equals(normalized) && !PAPER_SET_SOURCE_KIND.equals(normalized)) {
            throw badRequest("source_kind only supports paper or paper_set");
        }
        return normalized;
    }

    private String subjectTypeFromExamContent(String examContent) {
        return "Listening".equals(examContent) ? "listening" : "reading";
    }

    private String examContentFromSubjectType(String subjectType) {
        return "listening".equalsIgnoreCase(trimToEmpty(subjectType)) ? "Listening" : "Reading";
    }

    private String buildPaperSetPaperCode(long paperSetId) {
        return PAPER_SET_CODE_PREFIX + paperSetId;
    }

    private SourceMeta extractSourceMeta(String paperCode, Object payloadJson) {
        Long paperSetId = null;
        if (payloadJson instanceof Map<?, ?> map) {
            Object meta = map.get("_meta");
            if (meta instanceof Map<?, ?> metaMap) {
                paperSetId = nullableLong(metaMap.get("paper_set_id"));
            }
        }
        if (paperSetId == null) {
            Matcher matcher = PAPER_SET_CODE_PATTERN.matcher(trimToEmpty(paperCode));
            if (matcher.matches()) {
                paperSetId = Long.parseLong(matcher.group(1));
            }
        }
        return paperSetId == null ? new SourceMeta(PAPER_SOURCE_KIND, null) : new SourceMeta(PAPER_SET_SOURCE_KIND, paperSetId);
    }

    private String questionBusinessId(Map<String, Object> question) {
        String code = trimToNull(stringValue(question.get("question_code")));
        if (code != null) {
            return code;
        }
        String questionId = trimToNull(stringValue(question.get("question_id")));
        if (questionId != null) {
            return questionId;
        }
        Long examQuestionId = nullableLong(question.get("exam_question_id"));
        return examQuestionId == null ? "Q" : "Q-" + examQuestionId;
    }

    private Object nullableQuestionNo(Object value) {
        String text = trimToNull(stringValue(value));
        return text;
    }

    private String paperTitle(Map<String, Object> row) {
        return blankToDefault(stringValue(row.get("paper_name")), blankToDefault(stringValue(row.get("paper_code")), "Paper " + stringValue(row.get("exam_paper_id"))));
    }

    private String passageGroupFallbackId(Map<String, Object> section, Map<String, Object> group) {
        return blankToDefault(stringValue(section.get("section_id")), "section") + "-G" + blankToDefault(stringValue(group.get("sort_order")), stringValue(group.get("exam_group_id")));
    }

    private String buildQuestionFallbackHtml(Map<String, Object> question) {
        String questionNo = trimToNull(stringValue(question.get("question_no")));
        return questionNo == null ? "<p>" + questionBusinessId(question) + "</p>" : "<p>Question " + questionNo + "</p>";
    }

    private String buildBlankQuestionContent(Map<String, Object> question, List<Map<String, Object>> blanks) {
        String blankId = blanks.isEmpty() ? null : trimToNull(stringValue(blanks.get(0).get("blank_id")));
        if (blankId != null) {
            String questionNo = trimToNull(stringValue(question.get("question_no")));
            return questionNo == null ? "<p>[[" + blankId + "]]</p>" : "<p>Question " + questionNo + ": [[" + blankId + "]]</p>";
        }
        return buildQuestionFallbackHtml(question);
    }

    private String previewText(String value, int limit) {
        String text = trimToEmpty(value);
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit)).trim();
    }

    private String answerValueToString(Object value) {
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return JSONUtil.toJsonStr(value);
        }
        return trimToEmpty(stringValue(value));
    }

    private boolean answerValuePresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return CharSequenceUtil.isNotBlank(stringValue);
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty() && collection.stream().anyMatch(this::answerValuePresent);
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty() && map.values().stream().anyMatch(this::answerValuePresent);
        }
        return true;
    }

    private List<Object> toObjectList(Object value) {
        Object parsed = parseJson(value);
        if (parsed instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (parsed instanceof JSONArray array) {
            List<Object> result = new ArrayList<>();
            for (Object item : array) {
                result.add(item);
            }
            return result;
        }
        if (parsed == null) {
            return List.of();
        }
        return List.of(parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        Object parsed = parseJson(value);
        if (parsed instanceof JSONObject jsonObject) {
            return jsonObject.toBean(LinkedHashMap.class);
        }
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(stringValue(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        return safeMap(JSONUtil.toJsonStr(source));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        Object parsed = parseJson(value);
        if (parsed instanceof Collection<?> collection) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    result.add(safeMap(map));
                } else if (item instanceof JSONObject jsonObject) {
                    result.add(jsonObject.toBean(LinkedHashMap.class));
                }
            }
            return result;
        }
        if (parsed instanceof JSONArray array) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof Map<?, ?> || item instanceof JSONObject) {
                    result.add(safeMap(item));
                }
            }
            return result;
        }
        return List.of();
    }

    private Object parseJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?> || value instanceof JSONObject || value instanceof JSONArray) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return parseJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        String text = trimToNull(stringValue(value));
        if (text == null) {
            return null;
        }
        String first = text.substring(0, 1);
        if (!"{".equals(first) && !"[".equals(first)) {
            return value;
        }
        try {
            Object parsed = JSONUtil.parse(text);
            if (parsed instanceof JSONObject jsonObject) {
                return jsonObject.toBean(LinkedHashMap.class);
            }
            if (parsed instanceof JSONArray jsonArray) {
                List<Object> result = new ArrayList<>();
                for (Object item : jsonArray) {
                    result.add(item);
                }
                return result;
            }
            return parsed;
        } catch (Exception ignored) {
            return value;
        }
    }

    private List<Long> ids(List<Map<String, Object>> rows, String column) {
        List<Long> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long value = nullableLong(row.get(column));
            if (value != null && value > 0 && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<Long, List<Map<String, Object>>> groupByLong(List<Map<String, Object>> rows, String column) {
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long value = nullableLong(row.get(column));
            if (value != null && value > 0) {
                result.computeIfAbsent(value, key -> new ArrayList<>()).add(row);
            }
        }
        return result;
    }

    private int clampInt(Integer value, int defaultValue, int min, int max) {
        int parsed = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, parsed));
    }

    private int normalizeElapsedSeconds(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(value, 24 * 60 * 60));
    }

    private String titleCase(String value) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (CharSequenceUtil.isBlank(normalized)) {
            return "";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private String trimToNull(String value) {
        String trimmed = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(trimmed) ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
    }

    private String blankToDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object valueOrDefault(Object value, Object defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(trimToEmpty(stringValue(value)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(trimToEmpty(stringValue(value)));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private long longValue(Object value) {
        Long result = nullableLong(value);
        return result == null ? 0L : result;
    }

    private Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = trimToNull(stringValue(value));
            return text == null ? null : Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = trimToNull(stringValue(value));
            return text == null ? null : Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean boolValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = trimToEmpty(stringValue(value)).toLowerCase(Locale.ROOT);
        return Set.of("1", "true", "yes", "y").contains(text);
    }

    private Timestamp timestampNow() {
        return Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
    }

    private double roundTwo(double value) {
        return NumberUtil.round(value, 2).doubleValue();
    }

    private double roundOne(double value) {
        return NumberUtil.round(value, 1).doubleValue();
    }

    private List<Long> dedupePositiveLongs(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                result.add(value);
            }
        }
        return new ArrayList<>(result);
    }

    private String mostCommonType(List<Map<String, Object>> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String type = trimToNull(blankToDefault(stringValue(row.get("stat_type")), stringValue(row.get("question_type"))));
            if (type != null) {
                counts.put(type, counts.getOrDefault(type, 0) + 1);
            }
        }
        return counts.entrySet().stream()
            .sorted((left, right) -> {
                int countCompare = Integer.compare(right.getValue(), left.getValue());
                return countCompare != 0 ? countCompare : left.getKey().compareTo(right.getKey());
            })
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private int sortableQuestionNumber(Object value) {
        Matcher matcher = Pattern.compile("\\d+").matcher(stringValue(value));
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareDate(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        return stringValue(left).compareTo(stringValue(right));
    }

    private void addIfNotBlank(List<String> parts, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            parts.add(normalized);
        }
    }

    private String effectiveRuntimeValue(String configKey, String defaultValue) {
        Map<String, Object> row = mockExamMapper.findRuntimeConfig(configKey);
        if (row != null && "active".equals(stringValue(row.get("status"))) && CharSequenceUtil.isNotBlank(stringValue(row.get("config_value")))) {
            return stringValue(row.get("config_value"));
        }
        return defaultValue;
    }

    private String renderPrompt(String promptContent, Map<String, Object> context) {
        String rendered = blankToDefault(promptContent, "Translate selected_text into target_lang and return JSON.");
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String key = entry.getKey();
            String value = stringValue(entry.getValue());
            rendered = rendered.replace("{{" + key + "}}", value);
            rendered = rendered.replace("{" + key + "}", value);
        }
        return rendered;
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = trimToEmpty(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private record PaperBundle(
        Map<String, Object> paper,
        Map<String, Object> payload
    ) {
    }

    private record PaperSetBundle(
        Map<String, Object> paperSet,
        Map<String, Object> payload
    ) {
    }

    private record PromptHtml(
        String promptHtml,
        String promptWithBlankHtml
    ) {
    }

    private record SourceMeta(
        String sourceKind,
        Long paperSetId
    ) {
    }

    private record Evaluation(
        boolean answered,
        boolean correct,
        boolean gradable
    ) {
    }

    private record QuestionRecord(
        Map<String, Object> question,
        String id,
        Long examQuestionId,
        String questionNo,
        int questionIndex,
        String questionType,
        String statType
    ) {
    }

    private record AnswerEntry(
        Long examQuestionId,
        String questionId,
        String questionNo,
        String questionType,
        String statType,
        String blankId,
        String itemId,
        String answerValue,
        int sortOrder
    ) {
    }

    private record QuestionStateEntry(
        Long examQuestionId,
        String questionId,
        String questionNo,
        int questionIndex,
        String questionType,
        String statType,
        int marked,
        int answered,
        Integer correct
    ) {
    }

    private static final class TypeCounter {
        private final String questionType;
        private int totalQuestions;
        private int answeredCount;
        private int gradableQuestions;
        private int correctCount;

        private TypeCounter(String questionType) {
            this.questionType = questionType;
        }

        private void accept(Evaluation evaluation) {
            totalQuestions++;
            if (evaluation.answered()) {
                answeredCount++;
            }
            if (evaluation.gradable()) {
                gradableQuestions++;
                if (evaluation.correct()) {
                    correctCount++;
                }
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_type", questionType);
            item.put("total_questions", totalQuestions);
            item.put("answered_count", answeredCount);
            item.put("gradable_questions", gradableQuestions);
            item.put("correct_count", correctCount);
            item.put("wrong_count", Math.max(0, gradableQuestions - correctCount));
            item.put("unanswered_count", Math.max(0, totalQuestions - answeredCount));
            return item;
        }
    }
}
