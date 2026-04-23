import axios from "axios";
import { clearAccessToken, getAccessToken } from "../utils/authStorage";

const studentProfileRequest = axios.create({
  baseURL: "",
  timeout: 180000,
});

studentProfileRequest.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

studentProfileRequest.interceptors.response.use(
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

export function getStudentProfileLanguageArchive() {
  return studentProfileRequest.get("/api/v1/student-profile/archive/language");
}

export function getStudentProfileCurrentSession(options = {}) {
  return studentProfileRequest.get("/api/v1/student-profile/sessions/current", {
    params: {
      create_if_missing: options.createIfMissing ? 1 : 0,
    },
  });
}

export function getStudentProfileArchive(sessionId) {
  return studentProfileRequest.get("/api/v1/student-profile/archive", {
    params: sessionId ? { session_id: sessionId } : {},
  });
}

export function getStudentProfileCurriculumArchive() {
  return studentProfileRequest.get("/api/v1/student-profile/archive/curriculum");
}

export function saveStudentProfileArchive(sessionId, archiveForm) {
  return studentProfileRequest.post("/api/v1/student-profile/archive", {
    session_id: sessionId,
    archive_form: archiveForm,
  });
}

export function syncStudentProfileArchiveDraft(sessionId) {
  return studentProfileRequest.post("/api/v1/student-profile/archive/draft/sync", {
    session_id: sessionId,
  });
}

export function regenerateStudentProfileArchiveRadar(sessionId) {
  return studentProfileRequest.post("/api/v1/student-profile/archive/regenerate-radar", {
    session_id: sessionId,
  });
}

export function saveStudentProfileLanguageArchive(archiveForm) {
  return studentProfileRequest.post("/api/v1/student-profile/archive/language", {
    archive_form: archiveForm,
  });
}

export function saveStudentProfileCurriculumArchive(archiveForm) {
  return studentProfileRequest.post("/api/v1/student-profile/archive/curriculum", {
    archive_form: archiveForm,
  });
}
