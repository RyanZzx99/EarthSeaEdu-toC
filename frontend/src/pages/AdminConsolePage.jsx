import React, { useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  BadgeCheck,
  Database,
  FileCode2,
  KeyRound,
  Shield,
  Tags,
  Ticket,
  UserCog,
  Waves,
} from "lucide-react";
import {
  createNicknameContactPattern,
  createNicknameRuleGroup,
  createNicknameWordRule,
  generateInviteCodes,
  importQuestionBankBeta,
  issueInviteCode,
  listAiPromptConfigs,
  listAiRuntimeConfigs,
  listInviteCodes,
  listNicknameAuditLogs,
  listNicknameContactPatterns,
  listNicknameRuleGroups,
  listNicknameWordRules,
  listQuestionBanks,
  uploadQuestionBank,
  updateAiPromptConfig,
  updateAiRuntimeConfig,
  updateInviteCodeStatus,
  updateNicknameRuleTargetStatus,
  updateUserStatus,
} from "../api/auth";

const sectionGroups = [
  {
    key: "workspace",
    title: "总览",
    hint: "先查看工作台入口和密钥状态",
    items: [
      { key: "overview", label: "工作台概览", meta: "密钥输入与快捷刷新", sectionId: "admin-overview-cards" },
    ],
  },
  {
    key: "operations",
    title: "增长运营",
    hint: "邀请码和题库数据维护",
    items: [
      { key: "invite", label: "邀请码中心", meta: "生成、发放、查询与状态维护", sectionId: "admin-invite-center" },
      { key: "question-bank", label: "题库管理", meta: "JSON 题库上传与分页查看", sectionId: "admin-question-bank-center" },
    ],
  },
  {
    key: "governance",
    title: "风控治理",
    hint: "规则配置和命中回溯",
    items: [
      { key: "nickname", label: "昵称规则中心", meta: "分组、词条、联系方式规则", sectionId: "admin-nickname-center" },
      { key: "audit", label: "审核与回溯", meta: "命中日志与风险观察", sectionId: "admin-audit-center" },
    ],
  },
  {
    key: "system",
    title: "系统管理",
    hint: "账号和权限类基础维护",
    items: [
      { key: "system", label: "账号与权限", meta: "用户状态等基础控制", sectionId: "admin-system-center" },
    ],
  },
  {
    key: "ai-config",
    title: "AI 配置",
    hint: "模型运行时和 Prompt 配置",
    items: [
      { key: "runtime-config", label: "AI 模型配置", meta: "Base URL、默认模型、密钥与超时", sectionId: "admin-ai-runtime-config-center" },
      { key: "prompt", label: "AI 提示词中心", meta: "Prompt 配置查看与编辑", sectionId: "admin-ai-prompt-center" },
    ],
  },
];

const inviteStatusOptions = [
  { value: "1", label: "1（未使用）" },
  { value: "2", label: "2（已使用）" },
  { value: "3", label: "3（已禁用）" },
];
const inviteSceneOptions = [
  { value: "register", label: "注册邀请码" },
  { value: "teacher_portal", label: "教师邀请码" },
];

const groupTypeLabelMap = {
  reserved: "保留词（reserved）",
  black: "黑名单（black）",
  review: "人工复核（review）",
  whitelist: "白名单（whitelist）",
  contact: "联系方式（contact）",
};

const statusLabelMap = {
  draft: "草稿（draft）",
  active: "启用（active）",
  disabled: "停用（disabled）",
};

const matchTypeLabelMap = {
  contains: "包含（contains）",
  exact: "完全匹配（exact）",
  prefix: "前缀（prefix）",
  suffix: "后缀（suffix）",
  regex: "正则（regex）",
};

const decisionLabelMap = {
  reject: "拒绝（reject）",
  review: "复核（review）",
  pass: "放行（pass）",
};

const contactTypeLabelMap = {
  mobile: "手机号（mobile）",
  wechat: "微信（wechat）",
  qq: "QQ（qq）",
  email: "邮箱（email）",
  social: "社交平台（social）",
};

const riskLevelLabelMap = {
  low: "低（low）",
  medium: "中（medium）",
  high: "高（high）",
  critical: "严重（critical）",
};

const sceneLabelMap = {
  check: "检查（check）",
  update: "更新（update）",
};

const groupTypeOptions = ["reserved", "black", "review", "whitelist", "contact"];
const groupStatusOptions = ["draft", "active", "disabled"];
const wordMatchTypeOptions = ["contains", "exact", "prefix", "suffix", "regex"];
const wordDecisionOptions = ["reject", "review", "pass"];
const contactTypeOptions = ["mobile", "wechat", "qq", "email", "social"];
const contactDecisionOptions = ["reject", "review"];
const riskLevelOptions = ["low", "medium", "high", "critical"];
const questionBankStatusOptions = [
  { value: "1", label: "启用" },
  { value: "0", label: "停用" },
];
const questionBankContentMap = {
  IELTS: ["Listening", "Reading", "Speaking", "Writing"],
  SAT: ["Reading", "Writing", "Math"],
  ACT: ["English", "Math", "Reading", "Science", "Writing"],
  TOEFL: ["Listening", "Reading", "Speaking", "Writing"],
};
const examCategoryOptions = Object.keys(questionBankContentMap);
const importBetaModeOptions = [
  { value: "zip", label: "压缩包" },
  { value: "directory", label: "文件夹" },
  { value: "files", label: "文件批量" },
];

function validateMobile(mobile) {
  return /^1\d{10}$/.test(mobile);
}

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function formatInviteStatus(status) {
  const item = inviteStatusOptions.find((option) => option.value === status);
  return item?.label || status || "-";
}

function formatInviteScene(value) {
  return inviteSceneOptions.find((option) => option.value === value)?.label || value || "-";
}

function formatGroupType(value) {
  return groupTypeLabelMap[value] || value || "-";
}

function formatStatus(value) {
  return statusLabelMap[value] || value || "-";
}

function formatMatchType(value) {
  return matchTypeLabelMap[value] || value || "-";
}

function formatDecision(value) {
  return decisionLabelMap[value] || value || "-";
}

function formatContactType(value) {
  return contactTypeLabelMap[value] || value || "-";
}

function formatRiskLevel(value) {
  return riskLevelLabelMap[value] || value || "-";
}

function formatScene(value) {
  return sceneLabelMap[value] || value || "-";
}

function formatRuleTargetType(value) {
  if (value === "group") return "分组";
  if (value === "word") return "词条";
  if (value === "pattern") return "联系方式规则";
  return value || "-";
}

function formatQuestionBankStatus(value) {
  return questionBankStatusOptions.find((item) => item.value === value)?.label || value || "-";
}

function buildQuestionBankFileSummary(files) {
  if (!files.length) {
    return "";
  }

  if (files.length === 1) {
    return `已选择文件：${files[0].name}`;
  }

  const previewNames = files.slice(0, 4).map((item) => item.name).join("、");
  const suffix = files.length > 4 ? ` 等 ${files.length} 个文件` : "";
  return `已选择 ${files.length} 个文件：${previewNames}${suffix}`;
}

function buildImportBetaFileSummary(items) {
  if (!items.length) {
    return "";
  }

  if (items.length === 1) {
    return `已选择：${items[0].relativePath}`;
  }

  const previewNames = items.slice(0, 4).map((item) => item.relativePath).join("、");
  const suffix = items.length > 4 ? ` 等 ${items.length} 个文件` : "";
  return `已选择 ${items.length} 个文件：${previewNames}${suffix}`;
}

function createImportBetaItems(fileList) {
  return Array.from(fileList || []).map((file) => ({
    file,
    relativePath: file.webkitRelativePath || file.name,
  }));
}

function SectionNavButton({ item, isActive, onClick }) {
  return (
    <motion.button
      type="button"
      className="admin-nav-button"
      onClick={onClick}
      whileHover={{ background: isActive ? "#eef4ff" : "#f8fafc" }}
      whileTap={{ scale: 0.98 }}
      style={{
        color: isActive ? "#1a2744" : "#6b7280",
        background: isActive ? "#eef4ff" : "transparent",
      }}
    >
      <span className="admin-nav-copy">
        <span className="admin-nav-title">{item.label}</span>
        {item.meta ? <span className="admin-nav-meta">{item.meta}</span> : null}
      </span>
      {isActive ? (
        <motion.div
          layoutId="admin-nav-indicator"
          className="admin-nav-indicator"
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      ) : null}
    </motion.button>
  );
}

function AdminSection({ icon: Icon, title, subtitle, id, children, actions = null }) {
  return (
    <section className="admin-section" id={id}>
      <div className="home-section-head">
        <div className="home-section-icon">
          <Icon size={22} strokeWidth={2.1} />
        </div>
        <div className="admin-section-heading">
          <h2 className="home-section-title">{title}</h2>
          <p className="home-section-subtitle">{subtitle}</p>
        </div>
        {actions ? <div className="admin-section-actions">{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}

function AdminPanel({ title, description, children, aside = null }) {
  return (
    <div className="home-card admin-panel">
      <div className="admin-panel-head">
        <div>
          <h3 className="admin-panel-title">{title}</h3>
          {description ? <p className="admin-panel-subtitle">{description}</p> : null}
        </div>
        {aside}
      </div>
      {children}
    </div>
  );
}

function Field({ label, children, hint = null }) {
  return (
    <label className="field-group admin-field-group">
      <span className="field-label">{label}</span>
      {children}
      {hint ? <span className="field-tip">{hint}</span> : null}
    </label>
  );
}

function DataPlaceholder({ text }) {
  return <div className="admin-empty-state">{text}</div>;
}

function buildPromptEditorState(item) {
  if (!item) {
    return {
      id: "",
      prompt_key: "",
      biz_domain: "",
      prompt_stage: "",
      prompt_role: "",
      prompt_name: "",
      prompt_version: "",
      status: "draft",
      model_name: "",
      temperature: "",
      top_p: "",
      max_tokens: "",
      output_format: "text",
      prompt_content: "",
      remark: "",
      variables_json_text: "",
    };
  }
  return {
    id: item.id ?? "",
    prompt_key: item.prompt_key || "",
    biz_domain: item.biz_domain || "",
    prompt_stage: item.prompt_stage || "",
    prompt_role: item.prompt_role || "",
    prompt_name: item.prompt_name || "",
    prompt_version: item.prompt_version || "",
    status: item.status || "draft",
    model_name: item.model_name || "",
    temperature: item.temperature ?? "",
    top_p: item.top_p ?? "",
    max_tokens: item.max_tokens ?? "",
    output_format: item.output_format || "text",
    prompt_content: item.prompt_content || "",
    remark: item.remark || "",
    variables_json_text: item.variables_json == null ? "" : JSON.stringify(item.variables_json, null, 2),
  };
}

function buildRuntimeConfigEditorState(item) {
  if (!item) {
    return {
      id: "",
      config_group: "",
      config_key: "",
      config_name: "",
      config_value: "",
      effective_value_display: "",
      default_value_display: "",
      value_type: "string",
      is_secret: 0,
      status: "active",
      sort_order: 100,
      remark: "",
      has_override: false,
      using_default: true,
    };
  }
  return {
    id: item.id ?? "",
    config_group: item.config_group || "",
    config_key: item.config_key || "",
    config_name: item.config_name || "",
    config_value: item.config_value || "",
    effective_value_display: item.effective_value_display || "",
    default_value_display: item.default_value_display || "",
    value_type: item.value_type || "string",
    is_secret: item.is_secret ? 1 : 0,
    status: item.status || "active",
    sort_order: item.sort_order ?? 100,
    remark: item.remark || "",
    has_override: Boolean(item.has_override),
    using_default: Boolean(item.using_default),
  };
}

function GroupSelect({
  value,
  onChange,
  groups,
  placeholder = "全部分组",
  allowEmpty = true,
  optionLabel = (item) => `${item.group_name}（${item.group_code}）`,
}) {
  return (
    <select className="input" value={value} onChange={onChange}>
      {allowEmpty ? <option value="">{placeholder}</option> : null}
      {groups.map((item) => (
        <option key={item.id} value={item.id}>
          {optionLabel(item)}
        </option>
      ))}
    </select>
  );
}

export default function AdminConsolePage() {
  const [activeSection, setActiveSection] = useState("overview");
  const [adminKey, setAdminKey] = useState("");
  const [generateForm, setGenerateForm] = useState({
    count: 10,
    expires_days: "",
    note: "",
    invite_scene: "register",
  });
  const [issueForm, setIssueForm] = useState({ code: "", mobile: "" });
  const [queryForm, setQueryForm] = useState({
    status: "",
    mobile: "",
    code_keyword: "",
    invite_scene: "",
    limit: 50,
  });
  const [userStatusForm, setUserStatusForm] = useState({ user_id: "", mobile: "", status: "active" });
  const [questionBankUploadForm, setQuestionBankUploadForm] = useState({
    file_name: "",
    exam_category: "IELTS",
    exam_content: questionBankContentMap.IELTS[0],
    files: [],
  });
  const [questionBankImportBetaForm, setQuestionBankImportBetaForm] = useState({
    source_mode: "zip",
    items: [],
  });
  const [questionBankListQuery, setQuestionBankListQuery] = useState({
    page: 1,
    page_size: 10,
  });
  const [groupForm, setGroupForm] = useState({
    group_code: "",
    group_name: "",
    group_type: "reserved",
    status: "draft",
    priority: 100,
    description: "",
  });
  const [groupQueryForm, setGroupQueryForm] = useState({
    status: "",
    group_type: "",
  });
  const [wordRuleForm, setWordRuleForm] = useState({
    group_id: "",
    word: "",
    match_type: "contains",
    decision: "reject",
    status: "draft",
    priority: 100,
    risk_level: "medium",
    source: "manual",
    note: "",
  });
  const [contactPatternForm, setContactPatternForm] = useState({
    group_id: "",
    pattern_name: "",
    pattern_type: "mobile",
    pattern_regex: "",
    decision: "reject",
    status: "draft",
    priority: 100,
    risk_level: "high",
    normalized_hint: "",
    note: "",
  });
  const [wordRuleQueryForm, setWordRuleQueryForm] = useState({
    group_id: "",
    status: "",
    decision: "",
    keyword: "",
    limit: 50,
  });
  const [contactPatternQueryForm, setContactPatternQueryForm] = useState({
    group_id: "",
    status: "",
    pattern_type: "",
    keyword: "",
    limit: 50,
  });
  const [auditLogQueryForm, setAuditLogQueryForm] = useState({
    decision: "",
    scene: "",
    hit_group_code: "",
    limit: 50,
  });
  const [ruleStatusForm, setRuleStatusForm] = useState({ target_type: "group", target_id: "", status: "active" });
  const [lastUserStatusResult, setLastUserStatusResult] = useState({});
  const [lastRuleStatusResult, setLastRuleStatusResult] = useState({});
  const [questionBankUploadMessage, setQuestionBankUploadMessage] = useState("");
  const [questionBankImportBetaMessage, setQuestionBankImportBetaMessage] = useState("");
  const [questionBankImportBetaResult, setQuestionBankImportBetaResult] = useState(null);
  const [generatedItems, setGeneratedItems] = useState([]);
  const [lastIssued, setLastIssued] = useState({});
  const [inviteList, setInviteList] = useState([]);
  const [questionBankList, setQuestionBankList] = useState([]);
  const [queryTotal, setQueryTotal] = useState(0);
  const [questionBankTotal, setQuestionBankTotal] = useState(0);
  const [groupList, setGroupList] = useState([]);
  const [wordRuleList, setWordRuleList] = useState([]);
  const [contactPatternList, setContactPatternList] = useState([]);
  const [auditLogList, setAuditLogList] = useState([]);
  const [runtimeConfigList, setRuntimeConfigList] = useState([]);
  const [promptList, setPromptList] = useState([]);
  const [wordRuleTotal, setWordRuleTotal] = useState(0);
  const [contactPatternTotal, setContactPatternTotal] = useState(0);
  const [auditLogTotal, setAuditLogTotal] = useState(0);
  const [promptTotal, setPromptTotal] = useState(0);
  const [pendingStatusMap, setPendingStatusMap] = useState({});
  const [runtimeConfigEditor, setRuntimeConfigEditor] = useState(() => buildRuntimeConfigEditorState(null));
  const [runtimeConfigMessage, setRuntimeConfigMessage] = useState("");
  const [promptEditor, setPromptEditor] = useState(() => buildPromptEditorState(null));
  const [promptMessage, setPromptMessage] = useState("");
  const [loadingGenerate, setLoadingGenerate] = useState(false);
  const [loadingIssue, setLoadingIssue] = useState(false);
  const [loadingQuery, setLoadingQuery] = useState(false);
  const [loadingQuestionBankUpload, setLoadingQuestionBankUpload] = useState(false);
  const [loadingQuestionBankImportBeta, setLoadingQuestionBankImportBeta] = useState(false);
  const [loadingQuestionBankList, setLoadingQuestionBankList] = useState(false);
  const [loadingUserStatus, setLoadingUserStatus] = useState(false);
  const [loadingGroupCreate, setLoadingGroupCreate] = useState(false);
  const [loadingGroupList, setLoadingGroupList] = useState(false);
  const [loadingWordRuleCreate, setLoadingWordRuleCreate] = useState(false);
  const [loadingWordRuleList, setLoadingWordRuleList] = useState(false);
  const [loadingContactCreate, setLoadingContactCreate] = useState(false);
  const [loadingContactList, setLoadingContactList] = useState(false);
  const [loadingRuleStatus, setLoadingRuleStatus] = useState(false);
  const [loadingAuditLogs, setLoadingAuditLogs] = useState(false);
  const [loadingRuntimeConfigList, setLoadingRuntimeConfigList] = useState(false);
  const [loadingRuntimeConfigSave, setLoadingRuntimeConfigSave] = useState(false);
  const [loadingPromptList, setLoadingPromptList] = useState(false);
  const [loadingPromptSave, setLoadingPromptSave] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const generatedSummary = useMemo(() => generatedItems, [generatedItems]);
  const questionBankContentOptions = useMemo(
    () => questionBankContentMap[questionBankUploadForm.exam_category] || [],
    [questionBankUploadForm.exam_category]
  );
  const questionBankFileInputKey = questionBankUploadForm.files.length
    ? questionBankUploadForm.files.map((item) => `${item.name}-${item.lastModified}`).join("|")
    : "question-bank-empty";
  const questionBankImportBetaInputKey = questionBankImportBetaForm.items.length
    ? questionBankImportBetaForm.items
        .map((item) => `${item.relativePath}-${item.file.lastModified}-${item.file.size}`)
        .join("|")
    : "question-bank-import-beta-empty";
  const questionBankTotalPages = useMemo(() => {
    const pageSize = Math.max(1, Number(questionBankListQuery.page_size) || 10);
    return Math.max(1, Math.ceil(questionBankTotal / pageSize));
  }, [questionBankListQuery.page_size, questionBankTotal]);
  function ensureAdminKey() {
    if (!adminKey.trim()) {
      setErrorMessage("请输入管理员密钥");
      return false;
    }
    return true;
  }

  function scrollToSection(sectionId, key) {
    setActiveSection(key);
    const offset = 76;
    const element = document.getElementById(sectionId);
    if (!element) return;
    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - offset;
    window.scrollTo({ top: offsetPosition, behavior: "smooth" });
    if (key === "runtime-config" && adminKey.trim()) {
      handleListAiRuntimeConfigs();
    }
    if (key === "prompt" && adminKey.trim()) {
      handleListAiPrompts();
    }
    if (key === "question-bank" && adminKey.trim()) {
      handleListQuestionBanks(1);
    }
  }

  function getPendingStatus(code, currentStatus) {
    return pendingStatusMap[code] || currentStatus || "1";
  }

  function setPendingStatus(code, status) {
    setPendingStatusMap((previous) => ({ ...previous, [code]: status }));
  }

  async function handleGenerate() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!generateForm.count || generateForm.count < 1 || generateForm.count > 200) {
      setErrorMessage("生成数量必须在 1 到 200 之间");
      return;
    }
    try {
      setLoadingGenerate(true);
      const response = await generateInviteCodes(
        {
          count: Number(generateForm.count),
          expires_days: generateForm.expires_days ? Number(generateForm.expires_days) : null,
          note: generateForm.note || null,
          invite_scene: generateForm.invite_scene || "register",
        },
        adminKey
      );
      setGeneratedItems(response.data.items || []);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码生成失败");
    } finally {
      setLoadingGenerate(false);
    }
  }

  async function handleIssue() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!issueForm.code.trim()) {
      setErrorMessage("请输入邀请码");
      return;
    }
    if (!validateMobile(issueForm.mobile.trim())) {
      setErrorMessage("请输入正确的手机号");
      return;
    }
    try {
      setLoadingIssue(true);
      const response = await issueInviteCode(
        { code: issueForm.code.trim(), mobile: issueForm.mobile.trim() },
        adminKey
      );
      setLastIssued(response.data.item || response.data);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码发放失败");
    } finally {
      setLoadingIssue(false);
    }
  }

  async function handleQuery() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingQuery(true);
      const response = await listInviteCodes(
        {
          status: queryForm.status || undefined,
          mobile: queryForm.mobile || undefined,
          code_keyword: queryForm.code_keyword || undefined,
          invite_scene: queryForm.invite_scene || undefined,
          limit: queryForm.limit ? Number(queryForm.limit) : 50,
        },
        adminKey
      );
      const items = response.data.items || [];
      setInviteList(items);
      setQueryTotal(response.data.total || items.length);
      setPendingStatusMap(items.reduce((result, item) => ({ ...result, [item.code]: item.status }), {}));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码查询失败");
    } finally {
      setLoadingQuery(false);
    }
  }

  async function handleUpdateInviteStatus(item) {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      await updateInviteCodeStatus(
        { code: item.code, status: getPendingStatus(item.code, item.status) },
        adminKey
      );
      setInviteList((previous) =>
        previous.map((current) =>
          current.code === item.code ? { ...current, status: getPendingStatus(item.code, item.status) } : current
        )
      );
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "邀请码状态更新失败");
    }
  }

  function handleQuestionBankCategoryChange(nextCategory) {
    const nextOptions = questionBankContentMap[nextCategory] || [];
    setQuestionBankUploadMessage("");
    setQuestionBankUploadForm((previous) => ({
      ...previous,
      exam_category: nextCategory,
      exam_content: nextOptions[0] || "",
    }));
  }

  function handleQuestionBankFileChange(fileList) {
    setQuestionBankUploadMessage("");
    setQuestionBankUploadForm((previous) => ({
      ...previous,
      files: Array.from(fileList || []),
    }));
  }

  function handleQuestionBankImportBetaModeChange(nextMode) {
    setQuestionBankImportBetaMessage("");
    setQuestionBankImportBetaResult(null);
    setQuestionBankImportBetaForm({
      source_mode: nextMode,
      items: [],
    });
  }

  function handleQuestionBankImportBetaFileChange(fileList) {
    setQuestionBankImportBetaMessage("");
    setQuestionBankImportBetaResult(null);
    setQuestionBankImportBetaForm((previous) => ({
      ...previous,
      items: createImportBetaItems(fileList),
    }));
  }

  async function handleUploadQuestionBank() {
    setErrorMessage("");
    setQuestionBankUploadMessage("");
    if (!ensureAdminKey()) return;
    if (!questionBankUploadForm.files.length) {
      setErrorMessage("请先选择 JSON 文件");
      return;
    }

    try {
      setLoadingQuestionBankUpload(true);
      const selectedFiles = questionBankUploadForm.files;
      const titleValue = questionBankUploadForm.file_name.trim();
      const successItems = [];
      const failedItems = [];
      const failedFiles = [];

      for (let index = 0; index < selectedFiles.length; index += 1) {
        const currentFile = selectedFiles[index];
        const formData = new FormData();
        formData.append("file_name", selectedFiles.length === 1 ? titleValue : "");
        formData.append("exam_category", questionBankUploadForm.exam_category);
        formData.append("exam_content", questionBankUploadForm.exam_content);
        formData.append("file", currentFile);
        setQuestionBankUploadMessage(
          selectedFiles.length === 1
            ? `正在上传：${currentFile.name}`
            : `正在上传 ${index + 1} / ${selectedFiles.length}：${currentFile.name}`
        );

        try {
          await uploadQuestionBank(formData, adminKey);
          successItems.push(currentFile.name);
        } catch (error) {
          failedFiles.push(currentFile);
          failedItems.push(`${currentFile.name}：${error?.response?.data?.detail || "上传失败"}`);
        }
      }

      setQuestionBankUploadForm((previous) => ({
        ...previous,
        file_name: selectedFiles.length === 1 && failedFiles.length === 1 ? previous.file_name : "",
        files: failedFiles,
      }));

      if (successItems.length) {
        await handleListQuestionBanks(1);
      }

      if (!failedItems.length) {
        setQuestionBankUploadMessage(
          successItems.length > 1 ? `批量上传完成，成功 ${successItems.length} 个文件` : "题库上传成功"
        );
        return;
      }

      setQuestionBankUploadMessage(`批量上传完成，成功 ${successItems.length} 个，失败 ${failedItems.length} 个`);
      setErrorMessage(failedItems.join("；"));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "题库上传失败");
      setQuestionBankUploadMessage("");
    } finally {
      setLoadingQuestionBankUpload(false);
    }
  }

  async function handleImportQuestionBankBeta() {
    setErrorMessage("");
    setQuestionBankImportBetaMessage("");
    if (!ensureAdminKey()) return;
    if (!questionBankImportBetaForm.items.length) {
      setErrorMessage("请先选择要导入的文件");
      return;
    }

    try {
      setLoadingQuestionBankImportBeta(true);
      const formData = new FormData();
      formData.append("source_mode", questionBankImportBetaForm.source_mode);
      formData.append(
        "entry_paths_json",
        JSON.stringify(questionBankImportBetaForm.items.map((item) => item.relativePath))
      );
      questionBankImportBetaForm.items.forEach((item) => {
        formData.append("files", item.file);
      });

      setQuestionBankImportBetaMessage(`正在导入：${questionBankImportBetaForm.items.length} 个文件`);
      const response = await importQuestionBankBeta(formData, adminKey);
      setQuestionBankImportBetaResult(response.data);
      setQuestionBankImportBetaForm((previous) => ({
        ...previous,
        items: [],
      }));
      setQuestionBankImportBetaMessage(
        `导入完成：成功 ${response.data.success_count || 0} 个题包，失败 ${response.data.failure_count || 0} 个`
      );
    } catch (error) {
      setQuestionBankImportBetaResult(null);
      setQuestionBankImportBetaMessage("");
      setErrorMessage(error?.response?.data?.detail || "导入-测试版执行失败");
    } finally {
      setLoadingQuestionBankImportBeta(false);
    }
  }

  async function handleListQuestionBanks(page = questionBankListQuery.page) {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    const safePage = Math.max(1, Number(page) || 1);
    const safePageSize = Math.max(1, Number(questionBankListQuery.page_size) || 10);

    try {
      setLoadingQuestionBankList(true);
      const response = await listQuestionBanks(
        {
          page: safePage,
          page_size: safePageSize,
        },
        adminKey
      );
      const items = response.data.items || [];
      setQuestionBankList(items);
      setQuestionBankTotal(response.data.total || 0);
      setQuestionBankListQuery((previous) => ({
        ...previous,
        page: response.data.page || safePage,
        page_size: response.data.page_size || safePageSize,
      }));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "题库列表查询失败");
    } finally {
      setLoadingQuestionBankList(false);
    }
  }

  async function handleUpdateUserStatus() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!userStatusForm.user_id.trim() && !userStatusForm.mobile.trim()) {
      setErrorMessage("用户 ID 和手机号至少填写一个");
      return;
    }
    if (userStatusForm.mobile.trim() && !validateMobile(userStatusForm.mobile.trim())) {
      setErrorMessage("手机号格式不正确");
      return;
    }
    try {
      setLoadingUserStatus(true);
      const response = await updateUserStatus(
        {
          user_id: userStatusForm.user_id.trim() || null,
          mobile: userStatusForm.mobile.trim() || null,
          status: userStatusForm.status,
        },
        adminKey
      );
      setLastUserStatusResult(response.data.item || response.data);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "用户状态更新失败");
    } finally {
      setLoadingUserStatus(false);
    }
  }

  async function handleListGroups() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingGroupList(true);
      const response = await listNicknameRuleGroups(
        {
          status: groupQueryForm.status || undefined,
          group_type: groupQueryForm.group_type || undefined,
        },
        adminKey
      );
      setGroupList(response.data.items || []);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "规则分组查询失败");
    } finally {
      setLoadingGroupList(false);
    }
  }

  async function handleCreateGroup() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!groupForm.group_code.trim() || !groupForm.group_name.trim()) {
      setErrorMessage("分组编码和分组名称不能为空");
      return;
    }
    try {
      setLoadingGroupCreate(true);
      await createNicknameRuleGroup(
        {
          group_code: groupForm.group_code.trim(),
          group_name: groupForm.group_name.trim(),
          group_type: groupForm.group_type,
          status: groupForm.status,
          priority: Number(groupForm.priority),
          description: groupForm.description.trim() || null,
        },
        adminKey
      );
      await handleListGroups();
      setGroupForm((previous) => ({
        ...previous,
        group_code: "",
        group_name: "",
        description: "",
      }));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "规则分组创建失败");
    } finally {
      setLoadingGroupCreate(false);
    }
  }

  async function handleCreateWordRule() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!wordRuleForm.group_id || !wordRuleForm.word.trim()) {
      setErrorMessage("请选择分组并填写词条");
      return;
    }
    try {
      setLoadingWordRuleCreate(true);
      await createNicknameWordRule(
        {
          group_id: Number(wordRuleForm.group_id),
          word: wordRuleForm.word.trim(),
          match_type: wordRuleForm.match_type,
          decision: wordRuleForm.decision,
          status: wordRuleForm.status,
          priority: Number(wordRuleForm.priority),
          risk_level: wordRuleForm.risk_level,
          source: wordRuleForm.source,
          note: wordRuleForm.note.trim() || null,
        },
        adminKey
      );
      await handleListWordRules();
      setWordRuleForm((previous) => ({ ...previous, word: "", note: "" }));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "词条规则创建失败");
    } finally {
      setLoadingWordRuleCreate(false);
    }
  }

  async function handleListWordRules() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingWordRuleList(true);
      const response = await listNicknameWordRules(
        {
          group_id: wordRuleQueryForm.group_id ? Number(wordRuleQueryForm.group_id) : undefined,
          status: wordRuleQueryForm.status || undefined,
          decision: wordRuleQueryForm.decision || undefined,
          keyword: wordRuleQueryForm.keyword || undefined,
          limit: wordRuleQueryForm.limit ? Number(wordRuleQueryForm.limit) : 50,
        },
        adminKey
      );
      setWordRuleList(response.data.items || []);
      setWordRuleTotal(response.data.total || 0);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "词条规则查询失败");
    } finally {
      setLoadingWordRuleList(false);
    }
  }

  async function handleCreateContactPattern() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!contactPatternForm.pattern_name.trim() || !contactPatternForm.pattern_regex.trim()) {
      setErrorMessage("规则名称和正则表达式不能为空");
      return;
    }
    try {
      setLoadingContactCreate(true);
      await createNicknameContactPattern(
        {
          group_id: contactPatternForm.group_id ? Number(contactPatternForm.group_id) : null,
          pattern_name: contactPatternForm.pattern_name.trim(),
          pattern_type: contactPatternForm.pattern_type,
          pattern_regex: contactPatternForm.pattern_regex.trim(),
          decision: contactPatternForm.decision,
          status: contactPatternForm.status,
          priority: Number(contactPatternForm.priority),
          risk_level: contactPatternForm.risk_level,
          normalized_hint: contactPatternForm.normalized_hint.trim() || null,
          note: contactPatternForm.note.trim() || null,
        },
        adminKey
      );
      await handleListContactPatterns();
      setContactPatternForm((previous) => ({
        ...previous,
        pattern_name: "",
        pattern_regex: "",
        normalized_hint: "",
        note: "",
      }));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "联系方式规则创建失败");
    } finally {
      setLoadingContactCreate(false);
    }
  }

  async function handleListContactPatterns() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingContactList(true);
      const response = await listNicknameContactPatterns(
        {
          group_id: contactPatternQueryForm.group_id ? Number(contactPatternQueryForm.group_id) : undefined,
          status: contactPatternQueryForm.status || undefined,
          pattern_type: contactPatternQueryForm.pattern_type || undefined,
          keyword: contactPatternQueryForm.keyword || undefined,
          limit: contactPatternQueryForm.limit ? Number(contactPatternQueryForm.limit) : 50,
        },
        adminKey
      );
      setContactPatternList(response.data.items || []);
      setContactPatternTotal(response.data.total || 0);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "联系方式规则查询失败");
    } finally {
      setLoadingContactList(false);
    }
  }

  async function handleUpdateRuleStatus() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    if (!ruleStatusForm.target_id) {
      setErrorMessage("目标 ID 不能为空");
      return;
    }
    try {
      setLoadingRuleStatus(true);
      const response = await updateNicknameRuleTargetStatus(
        {
          target_type: ruleStatusForm.target_type,
          target_id: Number(ruleStatusForm.target_id),
          status: ruleStatusForm.status,
        },
        adminKey
      );
      setLastRuleStatusResult(response.data || {});
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "规则状态更新失败");
    } finally {
      setLoadingRuleStatus(false);
    }
  }

  function handleRuleTargetTypeChange(targetType) {
    setRuleStatusForm((previous) => ({
      ...previous,
      target_type: targetType,
      target_id: "",
    }));
  }

  async function handleListAuditLogs() {
    setErrorMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingAuditLogs(true);
      const response = await listNicknameAuditLogs(
        {
          decision: auditLogQueryForm.decision || undefined,
          scene: auditLogQueryForm.scene || undefined,
          hit_group_code: auditLogQueryForm.hit_group_code || undefined,
          limit: auditLogQueryForm.limit ? Number(auditLogQueryForm.limit) : 50,
        },
        adminKey
      );
      setAuditLogList(response.data.items || []);
      setAuditLogTotal(response.data.total || 0);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "审核日志查询失败");
    } finally {
      setLoadingAuditLogs(false);
    }
  }

  async function handleListAiRuntimeConfigs() {
    setErrorMessage("");
    setRuntimeConfigMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingRuntimeConfigList(true);
      const response = await listAiRuntimeConfigs(adminKey);
      const items = response.data.items || [];
      setRuntimeConfigList(items);
      setRuntimeConfigEditor((previous) => {
        if (!previous.config_key) return previous;
        const current = items.find((item) => item.config_key === previous.config_key);
        return current ? buildRuntimeConfigEditorState(current) : buildRuntimeConfigEditorState(null);
      });
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "AI 运行时配置查询失败");
    } finally {
      setLoadingRuntimeConfigList(false);
    }
  }

  function handleSelectRuntimeConfig(item) {
    setRuntimeConfigMessage("");
    setRuntimeConfigEditor(buildRuntimeConfigEditorState(item));
  }

  function handleRuntimeConfigEditorChange(field, value) {
    setRuntimeConfigMessage("");
    setRuntimeConfigEditor((previous) => ({ ...previous, [field]: value }));
  }

  async function handleSaveAiRuntimeConfig({ clearOverride = false } = {}) {
    setErrorMessage("");
    setRuntimeConfigMessage("");
    if (!ensureAdminKey()) return;
    if (!runtimeConfigEditor.config_key) {
      setErrorMessage("请先选择一条 AI 运行时配置");
      return;
    }
    try {
      setLoadingRuntimeConfigSave(true);
      const response = await updateAiRuntimeConfig(
        runtimeConfigEditor.config_key,
        {
          config_value: clearOverride ? null : runtimeConfigEditor.config_value,
          status: runtimeConfigEditor.status,
          remark: runtimeConfigEditor.remark.trim() || null,
          clear_override: clearOverride,
        },
        adminKey
      );
      const savedItem = response.data;
      setRuntimeConfigMessage(clearOverride ? "已恢复 .env 默认值" : "运行时配置保存成功");
      setRuntimeConfigList((previous) =>
        previous.map((item) => (item.config_key === savedItem.config_key ? savedItem : item))
      );
      setRuntimeConfigEditor(buildRuntimeConfigEditorState(savedItem));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "AI 运行时配置保存失败");
    } finally {
      setLoadingRuntimeConfigSave(false);
    }
  }

  async function handleListAiPrompts() {
    setErrorMessage("");
    setPromptMessage("");
    if (!ensureAdminKey()) return;
    try {
      setLoadingPromptList(true);
      const response = await listAiPromptConfigs({ limit: 200 }, adminKey);
      const items = response.data.items || [];
      setPromptList(items);
      setPromptTotal(response.data.total || items.length);
      setPromptEditor((previous) => {
        if (!previous.id) return previous;
        const current = items.find((item) => String(item.id) === String(previous.id));
        return current ? buildPromptEditorState(current) : buildPromptEditorState(null);
      });
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "Prompt 配置查询失败");
    } finally {
      setLoadingPromptList(false);
    }
  }

  function handleSelectPrompt(item) {
    setPromptMessage("");
    setPromptEditor(buildPromptEditorState(item));
  }

  function handlePromptEditorChange(field, value) {
    setPromptMessage("");
    setPromptEditor((previous) => ({ ...previous, [field]: value }));
  }

  async function handleSaveAiPrompt() {
    setErrorMessage("");
    setPromptMessage("");
    if (!ensureAdminKey()) return;
    if (!promptEditor.id) {
      setErrorMessage("请先选择一条 Prompt 配置");
      return;
    }
    if (!promptEditor.prompt_name.trim()) {
      setErrorMessage("Prompt 名称不能为空");
      return;
    }
    if (!promptEditor.prompt_version.trim()) {
      setErrorMessage("Prompt 版本不能为空");
      return;
    }
    if (!promptEditor.prompt_content.trim()) {
      setErrorMessage("Prompt 正文不能为空");
      return;
    }

    let parsedVariablesJson = null;
    if (promptEditor.variables_json_text.trim()) {
      try {
        parsedVariablesJson = JSON.parse(promptEditor.variables_json_text);
      } catch {
        setErrorMessage("variables_json 不是合法 JSON");
        return;
      }
    }

    try {
      setLoadingPromptSave(true);
      const response = await updateAiPromptConfig(
        promptEditor.id,
        {
          prompt_name: promptEditor.prompt_name.trim(),
          prompt_content: promptEditor.prompt_content,
          prompt_version: promptEditor.prompt_version.trim(),
          status: promptEditor.status,
          output_format: promptEditor.output_format.trim() || "text",
          model_name: promptEditor.model_name.trim() || null,
          temperature: promptEditor.temperature === "" ? null : Number(promptEditor.temperature),
          top_p: promptEditor.top_p === "" ? null : Number(promptEditor.top_p),
          max_tokens: promptEditor.max_tokens === "" ? null : Number(promptEditor.max_tokens),
          variables_json: parsedVariablesJson,
          remark: promptEditor.remark.trim() || null,
        },
        adminKey
      );
      const savedItem = response.data;
      setPromptMessage("Prompt 保存成功");
      setPromptList((previous) => previous.map((item) => (item.id === savedItem.id ? savedItem : item)));
      setPromptEditor(buildPromptEditorState(savedItem));
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "Prompt 保存失败");
    } finally {
      setLoadingPromptSave(false);
    }
  }

  return (
    <div className="admin-shell">
      <motion.header
        className="admin-topbar"
        initial={{ y: -18, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35 }}
      >
        <div className="home-topbar-inner">
          <div className="home-brand">
            <div className="home-brand-mark">控</div>
            <div className="home-brand-text">管理员控制台</div>
          </div>
          <div className="admin-top-status">
            <span className="admin-top-badge">运营后台</span>
          </div>
        </div>
      </motion.header>

      <main className="admin-main">
        <div className="home-content-wrap">
          {errorMessage ? <div className="error-box admin-global-alert">{errorMessage}</div> : null}
          <div className="admin-layout">
            <aside className="home-card admin-sidebar">
              <div className="admin-sidebar-head">
                <div className="admin-sidebar-eyebrow">后台导航</div>
                <h1 className="admin-sidebar-title">管理员控制台</h1>
                <p className="admin-sidebar-subtitle">按功能分区查看运营、风控、系统和 AI 配置能力。</p>
              </div>
              <div className="admin-sidebar-groups">
                {sectionGroups.map((group) => (
                  <div key={group.key} className="admin-sidebar-group">
                    <div className="admin-sidebar-group-head">
                      <div className="admin-sidebar-group-title">{group.title}</div>
                      <div className="admin-sidebar-group-hint">{group.hint}</div>
                    </div>
                    <div className="admin-sidebar-links">
                      {group.items.map((item) => (
                        <SectionNavButton
                          key={item.key}
                          item={item}
                          isActive={activeSection === item.key}
                          onClick={() => scrollToSection(item.sectionId, item.key)}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </aside>

            <div className="admin-content">

          <AdminSection
            id="admin-overview-cards"
            icon={Waves}
            title="工作台概览"
            subtitle="先完成管理员密钥输入和基础数据刷新，再进入对应业务分区。"
          >
            <div className="admin-summary-grid">
              <div className="home-card admin-summary-card">
                <div className="admin-summary-icon"><KeyRound size={20} /></div>
                <h3>管理员密钥</h3>
                <p>所有后台接口都通过 X-Admin-Key 鉴权，先在这里统一输入。</p>
                <input
                  className="input"
                  type="password"
                  value={adminKey}
                  onChange={(event) => setAdminKey(event.target.value)}
                  placeholder="请输入 X-Admin-Key"
                />
              </div>
              <div className="home-card admin-summary-card">
                <div className="admin-summary-icon"><Shield size={20} /></div>
                <h3>快捷刷新</h3>
                <p>刷新常用基础数据，便于后续下拉选择和编辑。</p>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGroupList} onClick={handleListGroups}>
                    {loadingGroupList ? "刷新中..." : "刷新规则分组"}
                  </button>
                  <button type="button" className="secondary-btn" disabled={loadingQuestionBankList} onClick={() => handleListQuestionBanks(1)}>
                    {loadingQuestionBankList ? "刷新中..." : "刷新题库列表"}
                  </button>
                  <button type="button" className="secondary-btn" disabled={loadingAuditLogs} onClick={handleListAuditLogs}>
                    {loadingAuditLogs ? "刷新中..." : "刷新审核日志"}
                  </button>
                </div>
              </div>
              <div className="home-card admin-summary-card">
                <div className="admin-summary-icon"><BadgeCheck size={20} /></div>
                <h3>快捷入口</h3>
                <p>常用操作直接跳转到对应分区。</p>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" onClick={() => scrollToSection("admin-question-bank-center", "question-bank")}>
                    进入题库管理
                  </button>
                  <button type="button" className="primary-btn" onClick={() => scrollToSection("admin-nickname-center", "nickname")}>
                    进入昵称规则中心
                  </button>
                  <button type="button" className="secondary-btn" onClick={() => scrollToSection("admin-ai-prompt-center", "prompt")}>
                    进入 AI 提示词中心
                  </button>
                </div>
              </div>
            </div>
          </AdminSection>

          <AdminSection
            id="admin-invite-center"
            icon={Ticket}
            title="邀请码中心"
            subtitle="邀请码生成、发放、查询和状态维护集中在一个分区。"
          >
            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="生成与发放" description="支持生成注册邀请码和教师邀请码；注册邀请码仍可按手机号发放。">
                <div className="admin-form-grid">
                  <Field label="邀请码用途">
                    <select
                      className="input"
                      value={generateForm.invite_scene}
                      onChange={(event) =>
                        setGenerateForm((previous) => ({ ...previous, invite_scene: event.target.value }))
                      }
                    >
                      {inviteSceneOptions.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="生成数量"><input className="input" type="number" min="1" max="200" value={generateForm.count} onChange={(event) => setGenerateForm((previous) => ({ ...previous, count: event.target.value }))} placeholder="1 到 200" /></Field>
                  <Field label="过期天数"><input className="input" type="number" min="1" max="3650" value={generateForm.expires_days} onChange={(event) => setGenerateForm((previous) => ({ ...previous, expires_days: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="批次备注"><input className="input" value={generateForm.note} onChange={(event) => setGenerateForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="例如：四月运营活动" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGenerate} onClick={handleGenerate}>
                    {loadingGenerate
                      ? "生成中..."
                      : generateForm.invite_scene === "teacher_portal"
                        ? "生成教师邀请码"
                        : "生成注册邀请码"}
                  </button>
                </div>
                <div className="admin-divider" />
                <div className="admin-form-grid">
                  <Field label="邀请码"><input className="input" value={issueForm.code} onChange={(event) => setIssueForm((previous) => ({ ...previous, code: event.target.value }))} placeholder="输入待发放的邀请码" /></Field>
                  <Field label="目标手机号"><input className="input" value={issueForm.mobile} onChange={(event) => setIssueForm((previous) => ({ ...previous, mobile: event.target.value }))} placeholder="11 位手机号" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingIssue} onClick={handleIssue}>{loadingIssue ? "发放中..." : "发放邀请码"}</button>
                </div>
              </AdminPanel>

              <AdminPanel title="最近生成与发放结果" description="展示最近一次生成批次和最近一次发放结果。">
                {generatedSummary.length ? (
                  <div className="list">
                    {generatedSummary.map((item) => (
                      <div key={item.code} className="list-item">
                        <span className="code">{item.code}</span>
                        <span className="meta">用途：{formatInviteScene(item.invite_scene)}</span>
                        <span className="meta">状态：{formatInviteStatus(item.status)}</span>
                        <span className="meta">过期：{formatDate(item.expires_time)}</span>
                      </div>
                    ))}
                  </div>
                ) : <DataPlaceholder text="还没有生成记录，先执行一次邀请码生成。" />}
                <div className="admin-divider" />
                {lastIssued.code ? (
                  <div className="admin-detail-list">
                    <div><strong>邀请码：</strong>{lastIssued.code}</div>
                    <div><strong>用途：</strong>{formatInviteScene(lastIssued.invite_scene)}</div>
                    <div><strong>状态：</strong>{formatInviteStatus(lastIssued.status)}</div>
                    <div><strong>手机号：</strong>{lastIssued.issued_to_mobile || "-"}</div>
                    <div><strong>发放时间：</strong>{formatDate(lastIssued.issued_time)}</div>
                  </div>
                ) : <DataPlaceholder text="还没有发放记录，发放后会显示在这里。" />}
              </AdminPanel>
            </div>

            <div className="admin-workbench-grid">
              <AdminPanel title="邀请码查询与状态维护" description="支持按状态、手机号、关键字查询，并在列表里直接维护状态。">
                <div className="admin-filter-grid">
                  <Field label="邀请码用途">
                    <select
                      className="input"
                      value={queryForm.invite_scene}
                      onChange={(event) => setQueryForm((previous) => ({ ...previous, invite_scene: event.target.value }))}
                    >
                      <option value="">全部用途</option>
                      {inviteSceneOptions.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="状态"><select className="input" value={queryForm.status} onChange={(event) => setQueryForm((previous) => ({ ...previous, status: event.target.value }))}><option value="">全部状态</option>{inviteStatusOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></Field>
                  <Field label="手机号"><input className="input" value={queryForm.mobile} onChange={(event) => setQueryForm((previous) => ({ ...previous, mobile: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="邀请码关键字"><input className="input" value={queryForm.code_keyword} onChange={(event) => setQueryForm((previous) => ({ ...previous, code_keyword: event.target.value }))} placeholder="支持模糊匹配" /></Field>
                  <Field label="返回条数"><input className="input" type="number" min="1" max="200" value={queryForm.limit} onChange={(event) => setQueryForm((previous) => ({ ...previous, limit: event.target.value }))} /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingQuery} onClick={handleQuery}>{loadingQuery ? "查询中..." : "查询邀请码"}</button>
                </div>
                {inviteList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {queryTotal} 条</div>
                    <table className="table">
                      <thead><tr><th>邀请码</th><th>用途</th><th>状态</th><th>发放手机号</th><th>使用用户</th><th>发放时间</th><th>过期时间</th><th>操作</th></tr></thead>
                      <tbody>
                        {inviteList.map((item) => (
                          <tr key={item.code}>
                            <td>{item.code}</td>
                            <td>{formatInviteScene(item.invite_scene)}</td>
                            <td>{formatInviteStatus(item.status)}</td>
                            <td>{item.issued_to_mobile || "-"}</td>
                            <td>{item.used_by_user_id || "-"}</td>
                            <td>{formatDate(item.issued_time)}</td>
                            <td>{formatDate(item.expires_time)}</td>
                            <td>
                              <div className="admin-inline-editor">
                                <select className="inline-select" value={getPendingStatus(item.code, item.status)} onChange={(event) => setPendingStatus(item.code, event.target.value)}>
                                  {inviteStatusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                                </select>
                                <button type="button" className="inline-action" onClick={() => handleUpdateInviteStatus(item)}>更新</button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text="还没有邀请码查询结果。" />}
              </AdminPanel>
            </div>
          </AdminSection>

          <AdminSection
            id="admin-question-bank-center"
            icon={Database}
            title="题库管理"
            subtitle="上传 JSON 题库到数据库，并按分页查看已入库的题库记录。"
            actions={<button type="button" className="secondary-btn" disabled={loadingQuestionBankList} onClick={() => handleListQuestionBanks(1)}>{loadingQuestionBankList ? "加载中..." : "刷新题库列表"}</button>}
          >
            <div className="admin-section-stack">
              <AdminPanel
                title="上传题库"
                description="支持单个或多个 JSON 文件上传。单个文件可自定义标题，批量上传时自动使用各文件名。"
                aside={questionBankUploadMessage ? <span className="check-success">{questionBankUploadMessage}</span> : null}
              >
                <div className="admin-form-grid">
                  <Field
                    label="标题"
                    hint={
                      questionBankUploadForm.files.length > 1
                        ? "批量上传时会忽略这里的标题，自动使用每个文件名入库"
                        : "留空则使用上传文件名"
                    }
                  >
                    <input
                      className="input"
                      value={questionBankUploadForm.file_name}
                      onChange={(event) => {
                        setQuestionBankUploadMessage("");
                        setQuestionBankUploadForm((previous) => ({ ...previous, file_name: event.target.value }));
                      }}
                      placeholder="例如：IELTS 听力真题 01"
                    />
                  </Field>
                  <Field label="考试类别">
                    <select className="input" value={questionBankUploadForm.exam_category} onChange={(event) => handleQuestionBankCategoryChange(event.target.value)}>
                      {examCategoryOptions.map((item) => <option key={item} value={item}>{item}</option>)}
                    </select>
                  </Field>
                  <Field label="考试内容">
                    <select
                      className="input"
                      value={questionBankUploadForm.exam_content}
                      onChange={(event) => {
                        setQuestionBankUploadMessage("");
                        setQuestionBankUploadForm((previous) => ({ ...previous, exam_content: event.target.value }));
                      }}
                    >
                      {questionBankContentOptions.map((item) => <option key={item} value={item}>{item}</option>)}
                    </select>
                  </Field>
                  <Field label="JSON 文件">
                    <input
                      key={questionBankFileInputKey}
                      className="input"
                      type="file"
                      multiple
                      accept=".json,application/json"
                      onChange={(event) => handleQuestionBankFileChange(event.target.files)}
                    />
                  </Field>
                </div>
                {questionBankUploadForm.files.length ? (
                  <div className="result-box">
                    {buildQuestionBankFileSummary(questionBankUploadForm.files)}
                  </div>
                ) : null}
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingQuestionBankUpload} onClick={handleUploadQuestionBank}>
                    {loadingQuestionBankUpload ? "批量上传中..." : "上传题库"}
                  </button>
                </div>
              </AdminPanel>

              <AdminPanel
                title="导入-测试版"
                description="按新 IELTS 结构化方案导入压缩包、文件夹或批量文件，落到 exam_* 表。旧 JSON 上传功能继续保留。"
                aside={questionBankImportBetaMessage ? <span className="check-success">{questionBankImportBetaMessage}</span> : null}
              >
                <div className="admin-segmented">
                  {importBetaModeOptions.map((item) => (
                    <button
                      key={item.value}
                      type="button"
                      className={`admin-segmented-btn ${questionBankImportBetaForm.source_mode === item.value ? "active" : ""}`}
                      onClick={() => handleQuestionBankImportBetaModeChange(item.value)}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>

                <div className="admin-form-grid">
                  <Field label="导入模式">
                    <input
                      className="input"
                      value={
                        importBetaModeOptions.find((item) => item.value === questionBankImportBetaForm.source_mode)?.label || "压缩包"
                      }
                      disabled
                    />
                  </Field>

                  <Field
                    label="导入文件"
                    hint={
                      questionBankImportBetaForm.source_mode === "zip"
                        ? "支持一个或多个 zip 包，zip 内部可包含多套题库。"
                        : questionBankImportBetaForm.source_mode === "directory"
                          ? "请选择包含 manifest.json 的根目录，浏览器会自动携带相对路径。"
                          : "支持批量文件，若涉及多套同名 section 文件，优先改用压缩包或文件夹模式。"
                    }
                  >
                    {questionBankImportBetaForm.source_mode === "zip" ? (
                      <input
                        key={questionBankImportBetaInputKey}
                        className="input"
                        type="file"
                        multiple
                        accept=".zip,application/zip"
                        onChange={(event) => handleQuestionBankImportBetaFileChange(event.target.files)}
                      />
                    ) : null}

                    {questionBankImportBetaForm.source_mode === "directory" ? (
                      <input
                        key={questionBankImportBetaInputKey}
                        className="input"
                        type="file"
                        multiple
                        webkitdirectory=""
                        directory=""
                        onChange={(event) => handleQuestionBankImportBetaFileChange(event.target.files)}
                      />
                    ) : null}

                    {questionBankImportBetaForm.source_mode === "files" ? (
                      <input
                        key={questionBankImportBetaInputKey}
                        className="input"
                        type="file"
                        multiple
                        onChange={(event) => handleQuestionBankImportBetaFileChange(event.target.files)}
                      />
                    ) : null}
                  </Field>
                </div>

                {questionBankImportBetaForm.items.length ? (
                  <div className="result-box">
                    {buildImportBetaFileSummary(questionBankImportBetaForm.items)}
                  </div>
                ) : null}

                <div className="admin-button-row">
                  <button
                    type="button"
                    className="primary-btn"
                    disabled={loadingQuestionBankImportBeta}
                    onClick={handleImportQuestionBankBeta}
                  >
                    {loadingQuestionBankImportBeta ? "导入中..." : "开始导入-测试版"}
                  </button>
                </div>

                {questionBankImportBetaResult ? (
                  <div className="admin-import-result">
                    <div className="admin-detail-list">
                      <div><strong>识别 manifest：</strong>{questionBankImportBetaResult.manifest_count || 0}</div>
                      <div><strong>成功题包：</strong>{questionBankImportBetaResult.success_count || 0}</div>
                      <div><strong>失败题包：</strong>{questionBankImportBetaResult.failure_count || 0}</div>
                      <div><strong>导入试卷：</strong>{questionBankImportBetaResult.imported_paper_count || 0}</div>
                      <div><strong>Section / Group / Question：</strong>{questionBankImportBetaResult.imported_section_count || 0} / {questionBankImportBetaResult.imported_group_count || 0} / {questionBankImportBetaResult.imported_question_count || 0}</div>
                      <div><strong>Answer / Blank / Option / Asset：</strong>{questionBankImportBetaResult.imported_answer_count || 0} / {questionBankImportBetaResult.imported_blank_count || 0} / {questionBankImportBetaResult.imported_option_count || 0} / {questionBankImportBetaResult.imported_asset_count || 0}</div>
                    </div>

                    {questionBankImportBetaResult.items?.length ? (
                      <div className="admin-import-result-block">
                        <h4>成功明细</h4>
                        <div className="admin-import-result-list">
                          {questionBankImportBetaResult.items.map((item) => (
                            <div key={`${item.paper_code}-${item.package_root}`} className="admin-import-result-item">
                              <strong>{item.paper_name}</strong>
                              <span>{item.subject_type} / {item.import_status}</span>
                              <span>{item.bank_code}</span>
                              <span>{item.package_root}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}

                    {questionBankImportBetaResult.failures?.length ? (
                      <div className="admin-import-result-block">
                        <h4>失败明细</h4>
                        <div className="admin-import-result-list">
                          {questionBankImportBetaResult.failures.map((item, index) => (
                            <div key={`${item.package_root}-${index}`} className="admin-import-result-item error">
                              <strong>{item.package_root}</strong>
                              <span>{item.message}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : null}
                  </div>
                ) : null}
              </AdminPanel>

              <AdminPanel title="题库列表" description="默认每页展示 10 条，按创建时间倒序排列。">
                {questionBankList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {questionBankTotal} 条，当前第 {questionBankListQuery.page} / {questionBankTotalPages} 页</div>
                    <table className="table">
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>标题</th>
                          <th>考试类别</th>
                          <th>考试内容</th>
                          <th>状态</th>
                          <th>创建时间</th>
                          <th>更新时间</th>
                        </tr>
                      </thead>
                      <tbody>
                        {questionBankList.map((item) => (
                          <tr key={item.id}>
                            <td>{item.id}</td>
                            <td>{item.file_name}</td>
                            <td>{item.exam_category}</td>
                            <td>{item.exam_content}</td>
                            <td>{formatQuestionBankStatus(item.status)}</td>
                            <td>{formatDate(item.create_time)}</td>
                            <td>{formatDate(item.update_time)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <div className="admin-pagination">
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={loadingQuestionBankList || questionBankListQuery.page <= 1}
                        onClick={() => handleListQuestionBanks(questionBankListQuery.page - 1)}
                      >
                        上一页
                      </button>
                      <span className="admin-pagination-copy">第 {questionBankListQuery.page} / {questionBankTotalPages} 页</span>
                      <button
                        type="button"
                        className="secondary-btn"
                        disabled={loadingQuestionBankList || questionBankListQuery.page >= questionBankTotalPages}
                        onClick={() => handleListQuestionBanks(questionBankListQuery.page + 1)}
                      >
                        下一页
                      </button>
                    </div>
                  </div>
                ) : <DataPlaceholder text={adminKey.trim() ? "还没有题库数据，先上传一份 JSON 题库或点击刷新列表。" : "先输入管理员密钥，再刷新题库列表。"} />}
              </AdminPanel>
            </div>
          </AdminSection>

          <AdminSection
            id="admin-nickname-center"
            icon={Tags}
            title="昵称规则中心"
            subtitle="把分组、词条、联系方式规则和状态管理集中在一个运营分区。"
            actions={<button type="button" className="secondary-btn" disabled={loadingGroupList} onClick={handleListGroups}>{loadingGroupList ? "刷新中..." : "刷新分组列表"}</button>}
          >
            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="规则分组管理" description="新建风控分组，并查看当前已启用或草稿中的分组。">
                <div className="admin-form-grid">
                  <Field label="分组编码"><input className="input" value={groupForm.group_code} onChange={(event) => setGroupForm((previous) => ({ ...previous, group_code: event.target.value }))} placeholder="例如：contact_wechat" /></Field>
                  <Field label="分组名称"><input className="input" value={groupForm.group_name} onChange={(event) => setGroupForm((previous) => ({ ...previous, group_name: event.target.value }))} placeholder="例如：微信导流" /></Field>
                  <Field label="分组类型"><select className="input" value={groupForm.group_type} onChange={(event) => setGroupForm((previous) => ({ ...previous, group_type: event.target.value }))}>{groupTypeOptions.map((item) => <option key={item} value={item}>{formatGroupType(item)}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={groupForm.status} onChange={(event) => setGroupForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={groupForm.priority} onChange={(event) => setGroupForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="分组说明"><input className="input" value={groupForm.description} onChange={(event) => setGroupForm((previous) => ({ ...previous, description: event.target.value }))} placeholder="说明这个分组的运营用途" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGroupCreate} onClick={handleCreateGroup}>{loadingGroupCreate ? "创建中..." : "创建规则分组"}</button>
                </div>
                <div className="admin-divider" />
                <div className="admin-filter-grid">
                  <Field label="查询状态">
                    <select className="input" value={groupQueryForm.status} onChange={(event) => setGroupQueryForm((previous) => ({ ...previous, status: event.target.value }))}>
                      <option value="">全部状态</option>
                      {groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}
                    </select>
                  </Field>
                  <Field label="查询类型">
                    <select className="input" value={groupQueryForm.group_type} onChange={(event) => setGroupQueryForm((previous) => ({ ...previous, group_type: event.target.value }))}>
                      <option value="">全部类型</option>
                      {groupTypeOptions.map((item) => <option key={item} value={item}>{formatGroupType(item)}</option>)}
                    </select>
                  </Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="secondary-btn" disabled={loadingGroupList} onClick={handleListGroups}>{loadingGroupList ? "查询中..." : "查询分组列表"}</button>
                </div>
                {groupList.length ? (
                  <div className="table-wrap">
                    <table className="table">
                      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th>状态</th><th>优先级</th></tr></thead>
                      <tbody>
                        {groupList.map((item) => (
                          <tr key={item.id}>
                            <td>{item.id}</td>
                            <td>{item.group_code}</td>
                            <td>{item.group_name}</td>
                            <td>{formatGroupType(item.group_type)}</td>
                            <td>{formatStatus(item.status)}</td>
                            <td>{item.priority}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text="还没有分组数据，先刷新或创建分组。" />}
              </AdminPanel>

              <AdminPanel title="规则启停" description="统一维护分组、词条、联系方式规则的状态。">
                <div className="admin-form-grid">
                  <Field label="目标类型"><select className="input" value={ruleStatusForm.target_type} onChange={(event) => handleRuleTargetTypeChange(event.target.value)}><option value="group">分组（group）</option><option value="word">词条（word）</option><option value="pattern">联系方式规则（pattern）</option></select></Field>
                  {ruleStatusForm.target_type === "group" ? (
                    <Field label="分组编码">
                      <GroupSelect
                        value={ruleStatusForm.target_id}
                        onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, target_id: event.target.value }))}
                        groups={groupList}
                        placeholder="请选择分组编码"
                        allowEmpty={false}
                        optionLabel={(item) => `${item.group_code}（${item.group_name}）`}
                      />
                    </Field>
                  ) : (
                    <Field label="目标 ID"><input className="input" type="number" min="1" value={ruleStatusForm.target_id} onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, target_id: event.target.value }))} placeholder="输入表中的目标 ID" /></Field>
                  )}
                  <Field label="更新状态"><select className="input" value={ruleStatusForm.status} onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingRuleStatus} onClick={handleUpdateRuleStatus}>{loadingRuleStatus ? "更新中..." : "更新规则状态"}</button>
                </div>
                {lastRuleStatusResult.id ? (
                  <div className="result-box">
                    已更新 {formatRuleTargetType(ruleStatusForm.target_type)} ID {lastRuleStatusResult.id}，当前状态：{formatStatus(lastRuleStatusResult.status)}
                  </div>
                ) : <DataPlaceholder text="状态更新结果会显示在这里。" />}
              </AdminPanel>
            </div>

            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="词条规则" description="使用下拉分组选择，避免继续手工输入 group_id。">
                <div className="admin-form-grid">
                  <Field label="所属分组"><GroupSelect value={wordRuleForm.group_id} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} placeholder="请选择分组" allowEmpty={false} /></Field>
                  <Field label="词条"><input className="input" value={wordRuleForm.word} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, word: event.target.value }))} placeholder="输入标准词条" /></Field>
                  <Field label="匹配方式"><select className="input" value={wordRuleForm.match_type} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, match_type: event.target.value }))}>{wordMatchTypeOptions.map((item) => <option key={item} value={item}>{formatMatchType(item)}</option>)}</select></Field>
                  <Field label="命中决策"><select className="input" value={wordRuleForm.decision} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, decision: event.target.value }))}>{wordDecisionOptions.map((item) => <option key={item} value={item}>{formatDecision(item)}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={wordRuleForm.status} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={wordRuleForm.priority} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="风险等级"><select className="input" value={wordRuleForm.risk_level} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, risk_level: event.target.value }))}>{riskLevelOptions.map((item) => <option key={item} value={item}>{formatRiskLevel(item)}</option>)}</select></Field>
                  <Field label="备注"><input className="input" value={wordRuleForm.note} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="可留空" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingWordRuleCreate} onClick={handleCreateWordRule}>{loadingWordRuleCreate ? "创建中..." : "创建词条规则"}</button>
                </div>
                <div className="admin-divider" />
                <div className="admin-filter-grid">
                  <Field label="分组筛选"><GroupSelect value={wordRuleQueryForm.group_id} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} /></Field>
                  <Field label="状态"><select className="input" value={wordRuleQueryForm.status} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, status: event.target.value }))}><option value="">全部状态</option>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                  <Field label="决策"><select className="input" value={wordRuleQueryForm.decision} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, decision: event.target.value }))}><option value="">全部决策</option>{wordDecisionOptions.map((item) => <option key={item} value={item}>{formatDecision(item)}</option>)}</select></Field>
                  <Field label="关键字"><input className="input" value={wordRuleQueryForm.keyword} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, keyword: event.target.value }))} placeholder="词条或备注关键字" /></Field>
                  <Field label="返回条数"><input className="input" type="number" min="1" max="200" value={wordRuleQueryForm.limit} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, limit: event.target.value }))} /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="secondary-btn" disabled={loadingWordRuleList} onClick={handleListWordRules}>{loadingWordRuleList ? "查询中..." : "查询词条规则"}</button>
                </div>
                {wordRuleList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {wordRuleTotal} 条</div>
                    <table className="table">
                      <thead><tr><th>ID</th><th>分组</th><th>词条</th><th>标准词</th><th>匹配</th><th>决策</th><th>状态</th><th>优先级</th></tr></thead>
                      <tbody>
                        {wordRuleList.map((item) => (
                          <tr key={item.id}>
                            <td>{item.id}</td>
                            <td>{item.group_id}</td>
                            <td>{item.word}</td>
                            <td>{item.normalized_word}</td>
                            <td>{formatMatchType(item.match_type)}</td>
                            <td>{formatDecision(item.decision)}</td>
                            <td>{formatStatus(item.status)}</td>
                            <td>{item.priority}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text="还没有词条规则查询结果。" />}
              </AdminPanel>

              <AdminPanel title="联系方式规则" description="手机号、微信、QQ、邮箱和站外社交规则统一在这里管理。">
                <div className="admin-form-grid">
                  <Field label="所属分组"><GroupSelect value={contactPatternForm.group_id} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} /></Field>
                  <Field label="规则名称"><input className="input" value={contactPatternForm.pattern_name} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, pattern_name: event.target.value }))} placeholder="例如：中国大陆手机号" /></Field>
                  <Field label="规则类型"><select className="input" value={contactPatternForm.pattern_type} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, pattern_type: event.target.value }))}>{contactTypeOptions.map((item) => <option key={item} value={item}>{formatContactType(item)}</option>)}</select></Field>
                  <Field label="正则表达式"><input className="input" value={contactPatternForm.pattern_regex} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, pattern_regex: event.target.value }))} placeholder="输入正则" /></Field>
                  <Field label="命中决策"><select className="input" value={contactPatternForm.decision} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, decision: event.target.value }))}>{contactDecisionOptions.map((item) => <option key={item} value={item}>{formatDecision(item)}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={contactPatternForm.status} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={contactPatternForm.priority} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="风险等级"><select className="input" value={contactPatternForm.risk_level} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, risk_level: event.target.value }))}>{riskLevelOptions.map((item) => <option key={item} value={item}>{formatRiskLevel(item)}</option>)}</select></Field>
                  <Field label="归一化说明"><input className="input" value={contactPatternForm.normalized_hint} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, normalized_hint: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="备注"><input className="input" value={contactPatternForm.note} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="可留空" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingContactCreate} onClick={handleCreateContactPattern}>{loadingContactCreate ? "创建中..." : "创建联系方式规则"}</button>
                </div>
                <div className="admin-divider" />
                <div className="admin-filter-grid">
                  <Field label="分组筛选"><GroupSelect value={contactPatternQueryForm.group_id} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} /></Field>
                  <Field label="状态"><select className="input" value={contactPatternQueryForm.status} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, status: event.target.value }))}><option value="">全部状态</option>{groupStatusOptions.map((item) => <option key={item} value={item}>{formatStatus(item)}</option>)}</select></Field>
                  <Field label="规则类型"><select className="input" value={contactPatternQueryForm.pattern_type} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, pattern_type: event.target.value }))}><option value="">全部类型</option>{contactTypeOptions.map((item) => <option key={item} value={item}>{formatContactType(item)}</option>)}</select></Field>
                  <Field label="关键字"><input className="input" value={contactPatternQueryForm.keyword} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, keyword: event.target.value }))} placeholder="规则名或备注关键字" /></Field>
                  <Field label="返回条数"><input className="input" type="number" min="1" max="200" value={contactPatternQueryForm.limit} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, limit: event.target.value }))} /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="secondary-btn" disabled={loadingContactList} onClick={handleListContactPatterns}>{loadingContactList ? "查询中..." : "查询联系方式规则"}</button>
                </div>
                {contactPatternList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {contactPatternTotal} 条</div>
                    <table className="table">
                      <thead><tr><th>ID</th><th>分组</th><th>名称</th><th>类型</th><th>决策</th><th>状态</th><th>优先级</th><th>正则</th></tr></thead>
                      <tbody>
                        {contactPatternList.map((item) => (
                          <tr key={item.id}>
                            <td>{item.id}</td>
                            <td>{item.group_id || "-"}</td>
                            <td>{item.pattern_name}</td>
                            <td>{formatContactType(item.pattern_type)}</td>
                            <td>{formatDecision(item.decision)}</td>
                            <td>{formatStatus(item.status)}</td>
                            <td>{item.priority}</td>
                            <td>{item.pattern_regex}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text="还没有联系方式规则查询结果。" />}
              </AdminPanel>
            </div>
          </AdminSection>

          <AdminSection id="admin-audit-center" icon={Shield} title="审核与回溯" subtitle="聚焦昵称风控命中结果，便于排查误杀和观察近期风险。">
            <div className="admin-workbench-grid">
              <AdminPanel title="审核日志查询" description="按决策、场景、规则分组快速筛选最近的命中记录。">
                <div className="admin-filter-grid">
                  <Field label="结果"><select className="input" value={auditLogQueryForm.decision} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, decision: event.target.value }))}><option value="">全部结果</option><option value="pass">{formatDecision("pass")}</option><option value="reject">{formatDecision("reject")}</option><option value="review">{formatDecision("review")}</option></select></Field>
                  <Field label="场景"><select className="input" value={auditLogQueryForm.scene} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, scene: event.target.value }))}><option value="">全部场景</option><option value="check">{formatScene("check")}</option><option value="update">{formatScene("update")}</option></select></Field>
                  <Field label="规则分组编码"><input className="input" value={auditLogQueryForm.hit_group_code} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, hit_group_code: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="返回条数"><input className="input" type="number" min="1" max="200" value={auditLogQueryForm.limit} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, limit: event.target.value }))} /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingAuditLogs} onClick={handleListAuditLogs}>{loadingAuditLogs ? "查询中..." : "查询审核日志"}</button>
                </div>
                {auditLogList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {auditLogTotal} 条</div>
                    <table className="table">
                      <thead><tr><th>ID</th><th>场景</th><th>原始昵称</th><th>结果</th><th>命中分组</th><th>命中内容</th><th>提示</th><th>时间</th></tr></thead>
                      <tbody>
                        {auditLogList.map((item) => (
                          <tr key={item.id}>
                            <td>{item.id}</td>
                            <td>{formatScene(item.scene)}</td>
                            <td>{item.raw_nickname}</td>
                            <td>{formatDecision(item.decision)}</td>
                            <td>{item.hit_group_code || "-"}</td>
                            <td>{item.hit_content || "-"}</td>
                            <td>{item.message || "-"}</td>
                            <td>{formatDate(item.create_time)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text="还没有审核日志查询结果。" />}
              </AdminPanel>
            </div>
          </AdminSection>

          <AdminSection
            id="admin-ai-runtime-config-center"
            icon={Waves}
            title="AI 模型配置"
            subtitle="集中维护 Base URL、默认模型、超时和运行时密钥。"
            actions={<button type="button" className="secondary-btn" disabled={loadingRuntimeConfigList} onClick={handleListAiRuntimeConfigs}>{loadingRuntimeConfigList ? "加载中..." : "刷新配置列表"}</button>}
          >
            <div className="admin-section-stack">
              <AdminPanel title="运行时配置列表" description="点击某个配置键后，在下方展开编辑界面。敏感值只展示掩码。">
                {runtimeConfigList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {runtimeConfigList.length} 项</div>
                    <table className="table">
                      <thead><tr><th>配置键</th><th>配置名称</th><th>当前值</th><th>状态</th><th>来源</th></tr></thead>
                      <tbody>
                        {runtimeConfigList.map((item) => (
                          <tr key={item.config_key} className={runtimeConfigEditor.config_key === item.config_key ? "admin-prompt-row-selected" : ""}>
                            <td><button type="button" className="admin-prompt-key-button" onClick={() => handleSelectRuntimeConfig(item)}>{item.config_key}</button></td>
                            <td>{item.config_name}</td>
                            <td>{item.effective_value_display || "-"}</td>
                            <td>{item.status}</td>
                            <td>{item.using_default ? ".env 默认值" : "数据库覆盖"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text={adminKey.trim() ? "还没有运行时配置数据，点右上角刷新列表。" : "先输入管理员密钥，再点右上角刷新列表。"} />}
              </AdminPanel>

              {runtimeConfigEditor.config_key ? (
                <AdminPanel
                  title={`编辑 ${runtimeConfigEditor.config_key}`}
                  description="数据库有值时优先使用数据库；清空后自动回退 .env 默认值。"
                  aside={runtimeConfigMessage ? <span className="check-success">{runtimeConfigMessage}</span> : null}
                >
                  <div className="admin-detail-list admin-prompt-readonly-meta">
                    <div><strong>配置组：</strong>{runtimeConfigEditor.config_group}</div>
                    <div><strong>值类型：</strong>{runtimeConfigEditor.value_type}</div>
                    <div><strong>当前来源：</strong>{runtimeConfigEditor.using_default ? ".env 默认值" : "数据库覆盖"}</div>
                    <div><strong>默认值：</strong>{runtimeConfigEditor.default_value_display || "-"}</div>
                    <div><strong>当前生效值：</strong>{runtimeConfigEditor.effective_value_display || "-"}</div>
                  </div>
                  <div className="admin-form-grid">
                    <Field label="配置名称"><input className="input" value={runtimeConfigEditor.config_name} disabled /></Field>
                    <Field label="状态">
                      <select className="input" value={runtimeConfigEditor.status} onChange={(event) => handleRuntimeConfigEditorChange("status", event.target.value)}>
                        <option value="active">active</option>
                        <option value="disabled">disabled</option>
                      </select>
                    </Field>
                    <Field
                      label="数据库覆盖值"
                      hint={runtimeConfigEditor.is_secret ? "敏感值不会回显。留空保存表示保持现状；输入新值后保存会覆盖当前数据库值。" : "留空保存表示保持现状；点恢复按钮可清空数据库覆盖并回退到 .env。"}
                    >
                      <input
                        className="input"
                        type={runtimeConfigEditor.is_secret ? "password" : "text"}
                        value={runtimeConfigEditor.config_value}
                        onChange={(event) => handleRuntimeConfigEditorChange("config_value", event.target.value)}
                        placeholder={runtimeConfigEditor.is_secret ? "输入新的密钥覆盖当前值" : "输入新的数据库覆盖值"}
                      />
                    </Field>
                    <Field label="备注">
                      <input className="input" value={runtimeConfigEditor.remark} onChange={(event) => handleRuntimeConfigEditorChange("remark", event.target.value)} placeholder="记录用途或变更说明" />
                    </Field>
                  </div>
                  <div className="admin-button-row">
                    <button type="button" className="primary-btn" disabled={loadingRuntimeConfigSave} onClick={() => handleSaveAiRuntimeConfig()}>{loadingRuntimeConfigSave ? "保存中..." : "保存运行时配置"}</button>
                    <button type="button" className="secondary-btn" disabled={loadingRuntimeConfigSave} onClick={() => handleSaveAiRuntimeConfig({ clearOverride: true })}>恢复 .env 默认值</button>
                  </div>
                </AdminPanel>
              ) : null}
            </div>
          </AdminSection>

          <AdminSection
            id="admin-ai-prompt-center"
            icon={FileCode2}
            title="AI 提示词中心"
            subtitle="直接查看 Prompt 列表，点击某个 prompt_key 在下方展开编辑。"
            actions={<button type="button" className="secondary-btn" disabled={loadingPromptList} onClick={handleListAiPrompts}>{loadingPromptList ? "加载中..." : "刷新 Prompt 列表"}</button>}
          >
            <div className="admin-section-stack">
              <AdminPanel title="Prompt 列表" description="这里展示当前已加载的 Prompt，点击某一项即可编辑。">
                {promptList.length ? (
                  <div className="table-wrap">
                    <div className="result-box">共 {promptTotal} 条</div>
                    <table className="table">
                      <thead><tr><th>ID</th><th>Prompt Key</th><th>名称</th><th>阶段</th><th>状态</th><th>模型</th><th>版本</th></tr></thead>
                      <tbody>
                        {promptList.map((item) => (
                          <tr key={item.id} className={String(promptEditor.id) === String(item.id) ? "admin-prompt-row-selected" : ""}>
                            <td>{item.id}</td>
                            <td><button type="button" className="admin-prompt-key-button" onClick={() => handleSelectPrompt(item)}>{item.prompt_key}</button></td>
                            <td>{item.prompt_name}</td>
                            <td>{item.prompt_stage}</td>
                            <td>{item.status}</td>
                            <td>{item.model_name || "-"}</td>
                            <td>{item.prompt_version}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : <DataPlaceholder text={adminKey.trim() ? "还没有 Prompt 数据，点右上角刷新列表。" : "先输入管理员密钥，再点右上角刷新列表。"} />}
              </AdminPanel>

              {promptEditor.id ? (
                <AdminPanel
                  title={`编辑 ${promptEditor.prompt_key}`}
                  description="修改后会直接更新 ai_prompt_configs，请谨慎操作。"
                  aside={promptMessage ? <span className="check-success">{promptMessage}</span> : null}
                >
                  <div className="admin-detail-list admin-prompt-readonly-meta">
                    <div><strong>业务域：</strong>{promptEditor.biz_domain || "-"}</div>
                    <div><strong>阶段：</strong>{promptEditor.prompt_stage || "-"}</div>
                    <div><strong>角色：</strong>{promptEditor.prompt_role || "-"}</div>
                    <div><strong>Prompt Key：</strong>{promptEditor.prompt_key}</div>
                    <div><strong>ID：</strong>{promptEditor.id}</div>
                  </div>
                  <div className="admin-form-grid">
                    <Field label="Prompt 名称"><input className="input" value={promptEditor.prompt_name} onChange={(event) => handlePromptEditorChange("prompt_name", event.target.value)} /></Field>
                    <Field label="Prompt 版本"><input className="input" value={promptEditor.prompt_version} onChange={(event) => handlePromptEditorChange("prompt_version", event.target.value)} /></Field>
                    <Field label="状态">
                      <select className="input" value={promptEditor.status} onChange={(event) => handlePromptEditorChange("status", event.target.value)}>
                        <option value="draft">草稿</option>
                        <option value="active">启用</option>
                        <option value="disabled">停用</option>
                        <option value="archived">归档</option>
                      </select>
                    </Field>
                    <Field label="输出格式">
                      <select className="input" value={promptEditor.output_format} onChange={(event) => handlePromptEditorChange("output_format", event.target.value)}>
                        <option value="text">text</option>
                        <option value="json">json</option>
                      </select>
                    </Field>
                    <Field label="模型名称"><input className="input" value={promptEditor.model_name} onChange={(event) => handlePromptEditorChange("model_name", event.target.value)} placeholder="例如 deepseek-chat / gpt-4.1-mini" /></Field>
                    <Field label="Temperature"><input className="input" type="number" step="0.1" value={promptEditor.temperature} onChange={(event) => handlePromptEditorChange("temperature", event.target.value)} /></Field>
                    <Field label="Top P"><input className="input" type="number" step="0.1" value={promptEditor.top_p} onChange={(event) => handlePromptEditorChange("top_p", event.target.value)} /></Field>
                    <Field label="Max Tokens"><input className="input" type="number" min="1" value={promptEditor.max_tokens} onChange={(event) => handlePromptEditorChange("max_tokens", event.target.value)} /></Field>
                  </div>
                  <div className="admin-form-grid">
                    <Field label="Prompt 正文">
                      <textarea className="input admin-prompt-textarea" value={promptEditor.prompt_content} onChange={(event) => handlePromptEditorChange("prompt_content", event.target.value)} placeholder="请输入 Prompt 正文" />
                    </Field>
                  </div>
                  <div className="admin-form-grid">
                    <Field label="variables_json" hint='使用 JSON 字符串，例如 {"profile_json":"当前档案快照"}'>
                      <textarea className="input admin-prompt-json-textarea" value={promptEditor.variables_json_text} onChange={(event) => handlePromptEditorChange("variables_json_text", event.target.value)} placeholder='{"profile_json":"当前档案快照"}' />
                    </Field>
                    <Field label="备注">
                      <textarea className="input admin-prompt-remark-textarea" value={promptEditor.remark} onChange={(event) => handlePromptEditorChange("remark", event.target.value)} placeholder="例如：用于档案页局部重算" />
                    </Field>
                  </div>
                  <div className="admin-button-row">
                    <button type="button" className="primary-btn" disabled={loadingPromptSave} onClick={handleSaveAiPrompt}>{loadingPromptSave ? "保存中..." : "保存 Prompt 修改"}</button>
                  </div>
                </AdminPanel>
              ) : null}
            </div>
          </AdminSection>

          <AdminSection id="admin-system-center" icon={UserCog} title="账号与权限" subtitle="管理员日常维护入口，主要处理用户状态等基础控制能力。">
            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="用户状态管理" description="支持按用户 ID 或手机号更新账号状态。">
                <div className="admin-form-grid">
                  <Field label="用户 ID" hint="和手机号二选一，至少填写一个。"><input className="input" value={userStatusForm.user_id} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, user_id: event.target.value }))} placeholder="输入 UUID" /></Field>
                  <Field label="手机号"><input className="input" value={userStatusForm.mobile} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, mobile: event.target.value }))} placeholder="11 位手机号" /></Field>
                  <Field label="目标状态"><select className="input" value={userStatusForm.status} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, status: event.target.value }))}><option value="active">{formatStatus("active")}</option><option value="disabled">{formatStatus("disabled")}</option></select></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingUserStatus} onClick={handleUpdateUserStatus}>{loadingUserStatus ? "保存中..." : "修改用户状态"}</button>
                </div>
              </AdminPanel>

              <AdminPanel title="最近状态更新结果" description="用户状态更新成功后，最近一次结果会显示在这里。">
                {lastUserStatusResult.user_id ? (
                  <div className="admin-detail-list">
                    <div><strong>用户 ID：</strong>{lastUserStatusResult.user_id}</div>
                    <div><strong>手机号：</strong>{lastUserStatusResult.mobile || "-"}</div>
                    <div><strong>状态：</strong>{formatStatus(lastUserStatusResult.status)}</div>
                  </div>
                ) : <DataPlaceholder text="还没有用户状态变更记录。" />}
              </AdminPanel>
            </div>
          </AdminSection>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
