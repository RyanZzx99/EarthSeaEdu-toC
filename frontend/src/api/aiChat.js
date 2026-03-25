/**
 * AI 建档模块前端接口封装
 *
 * 这一层的作用：
 * 1. 统一封装 AI 建档相关的 REST 查询接口
 * 2. 统一给请求补 Authorization 头，避免页面里重复写 token 逻辑
 * 3. 统一生成 WebSocket 地址，避免页面组件自己拼接 ws:// / wss://
 *
 * 设计说明：
 * 1. REST 请求仍然复用 Vite 的 /api 代理，所以 baseURL 保持空字符串
 * 2. WebSocket 因为浏览器不会走 axios，也不会自动走 Vite 的 HTTP 代理，
 *    所以这里单独提供一个地址构建函数
 * 3. 开发环境下默认指向同主机的 8000 端口，生产环境默认跟随当前域名
 */

import axios from "axios";

/**
 * 创建 AI 建档专用 axios 实例
 *
 * 说明：
 * 1. 这里和 auth.js 一样，统一从 localStorage 读取 access_token
 * 2. 这样首页在调用会话查询、消息恢复、结果查询时，不需要再手动拼请求头
 */
const aiChatRequest = axios.create({
  baseURL: "",
  timeout: 15000,
});

aiChatRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("access_token");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

aiChatRequest.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem("access_token");

      if (window.location.pathname !== "/login") {
        window.location.href = "/login?session_expired=1";
      }
    }

    return Promise.reject(error);
  }
);

/**
 * 查询当前学生在指定业务域下是否已有 active 会话
 *
 * 用途：
 * 1. 首页刷新后恢复正在进行中的 AI 建档会话
 * 2. 如果没有 active 会话，则前端等用户点击 Get Started 后再走 connect_init
 */
export function getCurrentAiChatSession(bizDomain = "student_profile_build") {
  return aiChatRequest.get("/api/v1/ai-chat/sessions/current", {
    params: {
      biz_domain: bizDomain,
    },
  });
}

/**
 * 查询某个 session 的详情
 *
 * 用途：
 * 1. 恢复页面时读取当前阶段、轮次、缺失维度
 * 2. 判断当前会话是否已经 completed / failed
 */
export function getAiChatSessionDetail(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}`);
}

/**
 * 查询某个 session 的可见历史消息
 *
 * 用途：
 * 1. 页面刷新后恢复聊天记录
 * 2. 后续如果要做“向上翻更多历史消息”，可以继续传 before_id
 */
export function getAiChatMessages(sessionId, params = {}) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/messages`, {
    params,
  });
}

/**
 * 查询某个 session 的最终结果
 *
 * 用途：
 * 1. 对话结束后读取六维图和中文总结
 * 2. 页面刷新后，如果之前已经建档完成，可直接恢复结果展示
 */
export function getAiChatResult(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/result`);
}

/**
 * 查询某个 session 的六维图数据
 *
 * 说明：
 * 1. 当前首页直接用完整 result 接口就能满足需求
 * 2. 这里保留单独的 radar 接口封装，便于后续单独复用
 */
export function getAiChatRadar(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/radar`);
}

/**
 * 主动触发某个 session 的六维图生成 / 更新
 *
 * 新业务流程里：
 * 1. WebSocket 只负责对话与进度更新
 * 2. 当系统判断信息足够后，前端展示“立即建档 / 更新六维图”按钮
 * 3. 只有用户点击按钮时，才调用这个接口执行最终 extraction / scoring / 正式建档
 */
export function buildAiChatProfile(sessionId) {
  // 中文注释：
  // “立即建档 / 更新六维图”会触发 extraction、code_resolution、scoring 和正式入库，
  // 整条链路明显比普通查询接口更慢。
  // 因此这里单独放宽超时时间，避免沿用全局 15 秒后被前端过早判定失败。
  return aiChatRequest.post(`/api/v1/ai-chat/sessions/${sessionId}/build-profile`, null, {
    timeout: 180000,
  });
}

/**
 * 构建 AI 建档 WebSocket 地址
 *
 * 地址规则：
 * 1. 如果显式配置了 VITE_AI_CHAT_WS_BASE_URL，则优先使用该值
 * 2. 开发环境默认连接到当前主机的 8000 端口
 * 3. 生产环境默认跟当前页面域名走同域
 *
 * 返回示例：
 * - ws://127.0.0.1:8000/ws/ai-chat
 * - wss://example.com/ws/ai-chat
 */
export function buildAiChatWsUrl() {
  const explicitBaseUrl = import.meta.env.VITE_AI_CHAT_WS_BASE_URL;

  if (explicitBaseUrl) {
    return `${explicitBaseUrl.replace(/\/$/, "")}/ws/ai-chat`;
  }

  const protocol = window.location.protocol === "https:" ? "wss" : "ws";

  if (import.meta.env.DEV) {
    return `${protocol}://${window.location.hostname}:8000/ws/ai-chat`;
  }

  return `${protocol}://${window.location.host}/ws/ai-chat`;
}
