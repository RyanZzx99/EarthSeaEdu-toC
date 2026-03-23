import React, { useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  BadgeCheck,
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
  issueInviteCode,
  listInviteCodes,
  listNicknameAuditLogs,
  listNicknameContactPatterns,
  listNicknameRuleGroups,
  listNicknameWordRules,
  updateInviteCodeStatus,
  updateNicknameRuleTargetStatus,
  updateUserStatus,
} from "../api/auth";

const sectionItems = [
  { key: "overview", label: "工作台", sectionId: "admin-overview" },
  { key: "invite", label: "邀请码", sectionId: "admin-invite-center" },
  { key: "nickname", label: "昵称规则", sectionId: "admin-nickname-center" },
  { key: "audit", label: "审核日志", sectionId: "admin-audit-center" },
  { key: "system", label: "系统管理", sectionId: "admin-system-center" },
];

const inviteStatusOptions = [
  { value: "1", label: "1（未使用）" },
  { value: "2", label: "2（已使用）" },
  { value: "3", label: "3（已禁用）" },
];

const groupTypeOptions = ["reserved", "black", "review", "whitelist", "contact"];
const groupStatusOptions = ["draft", "active", "disabled"];
const wordMatchTypeOptions = ["contains", "exact", "prefix", "suffix", "regex"];
const wordDecisionOptions = ["reject", "review", "pass"];
const contactTypeOptions = ["mobile", "wechat", "qq", "email", "social"];
const contactDecisionOptions = ["reject", "review"];
const riskLevelOptions = ["low", "medium", "high", "critical"];

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

function formatRuleTargetType(value) {
  if (value === "group") return "分组";
  if (value === "word") return "词条";
  if (value === "pattern") return "联系方式规则";
  return value || "-";
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
      <span>{item.label}</span>
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

function GroupSelect({ value, onChange, groups, placeholder = "全部分组", allowEmpty = true }) {
  return (
    <select className="input" value={value} onChange={onChange}>
      {allowEmpty ? <option value="">{placeholder}</option> : null}
      {groups.map((item) => (
        <option key={item.id} value={item.id}>
          {item.group_name}（{item.group_code}）
        </option>
      ))}
    </select>
  );
}

export default function AdminConsolePage() {
  const [activeSection, setActiveSection] = useState("overview");
  const [adminKey, setAdminKey] = useState("");
  const [generateForm, setGenerateForm] = useState({ count: 10, expires_days: "", note: "" });
  const [issueForm, setIssueForm] = useState({ code: "", mobile: "" });
  const [queryForm, setQueryForm] = useState({ status: "", mobile: "", code_keyword: "", limit: 50 });
  const [userStatusForm, setUserStatusForm] = useState({ user_id: "", mobile: "", status: "active" });
  const [groupForm, setGroupForm] = useState({
    group_code: "",
    group_name: "",
    group_type: "reserved",
    status: "draft",
    priority: 100,
    description: "",
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
  const [generatedItems, setGeneratedItems] = useState([]);
  const [lastIssued, setLastIssued] = useState({});
  const [inviteList, setInviteList] = useState([]);
  const [queryTotal, setQueryTotal] = useState(0);
  const [groupList, setGroupList] = useState([]);
  const [wordRuleList, setWordRuleList] = useState([]);
  const [contactPatternList, setContactPatternList] = useState([]);
  const [auditLogList, setAuditLogList] = useState([]);
  const [wordRuleTotal, setWordRuleTotal] = useState(0);
  const [contactPatternTotal, setContactPatternTotal] = useState(0);
  const [auditLogTotal, setAuditLogTotal] = useState(0);
  const [pendingStatusMap, setPendingStatusMap] = useState({});
  const [loadingGenerate, setLoadingGenerate] = useState(false);
  const [loadingIssue, setLoadingIssue] = useState(false);
  const [loadingQuery, setLoadingQuery] = useState(false);
  const [loadingUserStatus, setLoadingUserStatus] = useState(false);
  const [loadingGroupCreate, setLoadingGroupCreate] = useState(false);
  const [loadingGroupList, setLoadingGroupList] = useState(false);
  const [loadingWordRuleCreate, setLoadingWordRuleCreate] = useState(false);
  const [loadingWordRuleList, setLoadingWordRuleList] = useState(false);
  const [loadingContactCreate, setLoadingContactCreate] = useState(false);
  const [loadingContactList, setLoadingContactList] = useState(false);
  const [loadingRuleStatus, setLoadingRuleStatus] = useState(false);
  const [loadingAuditLogs, setLoadingAuditLogs] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const generatedSummary = useMemo(() => generatedItems, [generatedItems]);
  const overviewStats = useMemo(
    () => [
      { label: "规则分组", value: groupList.length, hint: "当前已加载的分组" },
      { label: "词条规则", value: wordRuleTotal, hint: "最近一次查询结果" },
      { label: "联系方式规则", value: contactPatternTotal, hint: "最近一次查询结果" },
      { label: "审核日志", value: auditLogTotal, hint: "最近一次查询结果" },
    ],
    [auditLogTotal, contactPatternTotal, groupList.length, wordRuleTotal]
  );

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
      const response = await listNicknameRuleGroups({}, adminKey);
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
          <nav className="admin-top-nav">
            {sectionItems.map((item) => (
              <SectionNavButton
                key={item.key}
                item={item}
                isActive={activeSection === item.key}
                onClick={() => scrollToSection(item.sectionId, item.key)}
              />
            ))}
          </nav>
          <div className="admin-top-status">
            <span className="admin-top-badge">运营后台</span>
          </div>
        </div>
      </motion.header>

      <main className="admin-main">
        <motion.section
          className="admin-hero"
          id="admin-overview"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.55 }}
        >
          <div className="home-hero-bg admin-hero-bg" />
          <div className="home-hero-orb home-hero-orb-left" />
          <div className="home-hero-orb home-hero-orb-right" />
          <div className="home-hero-content">
            <h1 className="home-hero-title">
              后台运营控制台
              <br />
              <span className="home-hero-title-accent">邀请码、昵称风控、审核回溯统一入口</span>
            </h1>
            <p className="home-hero-subtitle">
              把分散的管理动作收敛成可运营工作台。先输入管理员密钥，再进入对应分区执行操作。
            </p>
            <div className="admin-hero-actions">
              <button
                type="button"
                className="home-primary-button"
                onClick={() => scrollToSection("admin-nickname-center", "nickname")}
              >
                进入昵称规则中心
              </button>
              <button
                type="button"
                className="home-secondary-button"
                onClick={() => scrollToSection("admin-audit-center", "audit")}
              >
                查看审核日志
              </button>
            </div>
            <div className="home-hero-stats admin-hero-stats">
              {overviewStats.map((item) => (
                <div key={item.label} className="home-stat">
                  <div className="home-stat-number">{item.value}</div>
                  <div className="home-stat-label">{item.label}</div>
                  <div className="admin-stat-hint">{item.hint}</div>
                </div>
              ))}
            </div>
          </div>
        </motion.section>

        <div className="home-content-wrap">
          {errorMessage ? <div className="error-box admin-global-alert">{errorMessage}</div> : null}

          <AdminSection
            id="admin-overview-cards"
            icon={Waves}
            title="工作台概览"
            subtitle="先完成密钥加载和基础数据刷新，再进入对应业务分区。"
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
                <p>刷新常用基础数据，便于后续下拉选择和概览统计更新。</p>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGroupList} onClick={handleListGroups}>
                    {loadingGroupList ? "刷新中..." : "刷新规则分组"}
                  </button>
                  <button type="button" className="secondary-btn" disabled={loadingAuditLogs} onClick={handleListAuditLogs}>
                    {loadingAuditLogs ? "刷新中..." : "刷新审核日志"}
                  </button>
                </div>
              </div>
              <div className="home-card admin-summary-card">
                <div className="admin-summary-icon"><BadgeCheck size={20} /></div>
                <h3>最近结果</h3>
                <p>最近一次查询结果的数量概览，用来判断后台当前工作面。</p>
                <div className="admin-metric-stack">
                  <span>邀请码查询：{queryTotal}</span>
                  <span>词条规则：{wordRuleTotal}</span>
                  <span>联系方式规则：{contactPatternTotal}</span>
                  <span>审核日志：{auditLogTotal}</span>
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
              <AdminPanel title="生成与发放" description="先生成新批次邀请码，再按手机号发放给目标用户。">
                <div className="admin-form-grid">
                  <Field label="生成数量"><input className="input" type="number" min="1" max="200" value={generateForm.count} onChange={(event) => setGenerateForm((previous) => ({ ...previous, count: event.target.value }))} placeholder="1 到 200" /></Field>
                  <Field label="过期天数"><input className="input" type="number" min="1" max="3650" value={generateForm.expires_days} onChange={(event) => setGenerateForm((previous) => ({ ...previous, expires_days: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="批次备注"><input className="input" value={generateForm.note} onChange={(event) => setGenerateForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="例如：四月运营活动" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGenerate} onClick={handleGenerate}>{loadingGenerate ? "生成中..." : "生成邀请码"}</button>
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
                      <thead><tr><th>邀请码</th><th>状态</th><th>发放手机号</th><th>使用用户</th><th>发放时间</th><th>过期时间</th><th>操作</th></tr></thead>
                      <tbody>
                        {inviteList.map((item) => (
                          <tr key={item.code}>
                            <td>{item.code}</td>
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
                                <button type="button" className="inline-btn" onClick={() => handleUpdateInviteStatus(item)}>保存</button>
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
                  <Field label="分组类型"><select className="input" value={groupForm.group_type} onChange={(event) => setGroupForm((previous) => ({ ...previous, group_type: event.target.value }))}>{groupTypeOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={groupForm.status} onChange={(event) => setGroupForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={groupForm.priority} onChange={(event) => setGroupForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="分组说明"><input className="input" value={groupForm.description} onChange={(event) => setGroupForm((previous) => ({ ...previous, description: event.target.value }))} placeholder="说明这个分组的运营用途" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingGroupCreate} onClick={handleCreateGroup}>{loadingGroupCreate ? "创建中..." : "创建规则分组"}</button>
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
                            <td>{item.group_type}</td>
                            <td>{item.status}</td>
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
                  <Field label="目标类型"><select className="input" value={ruleStatusForm.target_type} onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, target_type: event.target.value }))}><option value="group">group（分组）</option><option value="word">word（词条）</option><option value="pattern">pattern（联系方式规则）</option></select></Field>
                  <Field label="目标 ID"><input className="input" type="number" min="1" value={ruleStatusForm.target_id} onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, target_id: event.target.value }))} placeholder="输入表中的目标 ID" /></Field>
                  <Field label="更新状态"><select className="input" value={ruleStatusForm.status} onChange={(event) => setRuleStatusForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingRuleStatus} onClick={handleUpdateRuleStatus}>{loadingRuleStatus ? "更新中..." : "更新规则状态"}</button>
                </div>
                {lastRuleStatusResult.id ? (
                  <div className="result-box">
                    已更新 {formatRuleTargetType(ruleStatusForm.target_type)} ID {lastRuleStatusResult.id}，当前状态：{lastRuleStatusResult.status}
                  </div>
                ) : <DataPlaceholder text="状态更新结果会显示在这里。" />}
              </AdminPanel>
            </div>

            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="词条规则" description="使用下拉分组选择，避免继续手工输入 group_id。">
                <div className="admin-form-grid">
                  <Field label="所属分组"><GroupSelect value={wordRuleForm.group_id} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} placeholder="请选择分组" allowEmpty={false} /></Field>
                  <Field label="词条"><input className="input" value={wordRuleForm.word} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, word: event.target.value }))} placeholder="输入标准词条" /></Field>
                  <Field label="匹配方式"><select className="input" value={wordRuleForm.match_type} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, match_type: event.target.value }))}>{wordMatchTypeOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="命中决策"><select className="input" value={wordRuleForm.decision} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, decision: event.target.value }))}>{wordDecisionOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={wordRuleForm.status} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={wordRuleForm.priority} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="风险等级"><select className="input" value={wordRuleForm.risk_level} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, risk_level: event.target.value }))}>{riskLevelOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="备注"><input className="input" value={wordRuleForm.note} onChange={(event) => setWordRuleForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="可留空" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingWordRuleCreate} onClick={handleCreateWordRule}>{loadingWordRuleCreate ? "创建中..." : "创建词条规则"}</button>
                </div>
                <div className="admin-divider" />
                <div className="admin-filter-grid">
                  <Field label="分组筛选"><GroupSelect value={wordRuleQueryForm.group_id} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} /></Field>
                  <Field label="状态"><select className="input" value={wordRuleQueryForm.status} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, status: event.target.value }))}><option value="">全部状态</option>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="决策"><select className="input" value={wordRuleQueryForm.decision} onChange={(event) => setWordRuleQueryForm((previous) => ({ ...previous, decision: event.target.value }))}><option value="">全部决策</option>{wordDecisionOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
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
                            <td>{item.match_type}</td>
                            <td>{item.decision}</td>
                            <td>{item.status}</td>
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
                  <Field label="规则类型"><select className="input" value={contactPatternForm.pattern_type} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, pattern_type: event.target.value }))}>{contactTypeOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="正则表达式"><input className="input" value={contactPatternForm.pattern_regex} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, pattern_regex: event.target.value }))} placeholder="输入正则" /></Field>
                  <Field label="命中决策"><select className="input" value={contactPatternForm.decision} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, decision: event.target.value }))}>{contactDecisionOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="状态"><select className="input" value={contactPatternForm.status} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, status: event.target.value }))}>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="优先级"><input className="input" type="number" min="1" max="10000" value={contactPatternForm.priority} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, priority: event.target.value }))} /></Field>
                  <Field label="风险等级"><select className="input" value={contactPatternForm.risk_level} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, risk_level: event.target.value }))}>{riskLevelOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="归一化说明"><input className="input" value={contactPatternForm.normalized_hint} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, normalized_hint: event.target.value }))} placeholder="可留空" /></Field>
                  <Field label="备注"><input className="input" value={contactPatternForm.note} onChange={(event) => setContactPatternForm((previous) => ({ ...previous, note: event.target.value }))} placeholder="可留空" /></Field>
                </div>
                <div className="admin-button-row">
                  <button type="button" className="primary-btn" disabled={loadingContactCreate} onClick={handleCreateContactPattern}>{loadingContactCreate ? "创建中..." : "创建联系方式规则"}</button>
                </div>
                <div className="admin-divider" />
                <div className="admin-filter-grid">
                  <Field label="分组筛选"><GroupSelect value={contactPatternQueryForm.group_id} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, group_id: event.target.value }))} groups={groupList} /></Field>
                  <Field label="状态"><select className="input" value={contactPatternQueryForm.status} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, status: event.target.value }))}><option value="">全部状态</option>{groupStatusOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
                  <Field label="规则类型"><select className="input" value={contactPatternQueryForm.pattern_type} onChange={(event) => setContactPatternQueryForm((previous) => ({ ...previous, pattern_type: event.target.value }))}><option value="">全部类型</option>{contactTypeOptions.map((item) => <option key={item} value={item}>{item}</option>)}</select></Field>
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
                            <td>{item.pattern_type}</td>
                            <td>{item.decision}</td>
                            <td>{item.status}</td>
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
                  <Field label="结果"><select className="input" value={auditLogQueryForm.decision} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, decision: event.target.value }))}><option value="">全部结果</option><option value="pass">pass</option><option value="reject">reject</option><option value="review">review</option></select></Field>
                  <Field label="场景"><select className="input" value={auditLogQueryForm.scene} onChange={(event) => setAuditLogQueryForm((previous) => ({ ...previous, scene: event.target.value }))}><option value="">全部场景</option><option value="check">check</option><option value="update">update</option></select></Field>
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
                            <td>{item.scene}</td>
                            <td>{item.raw_nickname}</td>
                            <td>{item.decision}</td>
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

          <AdminSection id="admin-system-center" icon={UserCog} title="账号与权限" subtitle="管理员日常维护入口，主要处理用户状态等基础控制能力。">
            <div className="admin-workbench-grid admin-workbench-grid--two">
              <AdminPanel title="用户状态管理" description="支持按用户 ID 或手机号更新账号状态。">
                <div className="admin-form-grid">
                  <Field label="用户 ID" hint="和手机号二选一，至少填写一个。"><input className="input" value={userStatusForm.user_id} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, user_id: event.target.value }))} placeholder="输入 UUID" /></Field>
                  <Field label="手机号"><input className="input" value={userStatusForm.mobile} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, mobile: event.target.value }))} placeholder="11 位手机号" /></Field>
                  <Field label="目标状态"><select className="input" value={userStatusForm.status} onChange={(event) => setUserStatusForm((previous) => ({ ...previous, status: event.target.value }))}><option value="active">active（启用）</option><option value="disabled">disabled（禁用）</option></select></Field>
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
                    <div><strong>状态：</strong>{lastUserStatusResult.status}</div>
                  </div>
                ) : <DataPlaceholder text="还没有用户状态变更记录。" />}
              </AdminPanel>
            </div>
          </AdminSection>
        </div>
      </main>
    </div>
  );
}
