import React, { useEffect, useMemo, useRef, useState } from "react";
import { motion, AnimatePresence } from "motion/react";
import { useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Bell,
  BookOpen,
  Calculator,
  GraduationCap,
  Home,
  Link2,
  Send,
  Settings,
  Sparkles,
  TrendingUp,
  UserRound,
  X,
} from "lucide-react";
import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
} from "recharts";
import { getMe } from "../api/auth";
import {
  buildAiChatProfile,
  buildAiChatWsUrl,
  getAiChatMessages,
  getAiChatResult,
  getAiChatSessionDetail,
  getCurrentAiChatSession,
} from "../api/aiChat";
import RankingTool from "../components/RankingTool";
import ScoreConverter from "../components/ScoreConverter";

const AI_CHAT_BIZ_DOMAIN = "student_profile_build";
const AI_CHAT_SESSION_CACHE_KEY = "latest_ai_chat_session_id";
const AI_CHAT_OPEN_PANEL_KEY = "open_ai_chat_panel";

const examLinks = [
  { name: "托福报名", url: "https://www.ets.org/toefl", accent: "#2c4a8a", badge: "TOEFL" },
  { name: "雅思报名", url: "https://www.ielts.org", accent: "#10b981", badge: "IELTS" },
  { name: "GRE Academic 报名", url: "https://www.ets.org/gre", accent: "#f59e0b", badge: "GRE A" },
  { name: "GRE General Test 报名", url: "https://www.ets.org/gre", accent: "#8b5cf6", badge: "GRE G" },
  { name: "LanguageCert Academic 报名", url: "https://www.languagecert.org", accent: "#ec4899", badge: "LCA" },
];

const sectionItems = [
  { key: "hero", label: "首页介绍", sectionId: "home-hero", icon: Home },
  { key: "tools", label: "快捷小工具", sectionId: "home-tools", icon: Calculator },
  { key: "exam", label: "考试报名", sectionId: "home-exams", icon: BookOpen },
  { key: "ranking", label: "大学排名", sectionId: "home-ranking", icon: TrendingUp },
];

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

function createChatMessage(role, content, extra = {}) {
  return {
    id: extra.id || `${role}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    isStreaming: Boolean(extra.isStreaming),
    deliveryStatus: extra.deliveryStatus || "sent",
  };
}

function sleep(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function logProfileFlow(step, detail = {}) {
  console.info(`[前端六维图][${step}]`, detail);
}

function normalizeProfileResult(resultData) {
  const radarScores = resultData?.radar_scores_json || {};

  return {
    radar_scores_json: {
      academic: radarScores.academic || { score: 0, reason: "暂无有效学术评分说明" },
      language: radarScores.language || { score: 0, reason: "暂无有效语言评分说明" },
      standardized: radarScores.standardized || { score: 0, reason: "暂无有效标化评分说明" },
      competition: radarScores.competition || { score: 0, reason: "暂无有效竞赛评分说明" },
      activity: radarScores.activity || { score: 0, reason: "暂无有效活动评分说明" },
      project: radarScores.project || { score: 0, reason: "暂无有效项目评分说明" },
    },
    summary_text: resultData?.summary_text || "当前档案结果已生成，但暂未返回完整中文总结。",
  };
}

function normalizeVisibleMessages(items) {
  return (items || [])
    .filter((item) => item.message_role === "user" || item.message_role === "assistant")
    .map((item) => createChatMessage(item.message_role, item.content, { id: item.id }));
}

function SectionNavButton({ item, isActive, onClick }) {
  const Icon = item.icon;

  return (
    <motion.button
      type="button"
      onClick={onClick}
      className="home-nav-button"
      style={{
        color: isActive ? "#1a2744" : "#6b7280",
        background: isActive ? "#f0f4ff" : "transparent",
      }}
      whileHover={{ background: isActive ? "#f0f4ff" : "#f9fafb" }}
      whileTap={{ scale: 0.98 }}
    >
      <Icon size={18} strokeWidth={2} />
      <span className="home-nav-button-label">{item.label}</span>
      {isActive ? (
        <motion.div
          layoutId="home-section-indicator"
          className="home-nav-indicator"
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      ) : null}
    </motion.button>
  );
}

function TopActionButton({ label, children }) {
  return (
    <motion.button
      type="button"
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
      className="home-top-icon-button"
      aria-label={label}
    >
      {children}
    </motion.button>
  );
}

function BrandBlock() {
  return (
    <div className="home-brand">
      <div className="home-brand-mark">录</div>
      <div className="home-brand-text">录途 Toolbox</div>
    </div>
  );
}

function BellIcon() {
  return <Bell size={20} strokeWidth={2} color="#4b5563" />;
}

function SettingsIcon() {
  return <Settings size={20} strokeWidth={2} color="#4b5563" />;
}

function ArrowRightIcon() {
  return <ArrowRight size={20} strokeWidth={2.2} />;
}

function getStageDisplayLabel(stage) {
  const stageLabelMap = {
    idle: "待开始",
    conversation: "对话中",
    progress_updating: "更新进度",
    extraction: "结构化提取中",
    scoring: "六维评分中",
    profile_saving: "档案创建中",
    build_ready: "可建档",
    completed: "已完成",
    failed: "生成异常",
  };

  return stageLabelMap[stage] || stage;
}

function getProfileStatusText(resultStatus) {
  if (resultStatus === "generated") {
    return "六维图已生成";
  }
  if (resultStatus === "saved") {
    return "六维图已生成";
  }
  if (resultStatus === "failed") {
    return "六维图已生成";
  }
  return "结果已生成";
}

function getArchiveStatusText(resultStatus) {
  // 中文注释：
  // 六维图结果和正式档案保存不是同一个阶段。
  // 这里单独展示档案状态，让用户能明确区分“六维图已生成”和“档案还在后台创建”。
  if (resultStatus === "generated") {
    return "档案创建中";
  }
  if (resultStatus === "saved") {
    return "档案创建完成";
  }
  if (resultStatus === "failed") {
    return "档案创建失败";
  }
  return "等待创建";
}

export default function HomePage() {
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [activeSection, setActiveSection] = useState("hero");
  const [showChat, setShowChat] = useState(false);
  const [showProfileResult, setShowProfileResult] = useState(false);
  const [chatEnded, setChatEnded] = useState(false);
  const [profileData, setProfileData] = useState(null);

  const [aiSessionId, setAiSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState("");
  const [currentStage, setCurrentStage] = useState("idle");
  const [assistantStreaming, setAssistantStreaming] = useState(false);
  const [assistantThinking, setAssistantThinking] = useState(false);

  const [missingDimensions, setMissingDimensions] = useState([]);
  const [dimensionProgress, setDimensionProgress] = useState({});
  const [nextQuestionFocus, setNextQuestionFocus] = useState(null);
  const [pendingProfileData, setPendingProfileData] = useState(null);
  const [profileResultStatus, setProfileResultStatus] = useState(null);
  const [saveErrorMessage, setSaveErrorMessage] = useState("");
  const [queuedOutgoingMessages, setQueuedOutgoingMessages] = useState([]);

  const [uiHint, setUiHint] = useState("");
  const [connectionError, setConnectionError] = useState("");
  const [createProfileLoading, setCreateProfileLoading] = useState(false);

  const wsRef = useRef(null);
  const connectPromiseRef = useRef(null);
  const assistantStreamingMessageIdRef = useRef(null);
  const sessionIdRef = useRef(null);
  const currentStageRef = useRef("idle");
  const nextQuestionFocusRef = useRef(null);
  const chatEndedRef = useRef(false);
  const assistantStreamingRef = useRef(false);
  const assistantThinkingRef = useRef(false);
  const createProfileLoadingRef = useRef(false);
  const profileBuildPollingRef = useRef(false);
  const queuedOutgoingMessagesRef = useRef([]);
  const queueFlushingRef = useRef(false);
  const chatBodyRef = useRef(null);

  useEffect(() => {
    sessionIdRef.current = aiSessionId;
  }, [aiSessionId]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // WebSocket onmessage 鍦ㄥ缓杩炴椂鍙粦瀹氫竴娆°€?    // 濡傛灉浜嬩欢澶勭悊鐩存帴璇诲彇 React state锛屽悗缁緢瀹规槗鎷垮埌寤鸿繛鐬棿鐨勬棫鍊笺€?    // 杩欓噷鐢?ref 鍚屾鏈€鏂伴樁娈碉紝淇濊瘉浜嬩欢鍥炶皟鎷垮埌鐨勬槸瀹炴椂鐘舵€併€?    currentStageRef.current = currentStage;
  }, [currentStage]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // 涓嬩竴杞拷闂劍鐐逛細琚?progress_updated 涓嶆柇鍒锋柊銆?    // 鍚庣画 stage_changed 闇€瑕佷緷璧栬繖涓€兼潵鐢熸垚鎻愮ず鏂囨锛屽洜姝ゅ悓姝ュ埌 ref銆?    nextQuestionFocusRef.current = nextQuestionFocus;
  }, [nextQuestionFocus]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // chatEnded 琛ㄧず绯荤粺鏄惁宸插垽鏂€滀俊鎭冻澶燂紝鍙互杩涘叆寤烘。缁撴灉闃舵鈥濄€?    // WebSocket 浜嬩欢澶勭悊鍚屾牱浼氳鍙栧畠锛屾墍浠ラ渶瑕佷繚鎸?ref 涓篃鏄渶鏂板€笺€?    chatEndedRef.current = chatEnded;
  }, [chatEnded]);

  useEffect(() => {
    if (!showChat || !chatBodyRef.current) {
      return;
    }

    window.requestAnimationFrame(() => {
      if (!chatBodyRef.current) {
        return;
      }
      chatBodyRef.current.scrollTop = chatBodyRef.current.scrollHeight;
    });
  }, [messages, showChat, assistantStreaming]);

  useEffect(() => {
    if (!profile?.user_id) {
      return;
    }

    if (localStorage.getItem(AI_CHAT_OPEN_PANEL_KEY) !== "1") {
      return;
    }

    localStorage.removeItem(AI_CHAT_OPEN_PANEL_KEY);
    handleStartChat();
  }, [profile?.user_id]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // 鑷姩缁彂鎺掗槦娑堟伅鏃讹紝涔熻鎷垮埌鏈€鏂扮殑鈥滄槸鍚︿粛鍦ㄦ祦寮忕敓鎴愨€濈姸鎬併€?    assistantStreamingRef.current = assistantStreaming;
  }, [assistantStreaming]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // thinking 鐘舵€佺敱澶氱 socket 浜嬩欢鍏卞悓椹卞姩锛岃嚜鍔ㄧ画鍙戞椂涓嶈兘璇绘棫鍊笺€?    assistantThinkingRef.current = assistantThinking;
  }, [assistantThinking]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // 鈥滅珛鍗冲缓妗ｂ€濋樁娈典細鐭殏杩涘叆 loading锛岃繖鏃朵笉鑳界户缁嚜鍔ㄥ彂鎺掗槦娑堟伅銆?    createProfileLoadingRef.current = createProfileLoading;
  }, [createProfileLoading]);

  useEffect(() => {
    // 涓枃娉ㄩ噴锛?    // 鍓嶇杩欓噷缁存姢涓€涓€滃緟鍙戦€侀槦鍒椻€濄€?    // 褰撲笂涓€杞繕鍦?assistant/progress/extraction 闃舵鏃讹紝
    // 鐢ㄦ埛鐐瑰嚮鍙戦€佷笉浼氱洿鎺ヤ涪寮冿紝鑰屾槸鍏堣繘鍏ヨ繖涓槦鍒楋紝绛夌郴缁熷洖鍒板彲鍙戦€佺姸鎬佸悗鑷姩鍙戝嚭銆?    queuedOutgoingMessagesRef.current = queuedOutgoingMessages;
  }, [queuedOutgoingMessages]);

  const radarChartData = useMemo(() => {
    if (!profileData) {
      return [];
    }

    return Object.entries(profileData.radar_scores_json).map(([key, value]) => ({
      subject: RADAR_LABELS[key] || key,
      score: value.score,
      fullMark: 100,
    }));
  }, [profileData]);

  const hasGeneratedProfile = Boolean(profileData || pendingProfileData || profileResultStatus);
  const isArchiveCreating = currentStage === "profile_saving" || profileResultStatus === "generated";
  const canChatInCurrentStage =
    currentStage === "idle" ||
    currentStage === "conversation" ||
    currentStage === "build_ready" ||
    currentStage === "completed" ||
    currentStage === "failed";
  const isRoundProcessing =
    assistantStreaming ||
    assistantThinking ||
    createProfileLoading ||
    currentStage === "progress_updating" ||
    currentStage === "extraction" ||
    currentStage === "scoring" ||
    currentStage === "profile_saving";
  const isChatBusy =
    assistantStreaming ||
    createProfileLoading ||
    (assistantThinking && currentStage !== "build_ready");
  const isBuildProfileBlocked =
    createProfileLoading ||
    assistantStreaming ||
    assistantThinking ||
    currentStage === "progress_updating" ||
    currentStage === "extraction" ||
    currentStage === "scoring" ||
    currentStage === "profile_saving";

  useEffect(() => {
    // 中文注释：
    // 这里保留一个自动续发兜底。
    // 如果历史状态里仍残留待发送消息，并且当前已经重新回到可发送阶段，
    // 就主动触发一次 flush，避免旧消息永远卡在队列里。
    if (!profile?.user_id) {
      return;
    }

    if (queuedOutgoingMessages.length === 0) {
      return;
    }

    if (!canChatInCurrentStage || isChatBusy) {
      return;
    }

    window.setTimeout(() => {
      void flushQueuedMessages(profile.user_id);
    }, 0);
  }, [
    queuedOutgoingMessages,
    currentStage,
    assistantStreaming,
    assistantThinking,
    createProfileLoading,
    profile?.user_id,
    canChatInCurrentStage,
    isChatBusy,
  ]);

  useEffect(() => {
    // 中文注释：
    // 旧会话可能停留在 completed / failed。
    // 当前新流程里，只要用户回到聊天页，就允许继续补充信息，
    // 因此这里统一把 completed / failed 拉回 build_ready，恢复输入能力。
    if (showChat && (currentStage === "completed" || currentStage === "failed")) {
      setCurrentStage("build_ready");
      currentStageRef.current = "build_ready";
      setChatEnded(true);
      chatEndedRef.current = true;
    }
  }, [showChat, currentStage]);

  function getDisplayName() {
    return profile?.nickname || profile?.mobile || "用户";
  }

  function getDisplaySubName() {
    return profile?.mobile || "已登录用户";
  }

  function getDisplayInitial() {
    const source = (profile?.nickname || profile?.mobile || "U").trim();
    return source.charAt(0).toUpperCase();
  }

  function scrollToSection(sectionId, key) {
    setActiveSection(key);
    const offset = 64;
    const element = document.getElementById(sectionId);

    if (!element) {
      return;
    }

    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - offset;

    window.scrollTo({
      top: offsetPosition,
      behavior: "smooth",
    });
  }

  useEffect(() => {
    let cancelled = false;

    async function bootstrapPage() {
      try {
        const response = await getMe();
        if (cancelled) {
          return;
        }

        setProfile(response.data);

        if (response.data?.user_id) {
          await restoreAiState(response.data.user_id);
        }
      } catch (error) {
        if (cancelled) {
          return;
        }

        if (error?.response?.status === 401) {
          localStorage.removeItem("access_token");
          navigate("/login", { replace: true });
          return;
        }

        setConnectionError(error?.response?.data?.detail || "首页初始化失败，请稍后重试。");
      }
    }

    bootstrapPage();

    return () => {
      cancelled = true;

      // 中文注释：
      // 离开首页时主动关闭 WebSocket，避免旧连接残留。
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [navigate]);

  async function restoreAiState(studentId) {
    try {
      const currentResponse = await getCurrentAiChatSession(AI_CHAT_BIZ_DOMAIN);
      const currentSessionId = currentResponse.data?.session?.session_id || null;
      const rememberedSessionId = localStorage.getItem(AI_CHAT_SESSION_CACHE_KEY);
      const candidateSessionIds = Array.from(new Set([currentSessionId, rememberedSessionId].filter(Boolean)));

      if (candidateSessionIds.length === 0) {
        return;
      }

      const sessionDetails = (
        await Promise.all(
          candidateSessionIds.map(async (sessionId) => {
            try {
              const response = await getAiChatSessionDetail(sessionId);
              return response.data || null;
            } catch (error) {
              console.warn("恢复 AI 会话详情失败", sessionId, error);
              return null;
            }
          })
        )
      ).filter(Boolean);

      const sessionDetail =
        sessionDetails.find((item) => item?.final_profile_id) ||
        sessionDetails.find((item) => item?.session_id === currentSessionId) ||
        sessionDetails[0];

      if (!sessionDetail?.session_id) {
        return;
      }

      const targetSessionId = sessionDetail.session_id;
      const messagesResponse = await getAiChatMessages(targetSessionId, { limit: 100 });
      const restoredMessages = normalizeVisibleMessages(messagesResponse.data?.items || []);
      const normalizedStage =
        sessionDetail.final_profile_id &&
        (sessionDetail.current_stage === "completed" || sessionDetail.current_stage === "failed")
          ? "build_ready"
          : sessionDetail.current_stage || "idle";

      setAiSessionId(sessionDetail.session_id);
      sessionIdRef.current = sessionDetail.session_id;
      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, sessionDetail.session_id);
      setMessages(restoredMessages);
      setMissingDimensions(sessionDetail.missing_dimensions || []);
      setCurrentStage(normalizedStage);
      currentStageRef.current = normalizedStage;

      if (restoredMessages.length > 0) {
        setShowChat(true);
      }

      if (sessionDetail.final_profile_id) {
        try {
          const resultResponse = await getAiChatResult(targetSessionId);
          const normalizedResult = normalizeProfileResult(resultResponse.data);
          const restoredResultStatus = resultResponse.data?.result_status || null;

          setPendingProfileData(normalizedResult);
          setProfileData(normalizedResult);
          setProfileResultStatus(restoredResultStatus);
          setSaveErrorMessage(resultResponse.data?.save_error_message || "");
          setShowChat(false);
          setShowProfileResult(false);
          setChatEnded(true);
          chatEndedRef.current = true;

          if (restoredResultStatus === "generated" || normalizedStage === "profile_saving") {
            setCreateProfileLoading(true);
            createProfileLoadingRef.current = true;
            setUiHint("六维图结果已恢复，档案正在后台继续创建。");
            logProfileFlow("恢复档案创建中状态", {
              sessionId: targetSessionId,
              currentStage: normalizedStage,
              resultStatus: restoredResultStatus,
            });
            void waitForProfileGenerationResult(targetSessionId, true);
          } else if (restoredResultStatus === "saved") {
            setUiHint("已恢复你上一次生成的六维图结果。");
          } else if (normalizedStage === "build_ready") {
            setUiHint("");
          } else {
            setUiHint("已恢复上一次建档结果，但正式保存存在异常。");
          }
        } catch (error) {
          console.error("恢复 AI 建档结果失败", error);
        }
      } else if (restoredMessages.length > 0) {
        setUiHint("已恢复你上一次的对话记录。");
      }

      if (!studentId) {
        setConnectionError("当前缺少学生身份信息，暂时无法初始化 AI 对话。");
      }
    } catch (error) {
      setConnectionError(error?.message || "恢复 AI 会话失败，请稍后重试。");
      console.error("恢复 AI 会话失败", error);
    }
  }

  async function ensureSocketConnected(studentId) {
    if (!studentId) {
      throw new Error("缺少 student_id，无法初始化 AI 对话连接。");
    }

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN && sessionIdRef.current) {
      return sessionIdRef.current;
    }

    if (connectPromiseRef.current) {
      return connectPromiseRef.current;
    }

    connectPromiseRef.current = new Promise((resolve, reject) => {
      const ws = new WebSocket(buildAiChatWsUrl());
      let connectResolved = false;
      let connectRejected = false;

      function rejectConnect(message) {
        if (connectResolved || connectRejected) {
          return;
        }

        connectRejected = true;
        reject(new Error(message));
      }

      wsRef.current = ws;
      setConnectionError("");
      setUiHint("正在连接 AI 建档助手...");

      ws.onopen = () => {
        ws.send(
          JSON.stringify({
            type: "connect_init",
            request_id: `connect-${Date.now()}`,
            session_id: sessionIdRef.current,
            payload: {
              student_id: studentId,
              session_id: sessionIdRef.current,
              biz_domain: AI_CHAT_BIZ_DOMAIN,
              access_token: localStorage.getItem("access_token"),
            },
          })
        );
      };

      ws.onmessage = (event) => {
        const data = JSON.parse(event.data);

        if (data.type === "connect_ack") {
          connectResolved = true;
          setAiSessionId(data.session_id);
          sessionIdRef.current = data.session_id;
          localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, data.session_id);
          const stage = data.payload?.current_stage || "conversation";
          setCurrentStage(stage);
          currentStageRef.current = stage;
          setUiHint("AI 建档助手已连接，可以开始对话。");
          resolve(data.session_id);
          return;
        }

        // 中文注释：
        // connect_init 还没完成时，如果后端先返回了 error，
        // 说明连接本身虽然建立了，但初始化鉴权 / 会话绑定已经失败。
        // 这里必须直接让初始化 promise 失败，并把后端明确错误展示出来，
        // 不能再等 onclose 时落成笼统的“初始化未完成”。
        if (!connectResolved && data.type === "error") {
          const backendMessage = data.payload?.message || "AI 对话初始化失败，请稍后重试。";
          setConnectionError(backendMessage);
          setUiHint("");
          rejectConnect(backendMessage);
          return;
        }

        handleAiSocketEvent(data);
      };

      ws.onerror = () => {
        if (!connectResolved) {
          rejectConnect("AI 对话连接建立失败，请检查网络后重试。");
        }
      };

      ws.onclose = () => {
        wsRef.current = null;
        assistantStreamingMessageIdRef.current = null;

        if (!connectResolved) {
          rejectConnect("AI 对话连接已关闭，初始化未完成。");
          return;
        }

        setUiHint("AI 连接已断开，重新发送消息时会自动重连。");
      };
    }).finally(() => {
      connectPromiseRef.current = null;
    });

    return connectPromiseRef.current;
  }

  function handleAiSocketEvent(event) {
    const payload = event.payload || {};

    switch (event.type) {
      case "user_message_saved":
        setMessages((previous) => {
          const nextMessages = [...previous];
          const targetIndex = [...nextMessages]
            .map((item, index) => ({ item, index }))
            .reverse()
            .find(({ item }) => item.role === "user" && item.deliveryStatus !== "sent")?.index;

          if (typeof targetIndex === "number") {
            nextMessages[targetIndex] = {
              ...nextMessages[targetIndex],
              deliveryStatus: "sent",
            };
          }

          return nextMessages;
        });
        break;

      case "stage_changed": {
        const nextStage = payload.current_stage || "idle";
        const conversationPhase = payload.conversation_phase || null;
        setCurrentStage(nextStage);
        currentStageRef.current = nextStage;
        logProfileFlow("阶段变更", {
          sessionId: event.session_id,
          currentStage: nextStage,
          conversationPhase,
        });

        if (nextStage === "conversation") {
          // 中文注释：
          // 后端现在会把 conversation 再细分成两种子状态：
          // 1. generating_assistant：正在生成助手回复，此时继续展示忙碌态
          // 2. ready_for_input：本轮已经处理完成，可以恢复输入框
          // 这样可以避免前端把“可继续输入”误判成“仍在整理回复”，导致输入框一直转圈。
          if (conversationPhase === "generating_assistant") {
            setAssistantThinking(true);
            setUiHint(chatEndedRef.current ? "你可以继续补充信息，我会基于新增内容继续整理档案。" : "建档助手正在整理回复...");
          } else {
            setAssistantThinking(false);
            setAssistantStreaming(false);
            assistantStreamingMessageIdRef.current = null;
            setUiHint(chatEndedRef.current ? "你可以继续补充信息，我会基于新增内容继续整理档案。" : "这一轮已处理完成，你可以继续输入下一条信息。");
          }
        } else if (nextStage === "progress_updating") {
          setAssistantThinking(false);
          setUiHint("正在更新档案进度...");
        } else if (nextStage === "build_ready") {
          setAssistantThinking(false);
          setChatEnded(true);
          chatEndedRef.current = true;
          setUiHint("");
        } else if (nextStage === "profile_saving") {
          setAssistantThinking(false);
          setCreateProfileLoading(true);
          createProfileLoadingRef.current = true;
          setUiHint("六维图结果已经生成，档案正在后台创建。");
        } else if (nextStage === "completed") {
          setAssistantThinking(false);
          setUiHint("六维图结果已生成。");
        } else if (nextStage === "failed") {
          setAssistantThinking(false);
          setAssistantStreaming(false);
          assistantStreamingMessageIdRef.current = null;
          setCreateProfileLoading(false);
          createProfileLoadingRef.current = false;
          setCurrentStage("build_ready");
          currentStageRef.current = "build_ready";
          setChatEnded(true);
          chatEndedRef.current = true;
          setUiHint("当前处理出现异常，你可以继续补充信息后再重新生成。");
        }
        break;
      }

      case "assistant_token":
        setAssistantThinking(false);
        setAssistantStreaming(true);
        setUiHint("建档助手正在生成回复...");
        setMessages((previous) => {
          const nextMessages = [...previous];
          const streamingId = assistantStreamingMessageIdRef.current;
          const streamingIndex = streamingId ? nextMessages.findIndex((item) => item.id === streamingId) : -1;

          if (streamingIndex >= 0) {
            nextMessages[streamingIndex] = {
              ...nextMessages[streamingIndex],
              content: payload.accumulated_text || nextMessages[streamingIndex].content + (payload.delta_text || ""),
              isStreaming: true,
            };
            return nextMessages;
          }

          const newAssistantMessage = createChatMessage(
            "assistant",
            payload.accumulated_text || payload.delta_text || "",
            { isStreaming: true }
          );
          assistantStreamingMessageIdRef.current = newAssistantMessage.id;
          nextMessages.push(newAssistantMessage);
          return nextMessages;
        });
        break;

      case "assistant_done":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        setUiHint("助手回复完成，正在整理本轮进度...");
        setMessages((previous) => {
          const nextMessages = [...previous];
          const streamingId = assistantStreamingMessageIdRef.current;
          const streamingIndex = streamingId ? nextMessages.findIndex((item) => item.id === streamingId) : -1;

          if (streamingIndex >= 0) {
            nextMessages[streamingIndex] = {
              ...nextMessages[streamingIndex],
              content: payload.content || nextMessages[streamingIndex].content,
              isStreaming: false,
              deliveryStatus: "sent",
            };
          } else {
            const lastAssistantIndex = [...nextMessages]
              .map((item, index) => ({ item, index }))
              .reverse()
              .find(({ item }) => item.role === "assistant")?.index;

            if (
              typeof lastAssistantIndex === "number" &&
              (nextMessages[lastAssistantIndex].isStreaming ||
                nextMessages[lastAssistantIndex].content === (payload.content || ""))
            ) {
              nextMessages[lastAssistantIndex] = {
                ...nextMessages[lastAssistantIndex],
                content: payload.content || nextMessages[lastAssistantIndex].content,
                isStreaming: false,
                deliveryStatus: "sent",
              };
            } else {
              nextMessages.push(
                createChatMessage("assistant", payload.content || "", {
                  id: payload.message_id || undefined,
                  deliveryStatus: "sent",
                })
              );
            }
          }

          return nextMessages;
        });
        assistantStreamingMessageIdRef.current = null;
        break;

      case "progress_updated":
        setMissingDimensions(payload.missing_dimensions || []);
        setDimensionProgress(payload.dimension_progress || {});
        setNextQuestionFocus(payload.next_question_focus || null);
        nextQuestionFocusRef.current = payload.next_question_focus || null;
        setChatEnded(Boolean(payload.stop_ready));
        chatEndedRef.current = Boolean(payload.stop_ready);

        if (payload.stop_ready) {
          setUiHint("");
        } else if (payload.next_question_focus) {
          setUiHint(`当前重点还缺 ${RADAR_LABELS[payload.next_question_focus] || payload.next_question_focus} 相关信息，可以继续补充。`);
        } else {
          setUiHint("可以继续补充信息了，我会接着帮你完成建档。");
        }
        break;

      case "radar_scores_ready":
        setPendingProfileData((previous) => ({
          ...(previous || normalizeProfileResult({})),
          radar_scores_json: normalizeProfileResult({
            radar_scores_json: payload.radar_scores_json,
            summary_text: previous?.summary_text,
          }).radar_scores_json,
        }));
        break;

      case "summary_ready":
        setPendingProfileData((previous) => ({
          ...(previous || normalizeProfileResult({})),
          summary_text: payload.summary_text || "",
        }));
        break;

      case "profile_saved":
        setProfileResultStatus(payload.result_status || null);
        setSaveErrorMessage(payload.save_error_message || "");
        if (payload.result_status === "saved" || payload.result_status === "failed") {
          setCreateProfileLoading(false);
          createProfileLoadingRef.current = false;
        }
        setUiHint(payload.result_status === "saved" ? "六维图与建档结果已更新完成。" : "六维图结果已生成，但正式保存存在异常。");
        logProfileFlow("档案状态更新", {
          sessionId: event.session_id,
          resultStatus: payload.result_status || null,
          saveErrorMessage: payload.save_error_message || "",
        });
        break;

      case "generation_cancel_requested":
        setUiHint("正在停止本轮生成...");
        break;

      case "generation_cancelled":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        assistantStreamingMessageIdRef.current = null;
        setMessages((previous) => previous.filter((item) => !item.isStreaming));
        setUiHint("本轮生成已取消，你可以继续补充信息。");
        break;

      case "error":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        assistantStreamingMessageIdRef.current = null;
        setConnectionError(payload.message || "AI 对话过程中出现异常，请稍后重试。");
        break;

      default:
        break;
    }
  }

  function handleStartChat() {
    setShowChat(true);
    setShowProfileResult(false);
    setConnectionError("");

    if (hasGeneratedProfile && (currentStageRef.current === "completed" || currentStageRef.current === "failed")) {
      setCurrentStage("build_ready");
      currentStageRef.current = "build_ready";
      setChatEnded(true);
      chatEndedRef.current = true;
    }

    setUiHint(
      messages.length > 0
        ? "可以继续补充信息，我会基于当前会话继续整理档案。"
        : "可以直接开始说你的申请情况，我会一步步帮你完成六维建档。"
    );
  }

  function handleCloseChat() {
    setShowChat(false);
    setUiHint("");
  }

  function handleViewMyRadar() {
    if (!profileData) {
      return;
    }
    setShowChat(false);
    setShowProfileResult(true);
    setUiHint("");
    setActiveSection("hero");
    document.getElementById("home-hero")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function buildSupplementGuidanceMessage() {
    const missingLabels = missingDimensions.map((key) => RADAR_LABELS[key] || key);
    if (missingLabels.length > 0) {
      return `好的，我们继续补充信息。当前还缺 ${missingLabels.join("、")}，你可以优先补这些内容。`;
    }

    const partialLabels = Object.entries(dimensionProgress || {})
      .filter(([, value]) => value?.status === "partial")
      .map(([key]) => RADAR_LABELS[key] || key);

    if (partialLabels.length > 0) {
      return `目前 ${partialLabels.join("、")} 还可以继续补充更完整的信息，你可以从最有代表性的内容开始说。`;
    }

    return "我们可以继续补充更细的信息，比如更具体的成绩、竞赛、活动或项目经历，让六维图更准确。";
  }

  function handleContinueSupplementInfo() {
    setShowProfileResult(false);
    setShowChat(true);
    setCurrentStage("build_ready");
    currentStageRef.current = "build_ready";
    setAssistantThinking(false);
    assistantThinkingRef.current = false;
    setAssistantStreaming(false);
    assistantStreamingRef.current = false;
    setQueuedOutgoingMessages([]);
    queuedOutgoingMessagesRef.current = [];
    setConnectionError("");

    const guidanceMessage = buildSupplementGuidanceMessage();
    setUiHint(guidanceMessage);
    setMessages((previous) => {
      const lastMessage = previous[previous.length - 1];
      if (lastMessage?.role === "assistant" && lastMessage.content === guidanceMessage) {
        return previous;
      }
      return [...previous, createChatMessage("assistant", guidanceMessage)];
    });
  }

  function handleViewArchive() {
    if (!aiSessionId) {
      return;
    }

    navigate(`/profile?tab=archive&session_id=${encodeURIComponent(aiSessionId)}`);
  }

  async function waitForProfileGenerationResult(sessionId, hadProfileBefore) {
    // 中文注释：
    // 新流程里，六维图结果会先落到 ai_chat_profile_results。
    // 因此这里不再只盯着 build_ready，而是当阶段进入 profile_saving / build_ready 时，
    // 都主动尝试读取结果；如果结果状态还是 generated，就继续轮询直到档案创建完成。
    profileBuildPollingRef.current = true;
    const pollingStartedAt = Date.now();
    let lastLoggedStage = null;

    try {
      const timeoutAt = Date.now() + 180000;

      while (Date.now() < timeoutAt) {
        const sessionDetailResponse = await getAiChatSessionDetail(sessionId);
        const sessionDetail = sessionDetailResponse.data || {};
        const polledStage = sessionDetail.current_stage || "idle";

        setCurrentStage(polledStage);
        currentStageRef.current = polledStage;
        setMissingDimensions(sessionDetail.missing_dimensions || []);

        if (lastLoggedStage !== polledStage) {
          lastLoggedStage = polledStage;
          logProfileFlow("轮询阶段变化", {
            sessionId,
            currentStage: polledStage,
            elapsedMs: Date.now() - pollingStartedAt,
          });
        }

        if (polledStage === "extraction") {
          setUiHint("正在整理结构化档案...");
        } else if (polledStage === "scoring") {
          setUiHint("正在计算六维评分...");
        } else if (polledStage === "profile_saving") {
          setUiHint("六维图结果即将返回，档案随后会继续创建。");
        } else if (polledStage === "failed") {
          const failedMessage = sessionDetail.remark || "六维图生成失败，请稍后重试。";
          setConnectionError(failedMessage);
          setCreateProfileLoading(false);
          createProfileLoadingRef.current = false;
          if (hadProfileBefore) {
            setUiHint(failedMessage);
            setProfileResultStatus("failed");
            setSaveErrorMessage(failedMessage);
            setShowChat(false);
          } else {
            setCurrentStage("build_ready");
            currentStageRef.current = "build_ready";
            setChatEnded(true);
            chatEndedRef.current = true;
            setUiHint("本次生成失败，你可以继续补充信息后再重新生成。");
            setShowChat(true);
          }
          logProfileFlow("后台生成失败", {
            sessionId,
            message: failedMessage,
            elapsedMs: Date.now() - pollingStartedAt,
          });
          return false;
        }

        if (polledStage === "profile_saving" || polledStage === "build_ready") {
          try {
            const resultResponse = await getAiChatResult(sessionId);
            const resultPayload = resultResponse.data || {};
            const normalized = normalizeProfileResult(resultPayload);
            const resultStatus = resultPayload.result_status || null;
            const resultErrorMessage = resultPayload.save_error_message || "";

            setPendingProfileData(normalized);
            setProfileData(normalized);
            setShowProfileResult(true);
            setProfileResultStatus(resultStatus);
            setSaveErrorMessage(resultErrorMessage);
            setShowChat(false);
            setChatEnded(true);
            chatEndedRef.current = true;
            setConnectionError("");

            logProfileFlow("结果已返回前端", {
              sessionId,
              currentStage: polledStage,
              resultStatus,
              elapsedMs: Date.now() - pollingStartedAt,
            });

            if (resultStatus === "generated" || polledStage === "profile_saving") {
              setUiHint("六维图已生成，档案正在后台创建。");
              await sleep(1500);
              continue;
            }

            setUiHint(resultStatus === "saved" ? "六维图已更新完成，档案创建完成。" : "六维图已生成，但档案创建失败。");
            return resultStatus === "saved";
          } catch (error) {
            if (error?.response?.status !== 404) {
              throw error;
            }
          }
        }

        await sleep(1500);
      }

      setConnectionError("生成六维图超时，请稍后重试。");
      setUiHint("生成六维图超时，请稍后重试。");
      if (!hadProfileBefore) {
        setShowChat(true);
      }
      logProfileFlow("轮询超时", {
        sessionId,
        elapsedMs: Date.now() - pollingStartedAt,
      });
      return false;
    } finally {
      profileBuildPollingRef.current = false;
      setCreateProfileLoading(false);
      createProfileLoadingRef.current = false;
    }
  }

  function updateLocalQueuedMessageStatus(localMessageId, deliveryStatus) {
    // 中文注释：
    // 当前交互方案已经改成“处理期间禁止继续输入”，
    // 因此这里保留空实现，仅用于兼容之前的调用结构。
    void localMessageId;
    void deliveryStatus;
  }

  async function flushQueuedMessages(studentId) {
    // 中文注释：
    // 当前版本不再启用前端消息排队发送，这里保留空实现用于兼容旧逻辑。
    void studentId;
  }

  async function handleSendMessage() {
    await sendUserMessage();
  }

  async function handleCreateProfile() {
    // 中文注释：
    // 用户点击“立即建档 / 更新六维图”后，只提交后台异步任务。
    // 前端会切到 loading 态，并通过轮询等待后台完成。
    if (isBuildProfileBlocked) {
      setConnectionError("");
      setUiHint("当前还有上一轮处理未完成，请等待处理结束后再生成六维图。");
      return;
    }

    if (!sessionIdRef.current) {
      setConnectionError("当前还没有可用会话，请先开始对话。");
      return;
    }

    const hadProfileBefore = Boolean(profileData);
    setCreateProfileLoading(true);
    createProfileLoadingRef.current = true;
    setConnectionError("");
    setSaveErrorMessage("");
    setProfileResultStatus(null);
    setShowChat(false);
    setShowProfileResult(false);
    setUiHint(hasGeneratedProfile ? "正在后台更新六维图..." : "正在后台生成六维图...");
    logProfileFlow("发起生成请求", {
      sessionId: sessionIdRef.current,
      hadProfileBefore,
    });

    try {
      const response = await buildAiChatProfile(sessionIdRef.current);
      const acceptedStage = response.data?.current_stage || "extraction";
      setCurrentStage(acceptedStage);
      currentStageRef.current = acceptedStage;
      logProfileFlow("后台任务已受理", {
        sessionId: sessionIdRef.current,
        currentStage: acceptedStage,
      });
      await waitForProfileGenerationResult(sessionIdRef.current, hadProfileBefore);
    } catch (error) {
      const backendMessage = error?.response?.data?.detail;
      if (error?.response?.status === 409) {
        setConnectionError(backendMessage || "当前还有上一轮处理未完成，请稍后再试。");
      } else {
        setConnectionError(backendMessage || error?.message || "触发六维图生成失败，请稍后重试。");
      }

      if (!hadProfileBefore) {
        setShowChat(true);
      }
    } finally {
      if (!profileBuildPollingRef.current) {
        setCreateProfileLoading(false);
        createProfileLoadingRef.current = false;
      }
    }
  }

  async function sendUserMessage() {
    const trimmedInput = inputValue.trim();
    const canSendCurrentRound =
      currentStageRef.current === "idle" ||
      currentStageRef.current === "conversation" ||
      currentStageRef.current === "build_ready" ||
      currentStageRef.current === "completed" ||
      currentStageRef.current === "failed";
    const isBusyNow =
      assistantStreaming ||
      assistantThinking ||
      createProfileLoading ||
      currentStageRef.current === "progress_updating" ||
      currentStageRef.current === "extraction" ||
      currentStageRef.current === "scoring" ||
      currentStageRef.current === "profile_saving";

    // 中文注释：
    // 现在的交互规则是：只要上一轮还在处理，就不允许继续输入，
    // 必须等 assistant / progress / extraction / scoring 结束后再恢复输入框。
    if (!trimmedInput || !profile?.user_id || createProfileLoading) {
      return;
    }

    if (isBusyNow) {
      setConnectionError("");
      setUiHint("当前上一轮还在处理中，请等待档案信息更新完成后再继续输入。");
      return;
    }

    if (!canSendCurrentRound) {
      setConnectionError("");
      setUiHint("当前阶段暂不支持继续输入，你可以稍后再试。");
      return;
    }

    try {
      const sessionId = await ensureSocketConnected(profile.user_id);

      setMessages((previous) => [...previous, createChatMessage("user", trimmedInput)]);
      setInputValue("");
      setConnectionError("");
      setAssistantThinking(true);
      setUiHint("建档助手正在整理回复...");

      wsRef.current?.send(
        JSON.stringify({
          type: "user_message",
          request_id: `user-${Date.now()}`,
          // 中文注释：
          // 这里不再显式携带 session_id。
          // 原因是 user_message 发送时，后端已经通过当前 WebSocket 连接绑定了唯一会话上下文，
          // 如果前端本地缓存的 sessionIdRef 因恢复历史、切换结果态等过程发生漂移，
          // 继续把本地 session_id 带上，反而会触发“消息中的 session_id 与当前连接绑定的 session_id 不一致”。
          // 因此这里统一改成只依赖连接上下文，由后端以当前绑定会话为准。
          payload: {
            content: trimmedInput,
          },
        })
      );
    } catch (error) {
      setAssistantThinking(false);
      setConnectionError(error?.message || "发送消息失败，请稍后重试。");
    }
  }
  function handleInputKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      sendUserMessage();
    }
  }

  return (
    <div className="home-shell">
      <motion.header
        className="home-topbar"
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="home-topbar-inner">
          <BrandBlock />

          <nav className="home-top-nav">
            {sectionItems.map((item) => (
              <SectionNavButton
                key={item.key}
                item={item}
                isActive={activeSection === item.key}
                onClick={() => scrollToSection(item.sectionId, item.key)}
              />
            ))}
          </nav>

          <div className="home-top-actions">
            <TopActionButton label="鎻愰啋">
              <div className="home-top-icon-wrap">
                <BellIcon />
                <span className="home-top-icon-dot" />
              </div>
            </TopActionButton>
            <TopActionButton label="璁剧疆">
              <SettingsIcon />
            </TopActionButton>
            <div className="home-top-divider" />

            {profile ? (
              <motion.button
                type="button"
                onClick={() => navigate("/profile")}
                className="home-user-entry"
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
              >
                <div className="home-user-copy">
                  <p className="home-user-name">{getDisplayName()}</p>
                  <p className="home-user-subname">{getDisplaySubName()}</p>
                </div>
                {profile.avatar_url ? (
                  <img src={profile.avatar_url} alt="用户头像" className="home-user-avatar" />
                ) : (
                  <div className="home-user-avatar home-user-avatar-fallback">{getDisplayInitial()}</div>
                )}
              </motion.button>
            ) : null}
          </div>
        </div>
      </motion.header>

      <main className="home-main">
        <motion.section
          className="home-hero"
          id="home-hero"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6 }}
        >
          <div className="home-hero-bg" />
          <div className="home-hero-orb home-hero-orb-left" />
          <div className="home-hero-orb home-hero-orb-right" />

          <motion.div
            className="home-hero-content"
            layout
            transition={{ layout: { type: "spring", stiffness: 110, damping: 20, mass: 0.9 } }}
          >
            <motion.div
              layout="position"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.2 }}
            >
              <h1 className="home-hero-title">
                欢迎使用录途 Toolbox
                <br />
                <span className="home-hero-title-accent">你的留学申请工具箱</span>
              </h1>
            </motion.div>

            <motion.p
              className="home-hero-subtitle"
              layout="position"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.4 }}
            >
              备考、选校、申请、建档，一站式完成你的留学准备。
            </motion.p>

            <motion.div
              className="home-hero-actions"
              layout="position"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.6 }}
            >
              {!showChat && !showProfileResult ? (
                <motion.button
                  type="button"
                  onClick={profileData ? handleViewMyRadar : handleStartChat}
                  className="home-primary-button"
                  whileHover={{ scale: 1.05, boxShadow: "0 20px 40px rgba(59,130,246,0.35)" }}
                  whileTap={{ scale: 0.98 }}
                >
                  {profileData ? "查看我的六维图" : "Get Started"}
                  <ArrowRightIcon />
                </motion.button>
              ) : null}

              {!showProfileResult ? (
                <motion.button
                  type="button"
                  onClick={() => scrollToSection("home-ranking", "ranking")}
                  className="home-secondary-button"
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.98 }}
                >
                  了解更多
                </motion.button>
              ) : null}
            </motion.div>

            <AnimatePresence initial={false} mode="popLayout">
              {showChat && !showProfileResult ? (
                <motion.div
                  key="chat-shell"
                  className="home-ai-shell"
                  layout
                  initial={{ opacity: 0, y: 24, scale: 0.97, height: 0 }}
                  animate={{ opacity: 1, y: 0, scale: 1, height: "auto" }}
                  exit={{ opacity: 0, y: -10, scale: 0.985, height: 0, marginTop: 0 }}
                  transition={{
                    opacity: { duration: 0.2, ease: "easeOut" },
                    scale: { duration: 0.24, ease: "easeOut" },
                    y: { duration: 0.26, ease: "easeOut" },
                    height: { type: "spring", stiffness: 140, damping: 22 },
                    layout: { type: "spring", stiffness: 110, damping: 20, mass: 0.9 },
                  }}
                >
                  <div className="home-ai-header">
                    <div className="home-ai-header-brand">
                      <div className="home-ai-brand-icon">
                        <Sparkles size={18} strokeWidth={2.2} />
                      </div>
                      <div>
                        <h3 className="home-ai-title">学生 AI 建档助手</h3>
                        <p className="home-ai-subtitle">对话补全申请信息，系统自动生成六维图</p>
                      </div>
                    </div>

                    <button type="button" className="home-ai-close" onClick={handleCloseChat} aria-label="关闭对话框">
                      <X size={18} strokeWidth={2.4} />
                    </button>
                  </div>

                  <div className="home-ai-body" ref={chatBodyRef}>
                    {messages.length === 0 ? (
                      <div className="home-ai-empty-state">
                        <div className="home-ai-empty-icon">
                          <UserRound size={20} strokeWidth={2} />
                        </div>
                        <div>
                          <p className="home-ai-empty-title">可以直接开始说你的申请情况</p>
                          <p className="home-ai-empty-copy">
                            例如：我现在高二，A-Level 体系，想申请英国经济学，雅思预估 7.5。
                          </p>
                        </div>
                      </div>
                    ) : (
                      messages.map((message) => (
                        <div
                          key={message.id}
                          className={`home-ai-message-row ${message.role === "user" ? "home-ai-message-row-user" : "home-ai-message-row-assistant"}`}
                        >
                          {message.role === "assistant" ? (
                            <div className="home-ai-avatar home-ai-avatar-assistant">
                              <Sparkles size={16} strokeWidth={2.1} />
                            </div>
                          ) : null}

                          <div
                            className={`home-ai-bubble ${message.role === "user" ? "home-ai-bubble-user" : "home-ai-bubble-assistant"}`}
                          >
                            <p>{message.content}</p>
                            {message.role === "user" && message.deliveryStatus !== "sent" ? (
                              <span className="home-ai-message-status">
                                {message.deliveryStatus === "queued" ? "待发送" : "发送中"}
                              </span>
                            ) : null}
                            {message.isStreaming ? <span className="home-ai-stream-caret" /> : null}
                          </div>

                          {message.role === "user" ? (
                            <div className="home-ai-avatar home-ai-avatar-user">
                              <UserRound size={16} strokeWidth={2.1} />
                            </div>
                          ) : null}
                        </div>
                      ))
                    )}
                  </div>

                  <div className="home-ai-footer">
                    <div className="home-ai-status-row">
                      <span className="home-ai-stage-badge">
                        当前阶段：{getStageDisplayLabel(currentStage)}
                      </span>

                      {missingDimensions.length > 0 ? (
                        <span className="home-ai-status-copy">
                          当前还缺：{missingDimensions.map((key) => RADAR_LABELS[key] || key).join("、")}
                        </span>
                      ) : null}

                      {nextQuestionFocus ? (
                        <span className="home-ai-status-copy">
                          下一步重点：{RADAR_LABELS[nextQuestionFocus] || nextQuestionFocus}
                        </span>
                      ) : null}
                    </div>

                    {uiHint ? <p className="home-ai-hint">{uiHint}</p> : null}
                    {connectionError ? <p className="home-ai-error">{connectionError}</p> : null}
                    {saveErrorMessage && profileResultStatus !== "saved" ? (
                      <p className="home-ai-error">建档保存异常：{saveErrorMessage}</p>
                    ) : null}

                    {isRoundProcessing ? (
                      <div className="home-ai-processing-row">
                        <div className="home-ai-processing-spinner" />
                        <div className="home-ai-processing-copy">
                          <strong>正在更新档案信息</strong>
                          <span>这一轮还没处理完，处理完成后会恢复输入框和操作按钮。</span>
                        </div>
                      </div>
                    ) : (
                      <div className="home-ai-input-shell">
                        <div className="home-ai-input-row">
                          <textarea
                            className="home-ai-input"
                            value={inputValue}
                            onChange={(event) => setInputValue(event.target.value)}
                            onKeyDown={handleInputKeyDown}
                            placeholder="直接告诉我你的课程体系、成绩、竞赛、活动或项目经历..."
                            rows={2}
                            disabled={false}
                          />
                        </div>

                        <div className="home-ai-action-row">
                          <motion.button
                            type="button"
                            className="home-ai-send"
                            onClick={sendUserMessage}
                            whileHover={{ scale: 1.04 }}
                            whileTap={{ scale: 0.96 }}
                            disabled={!inputValue.trim()}
                          >
                            <Send size={18} strokeWidth={2.2} />
                          </motion.button>

                          {chatEnded ? (
                            <motion.button
                              type="button"
                              className="home-ai-build-button"
                              onClick={handleCreateProfile}
                              whileHover={{ scale: 1.04 }}
                              whileTap={{ scale: 0.96 }}
                              disabled={isBuildProfileBlocked}
                            >
                              {createProfileLoading ? "正在生成..." : hasGeneratedProfile ? "更新六维图" : "生成六维图"}
                              <ArrowRight size={18} strokeWidth={2.2} />
                            </motion.button>
                          ) : null}
                        </div>
                      </div>
                    )}
                  </div>
                </motion.div>
              ) : null}

              {createProfileLoading && !showProfileResult ? (
                <motion.div
                  key="profile-loading"
                  className="home-radar-loading-shell"
                  layout
                  initial={{ opacity: 0, y: 24, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 16, scale: 0.96 }}
                  transition={{ type: "spring", stiffness: 220, damping: 28 }}
                >
                  <div className="home-radar-loading-spinner" />
                  <h3>正在生成六维图</h3>
                  <p>
                    系统正在后台整理结构化档案并计算六维评分，
                    六维图结果出来后会先展示，正式档案会继续在后台创建。
                  </p>
                </motion.div>
              ) : null}

              {showProfileResult && profileData ? (
                <motion.div
                  key="profile-result"
                  className="home-radar-shell"
                  layout
                  initial={{ opacity: 0, y: 24, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 16, scale: 0.96 }}
                  transition={{ type: "spring", stiffness: 220, damping: 28 }}
                >
                  <div className="home-radar-header">
                    <div>
                      <h3 className="home-radar-title">你的六维建档结果</h3>
                      <p className="home-radar-subtitle">{getProfileStatusText(profileResultStatus)}</p>
                      <p className="home-radar-subtitle home-radar-subtitle-secondary">
                        当前档案状态：{getArchiveStatusText(profileResultStatus)}
                      </p>
                    </div>

                    <div className="home-radar-actions">
                      {profileResultStatus === "saved" && aiSessionId ? (
                        <motion.button
                          type="button"
                          className="home-radar-secondary-button"
                          onClick={handleViewArchive}
                          whileHover={{ scale: 1.03 }}
                          whileTap={{ scale: 0.97 }}
                        >
                          查看我的档案
                        </motion.button>
                      ) : null}

                      <motion.button
                        type="button"
                        className="home-radar-back-button"
                        onClick={handleContinueSupplementInfo}
                        whileHover={{ scale: 1.03 }}
                        whileTap={{ scale: 0.97 }}
                      >
                        继续补充信息
                      </motion.button>
                    </div>
                  </div>

                  {isArchiveCreating ? (
                    <div className="home-radar-loading-inline">
                      <div className="home-radar-loading-spinner home-radar-loading-spinner-sm" />
                      <span>六维图结果已生成，档案正在后台创建...</span>
                    </div>
                  ) : null}

                  {isArchiveCreating ? (
                    <div className="home-radar-status-banner home-radar-status-banner-pending">
                      档案正在创建，六维图结果已经先展示给你，创建完成后会自动更新为“档案创建完成”。
                    </div>
                  ) : null}

                  {profileResultStatus === "saved" ? (
                    <div className="home-radar-status-banner home-radar-status-banner-saved">
                      档案创建完成。
                    </div>
                  ) : null}

                  {saveErrorMessage && profileResultStatus === "failed" ? (
                    <div className="home-radar-warning">
                      建档结果已生成，但正式保存存在异常：{saveErrorMessage}
                    </div>
                  ) : null}

                  <div className="home-radar-top">
                    <div className="home-radar-chart-card">
                      <div className="home-radar-chart-head">
                        <div className="home-radar-chart-icon">
                          <TrendingUp size={18} strokeWidth={2.1} />
                        </div>
                        <div>
                          <h4>六维雷达图</h4>
                          <p>系统会基于当前对话中已经采集到的信息给出初版评估</p>
                        </div>
                      </div>

                      <div className="home-radar-chart-wrap">
                        <ResponsiveContainer width="100%" height="100%">
                          <RadarChart data={radarChartData} outerRadius="68%">
                            <PolarGrid stroke="rgba(148,163,184,0.30)" />
                            <PolarAngleAxis dataKey="subject" tick={{ fill: "#dbeafe", fontSize: 13 }} />
                            <PolarRadiusAxis
                              angle={30}
                              domain={[0, 100]}
                              tick={{ fill: "rgba(219,234,254,0.70)", fontSize: 11 }}
                              axisLine={false}
                            />
                            <Radar
                              dataKey="score"
                              stroke="#7dd3fc"
                              fill="rgba(125, 211, 252, 0.36)"
                              strokeWidth={2.5}
                              dot={{
                                r: 4,
                                fill: "#ffffff",
                                stroke: "#7dd3fc",
                                strokeWidth: 2,
                              }}
                            />
                          </RadarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>

                    <div className="home-radar-summary-card">
                      <div className="home-radar-summary-badge">
                        <Sparkles size={18} strokeWidth={2.2} />
                      </div>

                      <div className="home-radar-summary-copy">
                        <h4>综合总结</h4>
                        <p>{profileData.summary_text}</p>
                      </div>
                    </div>
                  </div>

                  <div className="home-radar-score-grid">
                    {Object.entries(profileData.radar_scores_json).map(([key, value]) => (
                      <motion.div
                        key={key}
                        className="home-radar-score-card"
                        style={{
                          "--score-from": (RADAR_COLORS[key] || RADAR_COLORS.academic).from,
                          "--score-to": (RADAR_COLORS[key] || RADAR_COLORS.academic).to,
                          "--score-bg": (RADAR_COLORS[key] || RADAR_COLORS.academic).bg,
                        }}
                        initial={{ opacity: 0, y: 14 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.08 }}
                      >
                        <div className="home-radar-score-top">
                          <div className="home-radar-score-meta">
                            <div className="home-radar-score-dot" />
                            <span>{RADAR_LABELS[key] || key}</span>
                          </div>

                          <div className="home-radar-score-value">
                            <strong>{value.score}</strong>
                            <span>/ 100</span>
                          </div>
                        </div>

                        <div className="home-radar-score-bar">
                          <motion.div
                            className="home-radar-score-bar-fill"
                            initial={{ width: 0 }}
                            animate={{ width: `${value.score}%` }}
                            transition={{ duration: 0.8, ease: "easeOut" }}
                          />
                        </div>

                        <p className="home-radar-score-reason">{value.reason}</p>
                      </motion.div>
                    ))}
                  </div>
                </motion.div>
              ) : null}
            </AnimatePresence>

          </motion.div>
        </motion.section>

        <div className="home-content-wrap">
          <motion.section
            className="home-section"
            id="home-tools"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.5 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><Calculator size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">快捷小工具</h2>
                <p className="home-section-subtitle">方便高效地处理留学成绩换算和基础申请判断。</p>
              </div>
            </div>
            <div className="home-card">
              <ScoreConverter />
            </div>
          </motion.section>

          <motion.section
            className="home-section"
            id="home-exams"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.6 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><Link2 size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">考试报名链接</h2>
                <p className="home-section-subtitle">快速跳转到主流留学考试官网报名入口。</p>
              </div>
            </div>

            <div className="home-exam-grid">
              {examLinks.map((link, index) => (
                <motion.a
                  key={index}
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="home-exam-card"
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.3, delay: 0.7 + index * 0.05 }}
                  whileHover={{ y: -4, boxShadow: "0 8px 20px rgba(0,0,0,0.1)" }}
                >
                  <div className="home-exam-card-head">
                    <div className="home-exam-badge" style={{ background: `${link.accent}15`, color: link.accent }}>
                      <GraduationCap size={20} strokeWidth={2.1} />
                    </div>
                    <span className="home-exam-arrow">→</span>
                  </div>
                  <h3 className="home-exam-title">{link.name}</h3>
                </motion.a>
              ))}
            </div>
          </motion.section>

          <motion.section
            className="home-section"
            id="home-ranking"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.8 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><TrendingUp size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">大学排名查询</h2>
                <p className="home-section-subtitle">根据地区、QS 区间和学校名称快速筛选院校。</p>
              </div>
            </div>
            <div className="home-card">
              <RankingTool />
            </div>
          </motion.section>
        </div>
      </main>
    </div>
  );
}
