package com.earthseaedu.backend.support;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.exception.ApiException;
import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
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
import org.springframework.stereotype.Component;

/**
 * A-Level PDF 元信息识别支持类，优先根据 PDF 内容识别文件类型、学科、卷号和场次。
 */
@Component
public class AlevelPdfMetaSupport {

    private static final String EXAM_BOARD = "OxfordAQA";
    private static final String CONFIDENCE_HIGH = "high";
    private static final String CONFIDENCE_MEDIUM = "medium";
    private static final String CONFIDENCE_LOW = "low";
    private static final Pattern EXPLICIT_UNIT_CODE_PATTERN = Pattern.compile("(?i)\\b(AC|BL|BU|CH|EC|FM|MA|PH)\\s*0?([1-5])\\b");
    private static final Pattern UNIT_NO_PATTERN = Pattern.compile("(?i)\\bunit\\s*-?\\s*([1-5])\\b");
    private static final Pattern PAPER_NO_PATTERN = Pattern.compile("(?i)\\bpaper\\s*-?\\s*([1-5])\\b");
    private static final Pattern QUESTION_PAPER_PATTERN = Pattern.compile("(?i)\\bquestion\\s*paper\\b");
    private static final Pattern MISSPELLED_QUESTION_PAPER_PATTERN = Pattern.compile("(?i)\\bquesiton\\s*paper\\b");
    private static final Pattern MARK_SCHEME_PATTERN = Pattern.compile("(?i)\\bmark\\s*scheme\\b");
    private static final Pattern INSERT_PATTERN = Pattern.compile("(?i)\\b(insert|source\\s*booklet|paper\\s*insert)\\b");
    private static final Pattern REPORT_PATTERN = Pattern.compile(
        "(?i)\\b(report\\s+on\\s+the\\s+examination|report\\s+on\\s+exams?|examiners?'?\\s*report|report\\s+on\\s+exam)\\b"
    );
    private static final Pattern REPORT_SHORT_PATTERN = Pattern.compile("(?i)\\bwre\\b");
    private static final Pattern QUESTION_PAPER_CONTEXT_PATTERN = Pattern.compile(
        "(?i)\\b(time\\s+allowed|answer\\s+all\\s+questions|materials|instructions|do\\s+not\\s+write)\\b"
    );
    private static final Pattern AS_QUALIFICATION_PATTERN = Pattern.compile("(?i)\\b(international\\s+as|as-level|as\\s+level)\\b");
    private static final Pattern ALEVEL_QUALIFICATION_PATTERN = Pattern.compile("(?i)\\b(international\\s+a-level|a-level|a\\s+level)\\b");
    private static final Pattern AS_AND_ALEVEL_PATTERN = Pattern.compile("(?i)\\b(international\\s+as\\s+and\\s+a-level|as\\s+and\\s+a-level)\\b");
    private static final Pattern SESSION_PATTERN = Pattern.compile("(?i)\\b(may\\s*/?\\s*june|october\\s*/?\\s*november|january)\\s+(20\\d{2})\\b");
    private static final Pattern DATED_SESSION_PATTERN = Pattern.compile(
        "(?i)\\b\\d{1,2}\\s+(january|jan|may|june|jun|october|oct|november|nov)\\s+(20\\d{2})\\b"
    );
    private static final Pattern MONTH_YEAR_PATTERN = Pattern.compile("(?i)\\b(january|jan|may|june|jun|october|oct|november|nov)\\s+(20\\d{2})\\b");
    private static final Pattern FILENAME_MAY_JUNE_PATTERN = Pattern.compile("(?i)\\bmay[-_\\s]?june[-_\\s]?(20\\d{2}|\\d{2})\\b");
    private static final Pattern FILENAME_JANUARY_PATTERN = Pattern.compile("(?i)\\bjan(?:uary)?[-_\\s]?(20\\d{2}|\\d{2})\\b");
    private static final Pattern FILENAME_OCT_NOV_PATTERN = Pattern.compile("(?i)\\boct(?:ober)?[-_\\s]?nov(?:ember)?[-_\\s]?(20\\d{2}|\\d{2})\\b");
    private static final Pattern FILENAME_JUNE_PATTERN = Pattern.compile("(?i)\\bjun(?:e)?[-_\\s]?(20\\d{2}|\\d{2})\\b");
    private static final Pattern FILENAME_NOVEMBER_PATTERN = Pattern.compile("(?i)\\bnov(?:ember)?[-_\\s]?(20\\d{2}|\\d{2})\\b");
    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern FILENAME_UNIT_PATTERN = Pattern.compile("(?i)\\bunit[-_\\s]?([1-5])\\b");
    private static final Pattern FILENAME_PAPER_PATTERN = Pattern.compile("(?i)\\bpaper[-_\\s]?([1-5])\\b");
    private static final Pattern FILENAME_INSERT_PATTERN = Pattern.compile("(?i)\\binsert[-_\\s]?([1-5])\\b");

    private static final Map<String, SubjectSpec> SUBJECT_SPECS = Map.ofEntries(
        Map.entry("ACCOUNTING", new SubjectSpec("Accounting", "9615", "AC", List.of("accounting"))),
        Map.entry("BIOLOGY", new SubjectSpec("Biology", "9610", "BL", List.of("biology"))),
        Map.entry("BUSINESS", new SubjectSpec("Business", "9625", "BU", List.of("business"))),
        Map.entry("CHEMISTRY", new SubjectSpec("Chemistry", "9620", "CH", List.of("chemistry"))),
        Map.entry("ECONOMICS", new SubjectSpec("Economics", "9640", "EC", List.of("economics"))),
        Map.entry("FURTHER_MATHEMATICS", new SubjectSpec("Further Mathematics", "9665", "FM", List.of("further mathematics", "further maths"))),
        Map.entry("MATHEMATICS", new SubjectSpec("Mathematics", "9660", "MA", List.of("mathematics", "maths", "math"))),
        Map.entry("PHYSICS", new SubjectSpec("Physics", "9630", "PH", List.of("physics")))
    );

    public DetectionResult detect(byte[] rawBytes, String logicalPath, String sourceFileName) {
        PdfExcerpt excerpt = extractPdfExcerpt(rawBytes, logicalPath);
        String contentText = excerpt.normalizedText();
        String headerText = normalizeText(String.join(" ", excerpt.lines().stream().limit(12).toList()));
        String filenameText = normalizeText(sourceFileName + " " + logicalPath);

        List<String> warnings = new ArrayList<>();
        Set<String> matchedTokens = new LinkedHashSet<>();
        Map<String, String> confidence = new LinkedHashMap<>();

        DetectionField<String> documentTypeField = detectDocumentType(headerText, contentText, filenameText, matchedTokens, warnings);
        confidence.put("document_type", documentTypeField.confidence());

        DetectionField<SubjectSpec> subjectField = detectSubject(headerText, contentText, filenameText, matchedTokens, warnings);
        confidence.put("subject", subjectField.confidence());

        DetectionField<Integer> unitNoField = detectUnitNo(contentText, filenameText, matchedTokens, warnings);
        DetectionField<String> unitCodeField = detectUnitCode(contentText, filenameText, subjectField.value(), unitNoField.value(), matchedTokens, warnings);
        confidence.put("unit", strongest(unitCodeField.confidence(), unitNoField.confidence()));

        DetectionField<String> qualificationField = detectQualification(headerText, contentText, filenameText, unitNoField.value(), matchedTokens, warnings);
        confidence.put("qualification", qualificationField.confidence());

        DetectionField<SessionInfo> sessionField = detectSession(headerText, contentText, filenameText, matchedTokens, warnings);
        confidence.put("session", sessionField.confidence());

        String subjectName = subjectField.value() == null ? null : subjectField.value().subjectName();
        String subjectCode = subjectField.value() == null ? null : subjectField.value().subjectCode();
        String unitCode = unitCodeField.value();
        Integer unitNo = unitNoField.value();
        String qualification = qualificationField.value();
        SessionInfo sessionInfo = sessionField.value();
        String examSession = sessionInfo == null ? null : sessionInfo.examSession();
        String sessionCode = sessionInfo == null ? null : sessionInfo.sessionCode();

        String overallConfidence = resolveOverallConfidence(documentTypeField.value(), subjectCode, unitCode, examSession, confidence);
        confidence.put("overall", overallConfidence);

        String bundleKey = buildBundleKey(qualification, subjectCode, unitCode, sessionCode, logicalPath, warnings);

        return new DetectionResult(
            excerpt.pageCount(),
            documentTypeField.value(),
            qualification,
            subjectName,
            subjectCode,
            unitNo,
            unitCode,
            examSession,
            sessionCode,
            EXAM_BOARD,
            bundleKey,
            overallConfidence,
            confidence,
            new ArrayList<>(matchedTokens),
            excerpt.page1Title(),
            warnings
        );
    }

    public DetectionResult fromParseResultJson(String parseResultJson) {
        if (CharSequenceUtil.isBlank(parseResultJson)) {
            return null;
        }
        JSONObject root = JSONUtil.parseObj(parseResultJson);
        JSONObject detected = root.getJSONObject("detected");
        if (detected == null || detected.isEmpty()) {
            return null;
        }
        JSONObject confidenceObject = root.getJSONObject("confidence");
        JSONObject evidence = root.getJSONObject("evidence");
        List<String> matchedTokens = jsonStringList(evidence == null ? null : evidence.getJSONArray("matched_tokens"));
        List<String> warnings = jsonStringList(root.getJSONArray("warnings"));
        Map<String, String> confidence = jsonStringMap(confidenceObject);
        return new DetectionResult(
            root.getInt("page_count"),
            detected.getStr("document_type"),
            detected.getStr("qualification"),
            detected.getStr("subject_name"),
            detected.getStr("subject_code"),
            detected.getInt("unit_no"),
            detected.getStr("unit_code"),
            detected.getStr("exam_session"),
            detected.getStr("session_code"),
            detected.getStr("exam_board"),
            root.getStr("bundle_key"),
            confidence.getOrDefault("overall", CONFIDENCE_LOW),
            confidence,
            matchedTokens,
            evidence == null ? null : evidence.getStr("page_1_title"),
            warnings
        );
    }

    public boolean hasCorePaperFields(DetectionResult detectionResult) {
        return detectionResult != null
            && CharSequenceUtil.isNotBlank(detectionResult.subjectCode())
            && CharSequenceUtil.isNotBlank(detectionResult.unitCode())
            && CharSequenceUtil.isNotBlank(detectionResult.examSession());
    }

    public String buildParseResultJson(
        String logicalPath,
        String originName,
        String sourceFileType,
        String bundleCode,
        DetectionResult detectionResult
    ) {
        JSONObject result = new JSONObject();
        result.set("logical_path", logicalPath);
        result.set("origin_name", originName);
        result.set("bundle_code", bundleCode);
        result.set("source_file_type", sourceFileType);
        result.set("page_count", detectionResult.pageCount());

        JSONObject detected = new JSONObject();
        detected.set("document_type", detectionResult.documentType());
        detected.set("qualification", detectionResult.qualification());
        detected.set("subject_name", detectionResult.subjectName());
        detected.set("subject_code", detectionResult.subjectCode());
        detected.set("unit_no", detectionResult.unitNo());
        detected.set("unit_code", detectionResult.unitCode());
        detected.set("exam_session", detectionResult.examSession());
        detected.set("session_code", detectionResult.sessionCode());
        detected.set("exam_board", detectionResult.examBoard());
        result.set("detected", detected);

        JSONObject confidence = new JSONObject();
        for (Map.Entry<String, String> entry : detectionResult.confidence().entrySet()) {
            confidence.set(entry.getKey(), entry.getValue());
        }
        result.set("confidence", confidence);

        JSONObject evidence = new JSONObject();
        evidence.set("page_1_title", detectionResult.page1Title());
        evidence.set("matched_tokens", detectionResult.matchedTokens());
        result.set("evidence", evidence);
        result.set("warnings", detectionResult.warnings());
        return result.toString();
    }

    public String buildParseWarningJson(DetectionResult detectionResult, String sourceFileType) {
        JSONArray warnings = new JSONArray();
        if ("OTHER".equals(sourceFileType)) {
            warnings.add("未从 PDF 内容稳定识别为 question-paper / mark-scheme / insert / exam-report，已按 OTHER 归类");
        }
        for (String warning : detectionResult.warnings()) {
            warnings.add(warning);
        }
        return warnings.isEmpty() ? null : warnings.toString();
    }

    private PdfExcerpt extractPdfExcerpt(byte[] rawBytes, String logicalPath) {
        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            int pageCount = document.getNumberOfPages();
            int endPage = Math.min(pageCount, 2);
            List<String> lines = new ArrayList<>();
            String page1Title = null;
            for (int pageNo = 1; pageNo <= endPage; pageNo++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String pageText = stripper.getText(document);
                for (String line : pageText.split("\\R")) {
                    String normalizedLine = normalizeLine(line);
                    if (normalizedLine == null) {
                        continue;
                    }
                    if (page1Title == null) {
                        page1Title = AlevelPdfTextNormalizationSupport.normalizeLine(line);
                    }
                    lines.add(normalizedLine);
                }
            }
            return new PdfExcerpt(pageCount, lines, String.join("\n", lines), page1Title);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "解析 PDF 内容失败: " + logicalPath);
        }
    }

    private DetectionField<String> detectDocumentType(
        String headerText,
        String contentText,
        String filenameText,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        boolean questionPaperByFilename = QUESTION_PAPER_PATTERN.matcher(filenameText).find()
            || MISSPELLED_QUESTION_PAPER_PATTERN.matcher(filenameText).find()
            || filenameText.contains(" questionpaper ")
            || filenameText.contains(" qp ");
        boolean questionPaperContext = QUESTION_PAPER_PATTERN.matcher(headerText).find()
            || QUESTION_PAPER_PATTERN.matcher(contentText).find()
            || QUESTION_PAPER_CONTEXT_PATTERN.matcher(headerText).find();
        if (MARK_SCHEME_PATTERN.matcher(contentText).find()) {
            matchedTokens.add("content:mark scheme");
            return new DetectionField<>("MARK_SCHEME", CONFIDENCE_HIGH);
        }
        if (REPORT_PATTERN.matcher(contentText).find()) {
            matchedTokens.add("content:report");
            return new DetectionField<>("EXAM_REPORT", CONFIDENCE_HIGH);
        }
        if (questionPaperContext && questionPaperByFilename) {
            matchedTokens.add("content+filename:question paper");
            return new DetectionField<>("QUESTION_PAPER", CONFIDENCE_HIGH);
        }
        if (QUESTION_PAPER_PATTERN.matcher(contentText).find()) {
            matchedTokens.add("content:question paper");
            return new DetectionField<>("QUESTION_PAPER", CONFIDENCE_HIGH);
        }
        if (questionPaperByFilename) {
            matchedTokens.add("filename:question paper");
            warnings.add("文件类型由文件名兜底识别为 QUESTION_PAPER");
            return new DetectionField<>("QUESTION_PAPER", CONFIDENCE_MEDIUM);
        }
        if (INSERT_PATTERN.matcher(contentText).find()) {
            matchedTokens.add("content:insert");
            return new DetectionField<>("INSERT", CONFIDENCE_HIGH);
        }
        if (MARK_SCHEME_PATTERN.matcher(filenameText).find() || filenameText.contains(" markscheme ") || filenameText.contains(" ms ")) {
            matchedTokens.add("filename:mark scheme");
            warnings.add("文件类型由文件名兜底识别为 MARK_SCHEME");
            return new DetectionField<>("MARK_SCHEME", CONFIDENCE_MEDIUM);
        }
        if (INSERT_PATTERN.matcher(filenameText).find()) {
            matchedTokens.add("filename:insert");
            warnings.add("文件类型由文件名兜底识别为 INSERT");
            return new DetectionField<>("INSERT", CONFIDENCE_MEDIUM);
        }
        if (REPORT_PATTERN.matcher(filenameText).find() || REPORT_SHORT_PATTERN.matcher(filenameText).find()) {
            matchedTokens.add("filename:report");
            warnings.add("文件类型由文件名兜底识别为 EXAM_REPORT");
            return new DetectionField<>("EXAM_REPORT", CONFIDENCE_MEDIUM);
        }
        warnings.add("未从内容或文件名稳定识别文件类型");
        return new DetectionField<>("OTHER", CONFIDENCE_LOW);
    }

    private DetectionField<SubjectSpec> detectSubject(
        String headerText,
        String contentText,
        String filenameText,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        Matcher explicitCodeMatcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(filenameText);
        if (explicitCodeMatcher.find()) {
            SubjectSpec spec = subjectSpecByPrefix(explicitCodeMatcher.group(1));
            if (spec != null) {
                matchedTokens.add("filename:unit code subject=" + spec.subjectName());
                return new DetectionField<>(spec, CONFIDENCE_HIGH);
            }
        }
        explicitCodeMatcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(headerText);
        if (explicitCodeMatcher.find()) {
            SubjectSpec spec = subjectSpecByPrefix(explicitCodeMatcher.group(1));
            if (spec != null) {
                matchedTokens.add("header:unit code subject=" + spec.subjectName());
                return new DetectionField<>(spec, CONFIDENCE_HIGH);
            }
        }
        for (SubjectSpec spec : orderedSubjectSpecs()) {
            for (String keyword : spec.keywords()) {
                if (headerText.contains(keyword)) {
                    matchedTokens.add("header:subject=" + spec.subjectName());
                    return new DetectionField<>(spec, CONFIDENCE_HIGH);
                }
            }
        }
        explicitCodeMatcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(contentText);
        if (explicitCodeMatcher.find()) {
            SubjectSpec spec = subjectSpecByPrefix(explicitCodeMatcher.group(1));
            if (spec != null) {
                matchedTokens.add("content:unit code subject=" + spec.subjectName());
                return new DetectionField<>(spec, CONFIDENCE_HIGH);
            }
        }
        for (SubjectSpec spec : orderedSubjectSpecs()) {
            for (String keyword : spec.keywords()) {
                if (contentText.contains(keyword)) {
                    matchedTokens.add("content:subject=" + spec.subjectName());
                    return new DetectionField<>(spec, CONFIDENCE_MEDIUM);
                }
            }
        }
        for (SubjectSpec spec : orderedSubjectSpecs()) {
            for (String keyword : spec.keywords()) {
                if (filenameText.contains(keyword)) {
                    matchedTokens.add("filename:subject=" + spec.subjectName());
                    warnings.add("学科由文件名兜底识别为 " + spec.subjectName());
                    return new DetectionField<>(spec, CONFIDENCE_MEDIUM);
                }
            }
            if (filenameText.contains(" " + spec.prefix().toLowerCase(Locale.ROOT) + "0")) {
                matchedTokens.add("filename:unit prefix=" + spec.prefix());
                warnings.add("学科由文件名中的卷号前缀兜底识别为 " + spec.subjectName());
                return new DetectionField<>(spec, CONFIDENCE_MEDIUM);
            }
        }
        warnings.add("未识别到学科信息");
        return new DetectionField<>(null, CONFIDENCE_LOW);
    }

    private DetectionField<Integer> detectUnitNo(
        String contentText,
        String filenameText,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        Matcher explicitCodeMatcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(contentText);
        if (explicitCodeMatcher.find()) {
            Integer unitNo = parseUnitNo(explicitCodeMatcher.group(2));
            if (unitNo != null) {
                matchedTokens.add("content:unit code=" + explicitCodeMatcher.group());
                return new DetectionField<>(unitNo, CONFIDENCE_HIGH);
            }
        }
        Matcher unitMatcher = UNIT_NO_PATTERN.matcher(contentText);
        if (unitMatcher.find()) {
            Integer unitNo = parseUnitNo(unitMatcher.group(1));
            matchedTokens.add("content:unit=" + unitNo);
            return new DetectionField<>(unitNo, CONFIDENCE_HIGH);
        }
        Matcher paperMatcher = PAPER_NO_PATTERN.matcher(contentText);
        if (paperMatcher.find()) {
            Integer unitNo = parseUnitNo(paperMatcher.group(1));
            matchedTokens.add("content:paper=" + unitNo);
            return new DetectionField<>(unitNo, CONFIDENCE_MEDIUM);
        }
        Matcher filenameUnitMatcher = FILENAME_UNIT_PATTERN.matcher(filenameText);
        if (filenameUnitMatcher.find()) {
            Integer unitNo = parseUnitNo(filenameUnitMatcher.group(1));
            matchedTokens.add("filename:unit=" + unitNo);
            warnings.add("卷号由文件名中的 unit 信息兜底识别");
            return new DetectionField<>(unitNo, CONFIDENCE_MEDIUM);
        }
        Matcher filenamePaperMatcher = FILENAME_PAPER_PATTERN.matcher(filenameText);
        if (filenamePaperMatcher.find()) {
            Integer unitNo = parseUnitNo(filenamePaperMatcher.group(1));
            matchedTokens.add("filename:paper=" + unitNo);
            warnings.add("卷号由文件名中的 paper 信息兜底识别");
            return new DetectionField<>(unitNo, CONFIDENCE_LOW);
        }
        Matcher filenameInsertMatcher = FILENAME_INSERT_PATTERN.matcher(filenameText);
        if (filenameInsertMatcher.find()) {
            Integer unitNo = parseUnitNo(filenameInsertMatcher.group(1));
            matchedTokens.add("filename:insert=" + unitNo);
            warnings.add("卷号由文件名中的 insert 编号兜底识别");
            return new DetectionField<>(unitNo, CONFIDENCE_LOW);
        }
        warnings.add("未识别到 unit/paper 编号");
        return new DetectionField<>(null, CONFIDENCE_LOW);
    }

    private DetectionField<String> detectUnitCode(
        String contentText,
        String filenameText,
        SubjectSpec subjectSpec,
        Integer unitNo,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        Matcher matcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(contentText);
        if (matcher.find()) {
            String unitCode = matcher.group(1).toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, "%02d", parseUnitNo(matcher.group(2)));
            matchedTokens.add("content:unit code=" + unitCode);
            return new DetectionField<>(unitCode, CONFIDENCE_HIGH);
        }
        Matcher filenameCodeMatcher = EXPLICIT_UNIT_CODE_PATTERN.matcher(filenameText);
        if (filenameCodeMatcher.find()) {
            String unitCode = filenameCodeMatcher.group(1).toUpperCase(Locale.ROOT)
                + String.format(Locale.ROOT, "%02d", parseUnitNo(filenameCodeMatcher.group(2)));
            matchedTokens.add("filename:unit code=" + unitCode);
            warnings.add("卷代码由文件名兜底识别为 " + unitCode);
            return new DetectionField<>(unitCode, CONFIDENCE_MEDIUM);
        }
        if (subjectSpec != null && unitNo != null) {
            String unitCode = subjectSpec.prefix() + String.format(Locale.ROOT, "%02d", unitNo);
            matchedTokens.add("derived:unit code=" + unitCode);
            warnings.add("卷代码由学科和 unit 编号推导为 " + unitCode);
            return new DetectionField<>(unitCode, CONFIDENCE_MEDIUM);
        }
        warnings.add("未识别到卷代码");
        return new DetectionField<>(null, CONFIDENCE_LOW);
    }

    private DetectionField<String> detectQualification(
        String headerText,
        String contentText,
        String filenameText,
        Integer unitNo,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        if (filenameText.contains(" as level ") || filenameText.startsWith(" as ")) {
            matchedTokens.add("filename:qualification=AS");
            warnings.add("qualification 由文件名前缀兜底识别为 International AS");
            return new DetectionField<>("International AS", CONFIDENCE_MEDIUM);
        }
        if (filenameText.contains(" a level ") || filenameText.startsWith(" a level ")) {
            matchedTokens.add("filename:qualification=A-Level");
            warnings.add("qualification 由文件名前缀兜底识别为 International A-Level");
            return new DetectionField<>("International A-Level", CONFIDENCE_MEDIUM);
        }
        String qualificationText = headerText + " " + contentText;
        if (AS_AND_ALEVEL_PATTERN.matcher(qualificationText).find()) {
            if (unitNo != null) {
                String qualification = unitNo <= 2 ? "International AS" : "International A-Level";
                matchedTokens.add("content:qualification=as-and-a-level");
                warnings.add("qualification 从 AS and A-Level 标题结合卷号推导为 " + qualification);
                return new DetectionField<>(qualification, CONFIDENCE_MEDIUM);
            }
        }
        if (AS_QUALIFICATION_PATTERN.matcher(qualificationText).find()) {
            matchedTokens.add("content:qualification=AS");
            return new DetectionField<>("International AS", CONFIDENCE_HIGH);
        }
        if (ALEVEL_QUALIFICATION_PATTERN.matcher(qualificationText).find()) {
            matchedTokens.add("content:qualification=A-Level");
            return new DetectionField<>("International A-Level", CONFIDENCE_HIGH);
        }
        if (filenameText.contains(" as-level ") || filenameText.startsWith("as-") || filenameText.contains(" internationalbiology as ")) {
            matchedTokens.add("filename:qualification=AS");
            warnings.add("qualification 由文件名兜底识别为 International AS");
            return new DetectionField<>("International AS", CONFIDENCE_MEDIUM);
        }
        if (filenameText.contains(" a-level ") || filenameText.startsWith("a-level-") || filenameText.contains(" internationalbiology a ")) {
            matchedTokens.add("filename:qualification=A-Level");
            warnings.add("qualification 由文件名兜底识别为 International A-Level");
            return new DetectionField<>("International A-Level", CONFIDENCE_MEDIUM);
        }
        if (unitNo != null) {
            String qualification = unitNo <= 2 ? "International AS" : "International A-Level";
            matchedTokens.add("derived:qualification=" + qualification);
            warnings.add("qualification 由卷号推导为 " + qualification);
            return new DetectionField<>(qualification, CONFIDENCE_LOW);
        }
        warnings.add("未识别到 qualification");
        return new DetectionField<>(null, CONFIDENCE_LOW);
    }

    private DetectionField<SessionInfo> detectSession(
        String headerText,
        String contentText,
        String filenameText,
        Set<String> matchedTokens,
        List<String> warnings
    ) {
        String sessionText = headerText + " " + contentText;
        Matcher sessionMatcher = SESSION_PATTERN.matcher(sessionText);
        if (sessionMatcher.find()) {
            SessionInfo sessionInfo = sessionInfo(sessionMatcher.group(1), sessionMatcher.group(2));
            matchedTokens.add("content:session=" + sessionInfo.examSession());
            return new DetectionField<>(sessionInfo, CONFIDENCE_HIGH);
        }
        Matcher datedMatcher = DATED_SESSION_PATTERN.matcher(sessionText);
        if (datedMatcher.find()) {
            SessionInfo sessionInfo = sessionInfo(datedMatcher.group(1), datedMatcher.group(2));
            matchedTokens.add("content:date-session=" + sessionInfo.examSession());
            return new DetectionField<>(sessionInfo, CONFIDENCE_MEDIUM);
        }
        Matcher monthYearMatcher = MONTH_YEAR_PATTERN.matcher(sessionText);
        if (monthYearMatcher.find()) {
            SessionInfo sessionInfo = sessionInfo(monthYearMatcher.group(1), monthYearMatcher.group(2));
            matchedTokens.add("content:month-session=" + sessionInfo.examSession());
            return new DetectionField<>(sessionInfo, CONFIDENCE_MEDIUM);
        }
        SessionInfo filenameSession = detectSessionFromFilename(filenameText);
        if (filenameSession != null) {
            matchedTokens.add("filename:session=" + filenameSession.examSession());
            warnings.add("场次由文件名/目录兜底识别为 " + filenameSession.examSession());
            return new DetectionField<>(filenameSession, CONFIDENCE_MEDIUM);
        }
        warnings.add("未识别到考试场次");
        return new DetectionField<>(null, CONFIDENCE_LOW);
    }

    private String buildBundleKey(
        String qualification,
        String subjectCode,
        String unitCode,
        String sessionCode,
        String logicalPath,
        List<String> warnings
    ) {
        if (CharSequenceUtil.isNotBlank(subjectCode) && CharSequenceUtil.isNotBlank(unitCode) && CharSequenceUtil.isNotBlank(sessionCode)) {
            String normalizedQualification = CharSequenceUtil.blankToDefault(qualification, "UNKNOWN")
                .replace("International ", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toUpperCase(Locale.ROOT);
            return "OXFORDAQA|" + normalizedQualification + "|" + subjectCode + "|" + unitCode + "|" + sessionCode;
        }
        warnings.add("bundle key 缺少关键元信息，已回退到路径归一化方案");
        return normalizePathFallback(logicalPath);
    }

    private String normalizePathFallback(String logicalPath) {
        String normalized = normalizeText(logicalPath)
            .replace(' ', '-')
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        return CharSequenceUtil.blankToDefault(normalized, "UNKNOWN");
    }

    private SessionInfo detectSessionFromFilename(String filenameText) {
        Matcher mayJuneMatcher = FILENAME_MAY_JUNE_PATTERN.matcher(filenameText);
        if (mayJuneMatcher.find()) {
            return sessionInfo("may june", normalizeYear(mayJuneMatcher.group(1)));
        }
        Matcher januaryMatcher = FILENAME_JANUARY_PATTERN.matcher(filenameText);
        if (januaryMatcher.find()) {
            return sessionInfo("january", normalizeYear(januaryMatcher.group(1)));
        }
        Matcher octNovMatcher = FILENAME_OCT_NOV_PATTERN.matcher(filenameText);
        if (octNovMatcher.find()) {
            return sessionInfo("october november", normalizeYear(octNovMatcher.group(1)));
        }
        Matcher juneMatcher = FILENAME_JUNE_PATTERN.matcher(filenameText);
        if (juneMatcher.find()) {
            return sessionInfo("may june", normalizeYear(juneMatcher.group(1)));
        }
        Matcher novemberMatcher = FILENAME_NOVEMBER_PATTERN.matcher(filenameText);
        if (novemberMatcher.find()) {
            return sessionInfo("october november", normalizeYear(novemberMatcher.group(1)));
        }
        Matcher yearMatcher = YEAR_ONLY_PATTERN.matcher(filenameText);
        if (yearMatcher.find()) {
            String year = yearMatcher.group(1);
            if (filenameText.contains(" januaryuary ") || filenameText.contains(" january ")) {
                return sessionInfo("january", year);
            }
            if (filenameText.contains(" may_june ") || filenameText.contains(" may june ") || filenameText.contains(" mayjune ")) {
                return sessionInfo("may june", year);
            }
        }
        return null;
    }

    private SessionInfo sessionInfo(String rawLabel, String year) {
        String normalized = normalizeText(rawLabel);
        String sessionCode;
        String examSession;
        if (normalized.contains("january") || normalized.equals("jan")) {
            examSession = "January " + year;
            sessionCode = "JAN" + year;
        } else if (normalized.contains("october") || normalized.contains("november") || normalized.contains("oct") || normalized.contains("nov")) {
            examSession = "October/November " + year;
            sessionCode = "ON" + year;
        } else {
            examSession = "May/June " + year;
            sessionCode = "MJ" + year;
        }
        return new SessionInfo(examSession, sessionCode);
    }

    private String resolveOverallConfidence(
        String documentType,
        String subjectCode,
        String unitCode,
        String examSession,
        Map<String, String> confidence
    ) {
        if (CharSequenceUtil.isBlank(documentType) || "OTHER".equals(documentType)
            || CharSequenceUtil.isBlank(subjectCode)
            || CharSequenceUtil.isBlank(unitCode)
            || CharSequenceUtil.isBlank(examSession)) {
            return CONFIDENCE_LOW;
        }
        if (confidence.containsValue(CONFIDENCE_LOW)) {
            return CONFIDENCE_MEDIUM;
        }
        return CONFIDENCE_HIGH;
    }

    private String strongest(String left, String right) {
        if (CONFIDENCE_HIGH.equals(left) || CONFIDENCE_HIGH.equals(right)) {
            return CONFIDENCE_HIGH;
        }
        if (CONFIDENCE_MEDIUM.equals(left) || CONFIDENCE_MEDIUM.equals(right)) {
            return CONFIDENCE_MEDIUM;
        }
        return CONFIDENCE_LOW;
    }

    private List<SubjectSpec> orderedSubjectSpecs() {
        return List.of(
            SUBJECT_SPECS.get("FURTHER_MATHEMATICS"),
            SUBJECT_SPECS.get("MATHEMATICS"),
            SUBJECT_SPECS.get("ACCOUNTING"),
            SUBJECT_SPECS.get("BIOLOGY"),
            SUBJECT_SPECS.get("BUSINESS"),
            SUBJECT_SPECS.get("CHEMISTRY"),
            SUBJECT_SPECS.get("ECONOMICS"),
            SUBJECT_SPECS.get("PHYSICS")
        );
    }

    private SubjectSpec subjectSpecByPrefix(String prefix) {
        if (CharSequenceUtil.isBlank(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.toUpperCase(Locale.ROOT);
        for (SubjectSpec spec : SUBJECT_SPECS.values()) {
            if (spec.prefix().equals(normalizedPrefix)) {
                return spec;
            }
        }
        return null;
    }

    private Integer parseUnitNo(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return null;
        }
        try {
            int unitNo = Integer.parseInt(value.trim());
            return unitNo >= 1 && unitNo <= 5 ? unitNo : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeYear(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return String.valueOf(Year.now().getValue());
        }
        String trimmed = value.trim();
        if (trimmed.length() == 2) {
            return "20" + trimmed;
        }
        return trimmed;
    }

    private List<String> jsonStringList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (Object item : array) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Map<String, String> jsonStringMap(JSONObject object) {
        Map<String, String> result = new LinkedHashMap<>();
        if (object == null) {
            return result;
        }
        for (String key : object.keySet()) {
            Object value = object.get(key);
            result.put(key, value == null ? null : String.valueOf(value));
        }
        return result;
    }

    private String normalizeLine(String rawLine) {
        return AlevelPdfTextNormalizationSupport.normalizeLine(rawLine);
    }

    private String normalizeText(String rawText) {
        return AlevelPdfTextNormalizationSupport.normalizeSearchText(rawText);
    }

    private record PdfExcerpt(int pageCount, List<String> lines, String normalizedText, String page1Title) {
    }

    private record DetectionField<T>(T value, String confidence) {
    }

    private record SessionInfo(String examSession, String sessionCode) {
    }

    private record SubjectSpec(String subjectName, String subjectCode, String prefix, List<String> keywords) {
    }

    public record DetectionResult(
        Integer pageCount,
        String documentType,
        String qualification,
        String subjectName,
        String subjectCode,
        Integer unitNo,
        String unitCode,
        String examSession,
        String sessionCode,
        String examBoard,
        String bundleKey,
        String overallConfidence,
        Map<String, String> confidence,
        List<String> matchedTokens,
        String page1Title,
        List<String> warnings
    ) {
    }
}
