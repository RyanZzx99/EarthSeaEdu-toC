import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/auth";

const NAV_ITEMS = [
  { key: "exam", label: "模考模式 - 待更新", path: "/mockexam" },
  { key: "practice", label: "练习模式", path: "/mockexam/practice" },
];

function BrandBlock() {
  return (
    <div className="home-brand">
      <div className="home-brand-mark">录</div>
      <div className="home-brand-text">录途 Toolbox</div>
    </div>
  );
}

function getDisplayName(profile) {
  return profile?.nickname || profile?.mobile || profile?.user_id || "用户";
}

function getDisplaySubName(profile) {
  return profile?.mobile || "个人中心";
}

function getDisplayInitial(profile) {
  const rawValue = profile?.nickname || profile?.mobile || "录";
  return String(rawValue).trim().slice(0, 1).toUpperCase() || "录";
}

function MockExamNavButton({ item, isActive, onClick }) {
  return (
    <motion.button
      type="button"
      onClick={onClick}
      className="home-nav-button"
      style={{
        color: isActive ? "#1a2744" : "#6b7280",
        background: isActive ? "#f0f4ff" : "transparent",
      }}
      whileHover={{ background: isActive ? "#f0f4ff" : "#f9fafb" }}
      whileTap={{ scale: 0.98 }}
    >
      <span className="home-nav-button-label">{item.label}</span>
      {isActive ? (
        <motion.div
          layoutId="mockexam-section-indicator"
          className="home-nav-indicator"
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      ) : null}
    </motion.button>
  );
}

export default function MockExamModeHeader({ activeMode = "exam" }) {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);

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
      className="home-topbar mockexam-home-topbar"
      initial={false}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.4 }}
    >
      <div className="home-topbar-inner">
        <button type="button" className="mockexam-home-brand-button" onClick={() => navigate("/")}>
          <BrandBlock />
        </button>

        <nav className="home-top-nav">
          {NAV_ITEMS.map((item) => (
            <MockExamNavButton
              key={item.key}
              item={item}
              isActive={activeMode === item.key}
              onClick={() => navigate(item.path)}
            />
          ))}
        </nav>

        <div className="home-top-actions">
          <button type="button" className="home-teacher-switch-button" onClick={() => navigate("/")}>
            <ArrowLeft size={18} strokeWidth={2.1} />
            <span>返回首页</span>
          </button>
          <div className="home-top-divider" />

          <motion.button
            type="button"
            onClick={() => navigate("/profile")}
            className="home-user-entry"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <div className="home-user-copy">
              <p className="home-user-name">{getDisplayName(profile)}</p>
              <p className="home-user-subname">{getDisplaySubName(profile)}</p>
            </div>
            {profile?.avatar_url ? (
              <img src={profile.avatar_url} alt="用户头像" className="home-user-avatar" />
            ) : (
              <div className="home-user-avatar home-user-avatar-fallback">{getDisplayInitial(profile)}</div>
            )}
          </motion.button>
        </div>
      </div>
    </motion.header>
  );
}
