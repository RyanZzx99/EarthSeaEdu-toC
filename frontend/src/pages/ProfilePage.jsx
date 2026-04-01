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
  getAiChatArchiveForm,
  getCurrentAiChatSession,
  regenerateAiChatArchiveRadar,
  saveAiChatArchiveForm,
} from "../api/aiChat";

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 24;
const BCRYPT_PASSWORD_MAX_BYTES = 72;
const AI_CHAT_BIZ_DOMAIN = "student_profile_build";
const AI_CHAT_SESSION_CACHE_KEY = "latest_ai_chat_session_id";
const AI_CHAT_OPEN_PANEL_KEY = "open_ai_chat_panel";
const RADAR_LABELS = {
  academic: "学术成绩",
  language: "语言能力",
  standardized: "标化考试",
  competition: "学术竞赛",
  activity: "活动领导力",
  project: "项目实践",
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
    tables: ["student_academic_chinese_high_school_subject"],
  },
  A_LEVEL: {
    label: "A-Level",
    tables: ["student_academic_a_level_subject"],
  },
  AP: {
    label: "AP",
    tables: ["student_academic_ap_profile", "student_academic_ap_course"],
  },
  IB: {
    label: "IB",
    tables: ["student_academic_ib_profile", "student_academic_ib_subject"],
  },
};

const CURRICULUM_TABLE_NAMES = new Set(
  Object.values(CURRICULUM_MODULES).flatMap((item) => item.tables)
);

const LANGUAGE_DETAIL_TABLES = [
  "student_language_ielts",
  "student_language_toefl_ibt",
  "student_language_toefl_essentials",
  "student_language_det",
  "student_language_pte",
  "student_language_languagecert",
  "student_language_cambridge",
  "student_language_other",
];

const LANGUAGE_DETAIL_TABLE_NAME_SET = new Set(LANGUAGE_DETAIL_TABLES);

const NON_CONTENT_FIELD_NAMES = new Set([
  "student_id",
  "student_academic_id",
  "student_language_id",
  "student_standardized_test_id",
  "schema_version",
  "profile_type",
  "notes",
]);

const STANDARDIZED_ACT_FIELDS = new Set([
  "act_english",
  "act_math",
  "act_reading",
  "act_science",
]);

const STANDARDIZED_SAT_FIELDS = new Set(["sat_erw", "sat_math"]);

const SEARCHABLE_SELECT_FIELDS = {
  student_basic_info: new Set(["CTRY_CODE_VAL", "MAJ_CODE_VAL"]),
};

const UNIQUE_SUBJECT_SELECT_FIELD_BY_TABLE = {
  student_academic_a_level_subject: "al_subject_id",
  student_academic_ap_course: "ap_course_id",
  student_academic_ib_subject: "ib_subject_id",
  student_academic_chinese_high_school_subject: "chs_subject_id",
};

const ROW_TABLES_WITH_STUDENT_ID = new Set([
  "student_basic_info_curriculum_system",
  "student_academic_a_level_subject",
  "student_academic_ap_course",
  "student_academic_ib_subject",
  "student_academic_chinese_high_school_subject",
  "student_language_ielts",
  "student_language_toefl_ibt",
  "student_language_toefl_essentials",
  "student_language_det",
  "student_language_pte",
  "student_language_languagecert",
  "student_language_cambridge",
  "student_language_other",
  "student_standardized_test_records",
  "student_competition_entries",
  "student_activity_entries",
  "student_project_entries",
]);

function normalizeArchiveBundle(data) {
  const radarScores = data?.radar_scores_json || {};
  const hasRadarResult = Boolean(
    data?.result_status ||
      data?.summary_text ||
      Object.keys(radarScores).length > 0
  );

  return {
    session_id: data?.session_id || "",
    archive_form: data?.archive_form || {},
    form_meta: data?.form_meta || { table_order: [], tables: {} },
    result_status: data?.result_status || null,
    summary_text: data?.summary_text || "当前档案已保存，但还没有生成完整总结。",
    radar_scores_json: {
      academic: radarScores.academic || { score: 0, reason: "暂无有效学术评分说明" },
      language: radarScores.language || { score: 0, reason: "暂无有效语言评分说明" },
      standardized: radarScores.standardized || { score: 0, reason: "暂无有效标化评分说明" },
      competition: radarScores.competition || { score: 0, reason: "暂无有效竞赛评分说明" },
      activity: radarScores.activity || { score: 0, reason: "暂无有效活动评分说明" },
      project: radarScores.project || { score: 0, reason: "暂无有效项目评分说明" },
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
  return {
    ...normalizedBundle,
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
        field.options.map((option) => [String(option.value), option.label])
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

function buildArchiveOverview(archiveForm, optionLabelMap) {
  const basicInfo = archiveForm?.student_basic_info || {};
  const academic = archiveForm?.student_academic || {};
  const language = archiveForm?.student_language || {};
  const standardized = archiveForm?.student_standardized_tests || {};
  const curriculumCodeLabels = optionLabelMap?.student_basic_info_curriculum_system?.curriculum_system_code || {};
  const languageTypeLabels = optionLabelMap?.student_language?.best_test_type_code || {};
  const curriculumSystems = Array.isArray(archiveForm?.student_basic_info_curriculum_system)
    ? archiveForm.student_basic_info_curriculum_system
        .map((item) => item?.curriculum_system_code)
        .filter(Boolean)
        .map((item) => formatOptionValue(curriculumCodeLabels, item))
    : [];

  return [
    { label: "当前年级", value: basicInfo.current_grade || "未填写" },
    { label: "目标入学季", value: basicInfo.target_entry_term || "未填写" },
    { label: "课程体系", value: curriculumSystems.join("、") || "未填写" },
    { label: "学校名称", value: academic.school_name || "未填写" },
    { label: "所在城市", value: academic.school_city || "未填写" },
    { label: "最佳语言考试", value: formatOptionValue(languageTypeLabels, language.best_test_type_code) || "未填写" },
    { label: "最佳标化考试", value: standardized.best_test_type || "未填写" },
    { label: "竞赛条数", value: String((archiveForm?.student_competition_entries || []).length) },
    { label: "活动条数", value: String((archiveForm?.student_activity_entries || []).length) },
    { label: "项目条数", value: String((archiveForm?.student_project_entries || []).length) },
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

function getActiveCurriculumCodes(archiveForm) {
  // 中文注释：
  // 课程体系模块既要响应用户在“课程体系”里新选的值，
  // 也要自动兜住数据库里已经存在成绩数据的课程体系，避免用户必须先手动再选一次才能看到旧数据。
  const selectedCodes = new Set(
    (archiveForm?.student_basic_info_curriculum_system || [])
      .map((item) => item?.curriculum_system_code)
      .filter(Boolean)
  );

  Object.entries(CURRICULUM_MODULES).forEach(([curriculumCode, module]) => {
    const hasModuleData = module.tables.some((tableName) =>
      hasMeaningfulValue(archiveForm?.[tableName])
    );
    if (hasModuleData) {
      selectedCodes.add(curriculumCode);
    }
  });

  return Array.from(selectedCodes);
}

function buildEmptyRow(fields, { tableName = "", studentId = "" } = {}) {
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
  return row;
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

  if (tableName !== "student_standardized_test_records") {
    return true;
  }

  const testType = String(row?.test_type || "").toUpperCase();
  const status = String(row?.status || "").toUpperCase();

  // 中文注释：
  // 标化考试在“考试类型”还未选择时，只保留“考试类型”和“考试状态”，
  // 避免一上来就把 ACT/SAT 的分项字段和总分字段全部展开，表单会显得很乱。
  if (!testType) {
    return field.name === "test_type" || field.name === "status";
  }

  if (STANDARDIZED_ACT_FIELDS.has(field.name)) {
    return testType === "ACT";
  }
  if (STANDARDIZED_SAT_FIELDS.has(field.name)) {
    return testType === "SAT";
  }
  if (field.name === "total_score") {
    return status === "SCORED";
  }
  if (field.name === "estimated_total_score") {
    return status === "PLANNED" || status === "ESTIMATED";
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

function normalizeSearchText(value) {
  return String(value || "").trim().toLowerCase();
}

function isSearchableSelectField(tableName, fieldName) {
  return Boolean(SEARCHABLE_SELECT_FIELDS?.[tableName]?.has(fieldName));
}

function buildRenderableRowFieldMeta({ tableName, field, rowIndex, row, rows, archiveFormState }) {
  let normalizedField = field;

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
    if (!keyword) {
      return options;
    }
    return options.filter((option) => {
      const haystack = normalizeSearchText(`${option.label} ${option.value}`);
      return haystack.includes(keyword);
    });
  }, [options, query]);

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

function renderFieldControl({ tableName, field, value, onChange }) {
  if (field.hidden) {
    return null;
  }

  if (tableName === "student_academic" && field.name === "school_city") {
    return (
      <input
        className="profile-form-control"
        type="text"
        value={toInputValue(value, "text")}
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
    const options = Array.isArray(field.options) ? field.options : [];
    const hasCurrentValue = value !== null && value !== undefined && value !== "";
    const hasMatchedCurrentValue = hasCurrentValue
      ? options.some((option) => String(option.value) === String(value))
      : true;
    const normalizedOptions = hasMatchedCurrentValue
      ? options
      : [{ value, label: `当前值：${value}` }, ...options];

    if (isSearchableSelectField(tableName, field.name)) {
      return (
        <SearchableSelectControl
          options={normalizedOptions}
          value={value ?? ""}
          onChange={onChange}
          placeholder="请输入关键词搜索"
        />
      );
    }

    return (
      <select className="profile-form-control" value={value ?? ""} onChange={(event) => onChange(event.target.value)}>
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
        onChange={(event) => onChange(event.target.value)}
      />
    );
  }

  return (
    <input
      className="profile-form-control"
      type={field.input_type === "date" ? "date" : field.input_type === "number" ? "number" : "text"}
      step={field.input_type === "number" ? "any" : undefined}
      value={toInputValue(value, field.input_type)}
      onChange={(event) => onChange(event.target.value)}
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
  const [showPasswordEditor, setShowPasswordEditor] = useState(false);
  const [showForgotPasswordDialog, setShowForgotPasswordDialog] = useState(false);
  const [nicknameCheckMessage, setNicknameCheckMessage] = useState("");
  const [nicknameCheckAvailable, setNicknameCheckAvailable] = useState(false);
  const [passwordCheckMessage, setPasswordCheckMessage] = useState("");
  const [passwordCheckAvailable, setPasswordCheckAvailable] = useState(false);
  const [profile, setProfile] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
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
  const [archiveSessionId, setArchiveSessionId] = useState("");
  const [archiveBundle, setArchiveBundle] = useState(null);
  const [archiveFormState, setArchiveFormState] = useState({});
  const [archiveMessage, setArchiveMessage] = useState("");
  const [archiveErrorMessage, setArchiveErrorMessage] = useState("");
  const [activeLanguageDetailTable, setActiveLanguageDetailTable] = useState("");
  const [collapsedArchiveSections, setCollapsedArchiveSections] = useState({});
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
  const availableLanguageDetailTables = useMemo(
    () =>
      LANGUAGE_DETAIL_TABLES.filter((tableName) => archiveBundle?.form_meta?.tables?.[tableName]),
    [archiveBundle?.form_meta?.tables]
  );
  const preferredLanguageDetailTable = useMemo(
    () =>
      availableLanguageDetailTables.find((tableName) => hasMeaningfulValue(archiveBundle?.archive_form?.[tableName])) ||
      availableLanguageDetailTables[0] ||
      "",
    [availableLanguageDetailTables, archiveBundle?.archive_form]
  );
  const detailTableNames = useMemo(
    () =>
      (archiveBundle?.form_meta?.table_order || []).filter(
        (tableName) =>
          tableName !== "student_basic_info" &&
          tableName !== "student_basic_info_curriculum_system" &&
          tableName !== "student_academic" &&
          tableName !== "student_language" &&
          !LANGUAGE_DETAIL_TABLE_NAME_SET.has(tableName) &&
          !CURRICULUM_TABLE_NAMES.has(tableName)
      ),
    [archiveBundle?.form_meta?.table_order]
  );
  const archiveSectionKeys = useMemo(
    () => buildArchiveSectionKeys(detailTableNames, availableLanguageDetailTables.length > 0),
    [detailTableNames, availableLanguageDetailTables.length]
  );

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
    setActiveLanguageDetailTable((previous) => {
      if (previous && availableLanguageDetailTables.includes(previous)) {
        return previous;
      }
      return preferredLanguageDetailTable;
    });
  }, [availableLanguageDetailTables, preferredLanguageDetailTable]);

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

  function getUtf8ByteLength(value) {
    return new TextEncoder().encode(value).length;
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
    if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
      return "密码长度需为 8-24 位";
    }
    if (/\s/.test(value)) {
      return "密码不能包含空格";
    }
    const hasLetter = /[A-Za-z]/.test(value);
    const hasDigit = /\d/.test(value);
    const hasSpecial = /[^A-Za-z0-9]/.test(value);
    const categoryCount = [hasLetter, hasDigit, hasSpecial].filter(Boolean).length;
    if (categoryCount < 2) {
      return "密码至少需要包含字母、数字、特殊字符中的 2 种";
    }
    if (getUtf8ByteLength(value) > BCRYPT_PASSWORD_MAX_BYTES) {
      return "密码字节长度不能超过 72 bytes";
    }
    return "";
  }

  async function fetchProfile() {
    setErrorMessage("");
    try {
      setLoading(true);
      const response = await getMe();
      setProfile(response.data);
      setNicknameForm({ nickname: response.data.nickname || "" });
    } catch (error) {
      const detail = error?.response?.data?.detail || "获取用户信息失败";
      setErrorMessage(detail);
      if (error?.response?.status === 401) {
        localStorage.removeItem("access_token");
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
          const currentResponse = await getCurrentAiChatSession(AI_CHAT_BIZ_DOMAIN, {
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

      const response = await getAiChatArchiveForm(targetSessionId);
      const normalizedBundle = normalizeArchiveBundleForView(response.data);
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
      return;
      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, targetSessionId);

      if (requestedArchiveSessionId !== targetSessionId) {
        const nextSearchParams = new URLSearchParams(searchParams);
        nextSearchParams.set("tab", "archive");
        nextSearchParams.set("session_id", targetSessionId);
        setSearchParams(nextSearchParams, { replace: true });
      }
    } catch (error) {
      setArchiveBundle(null);
      setArchiveSessionId("");
      setArchiveFormState({});
      setArchiveErrorMessage(error?.response?.data?.detail || error?.message || "档案加载失败，请稍后重试。");
      return;
      if (error?.response?.status === 404) {
        setArchiveBundle(null);
        setArchiveSessionId("");
        setArchiveFormState({});
        setArchiveErrorMessage("当前还没有可查看的档案结果，请先回首页生成六维图并完成建档。");
      } else {
        setArchiveErrorMessage(error?.response?.data?.detail || "档案加载失败，请稍后重试。");
      }
    } finally {
      setArchiveLoading(false);
    }
  }

  function updateSingleField(tableName, fieldName, nextValue) {
    setArchiveMessage("");
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

  function handleAddRow(tableName) {
    const fields = archiveBundle?.form_meta?.tables?.[tableName]?.fields || [];
    setArchiveMessage("");
    setArchiveFormState((previous) => ({
      ...previous,
      [tableName]: [
        ...(Array.isArray(previous[tableName]) ? previous[tableName] : []),
        buildEmptyRow(fields, {
          tableName,
          studentId: previous?.student_basic_info?.student_id || archiveStudentId,
        }),
      ],
    }));
  }

  function handleRemoveRow(tableName, rowIndex) {
    setArchiveMessage("");
    setArchiveFormState((previous) => ({
      ...previous,
      [tableName]: (Array.isArray(previous[tableName]) ? previous[tableName] : []).filter((_, index) => index !== rowIndex),
    }));
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
    setArchiveErrorMessage("");
  }

  async function saveArchiveFormSnapshot({ silent = false } = {}) {
    if (!archiveSessionId) {
      throw new Error("当前缺少会话信息，暂时无法保存档案。");
    }

    const payloadArchiveForm = injectArchiveFormStudentIds(archiveFormState, archiveStudentId);
    const response = await saveAiChatArchiveForm(archiveSessionId, payloadArchiveForm);
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
    localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, archiveSessionId);

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
      setArchiveErrorMessage("");

      await saveArchiveFormSnapshot({ silent: true });
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
      setArchiveErrorMessage("请先保存修改，再重新生成六维图。");
      setArchiveMessage("");
      return;
    }

    try {
      setArchiveRegenerating(true);
      setArchiveMessage("");
      setArchiveErrorMessage("");

      const response = await regenerateAiChatArchiveRadar(archiveSessionId);
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

      if (!archiveSessionId) {
        throw new Error("当前缺少会话信息，暂时无法继续对话。");
      }

      if (isArchiveDirty) {
        setArchiveSaving(true);
        await saveArchiveFormSnapshot({ silent: true });
      }

      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, archiveSessionId);
      localStorage.setItem(AI_CHAT_OPEN_PANEL_KEY, "1");
      navigate("/");
    } catch (error) {
      setArchiveErrorMessage(error?.response?.data?.detail || error?.message || "继续对话前保存档案失败，请稍后重试。");
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
      localStorage.removeItem("access_token");
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

        {loading ? <div className="loading-box">正在加载用户信息...</div> : null}
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
                        {checkNicknameLoading ? "检查中..." : "检查昵称是否可用"}
                      </button>
                      <button type="button" className="primary-btn inline-btn" disabled={updateNicknameLoading} onClick={handleUpdateNickname}>
                        {updateNicknameLoading ? "保存中..." : "保存昵称"}
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
                    {checkPasswordLoading ? "检查中..." : "检查密码是否可用"}
                  </button>
                  <button type="button" className="primary-btn" disabled={setPasswordLoading} onClick={handleSetPassword}>
                    {setPasswordLoading ? "提交中..." : "保存密码"}
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
            {logoutLoading ? "退出中..." : "退出登录"}
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

        {loading ? <div className="loading-box">正在加载用户信息...</div> : null}
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
            </div>

            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <h2 className="card-title">基础资料</h2>
              </div>
              <div className="profile-account-meta-grid">
                <div className="info-item">
                  <span className="label">用户 ID</span>
                  <span className="value">{profile.user_id}</span>
                </div>
                <div className="info-item">
                  <span className="label">手机号</span>
                  <span className="value">{profile.mobile || "未绑定"}</span>
                </div>
              </div>
            </div>

            <div className="profile-account-section">
              <div className="profile-account-section-head">
                <h2 className="card-title">昵称</h2>
                {!showNicknameEditor ? (
                  <button type="button" className="secondary-btn inline-btn" onClick={() => setShowNicknameEditor(true)}>
                    修改昵称
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
                        {checkNicknameLoading ? "检查中..." : "检查昵称是否可用"}
                      </button>
                      <button type="button" className="primary-btn inline-btn" disabled={updateNicknameLoading} onClick={handleUpdateNickname}>
                        {updateNicknameLoading ? "保存中..." : "保存昵称"}
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
                      {checkPasswordLoading ? "检查中..." : "检查密码是否可用"}
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
                      {setPasswordLoading ? "提交中..." : "保存密码"}
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
                {logoutLoading ? "退出中..." : "退出登录"}
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
                    {forgotPasswordSendingCode
                      ? "发送中..."
                      : forgotPasswordCodeCountdown > 0
                        ? `${forgotPasswordCodeCountdown}s后重发`
                        : "获取验证码"}
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
                    {forgotPasswordCheckLoading ? "检查中..." : "检查密码是否可用"}
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
                    {forgotPasswordSaving ? "保存中..." : "保存修改"}
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
    } = options;

    const collapsed = collapsible && isArchiveSectionCollapsed(sectionKey);
    const visibleFields = (tableMeta.fields || []).filter(
      (field) => !field.hidden || (tableName === "student_project_outputs" && field.name === "project_id")
    );
    if (visibleFields.length === 0) {
      return null;
    }

    const wrapperClassName = embedded
      ? "profile-embedded-section"
      : `card profile-form-card ${collapsed ? "profile-form-card-collapsed" : ""}`;
    const singleDescription = description || "\u8fd9\u91cc\u5c55\u793a\u7684\u662f\u6b63\u5f0f\u6863\u6848\u4e3b\u8868\u4fe1\u606f\uff0c\u53ef\u76f4\u63a5\u4fee\u6539\u540e\u4fdd\u5b58\u3002";
    const arrayDescription = description || "\u53ef\u65b0\u589e\u3001\u5220\u9664\u548c\u4fee\u6539\u8fd9\u4e00\u7c7b\u660e\u7ec6\u6570\u636e\u3002";

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
                      updateSingleField(tableName, field.name, normalizeChangedValue(rawValue, field.input_type)),
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
    return (
      <div key={tableName} className={wrapperClassName}>
        {collapsible ? (
          renderSectionHeader({
            title,
            description: showDescription ? arrayDescription : null,
            collapsed,
            onToggleCollapse: () => toggleArchiveSection(sectionKey),
            action: (
              <button type="button" className="secondary-btn" onClick={() => handleAddRow(tableName)}>
                {addButtonLabel}
              </button>
            ),
          })
          ) : (
            <div className="profile-form-array-head">
              <div>
                <h3 className="card-title">{title}</h3>
              </div>
              <button type="button" className="secondary-btn" onClick={() => handleAddRow(tableName)}>
                {addButtonLabel}
            </button>
          </div>
        )}

        {!collapsed ? (
          <>
            {rows.length === 0 ? <div className="profile-form-empty">{"\u5f53\u524d\u6ca1\u6709\u6570\u636e\uff0c\u53ef\u4ee5\u70b9\u51fb\u201c\u65b0\u589e\u4e00\u6761\u201d\u3002"}</div> : null}

            <div className="profile-form-stack">
              {rows.map((row, rowIndex) => {
                const rowVisibleFields = visibleFields
                  .map((field) =>
                    buildRenderableRowFieldMeta({
                      tableName,
                      field,
                      rowIndex,
                      row,
                      rows,
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
                                onChange: (rawValue) =>
                                  updateRowField(
                                    tableName,
                                    rowIndex,
                                    field.name,
                                    normalizeChangedValue(
                                      rawValue,
                                      tableName === "student_project_outputs" && field.name === "project_id"
                                        ? "number"
                                        : field.input_type
                                    )
                                  ),
                              })}
                              {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                            </div>
                          );
                        })}
                      </div>

                      <div className="profile-form-row-side">
                        <button type="button" className="secondary-btn" onClick={() => handleRemoveRow(tableName, rowIndex)}>
                          {"\u5220\u9664"}
                        </button>
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

  function renderCurriculumModule() {
    const tableName = "student_basic_info_curriculum_system";
    const tableMeta = archiveBundle?.form_meta?.tables?.[tableName];
    const visibleFields = (tableMeta?.fields || []).filter((field) => !field.hidden);
    const actualRows = Array.isArray(archiveFormState?.[tableName]) ? archiveFormState[tableName] : [];
    const displayRows =
      actualRows.length > 0
        ? actualRows
        : [
            buildEmptyRow(tableMeta?.fields || [], {
              tableName,
              studentId: archiveStudentId,
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
                                  normalizeChangedValue(rawValue, field.input_type)
                                ),
                            })}
                            {field.helper_text ? <p className="profile-form-help">{field.helper_text}</p> : null}
                          </div>
                        ))}
                      </div>

                      {!isVirtualRow ? (
                        <div className="profile-form-row-side">
                          <button type="button" className="secondary-btn" onClick={() => handleRemoveRow(tableName, rowIndex)}>
                            {"\u5220\u9664"}
                          </button>
                        </div>
                      ) : null}
                    </div>

                    {curriculumMeta ? (
                      <div className="profile-curriculum-section-stack">
                        {curriculumMeta.tables.map((curriculumTableName) =>
                          renderArchiveSection(curriculumTableName, {
                            embedded: true,
                            showDescription: false,
                          })
                        )}
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
    if (availableLanguageDetailTables.length === 0 || !activeLanguageDetailTable) {
      return null;
    }

    const collapsed = isArchiveSectionCollapsed("language_detail_module");

      return (
        <div className={`card profile-language-card ${collapsed ? "profile-form-card-collapsed" : ""}`}>
          {renderSectionHeader({
            title: "\u8bed\u8a00\u8003\u8bd5",
            collapsed,
            onToggleCollapse: () => toggleArchiveSection("language_detail_module"),
            headingLevel: 2,
          })}

        {!collapsed ? (
          <>
            <div className="profile-language-switcher">
              {availableLanguageDetailTables.map((tableName) => (
                <button
                  key={tableName}
                  type="button"
                  className={`profile-language-switcher-button ${
                    activeLanguageDetailTable === tableName ? "profile-language-switcher-button-active" : ""
                  }`}
                  onClick={() => setActiveLanguageDetailTable(tableName)}
                >
                  {archiveBundle?.form_meta?.tables?.[tableName]?.label || tableName}
                </button>
              ))}
            </div>

            <div className="profile-language-panel">
              {renderArchiveSection(activeLanguageDetailTable, {
                embedded: true,
                showDescription: false,
              })}
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderArchivePanel() {
    const saveDisabled = !isArchiveDirty || archiveSaving || archiveRegenerating;
    const regenerateDisabled = archiveSaving || archiveRegenerating;

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

            {renderCurriculumModule()}

            {renderArchiveSection("student_academic", {
              title: "\u5b66\u672f\u4fe1\u606f",
              collapsible: true,
              sectionKey: "student_academic",
            })}

            {renderLanguageDetailModule()}

            {detailTableNames.map((tableName) =>
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
                {archiveSaving ? (
                  <>
                    <span className="profile-floating-button-spinner" />
                    {"\u6b63\u5728\u4fdd\u5b58..."}
                  </>
                ) : (
                  "\u4fdd\u5b58\u4fee\u6539"
                )}
              </button>

              <button
                type="button"
                className={`profile-floating-button profile-floating-button-radar ${
                  archiveRegenerating ? "profile-floating-button-loading" : ""
                }`}
                disabled={regenerateDisabled}
                onClick={handleRegenerateArchiveRadar}
              >
                {archiveRegenerating ? (
                  <>
                    <span className="profile-floating-button-spinner" />
                    {"\u6b63\u5728\u751f\u6210..."}
                  </>
                ) : (
                  "\u91cd\u65b0\u751f\u6210\u516d\u7ef4\u56fe"
                )}
              </button>
            </div>
          </>
        ) : null}
      </div>
    );
  }

  function renderArchivePanelV2() {
    const saveDisabled = !isArchiveDirty || archiveSaving || archiveRegenerating;
    const regenerateDisabled = archiveSaving || archiveRegenerating;
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
              智能建档
            </button>
          </div>
        </div>

        {archiveLoading ? (
          <div className="profile-archive-loading">
            <div className="profile-archive-loading-spinner" />
            <span>正在加载正式档案...</span>
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

            {renderCurriculumModule()}

            {renderArchiveSection("student_academic", {
              title: "学术信息",
              collapsible: true,
              sectionKey: "student_academic",
            })}

            {renderLanguageDetailModule()}

            {detailTableNames.map((tableName) =>
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
              <button
                type="button"
                className={`profile-floating-button profile-floating-button-save ${
                  saveDisabled ? "profile-floating-button-disabled" : "profile-floating-button-save-active"
                }`}
                disabled={saveDisabled}
                onClick={handleSaveArchiveForm}
              >
                {archiveSaving ? (
                  <>
                    <span className="profile-floating-button-spinner" />
                    正在保存...
                  </>
                ) : (
                  "保存修改"
                )}
              </button>

              <button
                type="button"
                className={`profile-floating-button profile-floating-button-radar ${
                  archiveRegenerating ? "profile-floating-button-loading" : ""
                }`}
                disabled={regenerateDisabled}
                onClick={handleRegenerateArchiveRadar}
              >
                {archiveRegenerating ? (
                  <>
                    <span className="profile-floating-button-spinner" />
                    正在生成...
                  </>
                ) : (
                  "重新生成六维图"
                )}
              </button>
            </div>
          </>
        ) : null}
      </div>
    );
  }

  return (
    <div className="profile-shell">
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
