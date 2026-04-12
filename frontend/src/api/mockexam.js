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

export function getMockExamPapers(params) {
  return request.get("/api/v1/mockexam/papers", {
    params,
  });
}

export function getMockExamPaper(examPaperId) {
  return request.get(`/api/v1/mockexam/papers/${examPaperId}`);
}

export function getMockExamPaperSets(params) {
  return request.get("/api/v1/mockexam/paper-sets", {
    params,
  });
}

export function getMockExamPaperSet(mockexamPaperSetId) {
  return request.get(`/api/v1/mockexam/paper-sets/${mockexamPaperSetId}`);
}

export function submitMockExamPaper(examPaperId, data) {
  return request.post(`/api/v1/mockexam/papers/${examPaperId}/submit`, data);
}

export function submitMockExamPaperSet(mockexamPaperSetId, data) {
  return request.post(`/api/v1/mockexam/paper-sets/${mockexamPaperSetId}/submit`, data);
}

export function getMockExamSubmissions(params) {
  return request.get("/api/v1/mockexam/submissions", {
    params,
  });
}

export function getMockExamSubmission(submissionId) {
  return request.get(`/api/v1/mockexam/submissions/${submissionId}`);
}

export function getMockExamProgresses(params) {
  return request.get("/api/v1/mockexam/progress", {
    params,
  });
}

export function getMockExamProgress(progressId) {
  return request.get(`/api/v1/mockexam/progress/${progressId}`);
}

export function saveMockExamProgress(examPaperId, data) {
  return request.post(`/api/v1/mockexam/papers/${examPaperId}/progress`, data);
}

export function saveMockExamPaperSetProgress(mockexamPaperSetId, data) {
  return request.post(`/api/v1/mockexam/paper-sets/${mockexamPaperSetId}/progress`, data);
}

export function discardMockExamProgress(progressId) {
  return request.post(`/api/v1/mockexam/progress/${progressId}/discard`);
}

export function getMockExamFavorites(params) {
  return request.get("/api/v1/mockexam/favorites", {
    params,
  });
}

export function toggleMockExamFavorite(examQuestionId, data) {
  return request.post(`/api/v1/mockexam/questions/${examQuestionId}/favorite`, data);
}

export function getMockExamWrongQuestions(params) {
  return request.get("/api/v1/mockexam/wrong-questions", {
    params,
  });
}

export function getMockExamQuestionDetail(examQuestionId) {
  return request.get(`/api/v1/mockexam/questions/${examQuestionId}`);
}

export function getTeacherMockExamPaperSets() {
  return request.get("/api/v1/teacher/mockexam/paper-sets");
}

export function createTeacherMockExamPaperSet(data) {
  return request.post("/api/v1/teacher/mockexam/paper-sets", data);
}

export function updateTeacherMockExamPaperSetStatus(mockexamPaperSetId, data) {
  return request.post(`/api/v1/teacher/mockexam/paper-sets/${mockexamPaperSetId}/status`, data);
}
