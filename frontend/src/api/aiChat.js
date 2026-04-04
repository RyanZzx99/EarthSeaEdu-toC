/**
 * AI 建档模块前端接口封装
 */

import axios from "axios";

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

export function getCurrentAiChatSession(
  bizDomain = "student_profile_build",
  options = {}
) {
  return aiChatRequest.get("/api/v1/ai-chat/sessions/current", {
    params: {
      biz_domain: bizDomain,
      create_if_missing: options.createIfMissing ? 1 : 0,
    },
  });
}

export function getAiChatSessionDetail(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}`);
}

export function getAiChatMessages(sessionId, params = {}) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/messages`, {
    params,
  });
}

export function getAiChatResult(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/result`);
}

export function getAiChatArchiveForm(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/archive-form`, {
    timeout: 180000,
  });
}

export function saveAiChatArchiveForm(sessionId, archiveForm) {
  return aiChatRequest.post(
    `/api/v1/ai-chat/sessions/${sessionId}/archive-form`,
    {
      archive_form: archiveForm,
    },
    {
      timeout: 180000,
    }
  );
}

export function regenerateAiChatArchiveRadar(sessionId) {
  return aiChatRequest.post(
    `/api/v1/ai-chat/sessions/${sessionId}/archive-form/regenerate-radar`,
    null,
    {
      timeout: 180000,
    }
  );
}

export function syncAiChatDraftFromOfficial(sessionId) {
  return aiChatRequest.post(
    `/api/v1/ai-chat/sessions/${sessionId}/draft/sync-from-official`,
    null,
    {
      timeout: 180000,
    }
  );
}

export function regenerateAiChatDraftRadar(sessionId) {
  return aiChatRequest.post(
    `/api/v1/ai-chat/sessions/${sessionId}/draft/regenerate-radar`,
    null,
    {
      timeout: 180000,
    }
  );
}

export function getAiChatRadar(sessionId) {
  return aiChatRequest.get(`/api/v1/ai-chat/sessions/${sessionId}/radar`);
}

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
