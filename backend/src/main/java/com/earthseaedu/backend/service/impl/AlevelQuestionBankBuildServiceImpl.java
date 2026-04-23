package com.earthseaedu.backend.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AlevelAssetMapper;
import com.earthseaedu.backend.mapper.AlevelModuleMapper;
import com.earthseaedu.backend.mapper.AlevelPaperMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionAnswerMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionMapper;
import com.earthseaedu.backend.mapper.AlevelQuestionOptionMapper;
import com.earthseaedu.backend.mapper.AlevelSourceFileMapper;
import com.earthseaedu.backend.mapper.MockExamPaperRefMapper;
import com.earthseaedu.backend.mapper.MockExamQuestionRefMapper;
import com.earthseaedu.backend.model.alevel.AlevelAsset;
import com.earthseaedu.backend.model.alevel.AlevelModule;
import com.earthseaedu.backend.model.alevel.AlevelPaper;
import com.earthseaedu.backend.model.alevel.AlevelQuestion;
import com.earthseaedu.backend.model.alevel.AlevelQuestionAnswer;
import com.earthseaedu.backend.model.alevel.AlevelQuestionOption;
import com.earthseaedu.backend.model.alevel.AlevelSourceFile;
import com.earthseaedu.backend.model.mockexam.MockExamPaperRef;
import com.earthseaedu.backend.model.mockexam.MockExamQuestionRef;
import com.earthseaedu.backend.service.AlevelQuestionBankBuildService;
import com.earthseaedu.backend.support.AlevelPdfMetaSupport;
import com.earthseaedu.backend.support.AlevelPdfTextNormalizationSupport;
import com.earthseaedu.backend.support.StoragePathSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * A-Level 题库构建服务实现，负责把 source file 解析为试卷、题目、答案和 mockexam 引用。
 */
@Service
public class AlevelQuestionBankBuildServiceImpl implements AlevelQuestionBankBuildService {

    private static final String SOURCE_TYPE_ALEVEL = "A_LEVEL";
    private static final String EXAM_CATEGORY_ALEVEL = "ALEVEL";
    private static final String QUALIFICATION = "International A-Level";
    private static final String EXAM_BOARD = "OxfordAQA";
    private static final Pattern FILE_NAME_META_PATTERN = Pattern.compile(
        "^oxfordaqa-international-a-level-(.+)-([A-Z]{2}\\d{2})-([a-z-]+)-(\\d{4})-(question-paper|mark-scheme|insert|exam-report)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUESTION_START_PATTERN = Pattern.compile(
        "^(\\d{1,2}(?:\\.\\d+)?(?:\\([A-Za-zivxIVX]+\\))*)(?:\\s+(.*))?$"
    );
    private static final Pattern OPTION_START_PATTERN = Pattern.compile("^([A-D])(?:[\\).]|\\s{2,}|\\s)+(.*)$");
    private static final Pattern TRAILING_MARK_PATTERN = Pattern.compile("^(.*?)(?:\\s+(\\d+(?:\\.\\d+)?))$");
    private static final Pattern BRACKET_MARK_PATTERN = Pattern.compile("\\[(\\d+(?:\\.\\d+)?)\\s*marks?\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANSWER_OPTION_PATTERN = Pattern.compile("(?i)\\banswer\\s*:\\s*([A-D])\\b");
    private static final Pattern LEADING_SPLIT_NUMBER_PATTERN = Pattern.compile("^(\\d{1,2})\\s+(\\d{1,2})(?:\\s+(.*))?$");
    private static final Pattern LEADING_SUBQUESTION_PATTERN = Pattern.compile("^(\\d{1,2})\\s+(\\d)(?:\\s+(.*))?$");
    private static final Pattern LEADING_SPLIT_SUBQUESTION_PATTERN = Pattern.compile(
        "^(\\d)\\s+(\\d)\\s*\\.\\s*(\\d+)(?:\\s+(.*))?$"
    );
    private static final Pattern LEADING_DOT_NUMBER_PATTERN = Pattern.compile("^\\.+\\s*(\\d{1,2}(?:\\.\\d+)?(?:\\([A-Za-zivxIVX]+\\))*)(?:\\s+(.*))?$");
    private static final Pattern NUMERIC_SEQUENCE_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)?(?:\\s+\\d+(?:\\.\\d+)?)+$");
    private static final Pattern AO_MARK_PATTERN = Pattern.compile("(?i)^AO\\d(?:\\s*=\\s*\\d+(?:\\.\\d+)?)?$");
    private static final Pattern UNIT_NAME_PATTERN = Pattern.compile("(?i)^(Unit\\s+[^\\n]+)$");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(\\d+)\\s*hour(?:s)?(?:\\s+(\\d+)\\s*minutes?)?");
    private static final Pattern TOTAL_MARK_PATTERN = Pattern.compile(
        "(?i)(?:the\\s+)?(?:maximum\\s+mark(?:s)?(?:\\s+for\\s+this\\s+paper)?|total\\s+for\\s+this\\s+paper|out\\s+of)\\D{0,20}(\\d+(?:\\.\\d+)?)"
    );
    private static final Pattern INLINE_PAGE_CODE_PATTERN = Pattern.compile("(?i)\\bIB/[A-Z]/[A-Za-z]{3}\\d{2}/[A-Z]{2}\\d{2}\\b");
    private static final Pattern INSTRUCTION_START_PATTERN = Pattern.compile("(?i)^(section\\s+[a-z]|answer all questions|only one answer per question)");
    private static final Pattern INSTRUCTION_DROP_PATTERN = Pattern.compile(
        "(?i)^(please write clearly.*|centre number.*|candidate number.*|surname|forename\\(s\\)|candidate signature|i declare this is my own work\\.?|international as|economics|biology|chemistry|further mathematics|business|thursday .*|materials|for examiner.?s use|question mark|instructions|information|total|\\d+(?:[-–]\\d+)?)$"
    );
    private static final Pattern HEADER_DROP_PATTERN = Pattern.compile(
        "(?i)^(oxfordaqa|do not write outside the box|turn over|copyright|for examiner.?s use|answer all questions in the spaces provided on the question paper)$"
    );
    private static final Pattern PAGE_NOISE_PATTERN = Pattern.compile(
        "(?i)^(\\*[^*]+\\*|ib/g/.+|do not write|outside the|box|turn over(?: for the next question)?|extra space|end of questions|there are no questions printed on this page|do not write on this page|answer in the spaces provided|question additional page, if required\\.?|number write the question numbers in the left-hand margin\\.?|copyright information|all rights reserved\\.?|assessment objectives grid|ao1 ao2 ao3 ao4 total|total|question part marking guidance|marks)$"
    );
    private static final Pattern MARK_SCHEME_NOISE_PATTERN = Pattern.compile(
        "(?i)^(mark scheme|international as .+|total|question part marking guidance|marks|assessment objectives grid|ao1 ao2 ao3 ao4 total|section [a-z])$"
    );
    private static final Map<String, SubjectMeta> SUBJECT_META_MAP = Map.of(
        "BL", new SubjectMeta("9610", "Biology"),
        "BU", new SubjectMeta("9625", "Business"),
        "CH", new SubjectMeta("9620", "Chemistry"),
        "EC", new SubjectMeta("9640", "Economics"),
        "FM", new SubjectMeta("9665", "Further Mathematics")
    );

    private final AlevelSourceFileMapper alevelSourceFileMapper;
    private final AlevelPaperMapper alevelPaperMapper;
    private final AlevelModuleMapper alevelModuleMapper;
    private final AlevelQuestionMapper alevelQuestionMapper;
    private final AlevelQuestionOptionMapper alevelQuestionOptionMapper;
    private final AlevelQuestionAnswerMapper alevelQuestionAnswerMapper;
    private final AlevelAssetMapper alevelAssetMapper;
    private final MockExamPaperRefMapper mockExamPaperRefMapper;
    private final MockExamQuestionRefMapper mockExamQuestionRefMapper;
    private final AlevelPdfMetaSupport alevelPdfMetaSupport;
    private final StoragePathSupport storagePathSupport;

    public AlevelQuestionBankBuildServiceImpl(
        AlevelSourceFileMapper alevelSourceFileMapper,
        AlevelPaperMapper alevelPaperMapper,
        AlevelModuleMapper alevelModuleMapper,
        AlevelQuestionMapper alevelQuestionMapper,
        AlevelQuestionOptionMapper alevelQuestionOptionMapper,
        AlevelQuestionAnswerMapper alevelQuestionAnswerMapper,
        AlevelAssetMapper alevelAssetMapper,
        MockExamPaperRefMapper mockExamPaperRefMapper,
        MockExamQuestionRefMapper mockExamQuestionRefMapper,
        AlevelPdfMetaSupport alevelPdfMetaSupport,
        StoragePathSupport storagePathSupport
    ) {
        this.alevelSourceFileMapper = alevelSourceFileMapper;
        this.alevelPaperMapper = alevelPaperMapper;
        this.alevelModuleMapper = alevelModuleMapper;
        this.alevelQuestionMapper = alevelQuestionMapper;
        this.alevelQuestionOptionMapper = alevelQuestionOptionMapper;
        this.alevelQuestionAnswerMapper = alevelQuestionAnswerMapper;
        this.alevelAssetMapper = alevelAssetMapper;
        this.mockExamPaperRefMapper = mockExamPaperRefMapper;
        this.mockExamQuestionRefMapper = mockExamQuestionRefMapper;
        this.alevelPdfMetaSupport = alevelPdfMetaSupport;
        this.storagePathSupport = storagePathSupport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BuildResult buildBundle(String bundleCode) {
        List<AlevelSourceFile> sourceFiles = latestSourceFiles(alevelSourceFileMapper.findActiveByBundleCode(bundleCode));
        if (sourceFiles.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "A-Level source bundle not found: " + bundleCode);
        }

        SourceBundle bundle = SourceBundle.from(sourceFiles);
        if (bundle.questionPaper() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bundle missing question-paper: " + bundleCode);
        }

        Path questionPaperPath = loadStoredPdf(bundle.questionPaper());
        FileNameMeta fileNameMeta = resolvePaperMeta(bundle.questionPaper(), questionPaperPath);
        AlevelPaper existingPaper = alevelPaperMapper.findActiveByPaperCode(fileNameMeta.paperCode());
        boolean rebuilding = existingPaper != null;

        PdfDocumentText questionPaperText = extractPdfText(questionPaperPath);
        PdfDocumentText markSchemeText = bundle.markScheme() == null
            ? null
            : extractPdfText(loadStoredPdf(bundle.markScheme()));

        ParsedPaper parsedPaper = parseQuestionPaper(bundle.questionPaper(), fileNameMeta, questionPaperText);
        List<String> orderedQuestionKeys = parsedPaper.questions().stream()
            .map(ParsedQuestion::displayKey)
            .distinct()
            .toList();
        Map<String, ParsedMarkScheme> markSchemeMap = markSchemeText == null
            ? Map.of()
            : parseMarkScheme(markSchemeText, orderedQuestionKeys);

        AlevelPaper paper = rebuilding
            ? refreshPaper(existingPaper, bundle.questionPaper(), fileNameMeta, parsedPaper)
            : insertPaper(bundle.questionPaper(), fileNameMeta, parsedPaper);
        MockExamPaperRef paperRef = ensurePaperRef(paper);

        if (rebuilding) {
            deactivateExistingPaperChildren(paper);
        }

        AlevelModule module = insertDefaultModule(paper, parsedPaper);
        int insertAssetCount = insertBundleAssets(bundle, paper);

        ParsedQuestionSet questionSet = enrichQuestionSet(parsedPaper.questions(), markSchemeMap);
        InsertCounts insertCounts = insertQuestionsAndAnswers(paper, module, questionSet);
        int questionRefCount = syncQuestionRefs(paper, paperRef);

        syncSourceFiles(
            sourceFiles,
            paper.getAlevelPaperId(),
            "PARSED",
            bundleCode,
            paper,
            insertCounts.questionCount(),
            insertCounts.answerCount(),
            questionRefCount,
            null
        );

        return new BuildResult(
            bundleCode,
            false,
            paper.getAlevelPaperId(),
            paper.getPaperCode(),
            insertCounts.questionCount(),
            insertCounts.answerCount(),
            insertAssetCount,
            questionRefCount,
            buildResultPayload(
                paper.getAlevelPaperId(),
                paper.getPaperCode(),
                insertCounts.questionCount(),
                insertCounts.answerCount(),
                insertAssetCount,
                questionRefCount,
                rebuilding,
                false
            )
        );
    }

    private List<AlevelSourceFile> latestSourceFiles(List<AlevelSourceFile> sourceFiles) {
        Map<String, AlevelSourceFile> latestByName = new LinkedHashMap<>();
        for (AlevelSourceFile sourceFile : sourceFiles) {
            String key = trimToEmpty(sourceFile.getSourceFileName()).toLowerCase(Locale.ROOT);
            AlevelSourceFile existing = latestByName.get(key);
            if (existing == null || sourceFile.getAlevelSourceFileId() > existing.getAlevelSourceFileId()) {
                latestByName.put(key, sourceFile);
            }
        }
        return new ArrayList<>(latestByName.values());
    }

    private AlevelPaper insertPaper(AlevelSourceFile questionPaper, FileNameMeta fileNameMeta, ParsedPaper parsedPaper) {
        AlevelPaper row = new AlevelPaper();
        row.setPaperCode(fileNameMeta.paperCode());
        row.setPaperName(buildPaperName(fileNameMeta));
        row.setExamBoard(EXAM_BOARD);
        row.setQualification(fileNameMeta.qualification());
        row.setSubjectCode(fileNameMeta.subjectCode());
        row.setSubjectName(fileNameMeta.subjectName());
        row.setUnitCode(fileNameMeta.unitCode());
        row.setUnitName(trimToNull(parsedPaper.unitName()));
        row.setExamSession(fileNameMeta.examSession());
        row.setSourceName(questionPaper.getSourceFileName());
        row.setDurationSeconds(parsedPaper.durationSeconds());
        row.setTotalScore(parsedPaper.totalScore());
        row.setStatus(1);
        row.setRemark(null);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleteFlag("1");
        alevelPaperMapper.insert(row);
        return row;
    }

    private AlevelPaper refreshPaper(
        AlevelPaper existingPaper,
        AlevelSourceFile questionPaper,
        FileNameMeta fileNameMeta,
        ParsedPaper parsedPaper
    ) {
        existingPaper.setPaperName(buildPaperName(fileNameMeta));
        existingPaper.setExamBoard(EXAM_BOARD);
        existingPaper.setQualification(fileNameMeta.qualification());
        existingPaper.setSubjectCode(fileNameMeta.subjectCode());
        existingPaper.setSubjectName(fileNameMeta.subjectName());
        existingPaper.setUnitCode(fileNameMeta.unitCode());
        existingPaper.setUnitName(trimToNull(parsedPaper.unitName()));
        existingPaper.setExamSession(fileNameMeta.examSession());
        existingPaper.setSourceName(questionPaper.getSourceFileName());
        existingPaper.setDurationSeconds(parsedPaper.durationSeconds());
        existingPaper.setTotalScore(parsedPaper.totalScore());
        existingPaper.setStatus(1);
        existingPaper.setRemark(null);
        existingPaper.setUpdateTime(LocalDateTime.now());
        existingPaper.setDeleteFlag("1");
        alevelPaperMapper.updateMetadata(existingPaper);
        return existingPaper;
    }

    private AlevelModule insertDefaultModule(AlevelPaper paper, ParsedPaper parsedPaper) {
        AlevelModule row = new AlevelModule();
        row.setAlevelPaperId(paper.getAlevelPaperId());
        row.setModuleCode("MAIN");
        row.setModuleName(firstNonBlank(trimToNull(paper.getUnitName()), trimToNull(paper.getPaperName()), "Main"));
        row.setModuleType("PAPER");
        row.setInstructionsHtml(parsedPaper.instructionsHtml());
        row.setInstructionsText(parsedPaper.instructionsText());
        row.setSortOrder(1);
        row.setStatus(1);
        row.setRemark(null);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleteFlag("1");
        alevelModuleMapper.insert(row);
        return row;
    }

    private void deactivateExistingPaperChildren(AlevelPaper paper) {
        LocalDateTime now = LocalDateTime.now();
        alevelQuestionAnswerMapper.deactivateByPaperId(paper.getAlevelPaperId(), now);
        alevelQuestionOptionMapper.deactivateByPaperId(paper.getAlevelPaperId(), now);
        alevelQuestionMapper.deactivateByPaperId(paper.getAlevelPaperId(), now);
        alevelModuleMapper.deactivateByPaperId(paper.getAlevelPaperId(), now);
        alevelAssetMapper.deactivateByOwner("PAPER", paper.getAlevelPaperId(), now);
    }

    private int insertBundleAssets(SourceBundle bundle, AlevelPaper paper) {
        int count = 0;
        for (AlevelSourceFile insertFile : bundle.insertFiles()) {
            AlevelAsset asset = new AlevelAsset();
            asset.setOwnerType("PAPER");
            asset.setOwnerId(paper.getAlevelPaperId());
            asset.setAssetType("PDF");
            asset.setAssetRole("DOWNLOAD_ATTACHMENT");
            asset.setAssetName(insertFile.getSourceFileName());
            asset.setSourcePath(insertFile.getSourceFileName());
            asset.setStoragePath(insertFile.getStoragePath());
            asset.setAssetUrl(insertFile.getAssetUrl());
            asset.setFileHash(insertFile.getSourceFileHash());
            asset.setMimeType("application/pdf");
            asset.setSourcePageNo(null);
            asset.setSourceBboxJson(null);
            asset.setSortOrder(count + 1);
            asset.setStatus(1);
            asset.setRemark("source_file_id=" + insertFile.getAlevelSourceFileId());
            asset.setCreateTime(LocalDateTime.now());
            asset.setUpdateTime(LocalDateTime.now());
            asset.setDeleteFlag("1");
            alevelAssetMapper.insert(asset);
            count++;
        }
        return count;
    }

    private ParsedQuestionSet enrichQuestionSet(List<ParsedQuestion> parsedQuestions, Map<String, ParsedMarkScheme> markSchemeMap) {
        Map<String, ParsedQuestion> byKey = new LinkedHashMap<>();
        for (ParsedQuestion question : parsedQuestions) {
            ParsedMarkScheme scheme = markSchemeMap.getOrDefault(question.displayKey(), ParsedMarkScheme.empty(question.displayKey()));
            ParsedQuestion enriched = question.withMarkScheme(scheme);
            byKey.put(enriched.displayKey(), enriched);
        }
        return new ParsedQuestionSet(new ArrayList<>(byKey.values()));
    }

    private InsertCounts insertQuestionsAndAnswers(AlevelPaper paper, AlevelModule module, ParsedQuestionSet questionSet) {
        Map<String, Long> parentIdMap = new HashMap<>();
        Map<String, Boolean> hasChildren = new HashMap<>();
        for (ParsedQuestion question : questionSet.questions()) {
            if (question.parentKey() != null) {
                hasChildren.put(question.parentKey(), true);
            }
        }

        int questionCount = 0;
        int answerCount = 0;
        List<ParsedQuestion> orderedQuestions = new ArrayList<>(questionSet.questions());
        orderedQuestions.sort(Comparator.comparingInt(ParsedQuestion::sortOrder));

        for (ParsedQuestion parsedQuestion : orderedQuestions) {
            boolean parent = hasChildren.getOrDefault(parsedQuestion.displayKey(), false);
            QuestionMeta questionMeta = resolveQuestionMeta(parsedQuestion, parent);

            AlevelQuestion row = new AlevelQuestion();
            row.setAlevelPaperId(paper.getAlevelPaperId());
            row.setAlevelModuleId(module.getAlevelModuleId());
            row.setParentQuestionId(parsedQuestion.parentKey() == null ? null : parentIdMap.get(parsedQuestion.parentKey()));
            row.setQuestionCode(buildQuestionCode(paper.getPaperCode(), parsedQuestion.displayKey()));
            row.setQuestionNoDisplay(parsedQuestion.displayKey());
            row.setMarkSchemeQuestionKey(parsedQuestion.displayKey());
            row.setQuestionType(questionMeta.questionType());
            row.setResponseMode(questionMeta.responseMode());
            row.setAutoGradable(questionMeta.autoGradable());
            row.setStemHtml(toParagraphHtml(parsedQuestion.stemText()));
            row.setStemText(trimToNull(parsedQuestion.stemText()));
            row.setContentHtml(null);
            row.setContentText(null);
            row.setAnswerInputSchemaJson(null);
            row.setMaxScore(parsedQuestion.maxScore());
            row.setSourcePageNo(parsedQuestion.sourcePageNo());
            row.setSourceBboxJson(null);
            row.setSortOrder(parsedQuestion.sortOrder());
            row.setStatus(1);
            row.setRemark(null);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            row.setDeleteFlag("1");
            alevelQuestionMapper.insert(row);
            parentIdMap.put(parsedQuestion.displayKey(), row.getAlevelQuestionId());
            questionCount++;

            if (!parsedQuestion.options().isEmpty()) {
                int optionIndex = 1;
                for (ParsedOption option : parsedQuestion.options()) {
                    AlevelQuestionOption optionRow = new AlevelQuestionOption();
                    optionRow.setAlevelQuestionId(row.getAlevelQuestionId());
                    optionRow.setOptionKey(option.key());
                    optionRow.setOptionHtml(toParagraphHtml(option.text()));
                    optionRow.setOptionText(trimToNull(option.text()));
                    optionRow.setStructureJson(null);
                    optionRow.setSortOrder(optionIndex++);
                    optionRow.setStatus(1);
                    optionRow.setRemark(null);
                    optionRow.setCreateTime(LocalDateTime.now());
                    optionRow.setUpdateTime(LocalDateTime.now());
                    optionRow.setDeleteFlag("1");
                    alevelQuestionOptionMapper.insert(optionRow);
                }
            }

            if (parent) {
                continue;
            }

            AlevelQuestionAnswer answerRow = new AlevelQuestionAnswer();
            answerRow.setAlevelQuestionId(row.getAlevelQuestionId());
            answerRow.setAnswerRaw(trimToNull(AlevelPdfTextNormalizationSupport.normalizeTextBlock(resolveAnswerRaw(parsedQuestion))));
            answerRow.setAnswerJson(buildAnswerJson(parsedQuestion, questionMeta));
            answerRow.setMarkSchemeJson(buildMarkSchemeJson(parsedQuestion));
            answerRow.setMarkSchemeExcerptText(trimToNull(AlevelPdfTextNormalizationSupport.normalizeTextBlock(parsedQuestion.markScheme().rawExcerpt())));
            answerRow.setGradingMode(questionMeta.autoGradable() == 1 ? "AUTO" : "REFERENCE_ONLY");
            answerRow.setStatus(1);
            answerRow.setRemark(null);
            answerRow.setCreateTime(LocalDateTime.now());
            answerRow.setUpdateTime(LocalDateTime.now());
            answerRow.setDeleteFlag("1");
            alevelQuestionAnswerMapper.insert(answerRow);
            answerCount++;
        }
        return new InsertCounts(questionCount, answerCount);
    }

    private MockExamPaperRef ensurePaperRef(AlevelPaper paper) {
        MockExamPaperRef existing = mockExamPaperRefMapper.findActiveBySource(SOURCE_TYPE_ALEVEL, paper.getAlevelPaperId());
        if (existing != null) {
            existing.setExamCategory(EXAM_CATEGORY_ALEVEL);
            existing.setExamContent(trimToNull(paper.getSubjectName()));
            existing.setExamBoard(trimToNull(paper.getExamBoard()));
            existing.setSubjectCode(trimToNull(paper.getSubjectCode()));
            existing.setSubjectName(trimToNull(paper.getSubjectName()));
            existing.setPaperCode(trimToNull(paper.getPaperCode()));
            existing.setPaperName(trimToNull(paper.getPaperName()));
            existing.setDurationSeconds(paper.getDurationSeconds());
            existing.setTotalScore(paper.getTotalScore());
            existing.setPayloadAdapter("ALEVEL");
            existing.setStatus(1);
            existing.setRemark(null);
            existing.setUpdateTime(LocalDateTime.now());
            existing.setDeleteFlag("1");
            mockExamPaperRefMapper.updateMetadata(existing);
            return existing;
        }

        MockExamPaperRef row = new MockExamPaperRef();
        row.setSourceType(SOURCE_TYPE_ALEVEL);
        row.setSourcePaperId(paper.getAlevelPaperId());
        row.setExamCategory(EXAM_CATEGORY_ALEVEL);
        row.setExamContent(trimToNull(paper.getSubjectName()));
        row.setExamBoard(trimToNull(paper.getExamBoard()));
        row.setSubjectCode(trimToNull(paper.getSubjectCode()));
        row.setSubjectName(trimToNull(paper.getSubjectName()));
        row.setPaperCode(trimToNull(paper.getPaperCode()));
        row.setPaperName(trimToNull(paper.getPaperName()));
        row.setDurationSeconds(paper.getDurationSeconds());
        row.setTotalScore(paper.getTotalScore());
        row.setPayloadAdapter("ALEVEL");
        row.setStatus(1);
        row.setRemark(null);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleteFlag("1");
        mockExamPaperRefMapper.insert(row);
        return row;
    }

    private int syncQuestionRefs(AlevelPaper paper, MockExamPaperRef paperRef) {
        List<AlevelQuestion> questions = alevelQuestionMapper.findActiveByPaperId(paper.getAlevelPaperId());
        Set<Long> parentIds = new LinkedHashSet<>();
        for (AlevelQuestion question : questions) {
            if (question.getParentQuestionId() != null) {
                parentIds.add(question.getParentQuestionId());
            }
        }

        Map<String, MockExamQuestionRef> existingByCode = new LinkedHashMap<>();
        for (MockExamQuestionRef existing : mockExamQuestionRefMapper.findActiveByPaperRefId(paperRef.getMockexamPaperRefId())) {
            String questionCode = trimToNull(existing.getSourceQuestionCode());
            if (questionCode != null) {
                existingByCode.put(questionCode, existing);
            }
        }

        int activeCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (AlevelQuestion question : questions) {
            if (parentIds.contains(question.getAlevelQuestionId())) {
                continue;
            }
            activeCount++;
            MockExamQuestionRef existing = existingByCode.remove(question.getQuestionCode());
            if (existing != null) {
                existing.setMockexamPaperRefId(paperRef.getMockexamPaperRefId());
                existing.setSourceType(SOURCE_TYPE_ALEVEL);
                existing.setSourceQuestionId(question.getAlevelQuestionId());
                existing.setSourceQuestionCode(question.getQuestionCode());
                existing.setQuestionNoDisplay(question.getQuestionNoDisplay());
                existing.setQuestionType(question.getQuestionType());
                existing.setResponseMode(question.getResponseMode());
                existing.setStatType(question.getQuestionType());
                existing.setMaxScore(question.getMaxScore());
                existing.setPreviewText(trimToNull(previewText(question.getStemText(), 220)));
                existing.setStatus(1);
                existing.setRemark(null);
                existing.setUpdateTime(now);
                existing.setDeleteFlag("1");
                mockExamQuestionRefMapper.updateMetadata(existing);
                continue;
            }

            MockExamQuestionRef row = new MockExamQuestionRef();
            row.setMockexamPaperRefId(paperRef.getMockexamPaperRefId());
            row.setSourceType(SOURCE_TYPE_ALEVEL);
            row.setSourceQuestionId(question.getAlevelQuestionId());
            row.setSourceQuestionCode(question.getQuestionCode());
            row.setQuestionNoDisplay(question.getQuestionNoDisplay());
            row.setQuestionType(question.getQuestionType());
            row.setResponseMode(question.getResponseMode());
            row.setStatType(question.getQuestionType());
            row.setMaxScore(question.getMaxScore());
            row.setPreviewText(trimToNull(previewText(question.getStemText(), 220)));
            row.setStatus(1);
            row.setRemark(null);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            row.setDeleteFlag("1");
            mockExamQuestionRefMapper.insert(row);
        }
        for (MockExamQuestionRef orphanRef : existingByCode.values()) {
            mockExamQuestionRefMapper.updateStatus(orphanRef.getMockexamQuestionRefId(), 0, now);
        }
        return activeCount;
    }

    private void syncSourceFiles(
        List<AlevelSourceFile> sourceFiles,
        Long paperId,
        String parseStatus,
        String bundleCode,
        AlevelPaper paper,
        int questionCount,
        int answerCount,
        int questionRefCount,
        String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> keepSourceFileIds = new ArrayList<>();
        for (AlevelSourceFile sourceFile : sourceFiles) {
            keepSourceFileIds.add(sourceFile.getAlevelSourceFileId());
            alevelSourceFileMapper.bindPaperId(sourceFile.getAlevelSourceFileId(), paperId, now);
            JSONObject parseResult = JSONUtil.parseObj(blankToDefault(sourceFile.getParseResultJson(), "{}"));
            parseResult.set("bundle_code", bundleCode);
            parseResult.set("alevel_paper_id", paperId);
            parseResult.set("paper_code", paper == null ? null : paper.getPaperCode());
            parseResult.set("page_count", sourceFile.getPageCount());
            parseResult.set("question_count", questionCount);
            parseResult.set("answer_count", answerCount);
            parseResult.set("question_ref_count", questionRefCount);

            alevelSourceFileMapper.updateParseState(
                sourceFile.getAlevelSourceFileId(),
                parseStatus,
                parseResult.toString(),
                sourceFile.getParseWarningJson(),
                errorMessage,
                now
            );
        }
        if (paperId != null) {
            alevelSourceFileMapper.deactivateStaleByPaperId(paperId, keepSourceFileIds, now);
        }
    }

    private Map<String, Object> buildResultPayload(
        Long paperId,
        String paperCode,
        int questionCount,
        int answerCount,
        int insertAssetCount,
        int questionRefCount,
        boolean rebuilt,
        boolean skipped
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alevel_paper_id", paperId);
        payload.put("paper_code", paperCode);
        payload.put("question_count", questionCount);
        payload.put("answer_count", answerCount);
        payload.put("insert_asset_count", insertAssetCount);
        payload.put("question_ref_count", questionRefCount);
        payload.put("build_status", skipped ? "skipped_existing" : (rebuilt ? "rebuilt_existing" : "parsed"));
        return payload;
    }

    private ParsedPaper parseQuestionPaper(AlevelSourceFile sourceFile, FileNameMeta fileNameMeta, PdfDocumentText documentText) {
        List<PaperLine> lines = normalizeLines(documentText.pages());
        List<String> instructionLines = new ArrayList<>();
        List<ParsedQuestion> questions = new ArrayList<>();
        ParsedQuestionBuilder current = null;
        boolean inQuestionArea = false;
        int sortOrder = 1;

        for (PaperLine line : lines) {
            String text = line.text();
            if (!inQuestionArea && looksLikeQuestionStart(text)) {
                inQuestionArea = true;
            }

            if (!inQuestionArea) {
                instructionLines.add(text);
                continue;
            }

            QuestionStart start = extractQuestionStart(text);
            if (start != null) {
                if (current != null) {
                    questions.add(current.build(sortOrder++));
                }
                current = new ParsedQuestionBuilder(start.key(), deriveParentKey(start.key()), start.remainder(), line.pageNo());
                continue;
            }

            if (current != null) {
                current.addLine(text);
            }
        }
        if (current != null) {
            questions.add(current.build(sortOrder));
        }

        if (questions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "question-paper 未识别出题号：" + sourceFile.getSourceFileName());
        }

        String instructionsText = trimToNull(joinLines(filterInstructionLines(instructionLines)));
        return new ParsedPaper(
            resolveUnitName(lines),
            resolveDurationSeconds(lines),
            resolveTotalScore(lines),
            instructionsText == null ? "" : toParagraphHtml(instructionsText),
            instructionsText,
            questions
        );
    }

    private Map<String, ParsedMarkScheme> parseMarkScheme(PdfDocumentText documentText, List<String> orderedQuestionKeys) {
        List<PaperLine> lines = normalizeLines(documentText.pages());
        Map<String, ParsedMarkSchemeBuilder> builders = new LinkedHashMap<>();
        ParsedMarkPointBuilder currentPoint = null;
        ParsedMarkSchemeBuilder currentScheme = null;
        Map<String, Integer> questionOrder = new LinkedHashMap<>();
        for (int index = 0; index < orderedQuestionKeys.size(); index++) {
            questionOrder.putIfAbsent(orderedQuestionKeys.get(index), index);
        }
        int currentQuestionIndex = -1;

        for (PaperLine line : lines) {
            String text = line.text();
            if (isMarkSchemeNoiseLine(text)) {
                continue;
            }

            QuestionStart start = extractQuestionStart(text);
            if (start != null) {
                String key = start.key();
                String resolvedKey = resolveMarkSchemeQuestionKey(key, questionOrder);
                Integer candidateOrder = resolvedKey == null ? null : questionOrder.get(resolvedKey);
                if (candidateOrder != null) {
                    if (candidateOrder < currentQuestionIndex) {
                        start = null;
                    } else {
                        currentQuestionIndex = candidateOrder;
                        currentScheme = builders.computeIfAbsent(resolvedKey, ParsedMarkSchemeBuilder::new);
                        currentScheme.captureTotalMarks(start.remainder());
                        currentScheme.appendRaw(text);
                        currentPoint = currentScheme.addPoint(start.remainder());
                        continue;
                    }
                } else {
                    start = null;
                }
            }

            if (currentScheme == null) {
                continue;
            }

            currentScheme.appendRaw(text);
            if (currentPoint == null) {
                currentPoint = currentScheme.addPoint(text);
                continue;
            }

            currentPoint.append(text);
        }

        Map<String, ParsedMarkScheme> result = new LinkedHashMap<>();
        for (Map.Entry<String, ParsedMarkSchemeBuilder> entry : builders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().build());
        }
        return result;
    }

    private PdfDocumentText extractPdfText(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PageText> pages = new ArrayList<>();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                pages.add(new PageText(pageNo, stripper.getText(document)));
            }
            return new PdfDocumentText(document.getNumberOfPages(), pages);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 PDF 失败：" + pdfPath.getFileName());
        }
    }

    private Path loadStoredPdf(AlevelSourceFile sourceFile) {
        String storagePath = trimToNull(sourceFile.getStoragePath());
        if (storagePath == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "source file storage_path is empty: " + sourceFile.getSourceFileName());
        }
        Path path = storagePathSupport.ensureExamAssetRoot().resolve(storagePath).normalize();
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "source file not found: " + storagePath);
        }
        return path;
    }

    private List<PaperLine> normalizeLines(List<PageText> pages) {
        List<PaperLine> result = new ArrayList<>();
        for (PageText page : pages) {
            String[] rawLines = String.valueOf(page.text()).split("\\R");
            for (String rawLine : rawLines) {
                String normalized = normalizeLine(rawLine);
                if (normalized == null) {
                    continue;
                }
                result.add(new PaperLine(page.pageNo(), normalized));
            }
        }
        return result;
    }

    private List<String> filterInstructionLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        int startIndex = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = trimToNull(lines.get(index));
            if (line != null && INSTRUCTION_START_PATTERN.matcher(line).find()) {
                startIndex = index;
                break;
            }
        }
        for (int index = startIndex; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalized = trimToNull(line);
            if (normalized == null) {
                continue;
            }
            if (INSTRUCTION_DROP_PATTERN.matcher(normalized).matches()) {
                continue;
            }
            if (looksLikeQuestionStart(normalized)) {
                continue;
            }
            if (normalized.equalsIgnoreCase("section a") || normalized.equalsIgnoreCase("section b")) {
                continue;
            }
            result.add(normalized);
        }
        return result;
    }

    private String normalizeLine(String rawLine) {
        String text = AlevelPdfTextNormalizationSupport.normalizeLine(rawLine);
        if (text == null) {
            return null;
        }
        if (HEADER_DROP_PATTERN.matcher(text).matches()) {
            return null;
        }
        if (PAGE_NOISE_PATTERN.matcher(text).matches()) {
            return null;
        }
        if (text.toLowerCase(Locale.ROOT).startsWith("mark scheme ")) {
            return null;
        }
        text = text
            .replaceAll("^\\.+\\s*", "")
            .replaceAll("^\\d+\\s+of\\s+\\d+$", "")
            .replaceAll("(?i)\\bquestion\\s+\\d+\\s+continued\\b", "")
            .replaceAll("(?i)\\bcontinued on next page\\b", "")
            .replace("►", "")
            .replace("•", "•")
            .replaceAll("(?i)\\bdo not write outside the box\\b", "")
            .replaceAll("(?i)\\bturn over for the next question\\b", "")
            .replaceAll("(?i)\\bturn over\\b", "")
            .replaceAll("(?i)\\bbox\\b", "")
            .replaceAll("(?i)there are no questions printed on this page", "")
            .replaceAll("(?i)question additional page, if required\\.", "")
            .replaceAll("(?i)number write the question numbers in the left-hand margin\\.", "")
            .replaceAll("(?i)\\bAO\\d\\s*=\\s*\\d+(?:\\.\\d+)?\\b", "")
            .replaceAll("(?i)section [a-z] total for this section: \\d+ marks", "")
            .replaceAll("(?i)for confidentiality purposes, all acknowledgements of third-party copyright material are published in a separate booklet\\.", "")
            .replaceAll("(?i)this booklet is published after each live examination series.*", "")
            .replaceAll("(?i)^this booklet$", "")
            .replaceAll("(?i)permission to reproduce all copyright material has been applied for\\.", "")
            .replaceAll("(?i)in some cases, efforts to contact copyright-holders.*", "")
            .replaceAll("(?i)^been unsuccessful and oxfordaqa will be happy to rectify any omissions of acknowledgements\\..*", "")
            .replaceAll("(?i)^contact the copyright team\\.$", "")
            .replaceAll("(?i)copyright [^\\n]*all rights reserved\\.", "")
            .replaceAll(INLINE_PAGE_CODE_PATTERN.pattern(), "")
            .replaceAll("\\s+", " ")
            .trim();
        text = AlevelPdfTextNormalizationSupport.normalizeLine(text);
        if (text == null) {
            return null;
        }
        if (text.matches("^\\d+$")) {
            return null;
        }
        return text;
    }

    private boolean looksLikeQuestionStart(String line) {
        QuestionStart start = extractQuestionStart(line);
        if (start == null) {
            return false;
        }
        String remainder = trimToNull(start.remainder());
        if (remainder == null) {
            return true;
        }
        String probe = remainder.toLowerCase(Locale.ROOT);
        return !probe.startsWith("hour")
            && !probe.startsWith("hours")
            && !probe.startsWith("minute")
            && !probe.startsWith("maximum")
            && !probe.startsWith("mark")
            && !probe.startsWith("question");
    }

    private QuestionStart extractQuestionStart(String line) {
        String normalizedLine = trimToEmpty(line);
        Matcher splitSubquestionMatcher = LEADING_SPLIT_SUBQUESTION_PATTERN.matcher(normalizedLine);
        if (splitSubquestionMatcher.matches()) {
            int headNumber = parseInt(splitSubquestionMatcher.group(1)) * 10 + parseInt(splitSubquestionMatcher.group(2));
            String subKey = trimToEmpty(splitSubquestionMatcher.group(3));
            String remainder = trimToNull(splitSubquestionMatcher.group(4));
            normalizedLine = headNumber + "." + subKey + (remainder == null ? "" : " " + remainder);
        }

        Matcher compactSubquestionMatcher = LEADING_SUBQUESTION_PATTERN.matcher(normalizedLine);
        if (compactSubquestionMatcher.matches()) {
            String rawHead = trimToEmpty(compactSubquestionMatcher.group(1));
            String rawSubKey = trimToEmpty(compactSubquestionMatcher.group(2));
            String remainder = trimToNull(compactSubquestionMatcher.group(3));
            int headNumber = parseInt(rawHead);
            if (rawHead.length() >= 2 && headNumber >= 10 && looksLikeQuestionText(remainder)) {
                normalizedLine = headNumber + "." + rawSubKey + (remainder == null ? "" : " " + remainder);
            }
        }

        Matcher dotNumberMatcher = LEADING_DOT_NUMBER_PATTERN.matcher(normalizedLine);
        if (dotNumberMatcher.matches()) {
            String remainder = trimToNull(dotNumberMatcher.group(2));
            normalizedLine = dotNumberMatcher.group(1) + (remainder == null ? "" : " " + remainder);
        }

        Matcher splitNumberMatcher = LEADING_SPLIT_NUMBER_PATTERN.matcher(normalizedLine);
        if (splitNumberMatcher.matches()) {
            String firstToken = trimToEmpty(splitNumberMatcher.group(1));
            String secondToken = trimToEmpty(splitNumberMatcher.group(2));
            int firstNumber = parseInt(firstToken);
            int secondNumber = parseInt(secondToken);
            String remainder = trimToNull(splitNumberMatcher.group(3));
            boolean singleDigitTokens = firstToken.length() == 1 && secondToken.length() == 1;
            if (singleDigitTokens && firstNumber == 0 && looksLikeQuestionText(remainder)) {
                normalizedLine = secondNumber + (remainder == null ? "" : " " + remainder);
            } else if (singleDigitTokens && firstNumber > 0 && looksLikeQuestionText(remainder)) {
                int combined = firstNumber * 10 + secondNumber;
                if (combined >= 10 && combined <= 40) {
                    normalizedLine = combined + (remainder == null ? "" : " " + remainder);
                }
            }
        }

        Matcher matcher = QUESTION_START_PATTERN.matcher(normalizedLine);
        if (!matcher.matches()) {
            return null;
        }
        String key = trimToNull(matcher.group(1));
        if (key == null) {
            return null;
        }
        String normalizedKey = normalizeQuestionKey(key);
        if (!isPlausibleQuestionKey(normalizedKey)) {
            return null;
        }
        String remainder = trimToNull(matcher.group(2));
        if (!isPlausibleQuestionRemainder(key, remainder)) {
            return null;
        }
        return new QuestionStart(normalizedKey, trimToEmpty(remainder));
    }

    private boolean isPlausibleQuestionKey(String key) {
        String normalized = trimToNull(key);
        if (normalized == null) {
            return false;
        }
        Matcher matcher = Pattern.compile("^(\\d{1,2})").matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        int root = parseInt(matcher.group(1));
        return root > 0 && root <= 40;
    }

    private boolean isPlausibleQuestionRemainder(String rawKey, String remainder) {
        String normalized = trimToNull(remainder);
        if (normalized == null) {
            return true;
        }
        if (NUMERIC_SEQUENCE_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (normalized.matches("^\\d+(?:\\.\\d+)?$")) {
            return rawKey.length() >= 2 || rawKey.contains(".") || rawKey.contains("(");
        }
        return true;
    }

    private boolean looksLikeQuestionText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return false;
        }
        if (NUMERIC_SEQUENCE_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        return normalized.matches(".*[A-Za-z\\[\\(\\?].*");
    }

    private String deriveParentKey(String key) {
        String normalized = normalizeQuestionKey(key);
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex > 0) {
            return normalized.substring(0, dotIndex);
        }
        int lastBracketIndex = normalized.lastIndexOf('(');
        if (lastBracketIndex > 0 && normalized.endsWith(")")) {
            return normalized.substring(0, lastBracketIndex);
        }
        return null;
    }

    private QuestionMeta resolveQuestionMeta(ParsedQuestion question, boolean hasChildren) {
        if (hasChildren) {
            return new QuestionMeta("STRUCTURED_GROUP", "MANUAL_ONLY", 0);
        }
        if (!question.options().isEmpty()) {
            return new QuestionMeta("SINGLE_CHOICE", "RADIO", 1);
        }
        BigDecimal maxScore = question.maxScore();
        if (maxScore != null && maxScore.compareTo(BigDecimal.valueOf(2L)) <= 0 && safeLength(question.stemText()) <= 180) {
            return new QuestionMeta("SHORT_ANSWER", "INPUT", 0);
        }
        return new QuestionMeta("OPEN_RESPONSE", "TEXTAREA", 0);
    }

    private String resolveAnswerRaw(ParsedQuestion question) {
        if (!question.options().isEmpty()) {
            String optionKey = resolveCorrectOptionKey(question);
            if (optionKey != null) {
                return optionKey;
            }
        }

        List<String> texts = new ArrayList<>();
        for (ParsedMarkPoint point : question.markScheme().points()) {
            String guidance = trimToNull(point.guidanceText());
            if (guidance != null) {
                texts.add(guidance);
            }
            if (texts.size() >= 3) {
                break;
            }
        }
        return texts.isEmpty() ? trimToNull(question.markScheme().rawExcerpt()) : String.join(" / ", texts);
    }

    private String resolveCorrectOptionKey(ParsedQuestion question) {
        for (ParsedMarkPoint point : question.markScheme().points()) {
            String guidance = trimToNull(point.guidanceText());
            if (guidance == null) {
                continue;
            }
            Matcher answerMatcher = ANSWER_OPTION_PATTERN.matcher(guidance);
            if (answerMatcher.find()) {
                return answerMatcher.group(1).trim().toUpperCase(Locale.ROOT);
            }
            String normalized = guidance.trim().toUpperCase(Locale.ROOT);
            if (normalized.matches("^[A-D]$")) {
                return normalized;
            }
            for (ParsedOption option : question.options()) {
                if (normalizeText(guidance).equals(normalizeText(option.text()))) {
                    return option.key();
                }
            }
        }
        return null;
    }

    private String buildAnswerJson(ParsedQuestion question, QuestionMeta questionMeta) {
        if (questionMeta.autoGradable() != 1) {
            return null;
        }
        String optionKey = resolveCorrectOptionKey(question);
        if (optionKey == null) {
            return null;
        }
        JSONObject payload = new JSONObject();
        payload.set("type", "single_choice");
        payload.set("correct_options", List.of(optionKey));
        return payload.toString();
    }

    private String buildMarkSchemeJson(ParsedQuestion question) {
        JSONObject payload = new JSONObject();
        payload.set("display_mode", "mark_scheme");
        payload.set("total_marks", question.maxScore());
        JSONArray points = new JSONArray();
        int index = 1;
        for (ParsedMarkPoint point : question.markScheme().points()) {
            BigDecimal markValue = point.markValue();
            if (markValue != null && question.maxScore() != null && markValue.compareTo(question.maxScore()) > 0) {
                markValue = null;
            }
            JSONObject item = new JSONObject();
            item.set("point_code", "P" + index++);
            item.set("mark_value", markValue);
            item.set("guidance_text", trimToNull(point.guidanceText()));
            item.set("comments_text", trimToNull(point.commentsText()));
            points.add(item);
        }
        payload.set("marking_points", points);
        return payload.toString();
    }

    private String normalizeQuestionKey(String value) {
        String normalized = trimToEmpty(value).replaceAll("\\s+", "");
        normalized = normalized.replaceAll("^0+(\\d)", "$1");
        normalized = normalized.replaceAll("\\.0+(\\d)", ".$1");
        return normalized.replaceAll("\\.+$", "");
    }

    private String buildQuestionCode(String paperCode, String questionKey) {
        String normalized = questionKey.replaceAll("[^A-Za-z0-9]+", "_");
        normalized = normalized.replaceAll("_+", "_").replaceAll("^_|_$", "");
        return paperCode + "_" + normalized;
    }

    private String resolveUnitName(List<PaperLine> lines) {
        for (PaperLine line : lines) {
            Matcher matcher = UNIT_NAME_PATTERN.matcher(line.text());
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private Integer resolveDurationSeconds(List<PaperLine> lines) {
        for (PaperLine line : lines) {
            Matcher matcher = DURATION_PATTERN.matcher(line.text());
            if (matcher.find()) {
                int hours = parseInt(matcher.group(1));
                int minutes = parseInt(matcher.group(2));
                return hours * 3600 + minutes * 60;
            }
        }
        return null;
    }

    private BigDecimal resolveTotalScore(List<PaperLine> lines) {
        for (PaperLine line : lines) {
            Matcher matcher = TOTAL_MARK_PATTERN.matcher(line.text());
            if (matcher.find()) {
                return parseDecimal(matcher.group(1));
            }
        }
        return null;
    }

    private FileNameMeta resolvePaperMeta(AlevelSourceFile sourceFile, Path questionPaperPath) {
        AlevelPdfMetaSupport.DetectionResult detectionResult = alevelPdfMetaSupport.fromParseResultJson(sourceFile.getParseResultJson());
        if (!alevelPdfMetaSupport.hasCorePaperFields(detectionResult)) {
            try {
                detectionResult = alevelPdfMetaSupport.detect(
                    Files.readAllBytes(questionPaperPath),
                    questionPaperPath.toString(),
                    sourceFile.getSourceFileName()
                );
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "读取 A-Level question-paper 失败: " + sourceFile.getSourceFileName());
            }
        }
        if (alevelPdfMetaSupport.hasCorePaperFields(detectionResult)) {
            return toPaperMeta(detectionResult);
        }
        return parseFileNameMeta(sourceFile.getSourceFileName());
    }

    private FileNameMeta toPaperMeta(AlevelPdfMetaSupport.DetectionResult detectionResult) {
        String qualification = trimToNull(detectionResult.qualification());
        if (qualification == null) {
            qualification = QUALIFICATION;
        }
        return new FileNameMeta(
            qualification,
            detectionResult.subjectCode(),
            detectionResult.subjectName(),
            detectionResult.unitCode(),
            detectionResult.examSession(),
            buildDetectedPaperCode(
                detectionResult.subjectCode(),
                detectionResult.unitCode(),
                detectionResult.examSession(),
                detectionResult.sessionCode()
            )
        );
    }

    private FileNameMeta parseFileNameMeta(String sourceFileName) {
        Matcher matcher = FILE_NAME_META_PATTERN.matcher(trimToEmpty(stripExtension(sourceFileName)));
        if (!matcher.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无法从文件名识别 A-Level 试卷信息：" + sourceFileName);
        }

        String subjectSlug = matcher.group(1);
        String unitCode = matcher.group(2).toUpperCase(Locale.ROOT);
        String sessionSlug = matcher.group(3).toLowerCase(Locale.ROOT);
        String year = matcher.group(4);
        SubjectMeta subjectMeta = SUBJECT_META_MAP.get(unitCode.substring(0, 2).toUpperCase(Locale.ROOT));
        if (subjectMeta == null) {
            String fallbackName = titleCaseSlug(subjectSlug);
            subjectMeta = new SubjectMeta(unitCode.substring(0, 2).toUpperCase(Locale.ROOT), fallbackName);
        }

        String examSession = sessionLabel(sessionSlug, year);
        return new FileNameMeta(
            QUALIFICATION,
            subjectMeta.subjectCode(),
            subjectMeta.subjectName(),
            unitCode,
            examSession,
            buildPaperCode(subjectMeta.subjectCode(), unitCode, sessionSlug, year)
        );
    }

    private String buildPaperCode(String subjectCode, String unitCode, String sessionSlug, String year) {
        return "OXFORDAQA_" + subjectCode + "_" + unitCode + "_" + year + "_" + sessionCode(sessionSlug);
    }

    private String buildDetectedPaperCode(String subjectCode, String unitCode, String examSession, String detectedSessionCode) {
        String sessionSuffix = trimToNull(detectedSessionCode);
        if (sessionSuffix != null && sessionSuffix.length() > 3) {
            sessionSuffix = sessionSuffix.substring(0, sessionSuffix.length() - 4);
        }
        if (sessionSuffix == null) {
            sessionSuffix = sessionCodeFromExamSession(examSession);
        }
        String year = extractYear(examSession);
        return "OXFORDAQA_" + subjectCode + "_" + unitCode + "_" + year + "_" + sessionSuffix;
    }

    private String buildPaperName(FileNameMeta fileNameMeta) {
        return fileNameMeta.subjectName() + " " + fileNameMeta.unitCode() + " " + fileNameMeta.examSession();
    }

    private String sessionLabel(String sessionSlug, String year) {
        return switch (sessionSlug) {
            case "may-june" -> "May/June " + year;
            case "october-november" -> "October/November " + year;
            case "january" -> "January " + year;
            default -> titleCaseSlug(sessionSlug).replace("-", "/") + " " + year;
        };
    }

    private String sessionCode(String sessionSlug) {
        return switch (sessionSlug) {
            case "may-june" -> "MJ";
            case "october-november" -> "ON";
            case "january" -> "JAN";
            default -> "GEN";
        };
    }

    private String sessionCodeFromExamSession(String examSession) {
        String normalized = trimToEmpty(examSession).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("may/june")) {
            return "MJ";
        }
        if (normalized.startsWith("october/november")) {
            return "ON";
        }
        if (normalized.startsWith("january")) {
            return "JAN";
        }
        return "GEN";
    }

    private String extractYear(String examSession) {
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(trimToEmpty(examSession));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0000";
    }

    private String titleCaseSlug(String value) {
        List<String> parts = new ArrayList<>();
        for (String token : trimToEmpty(value).split("-")) {
            String normalized = trimToNull(token);
            if (normalized == null) {
                continue;
            }
            if ("aqa".equalsIgnoreCase(normalized)) {
                parts.add("AQA");
                continue;
            }
            if ("alevel".equalsIgnoreCase(normalized)) {
                parts.add("A-Level");
                continue;
            }
            parts.add(normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", parts);
    }

    private String joinLines(Collection<String> lines) {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            String normalized = trimToNull(line);
            if (normalized != null) {
                items.add(normalized);
            }
        }
        return String.join("\n", items);
    }

    private String toParagraphHtml(String text) {
        String normalized = trimToNull(AlevelPdfTextNormalizationSupport.normalizeTextBlock(text));
        if (normalized == null) {
            return "";
        }
        String[] parts = normalized.split("\\n{2,}");
        List<String> html = new ArrayList<>();
        for (String part : parts) {
            String paragraph = trimToNull(part);
            if (paragraph == null) {
                continue;
            }
            html.add("<p>" + HtmlUtils.htmlEscape(paragraph).replace("\n", "<br />") + "</p>");
        }
        return String.join("", html);
    }

    private String previewText(String value, int limit) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit).trim();
    }

    private String normalizeText(String value) {
        return trimToEmpty(value).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal parseDecimal(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int parseInt(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return 0;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String stripExtension(String value) {
        String text = trimToEmpty(value);
        int index = text.lastIndexOf('.');
        return index < 0 ? text : text.substring(0, index);
    }

    private int safeLength(String value) {
        return trimToEmpty(value).length();
    }

    private boolean isMarkSchemeNoiseLine(String line) {
        String normalized = trimToNull(line);
        if (normalized == null) {
            return true;
        }
        if (MARK_SCHEME_NOISE_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        return normalized.toLowerCase(Locale.ROOT).startsWith("mark scheme ");
    }

    private String resolveMarkSchemeQuestionKey(String key, Map<String, Integer> questionOrder) {
        String normalized = normalizeQuestionKey(key);
        if (questionOrder.containsKey(normalized)) {
            return normalized;
        }
        String parentKey = deriveParentKey(normalized);
        if (parentKey != null && questionOrder.containsKey(parentKey)) {
            return parentKey;
        }
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            String rootKey = normalized.substring(0, dotIndex);
            if (questionOrder.containsKey(rootKey)) {
                return rootKey;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        String trimmed = CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
        return CharSequenceUtil.isBlank(trimmed) ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return CharSequenceUtil.trim(CharSequenceUtil.nullToEmpty(value));
    }

    private String blankToDefault(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private record SourceBundle(
        AlevelSourceFile questionPaper,
        AlevelSourceFile markScheme,
        List<AlevelSourceFile> insertFiles
    ) {
        static SourceBundle from(List<AlevelSourceFile> sourceFiles) {
            AlevelSourceFile questionPaper = null;
            AlevelSourceFile markScheme = null;
            List<AlevelSourceFile> insertFiles = new ArrayList<>();
            for (AlevelSourceFile sourceFile : sourceFiles) {
                String type = trimType(sourceFile.getSourceFileType());
                if ("QUESTION_PAPER".equals(type) && questionPaper == null) {
                    questionPaper = sourceFile;
                    continue;
                }
                if ("MARK_SCHEME".equals(type) && markScheme == null) {
                    markScheme = sourceFile;
                    continue;
                }
                if ("INSERT".equals(type)) {
                    insertFiles.add(sourceFile);
                }
            }
            return new SourceBundle(questionPaper, markScheme, insertFiles);
        }

        private static String trimType(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        }
    }

    private record SubjectMeta(String subjectCode, String subjectName) {
    }

    private record FileNameMeta(
        String qualification,
        String subjectCode,
        String subjectName,
        String unitCode,
        String examSession,
        String paperCode
    ) {
    }

    private record PdfDocumentText(int pageCount, List<PageText> pages) {
    }

    private record PageText(int pageNo, String text) {
    }

    private record PaperLine(int pageNo, String text) {
    }

    private record ParsedPaper(
        String unitName,
        Integer durationSeconds,
        BigDecimal totalScore,
        String instructionsHtml,
        String instructionsText,
        List<ParsedQuestion> questions
    ) {
    }

    private record QuestionStart(String key, String remainder) {
    }

    private record ParsedOption(String key, String text) {
    }

    private record ParsedQuestion(
        String displayKey,
        String parentKey,
        String stemText,
        List<ParsedOption> options,
        Integer sourcePageNo,
        int sortOrder,
        ParsedMarkScheme markScheme,
        BigDecimal maxScore
    ) {
        ParsedQuestion withMarkScheme(ParsedMarkScheme nextMarkScheme) {
            BigDecimal nextMaxScore = nextMarkScheme.totalMarks() == null || nextMarkScheme.totalMarks().compareTo(BigDecimal.ZERO) <= 0
                ? this.maxScore
                : nextMarkScheme.totalMarks();
            return new ParsedQuestion(
                displayKey,
                parentKey,
                stemText,
                options,
                sourcePageNo,
                sortOrder,
                nextMarkScheme,
                nextMaxScore
            );
        }
    }

    private record ParsedQuestionSet(List<ParsedQuestion> questions) {
    }

    private static final class ParsedQuestionBuilder {
        private final String displayKey;
        private final String parentKey;
        private final Integer sourcePageNo;
        private final List<String> lines = new ArrayList<>();

        private ParsedQuestionBuilder(String displayKey, String parentKey, String firstLine, Integer sourcePageNo) {
            this.displayKey = displayKey;
            this.parentKey = parentKey;
            this.sourcePageNo = sourcePageNo;
            if (trimToStatic(firstLine) != null) {
                this.lines.add(trimToStatic(firstLine));
            }
        }

        private void addLine(String line) {
            String normalized = trimToStatic(line);
            if (normalized != null) {
                this.lines.add(normalized);
            }
        }

        private ParsedQuestion build(int sortOrder) {
            ParsedOptions parsedOptions = parseOptions(lines);
            String stemText = trimToStatic(String.join("\n", parsedOptions.stemLines()));
            BigDecimal maxScore = extractStemMarks(stemText);
            return new ParsedQuestion(
                displayKey,
                parentKey,
                stemText,
                parsedOptions.options(),
                sourcePageNo,
                sortOrder,
                ParsedMarkScheme.empty(displayKey),
                maxScore
            );
        }

        private static BigDecimal extractStemMarks(String stemText) {
            String normalized = trimToStatic(stemText);
            if (normalized == null) {
                return null;
            }
            Matcher matcher = BRACKET_MARK_PATTERN.matcher(normalized);
            BigDecimal result = null;
            while (matcher.find()) {
                BigDecimal parsed = parseDecimalStatic(matcher.group(1));
                if (parsed != null) {
                    result = parsed;
                }
            }
            return result;
        }

        private static BigDecimal parseDecimalStatic(String value) {
            String normalized = trimToStatic(value);
            if (normalized == null) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private static ParsedOptions parseOptions(List<String> lines) {
            List<String> stemLines = new ArrayList<>();
            List<ParsedOption> options = new ArrayList<>();
            ParsedOptionBuilder currentOption = null;
            boolean optionsStarted = false;
            boolean optionAnchorSeen = false;
            Set<String> seenOptionKeys = new LinkedHashSet<>();

            for (String line : lines) {
                Matcher matcher = OPTION_START_PATTERN.matcher(line);
                if (matcher.matches() && (optionsStarted || optionAnchorSeen)) {
                    String optionKey = matcher.group(1).trim().toUpperCase(Locale.ROOT);
                    String optionText = matcher.group(2);
                    if (seenOptionKeys.contains(optionKey)) {
                        if (currentOption != null) {
                            currentOption.append(optionText);
                        } else {
                            stemLines.add(line);
                        }
                        continue;
                    }
                    optionsStarted = true;
                    if (currentOption != null) {
                        options.add(currentOption.build());
                    }
                    seenOptionKeys.add(optionKey);
                    currentOption = new ParsedOptionBuilder(optionKey, optionText);
                    continue;
                }

                if (optionsStarted && currentOption != null) {
                    currentOption.append(line);
                    continue;
                }

                stemLines.add(line);
                if (isOptionAnchorLine(line)) {
                    optionAnchorSeen = true;
                }
            }
            if (currentOption != null) {
                options.add(currentOption.build());
            }
            if (options.size() < 2) {
                return new ParsedOptions(new ArrayList<>(lines), List.of());
            }
            return new ParsedOptions(stemLines, options);
        }

        private static boolean isOptionAnchorLine(String line) {
            String normalized = trimToStatic(line);
            if (normalized == null) {
                return false;
            }
            return BRACKET_MARK_PATTERN.matcher(normalized).find()
                || normalized.endsWith("?")
                || normalized.endsWith("？")
                || normalized.toLowerCase(Locale.ROOT).contains("which one of the following");
        }

        private static String trimToStatic(String value) {
            String text = value == null ? "" : value.trim();
            return text.isEmpty() ? null : text;
        }
    }

    private record ParsedOptions(List<String> stemLines, List<ParsedOption> options) {
    }

    private static final class ParsedOptionBuilder {
        private final String key;
        private final List<String> lines = new ArrayList<>();

        private ParsedOptionBuilder(String key, String firstLine) {
            this.key = key;
            append(firstLine);
        }

        private void append(String line) {
            String normalized = line == null ? "" : line.trim();
            if (!normalized.isEmpty()) {
                lines.add(normalized);
            }
        }

        private ParsedOption build() {
            return new ParsedOption(key, String.join(" ", lines));
        }
    }

    private record ParsedMarkScheme(String questionKey, List<ParsedMarkPoint> points, BigDecimal totalMarks, String rawExcerpt) {
        static ParsedMarkScheme empty(String questionKey) {
            return new ParsedMarkScheme(questionKey, List.of(), null, null);
        }
    }

    private static final class ParsedMarkSchemeBuilder {
        private final String questionKey;
        private final List<ParsedMarkPointBuilder> points = new ArrayList<>();
        private final List<String> rawLines = new ArrayList<>();
        private BigDecimal explicitTotalMarks;

        private ParsedMarkSchemeBuilder(String questionKey) {
            this.questionKey = questionKey;
        }

        private void captureTotalMarks(String rawText) {
            if (explicitTotalMarks != null) {
                return;
            }
            BigDecimal parsed = extractQuestionLevelMark(rawText);
            if (parsed != null) {
                explicitTotalMarks = parsed;
            }
        }

        private ParsedMarkPointBuilder addPoint(String rawText) {
            ParsedMarkPointBuilder builder = new ParsedMarkPointBuilder(rawText);
            points.add(builder);
            return builder;
        }

        private void appendRaw(String line) {
            String normalized = trimToStatic(line);
            if (normalized != null) {
                rawLines.add(normalized);
            }
        }

        private ParsedMarkScheme build() {
            List<ParsedMarkPoint> builtPoints = new ArrayList<>();
            BigDecimal totalMarks = BigDecimal.ZERO;
            boolean hasAnyMark = false;
            for (ParsedMarkPointBuilder point : points) {
                ParsedMarkPoint built = point.build();
                if (built.guidanceText() == null && built.commentsText() == null && built.markValue() == null) {
                    continue;
                }
                builtPoints.add(built);
                if (built.markValue() != null) {
                    totalMarks = totalMarks.add(built.markValue());
                    hasAnyMark = true;
                }
            }
            return new ParsedMarkScheme(
                questionKey,
                builtPoints,
                explicitTotalMarks != null ? explicitTotalMarks : (hasAnyMark ? totalMarks : null),
                rawLines.isEmpty() ? null : String.join("\n", rawLines)
            );
        }

        private static BigDecimal extractQuestionLevelMark(String rawText) {
            String normalized = trimToStatic(rawText);
            if (normalized == null) {
                return null;
            }
            if (normalized.matches("^\\d+(?:\\.\\d+)?$")) {
                return parseDecimalStatic(normalized);
            }
            Matcher matcher = TRAILING_MARK_PATTERN.matcher(normalized);
            if (!matcher.matches()) {
                return null;
            }
            String leading = trimToStatic(matcher.group(1));
            BigDecimal parsed = parseDecimalStatic(matcher.group(2));
            if (parsed == null || leading == null || NUMERIC_SEQUENCE_PATTERN.matcher(leading).matches()) {
                return null;
            }
            return parsed;
        }

        private static BigDecimal parseDecimalStatic(String value) {
            String normalized = trimToStatic(value);
            if (normalized == null) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private static String trimToStatic(String value) {
            String text = value == null ? "" : value.trim();
            return text.isEmpty() ? null : text;
        }
    }

    private record ParsedMarkPoint(String guidanceText, String commentsText, BigDecimal markValue) {
    }

    private static final class ParsedMarkPointBuilder {
        private final List<String> guidanceLines = new ArrayList<>();
        private final List<String> commentLines = new ArrayList<>();
        private BigDecimal markValue;

        private ParsedMarkPointBuilder(String rawText) {
            append(rawText);
        }

        private void append(String rawText) {
            String text = trimToStatic(rawText);
            if (text == null) {
                return;
            }
            if (MARK_SCHEME_NOISE_PATTERN.matcher(text).matches() || text.toLowerCase(Locale.ROOT).startsWith("mark scheme ")) {
                return;
            }
            if (AO_MARK_PATTERN.matcher(text).matches()) {
                return;
            }
            String lowered = text.toLowerCase(Locale.ROOT);
            if (lowered.startsWith("allow") || lowered.startsWith("ignore") || lowered.startsWith("accept") || lowered.startsWith("do not accept")) {
                commentLines.add(text);
                return;
            }

            Matcher matcher = TRAILING_MARK_PATTERN.matcher(text);
            if (matcher.matches()) {
                String leading = trimToStatic(matcher.group(1));
                BigDecimal parsedMark = parseDecimalStatic(matcher.group(2));
                if (parsedMark != null
                    && leading != null
                    && !NUMERIC_SEQUENCE_PATTERN.matcher(leading).matches()
                    && countNumericTokens(leading) <= 1
                    && parsedMark.compareTo(BigDecimal.ZERO) >= 0
                    && parsedMark.compareTo(BigDecimal.valueOf(25L)) <= 0) {
                    guidanceLines.add(leading);
                    markValue = parsedMark;
                    return;
                }
            }

            guidanceLines.add(text);
        }

        private ParsedMarkPoint build() {
            return new ParsedMarkPoint(
                guidanceLines.isEmpty() ? null : String.join(" ", guidanceLines),
                commentLines.isEmpty() ? null : String.join(" ", commentLines),
                markValue
            );
        }

        private static String trimToStatic(String value) {
            String text = value == null ? "" : value.trim();
            return text.isEmpty() ? null : text;
        }

        private static BigDecimal parseDecimalStatic(String value) {
            String normalized = trimToStatic(value);
            if (normalized == null) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private static int countNumericTokens(String value) {
            String normalized = trimToStatic(value);
            if (normalized == null) {
                return 0;
            }
            Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(normalized);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        }
    }

    private record QuestionMeta(String questionType, String responseMode, int autoGradable) {
    }

    private record InsertCounts(int questionCount, int answerCount) {
    }
}
