package com.earthseaedu.backend.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.ActImportJobMapper;
import com.earthseaedu.backend.mapper.ActQuestionBankImportMapper;
import com.earthseaedu.backend.mapper.MockExamPaperRefMapper;
import com.earthseaedu.backend.mapper.MockExamQuestionRefMapper;
import com.earthseaedu.backend.model.mockexam.MockExamPaperRef;
import com.earthseaedu.backend.model.mockexam.MockExamQuestionRef;
import com.earthseaedu.backend.service.MockExamActService;
import com.earthseaedu.backend.service.MockExamService;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MockExamActServiceImpl implements MockExamActService {

    private static final String JOB_NAME_PREFIX = "act_question_bank_import_";
    private static final String SOURCE_TYPE_ACT = "ACT";
    private static final String EXAM_CATEGORY_ACT = "ACT";
    private static final Set<String> SOURCE_MODES = Set.of("zip", "directory", "files");
    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([^;,]+);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern DATE_IN_NAME_PATTERN = Pattern.compile("(20\\d{6})");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final List<SectionSpec> SECTION_SPECS = List.of(
        new SectionSpec("english", "English", 1),
        new SectionSpec("math", "Math", 2),
        new SectionSpec("reading", "Reading", 3),
        new SectionSpec("science", "Science", 4),
        new SectionSpec("writing", "Writing", 5)
    );

    private final ActImportJobMapper actImportJobMapper;
    private final ActQuestionBankImportMapper actQuestionBankImportMapper;
    private final MockExamPaperRefMapper mockExamPaperRefMapper;
    private final MockExamQuestionRefMapper mockExamQuestionRefMapper;
    private final StoragePathSupport storagePathSupport;
    private final MockExamService mockExamService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "act-question-bank-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    public MockExamActServiceImpl(
        ActImportJobMapper actImportJobMapper,
        ActQuestionBankImportMapper actQuestionBankImportMapper,
        MockExamPaperRefMapper mockExamPaperRefMapper,
        MockExamQuestionRefMapper mockExamQuestionRefMapper,
        StoragePathSupport storagePathSupport,
        MockExamService mockExamService,
        PlatformTransactionManager transactionManager
    ) {
        this.actImportJobMapper = actImportJobMapper;
        this.actQuestionBankImportMapper = actQuestionBankImportMapper;
        this.mockExamPaperRefMapper = mockExamPaperRefMapper;
        this.mockExamQuestionRefMapper = mockExamQuestionRefMapper;
        this.storagePathSupport = storagePathSupport;
        this.mockExamService = mockExamService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Map<String, Object> getOptions() {
        Map<String, Object> genericOptions = mockExamService.getOptions();
        List<String> contentOptions = extractActContentOptions(genericOptions);

        Map<String, Object> contentOptionsMap = new LinkedHashMap<>();
        contentOptionsMap.put(EXAM_CATEGORY_ACT, contentOptions);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("exam_category_options", List.of(EXAM_CATEGORY_ACT));
        response.put("supported_categories", List.of(EXAM_CATEGORY_ACT));
        response.put("content_options", contentOptions);
        response.put("content_options_map", contentOptionsMap);
        return response;
    }

    @Override
    public Map<String, Object> listPapers(String examContent) {
        return mockExamService.listPapers(EXAM_CATEGORY_ACT, examContent);
    }

    @Override
    public Map<String, Object> getPaper(long examPaperId) {
        return getPaper(examPaperId, null);
    }

    @Override
    public Map<String, Object> getPaper(long examPaperId, String examContent) {
        long normalizedPaperId = examPaperId > 0 ? -examPaperId : examPaperId;
        return mockExamService.getPaper(normalizedPaperId, examContent);
    }

    @Override
    public Map<String, Object> createImportJob(
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<MultipartFile> files
    ) {
        String normalizedSourceMode = normalizeSourceMode(sourceMode);
        validateUploadedFiles(normalizedSourceMode, files);

        long jobId = insertImportJob(normalizedSourceMode, trimToNull(batchName), files.size());
        String storagePath = "act_job_" + jobId;
        try {
            persistUploadedFiles(storagePath, normalizedSourceMode, trimToNull(batchName), entryPathsJson, files);
            actImportJobMapper.updateImportJobStoragePath(
                jobId,
                storagePath,
                "ACT import job created; waiting for JSON parsing"
            );
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save ACT import files: " + exception.getMessage());
        }

        executorService.submit(() -> processImportJob(jobId));
        return getImportJobDetail(jobId);
    }

    @Override
    public Map<String, Object> getImportJobDetail(long jobId) {
        return serializeImportJob(requireActImportJobRow(jobId));
    }

    private List<String> extractActContentOptions(Map<String, Object> genericOptions) {
        List<String> contentOptions = new ArrayList<>();
        Object contentOptionsMapObject = genericOptions.get("content_options_map");
        if (!(contentOptionsMapObject instanceof Map<?, ?> contentOptionsMap)) {
            return contentOptions;
        }

        Object rawOptions = contentOptionsMap.get(EXAM_CATEGORY_ACT);
        if (!(rawOptions instanceof Iterable<?> values)) {
            return contentOptions;
        }

        for (Object value : values) {
            if (value != null) {
                contentOptions.add(String.valueOf(value));
            }
        }
        return contentOptions;
    }

    private long insertImportJob(String sourceMode, String batchName, int uploadedFileCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobName", JOB_NAME_PREFIX + System.currentTimeMillis());
        row.put("batchName", batchName);
        row.put("sourceMode", sourceMode);
        row.put("uploadedFileCount", uploadedFileCount);
        row.put("progressMessage", "ACT import job created; waiting for JSON parsing");
        actImportJobMapper.insertImportJob(row);
        return generatedId(row, "Failed to create ACT import job");
    }

    private void persistUploadedFiles(
        String storagePath,
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<MultipartFile> files
    ) throws IOException {
        Path jobDir = importJobRoot().resolve(storagePath);
        FileUtil.mkdir(jobDir.toFile());

        JSONArray fileMetas = new JSONArray();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String storedName = "file_%05d.bin".formatted(index + 1);
            Path destination = jobDir.resolve(storedName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            JSONObject fileMeta = new JSONObject();
            fileMeta.set("filename", CharSequenceUtil.blankToDefault(file.getOriginalFilename(), storedName));
            fileMeta.set("stored_name", storedName);
            fileMetas.add(fileMeta);
        }

        JSONObject metadata = new JSONObject();
        metadata.set("source_mode", sourceMode);
        metadata.set("batch_name", batchName);
        metadata.set("entry_paths_json", entryPathsJson);
        metadata.set("files", fileMetas);
        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(metadata), jobDir.resolve("metadata.json").toFile());
    }

    private void processImportJob(long jobId) {
        Long batchId = null;
        try {
            actImportJobMapper.markImportJobRunning(jobId, "ACT JSON parsing started");
            Map<String, Object> row = requireActImportJobRow(jobId);
            String jobStoragePath = stringValue(column(row, "storage_path"));
            ImportMetadata metadata = loadMetadata(jobStoragePath);
            batchId = insertImportBatch(jobId, jobStoragePath, metadata);
            Long activeBatchId = batchId;
            ImportResult result = importFromStoredFiles(metadata, activeBatchId, progress -> {
                updateJobProgress(jobId, progress);
                updateImportBatchProgress(activeBatchId, progress);
            });
            updateJobCompleted(jobId, result);
            updateImportBatchCompleted(activeBatchId, result);
        } catch (Exception exception) {
            markJobFailed(jobId, exception);
            if (batchId != null) {
                updateImportBatchFailed(batchId, exception);
            }
        }
    }

    private ImportResult importFromStoredFiles(ImportMetadata metadata, long batchId, ProgressSink progressSink) {
        List<String> entryPaths = parseEntryPaths(metadata.entryPathsJson(), metadata.files().size());
        List<ResolvedJsonFile> resolvedJsonFiles = resolveJsonFiles(metadata, entryPaths);
        if (resolvedJsonFiles.isEmpty()) {
            throw new IllegalArgumentException("No ACT JSON files were found");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        Counts counts = new Counts();
        int skippedCount = 0;

        emitProgress(progressSink, resolvedJsonFiles.size(), items, failures, counts, skippedCount, "Found " + resolvedJsonFiles.size() + " ACT JSON file(s)");

        for (int index = 0; index < resolvedJsonFiles.size(); index++) {
            ResolvedJsonFile jsonFile = resolvedJsonFiles.get(index);
            try {
                FileImportResult item = transactionTemplate.execute(status -> importSingleJsonFile(jsonFile, batchId));
                if (item == null) {
                    throw new IllegalStateException("ACT import transaction returned no result");
                }
                if (item.skipped()) {
                    skippedCount++;
                } else {
                    counts.add(item.counts());
                }
                items.add(item.payload());
            } catch (Exception exception) {
                failures.add(failure(jsonFile.sourceName(), exception));
                insertFailedSourceFile(batchId, jsonFile, exception);
            }
            emitProgress(
                progressSink,
                resolvedJsonFiles.size(),
                items,
                failures,
                counts,
                skippedCount,
                progressMessage(index + 1, resolvedJsonFiles.size(), counts, skippedCount, failures)
            );
        }

        if (counts.paperCount == 0 && skippedCount == 0) {
            String message = failures.stream()
                .limit(5)
                .map(item -> stringValue(item.get("source_name")) + ": " + stringValue(item.get("message")))
                .reduce((left, right) -> left + "; " + right)
                .orElse("No ACT paper was imported");
            throw new IllegalArgumentException(message);
        }

        return new ImportResult(resolvedJsonFiles.size(), counts, skippedCount, failures.size(), items, failures);
    }

    private FileImportResult importSingleJsonFile(ResolvedJsonFile jsonFile, long batchId) {
        JSONObject root = JSONUtil.parseObj(new String(jsonFile.bytes(), StandardCharsets.UTF_8));
        PaperMetadata paperMetadata = parsePaperMetadata(jsonFile);
        Counts plannedCounts = countQuestions(root);
        if (plannedCounts.questionCount == 0) {
            throw new IllegalArgumentException("ACT JSON contains no supported section questions");
        }

        Map<String, Object> existingPaper = actQuestionBankImportMapper.findActivePaperByCode(paperMetadata.paperCode());
        if (existingPaper != null) {
            insertSourceFile(batchId, longValue(column(existingPaper, "act_paper_id")), jsonFile, "SKIPPED", skippedParseResult(existingPaper), null);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source_name", jsonFile.sourceName());
            payload.put("paper_code", paperMetadata.paperCode());
            payload.put("paper_name", column(existingPaper, "paper_name"));
            payload.put("import_status", "skipped_existing");
            payload.put("question_count", intValue(column(existingPaper, "question_count")));
            return new FileImportResult(true, new Counts(), payload);
        }

        long paperId = insertPaper(paperMetadata, jsonFile, plannedCounts);
        insertSourceFile(batchId, paperId, jsonFile, "PARSED", parsedParseResult(plannedCounts), null);

        ImportContext context = new ImportContext(paperId, paperMetadata.paperCode());
        List<QuestionRefDraft> questionRefs = new ArrayList<>();

        for (SectionSpec sectionSpec : SECTION_SPECS) {
            JSONObject sectionObject = root.getJSONObject(sectionSpec.code());
            if (sectionObject == null) {
                continue;
            }
            JSONArray questions = sectionObject.getJSONArray("default");
            if (questions == null || questions.isEmpty()) {
                continue;
            }

            long sectionId = insertSection(paperId, sectionSpec, questions.size());
            context.counts.sectionCount++;
            Map<String, PassageInsertResult> passageCache = new HashMap<>();

            for (int index = 0; index < questions.size(); index++) {
                Object item = questions.get(index);
                if (!(item instanceof JSONObject question)) {
                    continue;
                }
                QuestionInsertResult inserted = insertQuestionTree(
                    context,
                    sectionSpec,
                    sectionId,
                    question,
                    index,
                    passageCache
                );
                questionRefs.add(inserted.questionRef());
            }
        }

        int refCount = syncMockExamRefs(paperMetadata, paperId, questionRefs, context.counts);
        context.counts.questionRefCount = refCount;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_name", jsonFile.sourceName());
        payload.put("paper_code", paperMetadata.paperCode());
        payload.put("paper_name", paperMetadata.paperName());
        payload.put("import_status", "imported");
        payload.put("paper_id", paperId);
        payload.put("question_count", context.counts.questionCount);
        payload.put("option_count", context.counts.optionCount);
        payload.put("asset_count", context.counts.assetCount);
        payload.put("question_ref_count", refCount);
        return new FileImportResult(false, context.counts, payload);
    }

    private QuestionInsertResult insertQuestionTree(
        ImportContext context,
        SectionSpec sectionSpec,
        long sectionId,
        JSONObject question,
        int sectionQuestionIndex,
        Map<String, PassageInsertResult> passageCache
    ) {
        int sourceQuestionNo = intValue(question.get("id"));
        if (sourceQuestionNo <= 0) {
            sourceQuestionNo = context.nextSortOrder;
        }
        String questionNoDisplay = String.valueOf(sourceQuestionNo);
        String sourceJsonPath = "$." + sectionSpec.code() + ".default[" + sectionQuestionIndex + "]";

        String answerRaw = trimToNull(question.getStr("answer"));
        JSONArray options = parseAnswerOptions(question.getStr("answerOptions"));
        boolean hasOptions = options != null && !options.isEmpty();
        boolean autoGradable = hasOptions && answerRaw != null;
        String questionType = autoGradable ? "SINGLE_CHOICE" : "ESSAY";
        String responseMode = autoGradable ? "RADIO" : "TEXTAREA";

        PassageInsertResult passage = getOrCreatePassage(
            context,
            sectionSpec,
            sectionId,
            question.getStr("stimulus"),
            sourceJsonPath + ".stimulus",
            passageCache
        );

        String groupCode = context.paperCode + "_" + sectionSpec.code().toUpperCase(Locale.ROOT) + "_G" + sourceQuestionNo;
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("actPaperId", context.paperId);
        group.put("actSectionId", sectionId);
        group.put("actPassageId", passage == null ? null : passage.passageId());
        group.put("groupCode", groupCode);
        group.put("groupTitle", "Question " + questionNoDisplay);
        group.put("sourceQuestionNo", sourceQuestionNo);
        group.put("rawType", questionType);
        group.put("statType", questionType);
        group.put("instructionsHtml", null);
        group.put("instructionsText", null);
        group.put("contentHtml", passage == null ? null : passage.contentHtml());
        group.put("contentText", passage == null ? null : passage.contentText());
        group.put("hasSharedOptions", hasOptions ? 1 : 0);
        group.put("hasBlanks", 0);
        group.put("structureJson", null);
        group.put("sortOrder", context.nextSortOrder);
        group.put("remark", null);
        actQuestionBankImportMapper.insertActGroup(group);
        long groupId = generatedId(group, "Failed to insert ACT group");
        context.counts.groupCount++;

        List<String> optionKeys = new ArrayList<>();
        if (hasOptions) {
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                Object optionObject = options.get(optionIndex);
                if (!(optionObject instanceof JSONObject optionJson)) {
                    continue;
                }
                String optionKey = optionKey(optionIndex);
                optionKeys.add(optionKey);
                ProcessedHtml processedOption = processHtml(
                    optionJson.getStr("content"),
                    context.paperCode,
                    "option_" + sourceQuestionNo + "_" + optionKey,
                    sourceJsonPath + ".answerOptions[" + optionIndex + "].content",
                    "answerOptions.content",
                    "OPTION_IMAGE"
                );
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("actGroupId", groupId);
                option.put("optionKey", optionKey);
                option.put("optionHtml", processedOption.html());
                option.put("optionText", stripHtml(processedOption.html()));
                option.put("structureJson", null);
                option.put("sortOrder", optionIndex + 1);
                option.put("remark", null);
                actQuestionBankImportMapper.insertActGroupOption(option);
                long optionId = generatedId(option, "Failed to insert ACT option");
                context.counts.optionCount++;
                context.counts.assetCount += insertAssets(
                    processedOption.assets(),
                    new AssetOwner(context.paperId, sectionId, passage == null ? null : passage.passageId(), groupId, optionId, null, "OPTION", optionId)
                );
            }
        }

        ProcessedHtml processedStem = processHtml(
            question.getStr("stem"),
            context.paperCode,
            "question_" + sourceQuestionNo + "_stem",
            sourceJsonPath + ".stem",
            "stem",
            "STEM_IMAGE"
        );

        String questionCode = context.paperCode + "_" + sectionSpec.code().toUpperCase(Locale.ROOT) + "_Q" + sourceQuestionNo;
        BigDecimal score = autoGradable ? BigDecimal.ONE : null;
        Map<String, Object> questionRow = new LinkedHashMap<>();
        questionRow.put("actPaperId", context.paperId);
        questionRow.put("actSectionId", sectionId);
        questionRow.put("actPassageId", passage == null ? null : passage.passageId());
        questionRow.put("actGroupId", groupId);
        questionRow.put("questionCode", questionCode);
        questionRow.put("sourceQuestionNo", sourceQuestionNo);
        questionRow.put("questionNoDisplay", questionNoDisplay);
        questionRow.put("questionType", questionType);
        questionRow.put("responseMode", responseMode);
        questionRow.put("autoGradable", autoGradable ? 1 : 0);
        questionRow.put("stemHtml", processedStem.html());
        questionRow.put("stemText", stripHtml(processedStem.html()));
        questionRow.put("contentHtml", null);
        questionRow.put("contentText", null);
        questionRow.put("answerInputSchemaJson", answerInputSchema(responseMode));
        questionRow.put("score", score);
        questionRow.put("sourceJsonPath", sourceJsonPath);
        questionRow.put("rawJson", normalizedRawQuestion(question, optionKeys));
        questionRow.put("sortOrder", context.nextSortOrder);
        questionRow.put("remark", null);
        actQuestionBankImportMapper.insertActQuestion(questionRow);
        long questionId = generatedId(questionRow, "Failed to insert ACT question");
        context.counts.questionCount++;
        if (autoGradable) {
            context.counts.autoGradableQuestionCount++;
        }

        context.counts.assetCount += insertAssets(
            processedStem.assets(),
            new AssetOwner(context.paperId, sectionId, passage == null ? null : passage.passageId(), groupId, null, questionId, "QUESTION", questionId)
        );

        if (answerRaw != null || !autoGradable) {
            insertQuestionAnswer(questionId, answerRaw, autoGradable);
            if (answerRaw != null) {
                context.counts.answerCount++;
            }
        }

        QuestionRefDraft ref = new QuestionRefDraft(
            questionId,
            questionCode,
            questionNoDisplay,
            questionType,
            responseMode,
            questionType,
            score,
            stringValue(questionRow.get("stemText"))
        );
        context.nextSortOrder++;
        return new QuestionInsertResult(ref);
    }

    private PassageInsertResult getOrCreatePassage(
        ImportContext context,
        SectionSpec sectionSpec,
        long sectionId,
        String stimulusHtml,
        String sourceJsonPath,
        Map<String, PassageInsertResult> passageCache
    ) {
        if (isBlank(stimulusHtml)) {
            return null;
        }
        ProcessedHtml processedStimulus = processHtml(
            stimulusHtml,
            context.paperCode,
            "passage_" + sectionSpec.code() + "_" + (passageCache.size() + 1),
            sourceJsonPath,
            "stimulus",
            "STIMULUS_IMAGE"
        );
        String contentHash = DigestUtil.sha256Hex(nullToEmpty(processedStimulus.html()));
        PassageInsertResult existing = passageCache.get(contentHash);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actPaperId", context.paperId);
        row.put("actSectionId", sectionId);
        row.put("passageCode", context.paperCode + "_" + sectionSpec.code().toUpperCase(Locale.ROOT) + "_P" + (passageCache.size() + 1));
        row.put("passageTitle", sectionSpec.name() + " stimulus " + (passageCache.size() + 1));
        row.put("passageType", passageType(sectionSpec.code()));
        row.put("contentHtml", processedStimulus.html());
        row.put("contentText", stripHtml(processedStimulus.html()));
        row.put("contentHash", contentHash);
        row.put("structureJson", null);
        row.put("sortOrder", passageCache.size() + 1);
        row.put("remark", null);
        actQuestionBankImportMapper.insertActPassage(row);
        long passageId = generatedId(row, "Failed to insert ACT passage");
        context.counts.passageCount++;
        context.counts.assetCount += insertAssets(
            processedStimulus.assets(),
            new AssetOwner(context.paperId, sectionId, passageId, null, null, null, "PASSAGE", passageId)
        );

        PassageInsertResult inserted = new PassageInsertResult(passageId, stringValue(row.get("contentHtml")), stringValue(row.get("contentText")));
        passageCache.put(contentHash, inserted);
        return inserted;
    }

    private long insertPaper(PaperMetadata metadata, ResolvedJsonFile jsonFile, Counts plannedCounts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("paperCode", metadata.paperCode());
        row.put("paperName", metadata.paperName());
        row.put("examBoard", SOURCE_TYPE_ACT);
        row.put("examType", SOURCE_TYPE_ACT);
        row.put("testDate", metadata.testDate());
        row.put("regionCode", metadata.regionCode());
        row.put("regionName", metadata.regionName());
        row.put("sessionCode", metadata.sessionCode());
        row.put("sessionName", metadata.sessionName());
        row.put("sourceName", jsonFile.sourceName());
        row.put("sourceFileHash", jsonFile.sha256());
        row.put("durationSeconds", null);
        row.put("questionCount", plannedCounts.questionCount);
        row.put("autoGradableQuestionCount", plannedCounts.autoGradableQuestionCount);
        row.put("totalScore", BigDecimal.valueOf(plannedCounts.autoGradableQuestionCount));
        row.put("metadataJson", metadataJson(metadata, jsonFile).toString());
        row.put("remark", null);
        actQuestionBankImportMapper.insertActPaper(row);
        return generatedId(row, "Failed to insert ACT paper");
    }

    private long insertSection(long paperId, SectionSpec sectionSpec, int questionCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actPaperId", paperId);
        row.put("sectionCode", sectionSpec.code());
        row.put("sectionName", sectionSpec.name());
        row.put("sectionNo", sectionSpec.order());
        row.put("durationSeconds", null);
        row.put("questionCount", questionCount);
        row.put("instructionsHtml", null);
        row.put("instructionsText", null);
        row.put("structureJson", null);
        row.put("sortOrder", sectionSpec.order());
        row.put("remark", null);
        actQuestionBankImportMapper.insertActSection(row);
        return generatedId(row, "Failed to insert ACT section");
    }

    private void insertQuestionAnswer(long questionId, String answerRaw, boolean autoGradable) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actQuestionId", questionId);
        row.put("answerRaw", answerRaw);
        row.put("answerJson", answerJson(answerRaw, autoGradable));
        row.put("explanationHtml", null);
        row.put("explanationText", null);
        row.put("gradingMode", autoGradable ? "AUTO" : "MANUAL");
        row.put("remark", null);
        actQuestionBankImportMapper.insertActQuestionAnswer(row);
    }

    private long insertSourceFile(
        long batchId,
        Long paperId,
        ResolvedJsonFile jsonFile,
        String parseStatus,
        JSONObject parseResult,
        String errorMessage
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actImportBatchId", batchId);
        row.put("actPaperId", paperId);
        row.put("sourceFileType", "QUESTION_JSON");
        row.put("sourceFileName", jsonFile.sourceName());
        row.put("sourceFileHash", jsonFile.sha256());
        row.put("storagePath", jsonFile.storagePath());
        row.put("assetUrl", null);
        row.put("parseStatus", parseStatus);
        row.put("parseResultJson", parseResult == null ? null : parseResult.toString());
        row.put("parseWarningJson", null);
        row.put("errorMessage", errorMessage);
        row.put("remark", jsonFile.relativePath());
        actQuestionBankImportMapper.insertActSourceFile(row);
        return generatedId(row, "Failed to insert ACT source file");
    }

    private void insertFailedSourceFile(long batchId, ResolvedJsonFile jsonFile, Exception exception) {
        try {
            insertSourceFile(batchId, null, jsonFile, "FAILED", null, exception.getMessage());
        } catch (Exception ignored) {
            // Keep the original import failure visible through the job record.
        }
    }

    private int insertAssets(List<PendingAsset> assets, AssetOwner owner) {
        if (assets == null || assets.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < assets.size(); index++) {
            PendingAsset asset = assets.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("actPaperId", owner.paperId());
            row.put("actSectionId", owner.sectionId());
            row.put("actPassageId", owner.passageId());
            row.put("actGroupId", owner.groupId());
            row.put("actGroupOptionId", owner.optionId());
            row.put("actQuestionId", owner.questionId());
            row.put("ownerType", owner.ownerType());
            row.put("ownerId", owner.ownerId());
            row.put("assetType", "IMAGE");
            row.put("assetRole", asset.assetRole());
            row.put("assetName", asset.assetName());
            row.put("sourcePath", "data-uri:" + asset.sourceDataUriHash());
            row.put("sourceJsonPath", asset.sourceJsonPath());
            row.put("sourceHtmlField", asset.sourceHtmlField());
            row.put("storagePath", asset.storagePath());
            row.put("assetUrl", asset.assetUrl());
            row.put("fileHash", asset.fileHash());
            row.put("sourceDataUriHash", asset.sourceDataUriHash());
            row.put("mimeType", asset.mimeType());
            row.put("fileSize", asset.fileSize());
            row.put("imageWidthPx", asset.imageWidthPx());
            row.put("imageHeightPx", asset.imageHeightPx());
            row.put("sortOrder", index + 1);
            row.put("remark", null);
            actQuestionBankImportMapper.insertActAsset(row);
            count++;
        }
        return count;
    }

    private int syncMockExamRefs(PaperMetadata metadata, long paperId, List<QuestionRefDraft> questionRefs, Counts counts) {
        MockExamPaperRef paperRef = mockExamPaperRefMapper.findActiveBySource(SOURCE_TYPE_ACT, paperId);
        LocalDateTime now = LocalDateTime.now();
        if (paperRef == null) {
            paperRef = new MockExamPaperRef();
            paperRef.setSourceType(SOURCE_TYPE_ACT);
            paperRef.setSourcePaperId(paperId);
            paperRef.setCreateTime(now);
        }
        paperRef.setExamCategory(SOURCE_TYPE_ACT);
        paperRef.setExamContent(SOURCE_TYPE_ACT);
        paperRef.setExamBoard(SOURCE_TYPE_ACT);
        paperRef.setSubjectCode(SOURCE_TYPE_ACT);
        paperRef.setSubjectName("ACT");
        paperRef.setPaperCode(metadata.paperCode());
        paperRef.setPaperName(metadata.paperName());
        paperRef.setDurationSeconds(null);
        paperRef.setTotalScore(BigDecimal.valueOf(counts.autoGradableQuestionCount));
        paperRef.setPayloadAdapter("ACT");
        paperRef.setStatus(1);
        paperRef.setRemark(null);
        paperRef.setUpdateTime(now);
        paperRef.setDeleteFlag("1");
        if (paperRef.getMockexamPaperRefId() == null) {
            mockExamPaperRefMapper.insert(paperRef);
        } else {
            mockExamPaperRefMapper.updateMetadata(paperRef);
        }

        int refCount = 0;
        for (QuestionRefDraft draft : questionRefs) {
            MockExamQuestionRef questionRef = mockExamQuestionRefMapper.findActiveBySource(SOURCE_TYPE_ACT, draft.questionId());
            LocalDateTime questionNow = LocalDateTime.now();
            if (questionRef == null) {
                questionRef = new MockExamQuestionRef();
                questionRef.setSourceType(SOURCE_TYPE_ACT);
                questionRef.setSourceQuestionId(draft.questionId());
                questionRef.setCreateTime(questionNow);
            }
            questionRef.setMockexamPaperRefId(paperRef.getMockexamPaperRefId());
            questionRef.setSourceQuestionCode(draft.questionCode());
            questionRef.setQuestionNoDisplay(draft.questionNoDisplay());
            questionRef.setQuestionType(draft.questionType());
            questionRef.setResponseMode(draft.responseMode());
            questionRef.setStatType(draft.statType());
            questionRef.setMaxScore(draft.score());
            questionRef.setPreviewText(limitText(draft.previewText(), 500));
            questionRef.setStatus(1);
            questionRef.setRemark(null);
            questionRef.setUpdateTime(questionNow);
            questionRef.setDeleteFlag("1");
            if (questionRef.getMockexamQuestionRefId() == null) {
                mockExamQuestionRefMapper.insert(questionRef);
            } else {
                mockExamQuestionRefMapper.updateMetadata(questionRef);
            }
            refCount++;
        }
        return refCount;
    }

    private ProcessedHtml processHtml(
        String html,
        String paperCode,
        String fieldPrefix,
        String sourceJsonPath,
        String sourceHtmlField,
        String assetRole
    ) {
        if (isBlank(html)) {
            return new ProcessedHtml(null, List.of());
        }
        Matcher matcher = IMG_SRC_PATTERN.matcher(html);
        StringBuffer buffer = new StringBuffer();
        List<PendingAsset> assets = new ArrayList<>();
        int index = 1;
        while (matcher.find()) {
            String src = matcher.group(2);
            if (!isDataUri(src)) {
                continue;
            }
            PendingAsset asset = saveDataUriAsset(src, paperCode, fieldPrefix, index, sourceJsonPath, sourceHtmlField, assetRole);
            assets.add(asset);
            String replacementTag = matcher.group(0).replace(src, asset.assetUrl());
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacementTag));
            index++;
        }
        matcher.appendTail(buffer);
        return new ProcessedHtml(buffer.toString(), assets);
    }

    private PendingAsset saveDataUriAsset(
        String dataUri,
        String paperCode,
        String fieldPrefix,
        int index,
        String sourceJsonPath,
        String sourceHtmlField,
        String assetRole
    ) {
        Matcher matcher = DATA_URI_PATTERN.matcher(dataUri);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported image data URI in ACT JSON");
        }
        String mimeType = matcher.group(1).toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(matcher.group(2).replaceAll("\\s+", ""));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid base64 image data in ACT JSON");
        }

        String dataUriHash = DigestUtil.sha256Hex(dataUri);
        String fileHash = DigestUtil.sha256Hex(bytes);
        String extension = imageExtension(mimeType);
        String storagePath = "act/" + safePathSegment(paperCode) + "/" + safePathSegment(fieldPrefix) + "_" + index + "_" + dataUriHash.substring(0, 16) + "." + extension;
        Path destination = storagePathSupport.ensureExamAssetRoot().resolve(storagePath).normalize();
        try {
            FileUtil.mkdir(destination.getParent().toFile());
            Files.write(destination, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save ACT base64 image: " + exception.getMessage(), exception);
        }

        ImageSize imageSize = readImageSize(bytes);
        return new PendingAsset(
            fieldPrefix + "_" + index + "." + extension,
            sourceJsonPath,
            sourceHtmlField,
            storagePath,
            "/exam-assets/" + storagePath.replace("\\", "/"),
            fileHash,
            dataUriHash,
            mimeType,
            (long) bytes.length,
            imageSize.width(),
            imageSize.height(),
            assetRole
        );
    }

    private ImageSize readImageSize(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new ImageSize(null, null);
            }
            return new ImageSize(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            return new ImageSize(null, null);
        }
    }

    private List<ResolvedJsonFile> resolveJsonFiles(ImportMetadata metadata, List<String> entryPaths) {
        List<ResolvedJsonFile> resolved = new ArrayList<>();
        for (int index = 0; index < metadata.files().size(); index++) {
            StoredFile storedFile = metadata.files().get(index);
            String relativePath = index < entryPaths.size() ? entryPaths.get(index) : storedFile.filename();
            Path storedPath = importJobRoot().resolve(metadata.storagePath()).resolve(storedFile.storedName());
            if ("zip".equals(metadata.sourceMode())) {
                resolved.addAll(resolveJsonFilesFromZip(storedPath, storedFile.filename(), relativePath));
                continue;
            }
            if (!isJsonName(relativePath) && !isJsonName(storedFile.filename())) {
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(storedPath);
                resolved.add(new ResolvedJsonFile(
                    storedFile.filename(),
                    relativePath,
                    metadata.storagePath() + "/" + storedFile.storedName(),
                    bytes,
                    DigestUtil.sha256Hex(bytes)
                ));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read uploaded ACT JSON file: " + storedFile.filename(), exception);
            }
        }
        return resolved;
    }

    private List<ResolvedJsonFile> resolveJsonFilesFromZip(Path zipPath, String uploadedName, String relativePath) {
        List<ResolvedJsonFile> resolved = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !isJsonName(entry.getName())) {
                    continue;
                }
                byte[] bytes = zipInputStream.readAllBytes();
                String sourceName = uploadedName + "/" + entry.getName();
                resolved.add(new ResolvedJsonFile(
                    sourceName,
                    relativePath + "/" + entry.getName(),
                    zipPath.getFileName() + "!" + entry.getName(),
                    bytes,
                    DigestUtil.sha256Hex(bytes)
                ));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read ACT zip file: " + uploadedName);
        }
        return resolved;
    }

    private PaperMetadata parsePaperMetadata(ResolvedJsonFile jsonFile) {
        String baseName = baseName(jsonFile.sourceName());
        Matcher dateMatcher = DATE_IN_NAME_PATTERN.matcher(baseName);
        String compactDate = dateMatcher.find() ? dateMatcher.group(1) : null;
        LocalDate testDate = null;
        if (compactDate != null) {
            testDate = LocalDate.parse(compactDate, DATE_FORMATTER);
        }
        String regionName = null;
        String regionCode = null;
        if (baseName.contains("亚太") || baseName.toLowerCase(Locale.ROOT).contains("apac")) {
            regionName = "亚太";
            regionCode = "APAC";
        }
        String sessionName = null;
        String sessionCode = null;
        if (baseName.contains("上午") || baseName.toLowerCase(Locale.ROOT).contains("am")) {
            sessionName = "上午";
            sessionCode = "AM";
        } else if (baseName.contains("下午") || baseName.toLowerCase(Locale.ROOT).contains("pm")) {
            sessionName = "下午";
            sessionCode = "PM";
        }

        String paperCode;
        if (compactDate != null) {
            paperCode = "ACT" + compactDate + "_" + nullToDefault(regionCode, "REGION") + "_" + nullToDefault(sessionCode, "SESSION");
        } else {
            paperCode = "ACT_" + jsonFile.sha256().substring(0, 12).toUpperCase(Locale.ROOT);
        }
        String paperName = "ACT";
        if (testDate != null) {
            paperName += " " + testDate;
        }
        if (regionName != null) {
            paperName += " " + regionName;
        }
        if (sessionName != null) {
            paperName += " " + sessionName;
        }
        if ("ACT".equals(paperName)) {
            paperName = baseName;
        }
        return new PaperMetadata(paperCode, paperName, testDate, regionCode, regionName, sessionCode, sessionName);
    }

    private Counts countQuestions(JSONObject root) {
        Counts counts = new Counts();
        for (SectionSpec sectionSpec : SECTION_SPECS) {
            JSONObject sectionObject = root.getJSONObject(sectionSpec.code());
            if (sectionObject == null) {
                continue;
            }
            JSONArray questions = sectionObject.getJSONArray("default");
            if (questions == null) {
                continue;
            }
            for (Object item : questions) {
                if (!(item instanceof JSONObject question)) {
                    continue;
                }
                counts.questionCount++;
                JSONArray options = parseAnswerOptions(question.getStr("answerOptions"));
                if (options != null && !options.isEmpty() && trimToNull(question.getStr("answer")) != null) {
                    counts.autoGradableQuestionCount++;
                }
            }
        }
        return counts;
    }

    private JSONArray parseAnswerOptions(String raw) {
        if (isBlank(raw)) {
            return new JSONArray();
        }
        try {
            return JSONUtil.parseArray(raw);
        } catch (Exception exception) {
            throw new IllegalArgumentException("answerOptions is not a valid JSON array");
        }
    }

    private JSONObject metadataJson(PaperMetadata metadata, ResolvedJsonFile jsonFile) {
        JSONObject object = new JSONObject();
        object.set("source_name", jsonFile.sourceName());
        object.set("relative_path", jsonFile.relativePath());
        object.set("metadata_source", "filename");
        object.set("region_code", metadata.regionCode());
        object.set("session_code", metadata.sessionCode());
        return object;
    }

    private JSONObject parsedParseResult(Counts counts) {
        JSONObject object = new JSONObject();
        object.set("question_count", counts.questionCount);
        object.set("auto_gradable_question_count", counts.autoGradableQuestionCount);
        return object;
    }

    private JSONObject skippedParseResult(Map<String, Object> existingPaper) {
        JSONObject object = new JSONObject();
        object.set("skipped_reason", "paper_code_exists");
        object.set("existing_act_paper_id", column(existingPaper, "act_paper_id"));
        return object;
    }

    private String normalizedRawQuestion(JSONObject question, List<String> optionKeys) {
        JSONObject raw = new JSONObject();
        raw.set("source_id", question.get("id"));
        raw.set("stem", question.get("stem"));
        raw.set("stimulus", question.get("stimulus"));
        raw.set("answer", question.get("answer"));
        raw.set("section", question.get("section"));
        JSONArray normalizedOptions = new JSONArray();
        JSONArray options = parseAnswerOptions(question.getStr("answerOptions"));
        for (int index = 0; index < options.size(); index++) {
            Object optionObject = options.get(index);
            if (!(optionObject instanceof JSONObject option)) {
                continue;
            }
            JSONObject normalizedOption = new JSONObject();
            normalizedOption.set("option_key", index < optionKeys.size() ? optionKeys.get(index) : optionKey(index));
            normalizedOption.set("content", option.get("content"));
            normalizedOptions.add(normalizedOption);
        }
        raw.set("answerOptions", normalizedOptions);
        return raw.toString();
    }

    private String answerInputSchema(String responseMode) {
        JSONObject object = new JSONObject();
        if ("RADIO".equals(responseMode)) {
            object.set("type", "radio");
            object.set("options_source", "group_options");
        } else {
            object.set("type", "textarea");
        }
        return object.toString();
    }

    private String answerJson(String answerRaw, boolean autoGradable) {
        JSONObject object = new JSONObject();
        if (autoGradable) {
            object.set("correct_options", parseCorrectOptions(answerRaw));
        } else if (answerRaw != null) {
            object.set("reference_answer", answerRaw);
        }
        return object.toString();
    }

    private List<String> parseCorrectOptions(String answerRaw) {
        if (answerRaw == null) {
            return List.of();
        }
        String normalized = answerRaw.trim().toUpperCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (ch >= 'A' && ch <= 'Z') {
                values.add(String.valueOf(ch));
            }
        }
        if (values.isEmpty()) {
            values.add(normalized);
        }
        return values;
    }

    private void updateJobProgress(long jobId, Progress progress) {
        Map<String, Object> row = importJobRow(jobId, progress.counts());
        row.put("resolvedFileCount", progress.resolvedJsonCount());
        row.put("jsonCount", progress.resolvedJsonCount());
        row.put("successCount", progress.counts().paperCount);
        row.put("failureCount", progress.failureCount());
        row.put("progressMessage", progress.message());
        row.put("resultJson", progressResultJson(progress).toString());
        actImportJobMapper.updateJobProgress(row);
    }

    private void updateJobCompleted(long jobId, ImportResult result) {
        Map<String, Object> row = importJobRow(jobId, result.counts());
        row.put("resolvedFileCount", result.resolvedJsonCount());
        row.put("jsonCount", result.resolvedJsonCount());
        row.put("successCount", result.counts().paperCount);
        row.put("failureCount", result.failureCount());
        row.put(
            "progressMessage",
            "ACT import completed: imported " + result.counts().paperCount + ", skipped " + result.skippedCount() + ", failed " + result.failureCount()
        );
        row.put("resultJson", importResultJson(result).toString());
        actImportJobMapper.updateJobCompleted(row);
    }

    private void markJobFailed(long jobId, Exception exception) {
        actImportJobMapper.markJobFailed(jobId, exception.getMessage(), "ACT import failed: " + exception.getMessage());
    }

    private Map<String, Object> importJobRow(long jobId, Counts counts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", jobId);
        row.put("importedPaperCount", counts.paperCount);
        row.put("importedSectionCount", counts.sectionCount);
        row.put("importedGroupCount", counts.groupCount);
        row.put("importedQuestionCount", counts.questionCount);
        row.put("importedAnswerCount", counts.answerCount);
        row.put("importedOptionCount", counts.optionCount);
        row.put("importedAssetCount", counts.assetCount);
        return row;
    }

    private JSONObject progressResultJson(Progress progress) {
        JSONObject object = new JSONObject();
        object.set("items", progress.items());
        object.set("failures", progress.failures());
        object.set("skipped_count", progress.skippedCount());
        object.set("passage_count", progress.counts().passageCount);
        object.set("auto_gradable_question_count", progress.counts().autoGradableQuestionCount);
        object.set("question_ref_count", progress.counts().questionRefCount);
        return object;
    }

    private JSONObject importResultJson(ImportResult result) {
        JSONObject object = new JSONObject();
        object.set("items", result.items());
        object.set("failures", result.failures());
        object.set("skipped_count", result.skippedCount());
        object.set("passage_count", result.counts().passageCount);
        object.set("auto_gradable_question_count", result.counts().autoGradableQuestionCount);
        object.set("question_ref_count", result.counts().questionRefCount);
        return object;
    }

    private long insertImportBatch(long jobId, String jobStoragePath, ImportMetadata metadata) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchCode", "ACT_IMPORT_" + jobId + "_" + System.currentTimeMillis());
        row.put("batchName", metadata.batchName());
        row.put("importSourceType", metadata.sourceMode().toUpperCase(Locale.ROOT));
        row.put("originName", metadata.files().isEmpty() ? null : metadata.files().get(0).filename());
        row.put("sourceRootPath", metadata.sourceMode());
        row.put("storageRootPath", jobStoragePath);
        row.put("totalFileCount", metadata.files().size());
        row.put("resolvedJsonCount", 0);
        row.put("successCount", 0);
        row.put("failureCount", 0);
        row.put("importStatus", "RUNNING");
        row.put("progressPercent", BigDecimal.ZERO);
        row.put("progressMessage", "ACT import batch created");
        row.put("operatorId", null);
        row.put("resultJson", null);
        row.put("errorMessage", null);
        row.put("startedAt", Timestamp.valueOf(LocalDateTime.now()));
        row.put("finishedAt", null);
        row.put("remark", null);
        actQuestionBankImportMapper.insertImportBatch(row);
        return generatedId(row, "Failed to create ACT import batch");
    }

    private void updateImportBatchProgress(long batchId, Progress progress) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchId", batchId);
        row.put("resolvedJsonCount", progress.resolvedJsonCount());
        row.put("successCount", progress.counts().paperCount);
        row.put("failureCount", progress.failureCount());
        row.put("progressPercent", progressPercent(progress.items().size() + progress.failures().size(), progress.resolvedJsonCount()));
        row.put("progressMessage", progress.message());
        row.put("resultJson", progressResultJson(progress).toString());
        actQuestionBankImportMapper.updateImportBatchProgress(row);
    }

    private void updateImportBatchCompleted(long batchId, ImportResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchId", batchId);
        row.put("resolvedJsonCount", result.resolvedJsonCount());
        row.put("successCount", result.counts().paperCount);
        row.put("failureCount", result.failureCount());
        row.put("importStatus", result.failureCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS");
        row.put(
            "progressMessage",
            "ACT import completed: imported " + result.counts().paperCount + ", skipped " + result.skippedCount() + ", failed " + result.failureCount()
        );
        row.put("resultJson", importResultJson(result).toString());
        actQuestionBankImportMapper.updateImportBatchCompleted(row);
    }

    private void updateImportBatchFailed(long batchId, Exception exception) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batchId", batchId);
        row.put("progressMessage", "ACT import failed: " + exception.getMessage());
        row.put("errorMessage", exception.getMessage());
        actQuestionBankImportMapper.updateImportBatchFailed(row);
    }

    private BigDecimal progressPercent(int processed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.min(99, Math.round(processed * 100.0 / total)));
    }

    private void emitProgress(
        ProgressSink sink,
        int resolvedJsonCount,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures,
        Counts counts,
        int skippedCount,
        String message
    ) {
        sink.accept(new Progress(resolvedJsonCount, counts.copy(), skippedCount, failures.size(), message, new ArrayList<>(items), new ArrayList<>(failures)));
    }

    private String progressMessage(int processed, int total, Counts counts, int skippedCount, List<Map<String, Object>> failures) {
        return "ACT import progress " + processed + "/" + total
            + ": imported " + counts.paperCount
            + ", skipped " + skippedCount
            + ", failed " + failures.size();
    }

    private Map<String, Object> failure(String sourceName, Exception exception) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source_name", sourceName);
        item.put("message", exception.getMessage());
        return item;
    }

    private ImportMetadata loadMetadata(String storagePath) {
        if (isBlank(storagePath)) {
            throw new IllegalArgumentException("ACT import job is missing storage_path");
        }
        Path jobDir = importJobRoot().resolve(storagePath);
        Path metadataFile = jobDir.resolve("metadata.json");
        if (!Files.exists(metadataFile)) {
            throw new IllegalArgumentException("ACT import job is missing metadata.json");
        }
        JSONObject metadata = JSONUtil.readJSONObject(metadataFile.toFile(), StandardCharsets.UTF_8);
        JSONArray filesArray = metadata.getJSONArray("files");
        List<StoredFile> files = new ArrayList<>();
        if (filesArray != null) {
            for (Object item : filesArray) {
                if (item instanceof JSONObject file) {
                    files.add(new StoredFile(file.getStr("filename"), file.getStr("stored_name")));
                }
            }
        }
        return new ImportMetadata(
            storagePath,
            normalizeSourceMode(metadata.getStr("source_mode")),
            trimToNull(metadata.getStr("batch_name")),
            metadata.getStr("entry_paths_json"),
            files
        );
    }

    private List<String> parseEntryPaths(String entryPathsJson, int expectedCount) {
        if (isBlank(entryPathsJson)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(entryPathsJson);
            List<String> values = new ArrayList<>();
            for (Object item : array) {
                values.add(stringValue(item));
            }
            return values;
        } catch (Exception exception) {
            if (expectedCount <= 0) {
                return List.of();
            }
            return List.of();
        }
    }

    private String normalizeSourceMode(String sourceMode) {
        String normalized = trimToNull(sourceMode);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "source_mode is required");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!SOURCE_MODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid source_mode; only zip, directory or files are supported");
        }
        return normalized;
    }

    private void validateUploadedFiles(String sourceMode, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please upload at least one file");
        }
        for (MultipartFile file : files) {
            String filename = stringValue(file.getOriginalFilename());
            if ("zip".equals(sourceMode) && !isZipName(filename)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ACT zip import only accepts .zip files: " + filename);
            }
            if ("files".equals(sourceMode) && !isJsonName(filename)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ACT file import only accepts .json files: " + filename);
            }
        }
    }

    private Map<String, Object> requireActImportJobRow(long jobId) {
        Map<String, Object> row = actImportJobMapper.findActiveImportJobById(jobId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACT import job not found");
        }
        String jobName = stringValue(column(row, "job_name"));
        if (!jobName.startsWith(JOB_NAME_PREFIX)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACT import job not found");
        }
        return row;
    }

    private Map<String, Object> serializeImportJob(Map<String, Object> row) {
        JSONObject resultPayload = parseObject(column(row, "result_json"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("job_id", longValue(column(row, "exam_import_job_id")));
        result.put("job_name", column(row, "job_name"));
        result.put("batch_name", column(row, "bank_name"));
        result.put("source_mode", column(row, "source_mode"));
        result.put("status", column(row, "status"));
        result.put("uploaded_file_count", intValue(column(row, "uploaded_file_count")));
        result.put("resolved_json_count", intValue(column(row, "resolved_file_count")));
        result.put("success_count", intValue(column(row, "success_count")));
        result.put("skipped_count", intValue(resultPayload.get("skipped_count")));
        result.put("failure_count", intValue(column(row, "failure_count")));
        result.put("imported_paper_count", intValue(column(row, "imported_paper_count")));
        result.put("imported_section_count", intValue(column(row, "imported_section_count")));
        result.put("imported_passage_count", intValue(resultPayload.get("passage_count")));
        result.put("imported_group_count", intValue(column(row, "imported_group_count")));
        result.put("imported_question_count", intValue(column(row, "imported_question_count")));
        result.put("auto_gradable_question_count", intValue(resultPayload.get("auto_gradable_question_count")));
        result.put("imported_answer_count", intValue(column(row, "imported_answer_count")));
        result.put("imported_option_count", intValue(column(row, "imported_option_count")));
        result.put("imported_asset_count", intValue(column(row, "imported_asset_count")));
        result.put("question_ref_count", intValue(resultPayload.get("question_ref_count")));
        result.put("progress_message", column(row, "progress_message"));
        result.put("error_message", column(row, "error_message"));
        result.put("start_time", timestamp(column(row, "start_time")));
        result.put("finish_time", timestamp(column(row, "finish_time")));
        result.put("create_time", timestamp(column(row, "create_time")));
        result.put("update_time", timestamp(column(row, "update_time")));
        result.put("items", resultPayload.getJSONArray("items") == null ? List.of() : resultPayload.getJSONArray("items"));
        result.put("failures", resultPayload.getJSONArray("failures") == null ? List.of() : resultPayload.getJSONArray("failures"));
        return result;
    }

    private JSONObject parseObject(Object raw) {
        if (raw == null) {
            return new JSONObject();
        }
        try {
            if (raw instanceof JSONObject object) {
                return object;
            }
            if (raw instanceof Map<?, ?> map) {
                return new JSONObject(map);
            }
            String text = raw instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : stringValue(raw);
            if (isBlank(text)) {
                return new JSONObject();
            }
            return JSONUtil.parseObj(text);
        } catch (Exception exception) {
            return new JSONObject();
        }
    }

    private Path importJobRoot() {
        return storagePathSupport.ensureImportJobRoot();
    }

    private Object column(Map<String, Object> row, String columnName) {
        if (row == null || columnName == null) {
            return null;
        }
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        String camel = toCamelCase(columnName);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        return null;
    }

    private String toCamelCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private long generatedId(Map<String, Object> row, String message) {
        Object id = row.get("id");
        if (id instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(message);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception exception) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception exception) {
            return 0L;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimToNull(String value) {
        String trimmed = CharSequenceUtil.trim(value);
        return CharSequenceUtil.isBlank(trimmed) ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return CharSequenceUtil.isBlank(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String stripHtml(String html) {
        if (isBlank(html)) {
            return null;
        }
        String text = HTML_COMMENT_PATTERN.matcher(html).replaceAll(" ");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"");
        return trimToNull(text.replaceAll("\\s+", " "));
    }

    private boolean isDataUri(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("data:");
    }

    private boolean isJsonName(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private boolean isZipName(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private String baseName(String sourceName) {
        String normalized = sourceName == null ? "" : sourceName.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String safePathSegment(String value) {
        String normalized = value == null ? "asset" : value.trim();
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]+", "_");
        return normalized.isBlank() ? "asset" : normalized;
    }

    private String imageExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "png";
        };
    }

    private String optionKey(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private String passageType(String sectionCode) {
        if ("science".equals(sectionCode)) {
            return "DATA_SET";
        }
        if ("writing".equals(sectionCode)) {
            return "PROMPT";
        }
        return "PASSAGE";
    }

    private String timestamp(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private record SectionSpec(String code, String name, int order) {
    }

    private record StoredFile(String filename, String storedName) {
    }

    private record ImportMetadata(
        String storagePath,
        String sourceMode,
        String batchName,
        String entryPathsJson,
        List<StoredFile> files
    ) {
    }

    private record ResolvedJsonFile(
        String sourceName,
        String relativePath,
        String storagePath,
        byte[] bytes,
        String sha256
    ) {
    }

    private record PaperMetadata(
        String paperCode,
        String paperName,
        LocalDate testDate,
        String regionCode,
        String regionName,
        String sessionCode,
        String sessionName
    ) {
    }

    private record ProcessedHtml(String html, List<PendingAsset> assets) {
    }

    private record PendingAsset(
        String assetName,
        String sourceJsonPath,
        String sourceHtmlField,
        String storagePath,
        String assetUrl,
        String fileHash,
        String sourceDataUriHash,
        String mimeType,
        Long fileSize,
        Integer imageWidthPx,
        Integer imageHeightPx,
        String assetRole
    ) {
    }

    private record AssetOwner(
        long paperId,
        long sectionId,
        Long passageId,
        Long groupId,
        Long optionId,
        Long questionId,
        String ownerType,
        long ownerId
    ) {
    }

    private record ImageSize(Integer width, Integer height) {
    }

    private record PassageInsertResult(long passageId, String contentHtml, String contentText) {
    }

    private record QuestionRefDraft(
        long questionId,
        String questionCode,
        String questionNoDisplay,
        String questionType,
        String responseMode,
        String statType,
        BigDecimal score,
        String previewText
    ) {
    }

    private record QuestionInsertResult(QuestionRefDraft questionRef) {
    }

    private record FileImportResult(boolean skipped, Counts counts, Map<String, Object> payload) {
    }

    private record Progress(
        int resolvedJsonCount,
        Counts counts,
        int skippedCount,
        int failureCount,
        String message,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures
    ) {
    }

    private record ImportResult(
        int resolvedJsonCount,
        Counts counts,
        int skippedCount,
        int failureCount,
        List<Map<String, Object>> items,
        List<Map<String, Object>> failures
    ) {
    }

    @FunctionalInterface
    private interface ProgressSink {
        void accept(Progress progress);
    }

    private static class ImportContext {
        private final long paperId;
        private final String paperCode;
        private final Counts counts = new Counts();
        private int nextSortOrder = 1;

        private ImportContext(long paperId, String paperCode) {
            this.paperId = paperId;
            this.paperCode = paperCode;
            this.counts.paperCount = 1;
        }
    }

    private static class Counts {
        private int paperCount;
        private int sectionCount;
        private int passageCount;
        private int groupCount;
        private int questionCount;
        private int autoGradableQuestionCount;
        private int answerCount;
        private int optionCount;
        private int assetCount;
        private int questionRefCount;

        private void add(Counts other) {
            this.paperCount += other.paperCount;
            this.sectionCount += other.sectionCount;
            this.passageCount += other.passageCount;
            this.groupCount += other.groupCount;
            this.questionCount += other.questionCount;
            this.autoGradableQuestionCount += other.autoGradableQuestionCount;
            this.answerCount += other.answerCount;
            this.optionCount += other.optionCount;
            this.assetCount += other.assetCount;
            this.questionRefCount += other.questionRefCount;
        }

        private Counts copy() {
            Counts copy = new Counts();
            copy.add(this);
            return copy;
        }
    }
}
