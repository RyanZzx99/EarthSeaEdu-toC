import React from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import AdminConsolePage from "./pages/AdminConsolePage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import ProfilePage from "./pages/ProfilePage";

function RequireAuth({ children }) {
  const location = useLocation();
  const token = localStorage.getItem("access_token");

  // 中文注释：未登录时统一回到登录页，并保留来源路由用于后续扩展
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

function RedirectLoggedInLogin() {
  const token = localStorage.getItem("access_token");

  if (token) {
    return <Navigate to="/" replace />;
  }

  return <LoginPage />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<RedirectLoggedInLogin />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <HomePage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        }
      />
      <Route path="/admin-console" element={<AdminConsolePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
