import React, { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { useNavigate } from "react-router-dom";
import {
  checkSmsInviteRequired,
  checkWechatBindInviteRequired,
  getWechatAuthorizeUrl,
  passwordLogin,
  sendSmsCode,
  smsLogin,
  tempRegisterLogin,
  wechatInviteRegister,
  wechatBindMobile,
  wechatLogin,
} from "../api/auth";
import { LoadingOverlay } from "../components/LoadingPage";
import { setAccessToken } from "../utils/authStorage";
import { validatePasswordRule } from "../utils/passwordValidation";

function Particle({ x, y, size, duration, delay }) {
  return (
    <motion.div
      className="absolute rounded-full pointer-events-none"
      style={{
        left: `${x}%`,
        top: `${y}%`,
        width: size,
        height: size,
        background: "rgba(255,255,255,0.12)",
      }}
      animate={{
        y: [0, -30, 0],
        x: [0, 10, -10, 0],
        opacity: [0.3, 0.7, 0.3],
        scale: [1, 1.2, 1],
      }}
      transition={{ duration, delay, repeat: Infinity, ease: "easeInOut" }}
    />
  );
}

function AnimatedInput({ focused, children }) {
  return (
    <motion.div
      className="flex items-center rounded-2xl px-4 py-3 gap-3"
      animate={{
        borderColor: focused ? "#2c4a8a" : "#e5e7eb",
        boxShadow: focused ? "0 0 0 3px rgba(44,74,138,0.1)" : "0 0 0 0 rgba(44,74,138,0)",
      }}
      transition={{ duration: 0.2 }}
      style={{ border: "1.5px solid", background: "#fafafa" }}
    >
      {children}
    </motion.div>
  );
}

function ScannerLine() {
  return (
    <motion.div
      className="absolute left-2 right-2 h-0.5 rounded-full"
      style={{
        background: "linear-gradient(90deg, transparent, #07c160, transparent)",
        boxShadow: "0 0 8px #07c160",
      }}
      animate={{ top: ["10%", "88%", "10%"] }}
      transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
    />
  );
}

function AnimatedStat({ target, label, delay }) {
  return (
    <motion.div
      className="flex flex-col gap-1"
      initial={false}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5, ease: "easeOut" }}
    >
      <span style={{ fontSize: "22px", fontWeight: 700, color: "rgba(255,255,255,0.95)" }}>
        {target}
      </span>
      <span style={{ fontSize: "13px", color: "rgba(255,255,255,0.5)" }}>{label}</span>
    </motion.div>
  );
}

const particles = [
  { x: 10, y: 15, size: 6, duration: 5, delay: 0 },
  { x: 85, y: 20, size: 10, duration: 7, delay: 1 },
  { x: 30, y: 60, size: 4, duration: 6, delay: 0.5 },
  { x: 70, y: 75, size: 8, duration: 8, delay: 2 },
  { x: 50, y: 35, size: 5, duration: 5.5, delay: 1.5 },
  { x: 20, y: 85, size: 7, duration: 9, delay: 0.8 },
];

const tabs = [
  { key: "password", label: "密码登录" },
  { key: "sms", label: "验证码登录" },
  { key: "temp_register", label: "临时注册入口" },
  { key: "wechat", label: "微信扫码" },
];

function MobilePrefix() {
  return (
    <span
      style={{
        fontSize: "14px",
        color: "#9ca3af",
        borderRight: "1px solid #e5e7eb",
        paddingRight: "10px",
      }}
    >
      +86
    </span>
  );
}

function PasswordIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#a5aec0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="10" rx="2" />
      <path d="M7 11V8a5 5 0 0 1 10 0v3" />
    </svg>
  );
}

function SmsIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#a5aec0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 5h16v12H4z" />
      <path d="m4 7 8 6 8-6" />
    </svg>
  );
}

function EyeIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#a5aec0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function MessageBlock({ errorMessage, successMessage }) {
  if (!errorMessage && !successMessage) return null;

  return (
    <div
      className="mb-4 rounded-2xl px-4 py-3"
      style={{
        background: errorMessage ? "#fff1f2" : "#f0fdf4",
        border: `1px solid ${errorMessage ? "#fecdd3" : "#bbf7d0"}`,
        color: errorMessage ? "#be123c" : "#15803d",
        fontSize: "13px",
      }}
    >
      {errorMessage || successMessage}
    </div>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState("password");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [smsPhone, setSmsPhone] = useState("");
  const [smsCode, setSmsCode] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [tempRegisterPhone, setTempRegisterPhone] = useState("");
  const [tempRegisterPassword, setTempRegisterPassword] = useState("");
  const [tempRegisterInviteCode, setTempRegisterInviteCode] = useState("");
  const [wechatRegisterInviteCode, setWechatRegisterInviteCode] = useState("");
  const [registerToken, setRegisterToken] = useState("");
  const [bindPhone, setBindPhone] = useState("");
  const [bindCode, setBindCode] = useState("");
  const [bindInviteCode, setBindInviteCode] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberLogin, setRememberLogin] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [bindCountdown, setBindCountdown] = useState(0);
  const [loading, setLoading] = useState(false);
  const [sendCodeLoading, setSendCodeLoading] = useState(false);
  const [inviteCheckLoading, setInviteCheckLoading] = useState(false);
  const [bindInviteCheckLoading, setBindInviteCheckLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [bindToken, setBindToken] = useState("");
  const [smsNeedInvite, setSmsNeedInvite] = useState(null);
  const [bindNeedInvite, setBindNeedInvite] = useState(null);
  const [tempRegisterPasswordCheckMessage, setTempRegisterPasswordCheckMessage] = useState("");
  const [tempRegisterPasswordCheckAvailable, setTempRegisterPasswordCheckAvailable] = useState(false);
  const [hoveredPanel, setHoveredPanel] = useState(null);
  const [phoneFocused, setPhoneFocused] = useState(false);
  const [passFocused, setPassFocused] = useState(false);
  const [smsPhoneFocused, setSmsPhoneFocused] = useState(false);
  const [codeFocused, setCodeFocused] = useState(false);
  const [inviteFocused, setInviteFocused] = useState(false);
  const [tempRegisterPhoneFocused, setTempRegisterPhoneFocused] = useState(false);
  const [tempRegisterPasswordFocused, setTempRegisterPasswordFocused] = useState(false);
  const [tempRegisterInviteFocused, setTempRegisterInviteFocused] = useState(false);
  const [wechatRegisterInviteFocused, setWechatRegisterInviteFocused] = useState(false);
  const [bindPhoneFocused, setBindPhoneFocused] = useState(false);
  const [bindCodeFocused, setBindCodeFocused] = useState(false);
  const [bindInviteFocused, setBindInviteFocused] = useState(false);

  useEffect(() => {
    if (!countdown) return undefined;
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  useEffect(() => {
    if (!bindCountdown) return undefined;
    const timer = window.setTimeout(() => setBindCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [bindCountdown]);

  useEffect(() => {
    // 中文注释：进入登录页时处理会话过期提示和微信回调。
    handleSessionExpiredTip();
    handleLoginModeQuery();
    handleWechatCallbackLogin();
  }, []);

  const panelTitle = useMemo(
    () => {
      if (activeTab === "bind_mobile") return "绑定手机号";
      if (activeTab === "temp_register") return "临时注册入口";
      return "欢迎回来";
    },
    [activeTab]
  );

  const panelDescription = useMemo(() => {
    if (activeTab === "bind_mobile") return "完成手机号绑定后即可进入系统";
    if (activeTab === "temp_register") return "通过手机号、密码和邀请码快速完成注册并直接登录";
    return "登录您的学习账户，继续探索之旅";
  }, [activeTab]);

  const resolvedPanelTitle = useMemo(() => {
    if (activeTab === "wechat_invite_register") return "邀请码注册";
    if (activeTab === "bind_mobile") return "绑定手机号";
    if (activeTab === "temp_register") return "临时注册入口";
    return "欢迎回来";
  }, [activeTab]);

  const resolvedPanelDescription = useMemo(() => {
    if (activeTab === "wechat_invite_register") return "首次微信扫码请填写邀请码完成注册，成功后会直接登录";
    if (activeTab === "bind_mobile") return "完成手机号绑定后即可进入系统";
    if (activeTab === "temp_register") return "通过手机号、密码和邀请码快速完成注册并直接登录";
    return "登录您的学习账户，继续探索之旅";
  }, [activeTab]);

  const displayPanelTitle = useMemo(() => {
    if (activeTab === "wechat_invite_register") return "\u9080\u8bf7\u7801\u6ce8\u518c";
    if (activeTab === "bind_mobile") return "\u7ed1\u5b9a\u624b\u673a\u53f7";
    if (activeTab === "temp_register") return "\u4e34\u65f6\u6ce8\u518c\u5165\u53e3";
    return "\u6b22\u8fce\u56de\u6765";
  }, [activeTab]);

  const displayPanelDescription = useMemo(() => {
    if (activeTab === "wechat_invite_register") return "\u9996\u6b21\u5fae\u4fe1\u626b\u7801\u8bf7\u586b\u5199\u9080\u8bf7\u7801\u5b8c\u6210\u6ce8\u518c\uff0c\u6210\u529f\u540e\u4f1a\u76f4\u63a5\u767b\u5f55";
    if (activeTab === "bind_mobile") return "\u5b8c\u6210\u624b\u673a\u53f7\u7ed1\u5b9a\u540e\u5373\u53ef\u8fdb\u5165\u7cfb\u7edf";
    if (activeTab === "temp_register") return "\u901a\u8fc7\u624b\u673a\u53f7\u3001\u5bc6\u7801\u548c\u9080\u8bf7\u7801\u5feb\u901f\u5b8c\u6210\u6ce8\u518c\u5e76\u76f4\u63a5\u767b\u5f55";
    return "\u767b\u5f55\u60a8\u7684\u5b66\u4e60\u8d26\u6237\uff0c\u7ee7\u7eed\u63a2\u7d22\u4e4b\u65c5";
  }, [activeTab]);

  const busyOverlay = useMemo(() => {
    if (loading) {
      if (activeTab === "wechat") {
        return {
          message: "正在打开微信登录",
          submessage: "请稍候，正在跳转到微信授权页面",
        };
      }
      if (activeTab === "bind_mobile") {
        return {
          message: "正在完成绑定",
          submessage: "请稍候，正在提交手机号绑定信息",
        };
      }
      if (activeTab === "temp_register" || activeTab === "wechat_invite_register") {
        return {
          message: "正在创建账号",
          submessage: "请稍候，正在处理当前注册请求",
        };
      }
      return {
        message: "正在登录",
        submessage: "请稍候，正在验证当前账号信息",
      };
    }

    if (sendCodeLoading) {
      return {
        message: "正在发送验证码",
        submessage: "请稍候，验证码正在发送到你的手机",
      };
    }

    if (inviteCheckLoading || bindInviteCheckLoading) {
      return {
        message: "正在检查账号状态",
        submessage: "请稍候，正在判断当前手机号的注册与邀请码状态",
      };
    }

    return null;
  }, [activeTab, bindInviteCheckLoading, inviteCheckLoading, loading, sendCodeLoading]);

  const formVariants = {
    initial: { opacity: 1, x: 0 },
    animate: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: -16 },
  };

  function clearMessages() {
    setErrorMessage("");
    setSuccessMessage("");
  }

  function validateMobile(mobile) {
    return /^1\d{10}$/.test(mobile);
  }

  function resetSmsInviteRequirement() {
    // 中文注释：手机号变化后，之前基于旧手机号得到的邀请码判断结果就不再可靠，需要一并清空。
    setSmsNeedInvite(null);
    setInviteCode("");
  }

  function resetBindInviteRequirement() {
    // 中文注释：微信绑定手机号场景同样需要在手机号变化时清空旧判断结果，避免误把别的手机号状态带过来。
    setBindNeedInvite(null);
    setBindInviteCode("");
  }

  function resetTempRegisterPasswordCheckResult() {
    setTempRegisterPasswordCheckMessage("");
    setTempRegisterPasswordCheckAvailable(false);
  }

  async function ensureSmsInviteRequirement(targetMobile = smsPhone, options = {}) {
    const { showError = true } = options;

    // 中文注释：只有在手机号格式合法时才发起预检查，避免前端输入过程频繁请求后端。
    if (!validateMobile(targetMobile)) {
      resetSmsInviteRequirement();
      return null;
    }

    try {
      setInviteCheckLoading(true);
      const response = await checkSmsInviteRequired({ mobile: targetMobile });
      const needInvite = Boolean(response?.data?.need_invite_code);

      // 中文注释：把结果存进页面状态，后续发送验证码、提交登录、页面显示都复用这一份判断结果。
      setSmsNeedInvite(needInvite);
      return needInvite;
    } catch (error) {
      // 中文注释：这个预检查接口只负责控制邀请码输入框显示，不应该在所有场景都阻断主流程。
      // 例如“获取验证码”时，即使预检查暂时失败，也应尽量允许用户先拿到验证码，真正提交登录时再做强校验。
      if (showError) {
        setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
      }
      return null;
    } finally {
      setInviteCheckLoading(false);
    }
  }

  async function ensureBindInviteRequirement(targetMobile = bindPhone, options = {}) {
    const { showError = true } = options;

    // 中文注释：微信绑定手机号时需要同时依赖 bind_token 和手机号，因此两者缺一不可。
    if (!bindToken || !validateMobile(targetMobile)) {
      resetBindInviteRequirement();
      return null;
    }

    try {
      setBindInviteCheckLoading(true);
      const response = await checkWechatBindInviteRequired({
        bind_token: bindToken,
        mobile: targetMobile,
      });
      const needInvite = Boolean(response?.data?.need_invite_code);

      // 中文注释：绑定流程里同样把“是否需要邀请码”缓存到状态，避免页面和提交逻辑各算一遍。
      setBindNeedInvite(needInvite);
      return needInvite;
    } catch (error) {
      // 中文注释：绑定手机号场景与短信登录相同，预检查失败时不一定要直接阻断“获取验证码”按钮。
      if (showError) {
        setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
      }
      return null;
    } finally {
      setBindInviteCheckLoading(false);
    }
  }

  function saveAccessToken(token) {
    setAccessToken(token, rememberLogin);
  }

  function finishLogin(token) {
    saveAccessToken(token);
    navigate("/", { replace: true });
    window.setTimeout(() => {
      if (window.location.pathname === "/login") {
        window.location.replace("/");
      }
    }, 80);
  }

  function clearLoginQueryParams() {
    window.history.replaceState({}, document.title, "/login");
  }

  function replaceLoginQueryParams(updater) {
    const currentUrl = new URL(window.location.href);
    updater(currentUrl.searchParams);
    const nextQuery = currentUrl.searchParams.toString();
    const nextUrl = nextQuery ? `/login?${nextQuery}` : "/login";
    window.history.replaceState({}, document.title, nextUrl);
  }

  function handleLoginModeQuery() {
    const currentUrl = new URL(window.location.href);
    const mode = (currentUrl.searchParams.get("mode") || "").trim().toLowerCase();
    if (mode === "sms") {
      setActiveTab("sms");
      replaceLoginQueryParams((searchParams) => {
        searchParams.delete("mode");
      });
      return;
    }
    if (mode === "temp-register" || mode === "temp_register") {
      setActiveTab("temp_register");
      replaceLoginQueryParams((searchParams) => {
        searchParams.delete("mode");
      });
    }
  }

  function switchToSmsLogin(prefilledMobile = "") {
    clearMessages();
    resetSmsInviteRequirement();
    if (prefilledMobile) {
      setSmsPhone(prefilledMobile.replace(/\D/g, "").slice(0, 11));
    }
    setActiveTab("sms");
  }

  function switchToTempRegister(prefilledMobile = "") {
    clearMessages();
    if (prefilledMobile) {
      setTempRegisterPhone(prefilledMobile.replace(/\D/g, "").slice(0, 11));
    }
    setActiveTab("temp_register");
  }

  function handleSessionExpiredTip() {
    const currentUrl = new URL(window.location.href);
    if (currentUrl.searchParams.get("session_expired") === "1") {
      setErrorMessage("登录已过期，请重新登录");
      replaceLoginQueryParams((searchParams) => {
        searchParams.delete("session_expired");
      });
    }
  }

  async function handlePasswordLogin() {
    clearMessages();
    if (!validateMobile(phone)) return setErrorMessage("请输入正确的手机号");
    if (!password) return setErrorMessage("请输入密码");
    try {
      setLoading(true);
      const response = await passwordLogin({ mobile: phone, password });
      finishLogin(response.data.access_token);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  async function handleSendLoginCode() {
    clearMessages();
    if (!validateMobile(smsPhone)) return setErrorMessage("请输入正确的手机号");
    try {
      // 中文注释：发送验证码前先尝试刷新邀请码显隐状态，但这一步不应阻断验证码发送主流程。
      await ensureSmsInviteRequirement(smsPhone, { showError: false });
      setSendCodeLoading(true);
      await sendSmsCode({ mobile: smsPhone, biz_type: "login" });
      setSuccessMessage("验证码已发送，请注意查收");
      setCountdown(60);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setSendCodeLoading(false);
    }
  }

  async function handleSmsLogin() {
    clearMessages();
    if (!validateMobile(smsPhone)) return setErrorMessage("请输入正确的手机号");
    if (smsCode.length !== 6) return setErrorMessage("请输入 6 位验证码");
    const needInvite = smsNeedInvite === null ? await ensureSmsInviteRequirement(smsPhone) : smsNeedInvite;
    if (needInvite === null) return;
    if (needInvite && !inviteCode.trim()) return setErrorMessage("该手机号尚未注册，请输入邀请码");
    try {
      setLoading(true);
      const requestPayload = {
        mobile: smsPhone,
        code: smsCode,
      };

      // 中文注释：只有首次注册场景才把邀请码传给后端，已注册用户不再强制携带 invite_code。
      if (needInvite) {
        requestPayload.invite_code = inviteCode.trim();
      }

      const response = await smsLogin(requestPayload);
      finishLogin(response.data.access_token);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  async function handleTempRegisterLogin() {
    clearMessages();
    if (!validateMobile(tempRegisterPhone)) return setErrorMessage("请输入正确的手机号");
    if (!tempRegisterPassword) return setErrorMessage("请输入密码");
    if (!tempRegisterInviteCode.trim()) return setErrorMessage("请输入邀请码");
    const passwordValidationMessage = validatePasswordRule(tempRegisterPassword);
    if (passwordValidationMessage) return setErrorMessage(passwordValidationMessage);

    try {
      setLoading(true);
      const response = await tempRegisterLogin({
        mobile: tempRegisterPhone,
        password: tempRegisterPassword,
        invite_code: tempRegisterInviteCode.trim(),
      });
      finishLogin(response.data.access_token);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  function handleCheckTempRegisterPassword() {
    resetTempRegisterPasswordCheckResult();

    if (!tempRegisterPassword) {
      setTempRegisterPasswordCheckMessage("请输入密码");
      return;
    }

    const passwordValidationMessage = validatePasswordRule(tempRegisterPassword);
    if (passwordValidationMessage) {
      setTempRegisterPasswordCheckMessage(passwordValidationMessage);
      return;
    }

    setTempRegisterPasswordCheckAvailable(true);
    setTempRegisterPasswordCheckMessage("该密码可以使用");
  }

  async function handleWechatAuthorize() {
    clearMessages();
    try {
      setLoading(true);
      const response = await getWechatAuthorizeUrl();
      window.location.href = response.data.authorize_url;
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  async function handleWechatInviteRegister() {
    clearMessages();
    if (!registerToken) {
      setErrorMessage("\u6ce8\u518c\u51ed\u8bc1\u4e0d\u5b58\u5728\uff0c\u8bf7\u91cd\u65b0\u53d1\u8d77\u5fae\u4fe1\u767b\u5f55");
      return;
    }
    if (!wechatRegisterInviteCode.trim()) {
      setErrorMessage("\u8bf7\u8f93\u5165\u9080\u8bf7\u7801");
      return;
    }
    try {
      setLoading(true);
      const response = await wechatInviteRegister({
        register_token: registerToken,
        invite_code: wechatRegisterInviteCode.trim(),
      });
      finishLogin(response.data.access_token);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  async function handleSendBindCode() {
    clearMessages();
    if (!validateMobile(bindPhone)) return setErrorMessage("请输入正确的手机号");
    try {
      // 中文注释：发送绑定验证码前先尝试刷新邀请码显隐状态，但预检查失败不阻断验证码发送。
      await ensureBindInviteRequirement(bindPhone, { showError: false });
      setSendCodeLoading(true);
      await sendSmsCode({ mobile: bindPhone, biz_type: "bind_mobile" });
      setSuccessMessage("验证码已发送，请注意查收");
      setBindCountdown(60);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setSendCodeLoading(false);
    }
  }

  async function handleBindMobile() {
    clearMessages();
    if (!bindToken) return setErrorMessage("绑定凭证不存在，请重新发起微信登录");
    if (!validateMobile(bindPhone)) return setErrorMessage("请输入正确的手机号");
    if (bindCode.length !== 6) return setErrorMessage("请输入 6 位验证码");
    const needInvite = bindNeedInvite === null ? await ensureBindInviteRequirement(bindPhone) : bindNeedInvite;
    if (needInvite === null) return;
    if (needInvite && !bindInviteCode.trim()) return setErrorMessage("该手机号尚未注册，请输入邀请码");
    try {
      setLoading(true);
      const requestPayload = {
        bind_token: bindToken,
        mobile: bindPhone,
        code: bindCode,
      };

      // 中文注释：只有首次绑定新手机号时才需要邀请码，绑定已有手机号时直接走合并/绑定逻辑。
      if (needInvite) {
        requestPayload.invite_code = bindInviteCode.trim();
      }

      const response = await wechatBindMobile(requestPayload);
      finishLogin(response.data.access_token);
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    } finally {
      setLoading(false);
    }
  }

  async function handleWechatCallbackLogin() {
    const currentUrl = new URL(window.location.href);
    const code = currentUrl.searchParams.get("code");
    const state = currentUrl.searchParams.get("state");
    const wechatError = currentUrl.searchParams.get("wechat_error");
    if (wechatError) {
      setErrorMessage(`微信登录失败：${wechatError}`);
      clearLoginQueryParams();
      return;
    }
    if (!code || !state) return;
    try {
      setLoading(true);
      const response = await wechatLogin({ code, state });
      if (response.data.access_token) {
        finishLogin(response.data.access_token);
        return;
      }
      if (response.data.next_step === "wechat_invite_register") {
        setRegisterToken(response.data.register_token || "");
        setWechatRegisterInviteCode("");
        setActiveTab("wechat_invite_register");
        setSuccessMessage("\u8bf7\u5148\u586b\u5199\u9080\u8bf7\u7801\u5b8c\u6210\u6ce8\u518c");
        clearLoginQueryParams();
        return;
      }
      if (response.data.next_step === "bind_mobile") {
        setBindToken(response.data.bind_token);
        // 中文注释：切到微信绑定手机号界面前，先清空旧的邀请码判断状态，避免错误复用上一次手机号结果。
        resetBindInviteRequirement();
        setActiveTab("bind_mobile");
        setSuccessMessage("\u8bf7\u5148\u586b\u5199\u9080\u8bf7\u7801\u5b8c\u6210\u6ce8\u518c");
        clearLoginQueryParams();
      }
    } catch (error) {
      setErrorMessage(error?.response?.data?.detail || "\u9080\u8bf7\u7801\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
      clearLoginQueryParams();
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen w-full flex" style={{ background: "#ffffff" }}>
      {busyOverlay ? (
        <LoadingOverlay
          message={busyOverlay.message}
          submessage={busyOverlay.submessage}
        />
      ) : null}

      <motion.div
        className="hidden lg:flex relative flex-col justify-between p-12 overflow-hidden"
        style={{ background: "linear-gradient(135deg, #0a1220 0%, #0f1a2e 50%, #121826 100%)" }}
        animate={{ width: hoveredPanel === "left" ? "58%" : hoveredPanel === "right" ? "42%" : "52%" }}
        transition={{ type: "spring", stiffness: 300, damping: 30 }}
        onMouseEnter={() => setHoveredPanel("left")}
        onMouseLeave={() => setHoveredPanel(null)}
      >
        <motion.div
          className="absolute rounded-full pointer-events-none"
          style={{
            width: 500,
            height: 500,
            background: "radial-gradient(circle, rgba(79,142,247,0.18) 0%, transparent 70%)",
            top: "10%",
            left: "20%",
          }}
          animate={{ scale: [1, 1.15, 1], x: [0, 30, 0], y: [0, -20, 0] }}
          transition={{ duration: 10, repeat: Infinity, ease: "easeInOut" }}
        />
        <motion.div
          className="absolute rounded-full pointer-events-none"
          style={{
            width: 320,
            height: 320,
            background: "radial-gradient(circle, rgba(56,201,176,0.12) 0%, transparent 70%)",
            bottom: "15%",
            right: "10%",
          }}
          animate={{ scale: [1, 1.2, 1], x: [0, -20, 0], y: [0, 25, 0] }}
          transition={{ duration: 8, repeat: Infinity, ease: "easeInOut", delay: 2 }}
        />
        {particles.map((particle, index) => (
          <Particle key={index} {...particle} />
        ))}
        <div
          className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              "url(https://images.unsplash.com/photo-1547817651-7fb0cc360536?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx1bml2ZXJzaXR5JTIwY2FtcHVzJTIwc3R1ZGVudHMlMjBzdHVkeWluZ3xlbnwxfHx8fDE3NzM5NzU3Njh8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral)",
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        />
        <div className="absolute top-[-80px] right-[-80px] w-64 h-64 rounded-full opacity-10" style={{ background: "rgba(255,255,255,0.15)" }} />
        <div className="absolute bottom-[-60px] left-[-60px] w-48 h-48 rounded-full opacity-10" style={{ background: "rgba(255,255,255,0.1)" }} />
        <motion.div className="relative z-10 login-brand-text" initial={false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.1, ease: "easeOut" }}>
          <span className="login-brand-cn">录途</span>
          <span className="login-brand-en">LutoolBox</span>
        </motion.div>
        <div className="relative z-10 flex flex-col gap-8 items-center justify-center flex-1">
          <motion.div initial={false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, delay: 0.2, ease: "easeOut" }}>
            <h1 className="text-white mb-4" style={{ fontSize: "48px", fontWeight: 700, lineHeight: 1.3, letterSpacing: "-0.01em" }}>
              欢迎使用录途
              <br />
              你的留学申请工具包
            </h1>
            <p style={{ fontSize: "20px", color: "rgba(255,255,255,0.75)", lineHeight: 1.7, maxWidth: "480px" }}>
              备考、选校、申请、查分，一站式搞定。
            </p>
          </motion.div>
        </div>
        <motion.div className="relative z-10 flex gap-10" initial={false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.45 }}>
          <AnimatedStat target="4" label="登录方式" delay={0.55} />
          <AnimatedStat target="24h" label="随时访问" delay={0.65} />
          <AnimatedStat target="1站式" label="留学工具包" delay={0.75} />
        </motion.div>
        <motion.div className="relative z-10" initial={false} animate={{ opacity: 1 }} transition={{ duration: 0.6, delay: 1 }}>
          <p style={{ fontSize: "13px", color: "rgba(255,255,255,0.35)" }}>© 2026 录途 LutoolBox</p>
        </motion.div>
      </motion.div>

      <motion.div
        className="flex-1 flex flex-col items-center justify-center px-6 py-12 bg-white min-h-screen"
        initial={false}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.5 }}
        onMouseEnter={() => setHoveredPanel("right")}
        onMouseLeave={() => setHoveredPanel(null)}
      >
        <motion.div className="w-full login-page-panel" initial={false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.15, ease: "easeOut" }}>
          <div className="login-panel-header">
            <h2 className="login-panel-title">{displayPanelTitle}</h2>
            <p className="login-panel-subtitle">{displayPanelDescription}</p>
          </div>
          <MessageBlock errorMessage={errorMessage} successMessage={successMessage} />

          {activeTab !== "bind_mobile" && activeTab !== "wechat_invite_register" ? (
            <div className="login-tabbar flex rounded-lg mb-7 p-1 relative">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => {
                    // 中文注释：切换登录方式时顺手清理邀请码判断状态，避免某一种登录方式的判断结果污染另一种方式。
                    clearMessages();
                    resetSmsInviteRequirement();
                    resetBindInviteRequirement();
                    setActiveTab(tab.key);
                  }}
                  className="login-tab flex-1 py-2 rounded-md relative z-10"
                  type="button"
                >
                  {activeTab === tab.key ? <motion.div layoutId="tab-indicator" className="absolute inset-0 rounded-md login-tab-active-bg" transition={{ type: "spring", stiffness: 380, damping: 32 }} /> : null}
                  <span className="relative z-10">{tab.label}</span>
                </button>
              ))}
            </div>
          ) : null}

          <AnimatePresence mode="wait">
            {activeTab === "password" ? (
              <motion.div key="password" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col gap-4 login-form-panel">
                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">手机号</label>
                  <AnimatedInput focused={phoneFocused}>
                    <MobilePrefix />
                    <input type="tel" placeholder="请输入手机号" maxLength={11} value={phone} onChange={(event) => setPhone(event.target.value.replace(/\D/g, ""))} onFocus={() => setPhoneFocused(true)} onBlur={() => setPhoneFocused(false)} className="login-plain-input flex-1 outline-none bg-transparent" />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">密码</label>
                  <AnimatedInput focused={passFocused}>
                    <PasswordIcon />
                    <input type={showPassword ? "text" : "password"} placeholder="请输入密码" value={password} onChange={(event) => setPassword(event.target.value)} onFocus={() => setPassFocused(true)} onBlur={() => setPassFocused(false)} className="login-plain-input flex-1 outline-none bg-transparent" />
                    <button type="button" onClick={() => setShowPassword((value) => !value)} className="login-inline-button">
                      <EyeIcon />
                    </button>
                  </AnimatedInput>
                </div>

                <div className="flex items-center justify-between login-option-row">
                  <label className="flex items-center gap-2 cursor-pointer login-checkbox-label">
                    <input type="checkbox" checked={rememberLogin} onChange={(event) => setRememberLogin(event.target.checked)} className="login-checkbox" />
                    <span>记住我</span>
                  </label>
                  <button type="button" onClick={() => switchToSmsLogin(phone)} className="login-link-button">
                    忘记密码？
                  </button>
                </div>

                <motion.button type="button" onClick={handlePasswordLogin} className="login-submit-button w-full py-3 rounded-2xl mt-1" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }} whileHover={!loading ? { scale: 1.015, boxShadow: "0 6px 20px rgba(26,39,68,0.35)" } : {}} whileTap={!loading ? { scale: 0.98 } : {}}>
                  登录
                </motion.button>
              </motion.div>
            ) : null}

            {activeTab === "sms" ? (
              <motion.div key="sms" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col gap-4 login-form-panel">
                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">手机号</label>
                  <AnimatedInput focused={smsPhoneFocused}>
                    <MobilePrefix />
                    <input
                      type="tel"
                      placeholder="请输入手机号"
                      maxLength={11}
                      value={smsPhone}
                      onChange={(event) => {
                        const nextMobile = event.target.value.replace(/\D/g, "");
                        setSmsPhone(nextMobile);
                        resetSmsInviteRequirement();
                      }}
                      onFocus={() => setSmsPhoneFocused(true)}
                      onBlur={async () => {
                        setSmsPhoneFocused(false);
                        // 中文注释：失焦预检查只用于更新界面，不主动弹错误，避免用户还在输入阶段就看到干扰性报错。
                        await ensureSmsInviteRequirement(smsPhone, { showError: false });
                      }}
                      className="login-plain-input flex-1 outline-none bg-transparent"
                    />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">验证码</label>
                  <div className="flex gap-3">
                    <AnimatedInput focused={codeFocused}>
                      <SmsIcon />
                      <input type="text" placeholder="请输入验证码" maxLength={6} value={smsCode} onChange={(event) => setSmsCode(event.target.value.replace(/\D/g, ""))} onFocus={() => setCodeFocused(true)} onBlur={() => setCodeFocused(false)} className="login-plain-input flex-1 outline-none bg-transparent" />
                    </AnimatedInput>
                    <motion.button type="button" onClick={handleSendLoginCode} className="login-code-button rounded-2xl px-4 whitespace-nowrap" style={{ cursor: countdown > 0 || smsPhone.length !== 11 || sendCodeLoading ? "not-allowed" : "pointer" }}>
                      {countdown > 0 ? `${countdown}s 后重发` : "获取验证码"}
                    </motion.button>
                  </div>
                </div>

                {smsNeedInvite ? (
                  <div className="flex flex-col gap-1.5">
                    <label className="login-field-label">邀请码</label>
                    <AnimatedInput focused={inviteFocused}>
                      <input
                        type="text"
                        placeholder="请输入邀请码"
                        value={inviteCode}
                        onChange={(event) => setInviteCode(event.target.value)}
                        onFocus={() => setInviteFocused(true)}
                        onBlur={() => setInviteFocused(false)}
                        className="login-plain-input flex-1 outline-none bg-transparent"
                      />
                    </AnimatedInput>
                  </div>
                ) : null}

                <p className="login-helper-text">
                  {smsNeedInvite === true
                      ? "该手机号尚未注册，首次注册需填写邀请码。"
                      : smsNeedInvite === false
                        ? "该手机号已注册，可直接使用验证码登录。"
                        : "输入手机号后会自动判断是否需要邀请码。"}
                </p>

                <motion.button type="button" onClick={handleSmsLogin} className="login-submit-button w-full py-3 rounded-2xl mt-2" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }}>
                  登录 / 注册
                </motion.button>
              </motion.div>
            ) : null}

            {activeTab === "temp_register" ? (
              <motion.div key="temp_register" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col gap-4 login-form-panel">
                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">手机号</label>
                  <AnimatedInput focused={tempRegisterPhoneFocused}>
                    <MobilePrefix />
                    <input
                      type="tel"
                      placeholder="请输入手机号"
                      maxLength={11}
                      value={tempRegisterPhone}
                      onChange={(event) => setTempRegisterPhone(event.target.value.replace(/\D/g, ""))}
                      onFocus={() => setTempRegisterPhoneFocused(true)}
                      onBlur={() => setTempRegisterPhoneFocused(false)}
                      className="login-plain-input flex-1 outline-none bg-transparent"
                    />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">密码</label>
                  <div className="flex gap-3">
                    <AnimatedInput focused={tempRegisterPasswordFocused}>
                      <PasswordIcon />
                      <input
                        type={showPassword ? "text" : "password"}
                        placeholder="请输入密码"
                        value={tempRegisterPassword}
                        onChange={(event) => {
                          setTempRegisterPassword(event.target.value);
                          resetTempRegisterPasswordCheckResult();
                        }}
                        onFocus={() => setTempRegisterPasswordFocused(true)}
                        onBlur={() => setTempRegisterPasswordFocused(false)}
                        className="login-plain-input flex-1 outline-none bg-transparent"
                      />
                      <button type="button" onClick={() => setShowPassword((value) => !value)} className="login-inline-button">
                        <EyeIcon />
                      </button>
                    </AnimatedInput>
                    <motion.button type="button" onClick={handleCheckTempRegisterPassword} className="login-code-button rounded-2xl px-4 whitespace-nowrap" style={{ cursor: !tempRegisterPassword ? "not-allowed" : "pointer" }}>
                      检查是否可用
                    </motion.button>
                  </div>
                </div>

                {tempRegisterPasswordCheckMessage ? (
                  <p className={`check-message ${tempRegisterPasswordCheckAvailable ? "check-success" : "check-error"}`}>
                    {tempRegisterPasswordCheckMessage}
                  </p>
                ) : null}

                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">邀请码</label>
                  <AnimatedInput focused={tempRegisterInviteFocused}>
                    <input
                      type="text"
                      placeholder="请输入邀请码"
                      value={tempRegisterInviteCode}
                      onChange={(event) => setTempRegisterInviteCode(event.target.value)}
                      onFocus={() => setTempRegisterInviteFocused(true)}
                      onBlur={() => setTempRegisterInviteFocused(false)}
                      className="login-plain-input flex-1 outline-none bg-transparent"
                    />
                  </AnimatedInput>
                </div>

                <p className="login-helper-text">
                  仅限未注册手机号使用，提交后会直接完成注册并登录。密码需满足 8-24 位且至少包含两类字符。
                </p>

                <motion.button type="button" onClick={handleTempRegisterLogin} className="login-submit-button w-full py-3 rounded-2xl mt-1" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }} whileHover={!loading ? { scale: 1.015, boxShadow: "0 6px 20px rgba(26,39,68,0.35)" } : {}} whileTap={!loading ? { scale: 0.98 } : {}}>
                  注册并登录
                </motion.button>
              </motion.div>
            ) : null}

            {activeTab === "wechat" ? (
              <motion.div key="wechat" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col items-center gap-5 login-form-panel">
                <div style={{ display: "none" }}>
                  <div className="login-wechat-placeholder relative">
                    <ScannerLine />
                    <div className="login-wechat-placeholder-badge">已接入</div>
                  </div>
                </div>

                <div className="text-center" style={{ display: "none" }}>
                  <p className="login-wechat-title">使用 <span style={{ color: "#07c160", fontWeight: 600 }}>微信</span> 扫码登录</p>
                  <p className="login-wechat-subtitle">点击下方按钮后，将跳转到微信开放平台官方扫码确认页</p>
                </div>

                <motion.button type="button" onClick={handleWechatAuthorize} className="login-wechat-button w-full py-3 rounded-2xl" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }}>
                  打开微信扫码登录
                </motion.button>

                <div className="flex items-center gap-3 w-full">
                  <div className="flex-1 h-px" style={{ background: "#e5e7eb" }} />
                  <span style={{ fontSize: "12px", color: "#9ca3af" }}>或选择其他方式</span>
                  <div className="flex-1 h-px" style={{ background: "#e5e7eb" }} />
                </div>

                <div className="flex gap-3 w-full">
                  {[{ label: "密码登录", tab: "password" }, { label: "验证码登录", tab: "sms" }].map(({ label, tab }) => (
                    <motion.button key={tab} type="button" onClick={() => setActiveTab(tab)} className="login-alt-button flex-1 py-2.5 rounded-2xl" style={{ cursor: "pointer" }}>
                      {label}
                    </motion.button>
                  ))}
                </div>
              </motion.div>
            ) : null}

            {activeTab === "wechat_invite_register" ? (
              <motion.div key="wechat_invite_register" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col gap-4 login-form-panel">
                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">邀请码</label>
                  <AnimatedInput focused={wechatRegisterInviteFocused}>
                    <input
                      type="text"
                      placeholder="请输入邀请码"
                      value={wechatRegisterInviteCode}
                      onChange={(event) => setWechatRegisterInviteCode(event.target.value)}
                      onFocus={() => setWechatRegisterInviteFocused(true)}
                      onBlur={() => setWechatRegisterInviteFocused(false)}
                      className="login-plain-input flex-1 outline-none bg-transparent"
                    />
                  </AnimatedInput>
                </div>

                <p className="login-helper-text">
                  首次使用微信扫码时，填写邀请码即可完成注册并直接登录。手机号可在登录后到用户信息页绑定。
                </p>

                <motion.button type="button" onClick={handleWechatInviteRegister} className="login-submit-button w-full py-3 rounded-2xl mt-1" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }}>
                  完成注册并登录
                </motion.button>

                <button
                  type="button"
                  onClick={() => {
                    clearMessages();
                    setRegisterToken("");
                    setWechatRegisterInviteCode("");
                    setActiveTab("wechat");
                  }}
                  className="login-link-button self-start"
                >
                  返回微信扫码
                </button>
              </motion.div>
            ) : null}

            {activeTab === "bind_mobile" ? (
              <motion.div key="bind_mobile" variants={formVariants} initial="initial" animate="animate" exit="exit" transition={{ duration: 0.22, ease: "easeInOut" }} className="flex flex-col gap-4 login-form-panel">
                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">手机号</label>
                  <AnimatedInput focused={bindPhoneFocused}>
                    <MobilePrefix />
                    <input
                      type="tel"
                      placeholder="请输入手机号"
                      maxLength={11}
                      value={bindPhone}
                      onChange={(event) => {
                        const nextMobile = event.target.value.replace(/\D/g, "");
                        setBindPhone(nextMobile);
                        resetBindInviteRequirement();
                      }}
                      onFocus={() => setBindPhoneFocused(true)}
                      onBlur={async () => {
                        setBindPhoneFocused(false);
                        // 中文注释：绑定手机号输入框失焦时同样只做静默预检查，不用错误提示打断操作。
                        await ensureBindInviteRequirement(bindPhone, { showError: false });
                      }}
                      className="login-plain-input flex-1 outline-none bg-transparent"
                    />
                  </AnimatedInput>
                </div>

                <div className="flex flex-col gap-1.5">
                  <label className="login-field-label">验证码</label>
                  <div className="flex gap-3">
                    <AnimatedInput focused={bindCodeFocused}>
                      <SmsIcon />
                      <input type="text" placeholder="请输入验证码" maxLength={6} value={bindCode} onChange={(event) => setBindCode(event.target.value.replace(/\D/g, ""))} onFocus={() => setBindCodeFocused(true)} onBlur={() => setBindCodeFocused(false)} className="login-plain-input flex-1 outline-none bg-transparent" />
                    </AnimatedInput>
                    <motion.button type="button" onClick={handleSendBindCode} className="login-code-button rounded-2xl px-4 whitespace-nowrap" style={{ cursor: bindCountdown > 0 || bindPhone.length !== 11 || sendCodeLoading ? "not-allowed" : "pointer" }}>
                      {bindCountdown > 0 ? `${bindCountdown}s 后重发` : "获取验证码"}
                    </motion.button>
                  </div>
                </div>

                {bindNeedInvite ? (
                  <div className="flex flex-col gap-1.5">
                    <label className="login-field-label">邀请码</label>
                    <AnimatedInput focused={bindInviteFocused}>
                      <input
                        type="text"
                        placeholder="请输入邀请码"
                        value={bindInviteCode}
                        onChange={(event) => setBindInviteCode(event.target.value)}
                        onFocus={() => setBindInviteFocused(true)}
                        onBlur={() => setBindInviteFocused(false)}
                        className="login-plain-input flex-1 outline-none bg-transparent"
                      />
                    </AnimatedInput>
                  </div>
                ) : null}

                <p className="login-helper-text">
                  {bindNeedInvite === true
                      ? "该手机号尚未注册，首次绑定需填写邀请码。"
                      : bindNeedInvite === false
                        ? "该手机号已注册，可直接绑定，无需邀请码。"
                        : "输入手机号后会自动判断是否需要邀请码。"}
                </p>

                <motion.button type="button" onClick={handleBindMobile} className="login-submit-button w-full py-3 rounded-2xl mt-1" style={{ cursor: loading ? "wait" : "pointer", opacity: loading ? 0.7 : 1 }}>
                  完成绑定
                </motion.button>
              </motion.div>
            ) : null}
          </AnimatePresence>

          {activeTab !== "wechat" ? (
            <motion.p className="mt-5 text-center login-agreement-text" initial={false} animate={{ opacity: 1 }} transition={{ duration: 0.2 }}>
              登录即表示同意{" "}
              <button type="button" onClick={() => window.alert("用户协议暂未接入")} className="login-link-button">
                用户协议
              </button>{" "}
              与{" "}
              <button type="button" onClick={() => window.alert("隐私政策暂未接入")} className="login-link-button">
                隐私政策
              </button>
            </motion.p>
          ) : null}

          <div className="mt-6 pt-6 text-center login-register-row">
            <p className="login-register-text">
              还没有账号？{" "}
              <button type="button" onClick={() => switchToTempRegister(phone || smsPhone)} className="login-register-button">
                免费注册
              </button>
            </p>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}

