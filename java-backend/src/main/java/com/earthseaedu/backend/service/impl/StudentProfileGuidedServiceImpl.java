package com.earthseaedu.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.StudentProfileGuidedMapper;
import com.earthseaedu.backend.service.AiPromptRuntimeService;
import com.earthseaedu.backend.service.BusinessProfilePersistenceService;
import com.earthseaedu.backend.service.JwtService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * StudentProfileGuidedServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class StudentProfileGuidedServiceImpl implements StudentProfileGuidedService {

    private static final String QUESTIONNAIRE_CODE = "student_profile_guided_v1";
    private static final int QUESTIONNAIRE_VERSION = 1;
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

    private static final Map<String, String> GUIDED_LANGUAGE_TEST_DETAIL_TABLES = Map.of(
        "IELTS", "student_language_ielts",
        "TOEFL_IBT", "student_language_toefl_ibt",
        "TOEFL_ESSENTIALS", "student_language_toefl_essentials",
        "DET", "student_language_det",
        "PTE", "student_language_pte",
        "LANGUAGECERT", "student_language_languagecert",
        "CAMBRIDGE", "student_language_cambridge",
        "OTHER", "student_language_other"
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
    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final AiPromptRuntimeService aiPromptRuntimeService;

    /**
     * 创建 StudentProfileGuidedServiceImpl 实例。
     */
    public StudentProfileGuidedServiceImpl(
        JwtService jwtService,
        StudentProfileGuidedMapper guidedMapper,
        ObjectMapper objectMapper,
        BusinessProfilePersistenceService businessProfilePersistenceService,
        AiPromptRuntimeService aiPromptRuntimeService
    ) {
        this.jwtService = jwtService;
        this.guidedMapper = guidedMapper;
        this.objectMapper = objectMapper;
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.aiPromptRuntimeService = aiPromptRuntimeService;
    }

    /**
     * {@inheritDoc}
     */
    public List<Map<String, Object>> listQuestions() {
        return QUESTIONS.stream().map(this::copyQuestion).toList();
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
        if ("completed".equals(session.sessionStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "该固定问卷已完成，如需重填请重新开始");
        }

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
        Map<String, Object> enrichedQuestion = serializeQuestion(question, answers, null, null);
        Map<String, Object> normalizedAnswer;
        if (isTrue(rawAnswer, "skip")) {
            normalizedAnswer = new LinkedHashMap<>();
            normalizedAnswer.put("skipped", true);
        } else {
            normalizedAnswer = normalizeAnswer(enrichedQuestion, rawAnswer == null ? Map.of() : rawAnswer, answers);
        }
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
            generateGuidedResultForStudent(studentId, sessionId, "completed");
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
        generateGuidedResultForStudent(studentId, sessionId, triggerReason);
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
        return generateGuidedResultForStudent(studentId, sessionId, triggerReason);
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
        String triggerReason
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
        Map<String, Object> radarScores = normalizeRadarScores(
            firstNonNull(scoringJson.get("radar_scores_json"), scoringJson.get("radar_scores")),
            fallbackRadarScores(answers)
        );
        String summaryText = StrUtil.blankToDefault(
            string(scoringJson.get("summary_text")).trim(),
            "固定问卷建档结果已生成。"
        );
        String resultStatus = "saved";
        String saveErrorMessage = null;
        try {
            businessProfilePersistenceService.persistArchiveFormSnapshot(dbPayload, studentId);
        } catch (Exception exception) {
            resultStatus = "failed";
            saveErrorMessage = exception.getMessage();
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
        List<String> standardizedTests = selectedValues(answers.get("Q12"));

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
        profile.putAll(buildLanguageProfile(studentId, languageType, answers.get("Q10"), selectedValue(answers.get("Q11"))));
        Map<String, Object> standardizedPayload = buildStandardizedProfile(studentId, new LinkedHashSet<>(standardizedTests), answers, selectedValue(answers.get("Q14")));
        mergeNestedMap(profile, standardizedPayload, "student_academic_ib_profile");
        profile.putAll(standardizedPayload);
        profile.putAll(buildExperienceProfile(studentId, answers));
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
        profile.put("student_academic_a_level_profile", new LinkedHashMap<String, Object>());
        profile.put("student_academic_ap_profile", new LinkedHashMap<String, Object>());
        profile.put("student_academic_ib_profile", new LinkedHashMap<String, Object>());
        profile.put("student_academic_chinese_high_school_profile", new LinkedHashMap<String, Object>());
        profile.put("student_academic_chinese_high_school_subject", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_us_high_school_profile", new LinkedHashMap<String, Object>());
        profile.put("student_academic_us_high_school_course", new ArrayList<Map<String, Object>>());
        profile.put("student_academic_other_curriculum_profile", new LinkedHashMap<String, Object>());

        Map<String, Object> answer = q8 == null ? Map.of() : q8;
        if ("CHINESE_HIGH_SCHOOL".equals(curriculum)) {
            profile.put(
                "student_academic_chinese_high_school_profile",
                mapOfEntries(
                    "student_id",
                    studentId,
                    "grading_system_code",
                    firstNonBlank(selectedText(answer, "grading_system_code"), inferChineseHighSchoolGradingSystem(answer)),
                    "average_score_100",
                    decimalValue(answer.get("average_score_100")),
                    "gpa",
                    decimalValue(answer.get("gpa")),
                    "gpa_scale",
                    decimalValue(answer.get("gpa_scale"))
                )
            );
            return;
        }

        Map<String, Object> schoolPayload = mapOfEntries(
            "student_id",
            studentId,
            "gpa",
            decimalValue(answer.get("gpa")),
            "gpa_scale",
            decimalValue(answer.get("gpa_scale"))
        );
        if ("A_LEVEL".equals(curriculum)) {
            profile.put("student_academic_a_level_profile", schoolPayload);
        } else if ("AP".equals(curriculum)) {
            profile.put("student_academic_ap_profile", schoolPayload);
        } else if ("IB".equals(curriculum)) {
            profile.put("student_academic_ib_profile", schoolPayload);
        } else if ("US_HIGH_SCHOOL".equals(curriculum)) {
            schoolPayload.put("course_load_rigor_notes", nullableString(answer.get("strong_courses")));
            profile.put("student_academic_us_high_school_profile", schoolPayload);
        } else if ("INTERNATIONAL_OTHER".equals(curriculum) || "OTHER".equals(curriculum)) {
            schoolPayload.put("curriculum_scope_code", curriculum);
            schoolPayload.put("notes", nullableString(answer.get("strong_courses")));
            profile.put("student_academic_other_curriculum_profile", schoolPayload);
        }
    }

    private Map<String, Object> buildLanguageProfile(
        String studentId,
        String languageType,
        Map<String, Object> q10,
        String q11
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
            "student_language",
            mapOfEntries(
                "student_id",
                studentId,
                "best_test_type_code",
                languageType != null && !"NO_SCORE".equals(languageType) ? languageType : null,
                "best_score_status_code",
                languageType != null && !"NO_SCORE".equals(languageType)
                    ? StrUtil.blankToDefault(selectedText(q10, "status_code"), "SCORED")
                    : "ESTIMATED"
            )
        );
        for (String tableName : GUIDED_LANGUAGE_TEST_DETAIL_TABLES.values()) {
            payload.put(tableName, new ArrayList<Map<String, Object>>());
        }

        String tableName = GUIDED_LANGUAGE_TEST_DETAIL_TABLES.get(languageType == null ? "" : languageType);
        if (tableName != null && q10 != null && !Boolean.TRUE.equals(q10.get("skipped")) && hasAnyNonBlank(q10)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", studentId);
            row.put("is_best_score", 1);
            for (Map.Entry<String, Object> entry : q10.entrySet()) {
                String key = entry.getKey();
                if ("skipped".equals(key)) {
                    continue;
                }
                row.put(key, coerceProfileValue(key, entry.getValue()));
            }
            if ("student_language_other".equals(tableName) && nullableString(row.get("test_name")) == null) {
                row.put("test_name", "其他语言考试");
            }
            if (nullableString(row.get("status_code")) == null && hasAnyNonBlank(row)) {
                row.put("status_code", "SCORED");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get(tableName);
            rows.add(row);
        } else if (languageType == null || "NO_SCORE".equals(languageType)) {
            String estimatedTable = q11 != null && q11.startsWith("TOEFL") ? "student_language_toefl_ibt" : q11 != null && q11.startsWith("IELTS") ? "student_language_ielts" : null;
            if (estimatedTable != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get(estimatedTable);
                rows.add(
                    mapOfEntries(
                        "student_id",
                        studentId,
                        "status_code",
                        "ESTIMATED",
                        "estimated_total",
                        estimatedLanguageScore(q11),
                        "is_best_score",
                        1
                    )
                );
            }
        }
        return payload;
    }

    private Map<String, Object> buildStandardizedProfile(
        String studentId,
        Set<String> selectedTests,
        Map<String, Map<String, Object>> answers,
        String estimatedValue
    ) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<Map<String, Object>> aLevelRows = new ArrayList<>();
        List<Map<String, Object>> apRows = new ArrayList<>();
        List<Map<String, Object>> ibRows = new ArrayList<>();
        Map<String, Object> ibProfile = new LinkedHashMap<>();

        if (selectedTests.contains("A_LEVEL")) {
            aLevelRows.addAll(aLevelSubjectRows(studentId, answers.get("Q13_A_LEVEL"), "FULL_A_LEVEL"));
        }
        if (selectedTests.contains("AS")) {
            aLevelRows.addAll(aLevelSubjectRows(studentId, answers.get("Q13_AS"), "AS"));
        }
        if (selectedTests.contains("AP")) {
            apRows.addAll(apCourseRows(studentId, answers.get("Q13_AP")));
        }
        if (selectedTests.contains("IB")) {
            Map<String, Object> ibAnswer = answers.get("Q13_IB");
            Integer ibTotal = nullableInt(ibAnswer == null ? null : ibAnswer.get("ib_total"));
            if (ibTotal != null) {
                ibProfile.put("student_id", studentId);
                ibProfile.put("ib_total_predicted", ibTotal);
            }
            ibRows.addAll(ibSubjectRows(studentId, ibAnswer));
        }

        if (selectedTests.contains("SAT")) {
            Map<String, Object> sat = answers.get("Q13_SAT");
            if (sat == null) {
                sat = Map.of();
            }
            Map<String, Object> record = mapOfEntries(
                "student_id",
                studentId,
                "test_type",
                "SAT",
                "status",
                StrUtil.blankToDefault(selectedText(sat, "sat_status"), hasAnyValue(sat, "sat_total", "sat_ebrw", "sat_math") ? "SCORED" : "PLANNED"),
                "total_score",
                nullableInt(sat.get("sat_total")),
                "sat_erw",
                nullableInt(sat.get("sat_ebrw")),
                "sat_math",
                nullableInt(sat.get("sat_math")),
                "is_best_score",
                1
            );
            if (hasAnyNonBlank(record)) {
                records.add(record);
            }
        }
        if (selectedTests.contains("ACT")) {
            Map<String, Object> act = answers.get("Q13_ACT");
            if (act == null) {
                act = Map.of();
            }
            Map<String, Object> record = mapOfEntries(
                "student_id",
                studentId,
                "test_type",
                "ACT",
                "status",
                StrUtil.blankToDefault(selectedText(act, "act_status"), hasAnyValue(act, "act_total", "act_english", "act_math", "act_reading", "act_science") ? "SCORED" : "PLANNED"),
                "total_score",
                nullableInt(act.get("act_total")),
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
                records.add(record);
            }
        }
        if (estimatedValue != null) {
            records.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "test_type",
                    estimatedStandardizedTestType(estimatedValue),
                    "status",
                    "ESTIMATED",
                    "estimated_total_score",
                    estimatedStandardizedScore(estimatedValue),
                    "is_best_score",
                    1
                )
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("student_academic_a_level_subject", aLevelRows);
        payload.put("student_academic_ap_course", apRows);
        payload.put("student_academic_ib_profile", ibProfile);
        payload.put("student_academic_ib_subject", ibRows);
        payload.put(
            "student_standardized_tests",
            mapOfEntries(
                "student_id",
                studentId,
                "is_applicable",
                selectedTests.size() == 1 && selectedTests.contains("NONE") ? 0 : 1,
                "best_test_type",
                records.isEmpty() ? null : records.get(0).get("test_type"),
                "best_total_score",
                records.isEmpty() ? null : records.get(0).get("total_score")
            )
        );
        payload.put("student_standardized_test_records", records);
        return payload;
    }

    private Map<String, Object> buildExperienceProfile(String studentId, Map<String, Map<String, Object>> answers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("student_competitions", mapOfEntries("student_id", studentId));
        payload.put("student_activities", mapOfEntries("student_id", studentId));
        payload.put("student_projects_experience", mapOfEntries("student_id", studentId));
        payload.put("student_competition_entries", experienceRows(studentId, answers.get("Q15"), "competition"));
        payload.put("student_activity_entries", experienceRows(studentId, answers.get("Q16"), "activity"));
        payload.put("student_project_entries", experienceRows(studentId, answers.get("Q17"), "project"));
        payload.put("student_project_outputs", new ArrayList<Map<String, Object>>());
        return payload;
    }

    private Map<String, Object> normalizeRadarScores(Object rawScores, Map<String, Object> fallbackScores) {
        Map<String, Object> source = rawScores instanceof Map<?, ?> rawMap ? toStringKeyMap(rawMap) : new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String dimension : List.of("academic", "language", "standardized", "competition", "activity", "project")) {
            Object rawValue = source.get(dimension);
            Map<String, Object> fallback = fallbackScores.get(dimension) instanceof Map<?, ?> map ? toStringKeyMap(map) : scoreReason(50, "本地兜底评分。");
            int score;
            String reason;
            if (rawValue instanceof Number number) {
                score = number.intValue();
                reason = string(fallback.get("reason"));
            } else if (rawValue instanceof Map<?, ?> map) {
                Map<String, Object> scoreMap = toStringKeyMap(map);
                score = intValue(firstNonNull(scoreMap.get("score"), fallback.get("score")));
                reason = StrUtil.blankToDefault(string(scoreMap.get("reason")).trim(), string(fallback.get("reason")));
            } else {
                score = intValue(fallback.get("score"));
                reason = string(fallback.get("reason"));
            }
            result.put(dimension, scoreReason(Math.max(0, Math.min(100, score)), reason));
        }
        return result;
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
        if ("LT_50".equals(value)) {
            return 50;
        }
        if ("50_100".equals(value)) {
            return 100;
        }
        if ("100_200".equals(value)) {
            return 200;
        }
        if ("200_500".equals(value)) {
            return 500;
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
            String subject = nullableString(row.get("subject"));
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

    private List<Map<String, Object>> apCourseRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subject = nullableString(row.get("subject"));
            if (subject == null) {
                continue;
            }
            rows.add(
                mapOfEntries(
                    "student_id",
                    studentId,
                    "ap_course_id",
                    subject,
                    "score",
                    nullableInt(row.get("score")),
                    "is_predicted",
                    isPredictedStatus(row.get("status"))
                )
            );
        }
        return rows;
    }

    private List<Map<String, Object>> ibSubjectRows(String studentId, Map<String, Object> answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : repeatableRows(answer)) {
            String subject = nullableString(row.get("subject"));
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

    private List<Map<String, Object>> experienceRows(String studentId, Map<String, Object> answer, String kind) {
        if (answer == null || !"yes".equals(selectedText(answer, "has_experience"))) {
            return new ArrayList<>();
        }
        Map<String, Object> base = mapOfEntries("student_id", studentId, "sort_order", 1);
        if ("competition".equals(kind)) {
            base.put("competition_name", nullableString(answer.get("name")));
            base.put("competition_field", StrUtil.blankToDefault(nullableString(answer.get("competition_field")), "OTHER"));
            base.put("competition_tier", StrUtil.blankToDefault(nullableString(answer.get("competition_tier")), "UNKNOWN"));
            base.put("competition_level", StrUtil.blankToDefault(nullableString(answer.get("competition_level")), "INTERNATIONAL"));
            base.put("result_text", nullableString(answer.get("result_or_output")));
            base.put("evidence_type", StrUtil.blankToDefault(nullableString(answer.get("evidence_type")), "NONE"));
            base.put("evidence_link_or_note", nullableString(answer.get("evidence")));
        } else if ("activity".equals(kind)) {
            base.put("activity_name", nullableString(answer.get("name")));
            base.put("activity_category", StrUtil.blankToDefault(nullableString(answer.get("activity_category")), "OTHER"));
            base.put("activity_role", StrUtil.blankToDefault(nullableString(answer.get("activity_role")), "OTHER"));
            base.put("duration_months", nullableInt(answer.get("duration_months")));
            base.put("weekly_hours", decimalValue(answer.get("weekly_hours")));
            base.put("awards_or_media", nullableString(answer.get("result_or_output")));
            base.put("evidence_type", StrUtil.blankToDefault(nullableString(answer.get("evidence_type")), "NONE"));
            base.put("evidence_link_or_note", nullableString(answer.get("evidence")));
        } else {
            base.put("project_name", nullableString(answer.get("name")));
            base.put("project_type", StrUtil.blankToDefault(nullableString(answer.get("project_type")), "OTHER"));
            base.put("project_field", StrUtil.blankToDefault(nullableString(answer.get("project_field")), "OTHER"));
            base.put("relevance_to_major", StrUtil.blankToDefault(nullableString(answer.get("relevance_to_major")), "MEDIUM"));
            base.put("hours_total", decimalValue(answer.get("hours_total")));
            base.put("evidence_type", StrUtil.blankToDefault(nullableString(answer.get("evidence_type")), "NONE"));
            base.put("evidence_link_or_note", nullableString(answer.get("evidence")));
        }
        return new ArrayList<>(List.of(base));
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

    private String inferChineseHighSchoolGradingSystem(Map<String, Object> answer) {
        if (answer == null) {
            return null;
        }
        if (nullableString(answer.get("average_score_100")) != null) {
            return "PERCENTAGE";
        }
        if (nullableString(answer.get("gpa")) != null) {
            return "GPA";
        }
        return null;
    }

    private Object coerceProfileValue(String fieldName, Object value) {
        if (value == null || string(value).trim().isBlank()) {
            return null;
        }
        if (fieldName.endsWith("_json") && (value instanceof Map<?, ?> || value instanceof List<?>)) {
            return toJson(value);
        }
        if (fieldName.endsWith("_score")
            || fieldName.endsWith("_total")
            || fieldName.contains("score")
            || fieldName.contains("gpa")
            || fieldName.contains("estimated_total")) {
            BigDecimal decimal = decimalValue(value);
            return decimal == null ? value : decimal;
        }
        if ("is_best_score".equals(fieldName)) {
            return isTrue(Map.of("value", value), "value") ? 1 : 0;
        }
        return value;
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

    private String estimatedStandardizedTestType(String value) {
        if (value == null) {
            return "OTHER";
        }
        if (value.startsWith("SAT")) {
            return "SAT";
        }
        if (value.startsWith("ACT")) {
            return "ACT";
        }
        if (value.startsWith("A_LEVEL")) {
            return "A_LEVEL";
        }
        if (value.startsWith("AP")) {
            return "AP";
        }
        if (value.startsWith("IB")) {
            return "IB";
        }
        return "OTHER";
    }

    private Integer estimatedStandardizedScore(String value) {
        return switch (string(value)) {
            case "SAT_1450_1500" -> 1450;
            case "SAT_1500_1550" -> 1500;
            case "ACT_32_33" -> 32;
            case "ACT_34_36" -> 34;
            case "IB_40_PLUS" -> 40;
            default -> null;
        };
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

    private String optionLabelForQuestion(String questionCode, String value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> question = QUESTION_BY_CODE.get(questionCode);
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
                "基于课程体系、校内排名和校内成绩完整度的本地兜底评分。"
            )
        );
        result.put(
            "language",
            scoreReason(
                Math.min(100, 30 + 20 * boolInt(answers.containsKey("Q9")) + 30 * boolInt(answers.containsKey("Q10") || answers.containsKey("Q11"))),
                "基于语言考试类型和正式或预估成绩完整度的本地兜底评分。"
            )
        );
        result.put(
            "standardized",
            scoreReason(
                Math.min(100, 30 + 20 * boolInt(answers.containsKey("Q12")) + 30 * boolInt(hasAnyStandardizedScoreAnswer(answers) || answers.containsKey("Q14"))),
                "基于 SAT/ACT 或其他外部考试信息完整度的本地兜底评分。"
            )
        );
        result.put(
            "competition",
            scoreReason("yes".equals(selectedText(answers.get("Q15"), "has_experience")) ? 75 : 40, "基于是否已有代表性竞赛经历的本地兜底评分。")
        );
        result.put(
            "activity",
            scoreReason("yes".equals(selectedText(answers.get("Q16"), "has_experience")) ? 75 : 40, "基于是否已有长期活动或领导力经历的本地兜底评分。")
        );
        result.put(
            "project",
            scoreReason("yes".equals(selectedText(answers.get("Q17"), "has_experience")) ? 75 : 40, "基于是否已有研究、实习或项目经历的本地兜底评分。")
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
        Map<String, Object> row = guidedMapper.findOwnedSession(studentId, sessionId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "guided session not found");
        }
        return mapSessionRow(row);
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
        if (options != null) {
            result.put("options", options);
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
            if ("Q13".equals(code)) {
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
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : selectedValues(answers.get("Q12"))) {
            if ("NONE".equals(value) || !STANDARDIZED_SCORE_QUESTION_TEMPLATES.containsKey(value) || seen.contains(value)) {
                continue;
            }
            result.add(value);
            seen.add(value);
        }
        return result;
    }

    private boolean isQuestionVisible(Map<String, Object> question, Map<String, Map<String, Object>> answers) {
        String code = string(question.get("code"));
        if (STANDARDIZED_SCORE_QUESTION_TEMPLATES.values().stream().anyMatch(item -> code.equals(item.get("code")))) {
            return standardizedScoreQuestions(answers).stream().anyMatch(item -> code.equals(item.get("code")));
        }
        if ("Q8".equals(code)) {
            return selectedValue(answers.get("Q5")) != null;
        }
        if ("Q10".equals(code)) {
            String languageType = selectedValue(answers.get("Q9"));
            return languageType != null && !"NO_SCORE".equals(languageType);
        }
        if ("Q11".equals(code)) {
            Map<String, Object> q9Answer = answers.get("Q9");
            return "NO_SCORE".equals(selectedValue(q9Answer)) || isTrue(q9Answer, "skipped");
        }
        if ("Q13".equals(code)) {
            return false;
        }
        if ("Q14".equals(code)) {
            Set<String> values = new LinkedHashSet<>(selectedValues(answers.get("Q12")));
            if (values.isEmpty()) {
                return false;
            }
            if (values.contains("NONE")) {
                return true;
            }
            return !hasScoredExternalExamAnswerFromAnswers(answers);
        }
        return true;
    }

    private boolean hasScoredExternalExamAnswerFromAnswers(Map<String, Map<String, Object>> answers) {
        for (String examType : selectedStandardizedTestsInOrder(answers)) {
            Map<String, Object> template = STANDARDIZED_SCORE_QUESTION_TEMPLATES.get(examType);
            Map<String, Object> answer = template == null ? null : answers.get(string(template.get("code")));
            if (answer == null) {
                continue;
            }
            if ("A_LEVEL".equals(examType) || "AS".equals(examType)) {
                if (repeatableRowsHaveScoredValue(answer, "grade")) {
                    return true;
                }
            } else if ("AP".equals(examType)) {
                if (repeatableRowsHaveScoredValue(answer, "score")) {
                    return true;
                }
            } else if ("IB".equals(examType)) {
                if (hasAnyValue(answer, "ib_total") || repeatableRowsHaveScoredValue(answer, "score")) {
                    return true;
                }
            } else if ("SAT".equals(examType)) {
                if ("SCORED".equals(selectedText(answer, "sat_status"))
                    || (!Set.of("PLANNED", "ESTIMATED").contains(selectedText(answer, "sat_status"))
                    && hasAnyValue(answer, "sat_total", "sat_ebrw", "sat_math"))) {
                    return true;
                }
            } else if ("ACT".equals(examType)
                && ("SCORED".equals(selectedText(answer, "act_status"))
                || (!Set.of("PLANNED", "ESTIMATED").contains(selectedText(answer, "act_status"))
                && hasAnyValue(answer, "act_total", "act_english", "act_math", "act_reading", "act_science")))) {
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
            case "Q1" -> options(
                opt("G9", "G9 (初三)"),
                opt("G10", "G10 (高一)"),
                opt("G11", "G11 (高二)"),
                opt("G12", "G12 (高三)"),
                opt("Gap Year", "Gap Year"),
                opt("TRANSFER_YEAR_1", "大一转学申请"),
                opt("OTHER", "其他")
            );
            case "Q3" -> options(
                opt("US", "美国"),
                opt("UK", "英国"),
                opt("HK", "中国香港"),
                opt("SG", "新加坡"),
                opt("CA", "加拿大"),
                opt("AU", "澳大利亚"),
                opt("EU", "欧洲"),
                opt("OTHER", "其他")
            );
            case "Q4" -> options(
                opt("CS_AI_DS", "计算机 / AI / 数据科学"),
                opt("MATH_STATS", "数学 / 统计"),
                opt("ENGINEERING", "工程"),
                opt("BUSINESS_ECON_FINANCE", "经济 / 金融 / 商科"),
                opt("SCIENCE", "物理 / 化学 / 生物"),
                opt("PSY_EDU", "心理 / 教育"),
                opt("SOCIAL_SCIENCE", "社科 / 政治 / 国际关系"),
                opt("MEDIA_COMM", "传媒 / 新闻 / 传播"),
                opt("LAW", "法学相关"),
                opt("ART_DESIGN_ARCH", "艺术 / 设计 / 建筑"),
                opt("UNDECIDED", "还不确定")
            );
            case "Q5" -> options(
                opt("A_LEVEL", "A-Level"),
                opt("AP", "AP"),
                opt("IB", "IB"),
                opt("CHINESE_HIGH_SCHOOL", "普高"),
                opt("US_HIGH_SCHOOL", "国际学校美高体系"),
                opt("INTERNATIONAL_OTHER", "国际学校其他体系"),
                opt("OTHER", "其他")
            );
            case "Q9" -> options(
                opt("IELTS", "雅思"),
                opt("TOEFL_IBT", "托福 iBT"),
                opt("TOEFL_ESSENTIALS", "托福 Essentials"),
                opt("DET", "多邻国"),
                opt("PTE", "PTE"),
                opt("LANGUAGECERT", "LanguageCert"),
                opt("CAMBRIDGE", "剑桥英语"),
                opt("OTHER", "其他语言考试")
            );
            default -> null;
        };
    }

    private List<Map<String, Object>> fieldsForQuestion(
        String questionCode,
        Map<String, Map<String, Object>> answers
    ) {
        if ("Q8".equals(questionCode)) {
            String curriculum = selectedValue(answers.get("Q5"));
            if ("CHINESE_HIGH_SCHOOL".equals(curriculum)) {
                List<Map<String, Object>> fields = new ArrayList<>();
                fields.add(selectField("grading_system_code", "评分体系", options(opt("PERCENTAGE", "百分制"), opt("GPA", "GPA"), opt("OTHER", "其他"))));
                fields.add(inputField("average_score_100", "百分制平均分", "number"));
                fields.add(inputField("gpa", "GPA", "number"));
                fields.add(inputField("gpa_scale", "GPA 满分", "number"));
                for (int index = 1; index <= 6; index += 1) {
                    fields.add(selectField("chs_subject_" + index + "_id", "普高科目 " + index, chineseSubjectOptions()));
                    fields.add(inputField("chs_subject_" + index + "_score_100", "科目 " + index + " 百分制分数", "number"));
                }
                return fields;
            }
            if (curriculum != null) {
                return List.of(
                    inputField("gpa", "GPA", "number"),
                    inputField("gpa_scale", "满分", "number"),
                    selectField("is_weighted", "是否加权", options(opt("yes", "是"), opt("no", "否"), opt("unknown", "不清楚")))
                );
            }
            return List.of();
        }
        if ("Q10".equals(questionCode)) {
            return fallbackLanguageDetailFields(GUIDED_LANGUAGE_TEST_DETAIL_TABLES.get(selectedValue(answers.get("Q9"))));
        }
        if ("Q13_A_LEVEL".equals(questionCode)) {
            return List.of(selectField("a_level_board", "A-Level 考试局", A_LEVEL_BOARD_OPTIONS));
        }
        if ("Q13_AS".equals(questionCode)) {
            return List.of(selectField("a_level_board", "AS 考试局", A_LEVEL_BOARD_OPTIONS));
        }
        if ("Q13_IB".equals(questionCode)) {
            return List.of(inputField("ib_total", "IB 总分", "number"));
        }
        if ("Q13_SAT".equals(questionCode)) {
            return List.of(
                selectField("sat_status", "考试状态", SCORE_STATUS_OPTIONS),
                inputField("sat_total", "SAT 总分", "number"),
                inputField("sat_ebrw", "EBRW", "number"),
                inputField("sat_math", "Math", "number")
            );
        }
        if ("Q13_ACT".equals(questionCode)) {
            return List.of(
                selectField("act_status", "考试状态", SCORE_STATUS_OPTIONS),
                inputField("act_total", "ACT 总分", "number"),
                inputField("act_english", "English", "number"),
                inputField("act_math", "Math", "number"),
                inputField("act_reading", "Reading", "number"),
                inputField("act_science", "Science", "number")
            );
        }
        if (Set.of("Q15", "Q16", "Q17").contains(questionCode)) {
            return experienceFields(questionCode);
        }
        return List.of();
    }

    private List<Map<String, Object>> rowFieldsForQuestion(String questionCode) {
        if ("Q13_A_LEVEL".equals(questionCode) || "Q13_AS".equals(questionCode)) {
            return List.of(
                selectField("subject", "科目", aLevelSubjectOptions()),
                selectField("grade", "成绩", A_LEVEL_GRADE_OPTIONS),
                selectField("status", "状态", SCORE_STATUS_OPTIONS)
            );
        }
        if ("Q13_AP".equals(questionCode)) {
            return List.of(
                selectField("subject", "AP 科目", apCourseOptions()),
                selectField("score", "分数", AP_SCORE_OPTIONS),
                selectField("status", "状态", SCORE_STATUS_OPTIONS)
            );
        }
        if ("Q13_IB".equals(questionCode)) {
            return List.of(
                selectField("subject", "IB 科目", ibSubjectOptions()),
                selectField("level", "级别", IB_LEVEL_OPTIONS),
                selectField("score", "分数", IB_SCORE_OPTIONS),
                selectField("status", "状态", SCORE_STATUS_OPTIONS)
            );
        }
        return List.of();
    }

    private List<Map<String, Object>> fallbackLanguageDetailFields(String tableName) {
        if ("student_language_ielts".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("overall_score", "总分", "number"),
                inputField("listening_score", "听力分", "number"),
                inputField("reading_score", "阅读分", "number"),
                inputField("writing_score", "写作分", "number"),
                inputField("speaking_score", "口语分", "number")
            );
        }
        if ("student_language_toefl_ibt".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("total_score", "总分", "number"),
                inputField("reading_score", "阅读分", "number"),
                inputField("listening_score", "听力分", "number"),
                inputField("speaking_score", "口语分", "number"),
                inputField("writing_score", "写作分", "number")
            );
        }
        if ("student_language_toefl_essentials".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("core_score_1_12", "核心总分", "number"),
                inputField("literacy_1_12", "读写能力分", "number"),
                inputField("conversation_1_12", "会话能力分", "number"),
                inputField("comprehension_1_12", "理解能力分", "number")
            );
        }
        if ("student_language_det".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("total_score", "总分", "number"),
                inputField("literacy_score", "Literacy 分", "number"),
                inputField("comprehension_score", "Comprehension 分", "number"),
                inputField("conversation_score", "Conversation 分", "number"),
                inputField("production_score", "Production 分", "number")
            );
        }
        if ("student_language_pte".equals(tableName) || "student_language_languagecert".equals(tableName)) {
            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS));
            fields.add(inputField("test_date", "考试日期", "date"));
            fields.add(inputField("total_score", "总分", "number"));
            fields.add(inputField("reading_score", "阅读分", "number"));
            fields.add(inputField("listening_score", "听力分", "number"));
            fields.add(inputField("speaking_score", "口语分", "number"));
            fields.add(inputField("writing_score", "写作分", "number"));
            if ("student_language_languagecert".equals(tableName)) {
                fields.add(selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS));
            }
            return fields;
        }
        if ("student_language_cambridge".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                inputField("total_score", "总分", "number"),
                inputField("reading_score", "阅读分", "number"),
                inputField("use_of_english_score", "Use of English 分", "number"),
                inputField("listening_score", "听力分", "number"),
                inputField("speaking_score", "口语分", "number"),
                inputField("writing_score", "写作分", "number"),
                selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS)
            );
        }
        if ("student_language_other".equals(tableName)) {
            return List.of(
                selectField("status_code", "成绩状态", SCORE_STATUS_OPTIONS),
                inputField("test_date", "考试日期", "date"),
                textField("test_name", "考试名称"),
                inputField("score_total", "总分", "number"),
                textField("band_or_scale_desc", "分制说明"),
                selectField("cefr_level_code", "CEFR 等级", CEFR_LEVEL_OPTIONS),
                textareaField("score_breakdown_json", "分项成绩 JSON")
            );
        }
        return List.of();
    }

    private List<Map<String, Object>> experienceFields(String questionCode) {
        String label = switch (questionCode) {
            case "Q15" -> "竞赛";
            case "Q16" -> "活动";
            default -> "项目";
        };
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(selectField("has_experience", "是否有" + label + "经历", options(opt("yes", "有"), opt("no", "暂时没有"))));
        fields.add(textField("name", label + "名称"));
        if ("Q15".equals(questionCode)) {
            fields.add(selectField("competition_field", "竞赛领域", COMPETITION_FIELD_OPTIONS));
            fields.add(selectField("competition_tier", "竞赛层级", COMPETITION_TIER_OPTIONS));
            fields.add(selectField("competition_level", "竞赛级别", COMPETITION_LEVEL_OPTIONS));
            fields.add(textField("result_or_output", "结果描述"));
            fields.add(selectField("evidence_type", "佐证类型", COMPETITION_EVIDENCE_OPTIONS));
        } else if ("Q16".equals(questionCode)) {
            fields.add(selectField("activity_category", "活动类别", ACTIVITY_CATEGORY_OPTIONS));
            fields.add(selectField("activity_role", "活动角色", ACTIVITY_ROLE_OPTIONS));
            fields.add(inputField("duration_months", "持续月数", "number"));
            fields.add(inputField("weekly_hours", "每周投入小时", "number"));
            fields.add(textField("result_or_output", "影响 / 获奖 / 媒体报道"));
            fields.add(selectField("evidence_type", "佐证类型", ACTIVITY_EVIDENCE_OPTIONS));
        } else {
            fields.add(selectField("project_type", "项目类型", PROJECT_TYPE_OPTIONS));
            fields.add(selectField("project_field", "项目领域", PROJECT_FIELD_OPTIONS));
            fields.add(selectField("relevance_to_major", "与专业相关性", RELEVANCE_OPTIONS));
            fields.add(inputField("hours_total", "总投入小时", "number"));
            fields.add(textField("result_or_output", "产出 / 结果"));
            fields.add(selectField("evidence_type", "佐证类型", PROJECT_EVIDENCE_OPTIONS));
        }
        fields.add(textareaField("evidence", "证明材料或补充说明"));
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
            opt("MATH", "Mathematics"),
            opt("FURTHER_MATH", "Further Mathematics"),
            opt("PHYSICS", "Physics"),
            opt("CHEMISTRY", "Chemistry"),
            opt("BIOLOGY", "Biology"),
            opt("ECONOMICS", "Economics"),
            opt("BUSINESS", "Business"),
            opt("COMPUTER_SCIENCE", "Computer Science"),
            opt("OTHER", "Other")
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
            opt("MATH_AA", "Mathematics AA"),
            opt("MATH_AI", "Mathematics AI"),
            opt("PHYSICS", "Physics"),
            opt("CHEMISTRY", "Chemistry"),
            opt("BIOLOGY", "Biology"),
            opt("ECONOMICS", "Economics"),
            opt("BUSINESS_MANAGEMENT", "Business Management"),
            opt("COMPUTER_SCIENCE", "Computer Science"),
            opt("OTHER", "Other")
        );
    }

    private boolean hasAnyStandardizedScoreAnswer(Map<String, Map<String, Object>> answers) {
        return answers.keySet().stream().anyMatch(key -> key.startsWith("Q13_"));
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
        questions.add(with(with(withOptions(question("Q4", "basic", "一、基础申请目标", "multi", "你目前感兴趣的专业方向是？最多选 2 个。"), options(
            opt("CS_AI_DS", "计算机 / AI / 数据科学"),
            opt("MATH_STATS", "数学 / 统计"),
            opt("ENGINEERING", "工程"),
            opt("BUSINESS_ECON_FINANCE", "经济 / 金融 / 商科"),
            opt("SCIENCE", "物理 / 化学 / 生物"),
            opt("PSY_EDU", "心理 / 教育"),
            opt("SOCIAL_SCIENCE", "社科 / 政治 / 国际关系"),
            opt("MEDIA_COMM", "传媒 / 新闻 / 传播"),
            opt("LAW", "法学相关"),
            opt("ART_DESIGN_ARCH", "艺术 / 设计 / 建筑"),
            opt("UNDECIDED", "还不确定")
        )), "max_select", 2, "searchable", true), "default_selected_values", List.of("UNDECIDED"), "exclusive_option_values", List.of("UNDECIDED")));
        questions.add(withOptions(question("Q5", "academic", "二、校内学术背景", "single", "你目前就读的课程体系是？"), options(
            opt("A_LEVEL", "A-Level"),
            opt("AP", "AP"),
            opt("IB", "IB"),
            opt("CHINESE_HIGH_SCHOOL", "普高"),
            opt("US_HIGH_SCHOOL", "国际学校美高体系"),
            opt("INTERNATIONAL_OTHER", "国际学校其他体系"),
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
            opt("LT_50", "50 人以下"),
            opt("50_100", "50-100 人"),
            opt("100_200", "100-200 人"),
            opt("200_500", "200-500 人"),
            opt("GT_500", "500 人以上"),
            opt("UNKNOWN", "不清楚"),
            opt("CUSTOM", "自填")
        )), "custom_input_type", "number", "custom_placeholder", "请输入具体人数"));
        questions.add(question("Q8", "academic", "二、校内学术背景", "branch_form", "你的校内成绩可以怎么填写？"));
        questions.add(withOptions(question("Q9", "language", "三、语言成绩", "single", "你目前有哪类正式语言成绩？"), options(
            opt("IELTS", "雅思"),
            opt("TOEFL_IBT", "托福 iBT"),
            opt("TOEFL_ESSENTIALS", "托福 Essentials"),
            opt("DET", "多邻国"),
            opt("PTE", "PTE"),
            opt("LANGUAGECERT", "LanguageCert"),
            opt("CAMBRIDGE", "剑桥英语"),
            opt("OTHER", "其他语言考试")
        )));
        questions.add(question("Q10", "language", "三、语言成绩", "branch_form", "请填写你已有的正式语言成绩。"));
        questions.add(withOptions(question("Q11", "language", "三、语言成绩", "single", "如果还没有正式语言成绩，你当前大致目标或预估水平是？"), options(
            opt("IELTS_6", "IELTS 6"),
            opt("IELTS_6_5", "IELTS 6.5"),
            opt("IELTS_7", "IELTS 7"),
            opt("IELTS_7_5_PLUS", "IELTS 7.5+"),
            opt("TOEFL_90", "TOEFL 90"),
            opt("TOEFL_100", "TOEFL 100"),
            opt("TOEFL_105_PLUS", "TOEFL 105+"),
            opt("OTHER", "其他")
        )));
        questions.add(with(withOptions(question("Q12", "standardized", "四、标化与外部考试", "multi", "你目前涉及哪些外部考试？可多选。"), options(
            opt("A_LEVEL", "A-Level"),
            opt("AS", "AS"),
            opt("AP", "AP"),
            opt("IB", "IB"),
            opt("SAT", "SAT"),
            opt("ACT", "ACT"),
            opt("NONE", "还没有")
        )), "exclusive_option_values", List.of("NONE")));
        questions.add(question("Q13", "standardized", "四、标化与外部考试", "branch_form", "请补充你已有或预估的外部考试成绩。"));
        questions.add(with(withOptions(question("Q14", "standardized", "四、标化与外部考试", "single", "如果还没有正式标化成绩，你当前大致目标或预估水平是？"), options(
            opt("SAT_1450_1500", "SAT 1450-1500"),
            opt("SAT_1500_1550", "SAT 1500-1550"),
            opt("ACT_32_33", "ACT 32-33"),
            opt("ACT_34_36", "ACT 34-36"),
            opt("A_LEVEL_ASTARAA", "A-Level A*A*A"),
            opt("AP_3X5", "AP 3 门 5 分"),
            opt("IB_40_PLUS", "IB 40+"),
            opt("UNSURE", "还不确定")
        )), "allow_custom_text", true, "custom_placeholder", "可补充预估分数或说明"));
        questions.add(question("Q15", "competition", "五、竞赛经历", "experience_form", "你是否有比较重要的竞赛经历？如有，请填写最有代表性的一项。"));
        questions.add(question("Q16", "activity", "六、活动经历", "experience_form", "你是否有长期活动、社团、志愿、领导力或创业相关经历？"));
        questions.add(question("Q17", "project", "七、项目经历", "experience_form", "你是否有研究、实习、工程、作品集或其他项目经历？"));
        return Collections.unmodifiableList(questions);
    }

    private static Map<String, Map<String, Object>> buildStandardizedScoreQuestionTemplates() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("A_LEVEL", with(question("Q13_A_LEVEL", "standardized", "四、标化与外部考试", "repeatable_form", "请填写你的 A-Level 成绩。"), "exam_type", "A_LEVEL", "min_rows", 1));
        result.put("IB", with(question("Q13_IB", "standardized", "四、标化与外部考试", "repeatable_form", "请填写你的 IB 成绩。"), "exam_type", "IB", "min_rows", 1));
        result.put("AS", with(question("Q13_AS", "standardized", "四、标化与外部考试", "repeatable_form", "请填写你的 AS 成绩。"), "exam_type", "AS", "min_rows", 1));
        result.put("AP", with(question("Q13_AP", "standardized", "四、标化与外部考试", "repeatable_form", "请填写你的 AP 成绩。"), "exam_type", "AP", "min_rows", 1));
        result.put("SAT", with(question("Q13_SAT", "standardized", "四、标化与外部考试", "branch_form", "请填写你的 SAT 成绩。"), "exam_type", "SAT"));
        result.put("ACT", with(question("Q13_ACT", "standardized", "四、标化与外部考试", "branch_form", "请填写你的 ACT 成绩。"), "exam_type", "ACT"));
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

    private static Map<String, Object> inputField(String name, String label, String inputType) {
        return field(name, label, "text", null, inputType);
    }

    private static Map<String, Object> textareaField(String name, String label) {
        return field(name, label, "textarea", null, null);
    }

    private static Map<String, Object> selectField(String name, String label, List<Map<String, String>> options) {
        return field(name, label, "select", options, null);
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
