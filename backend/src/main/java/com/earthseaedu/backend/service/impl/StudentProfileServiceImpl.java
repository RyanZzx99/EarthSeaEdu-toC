package com.earthseaedu.backend.service.impl;

import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import com.earthseaedu.backend.dto.studentprofile.StudentProfileRequests;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.AiChatPersistenceMapper;
import com.earthseaedu.backend.mapper.StudentProfileMapper;
import com.earthseaedu.backend.service.AiChatDraftService;
import com.earthseaedu.backend.service.AiChatReadService;
import com.earthseaedu.backend.service.AiProfileRadarPendingService;
import com.earthseaedu.backend.service.BusinessProfileFormMetadata;
import com.earthseaedu.backend.service.BusinessProfileFormService;
import com.earthseaedu.backend.service.BusinessProfilePersistenceService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.StudentProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生档案新链路服务实现。
 * 课程体系与语言考试的新逻辑独立挂载在此服务中，
 * 不直接改写旧建档服务链路。
 */
@Service
public class StudentProfileServiceImpl implements StudentProfileService {

    private static final String DEFAULT_BIZ_DOMAIN = "student_profile_build";
    private static final Set<String> BUSY_STAGES = Set.of(
        "progress_updating",
        "extraction",
        "scoring",
        "profile_saving"
    );
    private static final String LANGUAGE_PARENT_TABLE = "student_language";
    private static final String LANGUAGE_TEST_RECORD_TABLE = "student_language_test_record";
    private static final String LANGUAGE_TEST_SCORE_ITEM_TABLE = "student_language_test_score_item";
    private static final List<String> LANGUAGE_TABLE_ORDER = List.of(
        LANGUAGE_TEST_RECORD_TABLE,
        LANGUAGE_TEST_SCORE_ITEM_TABLE
    );
    private static final Set<String> LANGUAGE_TABLES = Set.copyOf(LANGUAGE_TABLE_ORDER);

    private static final String CURRICULUM_SYSTEM_TABLE = "student_basic_info_curriculum_system";
    private static final List<String> CURRICULUM_TABLE_ORDER = List.of(
        CURRICULUM_SYSTEM_TABLE,
        "student_academic_curriculum_gpa",
        "student_academic_us_high_school_subject",
        "student_academic_other_curriculum_subject",
        "student_academic_a_level_subject",
        "student_academic_ap_subject",
        "student_academic_ib_subject",
        "student_academic_chinese_high_school_subject",
        "student_academic_ossd_subject"
    );
    private static final Set<String> CURRICULUM_TABLES = Set.copyOf(CURRICULUM_TABLE_ORDER);
    private static final Set<String> CURRICULUM_SCOPED_TABLES = Set.of(
        "student_academic_curriculum_gpa",
        "student_academic_other_curriculum_subject"
    );
    private static final Map<String, String> CURRICULUM_MULTI_TABLE_BY_CODE = Map.of(
        "A_LEVEL", "student_academic_a_level_subject",
        "AP", "student_academic_ap_subject",
        "IB", "student_academic_ib_subject",
        "CHINESE_HIGH_SCHOOL", "student_academic_chinese_high_school_subject",
        "US_HIGH_SCHOOL", "student_academic_us_high_school_subject",
        "OSSD", "student_academic_ossd_subject",
        "INTERNATIONAL_OTHER", "student_academic_other_curriculum_subject",
        "OTHER", "student_academic_other_curriculum_subject"
    );
    private static final String STANDARDIZED_SAT_TABLE = "student_standardized_sat";
    private static final String STANDARDIZED_ACT_TABLE = "student_standardized_act";
    private static final List<String> STANDARDIZED_TABLE_ORDER = List.of(
        STANDARDIZED_SAT_TABLE,
        STANDARDIZED_ACT_TABLE
    );
    private static final Set<String> STANDARDIZED_TABLES = Set.copyOf(STANDARDIZED_TABLE_ORDER);
    private static final Set<String> CURRICULUM_MEANINGFULNESS_IGNORED_FIELDS = Set.of(
        "student_id",
        "is_primary",
        "create_time",
        "update_time",
        "delete_flag"
    );
    private static final Set<String> DICTIONARY_TABLES = Set.of(
        "dict_curriculum_system",
        "dict_gpa_scale",
        "dict_us_high_school_course",
        "dict_a_level_subject",
        "dict_ap_course",
        "dict_ib_subject",
        "dict_chinese_high_school_subject"
    );
    private static final Map<String, String> STATIC_FIELD_OPTIONS = Map.ofEntries(
        Map.entry(key(CURRICULUM_SYSTEM_TABLE, "curriculum_system_code"), "curriculum_system"),
        Map.entry(key("student_academic_curriculum_gpa", "curriculum_system_code"), "curriculum_system"),
        Map.entry(key("student_academic_curriculum_gpa", "gpa_scale_code"), "gpa_scale"),
        Map.entry(key("student_academic_other_curriculum_subject", "curriculum_system_code"), "curriculum_system"),
        Map.entry(key("student_academic_other_curriculum_subject", "score_scale_code"), "gpa_scale"),
        Map.entry(key("student_academic_us_high_school_subject", "us_high_school_course_id"), "us_high_school_course"),
        Map.entry(key("student_academic_a_level_subject", "al_subject_id"), "a_level_subject"),
        Map.entry(key("student_academic_ap_subject", "ap_course_id"), "ap_course"),
        Map.entry(key("student_academic_ib_subject", "ib_subject_id"), "ib_subject"),
        Map.entry(key("student_academic_chinese_high_school_subject", "chs_subject_id"), "chs_subject")
    );
    private static final Set<String> TEXTAREA_FIELDS = Set.of(
        "notes",
        "weighting_rule_notes",
        "activity_summary",
        "research_summary"
    );
    private static final Set<String> HIDDEN_FIELDS = Set.of("student_id");
    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> LEGACY_CURRICULUM_PROFILE_TABLES = Set.of(
        "student_academic_us_high_school_profile",
        "student_academic_other_curriculum_profile",
        "student_academic_a_level_profile",
        "student_academic_ap_profile",
        "student_academic_ib_profile",
        "student_academic_chinese_high_school_profile"
    );
    private static final Set<String> LEGACY_CURRICULUM_SUBJECT_TABLES = Set.of(
        "student_academic_us_high_school_course",
        "student_academic_ap_course"
    );
    private static final Set<String> LEGACY_LANGUAGE_DETAIL_TABLES = Set.of(
        "student_language_ielts",
        "student_language_toefl_ibt",
        "student_language_toefl_home",
        "student_language_toefl_essentials",
        "student_language_det",
        "student_language_pte",
        "student_language_languagecert",
        "student_language_languagecert_academic",
        "student_language_cambridge",
        "student_language_other"
    );
    private static final Set<String> LEGACY_STANDARDIZED_DETAIL_TABLES = Set.of(
        "student_standardized_tests",
        "student_standardized_test_records"
    );
    private static final String ACTIVITY_EXPERIENCE_TABLE = "student_activity_experience";
    private static final String ACTIVITY_ATTACHMENT_TABLE = "student_activity_attachment";
    private static final String ENTERPRISE_INTERNSHIP_TABLE = "student_enterprise_internship";
    private static final String ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE = "student_enterprise_internship_attachment";
    private static final String RESEARCH_EXPERIENCE_TABLE = "student_research_experience";
    private static final String RESEARCH_ATTACHMENT_TABLE = "student_research_attachment";
    private static final String COMPETITION_RECORD_TABLE = "student_competition_record";
    private static final String COMPETITION_ATTACHMENT_TABLE = "student_competition_attachment";
    private static final List<String> EXPERIENCE_MAIN_TABLE_ORDER = List.of(
        ACTIVITY_EXPERIENCE_TABLE,
        ENTERPRISE_INTERNSHIP_TABLE,
        RESEARCH_EXPERIENCE_TABLE,
        COMPETITION_RECORD_TABLE
    );
    private static final List<String> EXPERIENCE_ATTACHMENT_TABLE_ORDER = List.of(
        ACTIVITY_ATTACHMENT_TABLE,
        ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
        RESEARCH_ATTACHMENT_TABLE,
        COMPETITION_ATTACHMENT_TABLE
    );
    private static final List<String> EXPERIENCE_TABLE_ORDER = List.of(
        ACTIVITY_EXPERIENCE_TABLE,
        ACTIVITY_ATTACHMENT_TABLE,
        ENTERPRISE_INTERNSHIP_TABLE,
        ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
        RESEARCH_EXPERIENCE_TABLE,
        RESEARCH_ATTACHMENT_TABLE,
        COMPETITION_RECORD_TABLE,
        COMPETITION_ATTACHMENT_TABLE
    );
    private static final Set<String> EXPERIENCE_TABLES = Set.copyOf(EXPERIENCE_TABLE_ORDER);
    private static final Set<String> EXPERIENCE_ATTACHMENT_TABLES = Set.copyOf(EXPERIENCE_ATTACHMENT_TABLE_ORDER);
    private static final Set<String> LEGACY_EXPERIENCE_DETAIL_TABLES = Set.of(
        "student_competitions",
        "student_competition_entries",
        "student_activities",
        "student_activity_entries",
        "student_projects_experience",
        "student_project_entries",
        "student_project_outputs"
    );
    private static final Map<String, String> EXPERIENCE_TABLE_ID_FIELDS = Map.of(
        ACTIVITY_EXPERIENCE_TABLE, "student_activity_experience_id",
        ACTIVITY_ATTACHMENT_TABLE, "student_activity_attachment_id",
        ENTERPRISE_INTERNSHIP_TABLE, "student_enterprise_internship_id",
        ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE, "student_enterprise_internship_attachment_id",
        RESEARCH_EXPERIENCE_TABLE, "student_research_experience_id",
        RESEARCH_ATTACHMENT_TABLE, "student_research_attachment_id",
        COMPETITION_RECORD_TABLE, "student_competition_record_id",
        COMPETITION_ATTACHMENT_TABLE, "student_competition_attachment_id"
    );
    private static final Map<String, String> EXPERIENCE_ATTACHMENT_PARENT_FIELDS = Map.of(
        ACTIVITY_ATTACHMENT_TABLE, "student_activity_experience_id",
        ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE, "student_enterprise_internship_id",
        RESEARCH_ATTACHMENT_TABLE, "student_research_experience_id",
        COMPETITION_ATTACHMENT_TABLE, "student_competition_record_id"
    );
    private static final Map<String, String> EXPERIENCE_ATTACHMENT_PARENT_TABLES = Map.of(
        ACTIVITY_ATTACHMENT_TABLE, ACTIVITY_EXPERIENCE_TABLE,
        ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE, ENTERPRISE_INTERNSHIP_TABLE,
        RESEARCH_ATTACHMENT_TABLE, RESEARCH_EXPERIENCE_TABLE,
        COMPETITION_ATTACHMENT_TABLE, COMPETITION_RECORD_TABLE
    );
    private static final Set<String> AUTO_INCREMENT_FIELDS = Set.copyOf(EXPERIENCE_TABLE_ID_FIELDS.values());
    private static final Set<String> EXPERIENCE_ATTACHMENT_MANAGED_FIELDS = Set.of("sort_order");
    private static final Map<String, String> PROFILE_TABLE_LABEL_OVERRIDES = Map.ofEntries(
        Map.entry(ACTIVITY_EXPERIENCE_TABLE, "活动经历"),
        Map.entry(ACTIVITY_ATTACHMENT_TABLE, "活动附件"),
        Map.entry(ENTERPRISE_INTERNSHIP_TABLE, "企业实习"),
        Map.entry(ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE, "企业实习附件"),
        Map.entry(RESEARCH_EXPERIENCE_TABLE, "科研经历"),
        Map.entry(RESEARCH_ATTACHMENT_TABLE, "科研附件"),
        Map.entry(COMPETITION_RECORD_TABLE, "学术竞赛"),
        Map.entry(COMPETITION_ATTACHMENT_TABLE, "竞赛证书附件"),
        Map.entry("student_academic_ap_subject", "AP 科目成绩"),
        Map.entry("student_academic_us_high_school_subject", "美高科目成绩"),
        Map.entry(STANDARDIZED_SAT_TABLE, "SAT"),
        Map.entry(STANDARDIZED_ACT_TABLE, "ACT")
    );
    private static final Map<String, String> PROFILE_FIELD_LABEL_OVERRIDES = Map.ofEntries(
        Map.entry("student_activity_experience_id", "活动经历ID"),
        Map.entry("activity_summary", "活动简述"),
        Map.entry("referrer_name", "推荐人"),
        Map.entry("start_time", "开始时间"),
        Map.entry("end_time", "结束时间"),
        Map.entry("student_activity_attachment_id", "活动附件ID"),
        Map.entry("file_name", "附件名称"),
        Map.entry("file_url", "附件地址"),
        Map.entry("file_key", "附件存储Key"),
        Map.entry("student_enterprise_internship_id", "企业实习ID"),
        Map.entry("company_name", "企业名"),
        Map.entry("position_name", "岗位"),
        Map.entry("student_enterprise_internship_attachment_id", "企业实习附件ID"),
        Map.entry("student_research_experience_id", "科研经历ID"),
        Map.entry("research_summary", "科研经历简述"),
        Map.entry("initiator_name", "发起方"),
        Map.entry("role_name", "担任角色"),
        Map.entry("student_research_attachment_id", "科研附件ID"),
        Map.entry("student_competition_record_id", "竞赛记录ID"),
        Map.entry("participants_text", "参赛人数"),
        Map.entry("result_text", "成绩描述"),
        Map.entry("student_competition_attachment_id", "竞赛附件ID"),
        Map.entry("student_standardized_sat_id", "SAT成绩ID"),
        Map.entry("student_standardized_act_id", "ACT成绩ID")
    );
    private static final Map<String, String> PROFILE_FIELD_HELPER_OVERRIDES = Map.of();
    private static final Map<String, String> PROFILE_TABLE_FIELD_LABEL_OVERRIDES = Map.ofEntries(
        Map.entry(key("student_academic_ossd_subject", "score_numeric"), "课程分数")
    );
    private static final Map<String, Set<String>> PROFILE_REMOVED_FIELDS_BY_TABLE = Map.ofEntries(
        Map.entry("student_academic_a_level_subject", Set.of("exam_series")),
        Map.entry("student_academic_ossd_subject", Set.of("school_year_label", "term_code", "score_text", "score_scale_code"))
    );
    private static final Map<String, Set<String>> PROFILE_HIDDEN_FIELDS_BY_TABLE = Map.ofEntries(
        Map.entry("student_academic_a_level_subject", Set.of("exam_series")),
        Map.entry(
            "student_academic_ossd_subject",
            Set.of("school_year_label", "term_code", "score_text", "score_scale_code")
        ),
        Map.entry(ACTIVITY_EXPERIENCE_TABLE, Set.of("student_activity_experience_id")),
        Map.entry(ACTIVITY_ATTACHMENT_TABLE, Set.of("student_activity_attachment_id", "student_activity_experience_id", "file_key", "sort_order")),
        Map.entry(ENTERPRISE_INTERNSHIP_TABLE, Set.of("student_enterprise_internship_id")),
        Map.entry(
            ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
            Set.of("student_enterprise_internship_attachment_id", "student_enterprise_internship_id", "file_key", "sort_order")
        ),
        Map.entry(RESEARCH_EXPERIENCE_TABLE, Set.of("student_research_experience_id")),
        Map.entry(RESEARCH_ATTACHMENT_TABLE, Set.of("student_research_attachment_id", "student_research_experience_id", "file_key", "sort_order")),
        Map.entry(COMPETITION_RECORD_TABLE, Set.of("student_competition_record_id")),
        Map.entry(COMPETITION_ATTACHMENT_TABLE, Set.of("student_competition_attachment_id", "student_competition_record_id", "file_key", "sort_order")),
        Map.entry(STANDARDIZED_SAT_TABLE, Set.of("student_standardized_sat_id")),
        Map.entry(STANDARDIZED_ACT_TABLE, Set.of("student_standardized_act_id"))
    );

    private static final Map<String, String> TABLE_LABELS =
        mergeStringMap(BusinessProfileFormMetadata.TABLE_LABELS, PROFILE_TABLE_LABEL_OVERRIDES);
    private static final Map<String, String> FIELD_LABELS =
        mergeStringMap(BusinessProfileFormMetadata.FIELD_LABELS, PROFILE_FIELD_LABEL_OVERRIDES);
    private static final Map<String, String> FIELD_HELPERS =
        mergeStringMap(BusinessProfileFormMetadata.FIELD_HELPERS, PROFILE_FIELD_HELPER_OVERRIDES);
    private static final Map<String, Set<String>> HIDDEN_FIELDS_BY_TABLE =
        mergeSetMap(BusinessProfileFormMetadata.HIDDEN_FIELDS_BY_TABLE, PROFILE_HIDDEN_FIELDS_BY_TABLE);
    private static final Map<String, List<String>> ENUM_FIELD_OPTIONS =
        BusinessProfileFormMetadata.ENUM_FIELD_OPTIONS;
    private static final Map<String, Map<String, String>> ENUM_OPTION_LABELS =
        BusinessProfileFormMetadata.ENUM_OPTION_LABELS;

    private final BusinessProfileFormService businessProfileFormService;
    private final BusinessProfilePersistenceService businessProfilePersistenceService;
    private final AiChatPersistenceMapper aiChatPersistenceMapper;
    private final AiChatReadService aiChatReadService;
    private final AiChatDraftService aiChatDraftService;
    private final AiProfileRadarPendingService aiProfileRadarPendingService;
    private final StudentProfileMapper studentProfileMapper;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> editableColumnsCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnMeta>> tableColumnsCache = new ConcurrentHashMap<>();

    public StudentProfileServiceImpl(
        BusinessProfileFormService businessProfileFormService,
        BusinessProfilePersistenceService businessProfilePersistenceService,
        AiChatPersistenceMapper aiChatPersistenceMapper,
        AiChatReadService aiChatReadService,
        AiChatDraftService aiChatDraftService,
        AiProfileRadarPendingService aiProfileRadarPendingService,
        StudentProfileMapper studentProfileMapper,
        JwtService jwtService,
        ObjectMapper objectMapper
    ) {
        this.businessProfileFormService = businessProfileFormService;
        this.businessProfilePersistenceService = businessProfilePersistenceService;
        this.aiChatPersistenceMapper = aiChatPersistenceMapper;
        this.aiChatReadService = aiChatReadService;
        this.aiChatDraftService = aiChatDraftService;
        this.aiProfileRadarPendingService = aiProfileRadarPendingService;
        this.studentProfileMapper = studentProfileMapper;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> loadCurrentSession(String authorizationHeader, Integer createIfMissing) {
        AiChatResponses.CurrentSessionEnvelope envelope = aiChatReadService.getCurrentSession(
            authorizationHeader,
            DEFAULT_BIZ_DOMAIN,
            createIfMissing
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("has_active_session", envelope.hasActiveSession());
        if (envelope.session() == null) {
            response.put("session", null);
            return response;
        }

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("session_id", envelope.session().sessionId());
        session.put("session_status", envelope.session().sessionStatus());
        session.put("current_stage", envelope.session().currentStage());
        session.put("current_round", envelope.session().currentRound());
        session.put("missing_dimensions", envelope.session().missingDimensions());
        session.put("last_message_at", envelope.session().lastMessageAt());
        session.put("is_new_session", envelope.session().isNewSession());
        response.put("session", session);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> loadArchiveBundle(String authorizationHeader, String sessionId) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        return buildArchiveBundle(studentId, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> loadLanguageArchiveBundle(String authorizationHeader) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        return buildLanguageSectionBundle(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> loadCurriculumArchiveBundle(String authorizationHeader) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        return buildCurriculumSectionBundle(studentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.ArchiveSavePayload payload
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        String sessionId = payload == null ? null : nullableString(payload.sessionId());
        if (sessionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "session_id is required");
        }

        AiChatSessionRow session = requireOwnedSession(studentId, sessionId);
        ensureSessionNotBusy(session);

        Map<String, Object> archiveForm = payload.archiveForm() == null
            ? Map.of()
            : objectMapper.convertValue(
                payload.archiveForm(),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );

        Map<String, Object> previousSnapshot = buildArchiveSnapshot(studentId);
        Map<String, Object> currentLegacySnapshot = businessProfileFormService.loadBusinessProfileSnapshot(studentId);
        businessProfilePersistenceService.persistArchiveFormSnapshot(
            pickLegacyArchiveForm(currentLegacySnapshot, archiveForm),
            studentId
        );
        persistCurriculumArchiveForm(studentId, pickCurriculumArchiveForm(archiveForm));
        persistLanguageArchiveForm(studentId, pickLanguageArchiveForm(archiveForm));
        persistStandardizedArchiveForm(studentId, pickStandardizedArchiveForm(archiveForm));
        persistExperienceArchiveForm(studentId, pickExperienceArchiveForm(archiveForm));
        Map<String, Object> currentSnapshot = buildArchiveSnapshot(studentId);

        aiProfileRadarPendingService.accumulateArchiveFormChanges(
            studentId,
            sessionId,
            firstNonBlank(session.bizDomain(), DEFAULT_BIZ_DOMAIN),
            previousSnapshot,
            currentSnapshot
        );
        updateSessionStage(studentId, sessionId, "build_ready");
        return buildArchiveBundle(studentId, sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncArchiveDraftFromOfficial(
        String authorizationHeader,
        StudentProfileRequests.SessionActionPayload payload
    ) {
        String sessionId = payload == null ? null : nullableString(payload.sessionId());
        if (sessionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "session_id is required");
        }
        return aiChatDraftService.syncFromOfficialSnapshot(authorizationHeader, sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> regenerateArchiveRadar(
        String authorizationHeader,
        StudentProfileRequests.SessionActionPayload payload
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        String sessionId = payload == null ? null : nullableString(payload.sessionId());
        if (sessionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "session_id is required");
        }

        aiChatDraftService.regenerateArchiveRadar(authorizationHeader, sessionId);
        return buildArchiveBundle(studentId, sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveLanguageArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.LanguageArchiveSavePayload payload
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        Map<String, Object> archiveForm = payload == null || payload.archiveForm() == null
            ? Map.of()
            : objectMapper.convertValue(
                payload.archiveForm(),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );

        persistLanguageArchiveForm(studentId, archiveForm);
        return loadLanguageArchiveBundle(authorizationHeader);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveCurriculumArchiveBundle(
        String authorizationHeader,
        StudentProfileRequests.CurriculumArchiveSavePayload payload
    ) {
        String studentId = jwtService.requireCurrentUserId(authorizationHeader);
        Map<String, Object> archiveForm = payload == null || payload.archiveForm() == null
            ? Map.of()
            : objectMapper.convertValue(
                payload.archiveForm(),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );

        persistCurriculumArchiveForm(studentId, archiveForm);
        return loadCurriculumArchiveBundle(authorizationHeader);
    }

    private Map<String, Object> buildArchiveBundle(String studentId, String sessionId) {
        AiChatSessionRow session = null;
        if (nullableString(sessionId) != null) {
            session = requireOwnedSession(studentId, sessionId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("session_id", session == null ? sessionId : session.sessionId());
        response.put("archive_form", buildArchiveSnapshot(studentId));
        response.put("form_meta", buildArchiveFormMeta());

        AiChatProfileResultRow profileResult = session == null ? null : findProfileResultBySessionId(session.sessionId());
        if (profileResult != null) {
            ensureProfileResultBelongsToStudent(profileResult, studentId);
            response.put("result_status", profileResult.resultStatus());
            response.put("summary_text", profileResult.summaryText());
            response.put("radar_scores_json", profileResult.radarScoresJson());
            response.put("save_error_message", profileResult.saveErrorMessage());
            response.put("create_time", formatDateTime(profileResult.createTime()));
            response.put("update_time", formatDateTime(profileResult.updateTime()));
            return response;
        }

        GuidedProfileResultRow guidedResult = findLatestGuidedResultByStudentId(studentId);
        response.put("result_status", guidedResult == null ? null : guidedResult.resultStatus());
        response.put("summary_text", guidedResult == null ? null : guidedResult.summaryText());
        response.put("radar_scores_json", guidedResult == null ? Collections.emptyMap() : guidedResult.radarScoresJson());
        response.put("save_error_message", guidedResult == null ? null : guidedResult.saveErrorMessage());
        response.put("create_time", guidedResult == null ? null : formatDateTime(guidedResult.createTime()));
        response.put("update_time", guidedResult == null ? null : formatDateTime(guidedResult.updateTime()));
        return response;
    }

    private Map<String, Object> buildArchiveSnapshot(String studentId) {
        Map<String, Object> legacyArchiveForm = new LinkedHashMap<>(businessProfileFormService.loadBusinessProfileSnapshot(studentId));
        LEGACY_CURRICULUM_PROFILE_TABLES.forEach(legacyArchiveForm::remove);
        LEGACY_CURRICULUM_SUBJECT_TABLES.forEach(legacyArchiveForm::remove);
        LEGACY_LANGUAGE_DETAIL_TABLES.forEach(legacyArchiveForm::remove);
        LEGACY_STANDARDIZED_DETAIL_TABLES.forEach(legacyArchiveForm::remove);
        LEGACY_EXPERIENCE_DETAIL_TABLES.forEach(legacyArchiveForm::remove);

        legacyArchiveForm.putAll(toMapOrEmpty(buildExperienceSectionBundle(studentId).get("archive_form")));
        legacyArchiveForm.putAll(toMapOrEmpty(buildCurriculumSectionBundle(studentId).get("archive_form")));
        legacyArchiveForm.putAll(toMapOrEmpty(buildLanguageSectionBundle(studentId).get("archive_form")));
        legacyArchiveForm.putAll(toMapOrEmpty(buildStandardizedSectionBundle(studentId).get("archive_form")));
        return legacyArchiveForm;
    }

    private Map<String, Object> buildArchiveFormMeta() {
        Map<String, Object> legacyFormMeta = new LinkedHashMap<>(businessProfileFormService.buildBusinessProfileFormMeta());
        List<String> legacyTableOrder = toStringList(legacyFormMeta.get("table_order"));
        Map<String, Object> legacyTables = new LinkedHashMap<>(toMapOrEmpty(legacyFormMeta.get("tables")));

        for (String tableName : CURRICULUM_TABLE_ORDER) {
            legacyTables.remove(tableName);
        }
        for (String tableName : LANGUAGE_TABLE_ORDER) {
            legacyTables.remove(tableName);
        }
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            legacyTables.remove(tableName);
        }
        LEGACY_CURRICULUM_PROFILE_TABLES.forEach(legacyTables::remove);
        LEGACY_CURRICULUM_SUBJECT_TABLES.forEach(legacyTables::remove);
        LEGACY_LANGUAGE_DETAIL_TABLES.forEach(legacyTables::remove);
        LEGACY_STANDARDIZED_DETAIL_TABLES.forEach(legacyTables::remove);
        EXPERIENCE_TABLE_ORDER.forEach(legacyTables::remove);
        LEGACY_EXPERIENCE_DETAIL_TABLES.forEach(legacyTables::remove);

        List<String> mergedTableOrder = new ArrayList<>();
        for (String tableName : legacyTableOrder) {
            if (
                CURRICULUM_TABLES.contains(tableName)
                    || LANGUAGE_TABLES.contains(tableName)
                    || STANDARDIZED_TABLES.contains(tableName)
                    || EXPERIENCE_TABLES.contains(tableName)
                    || LEGACY_CURRICULUM_PROFILE_TABLES.contains(tableName)
                    || LEGACY_CURRICULUM_SUBJECT_TABLES.contains(tableName)
                    || LEGACY_LANGUAGE_DETAIL_TABLES.contains(tableName)
                    || LEGACY_STANDARDIZED_DETAIL_TABLES.contains(tableName)
                    || LEGACY_EXPERIENCE_DETAIL_TABLES.contains(tableName)
            ) {
                continue;
            }
            mergedTableOrder.add(tableName);
        }
        mergedTableOrder.addAll(CURRICULUM_TABLE_ORDER);
        mergedTableOrder.addAll(LANGUAGE_TABLE_ORDER);
        mergedTableOrder.addAll(STANDARDIZED_TABLE_ORDER);
        mergedTableOrder.addAll(EXPERIENCE_TABLE_ORDER);

        legacyTables.putAll(toMapOrEmpty(buildExperienceSectionBundle(null).get("form_meta_tables")));
        legacyTables.putAll(toMapOrEmpty(buildCurriculumSectionBundle(null).get("form_meta_tables")));
        legacyTables.putAll(toMapOrEmpty(buildLanguageSectionBundle(null).get("form_meta_tables")));
        legacyTables.putAll(toMapOrEmpty(buildStandardizedSectionBundle(null).get("form_meta_tables")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table_order", mergedTableOrder);
        result.put("tables", legacyTables);
        return result;
    }

    private Map<String, Object> buildLanguageSectionBundle(String studentId) {
        Map<String, Object> archiveForm = new LinkedHashMap<>();
        if (studentId == null) {
            archiveForm.put(LANGUAGE_TEST_RECORD_TABLE, List.of());
            archiveForm.put(LANGUAGE_TEST_SCORE_ITEM_TABLE, List.of());
        } else {
            archiveForm.put(
                LANGUAGE_TEST_RECORD_TABLE,
                normalizeLanguageTestRecords(studentProfileMapper.listLanguageTestRecordsByStudentId(studentId))
            );
            archiveForm.put(
                LANGUAGE_TEST_SCORE_ITEM_TABLE,
                normalizeLanguageTestScoreItems(studentProfileMapper.listLanguageTestScoreItemsByStudentId(studentId))
            );
        }

        Map<String, Object> formMeta = buildLanguageFormMeta();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("archive_form", archiveForm);
        response.put("form_meta", formMeta);
        response.put("form_meta_tables", toMapOrEmpty(formMeta.get("tables")));
        return response;
    }

    private Map<String, Object> buildExperienceSectionBundle(String studentId) {
        Map<String, Object> archiveForm = new LinkedHashMap<>();
        if (studentId == null) {
            EXPERIENCE_TABLE_ORDER.forEach(tableName -> archiveForm.put(tableName, List.of()));
        } else {
            for (String tableName : EXPERIENCE_MAIN_TABLE_ORDER) {
                archiveForm.put(tableName, loadProfileRowsByStudent(tableName, studentId));
            }
            archiveForm.put(
                ACTIVITY_ATTACHMENT_TABLE,
                loadExperienceAttachmentRows(
                    ACTIVITY_ATTACHMENT_TABLE,
                    studentProfileMapper.listActivityAttachmentsByStudentId(studentId)
                )
            );
            archiveForm.put(
                ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
                loadExperienceAttachmentRows(
                    ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
                    studentProfileMapper.listEnterpriseInternshipAttachmentsByStudentId(studentId)
                )
            );
            archiveForm.put(
                RESEARCH_ATTACHMENT_TABLE,
                loadExperienceAttachmentRows(
                    RESEARCH_ATTACHMENT_TABLE,
                    studentProfileMapper.listResearchAttachmentsByStudentId(studentId)
                )
            );
            archiveForm.put(
                COMPETITION_ATTACHMENT_TABLE,
                loadExperienceAttachmentRows(
                    COMPETITION_ATTACHMENT_TABLE,
                    studentProfileMapper.listCompetitionAttachmentsByStudentId(studentId)
                )
            );
        }

        Map<String, Object> formMeta = buildExperienceFormMeta();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("archive_form", archiveForm);
        response.put("form_meta", formMeta);
        response.put("form_meta_tables", toMapOrEmpty(formMeta.get("tables")));
        return response;
    }

    private Map<String, Object> buildCurriculumSectionBundle(String studentId) {
        Map<String, Object> archiveForm = new LinkedHashMap<>();
        for (String tableName : CURRICULUM_TABLE_ORDER) {
            archiveForm.put(
                tableName,
                studentId == null ? List.of() : loadCurriculumRows(tableName, studentId)
            );
        }

        Map<String, Object> formMeta = buildCurriculumFormMeta();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("archive_form", archiveForm);
        response.put("form_meta", formMeta);
        response.put("form_meta_tables", toMapOrEmpty(formMeta.get("tables")));
        return response;
    }

    private Map<String, Object> buildStandardizedSectionBundle(String studentId) {
        Map<String, Object> archiveForm = new LinkedHashMap<>();
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            archiveForm.put(
                tableName,
                studentId == null ? List.of() : loadProfileRowsByStudent(tableName, studentId)
            );
        }

        Map<String, Object> formMeta = buildStandardizedFormMeta();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("archive_form", archiveForm);
        response.put("form_meta", formMeta);
        response.put("form_meta_tables", toMapOrEmpty(formMeta.get("tables")));
        return response;
    }

    private Map<String, Object> pickLegacyArchiveForm(
        Map<String, Object> currentLegacyArchiveForm,
        Map<String, Object> archiveForm
    ) {
        Map<String, Object> result = deepCopyMap(currentLegacyArchiveForm);
        for (Map.Entry<String, Object> entry : archiveForm.entrySet()) {
            if (isManagedByStudentProfileNewChain(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map<String, Object> pickCurriculumArchiveForm(Map<String, Object> archiveForm) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String tableName : CURRICULUM_TABLE_ORDER) {
            result.put(tableName, archiveForm.get(tableName));
        }
        return result;
    }

    private Map<String, Object> pickLanguageArchiveForm(Map<String, Object> archiveForm) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(LANGUAGE_TEST_RECORD_TABLE, archiveForm.get(LANGUAGE_TEST_RECORD_TABLE));
        result.put(LANGUAGE_TEST_SCORE_ITEM_TABLE, archiveForm.get(LANGUAGE_TEST_SCORE_ITEM_TABLE));
        return result;
    }

    private Map<String, Object> pickStandardizedArchiveForm(Map<String, Object> archiveForm) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            result.put(tableName, archiveForm.get(tableName));
        }
        return result;
    }

    private Map<String, Object> pickExperienceArchiveForm(Map<String, Object> archiveForm) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String tableName : EXPERIENCE_TABLE_ORDER) {
            result.put(tableName, archiveForm.get(tableName));
        }
        return result;
    }

    private void persistCurriculumArchiveForm(String studentId, Map<String, Object> archiveForm) {
        Map<String, Object> normalizedPayload = normalizeCurriculumArchiveForm(archiveForm, studentId);
        softDeleteCurriculumRows(studentId);
        insertCurriculumRows(normalizedPayload);
    }

    private void persistLanguageArchiveForm(String studentId, Map<String, Object> archiveForm) {
        List<Map<String, Object>> languageTestRecords = toMutableRows(archiveForm.get(LANGUAGE_TEST_RECORD_TABLE));
        List<Map<String, Object>> languageTestScoreItems = toMutableRows(archiveForm.get(LANGUAGE_TEST_SCORE_ITEM_TABLE));

        ensureLanguageProfileParent(studentId);
        studentProfileMapper.softDeleteLanguageTestScoreItemsByStudentId(studentId);
        studentProfileMapper.softDeleteLanguageTestRecordsByStudentId(studentId);

        Map<Long, Long> languageRecordIdMapping = insertLanguageTestRecords(studentId, languageTestRecords);
        insertLanguageTestScoreItems(languageTestScoreItems, languageRecordIdMapping);
    }

    private void ensureLanguageProfileParent(String studentId) {
        if (!tableExists(LANGUAGE_PARENT_TABLE)) {
            return;
        }
        Integer activeCount = studentProfileMapper.countActiveLanguageParentsByStudentId(studentId);
        if (activeCount != null && activeCount > 0) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("studentId", studentId);
        row.put("studentLanguageId", "lang_" + UUID.randomUUID());
        studentProfileMapper.insertLanguageParent(row);
    }

    private void persistStandardizedArchiveForm(String studentId, Map<String, Object> archiveForm) {
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            if (!tableExists(tableName)) {
                continue;
            }
            studentProfileMapper.softDeleteByStudent(safeProfileTableName(tableName), studentId);
        }
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            if (!tableExists(tableName)) {
                continue;
            }
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                row.put("student_id", studentId);
                if (isMeaningfulStandardizedRow(tableName, row)) {
                    insertDynamicRow(tableName, row);
                }
            }
        }
    }

    private void persistExperienceArchiveForm(String studentId, Map<String, Object> archiveForm) {
        Map<String, Object> normalizedPayload = normalizeExperienceArchiveForm(archiveForm, studentId);
        softDeleteExperienceRows(studentId);
        Map<String, Map<Long, Long>> insertedIdMappings = insertExperienceMainRows(normalizedPayload);
        insertExperienceAttachments(normalizedPayload, insertedIdMappings);
    }

    private Map<String, Object> buildLanguageFormMeta() {
        Map<String, Object> tables = new LinkedHashMap<>();

        tables.put(
            LANGUAGE_TEST_RECORD_TABLE,
            tableMeta(
                "\u8bed\u8a00\u8003\u8bd5\u8bb0\u5f55",
                List.of(
                    field("student_language_test_record_id", "\u8bed\u8a00\u8003\u8bd5\u8bb0\u5f55ID", "number", true, List.of()),
                    field("test_type_code", "\u8003\u8bd5\u7c7b\u578b", "select", false, loadLanguageTestTypeOptions()),
                    field("status_code", "\u6210\u7ee9\u72b6\u6001", "select", false, buildStatusOptions()),
                    field("test_date", "\u8003\u8bd5\u65e5\u671f", "date", false, List.of()),
                    field("exam_name_text", "\u8003\u8bd5\u540d\u79f0\u6216\u7248\u672c", "text", false, List.of()),
                    field("total_score", "\u603b\u5206", "number", false, List.of()),
                    field("score_scale_text", "\u5206\u5236\u8bf4\u660e", "text", false, List.of()),
                    field("cefr_level_code", "CEFR\u7b49\u7ea7", "select", false, buildCefrLevelOptions()),
                    field("evidence_level_code", "\u8bc1\u636e\u7b49\u7ea7", "select", false, buildEvidenceLevelOptions()),
                    field("is_best_score", "\u662f\u5426\u6700\u4f73\u6210\u7ee9", "checkbox", false, List.of()),
                    field("notes", "\u5907\u6ce8", "textarea", false, List.of())
                )
            )
        );

        tables.put(
            LANGUAGE_TEST_SCORE_ITEM_TABLE,
            tableMeta(
                "\u8bed\u8a00\u8003\u8bd5\u5206\u9879\u6210\u7ee9",
                List.of(
                    field("student_language_test_record_id", "\u8bed\u8a00\u8003\u8bd5\u8bb0\u5f55ID", "number", true, List.of()),
                    field("score_item_code", "\u5206\u9879", "select", false, loadLanguageTestScoreItemOptions()),
                    field("score_value", "\u5206\u9879\u5206\u6570", "number", false, List.of()),
                    field("score_scale_text", "\u5206\u9879\u5206\u5236\u8bf4\u660e", "text", false, List.of())
                )
            )
        );

        Map<String, Object> formMeta = new LinkedHashMap<>();
        formMeta.put("table_order", List.of(LANGUAGE_TEST_RECORD_TABLE, LANGUAGE_TEST_SCORE_ITEM_TABLE));
        formMeta.put("tables", tables);
        return formMeta;
    }

    private Map<String, Object> buildCurriculumFormMeta() {
        Map<String, List<Map<String, String>>> cachedOptions = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();

        for (String tableName : CURRICULUM_TABLE_ORDER) {
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
                field.put("label", fieldLabel(tableName, column.name()));
                field.put("input_type", inferInputType(column.name(), column.type(), options));
                field.put("hidden", isFieldHidden(tableName, column.name()));
                field.put("options", options);
                field.put("helper_text", FIELD_HELPERS.get(key(tableName, column.name())));
                fields.add(field);
            }

            Map<String, Object> tableMeta = new LinkedHashMap<>();
            tableMeta.put("label", TABLE_LABELS.getOrDefault(tableName, humanizeFieldName(tableName)));
            tableMeta.put("kind", "multi");
            tableMeta.put("fields", fields);
            tables.put(tableName, tableMeta);
        }

        Map<String, Object> formMeta = new LinkedHashMap<>();
        formMeta.put("table_order", CURRICULUM_TABLE_ORDER);
        formMeta.put("tables", tables);
        return formMeta;
    }

    private Map<String, Object> buildExperienceFormMeta() {
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String tableName : EXPERIENCE_TABLE_ORDER) {
            List<ColumnMeta> columns = getFormColumns(tableName);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (ColumnMeta column : columns) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", column.name());
                field.put("label", fieldLabel(tableName, column.name()));
                field.put("input_type", inferInputType(column.name(), column.type(), List.of()));
                field.put("hidden", isFieldHidden(tableName, column.name()));
                field.put("options", List.of());
                field.put("helper_text", FIELD_HELPERS.get(key(tableName, column.name())));
                fields.add(field);
            }

            tables.put(
                tableName,
                tableMeta(TABLE_LABELS.getOrDefault(tableName, humanizeFieldName(tableName)), fields)
            );
        }

        Map<String, Object> formMeta = new LinkedHashMap<>();
        formMeta.put("table_order", EXPERIENCE_TABLE_ORDER);
        formMeta.put("tables", tables);
        return formMeta;
    }

    private Map<String, Object> buildStandardizedFormMeta() {
        Map<String, Object> tables = new LinkedHashMap<>();
        for (String tableName : STANDARDIZED_TABLE_ORDER) {
            List<ColumnMeta> columns = getFormColumns(tableName);
            List<Map<String, Object>> fields = new ArrayList<>();
            for (ColumnMeta column : columns) {
                List<Map<String, String>> options = "status_code".equals(column.name())
                    ? buildStatusOptions()
                    : List.of();

                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", column.name());
                field.put("label", fieldLabel(tableName, column.name()));
                field.put("input_type", inferInputType(column.name(), column.type(), options));
                field.put("hidden", isFieldHidden(tableName, column.name()));
                field.put("options", options);
                field.put("helper_text", FIELD_HELPERS.get(key(tableName, column.name())));
                fields.add(field);
            }

            tables.put(
                tableName,
                tableMeta(TABLE_LABELS.getOrDefault(tableName, humanizeFieldName(tableName)), fields)
            );
        }

        Map<String, Object> formMeta = new LinkedHashMap<>();
        formMeta.put("table_order", STANDARDIZED_TABLE_ORDER);
        formMeta.put("tables", tables);
        return formMeta;
    }

    private Map<String, Object> tableMeta(String label, List<Map<String, Object>> fields) {
        Map<String, Object> tableMeta = new LinkedHashMap<>();
        tableMeta.put("label", label);
        tableMeta.put("kind", "multi");
        tableMeta.put("fields", fields);
        return tableMeta;
    }

    private String fieldLabel(String tableName, String fieldName) {
        String tableFieldKey = key(tableName, fieldName);
        if (PROFILE_TABLE_FIELD_LABEL_OVERRIDES.containsKey(tableFieldKey)) {
            return PROFILE_TABLE_FIELD_LABEL_OVERRIDES.get(tableFieldKey);
        }
        return FIELD_LABELS.getOrDefault(fieldName, humanizeFieldName(fieldName));
    }

    private Map<String, Object> field(
        String name,
        String label,
        String inputType,
        boolean hidden,
        List<Map<String, String>> options
    ) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("label", label);
        field.put("input_type", inputType);
        field.put("hidden", hidden);
        field.put("options", options);
        field.put("helper_text", null);
        return field;
    }

    private List<Map<String, String>> loadLanguageTestTypeOptions() {
        return normalizeDictionaryOptions(studentProfileMapper.listLanguageTestTypeOptions());
    }

    private List<Map<String, String>> loadLanguageTestScoreItemOptions() {
        return normalizeDictionaryOptions(studentProfileMapper.listLanguageTestScoreItemOptions());
    }

    private List<Map<String, String>> buildStatusOptions() {
        return List.of(
            option("SCORED", "\u5df2\u51fa\u5206"),
            option("PLANNED", "\u8ba1\u5212\u53c2\u52a0"),
            option("ESTIMATED", "\u9884\u4f30")
        );
    }

    private List<Map<String, String>> buildCefrLevelOptions() {
        return List.of(
            option("A1", "A1"),
            option("A2", "A2"),
            option("B1", "B1"),
            option("B2", "B2"),
            option("C1", "C1"),
            option("C2", "C2"),
            option("UNKNOWN", "\u672a\u77e5")
        );
    }

    private List<Map<String, String>> buildEvidenceLevelOptions() {
        return List.of(
            option("CONFIRMED", "\u5df2\u786e\u8ba4"),
            option("SELF_REPORTED", "\u5b66\u751f\u81ea\u8ff0"),
            option("ESTIMATED", "\u9884\u4f30")
        );
    }

    private Map<String, String> option(String value, String label) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    private List<Map<String, String>> normalizeDictionaryOptions(List<Map<String, Object>> rows) {
        List<Map<String, String>> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = nullableString(row.get("value"));
            if (value == null) {
                continue;
            }
            String label = firstNonBlank(nullableString(row.get("label_cn")), nullableString(row.get("label_en")), value);
            options.add(option(value, label));
        }
        return options;
    }

    private Map<String, Object> normalizeCurriculumArchiveForm(Map<String, Object> archiveForm, String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String tableName : CURRICULUM_TABLE_ORDER) {
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                row.put("student_id", studentId);
            }
            payload.put(tableName, rows);
        }

        normalizeCurriculumSystemPrimaryFlags(payload);
        pruneCurriculumRowsBySelection(payload);
        return payload;
    }

    private Map<String, Object> normalizeExperienceArchiveForm(Map<String, Object> archiveForm, String studentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String tableName : EXPERIENCE_MAIN_TABLE_ORDER) {
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                row.put("student_id", studentId);
            }
            payload.put(tableName, rows);
        }
        for (String tableName : EXPERIENCE_ATTACHMENT_TABLE_ORDER) {
            payload.put(tableName, toMutableRows(archiveForm.get(tableName)));
        }
        return payload;
    }

    private List<Map<String, Object>> loadProfileRowsByStudent(String tableName, String studentId) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<ColumnMeta> columns = getFormColumns(tableName);
        if (columns.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = studentProfileMapper.selectMultiRows(
            safeProfileTableName(tableName),
            safeColumnNames(columns),
            studentId
        );
        return normalizeRows(rows, columns);
    }

    private List<Map<String, Object>> loadExperienceAttachmentRows(
        String tableName,
        List<Map<String, Object>> rows
    ) {
        List<ColumnMeta> columns = getFormColumns(tableName);
        if (columns.isEmpty()) {
            return List.of();
        }
        return normalizeRows(rows, columns);
    }

    private List<Map<String, Object>> loadCurriculumRows(String tableName, String studentId) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<ColumnMeta> columns = getEditableColumns(tableName);
        if (columns.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = studentProfileMapper.selectMultiRows(
            safeProfileTableName(tableName),
            safeColumnNames(columns),
            studentId
        );
        return normalizeRows(rows, columns);
    }

    private void softDeleteCurriculumRows(String studentId) {
        List<String> reversedTableOrder = new ArrayList<>(CURRICULUM_TABLE_ORDER);
        Collections.reverse(reversedTableOrder);
        for (String tableName : reversedTableOrder) {
            if (!tableExists(tableName)) {
                continue;
            }
            studentProfileMapper.softDeleteByStudent(safeProfileTableName(tableName), studentId);
        }
    }

    private void insertCurriculumRows(Map<String, Object> archiveForm) {
        for (String tableName : CURRICULUM_TABLE_ORDER) {
            if (!tableExists(tableName)) {
                continue;
            }
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                if (isMeaningfulCurriculumRow(row)) {
                    insertDynamicRow(tableName, row);
                }
            }
        }
    }

    private void softDeleteExperienceRows(String studentId) {
        if (tableExists(ACTIVITY_ATTACHMENT_TABLE)) {
            studentProfileMapper.softDeleteActivityAttachmentsByStudentId(studentId);
        }
        if (tableExists(ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE)) {
            studentProfileMapper.softDeleteEnterpriseInternshipAttachmentsByStudentId(studentId);
        }
        if (tableExists(RESEARCH_ATTACHMENT_TABLE)) {
            studentProfileMapper.softDeleteResearchAttachmentsByStudentId(studentId);
        }
        if (tableExists(COMPETITION_ATTACHMENT_TABLE)) {
            studentProfileMapper.softDeleteCompetitionAttachmentsByStudentId(studentId);
        }

        List<String> reversedMainTableOrder = new ArrayList<>(EXPERIENCE_MAIN_TABLE_ORDER);
        Collections.reverse(reversedMainTableOrder);
        for (String tableName : reversedMainTableOrder) {
            if (!tableExists(tableName)) {
                continue;
            }
            studentProfileMapper.softDeleteByStudent(safeProfileTableName(tableName), studentId);
        }
    }

    private Map<String, Map<Long, Long>> insertExperienceMainRows(Map<String, Object> archiveForm) {
        Map<String, Map<Long, Long>> idMappings = new LinkedHashMap<>();
        for (String tableName : EXPERIENCE_MAIN_TABLE_ORDER) {
            Map<Long, Long> tableIdMapping = new LinkedHashMap<>();
            if (!tableExists(tableName)) {
                idMappings.put(tableName, tableIdMapping);
                continue;
            }

            String idField = EXPERIENCE_TABLE_ID_FIELDS.get(tableName);
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            for (Map<String, Object> row : rows) {
                if (!isMeaningfulExperienceRow(tableName, row)) {
                    continue;
                }

                Long clientId = toLong(row.get(idField));
                Long generatedId = insertDynamicRow(tableName, row);
                if (clientId != null && generatedId != null) {
                    tableIdMapping.put(clientId, generatedId);
                }
                if (generatedId != null) {
                    row.put(idField, generatedId);
                }
            }
            idMappings.put(tableName, tableIdMapping);
        }
        return idMappings;
    }

    private void insertExperienceAttachments(
        Map<String, Object> archiveForm,
        Map<String, Map<Long, Long>> insertedIdMappings
    ) {
        for (String tableName : EXPERIENCE_ATTACHMENT_TABLE_ORDER) {
            if (!tableExists(tableName)) {
                continue;
            }

            String parentField = EXPERIENCE_ATTACHMENT_PARENT_FIELDS.get(tableName);
            String parentTable = EXPERIENCE_ATTACHMENT_PARENT_TABLES.get(tableName);
            Map<Long, Long> parentIdMapping = insertedIdMappings.getOrDefault(parentTable, Map.of());
            Map<Long, Integer> sortOrderByParent = new LinkedHashMap<>();
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));

            for (Map<String, Object> row : rows) {
                if (!isMeaningfulExperienceRow(tableName, row)) {
                    continue;
                }

                Long clientParentId = toLong(row.get(parentField));
                Long actualParentId = clientParentId == null ? null : parentIdMapping.get(clientParentId);
                if (actualParentId == null) {
                    continue;
                }

                row.put(parentField, actualParentId);
                row.put("sort_order", sortOrderByParent.merge(actualParentId, 1, Integer::sum));
                insertDynamicRow(tableName, row);
            }
        }
    }

    private void normalizeCurriculumSystemPrimaryFlags(Map<String, Object> archiveForm) {
        List<Map<String, Object>> rows = toMutableRows(archiveForm.get(CURRICULUM_SYSTEM_TABLE));
        if (rows.isEmpty()) {
            archiveForm.put(CURRICULUM_SYSTEM_TABLE, rows);
            return;
        }

        List<Map<String, Object>> dedupedRows = new ArrayList<>();
        Map<String, Map<String, Object>> rowByCode = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String curriculumCode = normalizeCurriculumCode(row.get("curriculum_system_code"));
            if (curriculumCode == null) {
                continue;
            }
            Map<String, Object> existing = rowByCode.get(curriculumCode);
            if (existing == null) {
                row.put("curriculum_system_code", curriculumCode);
                rowByCode.put(curriculumCode, row);
                dedupedRows.add(row);
                continue;
            }
            if (toTinyInt(row.get("is_primary")) == 1) {
                existing.put("is_primary", 1);
            }
        }

        int primaryIndex = 0;
        for (int index = 0; index < dedupedRows.size(); index++) {
            if (toTinyInt(dedupedRows.get(index).get("is_primary")) == 1) {
                primaryIndex = index;
                break;
            }
        }
        for (int index = 0; index < dedupedRows.size(); index++) {
            dedupedRows.get(index).put("is_primary", index == primaryIndex ? 1 : 0);
        }

        archiveForm.put(CURRICULUM_SYSTEM_TABLE, dedupedRows);
    }

    private void pruneCurriculumRowsBySelection(Map<String, Object> archiveForm) {
        List<Map<String, Object>> curriculumSystemRows = toMutableRows(archiveForm.get(CURRICULUM_SYSTEM_TABLE));
        Set<String> selectedCodes = new LinkedHashSet<>();
        for (Map<String, Object> row : curriculumSystemRows) {
            String curriculumCode = normalizeCurriculumCode(row.get("curriculum_system_code"));
            if (curriculumCode != null) {
                selectedCodes.add(curriculumCode);
            }
        }

        if (selectedCodes.isEmpty()) {
            for (String tableName : CURRICULUM_TABLE_ORDER) {
                archiveForm.put(tableName, new ArrayList<>());
            }
            return;
        }

        Set<String> allowedTables = new LinkedHashSet<>();
        for (String curriculumCode : selectedCodes) {
            String mappedTable = CURRICULUM_MULTI_TABLE_BY_CODE.get(curriculumCode);
            if (mappedTable != null) {
                allowedTables.add(mappedTable);
            }
        }

        for (String tableName : CURRICULUM_TABLE_ORDER) {
            if (CURRICULUM_SYSTEM_TABLE.equals(tableName) || CURRICULUM_SCOPED_TABLES.contains(tableName)) {
                continue;
            }
            if (!allowedTables.contains(tableName)) {
                archiveForm.put(tableName, new ArrayList<>());
            }
        }

        for (String tableName : CURRICULUM_SCOPED_TABLES) {
            List<Map<String, Object>> rows = toMutableRows(archiveForm.get(tableName));
            List<Map<String, Object>> filteredRows = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String curriculumCode = normalizeCurriculumCode(row.get("curriculum_system_code"));
                if (curriculumCode != null && selectedCodes.contains(curriculumCode)) {
                    row.put("curriculum_system_code", curriculumCode);
                    filteredRows.add(row);
                }
            }
            archiveForm.put(tableName, filteredRows);
        }
    }

    private String normalizeCurriculumCode(Object value) {
        String curriculumCode = nullableString(value);
        return curriculumCode == null ? null : curriculumCode.toUpperCase();
    }

    private boolean isMeaningfulCurriculumRow(Map<String, Object> row) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (CURRICULUM_MEANINGFULNESS_IGNORED_FIELDS.contains(entry.getKey())) {
                continue;
            }
            if (hasMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isMeaningfulExperienceRow(String tableName, Map<String, Object> row) {
        Set<String> ignoredFields = new LinkedHashSet<>(
            Set.of("student_id", "delete_flag", "create_time", "update_time")
        );
        String idField = EXPERIENCE_TABLE_ID_FIELDS.get(tableName);
        if (idField != null) {
            ignoredFields.add(idField);
        }
        if (EXPERIENCE_ATTACHMENT_TABLES.contains(tableName)) {
            String parentField = EXPERIENCE_ATTACHMENT_PARENT_FIELDS.get(tableName);
            if (parentField != null) {
                ignoredFields.add(parentField);
            }
            ignoredFields.addAll(EXPERIENCE_ATTACHMENT_MANAGED_FIELDS);
        }
        return hasAnyMeaningfulValue(row, ignoredFields);
    }

    private boolean isMeaningfulStandardizedRow(String tableName, Map<String, Object> row) {
        Set<String> ignoredFields = new LinkedHashSet<>(
            Set.of("student_id", "delete_flag", "create_time", "update_time")
        );
        if (STANDARDIZED_SAT_TABLE.equals(tableName)) {
            ignoredFields.add("student_standardized_sat_id");
        }
        if (STANDARDIZED_ACT_TABLE.equals(tableName)) {
            ignoredFields.add("student_standardized_act_id");
        }
        return hasAnyMeaningfulValue(row, ignoredFields);
    }

    private boolean isManagedByStudentProfileNewChain(String tableName) {
        return CURRICULUM_TABLES.contains(tableName)
            || LANGUAGE_TABLES.contains(tableName)
            || STANDARDIZED_TABLES.contains(tableName)
            || EXPERIENCE_TABLES.contains(tableName)
            || LEGACY_CURRICULUM_PROFILE_TABLES.contains(tableName)
            || LEGACY_CURRICULUM_SUBJECT_TABLES.contains(tableName)
            || LEGACY_LANGUAGE_DETAIL_TABLES.contains(tableName)
            || LEGACY_STANDARDIZED_DETAIL_TABLES.contains(tableName)
            || LEGACY_EXPERIENCE_DETAIL_TABLES.contains(tableName);
    }

    private Map<Long, Long> insertLanguageTestRecords(String studentId, List<Map<String, Object>> rows) {
        Map<Long, Long> idMapping = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!isMeaningfulLanguageTestRecordRow(row)) {
                continue;
            }

            String testTypeCode = nullableString(row.get("test_type_code"));
            if (testTypeCode == null) {
                continue;
            }

            Long clientRecordId = toLong(row.get("student_language_test_record_id"));
            Map<String, Object> insertRow = new LinkedHashMap<>();
            insertRow.put("studentId", studentId);
            insertRow.put("testTypeCode", testTypeCode);
            insertRow.put("statusCode", nullableString(row.get("status_code")));
            insertRow.put("testDate", nullableString(row.get("test_date")));
            insertRow.put("examNameText", nullableString(row.get("exam_name_text")));
            insertRow.put("totalScore", toBigDecimal(row.get("total_score")));
            insertRow.put("scoreScaleText", nullableString(row.get("score_scale_text")));
            insertRow.put("cefrLevelCode", nullableString(row.get("cefr_level_code")));
            insertRow.put("evidenceLevelCode", nullableString(row.get("evidence_level_code")));
            insertRow.put("isBestScore", toTinyInt(row.get("is_best_score")));
            insertRow.put("notes", nullableString(row.get("notes")));
            studentProfileMapper.insertLanguageTestRecord(insertRow);

            Long actualRecordId = toLong(insertRow.get("studentLanguageTestRecordId"));
            if (clientRecordId != null && actualRecordId != null) {
                idMapping.put(clientRecordId, actualRecordId);
            }
            if (actualRecordId != null) {
                row.put("student_language_test_record_id", actualRecordId);
            }
        }
        return idMapping;
    }

    private void insertLanguageTestScoreItems(
        List<Map<String, Object>> rows,
        Map<Long, Long> languageRecordIdMapping
    ) {
        for (Map<String, Object> row : rows) {
            if (!isMeaningfulLanguageTestScoreItemRow(row)) {
                continue;
            }

            String scoreItemCode = nullableString(row.get("score_item_code"));
            if (scoreItemCode == null) {
                continue;
            }

            Long clientRecordId = toLong(row.get("student_language_test_record_id"));
            Long actualRecordId = clientRecordId == null ? null : languageRecordIdMapping.get(clientRecordId);
            if (actualRecordId == null) {
                continue;
            }

            Map<String, Object> insertRow = new LinkedHashMap<>();
            insertRow.put("studentLanguageTestRecordId", actualRecordId);
            insertRow.put("scoreItemCode", scoreItemCode);
            insertRow.put("scoreValue", toBigDecimal(row.get("score_value")));
            insertRow.put("scoreScaleText", nullableString(row.get("score_scale_text")));
            studentProfileMapper.insertLanguageTestScoreItem(insertRow);
        }
    }

    private boolean isMeaningfulLanguageTestRecordRow(Map<String, Object> row) {
        return hasAnyMeaningfulValue(
            row,
            Set.of("student_language_test_record_id", "student_id", "delete_flag", "create_time", "update_time")
        );
    }

    private boolean isMeaningfulLanguageTestScoreItemRow(Map<String, Object> row) {
        return hasAnyMeaningfulValue(
            row,
            Set.of(
                "student_language_test_score_item_id",
                "student_language_test_record_id",
                "delete_flag",
                "create_time",
                "update_time"
            )
        );
    }

    private boolean hasAnyMeaningfulValue(Map<String, Object> row, Set<String> ignoredFields) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (ignoredFields.contains(entry.getKey())) {
                continue;
            }
            if (hasMeaningfulValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMeaningfulValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return !stringValue.isBlank();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
    }


    private List<Map<String, Object>> toMutableRows(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> mapValue) {
                rows.add(
                    new LinkedHashMap<>(
                        objectMapper.convertValue(mapValue, new TypeReference<Map<String, Object>>() {
                        })
                    )
                );
            }
        }
        return rows;
    }

    private List<Map<String, Object>> normalizeLanguageTestRecords(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("student_language_test_record_id", toLong(row.get("student_language_test_record_id")));
            normalized.put("student_id", normalizeScalar(row.get("student_id")));
            normalized.put("test_type_code", normalizeScalar(row.get("test_type_code")));
            normalized.put("status_code", normalizeScalar(row.get("status_code")));
            normalized.put("test_date", normalizeScalar(row.get("test_date")));
            normalized.put("exam_name_text", normalizeScalar(row.get("exam_name_text")));
            normalized.put("total_score", normalizeScalar(row.get("total_score")));
            normalized.put("score_scale_text", normalizeScalar(row.get("score_scale_text")));
            normalized.put("cefr_level_code", normalizeScalar(row.get("cefr_level_code")));
            normalized.put("evidence_level_code", normalizeScalar(row.get("evidence_level_code")));
            normalized.put("is_best_score", toBoolean(row.get("is_best_score")));
            normalized.put("notes", normalizeScalar(row.get("notes")));
            result.add(normalized);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeLanguageTestScoreItems(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("student_language_test_score_item_id", toLong(row.get("student_language_test_score_item_id")));
            normalized.put("student_language_test_record_id", toLong(row.get("student_language_test_record_id")));
            normalized.put("score_item_code", normalizeScalar(row.get("score_item_code")));
            normalized.put("score_value", normalizeScalar(row.get("score_value")));
            normalized.put("score_scale_text", normalizeScalar(row.get("score_scale_text")));
            result.add(normalized);
        }
        return result;
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(
            source,
            new TypeReference<LinkedHashMap<String, Object>>() {
            }
        );
    }

    private Map<String, Object> toMapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : listValue) {
            String text = nullableString(item);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private AiChatSessionRow requireOwnedSession(String studentId, String sessionId) {
        AiChatSessionRow session = toAiChatSessionRow(aiChatPersistenceMapper.findSessionById(sessionId));
        if (session == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "session not found");
        }
        if (!studentId.equals(session.studentId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "session does not belong to current student");
        }
        return session;
    }

    private void ensureSessionNotBusy(AiChatSessionRow session) {
        if (session != null && BUSY_STAGES.contains(String.valueOf(firstNonBlank(session.currentStage(), "")))) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "current profile is still processing, please retry after it finishes"
            );
        }
    }

    private void ensureProfileResultBelongsToStudent(AiChatProfileResultRow result, String studentId) {
        if (result != null && !studentId.equals(result.studentId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "profile result does not belong to current student");
        }
    }

    private void updateSessionStage(String studentId, String sessionId, String currentStage) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sessionId", sessionId);
        row.put("studentId", studentId);
        row.put("currentStage", currentStage);
        row.put("remark", null);
        row.put("now", Timestamp.valueOf(now));
        aiChatPersistenceMapper.updateSessionStage(row);
    }

    private AiChatProfileResultRow findProfileResultBySessionId(String sessionId) {
        return toAiChatProfileResultRow(aiChatPersistenceMapper.findProfileResultBySessionId(sessionId));
    }

    private GuidedProfileResultRow findLatestGuidedResultByStudentId(String studentId) {
        return toGuidedProfileResultRow(aiChatPersistenceMapper.findLatestGuidedResultByStudentId(studentId));
    }

    private AiChatSessionRow toAiChatSessionRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatSessionRow(
            nullableString(column(row, "session_id")),
            nullableString(column(row, "student_id")),
            nullableString(column(row, "biz_domain")),
            nullableString(column(row, "current_stage"))
        );
    }

    private AiChatProfileResultRow toAiChatProfileResultRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new AiChatProfileResultRow(
            nullableString(column(row, "student_id")),
            nullableString(column(row, "result_status")),
            toMapOrEmpty(parseJsonValue(column(row, "radar_scores_json"))),
            nullableString(column(row, "summary_text")),
            nullableString(column(row, "save_error_message")),
            toLocalDateTime(column(row, "create_time")),
            toLocalDateTime(column(row, "update_time"))
        );
    }

    private GuidedProfileResultRow toGuidedProfileResultRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return new GuidedProfileResultRow(
            nullableString(column(row, "student_id")),
            nullableString(column(row, "result_status")),
            toMapOrEmpty(parseJsonValue(column(row, "radar_scores_json"))),
            nullableString(column(row, "summary_text")),
            nullableString(column(row, "save_error_message")),
            toLocalDateTime(column(row, "create_time")),
            toLocalDateTime(column(row, "update_time"))
        );
    }

    private Object column(Map<String, Object> row, String columnName) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (columnName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        String text = nullableString(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception exception) {
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private List<ColumnMeta> getEditableColumns(String tableName) {
        if (!tableExists(tableName)) {
            editableColumnsCache.remove(tableName);
            return List.of();
        }
        List<ColumnMeta> cached = editableColumnsCache.get(tableName);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<ColumnMeta> loaded = loadEditableColumns(tableName);
        if (loaded.isEmpty()) {
            editableColumnsCache.remove(tableName);
        } else {
            editableColumnsCache.put(tableName, loaded);
        }
        return loaded;
    }

    private List<ColumnMeta> getFormColumns(String tableName) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<ColumnMeta> columns = new ArrayList<>();
        for (ColumnMeta column : getTableColumns(tableName)) {
            if ("create_time".equals(column.name()) || "update_time".equals(column.name()) || "delete_flag".equals(column.name())) {
                continue;
            }
            if (isRemovedProfileField(tableName, column.name())) {
                continue;
            }
            columns.add(column);
        }
        return columns;
    }

    private List<ColumnMeta> loadEditableColumns(String tableName) {
        if (!tableExists(tableName)) {
            return List.of();
        }
        List<Map<String, Object>> rows = studentProfileMapper.showColumns(safeProfileTableName(tableName));
        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(rowValue(row, "Field", "field"));
            String fieldType = String.valueOf(rowValue(row, "Type", "type"));
            if ("create_time".equals(fieldName) || "update_time".equals(fieldName) || "delete_flag".equals(fieldName)) {
                continue;
            }
            if (isRemovedProfileField(tableName, fieldName)) {
                continue;
            }
            if (isAutoIncrementColumn(row)) {
                continue;
            }
            columns.add(new ColumnMeta(fieldName, fieldType));
        }
        return columns;
    }

    private boolean isAutoIncrementColumn(Map<String, Object> row) {
        Object extraValue = rowValue(row, "Extra", "extra");
        return extraValue != null && String.valueOf(extraValue).toLowerCase().contains("auto_increment");
    }

    private boolean tableExists(String tableName) {
        if (Boolean.TRUE.equals(tableExistsCache.get(tableName))) {
            return true;
        }
        Integer count = studentProfileMapper.countTable(safeKnownTableName(tableName));
        boolean exists = count != null && count > 0;
        if (exists) {
            tableExistsCache.put(tableName, true);
        } else {
            tableExistsCache.remove(tableName);
            editableColumnsCache.remove(tableName);
            tableColumnsCache.remove(tableName);
        }
        return exists;
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
        String normalizedColumnType = nullableString(columnType) == null
            ? ""
            : String.valueOf(columnType).toLowerCase();
        if (normalizedColumnType.contains("json")) {
            return parseJsonValue(value);
        }
        return normalizeScalar(value);
    }

    private Object parseJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception exception) {
            return text;
        }
    }

    private List<Map<String, String>> resolveFieldOptions(
        String tableName,
        String fieldName,
        String columnType,
        Map<String, List<Map<String, String>>> cachedOptions
    ) {
        String staticKey = STATIC_FIELD_OPTIONS.get(key(tableName, fieldName));
        if (staticKey != null && !staticKey.isBlank()) {
            return cachedOptions.computeIfAbsent(staticKey, this::loadStaticOptionsByKey);
        }
        List<String> explicitOptions = ENUM_FIELD_OPTIONS.get(key(tableName, fieldName));
        if (explicitOptions != null && !explicitOptions.isEmpty()) {
            return buildEnumOptions(tableName, fieldName, explicitOptions);
        }
        List<Map<String, String>> parsedOptions = parseEnumOptions(tableName, fieldName, columnType);
        return parsedOptions == null ? List.of() : parsedOptions;
    }

    private List<Map<String, String>> loadStaticOptionsByKey(String optionKey) {
        return switch (optionKey) {
            case "curriculum_system" ->
                loadSimpleDictionaryOptions(
                    "dict_curriculum_system",
                    "curriculum_system_code",
                    "curriculum_system_name_cn",
                    "curriculum_system_name_en"
                );
            case "gpa_scale" ->
                loadSimpleDictionaryOptions(
                    "dict_gpa_scale",
                    "gpa_scale_code",
                    "gpa_scale_name_cn",
                    "gpa_scale_name_en"
                );
            case "us_high_school_course" ->
                loadSimpleDictionaryOptions(
                    "dict_us_high_school_course",
                    "us_high_school_course_id",
                    "course_name_cn",
                    "course_name_en"
                );
            case "a_level_subject" ->
                loadSimpleDictionaryOptions(
                    "dict_a_level_subject",
                    "al_subject_id",
                    "subject_name_cn",
                    "subject_name_en"
                );
            case "ap_course" ->
                loadSimpleDictionaryOptions(
                    "dict_ap_course",
                    "ap_course_id",
                    "course_name_cn",
                    "course_name_en"
                );
            case "ib_subject" ->
                loadSimpleDictionaryOptions(
                    "dict_ib_subject",
                    "ib_subject_id",
                    "subject_name_cn",
                    "subject_name_en"
                );
            case "chs_subject" ->
                loadSimpleDictionaryOptions(
                    "dict_chinese_high_school_subject",
                    "chs_subject_id",
                    "subject_name_cn",
                    "subject_name_en"
                );
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
        List<Map<String, Object>> rows = studentProfileMapper.selectDictionaryOptions(
            safeDictionaryTableName(tableName),
            safeIdentifier(valueColumn),
            safeIdentifier(labelCnColumn),
            safeIdentifier(labelEnColumn)
        );

        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String value = String.valueOf(rowValue(row, "value"));
            String labelCn = nullableString(rowValue(row, "label_cn", "labelCn"));
            String labelEn = nullableString(rowValue(row, "label_en", "labelEn"));
            result.add(option(value, firstNonBlank(labelCn, labelEn, value)));
        }
        return result;
    }

    private List<Map<String, String>> buildEnumOptions(String tableName, String fieldName, List<String> values) {
        Map<String, String> labelMap = ENUM_OPTION_LABELS.getOrDefault(key(tableName, fieldName), Map.of());
        List<Map<String, String>> options = new ArrayList<>();
        for (String value : values) {
            options.add(option(value, labelMap.getOrDefault(value, value)));
        }
        return options;
    }

    private List<Map<String, String>> parseEnumOptions(String tableName, String fieldName, String columnType) {
        String normalized = nullableString(columnType);
        if (normalized == null || !normalized.startsWith("enum(") || !normalized.endsWith(")")) {
            return null;
        }
        String inner = normalized.substring("enum(".length(), normalized.length() - 1);
        if (inner.isBlank()) {
            return null;
        }
        Map<String, String> labelMap = ENUM_OPTION_LABELS.getOrDefault(key(tableName, fieldName), Map.of());
        List<Map<String, String>> options = new ArrayList<>();
        for (String rawItem : inner.split(",")) {
            String value = rawItem.trim();
            if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            value = value.replace("''", "'");
            options.add(option(value, labelMap.getOrDefault(value, value)));
        }
        return options;
    }

    private String inferInputType(String fieldName, String columnType, List<Map<String, String>> options) {
        String normalizedType = columnType == null ? "" : columnType.toLowerCase();
        if ("participants_text".equals(fieldName)) {
            return "integer";
        }
        if (options != null && !options.isEmpty()) {
            return "select";
        }
        if (TEXTAREA_FIELDS.contains(fieldName) || normalizedType.contains("json") || normalizedType.contains("text")) {
            return "textarea";
        }
        if (normalizedType.startsWith("date") || normalizedType.startsWith("datetime") || normalizedType.startsWith("timestamp")) {
            return "date";
        }
        if (normalizedType.startsWith("year")) {
            return "number";
        }
        if (normalizedType.startsWith("tinyint(1)") || fieldName.startsWith("is_") || fieldName.startsWith("prefer_")) {
            return "checkbox";
        }
        if (
            normalizedType.contains("int")
                || normalizedType.contains("decimal")
                || normalizedType.contains("float")
                || normalizedType.contains("double")
        ) {
            return "number";
        }
        return "text";
    }

    private boolean isFieldHidden(String tableName, String fieldName) {
        if (HIDDEN_FIELDS.contains(fieldName)) {
            return true;
        }
        return HIDDEN_FIELDS_BY_TABLE.getOrDefault(tableName, Set.of()).contains(fieldName);
    }

    private String humanizeFieldName(String fieldName) {
        String[] parts = fieldName == null ? new String[0] : fieldName.split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase());
        }
        return words.isEmpty() ? fieldName : String.join(" ", words);
    }

    private Long insertDynamicRow(String tableName, Map<String, Object> row) {
        Map<String, Object> insertRow = prepareInsertRow(tableName, row);
        if (insertRow.isEmpty()) {
            return null;
        }
        List<String> columns = new ArrayList<>(insertRow.keySet());
        String columnList = columns.stream().map(column -> "`" + column + "`").collect(java.util.stream.Collectors.joining(", "));
        List<Object> values = columns.stream().map(insertRow::get).toList();

        Map<String, Object> mutation = new LinkedHashMap<>();
        mutation.put("tableName", safeProfileTableName(tableName));
        mutation.put("columnList", columnList);
        mutation.put("values", values);
        studentProfileMapper.insertDynamicRow(mutation);
        return toLong(mutation.get("generatedId"));
    }

    private Map<String, Object> prepareInsertRow(String tableName, Map<String, Object> row) {
        Map<String, ColumnMeta> columnMap = getColumnMap(tableName);
        Map<String, Object> insertRow = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            ColumnMeta column = columnMap.get(entry.getKey());
            if (column == null || "create_time".equals(column.name()) || "update_time".equals(column.name())) {
                continue;
            }
            if (AUTO_INCREMENT_FIELDS.contains(column.name())) {
                continue;
            }
            insertRow.put(column.name(), normalizeWriteValue(entry.getValue(), column.type()));
        }
        if (columnMap.containsKey("delete_flag") && !insertRow.containsKey("delete_flag")) {
            insertRow.put("delete_flag", "1");
        }
        return insertRow;
    }

    private Object normalizeWriteValue(Object value, String columnType) {
        if (value == null) {
            return null;
        }
        String normalizedType = columnType == null ? "" : columnType.toLowerCase();
        if (value instanceof Boolean boolValue) {
            return normalizedType.contains("int") || normalizedType.contains("bit") ? (boolValue ? 1 : 0) : boolValue;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("invalid json field value", exception);
            }
        }
        return value;
    }

    private Map<String, ColumnMeta> getColumnMap(String tableName) {
        Map<String, ColumnMeta> result = new LinkedHashMap<>();
        for (ColumnMeta column : getTableColumns(tableName)) {
            result.put(column.name(), column);
        }
        return result;
    }

    private boolean hasColumn(String tableName, String columnName) {
        for (ColumnMeta column : getTableColumns(tableName)) {
            if (column.name().equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    private List<ColumnMeta> getTableColumns(String tableName) {
        if (!tableExists(tableName)) {
            tableColumnsCache.remove(tableName);
            return List.of();
        }
        List<ColumnMeta> cached = tableColumnsCache.get(tableName);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<Map<String, Object>> rows = studentProfileMapper.showColumns(safeProfileTableName(tableName));
        List<ColumnMeta> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String fieldName = String.valueOf(rowValue(row, "Field", "field"));
            if (isRemovedProfileField(tableName, fieldName)) {
                continue;
            }
            columns.add(
                new ColumnMeta(
                    fieldName,
                    String.valueOf(rowValue(row, "Type", "type"))
                )
            );
        }
        if (columns.isEmpty()) {
            tableColumnsCache.remove(tableName);
        } else {
            tableColumnsCache.put(tableName, columns);
        }
        return columns;
    }

    private boolean isRemovedProfileField(String tableName, String fieldName) {
        return PROFILE_REMOVED_FIELDS_BY_TABLE.getOrDefault(tableName, Set.of()).contains(fieldName);
    }

    private List<String> safeColumnNames(List<ColumnMeta> columns) {
        List<String> names = new ArrayList<>();
        for (ColumnMeta column : columns) {
            names.add(safeIdentifier(column.name()));
        }
        return names;
    }

    private String safeKnownTableName(String tableName) {
        if (
            CURRICULUM_TABLES.contains(tableName)
                || STANDARDIZED_TABLES.contains(tableName)
                || EXPERIENCE_TABLES.contains(tableName)
                || DICTIONARY_TABLES.contains(tableName)
        ) {
            return safeIdentifier(tableName);
        }
        throw new IllegalArgumentException("Unsupported table: " + tableName);
    }

    private String safeProfileTableName(String tableName) {
        if (
            !LANGUAGE_PARENT_TABLE.equals(tableName)
                && !CURRICULUM_TABLES.contains(tableName)
                && !STANDARDIZED_TABLES.contains(tableName)
                && !EXPERIENCE_TABLES.contains(tableName)
        ) {
            throw new IllegalArgumentException("Unsupported profile table: " + tableName);
        }
        return safeIdentifier(tableName);
    }

    private String safeDictionaryTableName(String tableName) {
        if (!DICTIONARY_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("Unsupported dictionary table: " + tableName);
        }
        return safeIdentifier(tableName);
    }

    private String safeIdentifier(String value) {
        if (value == null || value.isBlank() || !SQL_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        }
        return value;
    }

    private Object rowValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private Object normalizeScalar(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toString();
        }
        if (value instanceof Timestamp timestamp) {
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

    private String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer toTinyInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0 ? 1 : 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) ? 1 : 0;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return Boolean.FALSE;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return Boolean.FALSE;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, String> mergeStringMap(
        Map<String, String> base,
        Map<String, String> overrides
    ) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return Map.copyOf(merged);
    }

    private static Map<String, Set<String>> mergeSetMap(
        Map<String, Set<String>> base,
        Map<String, Set<String>> overrides
    ) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : base.entrySet()) {
            merged.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        for (Map.Entry<String, Set<String>> entry : overrides.entrySet()) {
            merged.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(merged);
    }

    private static String key(String tableName, String fieldName) {
        return tableName + "." + fieldName;
    }

    private record AiChatSessionRow(
        String sessionId,
        String studentId,
        String bizDomain,
        String currentStage
    ) {
    }

    private record AiChatProfileResultRow(
        String studentId,
        String resultStatus,
        Map<String, Object> radarScoresJson,
        String summaryText,
        String saveErrorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    private record GuidedProfileResultRow(
        String studentId,
        String resultStatus,
        Map<String, Object> radarScoresJson,
        String summaryText,
        String saveErrorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    private record ColumnMeta(String name, String type) {
    }
}
