import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Bell,
  BookOpen,
  Calculator,
  GraduationCap,
  Home,
  Link2,
  Settings,
  TrendingUp,
} from "lucide-react";
import { getMe } from "../api/auth";
import RankingTool from "../components/RankingTool";
import ScoreConverter from "../components/ScoreConverter";

const examLinks = [
  { name: "托福报名", url: "https://www.ets.org/toefl", accent: "#2c4a8a", badge: "TOEFL" },
  { name: "雅思报名", url: "https://www.ielts.org", accent: "#10b981", badge: "IELTS" },
  { name: "GRE Academic 报名", url: "https://www.ets.org/gre", accent: "#f59e0b", badge: "GRE A" },
  { name: "GRE General Test 报名", url: "https://www.ets.org/gre", accent: "#8b5cf6", badge: "GRE G" },
  { name: "LanguageCert Academic 报名", url: "https://www.languagecert.org", accent: "#ec4899", badge: "LCA" },
];

const sectionItems = [
  { key: "hero", label: "首页介绍", sectionId: "home-hero", icon: Home },
  { key: "tools", label: "快捷小工具", sectionId: "home-tools", icon: Calculator },
  { key: "exam", label: "考试报名", sectionId: "home-exams", icon: BookOpen },
  { key: "ranking", label: "大学排名", sectionId: "home-ranking", icon: TrendingUp },
];

function SectionNavButton({ item, isActive, onClick }) {
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
      <span className="home-nav-button-label">{item.label}</span>
      {isActive ? (
        <motion.div
          layoutId="home-section-indicator"
          className="home-nav-indicator"
          transition={{ type: "spring", stiffness: 380, damping: 32 }}
        />
      ) : null}
    </motion.button>
  );
}

function TopActionButton({ label, children }) {
  return (
    <motion.button
      type="button"
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
      className="home-top-icon-button"
      aria-label={label}
    >
      {children}
    </motion.button>
  );
}

function BrandBlock() {
  return (
    <div className="home-brand">
      <div className="home-brand-mark">录</div>
      <div className="home-brand-text">录途 LutoolBox</div>
    </div>
  );
}

function BellIcon() {
  return <Bell size={20} strokeWidth={2} color="#4b5563" />;
}

function SettingsIcon() {
  return <Settings size={20} strokeWidth={2} color="#4b5563" />;
}

function ArrowRightIcon() {
  return <ArrowRight size={20} strokeWidth={2.2} />;
}

export default function HomePage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [activeSection, setActiveSection] = useState("hero");

  useEffect(() => {
    let cancelled = false;

    async function fetchProfile() {
      try {
        const response = await getMe();
        if (!cancelled) {
          setProfile(response.data);
        }
      } catch (error) {
        if (error?.response?.status === 401) {
          localStorage.removeItem("access_token");
          navigate("/login", { replace: true });
        }
      }
    }

    fetchProfile();

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  function getDisplayName() {
    return profile?.nickname || profile?.mobile || "用户";
  }

  function getDisplaySubName() {
    return profile?.mobile || "已登录用户";
  }

  function getDisplayInitial() {
    const source = (profile?.nickname || profile?.mobile || "U").trim();
    return source.charAt(0).toUpperCase();
  }

  function scrollToSection(sectionId, key) {
    setActiveSection(key);
    const offset = 64;
    const element = document.getElementById(sectionId);
    if (!element) return;
    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - offset;
    window.scrollTo({
      top: offsetPosition,
      behavior: "smooth",
    });
  }

  return (
    <div className="home-shell">
      <motion.header
        className="home-topbar"
        initial={{ y: -20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="home-topbar-inner">
          <BrandBlock />

          <nav className="home-top-nav">
            {sectionItems.map((item) => (
              <SectionNavButton
                key={item.key}
                item={item}
                isActive={activeSection === item.key}
                onClick={() => scrollToSection(item.sectionId, item.key)}
              />
            ))}
          </nav>

          <div className="home-top-actions">
            <TopActionButton label="提醒">
              <div className="home-top-icon-wrap">
                <BellIcon />
                <span className="home-top-icon-dot" />
              </div>
            </TopActionButton>
            <TopActionButton label="设置">
              <SettingsIcon />
            </TopActionButton>
            <div className="home-top-divider" />

            {profile ? (
              <motion.button
                type="button"
                onClick={() => navigate("/profile")}
                className="home-user-entry"
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
              >
                <div className="home-user-copy">
                  <p className="home-user-name">{getDisplayName()}</p>
                  <p className="home-user-subname">{getDisplaySubName()}</p>
                </div>
                {profile.avatar_url ? (
                  <img src={profile.avatar_url} alt="用户头像" className="home-user-avatar" />
                ) : (
                  <div className="home-user-avatar home-user-avatar-fallback">{getDisplayInitial()}</div>
                )}
              </motion.button>
            ) : null}
          </div>
        </div>
      </motion.header>

      <main className="home-main">
        <motion.section
          className="home-hero"
          id="home-hero"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6 }}
        >
          <div className="home-hero-bg" />
          <div className="home-hero-orb home-hero-orb-left" />
          <div className="home-hero-orb home-hero-orb-right" />

          <div className="home-hero-content">
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.2 }}
            >
              <h1 className="home-hero-title">
                欢迎使用路途
                <br />
                <span className="home-hero-title-accent">你的留学申请工具包</span>
              </h1>
            </motion.div>

            <motion.p
              className="home-hero-subtitle"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.4 }}
            >
              备考、择校、申请、查分，一站式搞定
            </motion.p>

            <motion.div
              className="home-hero-actions"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.6 }}
            >
              <motion.button
                type="button"
                onClick={() => scrollToSection("home-tools", "tools")}
                className="home-primary-button"
                whileHover={{ scale: 1.05, boxShadow: "0 20px 40px rgba(59,130,246,0.35)" }}
                whileTap={{ scale: 0.98 }}
              >
                Get Started
                <ArrowRightIcon />
              </motion.button>

              <motion.button
                type="button"
                onClick={() => scrollToSection("home-ranking", "ranking")}
                className="home-secondary-button"
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.98 }}
              >
                了解更多
              </motion.button>
            </motion.div>

            <motion.div
              className="home-hero-stats"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, delay: 0.8 }}
            >
              <div className="home-stat">
                <div className="home-stat-number">10K+</div>
                <div className="home-stat-label">注册用户</div>
              </div>
              <div className="home-stat">
                <div className="home-stat-number">500+</div>
                <div className="home-stat-label">合作院校</div>
              </div>
              <div className="home-stat">
                <div className="home-stat-number">98%</div>
                <div className="home-stat-label">满意度</div>
              </div>
            </motion.div>
          </div>
        </motion.section>

        <div className="home-content-wrap">
          <motion.section
            className="home-section"
            id="home-tools"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.5 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><Calculator size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">快捷小工具</h2>
                <p className="home-section-subtitle">方便高效地处理留学成绩换算和基础申请判断。</p>
              </div>
            </div>

            <div className="home-card">
              {/* 中文注释：保留你现有项目里的分数换算组件，只替换它所在卡片的布局。 */}
              <ScoreConverter />
            </div>
          </motion.section>

          <motion.section
            className="home-section"
            id="home-exams"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.6 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><Link2 size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">考试报名链接</h2>
                <p className="home-section-subtitle">快速跳转到主流留学考试官网报名入口。</p>
              </div>
            </div>

            <div className="home-exam-grid">
              {examLinks.map((link, index) => (
                <motion.a
                  key={index}
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="home-exam-card"
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.3, delay: 0.7 + index * 0.05 }}
                  whileHover={{ y: -4, boxShadow: "0 8px 20px rgba(0,0,0,0.1)" }}
                >
                  <div className="home-exam-card-head">
                    <div className="home-exam-badge" style={{ background: `${link.accent}15`, color: link.accent }}>
                      <GraduationCap size={20} strokeWidth={2.1} />
                    </div>
                    <span className="home-exam-arrow">↗</span>
                  </div>
                  <h3 className="home-exam-title">{link.name}</h3>
                </motion.a>
              ))}
            </div>
          </motion.section>

          <motion.section
            className="home-section"
            id="home-ranking"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.8 }}
          >
            <div className="home-section-head">
              <div className="home-section-icon"><TrendingUp size={24} strokeWidth={2.1} /></div>
              <div>
                <h2 className="home-section-title">大学排名查询</h2>
                <p className="home-section-subtitle">根据地区、QS 区间和学校名称快速筛选院校。</p>
              </div>
            </div>

            <div className="home-card">
              {/* 中文注释：继续复用你现有的排名组件，避免重写现有数据逻辑。 */}
              <RankingTool />
            </div>
          </motion.section>
        </div>
      </main>
    </div>
  );
}
