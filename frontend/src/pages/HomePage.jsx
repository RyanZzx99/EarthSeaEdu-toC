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
      <div className="home-brand-text">录途 LutoolBox</div>
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
    build_ready: "可建档",
    completed: "已完成",
    failed: "生成异常",
  };

  return stageLabelMap[stage] || stage;
}

export default function HomePage() {
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [activeSection, setActiveSection] = useState("hero");
  const [showChat, setShowChat] = useState(false);
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
  const queuedOutgoingMessagesRef = useRef([]);
  const queueFlushingRef = useRef(false);

  useEffect(() => {
    sessionIdRef.current = aiSessionId;
  }, [aiSessionId]);

  useEffect(() => {
    // 中文注释：
    // WebSocket onmessage 在建连时只绑定一次。
    // 如果事件处理直接读取 React state，后续很容易拿到建连瞬间的旧值。
    // 这里用 ref 同步最新阶段，保证事件回调拿到的是实时状态。
    currentStageRef.current = currentStage;
  }, [currentStage]);

  useEffect(() => {
    // 中文注释：
    // 下一轮追问焦点会被 progress_updated 不断刷新。
    // 后续 stage_changed 需要依赖这个值来生成提示文案，因此同步到 ref。
    nextQuestionFocusRef.current = nextQuestionFocus;
  }, [nextQuestionFocus]);

  useEffect(() => {
    // 中文注释：
    // chatEnded 表示系统是否已判断“信息足够，可以进入建档结果阶段”。
    // WebSocket 事件处理同样会读取它，所以需要保持 ref 中也是最新值。
    chatEndedRef.current = chatEnded;
  }, [chatEnded]);

  useEffect(() => {
    // 中文注释：
    // 自动续发排队消息时，也要拿到最新的“是否仍在流式生成”状态。
    assistantStreamingRef.current = assistantStreaming;
  }, [assistantStreaming]);

  useEffect(() => {
    // 中文注释：
    // thinking 状态由多种 socket 事件共同驱动，自动续发时不能读旧值。
    assistantThinkingRef.current = assistantThinking;
  }, [assistantThinking]);

  useEffect(() => {
    // 中文注释：
    // “立即建档”阶段会短暂进入 loading，这时不能继续自动发排队消息。
    createProfileLoadingRef.current = createProfileLoading;
  }, [createProfileLoading]);

  useEffect(() => {
    // 中文注释：
    // 前端这里维护一个“待发送队列”。
    // 当上一轮还在 assistant/progress/extraction 阶段时，
    // 用户点击发送不会直接丢弃，而是先进入这个队列，等系统回到可发送状态后自动发出。
    queuedOutgoingMessagesRef.current = queuedOutgoingMessages;
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

  useEffect(() => {
    // 中文注释：
    // 旧版本留下的会话可能仍然停在 completed / failed。
    // 现在的新流程里，生成过六维图后依然允许继续补充信息，
    // 所以只要当前仍处于聊天界面且已有结果，就统一把阶段拉回 build_ready。
    if (
      showChat &&
      hasGeneratedProfile &&
      (currentStage === "completed" || currentStage === "failed")
    ) {
      setCurrentStage("build_ready");
      currentStageRef.current = "build_ready";
      setChatEnded(true);
      chatEndedRef.current = true;
    }
  }, [showChat, hasGeneratedProfile, currentStage]);

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

      // 中文注释：离开首页时主动关闭 WebSocket，避免旧连接残留。
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
      const targetSessionId = currentSessionId || rememberedSessionId;

      if (!targetSessionId) {
        return;
      }

      const [sessionDetailResponse, messagesResponse] = await Promise.all([
        getAiChatSessionDetail(targetSessionId),
        getAiChatMessages(targetSessionId, { limit: 100 }),
      ]);

      const sessionDetail = sessionDetailResponse.data;
      const restoredMessages = normalizeVisibleMessages(messagesResponse.data?.items || []);
      const normalizedRestoredStage =
        sessionDetail.final_profile_id &&
        (sessionDetail.current_stage === "completed" || sessionDetail.current_stage === "failed")
          ? "build_ready"
          : sessionDetail.current_stage || "idle";

      setAiSessionId(sessionDetail.session_id);
      sessionIdRef.current = sessionDetail.session_id;
      localStorage.setItem(AI_CHAT_SESSION_CACHE_KEY, sessionDetail.session_id);
      setMessages(restoredMessages);
      setMissingDimensions(sessionDetail.missing_dimensions || []);
      setCurrentStage(normalizedRestoredStage);
      currentStageRef.current = normalizedRestoredStage;

      if (restoredMessages.length > 0) {
        setShowChat(true);
      }

      if (sessionDetail.final_profile_id) {
        try {
          const resultResponse = await getAiChatResult(targetSessionId);
          const normalizedResult = normalizeProfileResult(resultResponse.data);

          setPendingProfileData(normalizedResult);
          setProfileResultStatus(resultResponse.data?.result_status || null);
          setSaveErrorMessage(resultResponse.data?.save_error_message || "");
          if (normalizedRestoredStage === "build_ready") {
            setChatEnded(true);
            chatEndedRef.current = true;
            setUiHint("已恢复最近一次建档会话，你可以继续补充信息，或直接更新六维图。");
          } else if (resultResponse.data?.result_status === "saved") {
            setUiHint("已恢复最近一次建档结果");
          } else {
            setUiHint("最近一次建档结果存在保存异常");
          }
        } catch (error) {
          console.error("恢复 AI 建档结果失败", error);
        }
      } else if (restoredMessages.length > 0) {
        setUiHint("已恢复最近一次建档会话");
      }

      if (!studentId) {
        setConnectionError("当前用户信息缺失，无法恢复建档会话。");
      }
    } catch (error) {
      setConnectionError(error?.message || "排队消息发送失败，请稍后重试。");
      console.error("恢复 AI 会话失败", error);
    }
  }

  async function ensureSocketConnected(studentId) {
    if (!studentId) {
      throw new Error("当前缺少 student_id，无法建立 AI 建档连接。");
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

      wsRef.current = ws;
      setConnectionError("");
      setUiHint("正在连接建档助手...");

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
          setCurrentStage(data.payload?.current_stage || "conversation");
          currentStageRef.current = data.payload?.current_stage || "conversation";
          setUiHint("建档助手已准备好，可以开始对话");
          resolve(data.session_id);
          return;
        }

        handleAiSocketEvent(data);
      };

      ws.onerror = () => {
        if (!connectResolved) {
          reject(new Error("AI 建档连接失败，请确认后端服务已启动。"));
        }
      };

      ws.onclose = () => {
        wsRef.current = null;
        assistantStreamingMessageIdRef.current = null;

        if (!connectResolved) {
          reject(new Error("AI 建档连接已关闭，请稍后重试。"));
          return;
        }

        setUiHint("建档连接已断开，下次发送消息时会自动重连");
      };
    }).finally(() => {
      connectPromiseRef.current = null;
    });

    return connectPromiseRef.current;
  }

  function handleAiSocketEvent(event) {
    const payload = event.payload || {};

    switch (event.type) {
      case "stage_changed":
        {
          const nextStage = payload.current_stage || "idle";
          const previousStage = currentStageRef.current;

          setCurrentStage(nextStage);
          currentStageRef.current = nextStage;

          // 中文注释：
          // `conversation` 在当前协议里既可能表示“开始生成回复”，
          // 也可能表示“上一轮已经处理完，重新回到等待用户输入”。
          // 如果前一个阶段是 progress_updating / extraction，
          // 就说明这里是“回到空闲态”，不能继续保持 thinking 状态。
          if (nextStage === "conversation" && (previousStage === "progress_updating" || previousStage === "extraction")) {
            setAssistantThinking(false);

            if (chatEndedRef.current) {
              setUiHint("信息已经足够，点击立即建档查看六维图");
            } else if (nextQuestionFocusRef.current) {
              setUiHint(
                `当前重点还缺 ${RADAR_LABELS[nextQuestionFocusRef.current] || nextQuestionFocusRef.current} 相关信息，可以继续补充`
              );
            } else {
              setUiHint("可以继续补充信息了，我会接着帮你完成建档。");
            }

            // 中文注释：
            // 当系统从 progress / extraction 回到 conversation，
            // 说明这一轮已经完全收尾，可以尝试把用户刚才排队的下一条消息继续发出去。
            window.setTimeout(() => {
              void flushQueuedMessages(profile?.user_id);
            }, 0);
            break;
          }
        }

        const chatEnded = chatEndedRef.current;

        if (payload.current_stage === "conversation") {
          setAssistantThinking(true);
          setUiHint(chatEnded ? "系统已判断信息足够，你也可以继续补充更多内容" : "建档助手正在整理回复");
        } else if (payload.current_stage === "progress_updating") {
          setAssistantThinking(false);
          setUiHint("正在更新建档进度...");
        } else if (payload.current_stage === "build_ready") {
          // 中文注释：
          // build_ready 表示“信息已经足够生成六维图”，
          // 但当前仍允许学生继续补充信息，不会自动进入最终建档。
          setAssistantThinking(false);
          setUiHint("信息已经足够，点击按钮可立即建档；如果你还想补充，也可以继续对话。");
        } else if (payload.current_stage === "completed") {
          setAssistantThinking(false);
          setUiHint("档案已完成，可以查看六维图");
        } else if (payload.current_stage === "failed") {
          setAssistantThinking(false);
          setUiHint("结果已生成，但正式建档保存失败");
        }
        break;

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

          const newAssistantMessage = createChatMessage("assistant", payload.accumulated_text || payload.delta_text || "", {
            isStreaming: true,
          });

          assistantStreamingMessageIdRef.current = newAssistantMessage.id;
          nextMessages.push(newAssistantMessage);
          return nextMessages;
        });
        break;

      case "assistant_done":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        setUiHint("回复已完成，正在更新建档进度...");

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

            // 中文注释：
            // 这里做一次幂等合并，原因是前端偶发会遇到两类边界情况：
            // 1. 流式 assistant 气泡已经存在，但 streamingId 丢失，导致找不到对应占位消息
            // 2. assistant_done 事件被重复消费一次，前端会把整段回复再追加一条
            // 因此在真正 push 新消息之前，先尝试把“最后一条 assistant 消息”视作当前轮的合并目标。
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
              return nextMessages;
            }

            nextMessages.push(
              createChatMessage("assistant", payload.content || "", {
                id: payload.message_id || undefined,
                deliveryStatus: "sent",
              })
            );
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
          setUiHint("信息已经足够，点击立即建档查看六维图");
        } else if (payload.next_question_focus) {
          setUiHint(`当前重点还缺“${RADAR_LABELS[payload.next_question_focus] || payload.next_question_focus}”相关信息`);
        } else {
          setUiHint("建档进度已更新，继续补充信息即可");
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
        setUiHint(payload.result_status === "saved" ? "档案已准备完成，点击立即建档查看六维图" : "结果已生成，但正式保存存在异常");
        break;

      case "generation_cancel_requested":
        setUiHint("正在停止本轮生成...");
        break;

      case "generation_cancelled":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        assistantStreamingMessageIdRef.current = null;
        setUiHint("本轮生成已停止，你可以继续发送新的消息");
        setMessages((previous) => previous.filter((item) => !item.isStreaming));

        // 中文注释：
        // 如果用户在取消当前生成期间又补发了新消息，
        // 那么取消完成后也应该把队列里的消息自动续上。
        window.setTimeout(() => {
          void flushQueuedMessages(profile?.user_id);
        }, 0);
        break;

      case "error":
        setAssistantStreaming(false);
        setAssistantThinking(false);
        assistantStreamingMessageIdRef.current = null;
        setConnectionError(payload.message || "AI 建档链路执行失败，请稍后重试。");
        break;

      default:
        break;
    }
  }

  function handleStartChat() {
    setShowChat(true);
    setConnectionError("");
    if (
      hasGeneratedProfile &&
      (currentStageRef.current === "completed" || currentStageRef.current === "failed")
    ) {
      // 中文注释：
      // 老会话可能是旧版本留下的 completed / failed 阶段。
      // 新流程里生成过六维图后仍允许继续补充，因此这里把阶段拉回 build_ready，
      // 避免继续发消息时被前端误判成“上一轮仍在处理中”。
      setCurrentStage("build_ready");
      currentStageRef.current = "build_ready";
      setChatEnded(true);
      chatEndedRef.current = true;
    }
    setUiHint(
      messages.length > 0
        ? "已恢复建档会话，可以继续补充信息"
        : "你可以直接告诉我课程体系、申请国家、专业方向或成绩情况"
    );
  }

  function handleCloseChat() {
    setShowChat(false);
    setUiHint("");
  }

  function buildSupplementGuidanceMessage() {
    // 中文注释：
    // “继续补充信息”不是单纯把结果页关掉，而是要给学生一个明确的补充方向。
    // 这里优先使用 progress_extraction 返回的缺失维度；
    // 如果缺失维度已经为空，再退一步提示“仍可继续补强”的 partial 维度。
    const missingLabels = missingDimensions.map((key) => RADAR_LABELS[key] || key);
    if (missingLabels.length > 0) {
      return `我们之前还没完整收集到${missingLabels.join("、")}相关信息。你可以优先补这些，我会继续帮你完善档案。`;
    }

    const partialLabels = Object.entries(dimensionProgress || {})
      .filter(([, value]) => value?.status === "partial")
      .map(([key]) => RADAR_LABELS[key] || key);

    if (partialLabels.length > 0) {
      return `目前${partialLabels.join("、")}这几部分还可以继续补得更细一些。你可以补充分数、角色、持续时间、结果或项目产出等信息。`;
    }

    return "如果你还想继续优化六维图，可以补充更详细的成绩、活动、项目、竞赛或目标院校信息，我会在原有基础上继续完善。";
  }

  function handleContinueSupplementInfo() {
    // 中文注释：
    // 六维图结果生成后，学生可以继续在同一条会话里补充信息。
    // 这里切回聊天态，但不清空 `chatEnded`，因为当前信息仍然足够生成六维图，
    // 也就是说“继续补充”和“随时再点更新六维图”应该同时成立。
    setProfileData(null);
    setShowChat(true);
    setCurrentStage("conversation");
    currentStageRef.current = "conversation";
    setConnectionError("");

    const guidanceMessage = buildSupplementGuidanceMessage();
    setUiHint(guidanceMessage);
    setMessages((previous) => {
      const lastMessage = previous[previous.length - 1];
      if (lastMessage?.role === "assistant" && lastMessage?.content === guidanceMessage) {
        return previous;
      }
      return [...previous, createChatMessage("assistant", guidanceMessage)];
    });
  }

  function updateLocalQueuedMessageStatus(localMessageId, deliveryStatus) {
    // 中文注释：
    // 队列里的用户消息会先以本地消息气泡展示出来，
    // 等真正发给后端后，再把状态从 queued 改成 sent。
    setMessages((previous) =>
      previous.map((item) => (item.id === localMessageId ? { ...item, deliveryStatus } : item))
    );
    // 中文注释：
    // 队列里的用户消息会先以本地消息气泡展示出来，
    // 等真正发给后端时，再把状态从 queued 改成 sent。
    setMessages((previous) =>
      previous.map((item) => (item.id === localMessageId ? { ...item, deliveryStatus } : item))
    );
  }

  async function flushQueuedMessages(studentId) {
    // 中文注释：
    // 这个函数只负责把队列里的第一条消息真正发出去。
    // 一次只发一条，是为了保持当前“单轮串行”的后端处理模型，避免并发轮次冲突。
    // 中文注释：
    // 这个函数只负责“把排队里的第一条消息真正发出去”。
    // 之所以一次只发一条，是因为当前后端链路仍然按“单轮串行”处理，
    // 一次性全部放出去会重新引入并发轮次冲突。
    if (queueFlushingRef.current) {
      return;
    }

    const nextQueuedMessage = queuedOutgoingMessagesRef.current[0];
    const canSendCurrentRound =
      currentStageRef.current === "idle" ||
      currentStageRef.current === "conversation" ||
      currentStageRef.current === "build_ready";

    if (
      !nextQueuedMessage ||
      !studentId ||
      !canSendCurrentRound ||
      assistantStreamingRef.current ||
      assistantThinkingRef.current ||
      createProfileLoadingRef.current
    ) {
      return;
    }

    queueFlushingRef.current = true;

    try {
      const sessionId = await ensureSocketConnected(studentId);

      setQueuedOutgoingMessages((previous) => previous.slice(1));
      updateLocalQueuedMessageStatus(nextQueuedMessage.localMessageId, "sent");
      setConnectionError("");
      setAssistantThinking(true);
      setUiHint("已继续发送你刚才排队的消息，建档助手正在处理...");
      setUiHint("已继续发送你刚才排队的消息，建档助手正在处理...");

      wsRef.current?.send(
        JSON.stringify({
          type: "user_message",
          request_id: `queued-${Date.now()}`,
          session_id: sessionId,
          payload: {
            content: nextQueuedMessage.content,
          },
        })
      );
    } catch (error) {
      setConnectionError(error?.message || "排队消息发送失败，请稍后重试。");
    } finally {
      queueFlushingRef.current = false;
    }
  }

  async function handleSendMessage() {
    const trimmedInput = inputValue.trim();
    const canSendCurrentRound =
      currentStageRef.current === "idle" ||
      currentStageRef.current === "conversation" ||
      currentStageRef.current === "build_ready";
    const hasQueuedMessages = queuedOutgoingMessagesRef.current.length > 0;

    // 中文注释：
    // 发送新消息前，除了要判断“是否正在流式输出”，
    // 还要判断“上一轮是否仍在做 progress / extraction / scoring”。
    // 否则用户会在上一轮还没彻底收尾时继续发消息，后端就会返回 GENERATION_IN_PROGRESS。
    if (!trimmedInput || !profile?.user_id || createProfileLoading) {
      return;
    }

    // 中文注释：
    // 发送新消息前，除了要判断“是否正在流式输出”，
    // 还要判断“上一轮是否仍在做 progress / extraction / scoring”。
    // 否则用户会在上一轮还没彻底收尾时继续发消息，后端就会返回
    // `GENERATION_IN_PROGRESS`，表现成页面像卡住一样。
    if (!trimmedInput || !profile?.user_id || createProfileLoading) {
      return;
    }

    if (hasQueuedMessages || !canSendCurrentRound || assistantStreaming || assistantThinking) {
      const queuedMessage = createChatMessage("user", trimmedInput, {
        deliveryStatus: "queued",
      });

      // 中文注释：
      // 当前轮次还没结束时，用户点击发送不会直接报错，
      // 而是先把消息展示在聊天区，并写入前端排队列表。
      // 等系统回到可继续对话状态后，会自动把这条消息真正发给后端。
      setMessages((previous) => [...previous, queuedMessage]);

      // 中文注释：
      // 当前轮次还没结束时，用户点击发送不会直接报错，
      // 而是先把消息展示在聊天区，并写入前端排队列表。
      // 等系统回到可继续对话状态后，会自动把这条消息真正发给后端。
      setMessages((previous) => [...previous, queuedMessage]);
      setQueuedOutgoingMessages((previous) => [
        ...previous,
        {
          localMessageId: queuedMessage.id,
          content: trimmedInput,
        },
      ]);
      setInputValue("");
      setConnectionError("");
      setUiHint("上一轮还在整理中，这条消息已排队，处理完会自动发送。");

      // 中文注释：
      // 如果当前其实已经回到了可发送状态，只是前面还有更早排队的消息没处理，
      // 那这里顺手触发一次 flush，保证队列按先来先发的顺序往外送。
      if (hasQueuedMessages && canSendCurrentRound && !assistantStreaming && !assistantThinking) {
        window.setTimeout(() => {
          void flushQueuedMessages(profile.user_id);
        }, 0);
      }
      setUiHint("上一轮还在整理中，这条消息已排队，处理完会自动发送。");

      // 中文注释：
      // 如果当前其实已经回到了可发送状态，只是前面还有更早排队的消息没处理，
      // 那么这里顺手触发一次 flush，保证队列按先来先发的顺序往外送。
      if (hasQueuedMessages && canSendCurrentRound && !assistantStreaming && !assistantThinking) {
        window.setTimeout(() => {
          void flushQueuedMessages(profile.user_id);
        }, 0);
      }
      return;
    }

    try {
      const sessionId = await ensureSocketConnected(profile.user_id);

      setMessages((previous) => [...previous, createChatMessage("user", trimmedInput)]);
      setInputValue("");
      setConnectionError("");
      setAssistantThinking(true);
      setUiHint("消息已发送，建档助手正在思考...");
      setUiHint("消息已发送，建档助手正在思考...");

      wsRef.current?.send(
        JSON.stringify({
          type: "user_message",
          request_id: `user-${Date.now()}`,
          session_id: sessionId,
          payload: {
            content: trimmedInput,
          },
        })
      );
    } catch (error) {
      setAssistantThinking(false);
      setConnectionError(error?.message || "发送消息失败，请稍后重试。");
      setConnectionError(error?.message || "发送消息失败，请稍后重试。");
    }
  }

  async function handleCreateProfile() {
    if (!sessionIdRef.current) {
      setConnectionError("当前没有可用的建档会话，请先开始对话。");
      return;
    }

    setCreateProfileLoading(true);
    setConnectionError("");
    setUiHint(hasGeneratedProfile ? "正在更新六维图，请稍候..." : "正在生成六维图，请稍候...");

    try {
      // 中文注释：
      // 新流程里，点击按钮时才真正触发最终建档。
      // 因此这里直接调用后端显式建档接口，而不是像旧流程那样轮询等待自动结果。
      const response = await buildAiChatProfile(sessionIdRef.current);
      const normalized = normalizeProfileResult(response.data);

      setPendingProfileData(normalized);
      setProfileData(normalized);
      setProfileResultStatus(response.data?.result_status || null);
      setSaveErrorMessage(response.data?.save_error_message || "");
      setShowChat(false);
      setUiHint(response.data?.result_status === "saved" ? "六维图已生成" : "六维图已生成，但正式建档保存有异常");
    } catch (error) {
      setConnectionError(
        error?.response?.data?.detail || error?.message || "建档结果生成失败，请稍后再试。"
      );
    } finally {
      setCreateProfileLoading(false);
    }
  }

  async function sendUserMessage() {
    const trimmedInput = inputValue.trim();
    const canSendCurrentRound =
      currentStageRef.current === "idle" ||
      currentStageRef.current === "conversation" ||
      currentStageRef.current === "build_ready";
    const hasQueuedMessages = queuedOutgoingMessagesRef.current.length > 0;

    // 中文注释：
    // 这是当前页面真正使用的发送入口。
    // 这里显式绕开前面历史遗留的旧发送函数，确保用户消息只会被前端追加一次。
    if (!trimmedInput || !profile?.user_id || createProfileLoading) {
      return;
    }

    if (hasQueuedMessages || !canSendCurrentRound || assistantStreaming || assistantThinking) {
      const queuedMessage = createChatMessage("user", trimmedInput, {
        deliveryStatus: "queued",
      });

      // 中文注释：
      // 当上一轮仍在处理时，这里只在前端展示一条“待发送”的本地消息，
      // 同时把内容写入发送队列，等待当前轮结束后自动续发。
      setMessages((previous) => [...previous, queuedMessage]);
      setQueuedOutgoingMessages((previous) => [
        ...previous,
        {
          localMessageId: queuedMessage.id,
          content: trimmedInput,
        },
      ]);
      setInputValue("");
      setConnectionError("");
      setUiHint("上一轮还在整理中，这条消息已排队，处理完会自动发送。");

      if (hasQueuedMessages && canSendCurrentRound && !assistantStreaming && !assistantThinking) {
        window.setTimeout(() => {
          void flushQueuedMessages(profile.user_id);
        }, 0);
      }
      return;
    }

    try {
      const sessionId = await ensureSocketConnected(profile.user_id);

      // 中文注释：
      // 当前轮已可直接发送时，只在这里追加一次用户消息，
      // 避免之前旧逻辑里重复 append 导致同一条输入显示两次。
      setMessages((previous) => [...previous, createChatMessage("user", trimmedInput)]);
      setInputValue("");
      setConnectionError("");
      setAssistantThinking(true);
      setUiHint("消息已发送，建档助手正在思考...");

      wsRef.current?.send(
        JSON.stringify({
          type: "user_message",
          request_id: `user-${Date.now()}`,
          session_id: sessionId,
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
            <TopActionButton label="提醒">
              <div className="home-top-icon-wrap">
                <BellIcon />
                <span className="home-top-icon-dot" />
              </div>
            </TopActionButton>
            <TopActionButton label="设置">
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

          <div className="home-hero-content">
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.2 }}
            >
              <h1 className="home-hero-title">
                欢迎使用录途
                <br />
                <span className="home-hero-title-accent">你的留学申请工具箱</span>
              </h1>
            </motion.div>

            <motion.p
              className="home-hero-subtitle"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.4 }}
            >
              备考、选校、申请、建档，一站式完成你的留学准备。
            </motion.p>

            <motion.div
              className="home-hero-actions"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.6 }}
            >
              {!showChat && !profileData ? (
                <motion.button
                  type="button"
                  onClick={handleStartChat}
                  className="home-primary-button"
                  whileHover={{ scale: 1.05, boxShadow: "0 20px 40px rgba(59,130,246,0.35)" }}
                  whileTap={{ scale: 0.98 }}
                >
                  Get Started
                  <ArrowRightIcon />
                </motion.button>
              ) : null}

              {!profileData ? (
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

            <AnimatePresence mode="wait">
              {showChat && !profileData ? (
                <motion.div
                  key="chat-shell"
                  className="home-ai-shell"
                  initial={{ opacity: 0, y: 24, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 16, scale: 0.96 }}
                  transition={{ type: "spring", stiffness: 220, damping: 28 }}
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

                  <div className="home-ai-body">
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

                    <div className="home-ai-input-row">
                      <textarea
                        className="home-ai-input"
                        value={inputValue}
                        onChange={(event) => setInputValue(event.target.value)}
                        onKeyDown={handleInputKeyDown}
                        placeholder="直接告诉我你的课程体系、成绩、竞赛、活动或项目经历..."
                        rows={2}
                        disabled={createProfileLoading}
                      />

                      <motion.button
                        type="button"
                        className="home-ai-send"
                        onClick={sendUserMessage}
                        whileHover={{ scale: 1.04 }}
                        whileTap={{ scale: 0.96 }}
                        disabled={!inputValue.trim() || createProfileLoading}
                      >
                        <Send size={18} strokeWidth={2.2} />
                      </motion.button>
                    </div>

                    {chatEnded ? (
                      <div className="home-ai-build-row">
                        <div className="home-ai-build-copy">
                          <h4>{hasGeneratedProfile ? "可以更新六维图" : "信息已足够建档"}</h4>
                          <p>
                            {hasGeneratedProfile
                              ? "你可以继续对话补充信息，也可以在合适的时候点击按钮更新六维图。"
                              : "系统已判断信息足够，点击按钮在这里生成你的六维图；如果还想补充，也可以继续对话。"}
                          </p>
                        </div>

                        <motion.button
                          type="button"
                          className="home-ai-build-button"
                          onClick={handleCreateProfile}
                          whileHover={{ scale: 1.04 }}
                          whileTap={{ scale: 0.96 }}
                          disabled={createProfileLoading}
                        >
                          {createProfileLoading ? "正在生成..." : hasGeneratedProfile ? "更新六维图" : "立即建档"}
                          <ArrowRight size={18} strokeWidth={2.2} />
                        </motion.button>
                      </div>
                    ) : null}
                  </div>
                </motion.div>
              ) : null}

              {profileData ? (
                <motion.div
                  key="profile-result"
                  className="home-radar-shell"
                  initial={{ opacity: 0, y: 24, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: 16, scale: 0.96 }}
                  transition={{ type: "spring", stiffness: 220, damping: 28 }}
                >
                  <div className="home-radar-header">
                    <div>
                      <h3 className="home-radar-title">你的六维建档结果</h3>
                      <p className="home-radar-subtitle">
                        {profileResultStatus === "saved" ? "档案已正式建档完成" : "结果已生成，当前处于结果展示态"}
                      </p>
                    </div>

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

                  {saveErrorMessage && profileResultStatus !== "saved" ? (
                    <div className="home-radar-warning">
                      建档结果已生成，但正式保存存在异常：{saveErrorMessage}
                    </div>
                  ) : null}

                  <div className="home-radar-grid">
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

                    <div className="home-radar-score-list">
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
                </motion.div>
              ) : null}
            </AnimatePresence>

            <motion.div
              className="home-hero-stats"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.8 }}
            >
              <div className="home-stat">
                <div className="home-stat-number">10K+</div>
                <div className="home-stat-label">注册用户</div>
              </div>
              <div className="home-stat">
                <div className="home-stat-number">500+</div>
                <div className="home-stat-label">合作院校</div>
              </div>
              <div className="home-stat">
                <div className="home-stat-number">98%</div>
                <div className="home-stat-label">满意度</div>
              </div>
            </motion.div>
          </div>
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
