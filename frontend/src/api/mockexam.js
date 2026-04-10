import axios from "axios";
import { clearAccessToken, getAccessToken } from "../utils/authStorage";

const request = axios.create({
  baseURL: "",
  timeout: 10000,
});

request.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
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
      clearAccessToken();
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

export function getMockExamBetaOptions() {
  return request.get("/api/v1/mockexam/beta/options");
}

export function getMockExamQuestionBanks(params) {
  return request.get("/api/v1/mockexam/question-banks", {
    params,
  });
}

export function getMockExamBetaPapers(params) {
  return request.get("/api/v1/mockexam/beta/papers", {
    params,
  });
}

export function getMockExamQuestionBank(questionBankId) {
  return request.get(`/api/v1/mockexam/question-banks/${questionBankId}`);
}

export function getMockExamBetaPaper(examPaperId) {
  return request.get(`/api/v1/mockexam/beta/papers/${examPaperId}`);
}

export function getMockExamSubmissions(params) {
  return request.get("/api/v1/mockexam/submissions", {
    params,
  });
}

export function getMockExamSubmission(submissionId) {
  return request.get(`/api/v1/mockexam/submissions/${submissionId}`);
}

export function submitMockExam(questionBankId, data) {
  return request.post(`/api/v1/mockexam/question-banks/${questionBankId}/submit`, data);
}

export function submitMockExamBetaPaper(examPaperId, data) {
  return request.post(`/api/v1/mockexam/beta/papers/${examPaperId}/submit`, data);
}

export function getMockExamExamSets(params) {
  return request.get("/api/v1/mockexam/exam-sets", {
    params,
  });
}

export function getMockExamExamSet(examSetId) {
  return request.get(`/api/v1/mockexam/exam-sets/${examSetId}`);
}

export function createMockExamExamSet(data) {
  return request.post("/api/v1/mockexam/exam-sets", data);
}

export function updateMockExamExamSetStatus(examSetId, data) {
  return request.post(`/api/v1/mockexam/exam-sets/${examSetId}/status`, data);
}

export function deleteMockExamExamSet(examSetId) {
  return request.delete(`/api/v1/mockexam/exam-sets/${examSetId}`);
}

export function submitMockExamExamSet(examSetId, data) {
  return request.post(`/api/v1/mockexam/exam-sets/${examSetId}/submit`, data);
}

export function buildMockExamQuickPractice(data) {
  return request.post("/api/v1/mockexam/quick-practice/build", data);
}

export function evaluateInlineMockExam(data) {
  return request.post("/api/v1/mockexam/evaluate", data);
}
