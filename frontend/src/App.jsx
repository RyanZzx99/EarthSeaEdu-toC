import React, { useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { getMe } from "./api/auth";
import { LoadingPage } from "./components/LoadingPage";
import AdminConsolePage from "./pages/AdminConsolePage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import MockExamPage from "./pages/MockExamPage";
import MockExamPracticePage from "./pages/MockExamPracticePage";
import MockExamQuestionDetailPage from "./pages/MockExamQuestionDetailPage";
import MockExamRunnerPage from "./pages/MockExamRunnerPage";
import MockExamSubmissionResultPage from "./pages/MockExamSubmissionResultPage";
import MockExamTestDetailPage from "./pages/MockExamTestDetailPage";
import MockExamTestPracticePage from "./pages/MockExamTestPracticePage";
import MockExamTopicPracticePage from "./pages/MockExamTopicPracticePage";
import ProfilePage from "./pages/ProfilePage";
import TeacherMockExamPage from "./pages/TeacherMockExamPage";
import TeacherPage from "./pages/TeacherPage";
import TeacherStudentArchivePage from "./pages/TeacherStudentArchivePage";
import { getAccessToken } from "./utils/authStorage";

const AUTH_OPTIONAL_PATHS = new Set(["/admin-console", "/admin-concole"]);

function useResolvedAuthState() {
  const location = useLocation();
  const [authState, setAuthState] = useState({
    status: "checking",
    reason: "unknown",
  });
  const [authRefreshSeed, setAuthRefreshSeed] = useState(0);

  useEffect(() => {
    function handleAuthTokenChanged() {
      setAuthState({
        status: "checking",
        reason: "token_changed",
      });
      setAuthRefreshSeed((previous) => previous + 1);
    }

    window.addEventListener("auth-token-changed", handleAuthTokenChanged);
    return () => {
      window.removeEventListener("auth-token-changed", handleAuthTokenChanged);
    };
  }, []);

  useEffect(() => {
    let active = true;

    async function verifyLoginState() {
      if (AUTH_OPTIONAL_PATHS.has(location.pathname)) {
        return;
      }

      const token = getAccessToken();

      if (!token) {
        if (active) {
          setAuthState({
            status: "unauthenticated",
            reason: "missing_token",
          });
        }
        return;
      }

      if (active) {
        setAuthState({
          status: "checking",
          reason: "verifying_token",
        });
      }

      try {
        await getMe();
        if (active) {
          setAuthState({
            status: "authenticated",
            reason: "verified",
          });
        }
      } catch (_error) {
        if (active) {
          setAuthState({
            status: "unauthenticated",
            reason: "expired_token",
          });
        }
      }
    }

    void verifyLoginState();

    return () => {
      active = false;
    };
  }, [location.pathname, authRefreshSeed]);

  return authState;
}

function AuthCheckingScreen({ pathname }) {
  if (pathname !== "/") {
    return null;
  }

  return <LoadingPage message="正在进入录途" submessage="请稍候，正在准备首页内容" />;
}

function RequireAuth({ children, authState }) {
  const location = useLocation();

  if (authState.status === "checking") {
    return <AuthCheckingScreen pathname={location.pathname} />;
  }

  if (authState.status !== "authenticated") {
    const loginPath =
      authState.reason === "expired_token" ? "/login?session_expired=1" : "/login";
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />;
  }

  return children;
}

function RedirectLoggedInLogin({ authState }) {
  const location = useLocation();

  if (authState.status === "checking") {
    return <AuthCheckingScreen pathname={location.pathname} />;
  }

  if (authState.status === "authenticated") {
    return <Navigate to="/" replace />;
  }

  return <LoginPage />;
}

export default function App() {
  const authState = useResolvedAuthState();

  return (
    <Routes>
      <Route path="/login" element={<RedirectLoggedInLogin authState={authState} />} />
      <Route
        path="/"
        element={
          <RequireAuth authState={authState}>
            <HomePage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth authState={authState}>
            <ProfilePage />
          </RequireAuth>
        }
      />
      <Route
        path="/teacher"
        element={
          <RequireAuth authState={authState}>
            <TeacherPage />
          </RequireAuth>
        }
      />
      <Route
        path="/teacher/mockexam"
        element={
          <RequireAuth authState={authState}>
            <TeacherMockExamPage />
          </RequireAuth>
        }
      />
      <Route
        path="/teacher/student-archive"
        element={
          <RequireAuth authState={authState}>
            <TeacherStudentArchivePage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam"
        element={
          <RequireAuth authState={authState}>
            <MockExamPage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/practice"
        element={
          <RequireAuth authState={authState}>
            <MockExamPracticePage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/practice/topic"
        element={
          <RequireAuth authState={authState}>
            <MockExamTopicPracticePage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/practice/test"
        element={
          <RequireAuth authState={authState}>
            <MockExamTestPracticePage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/practice/test/:id/:action"
        element={
          <RequireAuth authState={authState}>
            <MockExamTestDetailPage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/questions/:examQuestionId"
        element={
          <RequireAuth authState={authState}>
            <MockExamQuestionDetailPage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/run/:sourceType/:sourceId"
        element={
          <RequireAuth authState={authState}>
            <MockExamRunnerPage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/results/:submissionId"
        element={
          <RequireAuth authState={authState}>
            <MockExamSubmissionResultPage />
          </RequireAuth>
        }
      />
      <Route path="/admin-concole" element={<Navigate to="/admin-console" replace />} />
      <Route path="/admin-console" element={<AdminConsolePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
