import React, { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate, useSearchParams } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
} from "recharts";
import {
  bindMyMobile,
  checkMyNickname,
  checkMyPassword,
  checkMyResetPassword,
  getMe,
  logout,
  resetMyPasswordBySms,
  sendSmsCode,
  setPassword,
  updateMyNickname,
} from "../api/auth";
import {
  getStudentProfileCurrentSession,
  getStudentProfileArchive,
  regenerateStudentProfileArchiveRadar,
  saveStudentProfileArchive,
  syncStudentProfileArchiveDraft,
} from "../api/studentProfile";
import { InlineLoading, LoadingOverlay } from "../components/LoadingPage";
import { clearAccessToken } from "../utils/authStorage";
import { validatePasswordRule } from "../utils/passwordValidation";
const AI_CHAT_SESSION_CACHE_KEY = "latest_ai_chat_session_id";
const GUIDED_PROFILE_OPEN_PANEL_KEY = "open_guided_profile_panel";
const RADAR_LABELS = {
  academic: "学术成绩",
  language: "语言能力",
  standardized: "考试成绩",
  competition: "学术竞赛",
  activity: "活动/企业实习",
  project: "科研经历",
};

const RADAR_COLORS = {
  academic: { from: "#2c4a8a", to: "#4f7ad6", bg: "rgba(44,74,138,0.10)" },
  language: { from: "#0f9f7c", to: "#34d399", bg: "rgba(15,159,124,0.10)" },
  standardized: { from: "#c77b18", to: "#f59e0b", bg: "rgba(245,158,11,0.12)" },
  competition: { from: "#9c4ddb", to: "#c084fc", bg: "rgba(156,77,219,0.12)" },
  activity: { from: "#cc4e74", to: "#f472b6", bg: "rgba(244,114,182,0.12)" },
  project: { from: "#0891b2", to: "#22d3ee", bg: "rgba(34,211,238,0.12)" },
};

const CURRICULUM_MODULES = {
  CHINESE_HIGH_SCHOOL: {
    label: "中国普高",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_chinese_high_school_subject"],
  },
  US_HIGH_SCHOOL: {
    label: "国际学校美高体系",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_ap_subject", "student_standardized_sat", "student_standardized_act"],
  },
  A_LEVEL: {
    label: "A-Level",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_a_level_subject"],
  },
  AP: {
    label: "AP",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_ap_subject"],
  },
  IB: {
    label: "IB",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_ib_subject"],
  },
  OSSD: {
    label: "OSSD",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_ossd_subject"],
  },
  OTHER: {
    label: "其他课程体系",
    curriculumTables: ["student_academic_curriculum_gpa"],
    standardizedTables: ["student_academic_other_curriculum_subject"],
  },
};
const REMOVED_CURRICULUM_SYSTEM_CODES = new Set(["INTERNATIONAL_OTHER"]);

const CURRICULUM_TABLE_NAMES = new Set(
  Object.values(CURRICULUM_MODULES).flatMap((item) => [
    ...(item.curriculumTables || []),
    ...(item.standardizedTables || []),
  ])
);

const CURRICULUM_SECTION_TABLES = [
  "student_basic_info_curriculum_system",
  ...Array.from(CURRICULUM_TABLE_NAMES),
];
const CURRICULUM_SECTION_TABLE_NAME_SET = new Set(CURRICULUM_SECTION_TABLES);
const CURRICULUM_SCOPED_TABLES = new Set([
  "student_academic_curriculum_gpa",
  "student_academic_other_curriculum_subject",
]);
const LEGACY_CURRICULUM_DETAIL_TABLE_NAME_SET = new Set([
  "student_academic_us_high_school_profile",
  "student_academic_other_curriculum_profile",
  "student_academic_a_level_profile",
  "student_academic_ap_profile",
  "student_academic_ib_profile",
  "student_academic_chinese_high_school_profile",
]);

const LANGUAGE_TEST_RECORD_TABLE = "student_language_test_record";
const LANGUAGE_TEST_SCORE_ITEM_TABLE = "student_language_test_score_item";
const LANGUAGE_SECTION_TABLES = [LANGUAGE_TEST_RECORD_TABLE, LANGUAGE_TEST_SCORE_ITEM_TABLE];
const LANGUAGE_SECTION_TABLE_NAME_SET = new Set(LANGUAGE_SECTION_TABLES);
const STANDARDIZED_SAT_TABLE = "student_standardized_sat";
const STANDARDIZED_ACT_TABLE = "student_standardized_act";
const STANDARDIZED_SECTION_TABLES = [STANDARDIZED_SAT_TABLE, STANDARDIZED_ACT_TABLE];
const STANDARDIZED_SECTION_TABLE_NAME_SET = new Set(STANDARDIZED_SECTION_TABLES);
const STANDARDIZED_SELECTOR_CONFIG = [
  { tableName: STANDARDIZED_SAT_TABLE, label: "SAT" },
  { tableName: STANDARDIZED_ACT_TABLE, label: "ACT" },
  { tableName: "student_academic_chinese_high_school_subject", label: "普高科目成绩" },
  { tableName: "student_academic_us_high_school_subject", label: "美高科目成绩" },
  { tableName: "student_academic_a_level_subject", label: "A-Level 科目成绩" },
  { tableName: "student_academic_ap_subject", label: "AP 课程成绩" },
  { tableName: "student_academic_ib_subject", label: "IB 科目成绩" },
  { tableName: "student_academic_ossd_subject", label: "OSSD 科目成绩" },
  { tableName: "student_academic_other_curriculum_subject", label: "其他课程体系成绩" },
];
const STANDARDIZED_SELECTOR_TABLES = STANDARDIZED_SELECTOR_CONFIG.map((item) => item.tableName);
const STANDARDIZED_SELECTOR_TABLE_NAME_SET = new Set(STANDARDIZED_SELECTOR_TABLES);
const PROFILE_REMOVED_FIELD_NAMES_BY_TABLE = {
  student_academic_ossd_subject: new Set(["school_year_label", "term_code", "score_text", "score_scale_code"]),
  student_academic_a_level_subject: new Set(["exam_series"]),
};
const PROFILE_FIELD_LABEL_OVERRIDES_BY_TABLE = {
  student_academic_ossd_subject: {
    score_numeric: "课程分数",
  },
};
const LEGACY_STANDARDIZED_SELECTOR_TABLE_NAME_SET = new Set([
  "student_academic_us_high_school_course",
  "student_academic_ap_course",
]);
const LEGACY_STANDARDIZED_DETAIL_TABLE_NAME_SET = new Set([
  "student_standardized_tests",
  "student_standardized_test_records",
]);
const LEGACY_LANGUAGE_DETAIL_TABLE_NAME_SET = new Set([
  "student_language_ielts",
  "student_language_toefl_ibt",
  "student_language_toefl_home",
  "student_language_toefl_essentials",
  "student_language_det",
  "student_language_pte",
  "student_language_languagecert",
  "student_language_languagecert_academic",
  "student_language_cambridge",
  "student_language_other",
]);
const ACTIVITY_EXPERIENCE_TABLE = "student_activity_experience";
const ACTIVITY_ATTACHMENT_TABLE = "student_activity_attachment";
const ENTERPRISE_INTERNSHIP_TABLE = "student_enterprise_internship";
const ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE = "student_enterprise_internship_attachment";
const RESEARCH_EXPERIENCE_TABLE = "student_research_experience";
const RESEARCH_ATTACHMENT_TABLE = "student_research_attachment";
const COMPETITION_RECORD_TABLE = "student_competition_record";
const COMPETITION_ATTACHMENT_TABLE = "student_competition_attachment";
const EXPERIENCE_MAIN_TABLES = [
  ACTIVITY_EXPERIENCE_TABLE,
  ENTERPRISE_INTERNSHIP_TABLE,
  RESEARCH_EXPERIENCE_TABLE,
  COMPETITION_RECORD_TABLE,
];
const EXPERIENCE_MAIN_TABLE_NAME_SET = new Set(EXPERIENCE_MAIN_TABLES);
const EXPERIENCE_ATTACHMENT_TABLE_NAME_SET = new Set([
  ACTIVITY_ATTACHMENT_TABLE,
  ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE,
  RESEARCH_ATTACHMENT_TABLE,
  COMPETITION_ATTACHMENT_TABLE,
]);
const LEGACY_EXPERIENCE_DETAIL_TABLE_NAME_SET = new Set([
  "student_competitions",
  "student_competition_entries",
  "student_activities",
  "student_activity_entries",
  "student_projects_experience",
  "student_project_entries",
  "student_project_outputs",
]);
const TEMP_NUMERIC_ID_FIELD_BY_TABLE = {
  [LANGUAGE_TEST_RECORD_TABLE]: "student_language_test_record_id",
  [ACTIVITY_EXPERIENCE_TABLE]: "student_activity_experience_id",
  [ENTERPRISE_INTERNSHIP_TABLE]: "student_enterprise_internship_id",
  [RESEARCH_EXPERIENCE_TABLE]: "student_research_experience_id",
  [COMPETITION_RECORD_TABLE]: "student_competition_record_id",
};
const PARENT_ROW_CONFIG_BY_CHILD_TABLE = {
  [LANGUAGE_TEST_SCORE_ITEM_TABLE]: {
    parentTable: LANGUAGE_TEST_RECORD_TABLE,
    parentField: "student_language_test_record_id",
    parentIdField: "student_language_test_record_id",
    getLabel: (row) => row?.exam_name_text || row?.test_type_code || `语言考试 ${row?.student_language_test_record_id}`,
  },
  [ACTIVITY_ATTACHMENT_TABLE]: {
    parentTable: ACTIVITY_EXPERIENCE_TABLE,
    parentField: "student_activity_experience_id",
    parentIdField: "student_activity_experience_id",
    getLabel: (row) => row?.activity_summary || `活动 ${row?.student_activity_experience_id}`,
  },
  [ENTERPRISE_INTERNSHIP_ATTACHMENT_TABLE]: {
    parentTable: ENTERPRISE_INTERNSHIP_TABLE,
    parentField: "student_enterprise_internship_id",
    parentIdField: "student_enterprise_internship_id",
    getLabel: (row) =>
      [row?.company_name, row?.position_name].filter(Boolean).join(" / ") ||
      `实习 ${row?.student_enterprise_internship_id}`,
  },
  [RESEARCH_ATTACHMENT_TABLE]: {
    parentTable: RESEARCH_EXPERIENCE_TABLE,
    parentField: "student_research_experience_id",
    parentIdField: "student_research_experience_id",
    getLabel: (row) => row?.research_summary || `科研 ${row?.student_research_experience_id}`,
  },
  [COMPETITION_ATTACHMENT_TABLE]: {
    parentTable: COMPETITION_RECORD_TABLE,
    parentField: "student_competition_record_id",
    parentIdField: "student_competition_record_id",
    getLabel: (row) => row?.competition_name || `竞赛 ${row?.student_competition_record_id}`,
  },
};
const CHILD_TABLE_CONFIG_BY_PARENT_TABLE = Object.fromEntries(
  Object.entries(PARENT_ROW_CONFIG_BY_CHILD_TABLE).map(([childTable, config]) => [
    config.parentTable,
    { ...config, childTable },
  ])
);
const MONTH_RANGE_TABLE_NAME_SET = new Set([
  ACTIVITY_EXPERIENCE_TABLE,
  ENTERPRISE_INTERNSHIP_TABLE,
]);
const MONTH_PICKER_MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => {
  const monthValue = String(index + 1).padStart(2, "0");
  return {
    value: monthValue,
    label: `${index + 1}月`,
  };
});
const MONTH_PICKER_YEAR_OPTIONS = (() => {
  const currentYear = new Date().getFullYear();
  return Array.from({ length: 37 }, (_, index) => {
    const year = String(currentYear + 8 - index);
    return {
      value: year,
      label: `${year}年`,
    };
  });
})();
const STANDARDIZED_FALLBACK_FORM_META_TABLES = {
  [STANDARDIZED_SAT_TABLE]: {
    label: "SAT",
    kind: "multi",
    fields: [
      { name: "student_standardized_sat_id", label: "SAT成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "status_code", label: "成绩状态", input_type: "select", hidden: false, options: [
        { value: "SCORED", label: "已出分" },
        { value: "PLANNED", label: "待考试" },
        { value: "ESTIMATED", label: "预估" },
      ], helper_text: null },
      { name: "test_date", label: "考试日期", input_type: "date", hidden: false, options: [], helper_text: null },
      { name: "total_score", label: "总分", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "sat_erw", label: "SAT 阅读与写作", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "sat_math", label: "SAT 数学", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "is_best_score", label: "是否最佳成绩", input_type: "checkbox", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
  [STANDARDIZED_ACT_TABLE]: {
    label: "ACT",
    kind: "multi",
    fields: [
      { name: "student_standardized_act_id", label: "ACT成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "status_code", label: "成绩状态", input_type: "select", hidden: false, options: [
        { value: "SCORED", label: "已出分" },
        { value: "PLANNED", label: "待考试" },
        { value: "ESTIMATED", label: "预估" },
      ], helper_text: null },
      { name: "test_date", label: "考试日期", input_type: "date", hidden: false, options: [], helper_text: null },
      { name: "total_score", label: "总分", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "act_english", label: "ACT 英语", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "act_math", label: "ACT 数学", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "act_reading", label: "ACT 阅读", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "act_science", label: "ACT 科学", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "is_best_score", label: "是否最佳成绩", input_type: "checkbox", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
  student_academic_us_high_school_subject: {
    label: "美高科目成绩",
    kind: "multi",
    fields: [
      { name: "student_academic_us_high_school_subject_id", label: "美高科目成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "school_year_label", label: "学年/年级", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "term_code", label: "学期编码", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "us_high_school_course_id", label: "美高科目", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "course_name_text", label: "课程名称", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "course_category_code", label: "课程类别", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "course_level_code", label: "课程级别", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "grade_letter_code", label: "字母成绩", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "grade_percent", label: "百分制成绩", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "credit_earned", label: "学分", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
  student_academic_ap_subject: {
    label: "AP 课程成绩",
    kind: "multi",
    fields: [
      { name: "student_academic_ap_subject_id", label: "AP课程成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "ap_course_id", label: "AP 科目", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "score", label: "分数", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "year_taken", label: "考试年份", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
  student_academic_ossd_subject: {
    label: "OSSD 科目成绩",
    kind: "multi",
    fields: [
      { name: "student_academic_ossd_subject_id", label: "OSSD科目成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "course_name_text", label: "课程名称", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "course_level_code", label: "课程级别", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "score_numeric", label: "课程分数", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "credit_earned", label: "学分", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
  student_academic_other_curriculum_subject: {
    label: "其他课程体系成绩",
    kind: "multi",
    fields: [
      { name: "student_academic_other_curriculum_subject_id", label: "其他课程体系成绩ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "curriculum_system_code", label: "课程体系", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "school_year_label", label: "学年/年级", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "term_code", label: "学期编码", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "subject_name_text", label: "科目名称", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "subject_level_text", label: "科目级别", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "score_text", label: "原始成绩", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "score_numeric", label: "数值成绩", input_type: "number", hidden: false, options: [], helper_text: null },
      { name: "score_scale_code", label: "成绩分制", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "notes", label: "备注", input_type: "textarea", hidden: false, options: [], helper_text: null },
    ],
  },
};
const EXPERIENCE_FALLBACK_FORM_META_TABLES = {
  [ACTIVITY_EXPERIENCE_TABLE]: {
    label: "活动经历",
    kind: "multi",
    fields: [
      { name: "student_activity_experience_id", label: "活动经历ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "activity_summary", label: "活动简述", input_type: "textarea", hidden: false, options: [], helper_text: null },
      { name: "referrer_name", label: "推荐人", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "start_time", label: "开始时间", input_type: "date", hidden: false, options: [], helper_text: null },
      { name: "end_time", label: "结束时间", input_type: "date", hidden: false, options: [], helper_text: null },
    ],
  },
  [ENTERPRISE_INTERNSHIP_TABLE]: {
    label: "企业实习",
    kind: "multi",
    fields: [
      { name: "student_enterprise_internship_id", label: "企业实习ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "start_time", label: "开始时间", input_type: "date", hidden: false, options: [], helper_text: null },
      { name: "end_time", label: "结束时间", input_type: "date", hidden: false, options: [], helper_text: null },
      { name: "company_name", label: "企业名", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "position_name", label: "岗位", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "referrer_name", label: "推荐人", input_type: "text", hidden: false, options: [], helper_text: null },
    ],
  },
  [RESEARCH_EXPERIENCE_TABLE]: {
    label: "科研经历",
    kind: "multi",
    fields: [
      { name: "student_research_experience_id", label: "科研经历ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "research_summary", label: "科研经历简述", input_type: "textarea", hidden: false, options: [], helper_text: null },
      { name: "initiator_name", label: "发起方", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "role_name", label: "担任角色", input_type: "text", hidden: false, options: [], helper_text: null },
    ],
  },
  [COMPETITION_RECORD_TABLE]: {
    label: "学术竞赛",
    kind: "multi",
    fields: [
      { name: "student_competition_record_id", label: "竞赛记录ID", input_type: "number", hidden: true, options: [], helper_text: null },
      { name: "student_id", label: "学生ID", input_type: "text", hidden: true, options: [], helper_text: null },
      { name: "competition_name", label: "竞赛名称", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "competition_field", label: "竞赛领域", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "competition_level", label: "竞赛级别", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "participants_text", label: "参赛人数", input_type: "integer", hidden: false, options: [], helper_text: null },
      { name: "result_text", label: "成绩描述", input_type: "text", hidden: false, options: [], helper_text: null },
      { name: "competition_year", label: "参赛年份", input_type: "number", hidden: false, options: [], helper_text: null },
    ],
  },
};

const TARGET_COUNTRY_TABLE = "student_basic_info_target_country_entries";
const TARGET_MAJOR_TABLE = "student_basic_info_target_major_entries";
const MAX_TARGET_COUNTRY_ROWS = 3;
const MAX_TARGET_MAJOR_ROWS = 2;
const TARGET_PREFERENCE_DRAFT_FIELD = "__draft_row";

const NON_CONTENT_FIELD_NAMES = new Set([
  "student_id",
  "student_academic_id",
  "student_language_id",
  "student_standardized_test_id",
  "student_standardized_sat_id",
  "student_standardized_act_id",
  "schema_version",
  "profile_type",
  "notes",
  TARGET_PREFERENCE_DRAFT_FIELD,
]);

const SEARCHABLE_SELECT_FIELDS = {
  student_basic_info: new Set(["CTRY_CODE_VAL", "MAJ_CODE_VAL"]),
};

const BASIC_TARGET_FIELDS = new Set(["CTRY_CODE_VAL", "MAJ_CODE_VAL", "MAJ_INTEREST_TEXT"]);

const FIELD_INPUT_PLACEHOLDERS = {
  student_basic_info: {
    MAJ_INTEREST_TEXT: "若下拉没有合适选项，可填写原始专业表述，例如：人文",
  },
};

const UNIQUE_SUBJECT_SELECT_FIELD_BY_TABLE = {
  student_academic_a_level_subject: "al_subject_id",
  student_academic_ap_subject: "ap_course_id",
  student_academic_ib_subject: "ib_subject_id",
  student_academic_chinese_high_school_subject: "chs_subject_id",
  student_language_test_score_item: "score_item_code",
};

const ROW_TABLES_WITH_STUDENT_ID = new Set([
  "student_basic_info_curriculum_system",
  TARGET_COUNTRY_TABLE,
  TARGET_MAJOR_TABLE,
  "student_academic_curriculum_gpa",
  "student_academic_a_level_subject",
  "student_academic_ap_subject",
  "student_academic_ib_subject",
  "student_academic_chinese_high_school_subject",
  "student_academic_us_high_school_subject",
  "student_academic_ossd_subject",
  "student_academic_other_curriculum_subject",
  LANGUAGE_TEST_RECORD_TABLE,
  STANDARDIZED_SAT_TABLE,
  STANDARDIZED_ACT_TABLE,
  ACTIVITY_EXPERIENCE_TABLE,
  ENTERPRISE_INTERNSHIP_TABLE,
  RESEARCH_EXPERIENCE_TABLE,
  COMPETITION_RECORD_TABLE,
]);

const LEGACY_ENUM_VALUE_ALIASES = {
  student_language: {
    best_score_status_code: {
      PREDICTED: "ESTIMATED",
    },
  },
  student_language_test_record: {
    status_code: {
      PREDICTED: "ESTIMATED",
    },
  },
  [STANDARDIZED_SAT_TABLE]: {
    status_code: {
      PREDICTED: "ESTIMATED",
    },
  },
  [STANDARDIZED_ACT_TABLE]: {
    status_code: {
      PREDICTED: "ESTIMATED",
    },
  },
};

function normalizeLegacyEnumValue(tableName, fieldName, value) {
  if (value === null || value === undefined || value === "") {
    return value;
  }
  const alias =
    LEGACY_ENUM_VALUE_ALIASES?.[tableName]?.[fieldName]?.[String(value).toUpperCase()];
  return alias || value;
}

function normalizeArchiveFormEnumValues(archiveForm) {
  if (!archiveForm || typeof archiveForm !== "object") {
    return archiveForm || {};
  }

  const nextArchiveForm = deepClone(archiveForm);
  Object.entries(nextArchiveForm).forEach(([tableName, tableValue]) => {
    if (Array.isArray(tableValue)) {
      nextArchiveForm[tableName] = tableValue.map((row) => {
        if (!row || typeof row !== "object") {
          return row;
        }
        const normalizedRow = { ...row };
        Object.keys(normalizedRow).forEach((fieldName) => {
          normalizedRow[fieldName] = normalizeLegacyEnumValue(
            tableName,
            fieldName,
            normalizedRow[fieldName]
          );
        });
        return normalizedRow;
      });
      return;
    }

    if (tableValue && typeof tableValue === "object") {
      const normalizedRow = { ...tableValue };
      Object.keys(normalizedRow).forEach((fieldName) => {
        normalizedRow[fieldName] = normalizeLegacyEnumValue(
          tableName,
          fieldName,
          normalizedRow[fieldName]
        );
      });
      nextArchiveForm[tableName] = normalizedRow;
    }
  });

  return nextArchiveForm;
}

function normalizeArchiveBundle(data) {
  const radarScores = data?.radar_scores_json || {};
  const normalizeRadarDimension = (key, fallbackReason) => {
    const value = radarScores[key];
    if (typeof value === "number") {
      return { score: value, reason: fallbackReason };
    }
    if (value && typeof value === "object") {
      const score = Number(value.score ?? value.value ?? 0);
      return {
        score: Number.isFinite(score) ? Math.max(0, Math.min(100, score)) : 0,
        reason: value.reason || fallbackReason,
      };
    }
    return { score: 0, reason: fallbackReason };
  };
  const hasRadarResult = Boolean(
    data?.result_status ||
      data?.summary_text ||
      Object.keys(radarScores).length > 0
  );

  return {
    session_id: data?.session_id || "",
    archive_form: normalizeArchiveFormEnumValues(data?.archive_form || {}),
    form_meta: data?.form_meta || { table_order: [], tables: {} },
    result_status: data?.result_status || null,
    summary_text: data?.summary_text || "当前档案已保存，但还没有生成完整总结。",
    radar_scores_json: {
      academic: normalizeRadarDimension("academic", "暂无有效学术评分说明"),
      language: normalizeRadarDimension("language", "暂无有效语言评分说明"),
      standardized: normalizeRadarDimension("standardized", "暂无有效标化评分说明"),
      competition: normalizeRadarDimension("competition", "暂无有效竞赛评分说明"),
      activity: normalizeRadarDimension("activity", "暂无有效活动评分说明"),
      project: normalizeRadarDimension("project", "暂无有效项目评分说明"),
    },
    save_error_message: data?.save_error_message || "",
    create_time: data?.create_time || "",
    update_time: data?.update_time || "",
  };
}

function deepClone(value) {
  return JSON.parse(JSON.stringify(value));
}

function normalizeArchiveBundleForView(data) {
  const normalizedBundle = normalizeArchiveBundle(data);
  const radarScores = data?.radar_scores_json || {};
  const normalizedTableOrder = (Array.isArray(normalizedBundle?.form_meta?.table_order)
    ? normalizedBundle.form_meta.table_order
    : []
  ).filter(
    (tableName) =>
      !STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName) &&
      !LEGACY_STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName)
  );
  const mergedTableOrder = Array.from(
    new Set([
      ...normalizedTableOrder,
      ...EXPERIENCE_MAIN_TABLES,
    ])
  );
  const mergedArchiveForm = { ...(normalizedBundle.archive_form || {}) };
  EXPERIENCE_MAIN_TABLES.forEach((tableName) => {
    if (!Array.isArray(mergedArchiveForm[tableName])) {
      mergedArchiveForm[tableName] = [];
    }
  });
  STANDARDIZED_SELECTOR_TABLES.forEach((tableName) => {
    if (!Array.isArray(mergedArchiveForm[tableName])) {
      mergedArchiveForm[tableName] = [];
    }
  });
  const mergedFormMetaTables = {
    ...STANDARDIZED_FALLBACK_FORM_META_TABLES,
    ...EXPERIENCE_FALLBACK_FORM_META_TABLES,
    ...(normalizedBundle?.form_meta?.tables || {}),
  };

  return {
    ...normalizedBundle,
    archive_form: mergedArchiveForm,
    form_meta: {
      ...(normalizedBundle.form_meta || {}),
      table_order: mergedTableOrder,
      tables: mergedFormMetaTables,
    },
    has_radar_result: Boolean(
      data?.result_status ||
        data?.summary_text ||
        Object.keys(radarScores).length > 0
    ),
    summary_text: data?.summary_text || "",
  };
}

function getArchiveStatusDisplayText(resultStatus) {
  if (resultStatus === "saved") {
    return "档案创建完成";
  }
  if (resultStatus === "generated") {
    return "六维图已生成，档案待保存";
  }
  if (resultStatus === "failed") {
    return "档案保存失败";
  }
  return "六维图还未生成";
}

function getArchiveStatusText(resultStatus) {
  if (resultStatus === "saved") {
    return "档案创建完成";
  }
  if (resultStatus === "generated") {
    return "六维图已生成，档案待保存";
  }
  if (resultStatus === "failed") {
    return "档案保存失败";
  }
  return "暂无状态";
}

function buildFieldOptionLabelMap(formMeta) {
  const labelMap = {};
  Object.entries(formMeta?.tables || {}).forEach(([tableName, tableMeta]) => {
    labelMap[tableName] = {};
    (tableMeta?.fields || []).forEach((field) => {
      if (!Array.isArray(field.options) || field.options.length === 0) {
        return;
      }
      labelMap[tableName][field.name] = Object.fromEntries(
        filterFieldOptions(tableName, field.name, field.options).map((option) => [String(option.value), option.label])
      );
    });
  });
  return labelMap;
}

function formatOptionValue(optionMap, value) {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  return optionMap?.[String(value)] || String(value);
}

function getFieldOptions(formMeta, tableName, fieldName) {
  const field = formMeta?.tables?.[tableName]?.fields?.find((item) => item.name === fieldName);
  return filterFieldOptions(tableName, fieldName, Array.isArray(field?.options) ? field.options : []);
}

function filterFieldOptions(tableName, fieldName, options) {
  if (fieldName !== "curriculum_system_code") {
    return options;
  }
  return options.filter((option) => !REMOVED_CURRICULUM_SYSTEM_CODES.has(String(option.value)));
}

function findOptionLabel(options, value) {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  return options.find((option) => String(option.value) === String(value))?.label || String(value);
}

function buildDefaultTargetCountryRow({ studentId = "", index = 0, countryCode = "" } = {}) {
  return {
    student_id: studentId,
    country_code: countryCode || null,
    sort_order: index + 1,
    is_primary: index === 0 ? 1 : 0,
    source_flow: "manual_profile",
    source_session_id: null,
    remark: null,
    [TARGET_PREFERENCE_DRAFT_FIELD]: countryCode ? 0 : 1,
  };
}

function normalizeLanguageArchiveBundle(data) {
  return {
    archive_form: normalizeArchiveFormEnumValues(data?.archive_form || {}),
    form_meta: data?.form_meta || { table_order: [], tables: {} },
  };
}

function normalizeCurriculumArchiveBundle(data) {
  return {
    archive_form: normalizeArchiveFormEnumValues(data?.archive_form || {}),
    form_meta: data?.form_meta || { table_order: [], tables: {} },
  };
}

function mergeArchiveBundleWithSectionBundle(baseBundle, sectionBundle) {
  const baseTableOrder = Array.isArray(baseBundle?.form_meta?.table_order)
    ? baseBundle.form_meta.table_order
    : [];
  const sectionTableOrder = Array.isArray(sectionBundle?.form_meta?.table_order)
    ? sectionBundle.form_meta.table_order
    : [];
  const mergedTableOrder = Array.from(new Set([...baseTableOrder, ...sectionTableOrder]));

  return {
    ...(baseBundle || {}),
    archive_form: {
      ...(baseBundle?.archive_form || {}),
      ...(sectionBundle?.archive_form || {}),
    },
    form_meta: {
      ...(baseBundle?.form_meta || {}),
      ...(sectionBundle?.form_meta || {}),
      table_order: mergedTableOrder,
      tables: {
        ...(baseBundle?.form_meta?.tables || {}),
        ...(sectionBundle?.form_meta?.tables || {}),
      },
    },
  };
}

function pickCurriculumArchiveForm(archiveForm) {
  return Object.fromEntries(
    CURRICULUM_SECTION_TABLES.map((tableName) => [
      tableName,
      Array.isArray(archiveForm?.[tableName]) ? archiveForm[tableName] : [],
    ])
  );
}

function omitCurriculumArchiveForm(archiveForm) {
  if (!archiveForm || typeof archiveForm !== "object") {
    return {};
  }
  const nextArchiveForm = deepClone(archiveForm);
  CURRICULUM_SECTION_TABLES.forEach((tableName) => {
    delete nextArchiveForm[tableName];
  });
  return nextArchiveForm;
}

function pickLanguageArchiveForm(archiveForm) {
  return {
    [LANGUAGE_TEST_RECORD_TABLE]: Array.isArray(archiveForm?.[LANGUAGE_TEST_RECORD_TABLE])
      ? archiveForm[LANGUAGE_TEST_RECORD_TABLE]
      : [],
    [LANGUAGE_TEST_SCORE_ITEM_TABLE]: Array.isArray(archiveForm?.[LANGUAGE_TEST_SCORE_ITEM_TABLE])
      ? archiveForm[LANGUAGE_TEST_SCORE_ITEM_TABLE]
      : [],
  };
}

function omitLanguageArchiveForm(archiveForm) {
  if (!archiveForm || typeof archiveForm !== "object") {
    return {};
  }
  const nextArchiveForm = deepClone(archiveForm);
  delete nextArchiveForm[LANGUAGE_TEST_RECORD_TABLE];
  delete nextArchiveForm[LANGUAGE_TEST_SCORE_ITEM_TABLE];
  return nextArchiveForm;
}

function buildDefaultTargetMajorRow({ studentId = "", index = 0, majorCode = "", majorLabel = "" } = {}) {
  return {
    student_id: studentId,
    major_direction_code: majorCode || null,
    major_direction_label: majorLabel || null,
    major_code: majorCode || null,
    sort_order: index + 1,
    is_primary: index === 0 ? 1 : 0,
    source_flow: "manual_profile",
    source_session_id: null,
    remark: null,
    [TARGET_PREFERENCE_DRAFT_FIELD]: majorCode || majorLabel ? 0 : 1,
  };
}

function normalizeTargetPreferenceRows(
  archiveForm,
  { studentId = "", majorOptions = [], preserveDraftRows = false } = {}
) {
  const nextArchiveForm = deepClone(archiveForm || {});
  const basicInfo = {
    ...(nextArchiveForm.student_basic_info || {}),
    student_id: nextArchiveForm.student_basic_info?.student_id || studentId,
  };

  const rawCountryRows = Array.isArray(nextArchiveForm[TARGET_COUNTRY_TABLE]) ? [...nextArchiveForm[TARGET_COUNTRY_TABLE]] : [];
  if (rawCountryRows.length === 0 && basicInfo.CTRY_CODE_VAL) {
    rawCountryRows.push(
      buildDefaultTargetCountryRow({
        studentId: basicInfo.student_id || studentId,
        countryCode: basicInfo.CTRY_CODE_VAL,
      })
    );
  }

  const rawMajorRows = Array.isArray(nextArchiveForm[TARGET_MAJOR_TABLE]) ? [...nextArchiveForm[TARGET_MAJOR_TABLE]] : [];
  if (rawMajorRows.length === 0 && (basicInfo.MAJ_CODE_VAL || basicInfo.MAJ_INTEREST_TEXT)) {
    rawMajorRows.push(
      buildDefaultTargetMajorRow({
        studentId: basicInfo.student_id || studentId,
        majorCode: basicInfo.MAJ_CODE_VAL,
        majorLabel: basicInfo.MAJ_INTEREST_TEXT || findOptionLabel(majorOptions, basicInfo.MAJ_CODE_VAL),
      })
    );
  }

  const countryRows = rawCountryRows
    .filter(
      (row) =>
        row &&
        typeof row === "object" &&
        (row.country_code || (preserveDraftRows && row[TARGET_PREFERENCE_DRAFT_FIELD]))
    )
    .map((row, index) => ({
      ...buildDefaultTargetCountryRow({ studentId: basicInfo.student_id || studentId, index }),
      ...row,
      student_id: row.student_id || basicInfo.student_id || studentId,
      sort_order: index + 1,
      is_primary: index === 0 ? 1 : 0,
      source_flow: row.source_flow || "manual_profile",
      [TARGET_PREFERENCE_DRAFT_FIELD]: row.country_code ? 0 : 1,
    }));

  const majorRows = rawMajorRows
    .filter(
      (row) =>
        row &&
        typeof row === "object" &&
        (row.major_direction_code || row.major_code || (preserveDraftRows && row[TARGET_PREFERENCE_DRAFT_FIELD]))
    )
    .map((row, index) => {
      const majorCode = row.major_direction_code || row.major_code || "";
      return {
        ...buildDefaultTargetMajorRow({
          studentId: basicInfo.student_id || studentId,
          index,
          majorCode,
          majorLabel: row.major_direction_label || findOptionLabel(majorOptions, majorCode),
        }),
        ...row,
        student_id: row.student_id || basicInfo.student_id || studentId,
        major_direction_code: majorCode || null,
        major_direction_label: row.major_direction_label || findOptionLabel(majorOptions, majorCode) || majorCode || null,
        major_code: row.major_code || majorCode || null,
        sort_order: index + 1,
        is_primary: index === 0 ? 1 : 0,
        source_flow: row.source_flow || "manual_profile",
        [TARGET_PREFERENCE_DRAFT_FIELD]: majorCode ? 0 : 1,
      };
    });

  const primaryCountry = countryRows.find((row) => row?.country_code) || null;
  const primaryMajor =
    majorRows.find((row) => row?.major_code || row?.major_direction_code) || null;
  nextArchiveForm.student_basic_info = {
    ...basicInfo,
    CTRY_CODE_VAL: primaryCountry?.country_code || basicInfo.CTRY_CODE_VAL || null,
    MAJ_CODE_VAL: primaryMajor?.major_code || basicInfo.MAJ_CODE_VAL || null,
    MAJ_INTEREST_TEXT: primaryMajor?.major_direction_label || basicInfo.MAJ_INTEREST_TEXT || null,
  };
  nextArchiveForm[TARGET_COUNTRY_TABLE] = countryRows;
  nextArchiveForm[TARGET_MAJOR_TABLE] = majorRows;
  return nextArchiveForm;
}

function buildArchiveOverview(archiveForm, optionLabelMap) {
  const basicInfo = archiveForm?.student_basic_info || {};
  const academic = archiveForm?.student_academic || {};
  const language = archiveForm?.student_language || {};
  const languageTestRecords = Array.isArray(archiveForm?.[LANGUAGE_TEST_RECORD_TABLE])
    ? archiveForm[LANGUAGE_TEST_RECORD_TABLE]
    : [];
  const standardizedSatRows = Array.isArray(archiveForm?.[STANDARDIZED_SAT_TABLE])
    ? archiveForm[STANDARDIZED_SAT_TABLE]
    : [];
  const standardizedActRows = Array.isArray(archiveForm?.[STANDARDIZED_ACT_TABLE])
    ? archiveForm[STANDARDIZED_ACT_TABLE]
    : [];
  const curriculumCodeLabels = optionLabelMap?.student_basic_info_curriculum_system?.curriculum_system_code || {};
  const languageTypeLabels =
    optionLabelMap?.[LANGUAGE_TEST_RECORD_TABLE]?.test_type_code ||
    optionLabelMap?.student_language?.best_test_type_code ||
    {};
  const curriculumSystems = Array.isArray(archiveForm?.student_basic_info_curriculum_system)
    ? archiveForm.student_basic_info_curriculum_system
        .map((item) => item?.curriculum_system_code)
        .filter(Boolean)
        .map((item) => formatOptionValue(curriculumCodeLabels, item))
    : [];
  const bestLanguageRecord =
    languageTestRecords.find((item) => item?.is_best_score) ||
    languageTestRecords[0] ||
    null;
  const bestLanguageTestType = bestLanguageRecord?.test_type_code || language.best_test_type_code;
  const hasBestSat = standardizedSatRows.some((item) => item?.is_best_score);
  const hasBestAct = standardizedActRows.some((item) => item?.is_best_score);
  const bestStandardizedTestType = hasBestSat
    ? "SAT"
    : hasBestAct
      ? "ACT"
      : standardizedSatRows.length > 0
        ? "SAT"
        : standardizedActRows.length > 0
          ? "ACT"
          : "";

  return [
    { label: "当前年级", value: basicInfo.current_grade || "未填写" },
    { label: "目标入学季", value: basicInfo.target_entry_term || "未填写" },
    { label: "课程体系", value: curriculumSystems.join("、") || "未填写" },
    { label: "学校名称", value: academic.school_name || "未填写" },
    { label: "所在城市", value: academic.school_city || "未填写" },
    { label: "最佳语言考试", value: formatOptionValue(languageTypeLabels, bestLanguageTestType) || "未填写" },
    { label: "最佳考试成绩", value: bestStandardizedTestType || "未填写" },
    { label: "竞赛条数", value: String((archiveForm?.[COMPETITION_RECORD_TABLE] || []).length) },
    { label: "活动条数", value: String((archiveForm?.[ACTIVITY_EXPERIENCE_TABLE] || []).length) },
    { label: "实习条数", value: String((archiveForm?.[ENTERPRISE_INTERNSHIP_TABLE] || []).length) },
    { label: "科研条数", value: String((archiveForm?.[RESEARCH_EXPERIENCE_TABLE] || []).length) },
  ];
}

function hasMeaningfulValue(value) {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === "string") {
    return value.trim() !== "";
  }
  if (Array.isArray(value)) {
    return value.some((item) => hasMeaningfulValue(item));
  }
  if (typeof value === "object") {
    return Object.entries(value).some(
      ([key, item]) => !NON_CONTENT_FIELD_NAMES.has(key) && hasMeaningfulValue(item)
    );
  }
  return true;
}

function hasCurriculumTableData(archiveForm, tableName, curriculumCode) {
  const tableValue = archiveForm?.[tableName];
  if (!CURRICULUM_SCOPED_TABLES.has(tableName)) {
    return hasMeaningfulValue(tableValue);
  }
  if (!Array.isArray(tableValue)) {
    return false;
  }
  return tableValue.some(
    (row) => row?.curriculum_system_code === curriculumCode && hasMeaningfulValue(row)
  );
}

function getActiveCurriculumCodes(archiveForm) {
  const selectedCodes = new Set(
    (archiveForm?.student_basic_info_curriculum_system || [])
      .map((item) => item?.curriculum_system_code)
      .filter((item) => !REMOVED_CURRICULUM_SYSTEM_CODES.has(String(item)))
      .filter(Boolean)
  );
  if (selectedCodes.size > 0) {
    return Array.from(selectedCodes);
  }

  Object.entries(CURRICULUM_MODULES).forEach(([curriculumCode, module]) => {
    const hasModuleData = [...(module.curriculumTables || []), ...(module.standardizedTables || [])].some((tableName) =>
      hasCurriculumTableData(archiveForm, tableName, curriculumCode)
    );
    if (hasModuleData) {
      selectedCodes.add(curriculumCode);
    }
  });

  return Array.from(selectedCodes);
}

function getNextTemporaryNumericId(rows, fieldName) {
  return (
    rows.reduce((minimum, row) => {
      const numericValue = Number(row?.[fieldName]);
      if (Number.isFinite(numericValue) && numericValue < minimum) {
        return numericValue;
      }
      return minimum;
    }, 0) - 1
  );
}

function buildEmptyRow(
  fields,
  { tableName = "", studentId = "", existingRowsCount = 0, initialValues = {} } = {}
) {
  const row = {};
  fields.forEach((field) => {
    if (field.input_type === "checkbox") {
      row[field.name] = false;
      return;
    }
    row[field.name] = null;
  });
  if (studentId && ROW_TABLES_WITH_STUDENT_ID.has(tableName)) {
    row.student_id = studentId;
  }
  if (tableName === "student_basic_info_curriculum_system" && existingRowsCount === 0 && "is_primary" in row) {
    row.is_primary = true;
  }
  return {
    ...row,
    ...initialValues,
  };
}

function injectArchiveFormStudentIds(archiveForm, studentId) {
  if (!archiveForm || typeof archiveForm !== "object" || !studentId) {
    return archiveForm || {};
  }

  const nextArchiveForm = deepClone(archiveForm);
  Object.entries(nextArchiveForm).forEach(([tableName, tableValue]) => {
    if (!ROW_TABLES_WITH_STUDENT_ID.has(tableName) || !Array.isArray(tableValue)) {
      return;
    }
    nextArchiveForm[tableName] = tableValue.map((row) => {
      if (!row || typeof row !== "object") {
        return row;
      }
      return {
        ...row,
        student_id: studentId,
      };
    });
  });
  return nextArchiveForm;
}

function buildArchiveSectionKeys(detailTableNames, hasLanguageDetailTables) {
  const sectionKeys = [
    "student_basic_info",
    "curriculum_module",
    "student_academic",
  ];

  if (hasLanguageDetailTables) {
    sectionKeys.push("language_detail_module");
  }

  return [...sectionKeys, ...detailTableNames];
}

function shouldRenderRowField(tableName, row, field) {
  if (field.hidden) {
    return false;
  }
  return true;
}

function toInputValue(value, inputType) {
  if (inputType === "checkbox") {
    return Boolean(value);
  }
  if (value === null || value === undefined) {
    return "";
  }
  if (typeof value === "object") {
    return JSON.stringify(value, null, 2);
  }
  return String(value);
}

function normalizeChangedValue(rawValue, inputType) {
  if (inputType === "checkbox") {
    return rawValue ? 1 : 0;
  }
  if (rawValue === "") {
    return null;
  }
  if (inputType === "number") {
    const numericValue = Number(rawValue);
    return Number.isNaN(numericValue) ? null : numericValue;
  }
  return rawValue;
}

function sanitizeNumericInput(value, inputType) {
  const text = String(value ?? "");
  if (inputType === "integer") {
    return text.replace(/\D/g, "");
  }
  if (inputType === "number") {
    const numericText = text.replace(/[^\d.]/g, "");
    const [integerPart, ...decimalParts] = numericText.split(".");
    return decimalParts.length > 0 ? `${integerPart}.${decimalParts.join("")}` : integerPart;
  }
  return text;
}

function resolveTextInputType(inputType) {
  return inputType === "date" ? "date" : "text";
}

function resolveInputMode(inputType) {
  if (inputType === "integer") {
    return "numeric";
  }
  if (inputType === "number") {
    return "decimal";
  }
  return undefined;
}

function resolveInputPattern(inputType) {
  return inputType === "integer" ? "[0-9]*" : undefined;
}

function normalizeFieldChangedValue(tableName, fieldName, rawValue, inputType) {
  return normalizeLegacyEnumValue(
    tableName,
    fieldName,
    normalizeChangedValue(rawValue, inputType)
  );
}

function normalizeSearchText(value) {
  return String(value || "").trim().toLowerCase();
}

function isSearchableSelectField(tableName, fieldName) {
  return Boolean(SEARCHABLE_SELECT_FIELDS?.[tableName]?.has(fieldName));
}

function shouldUseMonthPickerField(tableName, fieldName) {
  return MONTH_RANGE_TABLE_NAME_SET.has(tableName) && (fieldName === "start_time" || fieldName === "end_time");
}

function toMonthInputValue(value) {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  const match = text.match(/^(\d{4})-(\d{2})/);
  return match ? `${match[1]}-${match[2]}` : "";
}

function parseMonthParts(value) {
  const monthValue = toMonthInputValue(value);
  if (!monthValue) {
    return { year: "", month: "" };
  }
  const [year = "", month = ""] = monthValue.split("-");
  return { year, month };
}

function buildDateFromMonthParts(year, month) {
  if (!year || !month) {
    return null;
  }
  return `${year}-${month}-01`;
}

function shouldForceVisibleField(tableName, fieldName) {
  if (tableName === "student_project_outputs" && fieldName === "project_id") {
    return true;
  }
  return PARENT_ROW_CONFIG_BY_CHILD_TABLE?.[tableName]?.parentField === fieldName;
}

function normalizeProfileFieldMeta(tableName, field) {
  const removedFieldNames = PROFILE_REMOVED_FIELD_NAMES_BY_TABLE?.[tableName];
  const labelOverrides = PROFILE_FIELD_LABEL_OVERRIDES_BY_TABLE?.[tableName];
  const nextLabel = labelOverrides?.[field.name] || field.label;
  const nextHidden = Boolean(field.hidden) || Boolean(removedFieldNames?.has(field.name));
  if (nextLabel === field.label && nextHidden === Boolean(field.hidden)) {
    return field;
  }
  return {
    ...field,
    label: nextLabel,
    hidden: nextHidden,
  };
}

function getVisibleProfileFields(tableName, fields, options = {}) {
  const { excludeBasicTargetFields = false } = options;
  return (fields || [])
    .map((field) => normalizeProfileFieldMeta(tableName, field))
    .filter(
      (field) =>
        (!field.hidden || shouldForceVisibleField(tableName, field.name)) &&
        !(excludeBasicTargetFields && BASIC_TARGET_FIELDS.has(field.name))
    );
}

function isNumericRelationField(tableName, fieldName) {
  if (tableName === "student_project_outputs" && fieldName === "project_id") {
    return true;
  }
  return PARENT_ROW_CONFIG_BY_CHILD_TABLE?.[tableName]?.parentField === fieldName;
}

function buildRenderableRowFieldMeta({ tableName, field, rowIndex, row, rows, archiveFormState }) {
  let normalizedField = field;

  if (shouldUseMonthPickerField(tableName, field.name)) {
    normalizedField = {
      ...field,
      input_type: "month_picker",
      hidden: false,
      helper_text: "仅记录到月份",
    };
  }

  if (tableName === "student_project_outputs" && field.name === "project_id") {
    normalizedField = {
      ...field,
      hidden: false,
      input_type: "select",
      options: (archiveFormState.student_project_entries || [])
        .filter((item) => item?.project_id !== null && item?.project_id !== undefined)
        .map((item) => ({
          value: String(item.project_id),
          label: item.project_name || `项目 ${item.project_id}`,
        })),
    };
  }

  const parentRowConfig = PARENT_ROW_CONFIG_BY_CHILD_TABLE[tableName];
  if (parentRowConfig && field.name === parentRowConfig.parentField) {
    normalizedField = {
      ...field,
      hidden: false,
      input_type: "select",
      options: (archiveFormState?.[parentRowConfig.parentTable] || [])
        .filter(
          (item) =>
            item?.[parentRowConfig.parentIdField] !== null &&
            item?.[parentRowConfig.parentIdField] !== undefined
        )
        .map((item) => ({
          value: String(item[parentRowConfig.parentIdField]),
          label: parentRowConfig.getLabel(item),
        })),
    };
  }

  const uniqueFieldName = UNIQUE_SUBJECT_SELECT_FIELD_BY_TABLE[tableName];
  if (normalizedField.input_type === "select" && uniqueFieldName === normalizedField.name) {
    const currentValue = row?.[normalizedField.name];
    const usedValues = new Set(
      (Array.isArray(rows) ? rows : [])
        .filter((_, index) => index !== rowIndex)
        .map((item) => item?.[normalizedField.name])
        .filter((item) => item !== null && item !== undefined && item !== "")
        .map((item) => String(item))
    );
    normalizedField = {
      ...normalizedField,
      options: (Array.isArray(normalizedField.options) ? normalizedField.options : []).filter((option) => {
        const optionValue = String(option.value);
        return String(currentValue) === optionValue || !usedValues.has(optionValue);
      }),
    };
  }

  return normalizedField;
}

function SearchableSelectControl({ options, value, onChange, placeholder = "请输入关键词搜索" }) {
  const containerRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [dropdownStyle, setDropdownStyle] = useState(null);
  const selectedOption = useMemo(
    () => (Array.isArray(options) ? options.find((option) => String(option.value) === String(value)) : null),
    [options, value]
  );
  const [query, setQuery] = useState(selectedOption?.label || "");

  useEffect(() => {
    if (!open) {
      setQuery(selectedOption?.label || "");
    }
  }, [selectedOption?.label, open]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    function updateDropdownPosition() {
      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) {
        return;
      }
      setDropdownStyle({
        position: "fixed",
        top: rect.bottom + 8,
        left: rect.left,
        width: rect.width,
      });
    }

    function handleOutsideClick(event) {
      if (!containerRef.current?.contains(event.target)) {
        setOpen(false);
        setQuery(selectedOption?.label || "");
      }
    }

    updateDropdownPosition();
    document.addEventListener("mousedown", handleOutsideClick);
    window.addEventListener("resize", updateDropdownPosition);
    window.addEventListener("scroll", updateDropdownPosition, true);
    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
      window.removeEventListener("resize", updateDropdownPosition);
      window.removeEventListener("scroll", updateDropdownPosition, true);
    };
  }, [open, selectedOption?.label]);

  const filteredOptions = useMemo(() => {
    if (!Array.isArray(options)) {
      return [];
    }
    const keyword = normalizeSearchText(query);
    const selectedKeyword = normalizeSearchText(selectedOption?.label || "");
    if (!keyword) {
      return options;
    }
    // 已选中某个值后重新展开下拉，应先展示完整候选列表，而不是只剩当前这一项。
    if (open && selectedKeyword && keyword === selectedKeyword) {
      return options;
    }
    return options.filter((option) => {
      const haystack = normalizeSearchText(`${option.label} ${option.value}`);
      return haystack.includes(keyword);
    });
  }, [open, options, query, selectedOption?.label]);

  return (
    <div className={`profile-search-select ${open ? "is-open" : ""}`} ref={containerRef}>
      <input
        className="profile-form-control profile-search-select-input"
        type="text"
        value={query}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          const nextQuery = event.target.value;
          setQuery(nextQuery);
          setOpen(true);
          if (!nextQuery.trim()) {
            onChange("");
          }
        }}
      />
      <button
        type="button"
        className="profile-search-select-trigger"
        aria-label="展开下拉选项"
        onClick={() => setOpen((previous) => !previous)}
      >
        <span className={`profile-search-select-arrow ${open ? "is-open" : ""}`}>⌄</span>
      </button>

      {open && dropdownStyle
        ? createPortal(
            <div className="profile-search-select-dropdown profile-search-select-dropdown-portal" style={dropdownStyle}>
              <button
                type="button"
                className={`profile-search-select-option ${!value ? "is-selected" : ""}`}
                onMouseDown={(event) => {
                  event.preventDefault();
                  onChange("");
                  setQuery("");
                  setOpen(false);
                }}
              >
                请选择
              </button>
              {filteredOptions.length > 0 ? (
                filteredOptions.map((option) => (
                  <button
                    key={`${option.value}`}
                    type="button"
                    className={`profile-search-select-option ${String(option.value) === String(value) ? "is-selected" : ""}`}
                    onMouseDown={(event) => {
                      event.preventDefault();
                      onChange(option.value);
                      setQuery(option.label);
                      setOpen(false);
                    }}
                  >
                    {option.label}
                  </button>
                ))
              ) : (
                <div className="profile-search-select-empty">没有匹配结果</div>
              )}
            </div>,
            document.body
          )
        : null}
    </div>
  );
}

function renderFieldControl({ tableName, field, value, onChange, row, onRowPatch }) {
  if (field.hidden) {
    return null;
  }

  const placeholder = FIELD_INPUT_PLACEHOLDERS?.[tableName]?.[field.name] || "";

  if (tableName === "student_academic" && field.name === "school_city") {
    return (
      <input
        className="profile-form-control"
        type="text"
        value={toInputValue(value, "text")}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    );
  }

  if (field.input_type === "checkbox") {
    return (
      <label className="profile-form-checkbox">
        <input type="checkbox" checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} />
        <span>{field.label}</span>
      </label>
    );
  }

  if (field.input_type === "select") {
    const options = filterFieldOptions(tableName, field.name, Array.isArray(field.options) ? field.options : []);
    const hasCurrentValue = value !== null && value !== undefined && value !== "";
    const hasMatchedCurrentValue = hasCurrentValue
      ? options.some((option) => String(option.value) === String(value))
      : true;
    const normalizedValue = normalizeLegacyEnumValue(tableName, field.name, value);
    const normalizedHasMatchedCurrentValue = hasCurrentValue
      ? options.some((option) => String(option.value) === String(normalizedValue))
      : true;
    const normalizedOptions = normalizedHasMatchedCurrentValue
      ? options
      : [{ value: normalizedValue, label: `当前值：${normalizedValue}` }, ...options];

    if (isSearchableSelectField(tableName, field.name)) {
      return (
        <SearchableSelectControl
          options={normalizedOptions}
          value={normalizedValue ?? ""}
          onChange={onChange}
          placeholder="请输入关键词搜索"
        />
      );
    }

    return (
      <select
        className="profile-form-control"
        value={normalizedValue ?? ""}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">请选择</option>
        {normalizedOptions.map((option) => (
          <option key={`${field.name}-${option.value}`} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    );
  }

  if (field.input_type === "textarea") {
    return (
      <textarea
        className="profile-form-control profile-form-textarea"
        value={toInputValue(value, field.input_type)}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    );
  }

  if (field.input_type === "month_picker") {
    const monthParts = parseMonthParts(value);

    function patchMonthValue(part, nextValue) {
      const nextParts = {
        ...monthParts,
        [part]: nextValue,
      };
      onRowPatch?.({
        [field.name]: buildDateFromMonthParts(nextParts.year, nextParts.month),
      });
    }

    return (
      <div className="profile-form-month-picker">
        <select
          className="profile-form-control profile-form-month-picker-select"
          value={monthParts.year}
          onChange={(event) => patchMonthValue("year", event.target.value)}
        >
          <option value="">选择年份</option>
          {MONTH_PICKER_YEAR_OPTIONS.map((option) => (
            <option key={`${field.name}-year-${option.value}`} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <select
          className="profile-form-control profile-form-month-picker-select"
          value={monthParts.month}
          onChange={(event) => patchMonthValue("month", event.target.value)}
        >
          <option value="">选择月份</option>
          {MONTH_PICKER_MONTH_OPTIONS.map((option) => (
            <option key={`${field.name}-month-${option.value}`} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
    );
  }

  return (
    <input
      className="profile-form-control"
      type={resolveTextInputType(field.input_type)}
      inputMode={resolveInputMode(field.input_type)}
      pattern={resolveInputPattern(field.input_type)}
      value={toInputValue(value, field.input_type)}
      placeholder={placeholder}
      onChange={(event) => onChange(sanitizeNumericInput(event.target.value, field.input_type))}
    />
  );
}

function renderFieldLabel(field) {
  if (field.input_type === "checkbox") {
    return <span className="profile-form-label-spacer" aria-hidden="true">占位</span>;
  }
  return <label>{field.label}</label>;
}

function getCleanArchiveStatusText(resultStatus) {
  if (resultStatus === "saved") {
    return "档案创建完成";
  }
  if (resultStatus === "generated") {
    return "六维图已生成，档案待保存";
  }
  if (resultStatus === "failed") {
    return "档案保存失败";
  }
  return "六维图还未生成";
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const archiveCollapseSeedSessionRef = useRef("");
  const activeTab = searchParams.get("tab") === "archive" ? "archive" : "account";
  const requestedArchiveSessionId = (searchParams.get("session_id") || "").trim();

  const [loading, setLoading] = useState(false);
  const [setPasswordLoading, setSetPasswordLoading] = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);
  const [updateNicknameLoading, setUpdateNicknameLoading] = useState(false);
  const [checkNicknameLoading, setCheckNicknameLoading] = useState(false);
  const [checkPasswordLoading, setCheckPasswordLoading] = useState(false);
  const [showNicknameEditor, setShowNicknameEditor] = useState(false);
  const [showMobileEditor, setShowMobileEditor] = useState(false);
  const [showPasswordEditor, setShowPasswordEditor] = useState(false);
  const [showForgotPasswordDialog, setShowForgotPasswordDialog] = useState(false);
  const [nicknameCheckMessage, setNicknameCheckMessage] = useState("");
  const [nicknameCheckAvailable, setNicknameCheckAvailable] = useState(false);
  const [passwordCheckMessage, setPasswordCheckMessage] = useState("");
  const [passwordCheckAvailable, setPasswordCheckAvailable] = useState(false);
  const [profile, setProfile] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [bindMobileForm, setBindMobileForm] = useState({ mobile: "" });
  const [bindMobileSaving, setBindMobileSaving] = useState(false);
  const [bindMobileMessage, setBindMobileMessage] = useState("");
  const [passwordForm, setPasswordForm] = useState({
    current_password: "",
    new_password: "",
    confirm_password: "",
  });
  const [nicknameForm, setNicknameForm] = useState({ nickname: "" });
  const [forgotPasswordForm, setForgotPasswordForm] = useState({
    mobile: "",
    code: "",
    new_password: "",
    confirm_password: "",
  });
  const [forgotPasswordSendingCode, setForgotPasswordSendingCode] = useState(false);
  const [forgotPasswordCheckLoading, setForgotPasswordCheckLoading] = useState(false);
  const [forgotPasswordCodeCountdown, setForgotPasswordCodeCountdown] = useState(0);
  const [forgotPasswordSaving, setForgotPasswordSaving] = useState(false);
  const [forgotPasswordMessage, setForgotPasswordMessage] = useState("");
  const [forgotPasswordError, setForgotPasswordError] = useState("");

  const [archiveLoading, setArchiveLoading] = useState(false);
  const [archiveSaving, setArchiveSaving] = useState(false);
  const [archiveRegenerating, setArchiveRegenerating] = useState(false);
  const [archiveDraftSyncing, setArchiveDraftSyncing] = useState(false);
  const [archiveSessionId, setArchiveSessionId] = useState("");
  const [archiveBundle, setArchiveBundle] = useState(null);
  const [archiveFormState, setArchiveFormState] = useState({});
  const [archiveMessage, setArchiveMessage] = useState("");
  const [archiveSaveWarning, setArchiveSaveWarning] = useState("");
  const [archiveErrorMessage, setArchiveErrorMessage] = useState("");
  const [collapsedArchiveSections, setCollapsedArchiveSections] = useState({});
  const [selectedLanguageTestTypeCode, setSelectedLanguageTestTypeCode] = useState("");
  const archiveStudentId =
    archiveFormState?.student_basic_info?.student_id ||
    archiveBundle?.archive_form?.student_basic_info?.student_id ||
    profile?.user_id ||
    "";

  const savedArchiveSnapshot = useMemo(() => JSON.stringify(archiveBundle?.archive_form || {}), [archiveBundle]);
  const currentArchiveSnapshot = useMemo(() => JSON.stringify(archiveFormState || {}), [archiveFormState]);
  const isArchiveDirty = Boolean(archiveBundle) && savedArchiveSnapshot !== currentArchiveSnapshot;
  const fieldOptionLabelMap = useMemo(
    () => buildFieldOptionLabelMap(archiveBundle?.form_meta),
    [archiveBundle?.form_meta]
  );
  const radarChartData = useMemo(
    () =>
      Object.entries(archiveBundle?.radar_scores_json || {}).map(([key, value]) => ({
        subject: RADAR_LABELS[key] || key,
        score: value.score || 0,
      })),
    [archiveBundle?.radar_scores_json]
  );
  const activeCurriculumCodes = useMemo(
    () => getActiveCurriculumCodes(archiveFormState),
    [archiveFormState]
  );
  const hasLanguageSection = useMemo(
    () => Boolean(archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_RECORD_TABLE]),
    [archiveBundle?.form_meta?.tables]
  );
  const languageTestTypeOptions = useMemo(
    () => getFieldOptions(archiveBundle?.form_meta, LANGUAGE_TEST_RECORD_TABLE, "test_type_code"),
    [archiveBundle?.form_meta]
  );
  const detailTableNames = useMemo(
    () =>
      (archiveBundle?.form_meta?.table_order || []).filter(
        (tableName) =>
          tableName !== "student_basic_info" &&
          tableName !== "student_basic_info_curriculum_system" &&
          tableName !== "student_academic" &&
          tableName !== "student_language" &&
          !LANGUAGE_SECTION_TABLE_NAME_SET.has(tableName) &&
          !STANDARDIZED_SECTION_TABLE_NAME_SET.has(tableName) &&
          !STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName) &&
          !CURRICULUM_SECTION_TABLE_NAME_SET.has(tableName) &&
          !EXPERIENCE_ATTACHMENT_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_CURRICULUM_DETAIL_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_LANGUAGE_DETAIL_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_STANDARDIZED_DETAIL_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_EXPERIENCE_DETAIL_TABLE_NAME_SET.has(tableName)
      ),
    [archiveBundle?.form_meta?.table_order]
  );
  const standardizedSelectorOptions = useMemo(
    () =>
      STANDARDIZED_SELECTOR_CONFIG.map(({ tableName, label }) => ({
        value: tableName,
        label: archiveBundle?.form_meta?.tables?.[tableName]?.label || label,
      })),
    [archiveBundle?.form_meta?.tables]
  );
  const experienceDetailTableNames = useMemo(
    () => EXPERIENCE_MAIN_TABLES.filter((tableName) => detailTableNames.includes(tableName)),
    [detailTableNames]
  );
  const otherDetailTableNames = useMemo(
    () =>
      detailTableNames.filter(
        (tableName) =>
          !EXPERIENCE_MAIN_TABLE_NAME_SET.has(tableName) &&
          !STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName) &&
          !LEGACY_STANDARDIZED_SELECTOR_TABLE_NAME_SET.has(tableName)
      ),
    [detailTableNames]
  );
  const archiveSectionKeys = useMemo(
    () => buildArchiveSectionKeys(detailTableNames, hasLanguageSection),
    [detailTableNames, hasLanguageSection]
  );
  const profileBusyOverlay = useMemo(() => {
    if (logoutLoading) {
      return { message: "正在退出登录", submessage: "请稍候，正在安全退出当前账号" };
    }
    if (bindMobileSaving) {
      return { message: "正在绑定手机号", submessage: "请稍候，正在保存当前手机号信息" };
    }
    if (updateNicknameLoading) {
      return { message: "正在保存昵称", submessage: "请稍候，正在更新当前昵称" };
    }
    if (checkNicknameLoading) {
      return { message: "正在检查昵称", submessage: "请稍候，正在确认昵称是否可用" };
    }
    if (checkPasswordLoading || forgotPasswordCheckLoading) {
      return { message: "正在检查密码", submessage: "请稍候，正在校验当前密码规则" };
    }
    if (setPasswordLoading || forgotPasswordSaving) {
      return { message: "正在保存密码", submessage: "请稍候，正在更新你的登录密码" };
    }
    if (forgotPasswordSendingCode) {
      return { message: "正在发送验证码", submessage: "请稍候，验证码正在发送到你的手机" };
    }
    if (archiveSaving) {
      return { message: "正在保存档案", submessage: "请稍候，正在同步最新档案内容" };
    }
    if (archiveRegenerating) {
      return { message: "正在生成六维图", submessage: "请稍候，系统正在刷新当前六维评分" };
    }
    if (archiveDraftSyncing) {
      return { message: "正在同步档案", submessage: "请稍候，系统正在同步当前正式档案" };
    }
    return null;
  }, [
    archiveDraftSyncing,
    archiveRegenerating,
    archiveSaving,
    bindMobileSaving,
    checkNicknameLoading,
    checkPasswordLoading,
    forgotPasswordCheckLoading,
    forgotPasswordSaving,
    forgotPasswordSendingCode,
    logoutLoading,
    setPasswordLoading,
    updateNicknameLoading,
  ]);

  useEffect(() => {
    void fetchProfile();
  }, []);

  useEffect(() => {
    if (activeTab === "archive") {
      void fetchArchive();
    }
  }, [activeTab, requestedArchiveSessionId]);

  useEffect(() => {
    setForgotPasswordForm((previous) => ({
      ...previous,
      mobile: profile?.mobile || "",
    }));
  }, [profile?.mobile]);

  useEffect(() => {
    if (forgotPasswordCodeCountdown <= 0) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setForgotPasswordCodeCountdown((previous) => Math.max(previous - 1, 0));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [forgotPasswordCodeCountdown]);

  useEffect(() => {
    if (!archiveMessage) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setArchiveMessage("");
    }, 3000);
    return () => window.clearTimeout(timer);
  }, [archiveMessage]);

  useEffect(() => {
    if (!archiveSaveWarning) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setArchiveSaveWarning("");
    }, 2500);
    return () => window.clearTimeout(timer);
  }, [archiveSaveWarning]);

  useEffect(() => {
    if (!archiveBundle || archiveSectionKeys.length === 0) {
      return;
    }

    setCollapsedArchiveSections((previous) => {
      const isNewSession =
        Boolean(archiveSessionId) && archiveCollapseSeedSessionRef.current !== archiveSessionId;
      const nextState = {};

      archiveSectionKeys.forEach((sectionKey) => {
        nextState[sectionKey] = isNewSession ? true : previous[sectionKey] ?? true;
      });

      archiveCollapseSeedSessionRef.current = archiveSessionId;
      return nextState;
    });
  }, [archiveBundle, archiveSessionId, archiveSectionKeys]);

  useEffect(() => {
    if (languageTestTypeOptions.length === 0) {
      setSelectedLanguageTestTypeCode("");
      return;
    }

    setSelectedLanguageTestTypeCode((previous) => {
      if (
        previous &&
        languageTestTypeOptions.some((option) => String(option.value) === String(previous))
      ) {
        return previous;
      }
      const firstTypeWithData = (Array.isArray(archiveFormState?.[LANGUAGE_TEST_RECORD_TABLE])
        ? archiveFormState[LANGUAGE_TEST_RECORD_TABLE]
        : []
      )
        .map((row) => row?.test_type_code)
        .find(Boolean);
      return firstTypeWithData || languageTestTypeOptions[0]?.value || "";
    });
  }, [archiveFormState, languageTestTypeOptions]);

  function notify(message) {
    window.alert(message);
  }

  function switchTab(nextTab) {
    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.set("tab", nextTab);
    if (nextTab === "archive" && archiveSessionId) {
      nextSearchParams.set("session_id", archiveSessionId);
    }
    setSearchParams(nextSearchParams);
  }

  function getDisplayInitial(nickname, mobile) {
    const source = (nickname || mobile || "U").trim();
    return source.charAt(0).toUpperCase();
  }

  function resetNicknameCheckResult() {
    setNicknameCheckMessage("");
    setNicknameCheckAvailable(false);
  }

  function resetPasswordCheckResult() {
    setPasswordCheckMessage("");
    setPasswordCheckAvailable(false);
  }

  function validatePassword(value) {
    return validatePasswordRule(value);
  }

  async function fetchProfile() {
    setErrorMessage("");
    try {
      setLoading(true);
      const response = await getMe();
      setProfile(response.data);
      setNicknameForm({ nickname: response.data.nickname || "" });
      setBindMobileForm({ mobile: response.data.mobile || "" });
      setBindMobileMessage("");
      setShowMobileEditor(!response.data.mobile);
    } catch (error) {
      const detail = error?.response?.data?.detail || "获取用户信息失败";
      setErrorMessage(detail);
      if (error?.response?.status === 401) {
        clearAccessToken();
        navigate("/login", { replace: true });
      }
    } finally {
      setLoading(false);
    }
  }

  async function fetchArchive() {
    setArchiveMessage("");
    setArchiveErrorMessage("");

    try {
      setArchiveLoading(true);
      let targetSessionId = requestedArchiveSessionId;

      if (!targetSessionId) {
        try {
          const currentResponse = await getStudentProfileCurrentSession({
            createIfMissing: true,
          });
          targetSessionId = currentResponse.data?.session?.session_id || "";
        } catch (error) {
          console.info("当前没有可恢复的 AI 建档会话", error);
        }
      }

      if (!targetSessionId) {
        targetSessionId = localStorage.getItem(AI_CHAT_SESSION_CACHE_KEY) || "";
      }

      if (!targetSessionId) {
        throw new Error("未能创建或获取当前档案会话");
      }

      const archiveResponse = await getStudentProfileArchive(targetSessionId);
      const normalizedBundle = normalizeArchiveBundleForView(archiveResponse.data);
      const normalizedArchiveForm = injectArchiveFormStudentIds(
        normalizedBundle.archive_form,
        normalizedBundle.archive_form?.student_basic_info?.student_id || profile?.user_id || ""
      );

      setArchiveSessionId(targetSessionId);
      setArchiveBundle({
        ...normalizedBundle,
        archive_form: normalizedArchiveForm,
      });
      setArchiveFormState(normalizedArchiveForm);
      setArchiveMessage("");
      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, targetSessionId);

      if (requestedArchiveSessionId !== targetSessionId) {
        const nextSearchParams = new URLSearchParams(searchParams);
        nextSearchParams.set("tab", "archive");
        nextSearchParams.set("session_id", targetSessionId);
        setSearchParams(nextSearchParams, { replace: true });
      }
      return;
    } catch (error) {
      setArchiveBundle(null);
      setArchiveSessionId("");
      setArchiveFormState({});
      if (error?.response?.status === 404) {
        setArchiveBundle(null);
        setArchiveSessionId("");
        setArchiveFormState({});
        setArchiveErrorMessage("当前还没有可查看的档案结果，请先回首页生成六维图并完成建档。");
      } else {
        setArchiveErrorMessage(error?.response?.data?.detail || error?.message || "档案加载失败，请稍后重试。");
      }
    } finally {
      setArchiveLoading(false);
    }
  }

  function updateSingleField(tableName, fieldName, nextValue) {
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => ({
      ...previous,
      [tableName]: {
        ...(previous[tableName] || {}),
        [fieldName]: nextValue,
      },
    }));
  }

  function updateRowField(tableName, rowIndex, fieldName, nextValue) {
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const nextRows = Array.isArray(previous[tableName]) ? [...previous[tableName]] : [];
      nextRows[rowIndex] = {
        ...(nextRows[rowIndex] || {}),
        [fieldName]: nextValue,
      };
      if (ROW_TABLES_WITH_STUDENT_ID.has(tableName)) {
        nextRows[rowIndex].student_id = previous?.student_basic_info?.student_id || archiveStudentId || "";
      }
      return {
        ...previous,
        [tableName]: nextRows,
      };
    });
  }

  function updateRowFields(tableName, rowIndex, nextValues) {
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const nextRows = Array.isArray(previous[tableName]) ? [...previous[tableName]] : [];
      nextRows[rowIndex] = {
        ...(nextRows[rowIndex] || {}),
        ...(nextValues || {}),
      };
      if (ROW_TABLES_WITH_STUDENT_ID.has(tableName)) {
        nextRows[rowIndex].student_id = previous?.student_basic_info?.student_id || archiveStudentId || "";
      }
      return {
        ...previous,
        [tableName]: nextRows,
      };
    });
  }

  function handleAddRow(tableName, options = {}) {
    const { preserveVirtualRowWhenEmpty = false } = options;
    const fields = archiveBundle?.form_meta?.tables?.[tableName]?.fields || [];
    const existingRows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const currentRows = Array.isArray(previous[tableName]) ? [...previous[tableName]] : [];
      const rowsToAppend =
        preserveVirtualRowWhenEmpty && currentRows.length === 0 ? 2 : 1;
      const temporaryIdField = TEMP_NUMERIC_ID_FIELD_BY_TABLE[tableName];

      for (let index = 0; index < rowsToAppend; index += 1) {
        const nextRowsSnapshot = [...currentRows];
        const initialValues = temporaryIdField
          ? {
              [temporaryIdField]: getNextTemporaryNumericId(nextRowsSnapshot, temporaryIdField),
            }
          : {};
        currentRows.push(
          buildEmptyRow(fields, {
            tableName,
            studentId: previous?.student_basic_info?.student_id || archiveStudentId,
            existingRowsCount: currentRows.length,
            initialValues,
          })
        );
      }

      return {
        ...previous,
        [tableName]: currentRows,
      };
    });
  }

  function handleAddCurriculumScopedRow(tableName, curriculumCode, options = {}) {
    const { preserveVirtualRowWhenEmpty = false } = options;
    const fields = archiveBundle?.form_meta?.tables?.[tableName]?.fields || [];
    const existingRows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    const scopedRowCount = existingRows.filter(
      (row) => row?.curriculum_system_code === curriculumCode
    ).length;

    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const currentRows = Array.isArray(previous[tableName]) ? [...previous[tableName]] : [];
      const rowsToAppend =
        preserveVirtualRowWhenEmpty && scopedRowCount === 0 ? 2 : 1;

      for (let index = 0; index < rowsToAppend; index += 1) {
        currentRows.push(
          buildEmptyRow(fields, {
            tableName,
            studentId: previous?.student_basic_info?.student_id || archiveStudentId,
            existingRowsCount: scopedRowCount + index,
            initialValues: {
              curriculum_system_code: curriculumCode,
            },
          })
        );
      }

      return {
        ...previous,
        [tableName]: currentRows,
      };
    });
  }

  function handleAddLanguageTestRecordRow(testTypeCode = "", options = {}) {
    const { preserveVirtualRowWhenEmpty = false } = options;
    const fields = archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_RECORD_TABLE]?.fields || [];
    const existingRows = Array.isArray(archiveFormState?.[LANGUAGE_TEST_RECORD_TABLE])
      ? archiveFormState[LANGUAGE_TEST_RECORD_TABLE]
      : [];
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const currentRows = Array.isArray(previous[LANGUAGE_TEST_RECORD_TABLE])
        ? [...previous[LANGUAGE_TEST_RECORD_TABLE]]
        : [];
      const currentTypeRows = currentRows.filter(
        (row) => String(row?.test_type_code || "") === String(testTypeCode || "")
      );
      const rowsToAppend =
        preserveVirtualRowWhenEmpty && currentTypeRows.length === 0 ? 2 : 1;

      for (let index = 0; index < rowsToAppend; index += 1) {
        const nextRowsSnapshot = [...currentRows];
        const temporaryRecordId = getNextTemporaryNumericId(
          nextRowsSnapshot,
          "student_language_test_record_id"
        );

        currentRows.push(
          buildEmptyRow(fields, {
            tableName: LANGUAGE_TEST_RECORD_TABLE,
            studentId: previous?.student_basic_info?.student_id || archiveStudentId,
            existingRowsCount: currentRows.length,
            initialValues: {
              student_language_test_record_id: temporaryRecordId,
              test_type_code: testTypeCode || null,
            },
          })
        );
      }

      return {
        ...previous,
        [LANGUAGE_TEST_RECORD_TABLE]: currentRows,
      };
    });
  }

  function handleAddLanguageScoreItemRow(languageTestRecordId, options = {}) {
    const { preserveVirtualRowWhenEmpty = false } = options;
    const fields = archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_SCORE_ITEM_TABLE]?.fields || [];
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const currentRows = Array.isArray(previous[LANGUAGE_TEST_SCORE_ITEM_TABLE])
        ? [...previous[LANGUAGE_TEST_SCORE_ITEM_TABLE]]
        : [];
      const currentRecordRows = currentRows.filter(
        (row) =>
          String(row?.student_language_test_record_id ?? "") ===
          String(languageTestRecordId ?? "")
      );
      const rowsToAppend =
        preserveVirtualRowWhenEmpty && currentRecordRows.length === 0 ? 2 : 1;

      for (let index = 0; index < rowsToAppend; index += 1) {
        currentRows.push(
          buildEmptyRow(fields, {
            tableName: LANGUAGE_TEST_SCORE_ITEM_TABLE,
            initialValues: {
              student_language_test_record_id: languageTestRecordId,
            },
          })
        );
      }

      return {
        ...previous,
        [LANGUAGE_TEST_SCORE_ITEM_TABLE]: currentRows,
      };
    });
  }

  function handleUpsertLanguageScoreItemField({
    recordRow,
    recordRowIndex,
    recordId,
    isVirtualRecordRow = false,
    scoreRow,
    scoreRowIndex,
    isVirtualScoreRow = false,
    fieldName,
    rawValue,
    inputType,
  }) {
    const fields = archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_SCORE_ITEM_TABLE]?.fields || [];
    const normalizedValue = normalizeChangedValue(rawValue, inputType);

    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const nextArchiveForm = {
        ...previous,
      };

      if (isVirtualRecordRow) {
        const nextRecordRows = Array.isArray(previous[LANGUAGE_TEST_RECORD_TABLE])
          ? [...previous[LANGUAGE_TEST_RECORD_TABLE]]
          : [];
        nextRecordRows[recordRowIndex] = {
          ...(nextRecordRows[recordRowIndex] || {}),
          ...(recordRow || {}),
          student_id: previous?.student_basic_info?.student_id || archiveStudentId,
        };
        nextArchiveForm[LANGUAGE_TEST_RECORD_TABLE] = nextRecordRows;
      }

      const nextScoreRows = Array.isArray(previous[LANGUAGE_TEST_SCORE_ITEM_TABLE])
        ? [...previous[LANGUAGE_TEST_SCORE_ITEM_TABLE]]
        : [];
      nextScoreRows[scoreRowIndex] = {
        ...(isVirtualScoreRow
          ? buildEmptyRow(fields, {
              tableName: LANGUAGE_TEST_SCORE_ITEM_TABLE,
              initialValues: {
                student_language_test_record_id: recordId,
              },
            })
          : {}),
        ...(nextScoreRows[scoreRowIndex] || {}),
        ...(scoreRow || {}),
        student_language_test_record_id: recordId,
        [fieldName]: normalizedValue,
      };
      nextArchiveForm[LANGUAGE_TEST_SCORE_ITEM_TABLE] = nextScoreRows;

      return nextArchiveForm;
    });
  }

  function handleRemoveRow(tableName, rowIndex) {
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const currentRows = Array.isArray(previous[tableName]) ? previous[tableName] : [];
      const nextRows = currentRows.filter((_, index) => index !== rowIndex);
      const relationConfig = CHILD_TABLE_CONFIG_BY_PARENT_TABLE[tableName];
      if (!relationConfig) {
        return {
          ...previous,
          [tableName]: nextRows,
        };
      }

      const removedParentId = currentRows[rowIndex]?.[relationConfig.parentIdField];
      const nextChildRows = (Array.isArray(previous[relationConfig.childTable])
        ? previous[relationConfig.childTable]
        : []
      ).filter(
        (row) =>
          String(row?.[relationConfig.parentField] ?? "") !==
          String(removedParentId ?? "")
      );

      return {
        ...previous,
        [tableName]: nextRows,
        ...(relationConfig.childTable
          ? {
              [relationConfig.childTable]: nextChildRows,
            }
          : {}),
      };
    });
  }

  function handleResetArchiveForm() {
    if (!archiveBundle) {
      return;
    }
    setArchiveFormState(
      injectArchiveFormStudentIds(
        archiveBundle.archive_form,
        archiveBundle.archive_form?.student_basic_info?.student_id || profile?.user_id || ""
      )
    );
    setArchiveMessage("已恢复为数据库中当前保存的档案内容。");
    setArchiveSaveWarning("");
    setArchiveErrorMessage("");
  }

  async function syncArchiveDraftAfterSave({ suppressError = false } = {}) {
    if (!archiveSessionId) {
      return;
    }

    try {
      setArchiveDraftSyncing(true);
      await syncStudentProfileArchiveDraft(archiveSessionId);
    } catch (error) {
      if (!suppressError) {
        setArchiveErrorMessage(
          error?.response?.data?.detail || "档案已保存，但同步建档上下文失败，请稍后重试。"
        );
      }
    } finally {
      setArchiveDraftSyncing(false);
    }
  }

  async function saveArchiveFormSnapshot({ silent = false, waitForDraftSync = true } = {}) {
    if (!archiveSessionId) {
      throw new Error("当前缺少会话信息，暂时无法保存档案。");
    }

    const normalizedArchiveFormState = normalizeArchiveFormEnumValues(archiveFormState);
    const preparedArchiveForm = injectArchiveFormStudentIds(
      normalizeTargetPreferenceRows(normalizedArchiveFormState, {
        studentId: archiveStudentId,
        majorOptions: getFieldOptions(archiveBundle?.form_meta, "student_basic_info", "MAJ_CODE_VAL"),
      }),
      archiveStudentId
    );
    const archiveResponse = await saveStudentProfileArchive(archiveSessionId, preparedArchiveForm);
    const normalizedBundle = normalizeArchiveBundleForView(archiveResponse.data);
    const normalizedArchiveForm = injectArchiveFormStudentIds(
      normalizedBundle.archive_form,
      normalizedBundle.archive_form?.student_basic_info?.student_id || profile?.user_id || ""
    );

    setArchiveBundle({
      ...normalizedBundle,
      archive_form: normalizedArchiveForm,
    });
    setArchiveFormState(normalizedArchiveForm);
    localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, archiveSessionId);

    if (waitForDraftSync) {
      await syncArchiveDraftAfterSave();
    } else {
      void syncArchiveDraftAfterSave({ suppressError: true });
    }

    if (!silent) {
      setArchiveMessage("保存成功");
    }
    return normalizedBundle;
  }

  async function handleSaveArchiveForm() {
    if (!archiveSessionId) {
      setArchiveErrorMessage("当前缺少会话信息，暂时无法保存档案。");
      return;
    }

    try {
      setArchiveSaving(true);
      setArchiveMessage("");
      setArchiveSaveWarning("");
      setArchiveErrorMessage("");

      await saveArchiveFormSnapshot({ silent: true, waitForDraftSync: false });
      setArchiveMessage("保存成功");
      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, archiveSessionId);
    } catch (error) {
      setArchiveErrorMessage(error?.response?.data?.detail || "档案保存失败，请稍后重试。");
    } finally {
      setArchiveSaving(false);
    }
  }

  function toggleArchiveSection(sectionKey) {
    setCollapsedArchiveSections((previous) => ({
      ...previous,
      [sectionKey]: !(previous[sectionKey] ?? true),
    }));
  }

  function isArchiveSectionCollapsed(sectionKey) {
    return collapsedArchiveSections[sectionKey] ?? true;
  }


  async function handleRegenerateArchiveRadar() {
    if (!archiveSessionId) {
      setArchiveErrorMessage("当前缺少会话信息，暂时无法重新生成六维图。");
      return;
    }

    if (isArchiveDirty) {
      setArchiveSaveWarning("请先保存");
      setArchiveMessage("");
      return;
    }

    try {
      setArchiveRegenerating(true);
      setArchiveMessage("");
      setArchiveSaveWarning("");
      setArchiveErrorMessage("");

      const response = await regenerateStudentProfileArchiveRadar(archiveSessionId);
      const normalizedBundle = normalizeArchiveBundleForView(response.data);
      const normalizedArchiveForm = injectArchiveFormStudentIds(
        normalizedBundle.archive_form,
        normalizedBundle.archive_form?.student_basic_info?.student_id || profile?.user_id || ""
      );

      setArchiveBundle({
        ...normalizedBundle,
        archive_form: normalizedArchiveForm,
      });
      setArchiveFormState(normalizedArchiveForm);
      setArchiveMessage("");
    } catch (error) {
      setArchiveErrorMessage(
        error?.response?.data?.detail || error?.message || "六维图重新生成失败，请稍后重试。"
      );
    } finally {
      setArchiveRegenerating(false);
    }
  }

  async function handleContinueSupplementConversation() {
    try {
      setArchiveMessage("");
      setArchiveErrorMessage("");

      if (archiveSessionId && isArchiveDirty) {
        setArchiveSaving(true);
        await saveArchiveFormSnapshot({ silent: true, waitForDraftSync: false });
      }

      localStorage.setItem(GUIDED_PROFILE_OPEN_PANEL_KEY, "1");
      navigate("/");
    } catch (error) {
      setArchiveErrorMessage(error?.response?.data?.detail || error?.message || "返回快速建档前保存档案失败，请稍后重试。");
    } finally {
      setArchiveSaving(false);
    }
  }

  async function handleCheckNickname() {
    resetNicknameCheckResult();
    if (!nicknameForm.nickname.trim()) {
      setNicknameCheckMessage("请输入昵称");
      return;
    }
    try {
      setCheckNicknameLoading(true);
      const response = await checkMyNickname({ nickname: nicknameForm.nickname.trim() });
      setNicknameCheckMessage(response.data.message);
      setNicknameCheckAvailable(Boolean(response.data.available));
    } catch (error) {
      setNicknameCheckMessage(error?.response?.data?.detail || "昵称检查失败");
      setNicknameCheckAvailable(false);
    } finally {
      setCheckNicknameLoading(false);
    }
  }

  async function handleUpdateNickname() {
    setErrorMessage("");
    if (!nicknameForm.nickname.trim()) {
      setErrorMessage("请输入昵称");
      return;
    }
    try {
      setUpdateNicknameLoading(true);
      await updateMyNickname({ nickname: nicknameForm.nickname.trim() });
      notify("昵称修改成功");
      setShowNicknameEditor(false);
      await fetchProfile();
      resetNicknameCheckResult();
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "昵称修改失败");
    } finally {
      setUpdateNicknameLoading(false);
    }
  }

  async function handleBindMyMobile() {
    setErrorMessage("");
    setBindMobileMessage("");

    const normalizedMobile = bindMobileForm.mobile.trim();
    if (!/^1\d{10}$/.test(normalizedMobile)) {
      setErrorMessage("请输入正确的手机号");
      return;
    }

    try {
      setBindMobileSaving(true);
      const response = await bindMyMobile({ mobile: normalizedMobile });
      const nextMessage = "手机号保存成功";
      await fetchProfile();
      setBindMobileMessage(nextMessage);
      setShowMobileEditor(false);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "手机号绑定失败");
    } finally {
      setBindMobileSaving(false);
    }
  }

  async function handleCheckPassword() {
    resetPasswordCheckResult();
    if (!passwordForm.new_password) {
      setPasswordCheckMessage("请输入新密码");
      return;
    }
    const localValidationMessage = validatePassword(passwordForm.new_password);
    if (localValidationMessage) {
      setPasswordCheckMessage(localValidationMessage);
      return;
    }
    try {
      setCheckPasswordLoading(true);
      const response = await checkMyPassword({
        current_password: passwordForm.current_password || undefined,
        new_password: passwordForm.new_password,
      });
      setPasswordCheckMessage(response.data.message);
      setPasswordCheckAvailable(Boolean(response.data.available));
    } catch (error) {
      setPasswordCheckMessage(error?.response?.data?.detail || "密码检查失败");
      setPasswordCheckAvailable(false);
    } finally {
      setCheckPasswordLoading(false);
    }
  }

  async function handleSetPassword() {
    setErrorMessage("");
    if (profile?.has_password && !passwordForm.current_password) {
      setErrorMessage("请输入当前密码");
      return;
    }
    if (!passwordForm.new_password) {
      setErrorMessage("请输入新密码");
      return;
    }
    if (passwordForm.new_password !== passwordForm.confirm_password) {
      setErrorMessage("两次输入的密码不一致");
      return;
    }
    const localValidationMessage = validatePassword(passwordForm.new_password);
    if (localValidationMessage) {
      setErrorMessage(localValidationMessage);
      return;
    }
    try {
      setSetPasswordLoading(true);
      await setPassword({
        current_password: passwordForm.current_password || undefined,
        new_password: passwordForm.new_password,
      });
      notify(profile?.has_password ? "密码修改成功" : "密码设置成功");
      setShowPasswordEditor(false);
      setPasswordForm({
        current_password: "",
        new_password: "",
        confirm_password: "",
      });
      resetPasswordCheckResult();
      await fetchProfile();
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "密码保存失败");
    } finally {
      setSetPasswordLoading(false);
    }
  }

  function closeForgotPasswordDialog() {
    setShowForgotPasswordDialog(false);
    setForgotPasswordMessage("");
    setForgotPasswordError("");
    setForgotPasswordCodeCountdown(0);
    setForgotPasswordForm((previous) => ({
      ...previous,
      code: "",
      new_password: "",
      confirm_password: "",
      mobile: profile?.mobile || previous.mobile,
    }));
  }

  function handleCheckForgotPasswordPassword() {
    setForgotPasswordMessage("");
    setForgotPasswordError("");

    if (!forgotPasswordForm.new_password) {
      setForgotPasswordError("请输入新密码");
      return;
    }
    void (async () => {
      try {
        setForgotPasswordCheckLoading(true);
        const response = await checkMyResetPassword({
          new_password: forgotPasswordForm.new_password,
        });
        if (response?.data?.available) {
          setForgotPasswordMessage(response.data.message || "该密码可以使用");
          return;
        }
        setForgotPasswordError(response?.data?.message || "该密码暂不可用");
      } catch (error) {
        setForgotPasswordError(error?.response?.data?.detail || "密码检查失败，请稍后重试。");
      } finally {
        setForgotPasswordCheckLoading(false);
      }
    })();
  }

  async function handleSendForgotPasswordCode() {
    setForgotPasswordMessage("");
    setForgotPasswordError("");

    if (!forgotPasswordForm.mobile.trim()) {
      setForgotPasswordError("请输入手机号");
      return;
    }

    try {
      setForgotPasswordSendingCode(true);
      await sendSmsCode({
        mobile: forgotPasswordForm.mobile.trim(),
        biz_type: "login",
      });
      setForgotPasswordMessage("验证码已发送，请查收短信。");
      setForgotPasswordCodeCountdown(20);
    } catch (error) {
      setForgotPasswordError(error?.response?.data?.detail || "验证码发送失败，请稍后重试。");
    } finally {
      setForgotPasswordSendingCode(false);
    }
  }

  async function handleResetPasswordBySms() {
    setForgotPasswordMessage("");
    setForgotPasswordError("");

    if (!forgotPasswordForm.mobile.trim()) {
      setForgotPasswordError("请输入手机号");
      return;
    }
    if (!forgotPasswordForm.code.trim()) {
      setForgotPasswordError("请输入验证码");
      return;
    }
    if (!forgotPasswordForm.new_password) {
      setForgotPasswordError("请输入新密码");
      return;
    }
    if (forgotPasswordForm.new_password !== forgotPasswordForm.confirm_password) {
      setForgotPasswordError("两次输入的新密码不一致");
      return;
    }

    const localValidationMessage = validatePassword(forgotPasswordForm.new_password);
    if (localValidationMessage) {
      setForgotPasswordError(localValidationMessage);
      return;
    }

    try {
      setForgotPasswordSaving(true);
      await resetMyPasswordBySms({
        mobile: forgotPasswordForm.mobile.trim(),
        code: forgotPasswordForm.code.trim(),
        new_password: forgotPasswordForm.new_password,
      });
      notify("密码重置成功");
      closeForgotPasswordDialog();
      await fetchProfile();
    } catch (error) {
      setForgotPasswordError(error?.response?.data?.detail || "密码重置失败，请稍后重试。");
    } finally {
      setForgotPasswordSaving(false);
    }
  }

  async function handleLogout() {
    setErrorMessage("");
    try {
      setLogoutLoading(true);
      await logout();
    } catch (error) {
      console.error("Logout request failed", error);
    } finally {
      clearAccessToken();
      setLogoutLoading(false);
      navigate("/login", { replace: true });
    }
  }

  function renderAccountPanel() {
    return (
      <div className="profile-content-stack">
        <div className="profile-page-head">
          <div>
            <h1 className="profile-page-title">用户信息</h1>
            <p className="profile-page-subtitle">管理昵称、密码和基础账号资料。</p>
          </div>
          <button type="button" className="secondary-btn" onClick={() => navigate("/")}>
            返回首页
          </button>
        </div>

        {loading ? null : null}
        {errorMessage ? <div className="error-box">{errorMessage}</div> : null}

        {!loading && profile ? (
          <div className="card">
            <h2 className="card-title">当前登录用户信息</h2>
            <div className="profile-hero">
              {profile.avatar_url ? (
                <img src={profile.avatar_url} alt="用户头像" className="avatar-image" />
              ) : (
                <div className="avatar-fallback">{getDisplayInitial(profile.nickname, profile.mobile)}</div>
              )}
              <div className="hero-meta">
                <div className="hero-name">{profile.nickname || "未设置昵称"}</div>
                <div className="hero-sub">{profile.mobile || "未绑定手机号"}</div>
              </div>
            </div>

            <div className="info-item"><span className="label">用户 ID：</span><span className="value">{profile.user_id}</span></div>
            <div className="info-item"><span className="label">手机号：</span><span className="value">{profile.mobile || "未绑定"}</span></div>

            <div className="info-item nickname-row">
              <span className="label">昵称：</span>
              <div className="value value-block">
                <div className="nickname-actions">
                  <span>{profile.nickname || "未设置"}</span>
                  {!showNicknameEditor ? (
                    <button type="button" className="secondary-btn inline-btn" onClick={() => setShowNicknameEditor(true)}>
                      修改昵称
                    </button>
                  ) : null}
                </div>

                {showNicknameEditor ? (
                  <div className="editor-box">
                    <input
                      value={nicknameForm.nickname}
                      onChange={(event) => {
                        setNicknameForm({ nickname: event.target.value });
                        resetNicknameCheckResult();
                      }}
                      className="input"
                      type="text"
                      maxLength={100}
                      placeholder="请输入新昵称"
                    />

                    <div className="inline-actions">
                      <button type="button" className="secondary-btn inline-btn" disabled={checkNicknameLoading} onClick={handleCheckNickname}>
                        检查昵称是否可用
                      </button>
                      <button type="button" className="primary-btn inline-btn" disabled={updateNicknameLoading} onClick={handleUpdateNickname}>
                        保存昵称
                      </button>
                      <button type="button" className="secondary-btn inline-btn" disabled={updateNicknameLoading || checkNicknameLoading} onClick={() => setShowNicknameEditor(false)}>
                        取消
                      </button>
                    </div>

                    {nicknameCheckMessage ? (
                      <p className={`check-message ${nicknameCheckAvailable ? "check-success" : "check-error"}`}>{nicknameCheckMessage}</p>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="info-item"><span className="label">状态：</span><span className="value">{profile.status}</span></div>
          </div>
        ) : null}

        {profile ? (
          <div className="card">
            <h2 className="card-title">设置密码</h2>
            <p className="desc">密码需为 8-24 位，且至少包含字母、数字、特殊字符中的 2 种，不能包含空格。</p>

            {!showPasswordEditor ? (
              <button type="button" className="primary-btn" onClick={() => setShowPasswordEditor(true)}>
                {profile.has_password ? "修改密码" : "设置密码"}
              </button>
            ) : (
              <div className="editor-box">
                <input
                  value={passwordForm.new_password}
                  onChange={(event) => {
                    setPasswordForm((previous) => ({ ...previous, new_password: event.target.value }));
                    resetPasswordCheckResult();
                  }}
                  className="input"
                  type="password"
                  placeholder="请输入新密码"
                />
                <input
                  value={passwordForm.confirm_password}
                  onChange={(event) => {
                    setPasswordForm((previous) => ({ ...previous, confirm_password: event.target.value }));
                    resetPasswordCheckResult();
                  }}
                  className="input"
                  type="password"
                  placeholder="请再次输入新密码"
                />

                <div className="inline-actions">
                  <button type="button" className="secondary-btn" disabled={checkPasswordLoading} onClick={handleCheckPassword}>
                        检查密码是否可用
                  </button>
                  <button type="button" className="primary-btn" disabled={setPasswordLoading} onClick={handleSetPassword}>
                        保存密码
                  </button>
                  <button type="button" className="secondary-btn" disabled={setPasswordLoading || checkPasswordLoading} onClick={() => setShowPasswordEditor(false)}>
                    取消
                  </button>
                </div>

                {passwordCheckMessage ? (
                  <p className={`check-message ${passwordCheckAvailable ? "check-success" : "check-error"}`}>{passwordCheckMessage}</p>
                ) : null}
              </div>
            )}
          </div>
        ) : null}

        <div className="actions">
          <button type="button" className="secondary-btn" disabled={loading} onClick={fetchProfile}>
            刷新用户信息
          </button>
          <button type="button" className="danger-btn" disabled={logoutLoading} onClick={handleLogout}>
            退出登录
          </button>
        </div>
      </div>
    );
  }

  function renderAccountPanelV2() {
    return (
      <div className="profile-content-stack">
        <div className="profile-page-head">
          <div>
            <h1 className="profile-page-title">用户信息</h1>
            <p className="profile-page-subtitle">管理昵称、手机号和登录密码。</p>
          </div>
          <button type="button" className="secondary-btn" onClick={() => navigate("/")}>
            返回首页
          </button>
        </div>

        {loading ? null : null}
        {errorMessage ? <div className="error-box">{errorMessage}</div> : null}

        {!loading && profile ? (
          <div className="card profile-account-card">
            <div className="profile-hero">
              {profile.avatar_url ? (
                <img src={profile.avatar_url} alt="用户头像" className="avatar-image" />
              ) : (
                <div className="avatar-fallback">{getDisplayInitial(profile.nickname, profile.mobile)}</div>
              )}
              <div className="hero-meta">
                <div className="hero-name">{profile.nickname || "未设置昵称"}</div>
                <div className="hero-sub">{profile.mobile || "未绑定手机号"}</div>
              </div>
            </div>            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <h2 className="card-title">{"\u624b\u673a\u53f7"}</h2>
                {profile.mobile && !showMobileEditor ? (
                  <button
                    type="button"
                    className="secondary-btn inline-btn"
                    onClick={() => {
                      setShowMobileEditor(true);
                      setBindMobileForm({ mobile: profile.mobile || "" });
                      setBindMobileMessage("");
                    }}
                  >
                    {"\u4fee\u6539\u624b\u673a\u53f7"}
                  </button>
                ) : null}
              </div>
              {!showMobileEditor && profile.mobile ? (
                <div className="profile-account-current-value">{profile.mobile}</div>
              ) : (
                <div className="editor-box">
                  <input
                    value={bindMobileForm.mobile}
                    onChange={(event) => {
                      setBindMobileForm({ mobile: event.target.value.replace(/\D/g, "").slice(0, 11) });
                      setBindMobileMessage("");
                    }}
                    className="input"
                    type="tel"
                    maxLength={11}
                    placeholder={"\u8bf7\u8f93\u5165\u624b\u673a\u53f7"}
                  />
                  <div className="inline-actions">
                    <button type="button" className="primary-btn inline-btn" disabled={bindMobileSaving} onClick={handleBindMyMobile}>
                      {profile.mobile ? "\u4fdd\u5b58\u624b\u673a\u53f7" : "\u7ed1\u5b9a\u624b\u673a\u53f7"}
                    </button>
                    {profile.mobile ? (
                      <button
                        type="button"
                        className="secondary-btn inline-btn"
                        disabled={bindMobileSaving}
                        onClick={() => {
                          setShowMobileEditor(false);
                          setBindMobileForm({ mobile: profile.mobile || "" });
                          setBindMobileMessage("");
                        }}
                      >
                        {"\u53d6\u6d88"}
                      </button>
                    ) : null}
                  </div>
                  {bindMobileMessage ? <p className="check-message check-success">{bindMobileMessage}</p> : null}
                </div>
              )}
            </div>
            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <h2 className="card-title">用户 ID</h2>
              </div>
              <div className="profile-account-current-value">{profile.user_id || "暂无"}</div>
            </div>
            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <h2 className="card-title">{"\u6635\u79f0"}</h2>
                {!showNicknameEditor ? (
                  <button type="button" className="secondary-btn inline-btn" onClick={() => setShowNicknameEditor(true)}>
                    {"\u4fee\u6539\u6635\u79f0"}
                  </button>
                ) : null}
              </div>
              <div className="value-block">
                <div className="profile-account-current-value">{profile.nickname || "未设置"}</div>
                {showNicknameEditor ? (
                  <div className="editor-box">
                    <input
                      value={nicknameForm.nickname}
                      onChange={(event) => {
                        setNicknameForm({ nickname: event.target.value });
                        resetNicknameCheckResult();
                      }}
                      className="input"
                      type="text"
                      maxLength={100}
                      placeholder="请输入新昵称"
                    />

                    <div className="inline-actions">
                      <button type="button" className="secondary-btn inline-btn" disabled={checkNicknameLoading} onClick={handleCheckNickname}>
                        检查昵称是否可用
                      </button>
                      <button type="button" className="primary-btn inline-btn" disabled={updateNicknameLoading} onClick={handleUpdateNickname}>
                        保存昵称
                      </button>
                      <button type="button" className="secondary-btn inline-btn" disabled={updateNicknameLoading || checkNicknameLoading} onClick={() => setShowNicknameEditor(false)}>
                        取消
                      </button>
                    </div>

                    {nicknameCheckMessage ? (
                      <p className={`check-message ${nicknameCheckAvailable ? "check-success" : "check-error"}`}>{nicknameCheckMessage}</p>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <div>
                  <h2 className="card-title">密码</h2>
                  <p className="desc">密码需 8-24 位，且至少包含字母、数字、特殊字符中的 2 种，不能包含空格。</p>
                </div>
                <div className="profile-account-inline-actions">
                  {!showPasswordEditor ? (
                    <button type="button" className="primary-btn inline-btn" onClick={() => setShowPasswordEditor(true)}>
                      {profile.has_password ? "修改密码" : "设置密码"}
                    </button>
                  ) : null}
                  {profile.has_password ? (
                    <button
                      type="button"
                      className="secondary-btn inline-btn"
                      onClick={() => {
                        setForgotPasswordMessage("");
                        setForgotPasswordError("");
                        setForgotPasswordForm({
                          mobile: profile?.mobile || "",
                          code: "",
                          new_password: "",
                          confirm_password: "",
                        });
                        setForgotPasswordCodeCountdown(0);
                        setShowForgotPasswordDialog(true);
                      }}
                    >
                      忘记密码
                    </button>
                  ) : null}
                </div>
              </div>

              {showPasswordEditor ? (
                <div className="editor-box">
                  {profile.has_password ? (
                    <input
                      value={passwordForm.current_password}
                      onChange={(event) => {
                        setPasswordForm((previous) => ({ ...previous, current_password: event.target.value }));
                        resetPasswordCheckResult();
                      }}
                      className="input"
                      type="password"
                      placeholder="请输入当前密码"
                    />
                  ) : null}
                  <div className="profile-code-row">
                    <input
                      value={passwordForm.new_password}
                      onChange={(event) => {
                        setPasswordForm((previous) => ({ ...previous, new_password: event.target.value }));
                        resetPasswordCheckResult();
                      }}
                      className="input"
                      type="password"
                      placeholder="请输入新密码"
                    />
                    <button type="button" className="secondary-btn inline-btn" disabled={checkPasswordLoading || !passwordForm.new_password} onClick={handleCheckPassword}>
                        检查密码是否可用
                    </button>
                  </div>
                  <input
                    value={passwordForm.confirm_password}
                    onChange={(event) => {
                      setPasswordForm((previous) => ({ ...previous, confirm_password: event.target.value }));
                      resetPasswordCheckResult();
                    }}
                    className="input"
                    type="password"
                    placeholder="请再次输入新密码"
                  />

                  <div className="inline-actions">
                    <button type="button" className="primary-btn inline-btn" disabled={setPasswordLoading} onClick={handleSetPassword}>
                        保存密码
                    </button>
                    <button
                      type="button"
                      className="secondary-btn inline-btn"
                      disabled={setPasswordLoading || checkPasswordLoading}
                      onClick={() => {
                        setShowPasswordEditor(false);
                        setPasswordForm({
                          current_password: "",
                          new_password: "",
                          confirm_password: "",
                        });
                        resetPasswordCheckResult();
                      }}
                    >
                      取消
                    </button>
                  </div>

                  {passwordCheckMessage ? (
                    <p className={`check-message ${passwordCheckAvailable ? "check-success" : "check-error"}`}>{passwordCheckMessage}</p>
                  ) : null}
                </div>
              ) : null}
            </div>

            <div className="profile-account-footer">
              <button type="button" className="danger-btn" disabled={logoutLoading} onClick={handleLogout}>
                退出登录
              </button>
            </div>
          </div>
        ) : null}

        {showForgotPasswordDialog ? (
          <div className="profile-modal-backdrop">
            <div className="profile-modal-card" onClick={(event) => event.stopPropagation()}>
              <div className="profile-modal-head">
                <div>
                  <h2 className="card-title">忘记密码</h2>
                  <p className="desc">通过当前绑定手机号获取验证码后，可直接重置密码。</p>
                </div>
                <button type="button" className="profile-modal-close" onClick={closeForgotPasswordDialog} aria-label="关闭">
                  ×
                </button>
              </div>

              <div className="editor-box">
                <input
                  className="input"
                  type="tel"
                  maxLength={11}
                  value={forgotPasswordForm.mobile}
                  readOnly
                  placeholder="当前绑定手机号"
                />

                <div className="profile-code-row">
                  <input
                    className="input"
                    type="text"
                    maxLength={6}
                    value={forgotPasswordForm.code}
                    onChange={(event) =>
                      setForgotPasswordForm((previous) => ({
                        ...previous,
                        code: event.target.value.replace(/\D/g, ""),
                      }))
                    }
                    placeholder="请输入验证码"
                  />
                  <button
                    type="button"
                    className="secondary-btn inline-btn"
                    disabled={forgotPasswordSendingCode || forgotPasswordCodeCountdown > 0}
                    onClick={handleSendForgotPasswordCode}
                  >
                    {forgotPasswordCodeCountdown > 0 ? `${forgotPasswordCodeCountdown}s后重发` : "获取验证码"}
                  </button>
                </div>

                <div className="profile-code-row">
                  <input
                    className="input"
                    type="password"
                    value={forgotPasswordForm.new_password}
                    onChange={(event) =>
                      setForgotPasswordForm((previous) => ({
                        ...previous,
                        new_password: event.target.value,
                      }))
                    }
                    placeholder="请输入新密码"
                  />
                  <button
                    type="button"
                    className="secondary-btn inline-btn"
                    disabled={forgotPasswordSaving || forgotPasswordCheckLoading || !forgotPasswordForm.new_password}
                    onClick={handleCheckForgotPasswordPassword}
                  >
                    检查密码是否可用
                  </button>
                </div>

                <input
                  className="input"
                  type="password"
                  value={forgotPasswordForm.confirm_password}
                  onChange={(event) =>
                    setForgotPasswordForm((previous) => ({
                      ...previous,
                      confirm_password: event.target.value,
                    }))
                  }
                  placeholder="请再次输入新密码"
                />

                {forgotPasswordMessage ? <p className="check-message check-success">{forgotPasswordMessage}</p> : null}
                {forgotPasswordError ? <p className="check-message check-error">{forgotPasswordError}</p> : null}

                <div className="inline-actions">
                  <button type="button" className="primary-btn inline-btn" disabled={forgotPasswordSaving} onClick={handleResetPasswordBySms}>
                    保存修改
                  </button>
                  <button type="button" className="secondary-btn inline-btn" disabled={forgotPasswordSaving} onClick={closeForgotPasswordDialog}>
                    取消
                  </button>
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    );
  }

  function renderSectionHeader({ title, description, collapsed, onToggleCollapse, action = null, headingLevel = 3 }) {
    const HeadingTag = headingLevel === 2 ? "h2" : "h3";

    return (
      <div className="profile-section-head">
        <div className="profile-section-head-copy">
          <HeadingTag className="card-title">{title}</HeadingTag>
        </div>
        <div className="profile-section-head-actions">
          {!collapsed ? action : null}
          <button
            type="button"
            className="profile-section-toggle"
            onClick={onToggleCollapse}
            aria-label={collapsed ? `展开${title}` : `收起${title}`}
            title={collapsed ? `展开${title}` : `收起${title}`}
          >
            <svg
              className={`profile-section-toggle-arrow ${collapsed ? "is-collapsed" : ""}`}
              viewBox="0 0 20 20"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M5 7.5L10 12.5L15 7.5"
                stroke="currentColor"
                strokeWidth="1.9"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>
    );
  }

  function updateTargetPreferenceState(updater) {
    setArchiveMessage("");
    setArchiveSaveWarning("");
    setArchiveFormState((previous) => {
      const majorOptions = getFieldOptions(archiveBundle?.form_meta, "student_basic_info", "MAJ_CODE_VAL");
      const normalizedPrevious = normalizeTargetPreferenceRows(previous, {
        studentId: previous?.student_basic_info?.student_id || archiveStudentId,
        majorOptions,
        preserveDraftRows: true,
      });
      return normalizeTargetPreferenceRows(updater(normalizedPrevious), {
        studentId: normalizedPrevious?.student_basic_info?.student_id || archiveStudentId,
        majorOptions,
        preserveDraftRows: true,
      });
    });
  }

  function handleAddTargetCountryRow() {
    updateTargetPreferenceState((previous) => {
      const rows = Array.isArray(previous[TARGET_COUNTRY_TABLE]) ? [...previous[TARGET_COUNTRY_TABLE]] : [];
      if (rows.length >= MAX_TARGET_COUNTRY_ROWS) {
        return previous;
      }
      return {
        ...previous,
        [TARGET_COUNTRY_TABLE]: [
          ...rows,
          buildDefaultTargetCountryRow({
            studentId: previous?.student_basic_info?.student_id || archiveStudentId,
            index: rows.length,
            countryCode: "",
          }),
        ],
      };
    });
  }

  function handleUpdateTargetCountryRow(rowIndex, fieldName, nextValue) {
    updateTargetPreferenceState((previous) => {
      const rows = Array.isArray(previous[TARGET_COUNTRY_TABLE]) ? [...previous[TARGET_COUNTRY_TABLE]] : [];
      rows[rowIndex] = {
        ...buildDefaultTargetCountryRow({
          studentId: previous?.student_basic_info?.student_id || archiveStudentId,
          index: rowIndex,
        }),
        ...(rows[rowIndex] || {}),
        [fieldName]: nextValue || null,
        [TARGET_PREFERENCE_DRAFT_FIELD]: nextValue ? 0 : 1,
      };
      return { ...previous, [TARGET_COUNTRY_TABLE]: rows };
    });
  }

  function handleRemoveTargetCountryRow(rowIndex) {
    updateTargetPreferenceState((previous) => {
      const nextRows = (Array.isArray(previous[TARGET_COUNTRY_TABLE]) ? previous[TARGET_COUNTRY_TABLE] : []).filter(
        (_, index) => index !== rowIndex
      );
      return {
        ...previous,
        student_basic_info: {
          ...(previous.student_basic_info || {}),
          CTRY_CODE_VAL: nextRows[0]?.country_code || null,
        },
        [TARGET_COUNTRY_TABLE]: nextRows,
      };
    });
  }

  function handleAddTargetMajorRow() {
    updateTargetPreferenceState((previous) => {
      const rows = Array.isArray(previous[TARGET_MAJOR_TABLE]) ? [...previous[TARGET_MAJOR_TABLE]] : [];
      if (rows.length >= MAX_TARGET_MAJOR_ROWS) {
        return previous;
      }
      return {
        ...previous,
        [TARGET_MAJOR_TABLE]: [
          ...rows,
          buildDefaultTargetMajorRow({
            studentId: previous?.student_basic_info?.student_id || archiveStudentId,
            index: rows.length,
            majorCode: "",
          }),
        ],
      };
    });
  }

  function handleUpdateTargetMajorRow(rowIndex, fieldName, nextValue) {
    updateTargetPreferenceState((previous) => {
      const majorOptions = getFieldOptions(archiveBundle?.form_meta, "student_basic_info", "MAJ_CODE_VAL");
      const rows = Array.isArray(previous[TARGET_MAJOR_TABLE]) ? [...previous[TARGET_MAJOR_TABLE]] : [];
      const normalizedValue = nextValue || null;
      rows[rowIndex] = {
        ...buildDefaultTargetMajorRow({
          studentId: previous?.student_basic_info?.student_id || archiveStudentId,
          index: rowIndex,
        }),
        ...(rows[rowIndex] || {}),
        [fieldName]: normalizedValue,
        [TARGET_PREFERENCE_DRAFT_FIELD]: normalizedValue ? 0 : 1,
      };
      if (fieldName === "major_direction_code") {
        rows[rowIndex].major_code = normalizedValue;
        rows[rowIndex].major_direction_label = normalizedValue ? findOptionLabel(majorOptions, normalizedValue) : null;
        rows[rowIndex][TARGET_PREFERENCE_DRAFT_FIELD] = normalizedValue ? 0 : 1;
      }
      return { ...previous, [TARGET_MAJOR_TABLE]: rows };
    });
  }

  function handleRemoveTargetMajorRow(rowIndex) {
    updateTargetPreferenceState((previous) => {
      const nextRows = (Array.isArray(previous[TARGET_MAJOR_TABLE]) ? previous[TARGET_MAJOR_TABLE] : []).filter(
        (_, index) => index !== rowIndex
      );
      return {
        ...previous,
        student_basic_info: {
          ...(previous.student_basic_info || {}),
          MAJ_CODE_VAL: nextRows[0]?.major_code || nextRows[0]?.major_direction_code || null,
          MAJ_INTEREST_TEXT: nextRows[0]?.major_direction_label || null,
        },
        [TARGET_MAJOR_TABLE]: nextRows,
      };
    });
  }

  function renderTargetPreferenceCard() {
    const basicInfo = archiveFormState?.student_basic_info || {};
    const countryOptions = getFieldOptions(archiveBundle?.form_meta, "student_basic_info", "CTRY_CODE_VAL");
    const majorOptions = getFieldOptions(archiveBundle?.form_meta, "student_basic_info", "MAJ_CODE_VAL");
    const countryRows = Array.isArray(archiveFormState?.[TARGET_COUNTRY_TABLE])
      ? archiveFormState[TARGET_COUNTRY_TABLE]
      : [];
    const majorRows = Array.isArray(archiveFormState?.[TARGET_MAJOR_TABLE])
      ? archiveFormState[TARGET_MAJOR_TABLE]
      : [];
    const displayCountryRows =
      countryRows.length > 0
        ? countryRows
        : basicInfo.CTRY_CODE_VAL
          ? [buildDefaultTargetCountryRow({ studentId: archiveStudentId, countryCode: basicInfo.CTRY_CODE_VAL })]
          : [buildDefaultTargetCountryRow({ studentId: archiveStudentId })];
    const displayMajorRows =
      majorRows.length > 0
      ? majorRows
      : basicInfo.MAJ_CODE_VAL || basicInfo.MAJ_INTEREST_TEXT
          ? [
              buildDefaultTargetMajorRow({
                studentId: archiveStudentId,
                majorCode: basicInfo.MAJ_CODE_VAL,
                majorLabel: basicInfo.MAJ_INTEREST_TEXT || findOptionLabel(majorOptions, basicInfo.MAJ_CODE_VAL),
              }),
            ]
          : [buildDefaultTargetMajorRow({ studentId: archiveStudentId })];
    const showVirtualCountryRow = countryRows.length === 0 && !basicInfo.CTRY_CODE_VAL;
    const showVirtualMajorRow = majorRows.length === 0 && !basicInfo.MAJ_CODE_VAL && !basicInfo.MAJ_INTEREST_TEXT;

    return (
      <div className="card profile-target-preference-card">
        <div className="profile-form-array-head">
          <div>
            <h3 className="card-title">目标国家与专业</h3>
          </div>
        </div>
        <div className="profile-target-edit-grid">
          <div className="profile-target-edit-block">
            <div className="profile-target-edit-head">
              <span className="profile-target-preference-label">目标国家 / 地区</span>
              {displayCountryRows.length < MAX_TARGET_COUNTRY_ROWS ? (
                <button type="button" className="secondary-btn" onClick={handleAddTargetCountryRow}>
                  新增国家
                </button>
              ) : null}
            </div>
            <div className="profile-form-stack">
              {displayCountryRows.map((row, rowIndex) => (
                <div key={`target-country-${rowIndex}`} className="profile-form-array-row">
                  <div className="profile-form-row-inline">
                    <div className="profile-form-grid">
                      <div className="profile-form-field">
                        <label>国家 / 地区</label>
                        <SearchableSelectControl
                          options={countryOptions}
                          value={row.country_code || ""}
                          onChange={(nextValue) => handleUpdateTargetCountryRow(rowIndex, "country_code", nextValue)}
                          placeholder="请输入国家关键词搜索"
                        />
                      </div>
                    </div>
                    <div className="profile-form-row-side">
                      {!showVirtualCountryRow ? (
                        <button type="button" className="secondary-btn" onClick={() => handleRemoveTargetCountryRow(rowIndex)}>
                          删除
                        </button>
                      ) : (
                        <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="profile-target-edit-block">
            <div className="profile-target-edit-head">
              <span className="profile-target-preference-label">目标专业方向</span>
              {displayMajorRows.length < MAX_TARGET_MAJOR_ROWS ? (
                <button type="button" className="secondary-btn" onClick={handleAddTargetMajorRow}>
                  新增专业
                </button>
              ) : null}
            </div>
            <div className="profile-form-stack">
              {displayMajorRows.map((row, rowIndex) => (
                <div key={`target-major-${rowIndex}`} className="profile-form-array-row">
                  <div className="profile-form-row-inline">
                    <div className="profile-form-grid">
                      <div className="profile-form-field">
                        <label>专业方向</label>
                        <SearchableSelectControl
                          options={majorOptions}
                          value={row.major_direction_code || row.major_code || ""}
                          onChange={(nextValue) => handleUpdateTargetMajorRow(rowIndex, "major_direction_code", nextValue)}
                          placeholder="请输入专业关键词搜索"
                        />
                      </div>
                    </div>
                    <div className="profile-form-row-side">
                      {!showVirtualMajorRow ? (
                        <button type="button" className="secondary-btn" onClick={() => handleRemoveTargetMajorRow(rowIndex)}>
                          删除
                        </button>
                      ) : (
                        <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  function renderArchiveSection(tableName, options = {}) {
    const tableMeta = archiveBundle?.form_meta?.tables?.[tableName];
    if (!tableMeta) {
      return null;
    }

    const {
      embedded = false,
      title = tableMeta.label,
      description = null,
      showDescription = true,
      addButtonLabel = "\u65b0\u589e\u4e00\u6761",
      collapsible = false,
      sectionKey = tableName,
      showVirtualRowWhenEmpty = false,
      onAddRow = null,
    } = options;

    const collapsed = collapsible && isArchiveSectionCollapsed(sectionKey);
    const visibleFields = getVisibleProfileFields(tableName, tableMeta.fields, {
      excludeBasicTargetFields: tableName === "student_basic_info",
    });
    if (visibleFields.length === 0) {
      return null;
    }

    const wrapperClassName = embedded
      ? "profile-embedded-section"
      : `card profile-form-card ${collapsed ? "profile-form-card-collapsed" : ""}`;
    const singleDescription = description || "\u8fd9\u91cc\u5c55\u793a\u7684\u662f\u6b63\u5f0f\u6863\u6848\u4e3b\u8868\u4fe1\u606f\uff0c\u53ef\u76f4\u63a5\u4fee\u6539\u540e\u4fdd\u5b58\u3002";
    const arrayDescription = description || "\u53ef\u65b0\u589e\u3001\u5220\u9664\u548c\u4fee\u6539\u8fd9\u4e00\u7c7b\u660e\u7ec6\u6570\u636e\u3002";
    const handleAddAction = onAddRow || (() => handleAddRow(tableName));

      if (tableMeta.kind === "single") {
        const sectionValue = archiveFormState?.[tableName] || {};

        return (
          <div key={tableName} className={wrapperClassName}>
          {collapsible ? (
            renderSectionHeader({
              title,
              description: showDescription ? singleDescription : null,
              collapsed,
              onToggleCollapse: () => toggleArchiveSection(sectionKey),
            })
            ) : (
              <div className="profile-form-array-head">
                <div>
                  <h3 className="card-title">{title}</h3>
                </div>
              </div>
            )}

          {!collapsed ? (
            <div className="profile-form-grid">
              {visibleFields.map((field) => (
                <div
                  key={`${tableName}-${field.name}`}
                  className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                >
                  {renderFieldLabel(field)}
                  {renderFieldControl({
                    tableName,
                    field,
                    value: sectionValue[field.name],
                    onChange: (rawValue) =>
                      updateSingleField(
                        tableName,
                        field.name,
                        normalizeFieldChangedValue(
                          tableName,
                          field.name,
                          rawValue,
                          field.input_type
                        )
                      ),
                  })}
                  {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                </div>
              ))}
            </div>
          ) : null}
        </div>
      );
    }

    const rows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    const displayRows =
      showVirtualRowWhenEmpty && rows.length === 0
        ? [
            buildEmptyRow(tableMeta.fields || [], {
              tableName,
              studentId: archiveStudentId,
              existingRowsCount: 0,
            }),
          ]
        : rows;

    return (
      <div key={tableName} className={wrapperClassName}>
        {collapsible ? (
          renderSectionHeader({
            title,
            description: showDescription ? arrayDescription : null,
            collapsed,
            onToggleCollapse: () => toggleArchiveSection(sectionKey),
            action: (
              <button type="button" className="secondary-btn" onClick={handleAddAction}>
                {addButtonLabel}
              </button>
            ),
          })
          ) : (
            <div className="profile-form-array-head">
              <div>
                <h3 className="card-title">{title}</h3>
              </div>
              <button type="button" className="secondary-btn" onClick={handleAddAction}>
                {addButtonLabel}
            </button>
          </div>
        )}

        {!collapsed ? (
          <>
            {rows.length === 0 && !showVirtualRowWhenEmpty ? (
              <div className="profile-form-empty">{"\u5f53\u524d\u6ca1\u6709\u6570\u636e\uff0c\u53ef\u4ee5\u70b9\u51fb\u201c\u65b0\u589e\u4e00\u6761\u201d\u3002"}</div>
            ) : null}

            <div className="profile-form-stack">
              {displayRows.map((row, rowIndex) => {
                const isVirtualRow = showVirtualRowWhenEmpty && rows.length === 0;
                const rowVisibleFields = visibleFields
                  .map((field) =>
                    buildRenderableRowFieldMeta({
                      tableName,
                      field,
                      rowIndex,
                      row,
                      rows: displayRows,
                      archiveFormState,
                    })
                  )
                  .filter((field) => shouldRenderRowField(tableName, row, field));

                return (
                  <div key={`${tableName}-${rowIndex}`} className="profile-form-array-row">
                    <div className="profile-form-row-inline">
                      <div className="profile-form-grid">
                        {rowVisibleFields.map((field) => {
                          return (
                            <div
                              key={`${tableName}-${rowIndex}-${field.name}`}
                              className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                            >
                              {renderFieldLabel(field)}
                              {renderFieldControl({
                                tableName,
                                field,
                                value: row?.[field.name],
                                row,
                                onRowPatch: (nextValues) =>
                                  updateRowFields(tableName, rowIndex, nextValues),
                                onChange: (rawValue) =>
                                  updateRowField(
                                    tableName,
                                    rowIndex,
                                    field.name,
                                    normalizeChangedValue(
                                      rawValue,
                                      isNumericRelationField(tableName, field.name) ? "number" : field.input_type
                                    )
                                  ),
                              })}
                              {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                            </div>
                          );
                        })}
                      </div>

                      <div className="profile-form-row-side">
                        {!isVirtualRow ? (
                          <button type="button" className="secondary-btn" onClick={() => handleRemoveRow(tableName, rowIndex)}>
                            {"\u5220\u9664"}
                          </button>
                        ) : (
                          <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderCurriculumScopedSection(tableName, curriculumCode, options = {}) {
    const tableMeta = archiveBundle?.form_meta?.tables?.[tableName];
    if (!tableMeta || tableMeta.kind !== "multi") {
      return renderArchiveSection(tableName, options);
    }

    const {
      embedded = false,
      title = tableMeta.label,
      addButtonLabel = "\u65b0\u589e\u4e00\u6761",
      showVirtualRowWhenEmpty = false,
      collapsible = false,
      sectionKey = `${tableName}-${curriculumCode}`,
    } = options;

    const visibleFields = getVisibleProfileFields(tableName, tableMeta.fields);
    if (visibleFields.length === 0) {
      return null;
    }

    const collapsed = collapsible && isArchiveSectionCollapsed(sectionKey);
    const allRows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    const scopedRows = allRows
      .map((row, actualRowIndex) => ({ row, actualRowIndex }))
      .filter(({ row }) => row?.curriculum_system_code === curriculumCode);
    const displayRows =
      showVirtualRowWhenEmpty && scopedRows.length === 0
        ? [
            {
              row: buildEmptyRow(tableMeta.fields || [], {
                tableName,
                studentId: archiveStudentId,
                existingRowsCount: scopedRows.length,
                initialValues: {
                  curriculum_system_code: curriculumCode,
                },
              }),
              actualRowIndex: allRows.length,
              isVirtualRow: true,
            },
          ]
        : scopedRows.map((item) => ({ ...item, isVirtualRow: false }));
    const wrapperClassName = embedded
      ? `profile-embedded-section ${collapsed ? "profile-form-card-collapsed" : ""}`
      : `card profile-form-card ${collapsed ? "profile-form-card-collapsed" : ""}`;
    const handleAddAction = () =>
      handleAddCurriculumScopedRow(tableName, curriculumCode, {
        preserveVirtualRowWhenEmpty: showVirtualRowWhenEmpty,
      });

    return (
      <div key={`${tableName}-${curriculumCode}`} className={wrapperClassName}>
        {collapsible ? (
          renderSectionHeader({
            title,
            collapsed,
            onToggleCollapse: () => toggleArchiveSection(sectionKey),
            action: (
              <button type="button" className="secondary-btn" onClick={handleAddAction}>
                {addButtonLabel}
              </button>
            ),
          })
        ) : (
          <div className="profile-form-array-head">
            <div>
              <h3 className="card-title">{title}</h3>
            </div>
            <button type="button" className="secondary-btn" onClick={handleAddAction}>
              {addButtonLabel}
            </button>
          </div>
        )}

        {!collapsed ? (
          <>
            {scopedRows.length === 0 && !showVirtualRowWhenEmpty ? (
              <div className="profile-form-empty">{"\u5f53\u524d\u6ca1\u6709\u6570\u636e\uff0c\u53ef\u4ee5\u70b9\u51fb\u201c\u65b0\u589e\u4e00\u6761\u201d\u3002"}</div>
            ) : null}

            <div className="profile-form-stack">
              {displayRows.map(({ row, actualRowIndex, isVirtualRow }, rowIndex) => {
                const rowVisibleFields = visibleFields
                  .map((field) =>
                    buildRenderableRowFieldMeta({
                      tableName,
                      field,
                      rowIndex: actualRowIndex,
                      row,
                      rows: allRows,
                      archiveFormState,
                    })
                  )
                  .filter((field) => shouldRenderRowField(tableName, row, field));

                return (
                  <div key={`${tableName}-${curriculumCode}-${rowIndex}`} className="profile-form-array-row">
                    <div className="profile-form-row-inline">
                      <div className="profile-form-grid">
                        {rowVisibleFields.map((field) => (
                          <div
                            key={`${tableName}-${curriculumCode}-${actualRowIndex}-${field.name}`}
                            className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                          >
                            {renderFieldLabel(field)}
                            {renderFieldControl({
                              tableName,
                              field,
                              value: row?.[field.name],
                              row,
                              onRowPatch: (nextValues) =>
                                updateRowFields(tableName, actualRowIndex, {
                                  ...row,
                                  ...nextValues,
                                  curriculum_system_code: curriculumCode,
                                }),
                              onChange: (rawValue) =>
                                updateRowFields(tableName, actualRowIndex, {
                                  ...row,
                                  curriculum_system_code: curriculumCode,
                                  [field.name]: normalizeChangedValue(
                                    rawValue,
                                    isNumericRelationField(tableName, field.name) ? "number" : field.input_type
                                  ),
                                }),
                            })}
                            {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                          </div>
                        ))}
                      </div>

                      <div className="profile-form-row-side">
                        {!isVirtualRow ? (
                          <button
                            type="button"
                            className="secondary-btn"
                            onClick={() => handleRemoveRow(tableName, actualRowIndex)}
                          >
                            {"\u5220\u9664"}
                          </button>
                        ) : (
                          <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderCurriculumExamSection(curriculumCode) {
    const curriculumMeta = CURRICULUM_MODULES[curriculumCode];
    const examTableNames = (curriculumMeta?.standardizedTables || []).filter(
      (tableName) => archiveBundle?.form_meta?.tables?.[tableName]
    );
    if (examTableNames.length === 0) {
      return null;
    }
    const shouldCollapseExamSections = curriculumCode === "US_HIGH_SCHOOL";

    return (
      <div key={`exam-score-${curriculumCode}`} className="profile-curriculum-exam-group">
        <div className="profile-curriculum-exam-head">
          <h3 className="card-title">考试成绩</h3>
          <p>根据当前选择的课程体系展示对应考试成绩表单。</p>
        </div>

        <div className="profile-curriculum-section-stack">
          {examTableNames.map((examTableName) => {
            const examTableLabel =
              standardizedSelectorOptions.find((option) => option.value === examTableName)?.label ||
              archiveBundle?.form_meta?.tables?.[examTableName]?.label ||
              "考试成绩";

            if (CURRICULUM_SCOPED_TABLES.has(examTableName)) {
              return renderCurriculumScopedSection(examTableName, curriculumCode, {
                embedded: true,
                title: examTableLabel,
                showVirtualRowWhenEmpty: true,
                collapsible: shouldCollapseExamSections,
                sectionKey: `curriculum_exam_${curriculumCode}_${examTableName}`,
              });
            }

            return renderArchiveSection(examTableName, {
              embedded: true,
              title: examTableLabel,
              showDescription: false,
              showVirtualRowWhenEmpty: true,
              collapsible: shouldCollapseExamSections,
              sectionKey: `curriculum_exam_${curriculumCode}_${examTableName}`,
              onAddRow: () =>
                handleAddRow(examTableName, {
                  preserveVirtualRowWhenEmpty: true,
                }),
            });
          })}
        </div>
      </div>
    );
  }

  function renderCurriculumModule() {
    const tableName = "student_basic_info_curriculum_system";
    const tableMeta = archiveBundle?.form_meta?.tables?.[tableName];
    const visibleFields = getVisibleProfileFields(tableName, tableMeta?.fields);
    const actualRows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    const displayRows =
      actualRows.length > 0
        ? actualRows
        : [
            buildEmptyRow(tableMeta?.fields || [], {
              tableName,
              studentId: archiveStudentId,
              existingRowsCount: 0,
            }),
          ];
    const collapsed = isArchiveSectionCollapsed("curriculum_module");

    return (
      <div className={`card profile-curriculum-card ${collapsed ? "profile-form-card-collapsed" : ""}`}>
        {renderSectionHeader({
          title: "\u8bfe\u7a0b\u4f53\u7cfb",
          collapsed,
          onToggleCollapse: () => toggleArchiveSection("curriculum_module"),
          action: (
            <button type="button" className="secondary-btn" onClick={() => handleAddRow(tableName)}>
              {"\u65b0\u589e\u8bfe\u7a0b\u4f53\u7cfb"}
            </button>
          ),
          headingLevel: 2,
        })}

        {!collapsed ? (
          <>
            <div className="profile-form-stack">
              {displayRows.map((row, rowIndex) => {
                const isVirtualRow = actualRows.length === 0;
                const curriculumCode = row?.curriculum_system_code;
                const curriculumMeta = curriculumCode ? CURRICULUM_MODULES[curriculumCode] : null;

                return (
                  <div key={`curriculum-selector-${rowIndex}`} className="profile-form-array-row">
                    <div className="profile-form-row-inline">
                      <div className="profile-form-grid">
                        {visibleFields.map((field) => (
                          <div
                            key={`${tableName}-${rowIndex}-${field.name}`}
                            className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                          >
                            {renderFieldLabel(field)}
                            {renderFieldControl({
                              tableName,
                              field,
                              value: row?.[field.name],
                              onChange: (rawValue) =>
                                updateRowField(
                                  tableName,
                                  rowIndex,
                                  field.name,
                                  normalizeFieldChangedValue(
                                    tableName,
                                    field.name,
                                    rawValue,
                                    field.input_type
                                  )
                                ),
                            })}
                            {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                          </div>
                        ))}
                      </div>

                      <div className="profile-form-row-side">
                        {!isVirtualRow ? (
                          <button type="button" className="secondary-btn" onClick={() => handleRemoveRow(tableName, rowIndex)}>
                            {"\u5220\u9664"}
                          </button>
                        ) : (
                          <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                        )}
                      </div>
                    </div>

                    {curriculumMeta ? (
                      <div className="profile-curriculum-section-stack">
                        {(curriculumMeta.curriculumTables || []).map((curriculumTableName) => {
                          if (CURRICULUM_SCOPED_TABLES.has(curriculumTableName)) {
                            return renderCurriculumScopedSection(curriculumTableName, curriculumCode, {
                              embedded: true,
                              showVirtualRowWhenEmpty:
                                curriculumTableName === "student_academic_curriculum_gpa",
                            });
                          }
                          return renderArchiveSection(curriculumTableName, {
                            embedded: true,
                            showDescription: false,
                          });
                        })}
                        {renderCurriculumExamSection(curriculumCode)}
                      </div>
                    ) : null}
                  </div>
                  );
                })}
              </div>
            </>
          ) : null}
        </div>
      );
  }

  function renderLanguageDetailModule() {
    const recordTableMeta = archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_RECORD_TABLE];
    const scoreItemTableMeta = archiveBundle?.form_meta?.tables?.[LANGUAGE_TEST_SCORE_ITEM_TABLE];
    if (!recordTableMeta) {
      return null;
    }

    const collapsed = isArchiveSectionCollapsed("language_detail_module");
    const recordRows = Array.isArray(archiveFormState?.[LANGUAGE_TEST_RECORD_TABLE])
      ? archiveFormState[LANGUAGE_TEST_RECORD_TABLE]
      : [];
    const activeLanguageTestTypeCode =
      selectedLanguageTestTypeCode || languageTestTypeOptions[0]?.value || "";
    const recordVisibleFields = (recordTableMeta.fields || []).filter(
      (field) => !field.hidden && field.name !== "test_type_code"
    );
    const scoreItemVisibleFields = (scoreItemTableMeta?.fields || []).filter((field) => !field.hidden);
    const testTypeLabels = fieldOptionLabelMap?.[LANGUAGE_TEST_RECORD_TABLE]?.test_type_code || {};
    const scopedRecordRows = recordRows
      .map((row, actualRowIndex) => ({ row, actualRowIndex, isVirtualRow: false }))
      .filter(
        ({ row }) =>
          String(row?.test_type_code || "") === String(activeLanguageTestTypeCode || "")
      );
    const displayRecordRows =
      scopedRecordRows.length > 0
        ? scopedRecordRows
        : activeLanguageTestTypeCode
          ? [
              {
                row: buildEmptyRow(recordTableMeta.fields || [], {
                  tableName: LANGUAGE_TEST_RECORD_TABLE,
                  studentId: archiveStudentId,
                  existingRowsCount: recordRows.length,
                  initialValues: {
                    student_language_test_record_id: getNextTemporaryNumericId(
                      recordRows,
                      "student_language_test_record_id"
                    ),
                    test_type_code: activeLanguageTestTypeCode,
                  },
                }),
                actualRowIndex: recordRows.length,
                isVirtualRow: true,
              },
            ]
          : [];

    return (
      <div className={`card profile-language-card ${collapsed ? "profile-form-card-collapsed" : ""}`}>
        {renderSectionHeader({
          title: "\u8bed\u8a00\u8003\u8bd5",
          collapsed,
          onToggleCollapse: () => toggleArchiveSection("language_detail_module"),
          action: (
            <button
              type="button"
              className="secondary-btn"
              onClick={() =>
                handleAddLanguageTestRecordRow(activeLanguageTestTypeCode, {
                  preserveVirtualRowWhenEmpty: true,
                })
              }
              disabled={!activeLanguageTestTypeCode}
            >
              {"\u65b0\u589e\u4e00\u6761"}
            </button>
          ),
          headingLevel: 2,
        })}

        {!collapsed ? (
          <>
            {languageTestTypeOptions.length > 0 ? (
              <div className="profile-language-switcher">
                {languageTestTypeOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={`profile-language-switcher-button ${
                      String(option.value) === String(activeLanguageTestTypeCode)
                        ? "profile-language-switcher-button-active"
                        : ""
                    }`}
                    onClick={() => setSelectedLanguageTestTypeCode(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            ) : null}

            {displayRecordRows.length === 0 ? (
              <div className="profile-form-empty">请选择一种语言考试类型。</div>
            ) : null}

            <div className="profile-form-stack">
              {displayRecordRows.map(({ row, actualRowIndex: recordActualRowIndex, isVirtualRow }, rowIndex) => {
                const rowVisibleFields = recordVisibleFields
                  .map((field) =>
                    buildRenderableRowFieldMeta({
                      tableName: LANGUAGE_TEST_RECORD_TABLE,
                      field,
                      rowIndex: recordActualRowIndex,
                      row,
                      rows: displayRecordRows.map((item) => item.row),
                      archiveFormState,
                    })
                  )
                  .filter((field) => shouldRenderRowField(LANGUAGE_TEST_RECORD_TABLE, row, field));
                const recordId = row?.student_language_test_record_id;
                const scopedScoreRows = (Array.isArray(archiveFormState?.[LANGUAGE_TEST_SCORE_ITEM_TABLE])
                  ? archiveFormState[LANGUAGE_TEST_SCORE_ITEM_TABLE]
                  : []
                )
                  .map((item, scoreRowActualIndex) => ({
                    item,
                    actualRowIndex: scoreRowActualIndex,
                    isVirtualRow: false,
                  }))
                  .filter(
                    ({ item }) =>
                      String(item?.student_language_test_record_id ?? "") ===
                      String(recordId ?? "")
                  );
                const displayScoreRows =
                  scopedScoreRows.length > 0
                    ? scopedScoreRows
                    : [
                        {
                          item: buildEmptyRow(scoreItemTableMeta?.fields || [], {
                            tableName: LANGUAGE_TEST_SCORE_ITEM_TABLE,
                            initialValues: {
                              student_language_test_record_id: recordId,
                            },
                          }),
                          actualRowIndex: Array.isArray(archiveFormState?.[LANGUAGE_TEST_SCORE_ITEM_TABLE])
                            ? archiveFormState[LANGUAGE_TEST_SCORE_ITEM_TABLE].length
                            : 0,
                          isVirtualRow: true,
                        },
                      ];
                const recordTitleParts = [
                  formatOptionValue(testTypeLabels, activeLanguageTestTypeCode) || `考试记录 ${rowIndex + 1}`,
                  row?.exam_name_text || (displayRecordRows.length > 1 ? `记录 ${rowIndex + 1}` : null),
                ].filter(Boolean);

                return (
                  <div key={`${LANGUAGE_TEST_RECORD_TABLE}-${recordId ?? rowIndex}`} className="profile-form-array-row">
                    <div className="profile-form-array-head">
                      <div>
                        <h3 className="card-title">{recordTitleParts.join(" · ")}</h3>
                      </div>
                    </div>

                    <div className="profile-form-row-inline">
                      <div className="profile-form-grid">
                        {rowVisibleFields.map((field) => (
                          <div
                            key={`${LANGUAGE_TEST_RECORD_TABLE}-${recordActualRowIndex}-${field.name}`}
                            className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                          >
                            {renderFieldLabel(field)}
                            {renderFieldControl({
                              tableName: LANGUAGE_TEST_RECORD_TABLE,
                              field,
                              value: row?.[field.name],
                              row,
                              onRowPatch: (nextValues) =>
                                updateRowFields(LANGUAGE_TEST_RECORD_TABLE, recordActualRowIndex, {
                                  ...row,
                                  ...nextValues,
                                  test_type_code: activeLanguageTestTypeCode,
                                }),
                              onChange: (rawValue) =>
                                updateRowFields(LANGUAGE_TEST_RECORD_TABLE, recordActualRowIndex, {
                                  ...row,
                                  [field.name]: normalizeChangedValue(rawValue, field.input_type),
                                  test_type_code: activeLanguageTestTypeCode,
                                }),
                            })}
                            {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                          </div>
                        ))}
                      </div>

                      <div className="profile-form-row-side">
                        {!isVirtualRow ? (
                          <button
                            type="button"
                            className="secondary-btn"
                            onClick={() => handleRemoveRow(LANGUAGE_TEST_RECORD_TABLE, recordActualRowIndex)}
                          >
                            {"\u5220\u9664"}
                          </button>
                        ) : (
                          <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                        )}
                      </div>
                    </div>

                    {scoreItemTableMeta ? (
                      <div className="profile-embedded-section">
                        <div className="profile-form-array-head">
                          <div>
                            <h4 className="card-title">分项成绩</h4>
                          </div>
                          <button
                            type="button"
                            className="secondary-btn"
                            onClick={() =>
                              handleAddLanguageScoreItemRow(recordId, {
                                preserveVirtualRowWhenEmpty: true,
                              })
                            }
                          >
                            {"\u65b0\u589e\u5206\u9879"}
                          </button>
                        </div>

                        <div className="profile-form-stack">
                          {displayScoreRows.map(({ item, actualRowIndex: scoreActualRowIndex, isVirtualRow: isVirtualScoreRow }, scoreRowIndex) => {
                            const rowVisibleScoreFields = scoreItemVisibleFields
                              .map((field) =>
                                buildRenderableRowFieldMeta({
                                  tableName: LANGUAGE_TEST_SCORE_ITEM_TABLE,
                                  field,
                                  rowIndex: scoreRowIndex,
                                  row: item,
                                  rows: displayScoreRows.map(({ item: scoreItem }) => scoreItem),
                                  archiveFormState,
                                })
                              )
                              .filter((field) =>
                                shouldRenderRowField(LANGUAGE_TEST_SCORE_ITEM_TABLE, item, field)
                              );

                            return (
                              <div
                                key={`${LANGUAGE_TEST_SCORE_ITEM_TABLE}-${recordId ?? rowIndex}-${scoreRowIndex}`}
                                className="profile-form-array-row"
                              >
                                <div className="profile-form-row-inline">
                                  <div className="profile-form-grid">
                                    {rowVisibleScoreFields.map((field) => (
                                      <div
                                        key={`${LANGUAGE_TEST_SCORE_ITEM_TABLE}-${scoreActualRowIndex}-${field.name}`}
                                        className={`profile-form-field ${field.input_type === "checkbox" ? "profile-form-field-checkbox" : ""}`}
                                      >
                                        {renderFieldLabel(field)}
                                        {renderFieldControl({
                                          tableName: LANGUAGE_TEST_SCORE_ITEM_TABLE,
                                          field,
                                          value: item?.[field.name],
                                          onChange: (rawValue) =>
                                            handleUpsertLanguageScoreItemField({
                                              recordRow: row,
                                              recordRowIndex: recordActualRowIndex,
                                              recordId,
                                              isVirtualRecordRow: isVirtualRow,
                                              scoreRow: item,
                                              scoreRowIndex: scoreActualRowIndex,
                                              isVirtualScoreRow,
                                              fieldName: field.name,
                                              rawValue,
                                              inputType: field.input_type,
                                            }),
                                        })}
                                        {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                                      </div>
                                    ))}
                                  </div>

                                  <div className="profile-form-row-side">
                                    {!isVirtualScoreRow && displayScoreRows.length > 1 ? (
                                      <button
                                        type="button"
                                        className="secondary-btn"
                                        onClick={() => handleRemoveRow(LANGUAGE_TEST_SCORE_ITEM_TABLE, scoreActualRowIndex)}
                                      >
                                        {"\u5220\u9664"}
                                      </button>
                                    ) : (
                                      <span className="profile-form-row-side-placeholder" aria-hidden="true" />
                                    )}
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderArchivePanel() {
    const archiveRegenerateBusy = archiveSaving || archiveRegenerating || archiveDraftSyncing;
    const saveDisabled = !isArchiveDirty || archiveSaving;
    const regenerateDisabled = archiveRegenerateBusy;

    return (
      <div className="profile-content-stack profile-archive-panel">
        <div className="profile-page-head">
          <div>
            <h1 className="profile-page-title">{"\u6211\u7684\u6863\u6848"}</h1>
          </div>

          <div className="profile-page-head-actions">
            <button type="button" className="secondary-btn" onClick={() => navigate("/")}>
              {"\u8fd4\u56de\u9996\u9875"}
            </button>
            <button type="button" className="primary-btn" onClick={() => navigate("/")}>
              {"\u53bb\u7ee7\u7eed\u8865\u5145\u5bf9\u8bdd"}
            </button>
          </div>
        </div>

        {archiveLoading ? (
          <div className="profile-archive-loading">
            <div className="profile-archive-loading-spinner" />
            <span>{"\u6b63\u5728\u52a0\u8f7d\u6b63\u5f0f\u6863\u6848..."}</span>
          </div>
        ) : null}
        {archiveErrorMessage ? <div className="error-box">{archiveErrorMessage}</div> : null}

        {!archiveLoading && !archiveBundle ? (
          <div className="card profile-archive-empty">
            <h2 className="card-title">{"\u8fd8\u6ca1\u6709\u53ef\u67e5\u770b\u7684\u6863\u6848"}</h2>
            <p className="desc">{"\u8bf7\u5148\u56de\u9996\u9875\u901a\u8fc7 AI \u5efa\u6863\u52a9\u624b\u751f\u6210\u516d\u7ef4\u56fe\u5e76\u5b8c\u6210\u5efa\u6863\u3002"}</p>
            <div className="profile-empty-actions">
              <button type="button" className="primary-btn" onClick={() => navigate("/")}>
                {"\u53bb\u9996\u9875\u5efa\u6863"}
              </button>
              <button type="button" className="secondary-btn" onClick={fetchArchive}>
                {"\u91cd\u65b0\u68c0\u67e5"}
              </button>
            </div>
          </div>
        ) : null}

        {archiveBundle ? (
          <>
            <div className="card">
              <div className="profile-archive-top">
                <div>
                  <h2 className="card-title">{"\u5f53\u524d\u6863\u6848\u72b6\u6001"}</h2>
                </div>
                <div className="profile-archive-status">
                  <span className="profile-status-badge">{getArchiveStatusText(archiveBundle.result_status)}</span>
                </div>
              </div>

              {archiveBundle.save_error_message ? (
                <div className="profile-archive-warning">{"\u6b63\u5f0f\u6863\u6848\u4fdd\u5b58\u5f02\u5e38\uff1a"}{archiveBundle.save_error_message}</div>
              ) : null}

              <div className="profile-radar-top">
                <div className="profile-radar-visual-card">
                  <div className="profile-radar-visual-head">
                    <div>
                      <h3>{"\u5df2\u751f\u6210\u7684\u516d\u7ef4\u56fe"}</h3>
                    </div>
                  </div>

                  <div className="profile-radar-chart-wrap">
                    <ResponsiveContainer width="100%" height="100%">
                      <RadarChart data={radarChartData} outerRadius="68%">
                        <PolarGrid stroke="rgba(148,163,184,0.30)" />
                        <PolarAngleAxis dataKey="subject" tick={{ fill: "#1e3a8a", fontSize: 13 }} />
                        <PolarRadiusAxis
                          angle={30}
                          domain={[0, 100]}
                          tick={{ fill: "rgba(30,58,138,0.70)", fontSize: 11 }}
                          axisLine={false}
                        />
                        <Radar
                          dataKey="score"
                          stroke="#2c7be5"
                          fill="rgba(44,123,229,0.28)"
                          strokeWidth={2.5}
                          dot={{
                            r: 4,
                            fill: "#ffffff",
                            stroke: "#2c7be5",
                            strokeWidth: 2,
                          }}
                        />
                      </RadarChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                <div className="profile-summary-box">
                  <h3>{"\u7efc\u5408\u603b\u7ed3"}</h3>
                  <p>{archiveBundle.summary_text}</p>
                </div>
              </div>

              <div className="profile-radar-score-grid">
                {Object.entries(archiveBundle.radar_scores_json).map(([key, value]) => (
                  <div
                    key={key}
                    className="profile-radar-score-card"
                    style={{
                      "--score-from": (RADAR_COLORS[key] || RADAR_COLORS.academic).from,
                      "--score-to": (RADAR_COLORS[key] || RADAR_COLORS.academic).to,
                      "--score-bg": (RADAR_COLORS[key] || RADAR_COLORS.academic).bg,
                    }}
                  >
                    <div className="profile-radar-score-head">
                      <span>{RADAR_LABELS[key] || key}</span>
                      <strong>{value.score}</strong>
                    </div>
                    <div className="profile-radar-score-bar">
                      <div className="profile-radar-score-bar-fill" style={{ width: `${value.score}%` }} />
                    </div>
                    <p>{value.reason}</p>
                  </div>
                ))}
              </div>
            </div>

            {renderArchiveSection("student_basic_info", {
              title: "\u5b66\u751f\u57fa\u672c\u4fe1\u606f",
              collapsible: true,
              sectionKey: "student_basic_info",
            })}

            {renderTargetPreferenceCard()}

            {renderCurriculumModule()}

            {renderArchiveSection("student_academic", {
              title: "\u5b66\u672f\u4fe1\u606f",
              collapsible: true,
              sectionKey: "student_academic",
            })}

            {renderLanguageDetailModule()}

            {experienceDetailTableNames.map((tableName) =>
              renderArchiveSection(tableName, {
                collapsible: true,
                sectionKey: tableName,
                showVirtualRowWhenEmpty: true,
                onAddRow: () =>
                  handleAddRow(tableName, {
                    preserveVirtualRowWhenEmpty: true,
                  }),
              })
            )}

            {otherDetailTableNames.map((tableName) =>
              renderArchiveSection(tableName, {
                collapsible: true,
                sectionKey: tableName,
              })
            )}

            <div className="profile-floating-actions-spacer" />
            <div className="profile-floating-actions">
              <button
                type="button"
                className={`profile-floating-button profile-floating-button-save ${
                  saveDisabled ? "profile-floating-button-disabled" : "profile-floating-button-save-active"
                }`}
                disabled={saveDisabled}
                onClick={handleSaveArchiveForm}
              >
                {"\u4fdd\u5b58\u4fee\u6539"}
              </button>

              <button
                type="button"
                className={`profile-floating-button profile-floating-button-radar ${
                  archiveRegenerateBusy ? "profile-floating-button-loading" : ""
                }`}
                disabled={regenerateDisabled}
                onClick={handleRegenerateArchiveRadar}
              >
                {"\u91cd\u65b0\u751f\u6210\u516d\u7ef4\u56fe"}
              </button>
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderArchivePanelV2() {
    const archiveRegenerateBusy = archiveSaving || archiveRegenerating || archiveDraftSyncing;
    const saveDisabled = !isArchiveDirty || archiveSaving;
    const regenerateDisabled = archiveRegenerateBusy;
    const hasRadarResult = Boolean(archiveBundle?.has_radar_result);

    return (
      <div className="profile-content-stack profile-archive-panel">
        <div className="profile-page-head">
          <div>
            <h1 className="profile-page-title">我的档案</h1>
          </div>

          <div className="profile-page-head-actions">
            <button type="button" className="secondary-btn" onClick={() => navigate("/")}>
              返回首页
            </button>
            <button type="button" className="primary-btn" onClick={handleContinueSupplementConversation}>
              返回快速建档
            </button>
          </div>
        </div>

        {archiveLoading ? (
          <div className="profile-archive-loading">
            <InlineLoading message="正在准备正式档案" />
          </div>
        ) : null}
        {archiveErrorMessage ? <div className="error-box">{archiveErrorMessage}</div> : null}

        {!archiveLoading && !archiveBundle ? (
          <div className="card profile-archive-empty">
            <h2 className="card-title">档案暂不可用</h2>
            <p className="desc">当前档案加载失败，你可以稍后重试。</p>
            <div className="profile-empty-actions">
              <button type="button" className="primary-btn" onClick={fetchArchive}>
                重新加载
              </button>
            </div>
          </div>
        ) : null}

        {archiveBundle ? (
          <>
            <div className="card">
              <div className="profile-archive-top">
                <div>
                  <h2 className="card-title">当前档案状态</h2>
                </div>
                <div className="profile-archive-status">
                  <span className="profile-status-badge">{getCleanArchiveStatusText(archiveBundle.result_status)}</span>
                </div>
              </div>

              {archiveBundle.save_error_message ? (
                <div className="profile-archive-warning">正式档案保存异常：{archiveBundle.save_error_message}</div>
              ) : null}

              {hasRadarResult ? (
                <>
                  <div className="profile-radar-top">
                    <div className="profile-radar-visual-card">
                      <div className="profile-radar-visual-head">
                        <div>
                          <h3>已生成的六维图</h3>
                        </div>
                      </div>

                      <div className="profile-radar-chart-wrap">
                        <ResponsiveContainer width="100%" height="100%">
                          <RadarChart data={radarChartData} outerRadius="68%">
                            <PolarGrid stroke="rgba(148,163,184,0.30)" />
                            <PolarAngleAxis dataKey="subject" tick={{ fill: "#1e3a8a", fontSize: 13 }} />
                            <PolarRadiusAxis
                              angle={30}
                              domain={[0, 100]}
                              tick={{ fill: "rgba(30,58,138,0.70)", fontSize: 11 }}
                              axisLine={false}
                            />
                            <Radar
                              dataKey="score"
                              stroke="#2c7be5"
                              fill="rgba(44,123,229,0.28)"
                              strokeWidth={2.5}
                              dot={{
                                r: 4,
                                fill: "#ffffff",
                                stroke: "#2c7be5",
                                strokeWidth: 2,
                              }}
                            />
                          </RadarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>

                    <div className="profile-summary-box">
                      <h3>综合总结</h3>
                      <p>{archiveBundle.summary_text || "六维图生成后，这里会展示基于当前档案的综合总结。"}</p>
                    </div>
                  </div>

                  <div className="profile-radar-score-grid">
                    {Object.entries(archiveBundle.radar_scores_json).map(([key, value]) => (
                      <div
                        key={key}
                        className="profile-radar-score-card"
                        style={{
                          "--score-from": (RADAR_COLORS[key] || RADAR_COLORS.academic).from,
                          "--score-to": (RADAR_COLORS[key] || RADAR_COLORS.academic).to,
                          "--score-bg": (RADAR_COLORS[key] || RADAR_COLORS.academic).bg,
                        }}
                      >
                        <div className="profile-radar-score-head">
                          <span>{RADAR_LABELS[key] || key}</span>
                          <strong>{value.score}</strong>
                        </div>
                        <div className="profile-radar-score-bar">
                          <div className="profile-radar-score-bar-fill" style={{ width: `${value.score}%` }} />
                        </div>
                        <p>{value.reason}</p>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <div className="profile-radar-empty-state">
                  <h3>六维图还未生成</h3>
                  <p>你可以先填写正式档案，保存后再点击下方按钮生成六维图。</p>
                </div>
              )}
            </div>

            {renderArchiveSection("student_basic_info", {
              title: "学生基本信息",
              collapsible: true,
              sectionKey: "student_basic_info",
            })}

            {renderTargetPreferenceCard()}

            {renderCurriculumModule()}

            {renderArchiveSection("student_academic", {
              title: "学术信息",
              collapsible: true,
              sectionKey: "student_academic",
            })}

            {renderLanguageDetailModule()}

            {experienceDetailTableNames.map((tableName) =>
              renderArchiveSection(tableName, {
                collapsible: true,
                sectionKey: tableName,
                showVirtualRowWhenEmpty: true,
                onAddRow: () =>
                  handleAddRow(tableName, {
                    preserveVirtualRowWhenEmpty: true,
                  }),
              })
            )}

            {otherDetailTableNames.map((tableName) =>
              renderArchiveSection(tableName, {
                collapsible: true,
                sectionKey: tableName,
              })
            )}

            <div className="profile-floating-actions-spacer" />
            <div className="profile-floating-actions">
              {archiveMessage ? (
                <div className="profile-floating-feedback" title={archiveMessage} aria-label={archiveMessage}>
                  <CheckCircle2 size={20} strokeWidth={2.25} />
                </div>
              ) : null}
              {archiveSaveWarning ? (
                <div className="profile-floating-save-warning" role="status" aria-live="polite">
                  {archiveSaveWarning}
                </div>
              ) : null}
              <button
                type="button"
                className={`profile-floating-button profile-floating-button-save ${
                  saveDisabled ? "profile-floating-button-disabled" : "profile-floating-button-save-active"
                }`}
                disabled={saveDisabled}
                onClick={handleSaveArchiveForm}
              >
                保存修改
              </button>

              <button
                type="button"
                className={`profile-floating-button profile-floating-button-radar ${
                  archiveRegenerateBusy ? "profile-floating-button-loading" : ""
                }`}
                disabled={regenerateDisabled}
                onClick={handleRegenerateArchiveRadar}
              >
                重新生成六维图
              </button>
            </div>
          </>
        ) : null}
      </div>
    );
  }

  if (loading && !profile) {
    return null;
  }

  return (
    <div className="profile-shell">
      {profileBusyOverlay ? (
        <LoadingOverlay
          message={profileBusyOverlay.message}
          submessage={profileBusyOverlay.submessage}
        />
      ) : null}

      <aside className="profile-sidebar">
        <div className="profile-sidebar-card">
          <div className="profile-sidebar-avatar">{getDisplayInitial(profile?.nickname, profile?.mobile)}</div>
          <div className="profile-sidebar-name">{profile?.nickname || "\u672a\u8bbe\u7f6e\u6635\u79f0"}</div>
          <div className="profile-sidebar-sub">{profile?.mobile || "\u5df2\u767b\u5f55\u7528\u6237"}</div>
        </div>

        <div className="profile-sidebar-nav">
          <button
            type="button"
            className={`profile-sidebar-link ${activeTab === "account" ? "profile-sidebar-link-active" : ""}`}
            onClick={() => switchTab("account")}
          >
            {"\u7528\u6237\u4fe1\u606f"}
          </button>
          <button
            type="button"
            className={`profile-sidebar-link ${activeTab === "archive" ? "profile-sidebar-link-active" : ""}`}
            onClick={() => switchTab("archive")}
          >
            {"\u67e5\u770b\u6863\u6848"}
          </button>
          <button type="button" className="profile-sidebar-link" onClick={() => navigate("/")}>
            {"\u8fd4\u56de\u9996\u9875"}
          </button>
        </div>
      </aside>

      <main className="profile-main">
        {activeTab === "archive" ? renderArchivePanelV2() : renderAccountPanelV2()}
      </main>
    </div>
  );
}

