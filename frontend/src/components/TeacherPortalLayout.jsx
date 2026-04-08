import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  ArrowRight,
  BookOpenCheck,
  FolderSearch2,
  GraduationCap,
} from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { getMe } from "../api/auth";

const SHORTCUT_ITEMS = [
  {
    key: "mockexam",
    title: "模拟考试",
    description: "进入模考入口、组合试卷创建与试卷列表管理",
    path: "/teacher/mockexam",
    icon: GraduationCap,
    palette: {
      start: "#f7f1e2",
      end: "#ece3cc",
      border: "rgba(152, 125, 58, 0.18)",
      badge: "rgba(181, 150, 73, 0.14)",
      icon: "#8f7430",
      glow: "rgba(204, 183, 128, 0.34)",
    },
  },
  {
    key: "archive",
    title: "学生档案",
    description: "按学生ID或手机号查看正式档案与六维图结果",
    path: "/teacher/student-archive",
    icon: FolderSearch2,
    palette: {
      start: "#edf5ef",
      end: "#e1eee4",
      border: "rgba(90, 128, 101, 0.16)",
      badge: "rgba(101, 145, 114, 0.13)",
      icon: "#567966",
      glow: "rgba(154, 191, 166, 0.28)",
    },
  },
];

function getActiveShortcutKey(pathname) {
  if (pathname.startsWith("/teacher/mockexam")) {
    return "mockexam";
  }
  if (pathname.startsWith("/teacher/student-archive")) {
    return "archive";
  }
  return "";
}

function TeacherBrandBlock() {
  return (
    <div className="home-brand">
      <div className="home-brand-mark">录</div>
      <div className="home-brand-text">录途 Toolbox</div>
    </div>
  );
}

function TeacherNavButton({ item, isActive, onClick }) {
  const Icon = item.icon;

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
      <Icon size={18} strokeWidth={2} />
      <span className="home-nav-button-label">{item.title}</span>
      {isActive ? (
        <motion.div
          layoutId="teacher-section-indicator"
          className="home-nav-indicator"
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      ) : null}
    </motion.button>
  );
}

function TeacherShortcutCard({ item, index, onClick }) {
  const Icon = item.icon;

  return (
    <motion.button
      type="button"
      className="home-shortcut-card"
      onClick={onClick}
      initial={false}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.42, delay: 0.1 + index * 0.06 }}
      whileTap={{ scale: 0.985 }}
      style={{
        "--shortcut-start": item.palette.start,
        "--shortcut-end": item.palette.end,
        "--shortcut-border": item.palette.border,
        "--shortcut-badge": item.palette.badge,
        "--shortcut-icon": item.palette.icon,
        "--shortcut-glow": item.palette.glow,
      }}
    >
      <span className="home-shortcut-card-glow" aria-hidden="true" />

      <div className="home-shortcut-card-head">
        <span className="home-shortcut-card-badge">
          <Icon size={20} strokeWidth={2.1} />
        </span>
        <span className="home-shortcut-card-arrow" aria-hidden="true">
          <ArrowRight size={18} strokeWidth={2.1} />
        </span>
      </div>

      <div className="home-shortcut-card-copy">
        <h3>{item.title}</h3>
        <p>{item.description}</p>
      </div>
    </motion.button>
  );
}

function getDisplayName(profile) {
  return profile?.nickname || profile?.mobile || profile?.user_id || "教师用户";
}

function getDisplaySubName(profile) {
  return profile?.mobile || "教师端账号";
}

function getDisplayInitial(profile) {
  const rawValue = profile?.nickname || profile?.mobile || "师";
  return String(rawValue).trim().slice(0, 1).toUpperCase() || "师";
}

export default function TeacherPortalLayout({
  heroTitle,
  heroSubtitle,
  showShortcutStrip = true,
  children,
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const activeShortcutKey = useMemo(
    () => getActiveShortcutKey(location.pathname),
    [location.pathname]
  );

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        const response = await getMe();
        if (!active) {
          return;
        }

        if (!response.data?.is_teacher) {
          navigate("/", { replace: true });
          return;
        }

        setProfile(response.data);
        setErrorMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setErrorMessage(error?.response?.data?.detail || "教师端加载失败，请稍后重试。");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void bootstrap();

    return () => {
      active = false;
    };
  }, [navigate]);

  function handleShortcutClick(item) {
    navigate(item.path);
  }

  if (loading) {
    return <div className="loading-box">正在加载教师端页面...</div>;
  }

  if (errorMessage) {
    return <div className="error-box">{errorMessage}</div>;
  }

  return (
    <div className="home-shell teacher-portal-shell">
      <motion.header
        className="home-topbar"
        initial={false}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="home-topbar-inner">
          <TeacherBrandBlock />

          <nav className="home-top-nav">
            {SHORTCUT_ITEMS.map((item) => (
              <TeacherNavButton
                key={item.key}
                item={item}
                isActive={activeShortcutKey === item.key}
                onClick={() => handleShortcutClick(item)}
              />
            ))}
          </nav>

          <div className="home-top-actions">
            <button
              type="button"
              className="home-teacher-switch-button"
              onClick={() => navigate("/")}
            >
              <BookOpenCheck size={18} strokeWidth={2.1} />
              <span>返回学生端</span>
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
                <div className="home-user-avatar home-user-avatar-fallback">
                  {getDisplayInitial(profile)}
                </div>
              )}
            </motion.button>
          </div>
        </div>
      </motion.header>

      <main className="home-main">
        <section className="home-hero teacher-portal-hero">
          <div className="home-hero-bg" />
          <div className="home-hero-orb home-hero-orb-left" />
          <div className="home-hero-orb home-hero-orb-right" />

          <div className="home-hero-content teacher-portal-hero-content">
            <div className="teacher-portal-badge">
              <GraduationCap size={18} strokeWidth={2.1} />
              <span>教师端</span>
            </div>
            <h1 className="home-hero-title">{heroTitle}</h1>
            <p className="home-hero-subtitle">{heroSubtitle}</p>
          </div>
        </section>

        <div className="home-content-wrap teacher-portal-content">
          {showShortcutStrip ? (
            <section className="home-shortcut-strip">
              <div className="home-shortcut-track">
                {SHORTCUT_ITEMS.map((item, index) => (
                  <TeacherShortcutCard
                    key={item.key}
                    item={item}
                    index={index}
                    onClick={() => handleShortcutClick(item)}
                  />
                ))}
              </div>
            </section>
          ) : null}

          {children}
        </div>
      </main>
    </div>
  );
}
