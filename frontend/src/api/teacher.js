import axios from "axios";
import { clearAccessToken, getAccessToken } from "../utils/authStorage";

const request = axios.create({
  baseURL: "",
  timeout: 15000,
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
    if (error?.response?.status === 401) {
      clearAccessToken();
      if (window.location.pathname !== "/login") {
        window.location.href = "/login?session_expired=1";
      }
    }
    return Promise.reject(error);
  }
);

export function getTeacherStudentArchive(params) {
  return request.get("/api/v1/teacher/students/archive", {
    params,
  });
}
