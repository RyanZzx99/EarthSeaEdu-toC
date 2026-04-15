import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { ArrowLeft, Bell, House, Settings } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/auth";

const DEFAULT_MODE_ITEMS = [
  { key: "exam", label: "模拟考试", path: "/mockexam" },
  { key: "practice", label: "练习模式", path: "/mockexam/practice" },
];

function getDisplayName(profile) {
  return profile?.nickname || profile?.mobile || "用户";
}

function getDisplaySubName(profile) {
  return profile?.email || profile?.mobile || "";
}

function getDisplayInitial(profile) {
  const rawValue = String(profile?.nickname || profile?.mobile || "L").trim();
  return rawValue.slice(0, 1).toUpperCase() || "L";
}

export default function MockExamModeHeader({
  activeMode = "exam",
  tabs = null,
  backButton = null,
}) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const tabItems = Array.isArray(tabs) ? tabs : DEFAULT_MODE_ITEMS;

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        const response = await getMe();
        if (!active) {
          return;
        }
        setProfile(response.data || null);
      } catch (_error) {
        if (!active) {
          return;
        }
        setProfile(null);
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
  }, []);

  return (
    <motion.header
      className="home-topbar mockexam-topbar-shell"
      initial={false}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.28 }}
    >
      <div className="home-topbar-inner">
        <div className="mockexam-topbar-left">
          {backButton ? (
            <button
              type="button"
              className="mockexam-topbar-back"
              onClick={() => navigate(backButton.path)}
            >
              <ArrowLeft size={16} strokeWidth={2.1} />
              <span>{backButton.label}</span>
            </button>
          ) : null}

          <button
            type="button"
            className="mockexam-topbar-brand"
            onClick={() => navigate("/")}
            aria-label="返回首页"
          >
            <div className="home-brand">
              <div className="home-brand-mark">L</div>
              <div className="home-brand-text">LutoolBox</div>
            </div>
          </button>
        </div>

        {tabItems.length ? (
          <nav className="mockexam-topbar-tabs" aria-label="mockexam-header-tabs">
            {tabItems.map((item) => (
              <button
                key={item.key}
                type="button"
                className={`mockexam-topbar-tab${activeMode === item.key ? " active" : ""}`}
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        ) : null}

        <div className="home-top-actions">
          <button type="button" className="mockexam-topbar-home" onClick={() => navigate("/")}>
            <House size={16} strokeWidth={2.1} />
            <span>返回首页</span>
          </button>

          <button type="button" className="home-top-icon-button" aria-label="通知" onClick={() => {}}>
            <span className="home-top-icon-wrap">
              <Bell size={18} strokeWidth={2.1} />
              <span className="home-top-icon-dot" />
            </span>
          </button>

          <button
            type="button"
            className="home-top-icon-button"
            aria-label="设置"
            onClick={() => navigate("/profile")}
          >
            <Settings size={18} strokeWidth={2.1} />
          </button>

          <div className="home-top-divider" />

          <button type="button" className="home-user-entry" onClick={() => navigate("/profile")}>
            <div className="home-user-copy mockexam-topbar-usercopy">
              <p className="home-user-name">{getDisplayName(profile)}</p>
              <p className="home-user-subname">{getDisplaySubName(profile)}</p>
            </div>
            {profile?.avatar_url ? (
              <img src={profile.avatar_url} alt="用户头像" className="home-user-avatar" />
            ) : (
              <div className="home-user-avatar home-user-avatar-fallback">
                {getDisplayInitial(profile)}
              </div>
            )}
          </button>
        </div>
      </div>
    </motion.header>
  );
}
