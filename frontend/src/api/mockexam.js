import axios from "axios";

const request = axios.create({
  baseURL: "",
  timeout: 10000,
});

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("access_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response) => response,
  (error) => {
    const response = error?.response;
    if (response?.status === 401) {
      localStorage.removeItem("access_token");
      if (window.location.pathname !== "/login") {
        window.location.href = "/login?session_expired=1";
      }
    }
    return Promise.reject(error);
  }
);

export function getMockExamOptions() {
  return request.get("/api/v1/mockexam/options");
}

export function getMockExamQuestionBanks(params) {
  return request.get("/api/v1/mockexam/question-banks", {
    params,
  });
}

export function getMockExamQuestionBank(questionBankId) {
  return request.get(`/api/v1/mockexam/question-banks/${questionBankId}`);
}

export function submitMockExam(questionBankId, data) {
  return request.post(`/api/v1/mockexam/question-banks/${questionBankId}/submit`, data);
}
