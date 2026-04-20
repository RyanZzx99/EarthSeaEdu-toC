import axios from "axios";
import { clearAccessToken, getAccessToken } from "../utils/authStorage";

const guidedRequest = axios.create({
  baseURL: "",
  timeout: 180000,
});

guidedRequest.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

guidedRequest.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearAccessToken();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login?session_expired=1";
      }
    }
    return Promise.reject(error);
  }
);

export function getCurrentGuidedProfileSession(createIfMissing = true) {
  return guidedRequest.get("/api/v1/student-profile/guided/current", {
    params: { create_if_missing: createIfMissing ? 1 : 0 },
  });
}

export function submitGuidedProfileAnswer(sessionId, questionCode, answer) {
  return guidedRequest.post(`/api/v1/student-profile/guided/sessions/${sessionId}/answers`, {
    question_code: questionCode,
    answer,
  });
}

export function exitGuidedProfileSession(sessionId, triggerReason = "manual_exit") {
  return guidedRequest.post(`/api/v1/student-profile/guided/sessions/${sessionId}/exit`, {
    trigger_reason: triggerReason,
  });
}

export function restartGuidedProfileSession() {
  return guidedRequest.post("/api/v1/student-profile/guided/restart");
}

