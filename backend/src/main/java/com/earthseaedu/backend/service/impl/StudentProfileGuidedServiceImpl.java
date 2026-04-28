package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.StudentProfileGuidedMapper;
import com.earthseaedu.backend.service.AiPromptRuntimeService;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.StudentProfileGuidedArchivePersistenceService;
import com.earthseaedu.backend.service.StudentProfileGuidedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * StudentProfileGuidedServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class StudentProfileGuidedServiceImpl implements StudentProfileGuidedService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileGuidedServiceImpl.class);

    private static final String QUESTIONNAIRE_CODE = "student_profile_guided_v3";
    private static final int QUESTIONNAIRE_VERSION = 3;
    private static final String SCORING_PROMPT_KEY = "student_profile_build.scoring";

    private static final List<Map<String, String>> SCORE_STATUS_OPTIONS = options(
        opt("SCORED", "已出分"),
        opt("PLANNED", "计划参加"),
        opt("ESTIMATED", "预估")
    );
    private static final List<Map<String, String>> CEFR_LEVEL_OPTIONS = options(
        opt("A1", "入门级"),
        opt("A2", "初级"),
        opt("B1", "中级"),
        opt("B2", "中高级"),
        opt("C1", "高级"),
        opt("C2", "精通级"),
        opt("UNKNOWN", "未知")
    );
    private static final List<Map<String, String>> A_LEVEL_GRADE_OPTIONS = options(
        opt("A*"), opt("A"), opt("B"), opt("C"), opt("D"), opt("E"), opt("U"), opt("NA")
    );
    private static final List<Map<String, String>> A_LEVEL_BOARD_OPTIONS = options(
        opt("CAIE", "剑桥国际"),
        opt("EDEXCEL", "爱德思"),
        opt("AQA", "AQA"),
        opt("OCR", "OCR"),
        opt("OTHER", "其他"),
        opt("UNKNOWN", "未知")
    );
    private static final List<Map<String, String>> AP_SCORE_OPTIONS = options(
        opt("5"), opt("4"), opt("3"), opt("2"), opt("1")
    );
    private static final List<Map<String, String>> IB_LEVEL_OPTIONS = options(
        opt("HL", "HL 高阶"),
        opt("SL", "SL 标准级")
    );
    private static final List<Map<String, String>> IB_SCORE_OPTIONS = options(
        opt("7"), opt("6"), opt("5"), opt("4"), opt("3"), opt("2"), opt("1")
    );

    private static final Map<String, String> A_LEVEL_SUBJECT_ID_MAPPING = Map.ofEntries(
        Map.entry("MATH", "AL_MATH"),
        Map.entry("FURTHER_MATH", "AL_FURTHER_MATH"),
        Map.entry("PHYSICS", "AL_PHYSICS"),
        Map.entry("CHEMISTRY", "AL_CHEMISTRY"),
        Map.entry("BIOLOGY", "AL_BIOLOGY"),
        Map.entry("ECONOMICS", "AL_ECONOMICS"),
        Map.entry("BUSINESS", "AL_BUSINESS"),
        Map.entry("COMPUTER_SCIENCE", "AL_COMPUTER_SCIENCE")
    );
    private static final Map<String, String> IB_SUBJECT_ID_MAPPING = Map.ofEntries(
        Map.entry("MATH_AA", "IB_MATH_AA"),
        Map.entry("MATH_AI", "IB_MATH_AI"),
        Map.entry("PHYSICS", "IB_PHYSICS"),
        Map.entry("CHEMISTRY", "IB_CHEMISTRY"),
        Map.entry("BIOLOGY", "IB_BIOLOGY"),
        Map.entry("ECONOMICS", "IB_ECONOMICS"),
        Map.entry("BUSINESS_MANAGEMENT", "IB_BUSINESS_MANAGEMENT"),
        Map.entry("COMPUTER_SCIENCE", "IB_COMPUTER_SCIENCE")
    );

    private static final Set<String> UNIVERSITY_OR_ABOVE_GRADES = Set.of(
        "TRANSFER_YEAR_1",
        "大一",
        "大二",
        "大三",
        "大四",
        "大五",
        "研一",
        "研二",
        "研三",
        "博一",
        "博二",
        "博三",
        "已毕业"
    );

    private static final List<Map<String, String>> COMPETITION_FIELD_OPTIONS = options(
        opt("MATH", "数学"),
        opt("CS", "计算机"),
        opt("PHYSICS", "物理"),
        opt("CHEM", "化学"),
        opt("BIO", "生物"),
        opt("ECON", "经济"),
        opt("DEBATE", "辩论"),
        opt("WRITING", "写作"),
        opt("OTHER", "其他")
    );
    private static final List<Map<String, String>> COMPETITION_TIER_OPTIONS = options(
        opt("T1", "第一梯队"),
        opt("T2", "第二梯队"),
        opt("T3", "第三梯队"),
        opt("T4", "第四梯队"),
        opt("UNKNOWN", "未知")
    );
    private static final List<Map<String, String>> COMPETITION_LEVEL_OPTIONS = options(
        opt("SCHOOL", "校级"),
        opt("CITY", "市级"),
        opt("PROVINCE", "省级"),
        opt("NATIONAL", "国家级"),
        opt("INTERNATIONAL", "国际级")
    );
    private static final List<Map<String, String>> ACTIVITY_CATEGORY_OPTIONS = options(
        opt("LEADERSHIP", "领导力活动"),
        opt("ACADEMIC", "学术活动"),
        opt("SPORTS", "体育活动"),
        opt("ARTS", "艺术活动"),
        opt("COMMUNITY", "社区活动"),
        opt("ENTREPRENEURSHIP", "创业活动"),
        opt("OTHER", "其他")
    );
    private static final List<Map<String, String>> ACTIVITY_ROLE_OPTIONS = options(
        opt("FOUNDER", "创始人"),
        opt("PRESIDENT", "负责人"),
        opt("CORE_MEMBER", "核心成员"),
        opt("MEMBER", "成员"),
        opt("OTHER", "其他")
    );
    private static final List<Map<String, String>> PROJECT_TYPE_OPTIONS = options(
        opt("RESEARCH", "科研项目"),
        opt("INTERNSHIP", "实习经历"),
        opt("ENGINEERING_PROJECT", "工程项目"),
        opt("STARTUP", "创业项目"),
        opt("CREATIVE_PROJECT", "创意项目"),
        opt("VOLUNTEER_WORK", "志愿工作"),
        opt("OTHER", "其他")
    );
    private static final List<Map<String, String>> PROJECT_FIELD_OPTIONS = options(
        opt("CS", "计算机"),
        opt("ECON", "经济"),
        opt("FIN", "金融"),
        opt("BIO", "生物"),
        opt("PHYS", "物理"),
        opt("DESIGN", "设计"),
        opt("OTHER", "其他")
    );
    private static final List<Map<String, String>> RELEVANCE_OPTIONS = options(
        opt("HIGH", "高相关"),
        opt("MEDIUM", "中相关"),
        opt("LOW", "低相关")
    );
    private static final List<Map<String, String>> COMPETITION_EVIDENCE_OPTIONS = options(
        opt("CERTIFICATE", "证书"),
        opt("LINK", "链接"),
        opt("SCHOOL_CONFIRMATION", "学校证明"),
        opt("NONE", "无")
    );
    private static final List<Map<String, String>> ACTIVITY_EVIDENCE_OPTIONS = options(
        opt("LINK", "链接"),
        opt("SCHOOL_CONFIRMATION", "学校证明"),
        opt("PHOTO", "照片"),
        opt("NONE", "无")
    );
    private static final List<Map<String, String>> PROJECT_EVIDENCE_OPTIONS = options(
        opt("LINK", "链接"),
        opt("MENTOR_LETTER", "导师证明"),
        opt("EMPLOYER_CONFIRMATION", "单位证明"),
        opt("NONE", "无")
    );

    private static final List<Map<String, Object>> QUESTIONS = buildQuestions();
    private static final Map<String, Map<String, Object>> STANDARDIZED_SCORE_QUESTION_TEMPLATES =
        buildStandardizedScoreQuestionTemplates();
    private static final Map<String, Map<String, Object>> QUESTION_BY_CODE = buildQuestionByCode();

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JwtService jwtService;
    private final StudentProfileGuidedMapper guidedMapper;
    private final ObjectMapper objectMapper;
    private final BusinessProfileFormService businessProfileFormService;
    private final StudentProfileGuidedArchivePersistenceService guidedArchivePersistenceService;
    private final AiPromptRuntimeService aiPromptRuntimeService;
    private final Map<String, List<Map<String, String>>> sharedQuestionOptionsCache = new ConcurrentHashMap<>();

    /**
     * 创建 StudentProfileGuidedServiceImpl 实例。
     */
    public StudentProfileGuidedServiceImpl(
        JwtService jwtService,
        StudentProfileGuidedMapper guidedMapper,
        ObjectMapper objectMapper,
        BusinessProfileFormService businessProfileFormService,
        StudentProfileGuidedArchivePersistenceService guidedArchivePersistenceService,
        AiPromptRuntimeService aiPromptRuntimeService
    ) {
        this.jwtService = jwtService;
        this.guidedMapper = guidedMapper;
        this.objectMapper = objectMapper;
        this.businessProfileFormService = businessProfileFormService;
        this.guidedArchivePersistenceService = guidedArchivePersistenceService;
        this.aiPromptRuntimeService = aiPromptRuntimeService;
    }

    /**
     * {@inheritDoc}
     */
    public List<Map<String, Object>> listQuestions() {
        return QUESTIONS.stream().map(question -> {
            Map<String, Object> result = copyQuestion(question);
            List<Map<String, String>> options = questionOptions(string(question.get("code")));
            if (options != null) {
                result.put("options", options);
            }
            return result;
        }).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Map<String, Object> getOrCreateCurrentBundle(
        String authorizationHeader,
        boolean createIfMissing
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        GuidedSessionRow session = findLatestSession(studentId);
        if (session != null && isStaleQuestionnaireSession(session.sessionId())) {
            guidedMapper.expireSession(session.sessionId());
            session = null;
        }
        if (session == null && createIfMissing) {
            session = createSession(studentId);
        }
        if (session == null) {
            return bundle(null, List.of(), List.of(), null, serializeVisibleQuestions(Map.of()), null);
        }
        return getSessionBundleForStudent(studentId, session.sessionId());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Map<String, Object> restartCurrentSession(String authorizationHeader) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        guidedMapper.expireSessionsForRestart(studentId, QUESTIONNAIRE_CODE);
        GuidedSessionRow session = createSession(studentId);
        return getSessionBundleForStudent(studentId, session.sessionId());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSessionBundle(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        return getSessionBundleForStudent(studentId, sessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Map<String, Object> submitGuidedAnswer(
        String authorizationHeader,
        String sessionId,
        String questionCode,
        Map<String, Object> rawAnswer
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        GuidedSessionRow session = requireOwnedSession(studentId, sessionId);
        Map<String, Object> question = QUESTION_BY_CODE.get(questionCode);
        if (question == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "题目不存在");
        }

        Map<String, Map<String, Object>> answers = loadAnswers(sessionId);
        if (!isQuestionVisible(question, answers)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "当前题目不在可作答范围内");
        }

        Map<String, Object> existingAnswer = answers.get(questionCode);
        boolean editingExistingAnswer = existingAnswer != null && isAnswerMeaningful(question, existingAnswer);
        if ("completed".equals(session.sessionStatus()) && !editingExistingAnswer) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "该固定问卷已完成，只能修改历史题目");
        }
        Map<String, Object> enrichedQuestion = serializeQuestion(question, answers, null, null);
        Map<String, Object> normalizedAnswer;
        if (isTrue(rawAnswer, "skip")) {
            if (Boolean.FALSE.equals(enrichedQuestion.get("skippable"))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "请先填写答案");
            }
            normalizedAnswer = new LinkedHashMap<>();
            normalizedAnswer.put("skipped", true);
        } else {
            normalizedAnswer = normalizeAnswer(enrichedQuestion, rawAnswer == null ? Map.of() : rawAnswer, answers);
        }
        validateRequiredAnswer(enrichedQuestion, normalizedAnswer);
        String displayText = answerDisplayText(enrichedQuestion, normalizedAnswer);
        if (!isAnswerMeaningful(enrichedQuestion, normalizedAnswer)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "请先填写答案");
        }

        int versionNo = session.versionNo() + 1;
        upsertAnswer(sessionId, studentId, question, normalizedAnswer, displayText, versionNo);
        upsertQuestionMessage(
            sessionId,
            studentId,
            "user",
            "answer",
            questionCode,
            displayText,
            Map.of("answer", normalizedAnswer),
            editingExistingAnswer
        );

        answers.put(questionCode, normalizedAnswer);
        guidedArchivePersistenceService.syncGuidedArchiveFormSnapshot(
            buildProfileJson(studentId, sessionId, answers),
            studentId
        );
        Map<String, Object> nextQuestion = findNextQuestion(answers);
        int visibleCount = visibleQuestions(answers).size();
        if (nextQuestion == null) {
            guidedMapper.completeSession(mapOfEntries(
                "sessionId",
                sessionId,
                "currentQuestionIndex",
                visibleCount,
                "versionNo",
                versionNo
            ));
            insertMessage(
                sessionId,
                studentId,
                "assistant",
                "notice",
                "问卷已完成，系统已开始整理你的建档结果。",
                null,
                Map.of("status", "completed")
            );
            generateGuidedResultForStudent(studentId, sessionId, "completed", false);
        } else {
            int nextIndex = visibleQuestionIndex(answers, string(nextQuestion.get("code")));
            guidedMapper.activateSession(mapOfEntries(
                "sessionId",
                sessionId,
                "currentQuestionCode",
                string(nextQuestion.get("code")),
                "currentQuestionIndex",
                nextIndex,
                "versionNo",
                versionNo
            ));
            upsertQuestionMessage(
                sessionId,
                studentId,
                "assistant",
                "question",
                string(nextQuestion.get("code")),
                string(nextQuestion.get("title")),
                serializeQuestion(nextQuestion, answers, nextIndex + 1, visibleCount),
                true
            );
        }

        return getSessionBundleForStudent(studentId, sessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Map<String, Object> exitGuidedSession(
        String authorizationHeader,
        String sessionId,
        String triggerReason
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        GuidedSessionRow session = requireOwnedSession(studentId, sessionId);
        if (!"completed".equals(session.sessionStatus())) {
            guidedMapper.exitSession(sessionId);
            insertMessage(
                sessionId,
                studentId,
                "assistant",
                "notice",
                "已保存当前问卷进度，并根据已有信息生成建档结果。",
                null,
                Map.of("status", "exited", "trigger_reason", triggerReason)
            );
        }
        generateGuidedResultForStudent(studentId, sessionId, triggerReason, true);
        return getSessionBundleForStudent(studentId, sessionId);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    public Map<String, Object> generateGuidedResult(
        String authorizationHeader,
        String sessionId,
        String triggerReason
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        return generateGuidedResultForStudent(studentId, sessionId, triggerReason, true);
    }

    private Map<String, Object> getSessionBundleForStudent(String studentId, String sessionId) {
        GuidedSessionRow session = requireOwnedSession(studentId, sessionId);
        Map<String, Map<String, Object>> answers = loadAnswers(sessionId);
        List<Map<String, Object>> visibleQuestions = serializeVisibleQuestions(answers);
        Map<String, Map<String, Object>> serializedByCode = new LinkedHashMap<>();
        for (Map<String, Object> question : visibleQuestions) {
            serializedByCode.put(string(question.get("code")), question);
        }

        Map<String, Object> currentQuestion = null;
        if (session.currentQuestionCode() != null && serializedByCode.containsKey(session.currentQuestionCode())) {
            currentQuestion = serializedByCode.get(session.currentQuestionCode());
        }
        if (currentQuestion == null && "active".equals(session.sessionStatus())) {
            Map<String, Object> nextQuestion = findNextQuestion(answers);
            if (nextQuestion != null) {
                currentQuestion = serializedByCode.get(string(nextQuestion.get("code")));
            }
        }

        return bundle(
            serializeSession(session),
            loadMessages(sessionId),
            loadAnswerRows(sessionId),
            currentQuestion,
            visibleQuestions,
            loadLatestResult(studentId, sessionId)
        );
    }

    private GuidedSessionRow createSession(String studentId) {
        String sessionId = UUID.randomUUID().toString();
        Map<String, Object> firstQuestion = QUESTIONS.get(0);
        guidedMapper.insertSession(mapOfEntries(
            "sessionId",
            sessionId,
            "studentId",
            studentId,
            "questionnaireCode",
            QUESTIONNAIRE_CODE,
            "questionnaireVersion",
            QUESTIONNAIRE_VERSION,
            "currentQuestionCode",
            string(firstQuestion.get("code")),
            "currentQuestionIndex",
            0
        ));
        insertMessage(
            sessionId,
            studentId,
            "assistant",
            "question",
            string(firstQuestion.get("title")),
            string(firstQuestion.get("code")),
            serializeQuestion(firstQuestion, Map.of(), 1, visibleQuestions(Map.of()).size())
        );
        return requireOwnedSession(studentId, sessionId);
    }

    private Map<String, Object> generateGuidedResultForStudent(
        String studentId,
        String sessionId,
        String triggerReason,
        boolean persistArchiveSnapshot
    ) {
        GuidedSessionRow session = requireOwnedSession(studentId, sessionId);
        Map<String, Object> latestResult = loadLatestResult(studentId, sessionId);
        if (latestResult != null && session.versionNo() <= session.lastGeneratedVersionNo()) {
            return latestResult;
        }

        Map<String, Map<String, Object>> answers = loadAnswers(sessionId);
        Map<String, Object> profileJson = buildProfileJson(studentId, sessionId, answers);
        Map<String, Object> dbPayload = new LinkedHashMap<>(profileJson);
        Map<String, Object> scoringJson = runAiScoring(studentId, sessionId, profileJson, answers);
        Map<String, Object> radarScores = applyGuidedRadarScoreRules(
            normalizeRadarScores(
                firstNonNull(scoringJson.get("radar_scores_json"), scoringJson.get("radar_scores")),
                fallbackRadarScores(answers)
            ),
            answers
        );
        String summaryText = StrUtil.blankToDefault(
            normalizeRadarCopy(string(scoringJson.get("summary_text")).trim()),
            "固定问卷建档结果已生成。"
        );
        String resultStatus = "saved";
        String saveErrorMessage = null;
        if (persistArchiveSnapshot) {
            try {
                guidedArchivePersistenceService.persistGuidedArchiveFormSnapshot(dbPayload, studentId);
            } catch (Exception exception) {
                resultStatus = "failed";
                saveErrorMessage = rootCauseMessage(exception);
                log.warn("Failed to persist guided archive snapshot, sessionId={}, studentId={}", sessionId, studentId, exception);
            }
        }

        guidedMapper.upsertResult(mapOfEntries(
            "sessionId",
            sessionId,
            "studentId",
            studentId,
            "sourceVersionNo",
            session.versionNo(),
            "resultStatus",
            resultStatus,
            "triggerReason",
            triggerReason,
            "profileJson",
            toJson(profileJson),
            "dbPayloadJson",
            toJson(dbPayload),
            "radarScoresJson",
            toJson(radarScores),
            "summaryText",
            summaryText,
            "saveErrorMessage",
            saveErrorMessage
        ));
        guidedMapper.updateSessionGenerationStage(mapOfEntries(
            "sessionId",
            sessionId,
            "currentStage",
            "saved".equals(resultStatus) ? "generated" : "failed",
            "lastGeneratedVersionNo",
            session.versionNo()
        ));
        return loadLatestResult(studentId, sessionId);
    }

    private Map<String, Object> buildProfileJson(
        String studentId,
        String sessionId,
        Map<String, Map<String, Object>> answers
    ) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("student_id", studentId);
        profile.put("session_id", sessionId);
        profile.put("questionnaire_code", QUESTIONNAIRE_CODE);
        profile.put("questionnaire_version", QUESTIONNAIRE_VERSION);
        profile.put("answers", answers);

        String currentGrade = selectedValue(answers.get("Q1"));
        String targetEntryTerm = selectedValue(answers.get("Q2"));
        List<String> targetCountries = selectedValues(answers.get("Q3"));
        List<String> targetMajors = selectedValues(answers.get("Q4"));
        String curriculum = selectedValue(answers.get("Q5"));
        String languageType = selectedValue(answers.get("Q9"));
        List<String> standardizedTests = selectedStandardizedTestsInOrder(answers);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("current_grade", currentGrade);
        summary.put("target_entry_term", targetEntryTerm);
        summary.put("target_countries", targetCountries);
        summary.put("target_majors", targetMajors);
        summary.put("curriculum_system", curriculum);
        summary.put("language_test_type", languageType);
        summary.put("standardized_tests", standardizedTests);
        profile.put("summary", summary);

        profile.put(
            "student_basic_info",
            mapOfEntries(
                "student_id",
                studentId,
                "schema_version",
                "guided_v1",
                "profile_type",
                "guided_profile",
                "current_grade",
                currentGrade,
                "target_entry_term",
                optionLabelForQuestion("Q2", targetEntryTerm),
                "CTRY_CODE_VAL",
                targetCountries.isEmpty() ? null : targetCountries.get(0),
                "MAJ_CODE_VAL",
                primaryMajorCode(targetMajors),
                "MAJ_INTEREST_TEXT",
                targetMajors.isEmpty() ? null : optionLabelForQuestion("Q4", targetMajors.get(0))
            )
        );
        profile.put("student_basic_info_curriculum_system", curriculumRows(studentId, curriculum));
        profile.put("student_basic_info_target_country_entries", targetCountryRows(studentId, sessionId, targetCountries));
        profile.put("student_basic_info_target_major_entries", targetMajorRows(studentId, sessionId, targetMajors));

        Map<String, Object> academic = mapOfEntries(
            "student_id",
            studentId,
            "grade_size",
            gradeSize(answers.get("Q7")),
            "rank_percentile",
            rankPercentile(selectedValue(answers.get("Q6"))),
            "rank_scope_code",
            "GRADE",
            "rank_evidence_level_code",
            "SELF_REPORTED",
            "rank_evidence_notes",
            optionLabelForQuestion("Q6", selectedValue(answers.get("Q6")))
        );
        profile.put("student_academic", academic);

        putAcademicProfiles(profile, studentId, curriculum, answers.get("Q8"));
        profile.putAll(buildLanguageProfile(studentId, languageType, answers.get("Q9_SCORE"), null));
        Map<String, Object> standardizedPayload = buildStandardizedProfile(studentId, new LinkedHashSet<>(standardizedTests), answers, selectedValue(answers.get("Q12")));
        profile.putAll(standardizedPayload);
        profile.putAll(buildExperienceProfile(studentId, currentGrade, answers));
        return profile;
    }

    private Map<String, Object> runAiScoring(
        String studentId,
        String sessionId,
        Map<String, Object> profileJson,
        Map<String, Map<String, Object>> answers
    ) {
        try {
            AiPromptRuntimeService.PromptRuntimeResult runtimeResult = aiPromptRuntimeService.executePrompt(
                SCORING_PROMPT_KEY,
                mapOfEntries(
                    "student_id",
                    studentId,
                    "session_id",
                    sessionId,
                    "profile_json",
                    profileJson,
                    "answers",
                    answers
                )
            );
            return aiPromptRuntimeService.parseJsonObject(runtimeResult.content(), "guided_profile_scoring");
        } catch (Exception exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("radar_scores_json", fallbackRadarScores(answers));
            fallback.put("summary_text", "系统已根据固定问卷答案生成当前建档结果。后续可继续补充更细信息以提升评估准确度。");
            return fallback;
        }
    }

    private void putAcademicProfiles(
        Map<String, Object> profile,
        String studentId,
        String curriculum,
        Map<String, Object> q8
    ) {
        profile.put("student_academic_a_level_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_ap_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_ib_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_chinese_high_school_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_us_high_school_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_ossd_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_other_curriculum_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_curriculum_gpa", new ArrayList<Map<String, Object>>());

        Map<String, Object> answer = q8 == null ? Map.of() : q8;
        List<Map<String, Object>> curriculumGpaRows = curriculumGpaRows(studentId, curriculum, answer);
        if (!curriculumGpaRows.isEmpty()) {
            profile.put("student_academic_curriculum_gpa", curriculumGpaRows);
        }
    }

    private List<Map<String, Object>> curriculumGpaRows(
        String studentId,
        String curriculum,
        Map<String, Object> answer
    ) {
        if (StrUtil.isBlank(curriculum) || answer == null || answer.isEmpty()) {
            return List.of();
        }

        BigDecimal gpaValue = decimalValue(answer.get("gpa"));
        String gpaScaleCode = inferGpaScaleCode(firstNonNull(answer.get("gpa_scale_code"), answer.get("gpa_scale")));

        if (gpaValue == null && StrUtil.isBlank(gpaScaleCode)) {
            return List.of();
        }

        return List.of(
            mapOfEntries(
                "student_id",
                studentId,
                "curriculum_system_code",
                curriculum,
                "gpa_scope_code",
                inferGpaScopeCode(selectedText(answer, "is_weighted")),
                "gpa_value",
                gpaValue,
                "gpa_scale_code",
                gpaScaleCode,
                "weighting_rule_notes",
                nullableString(answer.get("strong_courses"))
            )
        );
    }

    private String inferGpaScopeCode(String weightedValue) {
        if ("yes".equalsIgnoreCase(StrUtil.nullToEmpty(weightedValue))) {
            return "WEIGHTED";
        }
        if ("no".equalsIgnoreCase(StrUtil.nullToEmpty(weightedValue))) {
            return "UNWEIGHTED";
        }
        return "REPORTED";
    }

    private String inferGpaScaleCode(Object rawScale) {
        String scaleCode = nullableString(rawScale);
        if (scaleCode != null) {
            String normalized = scaleCode.trim().toUpperCase();
            if (Set.of("GPA_4_0", "GPA_4_3", "GPA_5_0", "GPA_7_0", "PERCENT_100", "OTHER").contains(normalized)) {
                return normalized;
            }
        }
        BigDecimal scale = decimalValue(rawScale);
        if (scale == null) {
            return null;
        }
        if (BigDecimal.valueOf(4.0).compareTo(scale) == 0) {
            return "GPA_4_0";
        }
        if (BigDecimal.valueOf(4.3).compareTo(scale) == 0) {
            return "GPA_4_3";
        }
        if (BigDecimal.valueOf(5.0).compareTo(scale) == 0) {
            return "GPA_5_0";
        }
        if (BigDecimal.valueOf(7.0).compareTo(scale) == 0) {
            return "GPA_7_0";
        }
        if (BigDecimal.valueOf(100.0).compareTo(scale) == 0) {
            return "PERCENT_100";
        }
        return "OTHER";
    }

    private Map<String, Object> buildLanguageProfile(
        String studentId,
        String languageType,
        Map<String, Object> q10,
        String q11
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> recordRows = new ArrayList<>();
        List<Map<String, Object>> scoreItemRows = new ArrayList<>();
        long tempRecordId = 1L;

        if (languageType != null && !"NO_SCORE".equals(languageType) && q10 != null && hasAnyNonBlank(q10)) {
            Map<String, Object> recordRow = mapOfEntries(
                "student_language_test_record_id",
                tempRecordId,
                "student_id",
                studentId,
                "test_type_code",
                languageType,
                "status_code",
                StrUtil.blankToDefault(selectedText(q10, "status_code"), "SCORED"),
                "test_date",
                nullableString(q10.get("test_date")),
                "exam_name_text",
                defaultLanguageExamName(languageType, q10),
                "total_score",
                decimalValue(firstNonNull(q10.get("overall_score"), q10.get("total_score"))),
                "score_scale_text",
                defaultLanguageScaleText(languageType, q10),
                "cefr_level_code",
                nullableString(q10.get("cefr_level_code")),
                "evidence_level_code",
                StrUtil.blankToDefault(nullableString(q10.get("evidence_level_code")), "SELF_REPORTED"),
                "is_best_score",
                1,
                "notes",
                nullableString(q10.get("notes"))
            );
            recordRows.add(recordRow);
            scoreItemRows.addAll(languageScoreItemRows(tempRecordId, q10, languageType));
        } else if ("NO_SCORE".equals(languageType)) {
            Map<String, Object> estimatedRecord = estimatedLanguageRecord(studentId, q11, tempRecordId);
            if (estimatedRecord != null) {
                recordRows.add(estimatedRecord);
            }
        }

        payload.put("student_language_test_record", recordRows);
        payload.put("student_language_test_score_item", scoreItemRows);
        return payload;
    }

    private Map<String, Object> buildStandardizedProfile(
        String studentId,
        Set<String> selectedTests,
        Map<String, Map<String, Object>> answers,
        String estimatedValue
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("student_academic_a_level_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_ap_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_ib_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_chinese_high_school_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_us_high_school_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_ossd_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_academic_other_curriculum_subject", new ArrayList<Map<String, Object>>());
        payload.put("student_standardized_sat", new ArrayList<Map<String, Object>>());
        payload.put("student_standardized_act", new ArrayList<Map<String, Object>>());

        if (selectedTests.contains("A_LEVEL")) {
            payload.put("student_academic_a_level_subject", aLevelSubjectRows(studentId, answers.get("Q11_A_LEVEL"), "FULL_A_LEVEL"));
        }
        if (selectedTests.contains("IB")) {
            payload.put("student_academic_ib_subject", ibSubjectRows(studentId, answers.get("Q11_IB")));
        }
        if (selectedTests.contains("CHINESE_HIGH_SCHOOL")) {
            payload.put("student_academic_chinese_high_school_subject", chineseHighSchoolSubjectRows(studentId, answers.get("Q11_CHINESE_HIGH_SCHOOL")));
        }
        if (selectedTests.contains("OSSD")) {
            payload.put("student_academic_ossd_subject", ossdSubjectRows(studentId, answers.get("Q11_OSSD")));
        }
        if (selectedTests.contains("OTHER")) {
            payload.put("student_academic_other_curriculum_subject", otherCurriculumSubjectRows(studentId, answers.get("Q11_OTHER")));
        }
        if (selectedTests.contains("AP")) {
            payload.put("student_academic_ap_subject", apSubjectRows(studentId, answers.get("Q11_AP")));
        }

        if (selectedTests.contains("SAT")) {
            Map<String, Object> sat = answers.get("Q11_SAT");
            if (sat == null) {
                sat = Map.of();
            }
            Map<String, Object> record = mapOfEntries(
                "student_id",
                studentId,
                "status_code",
                StrUtil.blankToDefault(selectedText(sat, "status_code"), hasAnyValue(sat, "total_score", "sat_erw", "sat_math") ? "SCORED" : "PLANNED"),
                "test_date",
                nullableString(sat.get("test_date")),
                "total_score",
                nullableInt(sat.get("total_score")),
                "sat_erw",
                nullableInt(sat.get("sat_erw")),
                "sat_math",
                nullableInt(sat.get("sat_math")),
                "is_best_score",
                1
            );
            if (hasAnyNonBlank(record)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> satRows = (List<Map<String, Object>>) payload.get("student_standardized_sat");
                satRows.add(record);
            }
        }
        if (selectedTests.contains("ACT")) {
            Map<String, Object> act = answers.get("Q11_ACT");
            if (act == null) {
                act = Map.of();
            }
            Map<String, Object> record = mapOfEntries(
                "student_id",
                studentId,
                "status_code",
                StrUtil.blankToDefault(selectedText(act, "status_code"), hasAnyValue(act, "total_score", "act_english", "act_math", "act_reading", "act_science") ? "SCORED" : "PLANNED"),
                "test_date",
                nullableString(act.get("test_date")),
                "total_score",
                nullableInt(act.get("total_score")),
                "act_english",
                nullableInt(act.get("act_english")),
                "act_math",
                nullableInt(act.get("act_math")),
                "act_reading",
                nullableInt(act.get("act_reading")),
                "act_science",
                nullableInt(act.get("act_science")),
                "is_best_score",
                1
            );
            if (hasAnyNonBlank(record)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> actRows = (List<Map<String, Object>>) payload.get("student_standardized_act");
                actRows.add(record);
            }
        }
        if (estimatedValue != null) {
            appendEstimatedStandardizedRows(payload, studentId, estimatedValue);
        }
        return payload;
    }

    private Map<String, Object> buildExperienceProfile(
        String studentId,
        String currentGrade,
        Map<String, Map<String, Object>> answers
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean universityGrade = isUniversityGrade(currentGrade);
        payload.put(
            "student_activity_experience",
            universityGrade ? new ArrayList<Map<String, Object>>() : activityExperienceRows(studentId, answers.get("Q13"))
        );
        payload.put(
            "student_enterprise_internship",
            universityGrade ? enterpriseInternshipRows(studentId, answers.get("Q13")) : new ArrayList<Map<String, Object>>()
        );
        payload.put("student_research_experience", researchExperienceRows(studentId, answers.get("Q14")));
        payload.put("student_competition_record", competitionRows(studentId, answers.get("Q15")));
        return payload;
    }

    private Map<String, Object> normalizeRadarScores(Object rawScores, Map<String, Object> fallbackScores) {
        Map<String, Object> source = rawScores instanceof Map<?, ?> rawMap ? toStringKeyMap(rawMap) : new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String dimension : List.of("academic", "language", "standardized", "competition", "activity", "project")) {
            Object rawValue = source.get(dimension);
            Map<String, Object> fallback = fallbackScores.get(dimension) instanceof Map<?, ?> map
                ? toStringKeyMap(map)
                : scoreReason(50, "当前信息还不够完整。建议继续补充关键经历和证明材料，让画像更准确。");
            int score;
            String reason;
            if (rawValue instanceof Number number) {
                score = number.intValue();
                reason = normalizeRadarCopy(string(fallback.get("reason")));
            } else if (rawValue instanceof Map<?, ?> map) {
                Map<String, Object> scoreMap = toStringKeyMap(map);
                score = intValue(firstNonNull(scoreMap.get("score"), fallback.get("score")));
                reason = normalizeRadarCopy(
                    StrUtil.blankToDefault(string(scoreMap.get("reason")).trim(), string(fallback.get("reason")))
                );
            } else {
                score = intValue(fallback.get("score"));
                reason = normalizeRadarCopy(string(fallback.get("reason")));
            }
            result.put(dimension, scoreReason(Math.max(0, Math.min(100, score)), reason));
        }
        return result;
    }

    private Map<String, Object> applyGuidedRadarScoreRules(
        Map<String, Object> radarScores,
        Map<String, Map<String, Object>> answers
    ) {
        Map<String, Object> result = new LinkedHashMap<>(radarScores);
        for (String dimension : List.of("academic", "language", "standardized", "competition", "activity", "project")) {
            Object rawValue = result.get(dimension);
            if (!(rawValue instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> scoreMap = toStringKeyMap(map);
            int score = Math.max(0, Math.min(100, intValue(scoreMap.get("score"))));
            String reason = normalizeRadarCopy(string(scoreMap.get("reason")));
            result.put(
                dimension,
                scoreReason(
                    score,
                    shouldReplaceRadarReason(reason) ? guidedRadarFeedbackForDimension(dimension, answers) : reason
                )
            );
        }
        result.put("project", guidedResearchRadarScore(answers.get("Q14")));
        result.put("activity", guidedActivityRadarScore(answers.get("Q13"), result.get("activity")));
        return result;
    }

    private boolean shouldReplaceRadarReason(String reason) {
        if (StrUtil.isBlank(reason)) {
            return true;
        }
        return reason.contains("评分")
            || reason.contains("计分")
            || reason.contains("完整度")
            || reason.contains("每项")
            || reason.contains("满分")
            || reason.contains("兜底")
            || reason.contains("基于")
            || reason.contains("不低于")
            || reason.contains("不得低于");
    }

    private String guidedRadarFeedbackForDimension(String dimension, Map<String, Map<String, Object>> answers) {
        return switch (dimension) {
            case "academic" -> "学术信息已经有基础。建议继续补充课程体系、校内排名、核心科目成绩和优势课程，让学术能力更完整地呈现。";
            case "language" -> "语言能力还可以通过更完整的考试信息来强化。建议补充考试日期、总分、分项成绩和成绩状态，方便判断下一步提分重点。";
            case "standardized" -> "标化和课程科目成绩还可以继续完善。建议补充 SAT/ACT 或课程体系科目成绩，并标清考试状态和具体分项，提升成绩画像的可信度。";
            case "competition" -> guidedCompetitionRadarFeedback(answers.get("Q15"));
            case "activity" -> guidedActivityRadarFeedback(answers.get("Q13"), hasGuidedActivityInternshipContent(answers.get("Q13")));
            case "project" -> guidedResearchRadarFeedback(answers.get("Q14"));
            default -> "当前信息还不够完整。建议继续补充关键经历和证明材料，让画像更准确。";
        };
    }

    private String guidedCompetitionRadarFeedback(Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return "目前代表性竞赛经历还不够突出。建议选择与目标专业相关的竞赛方向，先积累一段能体现兴趣和能力的成果。";
        }
        List<String> missingFields = new ArrayList<>();
        if (!hasNonBlankValue(answer.get("competition_name"))) {
            missingFields.add("竞赛名称");
        }
        if (!hasNonBlankValue(answer.get("competition_field"))) {
            missingFields.add("竞赛领域");
        }
        if (!hasNonBlankValue(answer.get("competition_level"))) {
            missingFields.add("竞赛级别");
        }
        if (!hasNonBlankValue(answer.get("participants_text"))) {
            missingFields.add("参赛规模");
        }
        if (!hasNonBlankValue(answer.get("result_text"))) {
            missingFields.add("成绩结果");
        }
        if (!hasNonBlankValue(answer.get("competition_year"))) {
            missingFields.add("参赛年份");
        }
        if (missingFields.isEmpty()) {
            return "竞赛经历信息已经比较完整。建议继续补充证书、排名证明或作品材料，让竞赛成果更可信、更有区分度。";
        }
        return "竞赛经历还可以补充" + String.join("、", missingFields)
            + "。建议把竞赛含金量、参与规模和最终结果写清楚，突出你的学术兴趣和竞争力。";
    }

    private Map<String, Object> guidedResearchRadarScore(Map<String, Object> answer) {
        int filledCount = countFilledFields(answer, List.of("has_experience", "research_summary", "initiator_name", "role_name"));
        int score = Math.min(100, filledCount * 30);
        return scoreReason(score, guidedResearchRadarFeedback(answer));
    }

    private Map<String, Object> guidedActivityRadarScore(Map<String, Object> answer, Object existingScore) {
        Map<String, Object> existing = existingScore instanceof Map<?, ?> map ? toStringKeyMap(map) : new LinkedHashMap<>();
        int score = intValue(existing.get("score"));
        boolean hasMeaningfulExperience = hasGuidedActivityInternshipContent(answer);
        if (hasMeaningfulExperience && score < 60) {
            score = 60;
        }
        return scoreReason(Math.max(0, Math.min(100, score)), guidedActivityRadarFeedback(answer, hasMeaningfulExperience));
    }

    private String guidedResearchRadarFeedback(Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return "目前还缺少科研经历。建议从校内课题、导师项目、线上科研或独立研究入手，先积累一个清晰的问题、方法和成果。";
        }
        List<String> missingFields = new ArrayList<>();
        if (!hasNonBlankValue(answer.get("research_summary"))) {
            missingFields.add("研究内容");
        }
        if (!hasNonBlankValue(answer.get("initiator_name"))) {
            missingFields.add("发起方");
        }
        if (!hasNonBlankValue(answer.get("role_name"))) {
            missingFields.add("担任角色");
        }
        if (missingFields.isEmpty()) {
            return "科研经历基础信息已经比较完整。建议继续补充课题成果、导师或机构背书、论文/展示/报告等材料，让学术潜力更有说服力。";
        }
        return "科研经历还可以继续完善" + String.join("、", missingFields)
            + "。建议把研究问题、你的具体贡献和可证明成果写清楚，这会让申请材料更有力量。";
    }

    private String guidedActivityRadarFeedback(Map<String, Object> answer, boolean hasMeaningfulExperience) {
        if (!hasMeaningfulExperience) {
            return "目前还缺少活动或企业实习经历。建议优先补充一段有持续时间、具体职责和推荐人的经历，突出主动性和真实投入。";
        }
        List<String> missingFields = new ArrayList<>();
        if (!hasNonBlankValue(answer.get("activity_summary")) && !hasNonBlankValue(answer.get("company_name"))) {
            missingFields.add("经历内容或企业名称");
        }
        if (!hasNonBlankValue(answer.get("start_time")) || !hasNonBlankValue(answer.get("end_time"))) {
            missingFields.add("起止时间");
        }
        if (!hasNonBlankValue(answer.get("position_name"))) {
            missingFields.add("岗位或职责");
        }
        if (!hasNonBlankValue(answer.get("referrer_name"))) {
            missingFields.add("推荐人");
        }
        if (missingFields.isEmpty()) {
            return "活动/企业实习经历已经有基础信息。建议继续补充可量化影响、团队角色、产出材料或推荐人证明，让经历更有说服力。";
        }
        return "活动/企业实习经历还可以补充" + String.join("、", missingFields)
            + "。建议把你承担的任务、影响范围和可验证成果写具体，能更好体现成长和行动力。";
    }

    private boolean hasGuidedActivityInternshipContent(Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return false;
        }
        return countFilledFields(
            answer,
            List.of("activity_summary", "start_time", "end_time", "company_name", "position_name", "referrer_name")
        ) > 0;
    }

    private int countFilledFields(Map<String, Object> answer, List<String> fieldNames) {
        if (answer == null) {
            return 0;
        }
        int count = 0;
        for (String fieldName : fieldNames) {
            if (hasNonBlankValue(answer.get(fieldName))) {
                count += 1;
            }
        }
        return count;
    }

    private boolean hasNonBlankValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return !stringValue.trim().isBlank();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
    }

    private String normalizeRadarCopy(String text) {
        if (text == null) {
            return null;
        }
        return text
            .replace("活动领导力", "活动/企业实习")
            .replace("活动 / 企业实习", "活动/企业实习")
            .replace("项目实践", "科研经历");
    }

    private void mergeNestedMap(Map<String, Object> target, Map<String, Object> source, String key) {
        Object update = source.remove(key);
        if (!(update instanceof Map<?, ?> updateMap) || updateMap.isEmpty()) {
            return;
        }
        Map<String, Object> existing = target.get(key) instanceof Map<?, ?> existingMap
            ? toStringKeyMap(existingMap)
            : new LinkedHashMap<>();
        existing.putAll(toStringKeyMap(updateMap));
        target.put(key, existing);
    }

    private List<Map<String, Object>> curriculumRows(String studentId, String curriculum) {
        String curriculumCode = curriculumDbCode(curriculum);
        if (curriculumCode == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
            List.of(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "curriculum_system_code",
                    curriculumCode,
                    "is_primary",
                    1,
                    "notes",
                    optionLabelForQuestion("Q5", curriculum)
                )
            )
        );
    }

    private List<Map<String, Object>> targetCountryRows(String studentId, String sessionId, List<String> countries) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < countries.size(); index++) {
            String countryCode = countries.get(index);
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "country_code",
                    countryCode,
                    "sort_order",
                    index + 1,
                    "is_primary",
                    index == 0 ? 1 : 0,
                    "source_flow",
                    "guided_profile",
                    "source_session_id",
                    sessionId,
                    "remark",
                    optionLabelForQuestion("Q3", countryCode)
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> targetMajorRows(String studentId, String sessionId, List<String> majors) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < majors.size(); index++) {
            String majorCode = majors.get(index);
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "major_direction_code",
                    majorCode,
                    "major_direction_label",
                    optionLabelForQuestion("Q4", majorCode),
                    "major_code",
                    Set.of("UNDECIDED", "OTHER", "OTHER_CUSTOM").contains(majorCode) ? null : majorCode,
                    "sort_order",
                    index + 1,
                    "is_primary",
                    index == 0 ? 1 : 0,
                    "source_flow",
                    "guided_profile",
                    "source_session_id",
                    sessionId
                )
            );
        }
        return rows;
    }

    private String primaryMajorCode(List<String> majors) {
        if (majors.isEmpty()) {
            return null;
        }
        String value = majors.get(0);
        return Set.of("UNDECIDED", "OTHER", "OTHER_CUSTOM").contains(value) ? null : value;
    }

    private String curriculumDbCode(String curriculum) {
        if (curriculum == null) {
            return null;
        }
        return "INTERNATIONAL_OTHER".equals(curriculum) ? "OTHER" : curriculum;
    }

    private Integer gradeSize(Map<String, Object> q7) {
        String value = selectedValue(q7);
        if ("LT_50".equals(value) || "AROUND_50".equals(value)) {
            return 50;
        }
        if ("50_100".equals(value) || "AROUND_100".equals(value)) {
            return 100;
        }
        if ("100_200".equals(value) || "AROUND_200".equals(value)) {
            return 200;
        }
        if ("200_500".equals(value) || "AROUND_500".equals(value)) {
            return 500;
        }
        if ("AROUND_1000".equals(value)) {
            return 1000;
        }
        if ("GT_500".equals(value)) {
            return 501;
        }
        Integer custom = nullableInt(q7 == null ? null : q7.get("custom_text"));
        return custom == null || custom <= 0 ? null : custom;
    }

    private Integer rankPercentile(String rankValue) {
        if (rankValue == null) {
            return null;
        }
        return switch (rankValue) {
            case "TOP_1" -> 1;
            case "TOP_5" -> 5;
            case "TOP_10" -> 10;
            case "TOP_20" -> 20;
            case "TOP_30" -> 30;
            case "UPPER_MIDDLE" -> 40;
            case "MIDDLE" -> 50;
            default -> nullableInt(rankValue);
        };
    }

    private List<Map<String, Object>> aLevelSubjectRows(String studentId, Map<String, Object> answer, String defaultStage) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subject = normalizeALevelSubjectId(row.get("subject"));
            if (subject == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "al_subject_id",
                    subject,
                    "stage_code",
                    defaultStage,
                    "grade_code",
                    nullableString(row.get("grade")),
                    "is_predicted",
                    isPredictedStatus(row.get("status")),
                    "board_code",
                    nullableString(answer == null ? null : answer.get("a_level_board"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> ibSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subject = normalizeIbSubjectId(row.get("subject"));
            if (subject == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "ib_subject_id",
                    subject,
                    "level_code",
                    nullableString(row.get("level")),
                    "score",
                    nullableInt(row.get("score")),
                    "is_predicted",
                    isPredictedStatus(row.get("status"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> chineseHighSchoolSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subject = nullableString(row.get("subject"));
            if (subject == null) {
                continue;
            }
            BigDecimal scoreNumeric = decimalValue(row.get("score_numeric"));
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "chs_subject_id",
                    subject,
                    "score_text",
                    nullableString(row.get("score_numeric")),
                    "score_numeric",
                    scoreNumeric,
                    "score_scale_code",
                    "PERCENT_100"
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> ossdSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String courseName = nullableString(row.get("course_name_text"));
            if (courseName == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "course_name_text",
                    courseName,
                    "course_level_code",
                    nullableString(row.get("course_level_code")),
                    "score_numeric",
                    decimalValue(row.get("score_numeric")),
                    "credit_earned",
                    decimalValue(row.get("credit_earned"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> otherCurriculumSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subjectName = nullableString(row.get("subject_name_text"));
            if (subjectName == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "curriculum_system_code",
                    "OTHER",
                    "subject_name_text",
                    subjectName,
                    "subject_level_text",
                    nullableString(row.get("subject_level_text")),
                    "score_text",
                    nullableString(row.get("score_text")),
                    "score_numeric",
                    decimalValue(row.get("score_numeric")),
                    "score_scale_code",
                    nullableString(row.get("score_scale_code"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> apSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String courseId = nullableString(row.get("ap_course_id"));
            if (courseId == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "ap_course_id",
                    courseId,
                    "score",
                    nullableInt(row.get("score")),
                    "year_taken",
                    nullableInt(row.get("year_taken"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> usHighSchoolSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String courseName = nullableString(row.get("course_name_text"));
            if (courseName == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "school_year_label",
                    nullableString(row.get("school_year_label")),
                    "term_code",
                    nullableString(row.get("term_code")),
                    "course_name_text",
                    courseName,
                    "course_level_code",
                    nullableString(row.get("course_level_code")),
                    "grade_letter_code",
                    nullableString(row.get("grade_letter_code")),
                    "grade_percent",
                    decimalValue(row.get("grade_percent")),
                    "credit_earned",
                    decimalValue(row.get("credit_earned"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> activityExperienceRows(String studentId, Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
            List.of(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "activity_summary",
                    nullableString(answer.get("activity_summary")),
                    "referrer_name",
                    nullableString(answer.get("referrer_name")),
                    "start_time",
                    nullableString(answer.get("start_time")),
                    "end_time",
                    nullableString(answer.get("end_time"))
                )
            )
        );
    }

    private List<Map<String, Object>> enterpriseInternshipRows(String studentId, Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
            List.of(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "start_time",
                    nullableString(answer.get("start_time")),
                    "end_time",
                    nullableString(answer.get("end_time")),
                    "company_name",
                    nullableString(answer.get("company_name")),
                    "position_name",
                    nullableString(answer.get("position_name")),
                    "referrer_name",
                    nullableString(answer.get("referrer_name"))
                )
            )
        );
    }

    private List<Map<String, Object>> researchExperienceRows(String studentId, Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
            List.of(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "research_summary",
                    nullableString(answer.get("research_summary")),
                    "initiator_name",
                    nullableString(answer.get("initiator_name")),
                    "role_name",
                    nullableString(answer.get("role_name"))
                )
            )
        );
    }

    private List<Map<String, Object>> competitionRows(String studentId, Map<String, Object> answer) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
            List.of(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "competition_name",
                    nullableString(answer.get("competition_name")),
                    "competition_field",
                    nullableString(answer.get("competition_field")),
                    "competition_level",
                    nullableString(answer.get("competition_level")),
                    "participants_text",
                    nullableString(answer.get("participants_text")),
                    "result_text",
                    nullableString(answer.get("result_text")),
                    "competition_year",
                    nullableInt(answer.get("competition_year"))
                )
            )
        );
    }

    private String normalizeALevelSubjectId(Object rawSubject) {
        String value = nullableString(rawSubject);
        if (value == null) {
            return null;
        }
        return value.startsWith("AL_") ? value : A_LEVEL_SUBJECT_ID_MAPPING.get(value);
    }

    private String normalizeIbSubjectId(Object rawSubject) {
        String value = nullableString(rawSubject);
        if (value == null) {
            return null;
        }
        return value.startsWith("IB_") ? value : IB_SUBJECT_ID_MAPPING.get(value);
    }

    private List<Map<String, Object>> repeatableRows(Map<String, Object> answer) {
        Object rowsObject = answer == null ? null : answer.get("rows");
        if (!(rowsObject instanceof List<?> rows)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                result.add(toStringKeyMap(map));
            }
        }
        return result;
    }

    private int isPredictedStatus(Object status) {
        return "SCORED".equals(string(status).trim()) ? 0 : 1;
    }

    private boolean hasAnyNonBlank(Map<String, Object> source) {
        if (source == null) {
            return false;
        }
        Set<String> ignored = Set.of("student_id", "is_best_score", "skipped", "test_type");
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (ignored.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null || Boolean.FALSE.equals(value)) {
                continue;
            }
            if (!string(value).trim().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void appendEstimatedStandardizedRows(
        Map<String, Object> payload,
        String studentId,
        String estimatedValue
    ) {
        if (estimatedValue == null) {
            return;
        }
        if (estimatedValue.startsWith("SAT")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> satRows = (List<Map<String, Object>>) payload.get("student_standardized_sat");
            if (satRows.isEmpty()) {
                satRows.add(
                    mapOfEntries(
                        "student_id",
                        studentId,
                        "status_code",
                        "ESTIMATED",
                        "total_score",
                        estimatedStandardizedScore(estimatedValue),
                        "is_best_score",
                        1,
                        "notes",
                        "首页标准问卷预估成绩"
                    )
                );
            }
            return;
        }
        if (estimatedValue.startsWith("ACT")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> actRows = (List<Map<String, Object>>) payload.get("student_standardized_act");
            if (actRows.isEmpty()) {
                actRows.add(
                    mapOfEntries(
                        "student_id",
                        studentId,
                        "status_code",
                        "ESTIMATED",
                        "total_score",
                        estimatedStandardizedScore(estimatedValue),
                        "is_best_score",
                        1,
                        "notes",
                        "首页标准问卷预估成绩"
                    )
                );
            }
        }
    }

    private Integer estimatedStandardizedScore(String value) {
        return switch (string(value)) {
            case "SAT_1550_PLUS" -> 1550;
            case "SAT_1500_1540" -> 1500;
            case "SAT_1450_1490" -> 1450;
            case "SAT_1400_1440" -> 1400;
            case "SAT_1450_1500" -> 1450;
            case "SAT_1500_1550" -> 1500;
            case "ACT_35_36" -> 35;
            case "ACT_33_34" -> 33;
            case "ACT_31_32" -> 31;
            case "ACT_32_33" -> 32;
            case "ACT_34_36" -> 34;
            case "IB_45" -> 45;
            case "IB_42_44" -> 42;
            case "IB_40_41" -> 40;
            case "IB_40_PLUS" -> 40;
            default -> null;
        };
    }

    private Map<String, Object> estimatedLanguageRecord(String studentId, String value, long tempRecordId) {
        BigDecimal estimatedScore = estimatedLanguageScore(value);
        if (estimatedScore == null) {
            return null;
        }
        String testTypeCode = value != null && value.startsWith("TOEFL") ? "TOEFL_IBT" : "IELTS";
        return mapOfEntries(
            "student_language_test_record_id",
            tempRecordId,
            "student_id",
            studentId,
            "test_type_code",
            testTypeCode,
            "status_code",
            "ESTIMATED",
            "total_score",
            estimatedScore,
            "score_scale_text",
            "IELTS".equals(testTypeCode) ? "9.0" : "120",
            "evidence_level_code",
            "ESTIMATED",
            "is_best_score",
            1,
            "notes",
            "首页标准问卷预估语言成绩"
        );
    }

    private BigDecimal estimatedLanguageScore(String value) {
        return switch (string(value)) {
            case "IELTS_6" -> BigDecimal.valueOf(6);
            case "IELTS_6_5" -> BigDecimal.valueOf(6.5);
            case "IELTS_7" -> BigDecimal.valueOf(7);
            case "IELTS_7_5_PLUS" -> BigDecimal.valueOf(7.5);
            case "TOEFL_90" -> BigDecimal.valueOf(90);
            case "TOEFL_100" -> BigDecimal.valueOf(100);
            case "TOEFL_105_PLUS" -> BigDecimal.valueOf(105);
            default -> null;
        };
    }

    private List<Map<String, Object>> languageScoreItemRows(
        long tempRecordId,
        Map<String, Object> answer,
        String languageType
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addLanguageScoreItem(rows, tempRecordId, "READING", answer.get("reading_score"));
        addLanguageScoreItem(rows, tempRecordId, "LISTENING", answer.get("listening_score"));
        addLanguageScoreItem(rows, tempRecordId, "SPEAKING", answer.get("speaking_score"));
        addLanguageScoreItem(rows, tempRecordId, "WRITING", answer.get("writing_score"));
        if ("CAMBRIDGE".equals(languageType)) {
            addLanguageScoreItem(rows, tempRecordId, "USE_OF_ENGLISH", answer.get("use_of_english_score"));
        }
        if ("DET".equals(languageType)) {
            addLanguageScoreItem(rows, tempRecordId, "LITERACY", answer.get("literacy_score"));
            addLanguageScoreItem(rows, tempRecordId, "COMPREHENSION", answer.get("comprehension_score"));
            addLanguageScoreItem(rows, tempRecordId, "CONVERSATION", answer.get("conversation_score"));
            addLanguageScoreItem(rows, tempRecordId, "PRODUCTION", answer.get("production_score"));
        }
        return rows;
    }

    private void addLanguageScoreItem(
        List<Map<String, Object>> rows,
        long tempRecordId,
        String scoreItemCode,
        Object rawScore
    ) {
        BigDecimal score = decimalValue(rawScore);
        if (score == null) {
            return;
        }
        rows.add(
            mapOfEntries(
                "student_language_test_record_id",
                tempRecordId,
                "score_item_code",
                scoreItemCode,
                "score_value",
                score
            )
        );
    }

    private String defaultLanguageExamName(String languageType, Map<String, Object> answer) {
        String explicitExamName = nullableString(answer == null ? null : answer.get("exam_name_text"));
        if (explicitExamName != null) {
            return explicitExamName;
        }
        if ("OTHER".equals(languageType)) {
            return "其他语言考试";
        }
        if ("CAMBRIDGE".equals(languageType)) {
            return "Cambridge English";
        }
        if ("LANGUAGECERT_ACADEMIC".equals(languageType)) {
            return "LanguageCert Academic";
        }
        return null;
    }

    private String defaultLanguageScaleText(String languageType, Map<String, Object> answer) {
        String explicitScale = nullableString(answer == null ? null : answer.get("score_scale_text"));
        if (explicitScale != null) {
            return explicitScale;
        }
        return switch (String.valueOf(languageType)) {
            case "IELTS" -> "9.0";
            case "TOEFL_IBT", "TOEFL_HOME" -> "120";
            case "DET" -> "160";
            case "PTE", "LANGUAGECERT_ACADEMIC", "CAMBRIDGE" -> null;
            default -> null;
        };
    }

    private boolean isUniversityGrade(String currentGrade) {
        if (currentGrade == null) {
            return false;
        }
        return UNIVERSITY_OR_ABOVE_GRADES.contains(currentGrade);
    }

    private String optionLabelForQuestion(String questionCode, String value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> source = QUESTION_BY_CODE.get(questionCode);
        Map<String, Object> question = source == null ? null : copyQuestion(source);
        List<Map<String, String>> options = questionOptions(questionCode);
        if (question != null && options != null) {
            question.put("options", options);
        }
        String label = question == null ? "" : optionLabel(question, value);
        return label.isBlank() ? value : label;
    }

    private Map<String, Object> mapOfEntries(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(string(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StrUtil.blankToDefault(current.getMessage(), throwable.getClass().getSimpleName());
    }

    private Integer nullableInt(Object value) {
        if (value == null || string(value).trim().isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null || string(value).trim().isBlank()) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(string(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Map<String, Object> fallbackRadarScores(Map<String, Map<String, Object>> answers) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
            "academic",
            scoreReason(
                Math.min(100, 35 + 10 * boolInt(answers.containsKey("Q5")) + 15 * boolInt(answers.containsKey("Q6")) + 20 * boolInt(answers.containsKey("Q8"))),
                "学术信息已经有基础。建议继续补充课程体系、校内排名、核心科目成绩和优势课程，让学术能力更完整地呈现。"
            )
        );
        result.put(
            "language",
            scoreReason(
                Math.min(100, 30 + 20 * boolInt(answers.containsKey("Q9")) + 30 * boolInt(answers.containsKey("Q9_SCORE"))),
                "语言能力还可以通过更完整的考试信息来强化。建议补充考试日期、总分、分项成绩和成绩状态，方便判断下一步提分重点。"
            )
        );
        result.put(
            "standardized",
            scoreReason(
                Math.min(100, 30 + 20 * boolInt(answers.containsKey("Q10")) + 30 * boolInt(hasAnyStandardizedScoreAnswer(answers) || answers.containsKey("Q12"))),
                "标化和课程科目成绩还可以继续完善。建议补充 SAT/ACT 或课程体系科目成绩，并标清考试状态和具体分项，提升成绩画像的可信度。"
            )
        );
        result.put(
            "competition",
            scoreReason(
                "yes".equals(selectedText(answers.get("Q15"), "has_experience")) ? 75 : 40,
                "竞赛经历建议突出竞赛领域、级别、参赛规模和结果证明。若目前经历较少，可以先选择与目标专业相关的竞赛积累代表性成果。"
            )
        );
        result.put(
            "activity",
            scoreReason(
                "yes".equals(selectedText(answers.get("Q13"), "has_experience")) ? 75 : 40,
                "活动/企业实习建议写清楚持续时间、具体职责、影响范围和推荐人，让经历更能体现主动性、责任感和成长。"
            )
        );
        result.put(
            "project",
            scoreReason(
                "yes".equals(selectedText(answers.get("Q14"), "has_experience")) ? 75 : 40,
                "科研经历建议补充研究问题、发起方、担任角色和可证明成果。把你的具体贡献写清楚，会更好体现学术潜力。"
            )
        );
        return result;
    }

    private Map<String, Object> scoreReason(int score, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("reason", reason);
        return result;
    }

    private int boolInt(boolean value) {
        return value ? 1 : 0;
    }

    private GuidedSessionRow findLatestSession(String studentId) {
        Map<String, Object> row = guidedMapper.findLatestSession(studentId, QUESTIONNAIRE_CODE);
        return row == null ? null : mapSessionRow(row);
    }

    private GuidedSessionRow requireOwnedSession(String studentId, String sessionId) {
        Map<String, Object> row = guidedMapper.findOwnedSession(studentId, sessionId, QUESTIONNAIRE_CODE);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "guided session not found");
        }
        GuidedSessionRow session = mapSessionRow(row);
        if (isStaleQuestionnaireSession(session.sessionId())) {
            throw new ApiException(HttpStatus.CONFLICT, "当前快速建档会话已过期，请重新打开快速建档");
        }
        return session;
    }

    private boolean isStaleQuestionnaireSession(String sessionId) {
        Map<String, Map<String, Object>> answers = loadAnswers(sessionId);
        Map<String, Object> q10 = answers.get("Q10");
        if (q10 != null && !q10.containsKey("selected_value")) {
            return true;
        }
        Map<String, Object> q12 = answers.get("Q12");
        return q12 != null && q12.containsKey("selected_values");
    }

    private GuidedSessionRow mapSessionRow(Map<String, Object> row) {
        return new GuidedSessionRow(
            string(column(row, "session_id")),
            string(column(row, "student_id")),
            string(column(row, "questionnaire_code")),
            intValue(column(row, "questionnaire_version")),
            nullableString(column(row, "current_question_code")),
            intValue(column(row, "current_question_index")),
            string(column(row, "session_status")),
            string(column(row, "current_stage")),
            intValue(column(row, "version_no")),
            intValue(column(row, "last_generated_version_no")),
            localDateTime(column(row, "started_at")),
            localDateTime(column(row, "finished_at")),
            localDateTime(column(row, "exited_at")),
            localDateTime(column(row, "create_time")),
            localDateTime(column(row, "update_time"))
        );
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = nullableString(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.replace(" ", "T"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Map<String, Object>> loadAnswers(String sessionId) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = guidedMapper.listAnswerJson(sessionId);
        for (Map<String, Object> row : rows) {
            Object answerJson = jsonObject(column(row, "answer_json"));
            result.put(
                string(column(row, "question_code")),
                answerJson instanceof Map<?, ?> answerMap ? toStringKeyMap(answerMap) : new LinkedHashMap<>()
            );
        }
        return result;
    }

    private List<Map<String, Object>> loadAnswerRows(String sessionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : guidedMapper.listAnswerRows(sessionId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_code", column(row, "question_code"));
            item.put("question_type", column(row, "question_type"));
            item.put("module_code", column(row, "module_code"));
            item.put("answer_json", jsonObject(column(row, "answer_json")));
            item.put("answer_display_text", column(row, "answer_display_text"));
            item.put("version_no", intValue(column(row, "version_no")));
            item.put("update_time", localDateTime(column(row, "update_time")));
            rows.add(item);
        }
        return rows;
    }

    private List<Map<String, Object>> loadMessages(String sessionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : guidedMapper.listMessages(sessionId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sequence_no", intValue(column(row, "sequence_no")));
            item.put("message_role", column(row, "message_role"));
            item.put("message_kind", column(row, "message_kind"));
            item.put("question_code", column(row, "question_code"));
            item.put("content", column(row, "content"));
            item.put("payload_json", jsonObject(column(row, "payload_json")));
            item.put("create_time", localDateTime(column(row, "create_time")));
            rows.add(item);
        }
        return rows;
    }

    private void upsertAnswer(
        String sessionId,
        String studentId,
        Map<String, Object> question,
        Map<String, Object> answer,
        String displayText,
        int versionNo
    ) {
        guidedMapper.upsertAnswer(mapOfEntries(
            "sessionId",
            sessionId,
            "studentId",
            studentId,
            "questionCode",
            string(question.get("code")),
            "questionType",
            string(question.get("type")),
            "moduleCode",
            nullableString(question.get("module_code")),
            "answerJson",
            toJson(answer),
            "answerDisplayText",
            displayText,
            "versionNo",
            versionNo
        ));
    }

    private int nextSequenceNo(String sessionId) {
        Integer value = guidedMapper.nextMessageSequenceNo(sessionId);
        return value == null ? 1 : value;
    }

    private void insertMessage(
        String sessionId,
        String studentId,
        String role,
        String kind,
        String content,
        String questionCode,
        Map<String, Object> payload
    ) {
        guidedMapper.insertMessage(mapOfEntries(
            "sessionId",
            sessionId,
            "studentId",
            studentId,
            "sequenceNo",
            nextSequenceNo(sessionId),
            "messageRole",
            role,
            "messageKind",
            kind,
            "questionCode",
            questionCode,
            "content",
            content,
            "payloadJson",
            toJson(payload == null ? Map.of() : payload)
        ));
    }

    private void upsertQuestionMessage(
        String sessionId,
        String studentId,
        String role,
        String kind,
        String questionCode,
        String content,
        Map<String, Object> payload,
        boolean replaceExisting
    ) {
        Long messageId = replaceExisting ? findLatestMessageId(sessionId, role, kind, questionCode) : null;
        if (messageId == null) {
            insertMessage(sessionId, studentId, role, kind, content, questionCode, payload);
            return;
        }
        guidedMapper.updateMessage(mapOfEntries(
            "id",
            messageId,
            "content",
            content,
            "payloadJson",
            toJson(payload == null ? Map.of() : payload)
        ));
    }

    private Long findLatestMessageId(String sessionId, String role, String kind, String questionCode) {
        return guidedMapper.findLatestMessageId(sessionId, role, kind, questionCode);
    }

    private Map<String, Object> loadLatestResult(String studentId, String sessionId) {
        Map<String, Object> row = guidedMapper.findLatestResult(studentId, sessionId);
        return row == null ? null : serializeResultRow(row);
    }

    private Map<String, Object> serializeResultRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", column(row, "id"));
        result.put("session_id", column(row, "session_id"));
        result.put("student_id", column(row, "student_id"));
        result.put("source_version_no", intValue(column(row, "source_version_no")));
        result.put("result_status", column(row, "result_status"));
        result.put("trigger_reason", column(row, "trigger_reason"));
        result.put("profile_json", jsonObject(column(row, "profile_json")));
        result.put("db_payload_json", jsonObject(column(row, "db_payload_json")));
        result.put("radar_scores_json", jsonObject(column(row, "radar_scores_json")));
        result.put("summary_text", column(row, "summary_text"));
        result.put("save_error_message", column(row, "save_error_message"));
        result.put("create_time", localDateTime(column(row, "create_time")));
        result.put("update_time", localDateTime(column(row, "update_time")));
        return result;
    }

    private Object column(Map<String, Object> row, String columnName) {
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        String camelName = snakeToCamel(columnName);
        return row.get(camelName);
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

    private Map<String, Object> bundle(
        Map<String, Object> session,
        List<Map<String, Object>> messages,
        List<Map<String, Object>> answers,
        Map<String, Object> currentQuestion,
        List<Map<String, Object>> questions,
        Map<String, Object> result
    ) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("session", session);
        bundle.put("messages", messages);
        bundle.put("answers", answers);
        bundle.put("current_question", currentQuestion);
        bundle.put("questions", questions);
        bundle.put("result", result);
        return bundle;
    }

    private Map<String, Object> serializeSession(GuidedSessionRow row) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", row.sessionId());
        session.put("student_id", row.studentId());
        session.put("questionnaire_code", row.questionnaireCode());
        session.put("questionnaire_version", row.questionnaireVersion());
        session.put("current_question_code", row.currentQuestionCode());
        session.put("current_question_index", row.currentQuestionIndex());
        session.put("session_status", row.sessionStatus());
        session.put("current_stage", row.currentStage());
        session.put("version_no", row.versionNo());
        session.put("last_generated_version_no", row.lastGeneratedVersionNo());
        session.put("started_at", row.startedAt());
        session.put("finished_at", row.finishedAt());
        session.put("exited_at", row.exitedAt());
        session.put("create_time", row.createTime());
        session.put("update_time", row.updateTime());
        return session;
    }

    private List<Map<String, Object>> serializeVisibleQuestions(Map<String, Map<String, Object>> answers) {
        List<Map<String, Object>> visible = visibleQuestions(answers);
        int total = visible.size();
        List<Map<String, Object>> result = new ArrayList<>(total);
        for (int index = 0; index < visible.size(); index += 1) {
            result.add(serializeQuestion(visible.get(index), answers, index + 1, total));
        }
        return result;
    }

    private Map<String, Object> serializeQuestion(
        Map<String, Object> question,
        Map<String, Map<String, Object>> answers,
        Integer index,
        Integer total
    ) {
        Map<String, Object> result = copyQuestion(question);
        List<Map<String, String>> options = questionOptions(string(question.get("code")));
        if ("Q12".equals(question.get("code"))) {
            options = estimatedStandardizedQuestionOptions(selectedValue(answers.get("Q5")));
        }
        if (options != null) {
            result.put("options", options);
        }
        if ("Q10".equals(question.get("code"))) {
            result.put("title", externalExamQuestionTitle(selectedValue(answers.get("Q5"))));
        }
        if ("Q9_SCORE".equals(question.get("code"))) {
            String languageLabel = optionLabelForQuestion("Q9", selectedValue(answers.get("Q9")));
            result.put("title", StrUtil.isBlank(languageLabel) ? "请填写对应的语言考试成绩。" : "请填写你的 " + languageLabel + " 成绩。");
        }
        if (index != null) {
            result.put("index", index);
        } else {
            result.put("index", defaultQuestionIndex(string(question.get("code"))));
        }
        if (total != null) {
            result.put("total", total);
        }
        result.put("fields", fieldsForQuestion(string(question.get("code")), answers));
        result.put("row_fields", rowFieldsForQuestion(string(question.get("code"))));
        return result;
    }

    private int defaultQuestionIndex(String questionCode) {
        for (int index = 0; index < QUESTIONS.size(); index += 1) {
            if (questionCode.equals(QUESTIONS.get(index).get("code"))) {
                return index + 1;
            }
        }
        return 0;
    }

    private Map<String, Object> copyQuestion(Map<String, Object> question) {
        return new LinkedHashMap<>(question);
    }

    private List<Map<String, Object>> visibleQuestions(Map<String, Map<String, Object>> answers) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> question : QUESTIONS) {
            String code = string(question.get("code"));
            if ("Q11".equals(code)) {
                result.addAll(standardizedScoreQuestions(answers));
                continue;
            }
            if (isQuestionVisible(question, answers)) {
                result.add(question);
            }
        }
        return result;
    }

    private List<Map<String, Object>> standardizedScoreQuestions(Map<String, Map<String, Object>> answers) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String value : selectedStandardizedTestsInOrder(answers)) {
            Map<String, Object> question = STANDARDIZED_SCORE_QUESTION_TEMPLATES.get(value);
            if (question != null) {
                result.add(question);
            }
        }
        return result;
    }

    private List<String> selectedStandardizedTestsInOrder(Map<String, Map<String, Object>> answers) {
        if (!"yes".equals(selectedValue(answers.get("Q10")))) {
            return List.of();
        }
        return examTypesForCurriculum(selectedValue(answers.get("Q5")));
    }

    private boolean isQuestionVisible(Map<String, Object> question, Map<String, Map<String, Object>> answers) {
        String code = string(question.get("code"));
        if (STANDARDIZED_SCORE_QUESTION_TEMPLATES.values().stream().anyMatch(item -> code.equals(item.get("code")))) {
            return standardizedScoreQuestions(answers).stream().anyMatch(item -> code.equals(item.get("code")));
        }
        if ("Q8".equals(code)) {
            return selectedValue(answers.get("Q5")) != null;
        }
        if ("Q9_SCORE".equals(code)) {
            String languageType = selectedValue(answers.get("Q9"));
            return languageType != null && !"NO_SCORE".equals(languageType);
        }
        if ("Q10".equals(code)) {
            return selectedValue(answers.get("Q5")) != null;
        }
        if ("Q11".equals(code)) {
            return false;
        }
        if ("Q12".equals(code)) {
            return "no".equals(selectedValue(answers.get("Q10")));
        }
        return true;
    }

    private List<String> examTypesForCurriculum(String curriculumCode) {
        return switch (StrUtil.nullToEmpty(curriculumCode)) {
            case "A_LEVEL" -> List.of("A_LEVEL");
            case "IB" -> List.of("IB");
            case "CHINESE_HIGH_SCHOOL" -> List.of("CHINESE_HIGH_SCHOOL");
            case "OSSD" -> List.of("OSSD");
            case "US_HIGH_SCHOOL" -> List.of("AP", "SAT", "ACT");
            case "OTHER" -> List.of("OTHER");
            default -> List.of();
        };
    }

    private boolean hasScoredExternalExamAnswerFromAnswers(Map<String, Map<String, Object>> answers) {
        for (String examType : selectedStandardizedTestsInOrder(answers)) {
            Map<String, Object> template = STANDARDIZED_SCORE_QUESTION_TEMPLATES.get(examType);
            Map<String, Object> answer = template == null ? null : answers.get(string(template.get("code")));
            if (answer == null) {
                continue;
            }
            if ("A_LEVEL".equals(examType)) {
                if (repeatableRowsHaveScoredValue(answer, "grade")) {
                    return true;
                }
            } else if ("IB".equals(examType)) {
                if (repeatableRowsHaveScoredValue(answer, "score")) {
                    return true;
                }
            } else if ("CHINESE_HIGH_SCHOOL".equals(examType)) {
                if (repeatableRowsHaveScoredValue(answer, "score_numeric")) {
                    return true;
                }
            } else if ("OSSD".equals(examType)) {
                if (repeatableRowsHaveAnyValue(answer, Set.of("score_numeric"))) {
                    return true;
                }
            } else if ("OTHER".equals(examType)) {
                if (repeatableRowsHaveAnyValue(answer, Set.of("score_text", "score_numeric"))) {
                    return true;
                }
            } else if ("AP".equals(examType)) {
                if (repeatableRowsHaveAnyValue(answer, Set.of("score"))) {
                    return true;
                }
            } else if ("SAT".equals(examType)) {
                if ("SCORED".equals(selectedText(answer, "status_code"))
                    || (!Set.of("PLANNED", "ESTIMATED").contains(selectedText(answer, "status_code"))
                    && hasAnyValue(answer, "total_score", "sat_erw", "sat_math"))) {
                    return true;
                }
            } else if ("ACT".equals(examType)
                && ("SCORED".equals(selectedText(answer, "status_code"))
                || (!Set.of("PLANNED", "ESTIMATED").contains(selectedText(answer, "status_code"))
                && hasAnyValue(answer, "total_score", "act_english", "act_math", "act_reading", "act_science")))) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatableRowsHaveScoredValue(Map<String, Object> answer, String scoreField) {
        Object rawRows = answer.get("rows");
        if (!(rawRows instanceof List<?> rows)) {
            return false;
        }
        for (Object rawRow : rows) {
            if (!(rawRow instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> typedRow = toStringKeyMap(row);
            String status = selectedText(typedRow, "status");
            if ("SCORED".equals(status)) {
                return true;
            }
            if (!Set.of("PLANNED", "ESTIMATED").contains(status) && hasAnyValue(typedRow, scoreField)) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatableRowsHaveAnyValue(Map<String, Object> answer, Set<String> fieldNames) {
        Object rawRows = answer.get("rows");
        if (!(rawRows instanceof List<?> rows)) {
            return false;
        }
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> typedRow = toStringKeyMap(row);
            for (String fieldName : fieldNames) {
                if (typedRow.containsKey(fieldName) && nullableString(typedRow.get(fieldName)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Object> findNextQuestion(Map<String, Map<String, Object>> answers) {
        for (Map<String, Object> question : visibleQuestions(answers)) {
            String code = string(question.get("code"));
            Map<String, Object> answer = answers.get(code);
            if (answer == null || !isAnswerMeaningful(question, answer)) {
                return question;
            }
        }
        return null;
    }

    private int visibleQuestionIndex(Map<String, Map<String, Object>> answers, String questionCode) {
        List<Map<String, Object>> visible = visibleQuestions(answers);
        for (int index = 0; index < visible.size(); index += 1) {
            if (questionCode.equals(visible.get(index).get("code"))) {
                return index;
            }
        }
        return 0;
    }

    private Map<String, Object> normalizeAnswer(
        Map<String, Object> question,
        Map<String, Object> rawAnswer,
        Map<String, Map<String, Object>> answers
    ) {
        String questionType = string(question.get("type"));
        if ("single".equals(questionType)) {
            Object value = rawAnswer.get("selected_value");
            if (value == null) {
                value = rawAnswer.get("value");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("selected_value", value == null ? "" : string(value));
            result.put("custom_text", string(rawAnswer.get("custom_text")).trim());
            return result;
        }
        if ("multi".equals(questionType)) {
            List<String> normalizedValues = rawValues(rawAnswer.get("selected_values"));
            if (normalizedValues.isEmpty()) {
                normalizedValues = rawValues(rawAnswer.get("values"));
            }
            Set<String> exclusiveValues = new LinkedHashSet<>(rawValues(question.get("exclusive_option_values")));
            if (!exclusiveValues.isEmpty() && normalizedValues.stream().anyMatch(exclusiveValues::contains)) {
                List<String> nonExclusive = normalizedValues.stream().filter(value -> !exclusiveValues.contains(value)).toList();
                if (nonExclusive.isEmpty()) {
                    String exclusiveValue = normalizedValues.stream()
                        .filter(exclusiveValues::contains)
                        .findFirst()
                        .orElse(null);
                    normalizedValues = exclusiveValue == null ? List.of() : List.of(exclusiveValue);
                } else {
                    normalizedValues = nonExclusive;
                }
            }
            int maxSelect = intValue(question.get("max_select"));
            if (maxSelect > 0 && normalizedValues.size() > maxSelect) {
                normalizedValues = new ArrayList<>(normalizedValues.subList(0, maxSelect));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("selected_values", normalizedValues);
            result.put("custom_text", string(rawAnswer.get("custom_text")).trim());
            return result;
        }
        if ("repeatable_form".equals(questionType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map<String, Object> field : rawFieldList(question.get("fields"))) {
                String fieldName = string(field.get("name"));
                result.put(fieldName, string(rawAnswer.get(fieldName)).trim());
            }
            List<Map<String, Object>> normalizedRows = new ArrayList<>();
            Object rawRows = rawAnswer.get("rows");
            if (rawRows instanceof List<?> rows) {
                for (Object rowObject : rows) {
                    if (!(rowObject instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Map<String, Object> typedRow = toStringKeyMap(row);
                    Map<String, Object> normalizedRow = new LinkedHashMap<>();
                    for (Map<String, Object> field : rawFieldList(question.get("row_fields"))) {
                        String fieldName = string(field.get("name"));
                        normalizedRow.put(fieldName, string(typedRow.get(fieldName)).trim());
                    }
                    if (normalizedRow.values().stream().anyMatch(value -> !string(value).isBlank())) {
                        normalizedRows.add(normalizedRow);
                    }
                }
            }
            result.put("rows", normalizedRows);
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> field : fieldsForQuestion(string(question.get("code")), answers)) {
            String fieldName = string(field.get("name"));
            result.put(fieldName, string(rawAnswer.get(fieldName)).trim());
        }
        return result;
    }

    private boolean isAnswerMeaningful(Map<String, Object> question, Map<String, Object> answer) {
        if (answer == null) {
            return false;
        }
        if (isTrue(answer, "skipped")) {
            return true;
        }
        String questionType = string(question.get("type"));
        if ("single".equals(questionType)) {
            return selectedValue(answer) != null || !selectedText(answer, "custom_text").isBlank();
        }
        if ("multi".equals(questionType)) {
            return !selectedValues(answer).isEmpty() || !selectedText(answer, "custom_text").isBlank();
        }
        if ("repeatable_form".equals(questionType)) {
            for (Map<String, Object> field : rawFieldList(question.get("fields"))) {
                if (!selectedText(answer, string(field.get("name"))).isBlank()) {
                    return true;
                }
            }
            Object rows = answer.get("rows");
            if (rows instanceof List<?> rowList) {
                for (Object rowObject : rowList) {
                    if (rowObject instanceof Map<?, ?> row && toStringKeyMap(row).values().stream().anyMatch(value -> !string(value).isBlank())) {
                        return true;
                    }
                }
            }
            return false;
        }
        return answer.values().stream().anyMatch(value -> !string(value).isBlank());
    }

    private void validateRequiredAnswer(Map<String, Object> question, Map<String, Object> answer) {
        if (answer == null || isTrue(answer, "skipped")) {
            return;
        }
        List<String> missingLabels = new ArrayList<>();
        if ("repeatable_form".equals(string(question.get("type")))) {
            addMissingRequiredFields(question, answer, rawFieldList(question.get("fields")), missingLabels, "");
            List<Map<String, Object>> rows = repeatableRows(answer);
            int minRows = intValue(question.get("min_rows"));
            if (minRows > 0 && rows.isEmpty()) {
                missingLabels.add("至少填写一条成绩");
            }
            int rowsToCheck = Math.max(rows.size(), minRows);
            for (int index = 0; index < rowsToCheck; index += 1) {
                Map<String, Object> row = index < rows.size() ? rows.get(index) : Map.of();
                if (index < minRows || hasAnyNonBlank(row)) {
                    addMissingRequiredFields(question, row, rawFieldList(question.get("row_fields")), missingLabels, "第" + (index + 1) + "条");
                }
            }
        } else {
            addMissingRequiredFields(question, answer, rawFieldList(question.get("fields")), missingLabels, "");
        }
        if (!missingLabels.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "请填写必填项：" + String.join("、", missingLabels));
        }
    }

    private void addMissingRequiredFields(
        Map<String, Object> question,
        Map<String, Object> answer,
        List<Map<String, Object>> fields,
        List<String> missingLabels,
        String labelPrefix
    ) {
        for (Map<String, Object> field : fields) {
            if (!shouldValidateRequiredField(question, answer, field)) {
                continue;
            }
            String fieldName = string(field.get("name"));
            if (selectedText(answer, fieldName).isBlank()) {
                String label = string(field.get("label"));
                missingLabels.add(StrUtil.isBlank(labelPrefix) ? label : labelPrefix + label);
            }
        }
    }

    private boolean shouldValidateRequiredField(Map<String, Object> question, Map<String, Object> answer, Map<String, Object> field) {
        if (!Boolean.TRUE.equals(field.get("required"))) {
            return false;
        }
        String questionCode = string(question.get("code"));
        String fieldName = string(field.get("name"));
        if (Set.of("Q13", "Q14", "Q15").contains(questionCode)
            && !"has_experience".equals(fieldName)
            && !"yes".equals(selectedText(answer, "has_experience"))) {
            return false;
        }
        return true;
    }

    private String answerDisplayText(Map<String, Object> question, Map<String, Object> answer) {
        if (isTrue(answer, "skipped")) {
            return "已跳过";
        }
        String questionType = string(question.get("type"));
        if ("single".equals(questionType)) {
            String label = optionLabel(question, selectedValue(answer));
            String customText = selectedText(answer, "custom_text");
            return !label.isBlank() && !customText.isBlank() ? label + "：" + customText : label;
        }
        if ("multi".equals(questionType)) {
            List<String> labels = selectedValues(answer).stream().map(value -> optionLabel(question, value)).toList();
            String customText = selectedText(answer, "custom_text");
            List<String> parts = new ArrayList<>(labels);
            if (!customText.isBlank()) {
                parts.add(customText);
            }
            return String.join("、", parts);
        }
        List<String> pairs = new ArrayList<>();
        for (Map<String, Object> field : rawFieldList(question.get("fields"))) {
            addDisplayPair(pairs, field, answer);
        }
        if ("repeatable_form".equals(questionType)) {
            Object rows = answer.get("rows");
            if (rows instanceof List<?> rowList) {
                for (Object rowObject : rowList) {
                    if (!(rowObject instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Map<String, Object> typedRow = toStringKeyMap(row);
                    List<String> rowPairs = new ArrayList<>();
                    for (Map<String, Object> field : rawFieldList(question.get("row_fields"))) {
                        addDisplayPair(rowPairs, field, typedRow);
                    }
                    if (!rowPairs.isEmpty()) {
                        pairs.add(String.join("，", rowPairs));
                    }
                }
            }
        }
        return pairs.isEmpty() ? "已填写" : String.join("；", pairs);
    }

    private void addDisplayPair(List<String> pairs, Map<String, Object> field, Map<String, Object> answer) {
        String fieldName = string(field.get("name"));
        String value = selectedText(answer, fieldName);
        if (value.isBlank()) {
            return;
        }
        if ("select".equals(field.get("kind"))) {
            value = optionLabel(field, value);
        }
        pairs.add(string(field.get("label")) + "：" + value);
    }

    private String optionLabel(Map<String, Object> questionOrField, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Object optionsObject = questionOrField.get("options");
        if (optionsObject instanceof List<?> optionsList) {
            for (Object optionObject : optionsList) {
                if (!(optionObject instanceof Map<?, ?> option)) {
                    continue;
                }
                Object optionValue = option.get("value");
                if (value.equals(optionValue == null ? null : string(optionValue))) {
                    Object label = option.get("label");
                    return label == null ? value : string(label);
                }
            }
        }
        return value;
    }

    private List<Map<String, String>> questionOptions(String questionCode) {
        return switch (questionCode) {
            case "Q1" -> firstNonEmptyOptions(
                sharedStudentBasicInfoOptions("current_grade"),
                fallbackCurrentGradeQuestionOptions()
            );
            case "Q2" -> firstNonEmptyOptions(
                sharedStudentBasicInfoOptions("target_entry_term"),
                fallbackEntryTermQuestionOptions()
            );
            case "Q3" -> firstNonEmptyOptions(
                sharedStudentBasicInfoOptions("CTRY_CODE_VAL"),
                options(
                opt("US", "美国"),
                opt("UK", "英国"),
                opt("HK", "中国香港"),
                opt("SG", "新加坡"),
                opt("CA", "加拿大"),
                opt("AU", "澳大利亚"),
                opt("EU", "欧洲"),
                opt("OTHER", "其他")
                )
            );
            case "Q4" -> firstNonEmptyOptions(
                sharedStudentBasicInfoOptions("MAJ_CODE_VAL"),
                options(
                opt("CS_AI_DS", "计算机 / AI / 数据科学"),
                opt("MATH_STATS", "数学 / 统计"),
                opt("ENGINEERING", "工程"),
                opt("BUSINESS_ECON_FINANCE", "经济 / 金融 / 商科"),
                opt("SCIENCE", "物理 / 化学 / 生物"),
                opt("PSY_EDU", "心理 / 教育"),
                opt("SOCIAL_SCIENCE", "社科 / 政治 / 国际关系"),
                opt("MEDIA_COMM", "传媒 / 新闻 / 传播"),
                opt("LAW", "法学相关"),
                opt("ART_DESIGN_ARCH", "艺术 / 设计 / 建筑")
                )
            );
            case "Q5" -> options(
                opt("A_LEVEL", "A-Level"),
                opt("IB", "IB"),
                opt("CHINESE_HIGH_SCHOOL", "普高"),
                opt("US_HIGH_SCHOOL", "国际学校美高体系"),
                opt("OSSD", "OSSD"),
                opt("OTHER", "其他")
            );
            case "Q7" -> gradeSizeQuestionOptions();
            case "Q9" -> languageTypeQuestionOptions();
            case "Q10" -> options(opt("yes", "是"), opt("no", "否"));
            default -> null;
        };
    }

    private List<Map<String, String>> gradeSizeQuestionOptions() {
        return options(
            opt("AROUND_50", "50 左右"),
            opt("AROUND_100", "100 左右"),
            opt("AROUND_200", "200 左右"),
            opt("AROUND_500", "500 左右"),
            opt("AROUND_1000", "1000 左右"),
            opt("UNKNOWN", "不清楚"),
            opt("CUSTOM", "其他")
        );
    }

    private List<Map<String, String>> languageTypeQuestionOptions() {
        List<Map<String, String>> sourceOptions = firstNonEmptyOptions(
            sharedFormFieldOptions("student_language_test_record", "test_type_code"),
            options(
                opt("IELTS", "雅思"),
                opt("TOEFL_IBT", "托福 iBT"),
                opt("TOEFL_HOME", "托福 Home"),
                opt("DET", "多邻国"),
                opt("PTE", "PTE"),
                opt("LANGUAGECERT_ACADEMIC", "LanguageCert Academic"),
                opt("CAMBRIDGE", "剑桥英语"),
                opt("OTHER", "其他语言考试")
            )
        );
        List<Map<String, String>> result = new ArrayList<>(sourceOptions);
        if (result.stream().noneMatch(option -> "NO_SCORE".equals(option.get("value")))) {
            result.add(opt("NO_SCORE", "暂时还没有"));
        }
        return List.copyOf(result);
    }

    private String externalExamQuestionTitle(String curriculumCode) {
        String curriculumLabel = optionLabelForQuestion("Q5", curriculumCode);
        if ("US_HIGH_SCHOOL".equals(curriculumCode)) {
            return "你是否考过 AP/SAT/ACT？";
        }
        if (StrUtil.isBlank(curriculumLabel)) {
            return "你是否已经有对应课程体系的考试成绩？";
        }
        return "你是否已经有" + curriculumLabel + "对应考试成绩？";
    }

    private List<Map<String, String>> estimatedStandardizedQuestionOptions(String curriculumCode) {
        return switch (StrUtil.nullToEmpty(curriculumCode)) {
            case "A_LEVEL" -> options(
                opt("A_LEVEL_ASTAR_ASTAR_ASTAR", "A-Level A*A*A*"),
                opt("A_LEVEL_ASTAR_ASTAR_A", "A-Level A*A*A"),
                opt("A_LEVEL_ASTAR_A_A", "A-Level A*AA"),
                opt("A_LEVEL_A_A_A", "A-Level AAA"),
                opt("A_LEVEL_A_A_B", "A-Level AAB"),
                opt("A_LEVEL_A_B_B", "A-Level ABB"),
                opt("UNSURE", "还不确定")
            );
            case "IB" -> options(
                opt("IB_45", "IB 45"),
                opt("IB_42_44", "IB 42-44"),
                opt("IB_40_41", "IB 40-41"),
                opt("IB_37_39", "IB 37-39"),
                opt("IB_34_36", "IB 34-36"),
                opt("UNSURE", "还不确定")
            );
            case "CHINESE_HIGH_SCHOOL" -> percentageEstimateOptions("CHS", "普高");
            case "OSSD" -> percentageEstimateOptions("OSSD", "OSSD");
            case "US_HIGH_SCHOOL" -> options(
                opt("AP_5_PLUS_5", "AP 5 门及以上 5 分"),
                opt("AP_3_4_5", "AP 3-4 门 5 分"),
                opt("AP_MIXED_4_5", "AP 以 4-5 分为主"),
                opt("SAT_1550_PLUS", "SAT 1550+"),
                opt("SAT_1500_1540", "SAT 1500-1540"),
                opt("SAT_1450_1490", "SAT 1450-1490"),
                opt("SAT_1400_1440", "SAT 1400-1440"),
                opt("ACT_35_36", "ACT 35-36"),
                opt("ACT_33_34", "ACT 33-34"),
                opt("ACT_31_32", "ACT 31-32"),
                opt("UNSURE", "还不确定")
            );
            case "OTHER" -> options(
                opt("TOP_TIER", "所在体系顶尖水平"),
                opt("STRONG", "所在体系优秀水平"),
                opt("GOOD", "所在体系良好水平"),
                opt("AVERAGE", "所在体系中等水平"),
                opt("UNSURE", "还不确定")
            );
            default -> options(opt("UNSURE", "还不确定"));
        };
    }

    private List<Map<String, String>> percentageEstimateOptions(String valuePrefix, String label) {
        return options(
            opt(valuePrefix + "_95_PLUS", label + " 95+"),
            opt(valuePrefix + "_90_94", label + " 90-94"),
            opt(valuePrefix + "_85_89", label + " 85-89"),
            opt(valuePrefix + "_80_84", label + " 80-84"),
            opt(valuePrefix + "_75_79", label + " 75-79"),
            opt("UNSURE", "还不确定")
        );
    }

    private List<Map<String, String>> firstNonEmptyOptions(
        List<Map<String, String>> primary,
        List<Map<String, String>> fallback
    ) {
        return primary == null || primary.isEmpty() ? fallback : primary;
    }

    private List<Map<String, String>> sharedStudentBasicInfoOptions(String fieldName) {
        return sharedFormFieldOptions("student_basic_info", fieldName);
    }

    private List<Map<String, String>> sharedFormFieldOptions(String tableName, String fieldName) {
        return sharedQuestionOptionsCache.computeIfAbsent(tableName + "." + fieldName, this::loadSharedFormFieldOptions);
    }

    private List<Map<String, String>> loadSharedFormFieldOptions(String cacheKey) {
        String[] segments = cacheKey.split("\\.", 2);
        if (segments.length != 2) {
            return List.of();
        }
        String tableName = segments[0];
        String fieldName = segments[1];
        try {
            Map<String, Object> formMeta = businessProfileFormService.buildBusinessProfileFormMeta();
            Map<String, Object> tables = formMeta.get("tables") instanceof Map<?, ?> map ? toStringKeyMap(map) : Map.of();
            Map<String, Object> tableMeta = tables.get(tableName) instanceof Map<?, ?> map
                ? toStringKeyMap(map)
                : Map.of();
            for (Map<String, Object> field : rawFieldList(tableMeta.get("fields"))) {
                if (!fieldName.equals(string(field.get("name")))) {
                    continue;
                }
                Object rawOptions = field.get("options");
                if (!(rawOptions instanceof List<?> optionList)) {
                    return List.of();
                }
                List<Map<String, String>> result = new ArrayList<>();
                for (Object optionObject : optionList) {
                    if (!(optionObject instanceof Map<?, ?> optionMap)) {
                        continue;
                    }
                    Map<String, Object> option = toStringKeyMap(optionMap);
                    String value = nullableString(option.get("value"));
                    if (value == null) {
                        continue;
                    }
                    result.add(opt(value, string(option.get("label"))));
                }
                return List.copyOf(result);
            }
        } catch (Exception exception) {
            log.warn("Failed to load shared guided question options for table={}, field={}", tableName, fieldName, exception);
        }
        return List.of();
    }

    private List<Map<String, String>> fallbackGpaScaleQuestionOptions() {
        return options(
            opt("GPA_4_0", "4.0"),
            opt("GPA_4_3", "4.3"),
            opt("GPA_5_0", "5.0"),
            opt("GPA_7_0", "7.0"),
            opt("PERCENT_100", "100"),
            opt("OTHER", "其他")
        );
    }

    private List<Map<String, String>> fallbackCurrentGradeQuestionOptions() {
        return options(
            opt("初一", "初一"),
            opt("初二", "初二"),
            opt("初三", "初三"),
            opt("G7", "G7 (初一)"),
            opt("G8", "G8 (初二)"),
            opt("G9", "G9 (初三)"),
            opt("高一", "高一"),
            opt("高二", "高二"),
            opt("高三", "高三"),
            opt("G10", "G10 (高一)"),
            opt("G11", "G11 (高二)"),
            opt("G12", "G12 (高三)"),
            opt("大一", "大一"),
            opt("大二", "大二"),
            opt("大三", "大三"),
            opt("大四", "大四"),
            opt("大五", "大五"),
            opt("研一", "研一"),
            opt("研二", "研二"),
            opt("研三", "研三"),
            opt("博一", "博一"),
            opt("博二", "博二"),
            opt("博三", "博三"),
            opt("Gap Year", "Gap Year"),
            opt("TRANSFER_YEAR_1", "大一转学申请"),
            opt("已毕业", "已毕业"),
            opt("OTHER", "其他")
        );
    }

    private List<Map<String, String>> fallbackEntryTermQuestionOptions() {
        int currentYear = Year.now().getValue();
        List<Map<String, String>> options = new ArrayList<>();
        for (int year = currentYear; year < currentYear + 10; year++) {
            options.add(opt(year + "春季入学", year + "春季入学"));
            options.add(opt(year + "秋季入学", year + "秋季入学"));
        }
        options.add(opt("暂未确定", "暂未确定"));
        return options;
    }

    private List<Map<String, String>> competitionYearOptions() {
        return yearOptions(15, 0);
    }

    private List<Map<String, String>> examYearOptions() {
        return yearOptions(10, 3);
    }

    private List<Map<String, String>> yearOptions(int pastYears, int futureYears) {
        int currentYear = Year.now().getValue();
        List<Map<String, String>> options = new ArrayList<>();
        for (int year = currentYear + futureYears; year >= currentYear - pastYears; year--) {
            String yearText = String.valueOf(year);
            options.add(opt(yearText, yearText));
        }
        return options;
    }

    private List<Map<String, Object>> fieldsForQuestion(
        String questionCode,
        Map<String, Map<String, Object>> answers
    ) {
        if ("Q8".equals(questionCode)) {
            return List.of(
                inputField("gpa", "当前 GPA", "number"),
                selectField(
                    "gpa_scale",
                    "分制",
                    firstNonEmptyOptions(
                        sharedFormFieldOptions("student_academic_curriculum_gpa", "gpa_scale_code"),
                        fallbackGpaScaleQuestionOptions()
                    )
                ),
                selectField("is_weighted", "是否加权", options(opt("yes", "是"), opt("no", "否"), opt("unknown", "不清楚")))
            );
        }
        if ("Q9_SCORE".equals(questionCode)) {
            return languageQuestionFields(selectedValue(answers.get("Q9")));
        }
        if ("Q11_A_LEVEL".equals(questionCode)) {
            return List.of(selectField("a_level_board", "A-Level 考试局", A_LEVEL_BOARD_OPTIONS));
        }
        if ("Q11_SAT".equals(questionCode)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("total_score", "总分", "number"),
                inputField("sat_erw", "SAT 阅读与写作", "number"),
                inputField("sat_math", "SAT 数学", "number")
            );
        }
        if ("Q11_ACT".equals(questionCode)) {
            return List.of(
                selectField("status_code", "考试状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("total_score", "ACT 总分", "number"),
                inputField("act_english", "English", "number"),
                inputField("act_math", "Math", "number"),
                inputField("act_reading", "Reading", "number"),
                inputField("act_science", "Science", "number")
            );
        }
        if (Set.of("Q13", "Q14", "Q15").contains(questionCode)) {
            return experienceFields(questionCode, answers);
        }
        return List.of();
    }

    private List<Map<String, Object>> rowFieldsForQuestion(String questionCode) {
        if ("Q11_A_LEVEL".equals(questionCode)) {
            return List.of(
                requiredSelectField("subject", "科目", aLevelSubjectOptions()),
                selectField("grade", "成绩", A_LEVEL_GRADE_OPTIONS),
                selectField("status", "状态", SCORE_STATUS_OPTIONS)
            );
        }
        if ("Q11_IB".equals(questionCode)) {
            return List.of(
                requiredSelectField("subject", "IB 科目", ibSubjectOptions()),
                selectField("level", "级别", IB_LEVEL_OPTIONS),
                selectField("score", "分数", IB_SCORE_OPTIONS),
                selectField("status", "状态", SCORE_STATUS_OPTIONS)
            );
        }
        if ("Q11_CHINESE_HIGH_SCHOOL".equals(questionCode)) {
            return List.of(
                requiredSelectField("subject", "科目", chineseSubjectOptions()),
                inputField("score_numeric", "分数", "number")
            );
        }
        if ("Q11_OSSD".equals(questionCode)) {
            return List.of(
                requiredInputField("course_name_text", "课程名称", "text"),
                selectField("course_level_code", "课程级别", options(
                    opt("U", "University Preparation"),
                    opt("M", "University/College Preparation"),
                    opt("C", "College Preparation"),
                    opt("E", "Workplace Preparation"),
                    opt("O", "Open"),
                    opt("OTHER", "其他")
                )),
                inputField("score_numeric", "课程分数", "number"),
                inputField("credit_earned", "学分", "number")
            );
        }
        if ("Q11_OTHER".equals(questionCode)) {
            return List.of(
                requiredInputField("subject_name_text", "科目名称", "text"),
                inputField("subject_level_text", "科目级别", "text"),
                inputField("score_text", "原始成绩", "text"),
                inputField("score_numeric", "数值成绩", "number"),
                selectField("score_scale_code", "成绩分制", fallbackGpaScaleQuestionOptions())
            );
        }
        if ("Q11_AP".equals(questionCode)) {
            return List.of(
                requiredSelectField("ap_course_id", "AP 科目", apCourseOptions()),
                selectField("score", "分数", AP_SCORE_OPTIONS),
                selectField("year_taken", "考试年份", examYearOptions())
            );
        }
        return List.of();
    }

    private List<Map<String, Object>> experienceFields(
        String questionCode,
        Map<String, Map<String, Object>> answers
    ) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if ("Q13".equals(questionCode)) {
            boolean universityGrade = isUniversityGrade(selectedValue(answers.get("Q1")));
            fields.add(requiredSelectField("has_experience", universityGrade ? "是否有企业实习经历" : "是否有活动经历", options(opt("yes", "有"), opt("no", "暂时没有"))));
            if (universityGrade) {
                fields.add(requiredInputField("start_time", "开始时间", "date"));
                fields.add(requiredInputField("end_time", "结束时间", "date"));
                fields.add(requiredTextField("company_name", "企业名"));
                fields.add(requiredTextField("position_name", "岗位"));
                fields.add(requiredTextField("referrer_name", "推荐人"));
            } else {
                fields.add(requiredTextareaField("activity_summary", "活动简述"));
                fields.add(requiredTextField("referrer_name", "推荐人"));
                fields.add(inputField("start_time", "开始时间", "date"));
                fields.add(inputField("end_time", "结束时间", "date"));
            }
        } else if ("Q14".equals(questionCode)) {
            fields.add(requiredSelectField("has_experience", "是否有科研经历", options(opt("yes", "有"), opt("no", "暂时没有"))));
            fields.add(requiredTextareaField("research_summary", "科研经历简述"));
            fields.add(requiredTextField("initiator_name", "发起方"));
            fields.add(requiredTextField("role_name", "担任角色"));
        } else {
            fields.add(requiredSelectField("has_experience", "是否有竞赛经历", options(opt("yes", "有"), opt("no", "暂时没有"))));
            fields.add(requiredTextField("competition_name", "竞赛名称"));
            fields.add(selectField("competition_field", "竞赛领域", COMPETITION_FIELD_OPTIONS));
            fields.add(selectField("competition_level", "竞赛级别", COMPETITION_LEVEL_OPTIONS));
            fields.add(inputField("participants_text", "参赛人数", "integer"));
            fields.add(textField("result_text", "成绩描述"));
            fields.add(selectField("competition_year", "参赛年份", competitionYearOptions()));
        }
        return fields;
    }

    private List<Map<String, Object>> languageQuestionFields(String languageType) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (languageType == null) {
            return fields;
        }
        fields.add(selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS));
        fields.add(inputField("test_date", "考试日期", "date"));
        fields.add(selectField("evidence_level_code", "证据等级", options(
            opt("CONFIRMED", "已确认"),
            opt("SELF_REPORTED", "学生自述"),
            opt("ESTIMATED", "预估")
        )));

        if ("IELTS".equals(languageType)) {
            fields.add(inputField("overall_score", "总分", "number"));
            fields.add(inputField("listening_score", "听力分", "number"));
            fields.add(inputField("reading_score", "阅读分", "number"));
            fields.add(inputField("writing_score", "写作分", "number"));
            fields.add(inputField("speaking_score", "口语分", "number"));
            return fields;
        }
        if (Set.of("TOEFL_IBT", "TOEFL_HOME", "PTE", "LANGUAGECERT_ACADEMIC").contains(languageType)) {
            fields.add(inputField("total_score", "总分", "number"));
            fields.add(inputField("reading_score", "阅读分", "number"));
            fields.add(inputField("listening_score", "听力分", "number"));
            fields.add(inputField("speaking_score", "口语分", "number"));
            fields.add(inputField("writing_score", "写作分", "number"));
            if ("LANGUAGECERT_ACADEMIC".equals(languageType)) {
                fields.add(selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS));
            }
            return fields;
        }
        if ("DET".equals(languageType)) {
            fields.add(inputField("total_score", "总分", "number"));
            fields.add(inputField("literacy_score", "Literacy 分", "number"));
            fields.add(inputField("comprehension_score", "Comprehension 分", "number"));
            fields.add(inputField("conversation_score", "Conversation 分", "number"));
            fields.add(inputField("production_score", "Production 分", "number"));
            return fields;
        }
        if ("CAMBRIDGE".equals(languageType)) {
            fields.add(textField("exam_name_text", "考试名称或版本"));
            fields.add(inputField("total_score", "总分", "number"));
            fields.add(inputField("reading_score", "阅读分", "number"));
            fields.add(inputField("use_of_english_score", "Use of English 分", "number"));
            fields.add(inputField("listening_score", "听力分", "number"));
            fields.add(inputField("speaking_score", "口语分", "number"));
            fields.add(inputField("writing_score", "写作分", "number"));
            fields.add(selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS));
            return fields;
        }
        if ("OTHER".equals(languageType)) {
            fields.add(textField("exam_name_text", "考试名称或版本"));
            fields.add(inputField("total_score", "总分", "number"));
            fields.add(textField("score_scale_text", "分制说明"));
            fields.add(selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS));
            fields.add(textareaField("notes", "备注"));
            return fields;
        }
        return fields;
    }

    private List<Map<String, String>> chineseSubjectOptions() {
        return options(
            opt("CHS_CHINESE", "语文"),
            opt("CHS_MATH", "数学"),
            opt("CHS_ENGLISH", "英语"),
            opt("CHS_PHYSICS", "物理"),
            opt("CHS_CHEMISTRY", "化学"),
            opt("CHS_BIOLOGY", "生物"),
            opt("CHS_HISTORY", "历史"),
            opt("CHS_GEOGRAPHY", "地理"),
            opt("CHS_POLITICS", "政治"),
            opt("OTHER", "其他")
        );
    }

    private List<Map<String, String>> aLevelSubjectOptions() {
        return options(
            opt("AL_MATH", "Mathematics"),
            opt("AL_FURTHER_MATH", "Further Mathematics"),
            opt("AL_PHYSICS", "Physics"),
            opt("AL_CHEMISTRY", "Chemistry"),
            opt("AL_BIOLOGY", "Biology"),
            opt("AL_ECONOMICS", "Economics"),
            opt("AL_BUSINESS", "Business"),
            opt("AL_COMPUTER_SCIENCE", "Computer Science")
        );
    }

    private List<Map<String, String>> apCourseOptions() {
        return options(
            opt("AP_CALCULUS_AB", "AP Calculus AB"),
            opt("AP_CALCULUS_BC", "AP Calculus BC"),
            opt("AP_STATISTICS", "AP Statistics"),
            opt("AP_COMPUTER_SCIENCE_A", "AP Computer Science A"),
            opt("AP_PHYSICS", "AP Physics"),
            opt("AP_CHEMISTRY", "AP Chemistry"),
            opt("AP_BIOLOGY", "AP Biology"),
            opt("AP_ECONOMICS", "AP Economics"),
            opt("OTHER", "Other")
        );
    }

    private List<Map<String, String>> ibSubjectOptions() {
        return options(
            opt("IB_MATH_AA", "Mathematics AA"),
            opt("IB_MATH_AI", "Mathematics AI"),
            opt("IB_PHYSICS", "Physics"),
            opt("IB_CHEMISTRY", "Chemistry"),
            opt("IB_BIOLOGY", "Biology"),
            opt("IB_ECONOMICS", "Economics"),
            opt("IB_BUSINESS_MANAGEMENT", "Business Management"),
            opt("IB_COMPUTER_SCIENCE", "Computer Science")
        );
    }

    private boolean hasAnyStandardizedScoreAnswer(Map<String, Map<String, Object>> answers) {
        return answers.keySet().stream().anyMatch(key -> key.startsWith("Q11_"));
    }

    private String selectedValue(Map<String, Object> answer) {
        if (answer == null) {
            return null;
        }
        String value = selectedText(answer, "selected_value");
        return value.isBlank() ? null : value;
    }

    private List<String> selectedValues(Map<String, Object> answer) {
        if (answer == null) {
            return List.of();
        }
        return rawValues(answer.get("selected_values"));
    }

    private List<String> rawValues(Object rawValues) {
        if (!(rawValues instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            String normalized = string(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String selectedText(Map<String, Object> answer, String key) {
        if (answer == null) {
            return "";
        }
        return string(answer.get(key)).trim();
    }

    private boolean hasAnyValue(Map<String, Object> source, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (!selectedText(source, fieldName).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrue(Map<String, Object> source, String key) {
        if (source == null) {
            return false;
        }
        Object value = source.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(string(value));
    }

    private Map<String, Object> jsonObject(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        if (raw instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        String text;
        if (raw instanceof byte[] bytes) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            text = string(raw);
        }
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(string(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "序列化问卷数据失败");
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullableString(Object value) {
        String text = string(value);
        return text.isBlank() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rawFieldList(Object fieldsObject) {
        if (!(fieldsObject instanceof List<?> fields)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object field : fields) {
            if (field instanceof Map<?, ?> map) {
                result.add(toStringKeyMap(map));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> buildQuestions() {
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(withOptions(question("Q1", "basic", "一、基础申请目标", "single", "你目前所在年级是？"), options(
            opt("G9", "G9 (初三)"),
            opt("G10", "G10 (高一)"),
            opt("G11", "G11 (高二)"),
            opt("G12", "G12 (高三)"),
            opt("Gap Year", "Gap Year"),
            opt("TRANSFER_YEAR_1", "大一转学申请"),
            opt("OTHER", "其他")
        )));
        questions.add(withOptions(question("Q2", "basic", "一、基础申请目标", "single", "你计划申请哪一届入学？"), options(
            opt("2026_FALL", "2026 Fall"),
            opt("2027_FALL", "2027 Fall"),
            opt("2028_FALL", "2028 Fall"),
            opt("OTHER", "其他")
        )));
        questions.add(with(withOptions(question("Q3", "basic", "一、基础申请目标", "multi", "你主要考虑哪些国家或地区？最多选 3 个。"), options(
            opt("US", "美国"),
            opt("UK", "英国"),
            opt("HK", "中国香港"),
            opt("SG", "新加坡"),
            opt("CA", "加拿大"),
            opt("AU", "澳大利亚"),
            opt("EU", "欧洲"),
            opt("OTHER", "其他")
        )), "max_select", 3, "searchable", true));
        questions.add(with(withOptions(question("Q4", "basic", "一、基础申请目标", "multi", "你目前感兴趣的专业方向是？最多选 2 个。"), options(
            opt("CS_AI_DS", "计算机 / AI / 数据科学"),
            opt("MATH_STATS", "数学 / 统计"),
            opt("ENGINEERING", "工程"),
            opt("BUSINESS_ECON_FINANCE", "经济 / 金融 / 商科"),
            opt("SCIENCE", "物理 / 化学 / 生物"),
            opt("PSY_EDU", "心理 / 教育"),
            opt("SOCIAL_SCIENCE", "社科 / 政治 / 国际关系"),
            opt("MEDIA_COMM", "传媒 / 新闻 / 传播"),
            opt("LAW", "法学相关"),
            opt("ART_DESIGN_ARCH", "艺术 / 设计 / 建筑")
        )), "max_select", 2, "searchable", true));
        questions.add(withOptions(question("Q5", "academic", "二、校内学术背景", "single", "你目前就读的课程体系是？"), options(
            opt("A_LEVEL", "A-Level"),
            opt("IB", "IB"),
            opt("CHINESE_HIGH_SCHOOL", "普高"),
            opt("US_HIGH_SCHOOL", "国际学校美高体系"),
            opt("OSSD", "OSSD"),
            opt("OTHER", "其他")
        )));
        questions.add(withOptions(question("Q6", "academic", "二、校内学术背景", "single", "你在年级中的大致表现是？"), options(
            opt("TOP_1", "年级前 1%"),
            opt("TOP_5", "年级前 5%"),
            opt("TOP_10", "年级前 10%"),
            opt("TOP_20", "年级前 20%"),
            opt("TOP_30", "年级前 30%"),
            opt("UPPER_MIDDLE", "年级中上"),
            opt("MIDDLE", "年级中等"),
            opt("UNKNOWN", "暂不清楚")
        )));
        questions.add(with(withOptions(question("Q7", "academic", "二、校内学术背景", "single", "你所在年级大约有多少人？"), options(
            opt("AROUND_50", "50 左右"),
            opt("AROUND_100", "100 左右"),
            opt("AROUND_200", "200 左右"),
            opt("AROUND_500", "500 左右"),
            opt("AROUND_1000", "1000 左右"),
            opt("UNKNOWN", "不清楚"),
            opt("CUSTOM", "其他")
        )), "custom_input_type", "number", "custom_placeholder", "请输入具体人数"));
        questions.add(question("Q8", "academic", "二、校内学术背景", "branch_form", "你的校内成绩可以怎么填写？"));
        questions.add(withOptions(question("Q9", "language", "三、语言能力", "single", "你目前有哪一种语言成绩？"), options(
            opt("IELTS", "雅思"),
            opt("TOEFL_IBT", "托福 iBT"),
            opt("TOEFL_HOME", "托福 Home"),
            opt("DET", "多邻国"),
            opt("PTE", "PTE"),
            opt("LANGUAGECERT_ACADEMIC", "LanguageCert Academic"),
            opt("CAMBRIDGE", "剑桥英语"),
            opt("OTHER", "其他语言考试"),
            opt("NO_SCORE", "暂时还没有")
        )));
        questions.add(with(question("Q9_SCORE", "language", "三、语言能力", "branch_form", "请填写对应的语言考试成绩。"), "skippable", false));
        questions.add(withOptions(question("Q10", "standardized", "四、考试成绩", "single", "你是否已经有对应课程体系的考试成绩？"), options(opt("yes", "是"), opt("no", "否"))));
        questions.add(question("Q11", "standardized", "四、考试成绩", "branch_form", "请填写对应的考试成绩。"));
        questions.add(withOptions(question("Q12", "standardized", "四、考试成绩", "single", "如果还没有正式考试成绩，你目前预估能拿到什么水平？"), options(opt("UNSURE", "还不确定"))));
        questions.add(question("Q13", "activity_internship", "五、活动 / 企业实习", "experience_form", "请填写你最有代表性的活动经历或企业实习。"));
        questions.add(question("Q14", "research", "五、科研经历", "experience_form", "你在校期间是否参加过一些科研项目？"));
        questions.add(question("Q15", "competition", "五、学术竞赛", "experience_form", "你是否参加过一些竞赛？"));
        return Collections.unmodifiableList(questions);
    }

    private static Map<String, Map<String, Object>> buildStandardizedScoreQuestionTemplates() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("A_LEVEL", with(question("Q11_A_LEVEL", "standardized", "四、考试成绩", "repeatable_form", "请填写你的 A-Level 成绩。"), "exam_type", "A_LEVEL", "min_rows", 1));
        result.put("IB", with(question("Q11_IB", "standardized", "四、考试成绩", "repeatable_form", "请填写你的 IB 成绩。"), "exam_type", "IB", "min_rows", 1));
        result.put("CHINESE_HIGH_SCHOOL", with(question("Q11_CHINESE_HIGH_SCHOOL", "standardized", "四、考试成绩", "repeatable_form", "请填写你的普高科目成绩。"), "exam_type", "CHINESE_HIGH_SCHOOL", "min_rows", 1));
        result.put("OSSD", with(question("Q11_OSSD", "standardized", "四、考试成绩", "repeatable_form", "请填写你的 OSSD 课程成绩。"), "exam_type", "OSSD", "min_rows", 1));
        result.put("OTHER", with(question("Q11_OTHER", "standardized", "四、考试成绩", "repeatable_form", "请填写你的其他课程体系成绩。"), "exam_type", "OTHER", "min_rows", 1));
        result.put("AP", with(question("Q11_AP", "standardized", "四、考试成绩", "repeatable_form", "请填写你的 AP 课程成绩。"), "exam_type", "AP", "min_rows", 1));
        result.put("SAT", with(question("Q11_SAT", "standardized", "四、考试成绩", "branch_form", "请填写你的 SAT 成绩。"), "exam_type", "SAT"));
        result.put("ACT", with(question("Q11_ACT", "standardized", "四、考试成绩", "branch_form", "请填写你的 ACT 成绩。"), "exam_type", "ACT"));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Map<String, Object>> buildQuestionByCode() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> question : QUESTIONS) {
            result.put(String.valueOf(question.get("code")), question);
        }
        for (Map<String, Object> question : STANDARDIZED_SCORE_QUESTION_TEMPLATES.values()) {
            result.put(String.valueOf(question.get("code")), question);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> question(
        String code,
        String moduleCode,
        String moduleTitle,
        String type,
        String title
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("module_code", moduleCode);
        result.put("module_title", moduleTitle);
        result.put("type", type);
        result.put("title", title);
        return result;
    }

    private static Map<String, Object> withOptions(Map<String, Object> question, List<Map<String, String>> options) {
        question.put("options", options);
        return question;
    }

    private static Map<String, Object> with(Map<String, Object> target, String key, Object value) {
        target.put(key, value);
        return target;
    }

    private static Map<String, Object> with(Map<String, Object> target, String key1, Object value1, String key2, Object value2) {
        target.put(key1, value1);
        target.put(key2, value2);
        return target;
    }

    private static Map<String, Object> with(
        Map<String, Object> target,
        String key1,
        Object value1,
        String key2,
        Object value2,
        String key3,
        Object value3
    ) {
        target.put(key1, value1);
        target.put(key2, value2);
        target.put(key3, value3);
        return target;
    }

    private static Map<String, Object> textField(String name, String label) {
        return field(name, label, "text", null, null);
    }

    private static Map<String, Object> requiredTextField(String name, String label) {
        return with(textField(name, label), "required", true);
    }

    private static Map<String, Object> inputField(String name, String label, String inputType) {
        return field(name, label, "text", null, inputType);
    }

    private static Map<String, Object> requiredInputField(String name, String label, String inputType) {
        return with(inputField(name, label, inputType), "required", true);
    }

    private static Map<String, Object> textareaField(String name, String label) {
        return field(name, label, "textarea", null, null);
    }

    private static Map<String, Object> requiredTextareaField(String name, String label) {
        return with(textareaField(name, label), "required", true);
    }

    private static Map<String, Object> selectField(String name, String label, List<Map<String, String>> options) {
        return field(name, label, "select", options, null);
    }

    private static Map<String, Object> requiredSelectField(String name, String label, List<Map<String, String>> options) {
        return with(selectField(name, label, options), "required", true);
    }

    private static Map<String, Object> field(
        String name,
        String label,
        String kind,
        List<Map<String, String>> options,
        String inputType
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("label", label);
        result.put("kind", kind);
        if (options != null) {
            result.put("options", options);
        }
        if (inputType != null) {
            result.put("input_type", inputType);
        }
        return result;
    }

    private static Map<String, String> opt(String value) {
        return opt(value, value);
    }

    private static Map<String, String> opt(String value, String label) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    @SafeVarargs
    private static List<Map<String, String>> options(Map<String, String>... options) {
        return List.of(options);
    }

    private record GuidedSessionRow(
        String sessionId,
        String studentId,
        String questionnaireCode,
        int questionnaireVersion,
        String currentQuestionCode,
        int currentQuestionIndex,
        String sessionStatus,
        String currentStage,
        int versionNo,
        int lastGeneratedVersionNo,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime exitedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }
}
