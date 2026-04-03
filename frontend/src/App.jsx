import React, { useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { getMe } from "./api/auth";
import AdminConsolePage from "./pages/AdminConsolePage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import MockExamPage from "./pages/MockExamPage";
import MockExamRunnerPage from "./pages/MockExamRunnerPage";
import ProfilePage from "./pages/ProfilePage";

function useResolvedAuthState() {
  const location = useLocation();
  const [authState, setAuthState] = useState({
    status: "checking",
    reason: "unknown",
  });

  useEffect(() => {
    let active = true;
    const token = localStorage.getItem("access_token");

    async function verifyLoginState() {
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
  }, [location.pathname]);

  return authState;
}

function AuthCheckingScreen() {
  return <div className="loading-box">正在校验登录状态...</div>;
}

function RequireAuth({ children, authState }) {
  const location = useLocation();

  if (authState.status === "checking") {
    return <AuthCheckingScreen />;
  }

  if (authState.status !== "authenticated") {
    const loginPath =
      authState.reason === "expired_token" ? "/login?session_expired=1" : "/login";
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />;
  }

  return children;
}

function RedirectLoggedInLogin({ authState }) {
  if (authState.status === "checking") {
    return <AuthCheckingScreen />;
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
        path="/mockexam"
        element={
          <RequireAuth authState={authState}>
            <MockExamPage />
          </RequireAuth>
        }
      />
      <Route
        path="/mockexam/run/:questionBankId"
        element={
          <RequireAuth authState={authState}>
            <MockExamRunnerPage />
          </RequireAuth>
        }
      />
      <Route path="/admin-console" element={<AdminConsolePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
