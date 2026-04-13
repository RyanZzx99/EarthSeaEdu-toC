import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { ArrowRight, Clock3, ListTodo, Trophy } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMockExamProgresses } from "../api/mockexam";
import MockExamModeHeader from "../components/MockExamModeHeader";
import "../mockexam/mockexam.css";

const quickEntries = [
  {
    key: "recent-activity",
    title: "继续上次练习",
    description: "回到最近一次点击“保存并退出”的未完成试卷或组合试卷。",
    icon: Clock3,
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
    key: "wrong-book",
    title: "我的错题",
    description: "集中复盘最近错题，优先补强高频失分题型。",
    icon: ListTodo,
    palette: {
      start: "#edf5f8",
      end: "#dfeaf1",
      border: "rgba(84, 117, 141, 0.16)",
      badge: "rgba(107, 143, 168, 0.14)",
      icon: "#4f6f87",
      glow: "rgba(153, 184, 205, 0.28)",
    },
  },
  {
    key: "submission-history",
    title: "练习记录",
    description: "查看最近交卷成绩、提交记录和成绩回看入口。",
    icon: Trophy,
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

function getApiError(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string" && detail.trim()) {
    return detail;
  }
  if (Array.isArray(detail) && detail.length) {
    const first = detail[0];
    if (typeof first === "string" && first.trim()) {
      return first;
    }
    if (first?.msg) {
      return String(first.msg);
    }
  }
  if (detail?.msg) {
    return String(detail.msg);
  }
  return fallback;
}

export default function MockExamPage() {
  const navigate = useNavigate();
  const [latestProgress, setLatestProgress] = useState(null);
  const [loadingProgress, setLoadingProgress] = useState(true);
  const [progressMessage, setProgressMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function loadLatestProgress() {
      try {
        const response = await getMockExamProgresses({ limit: 1 });
        if (!active) {
          return;
        }
        setLatestProgress(response.data?.items?.[0] || null);
        setProgressMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setProgressMessage(getApiError(error, "未完成练习加载失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoadingProgress(false);
        }
      }
    }

    void loadLatestProgress();

    return () => {
      active = false;
    };
  }, []);

  function handleQuickEntry(key) {
    if (key === "recent-activity") {
      if (latestProgress?.progress_id) {
        navigate(`/mockexam/run/progress/${latestProgress.progress_id}`);
        return;
      }
      navigate("/mockexam/practice#recent-activity");
      return;
    }

    if (key === "wrong-book") {
      navigate("/mockexam/practice#wrong-book");
      return;
    }

    navigate("/mockexam/practice#submission-history");
  }

  return (
    <div className="home-shell mockexam-home-shell">
      <MockExamModeHeader activeMode="exam" />

      <main className="home-main mockexam-home-main">
        <div className="home-content-wrap mockexam-home-content mockexam-home-content-flat">
          <section className="mockexam-entry-section">
            <div className="home-section-head">
              <div className="home-section-icon">
                <Trophy size={24} strokeWidth={2.1} />
              </div>
              <div>
                <h2 className="home-section-title">模拟考试入口</h2>
                <p className="home-section-subtitle">当前可用能力统一归类到练习模式，模考模式先保留界面占位。</p>
              </div>
            </div>

            <div className="mockexam-entry-grid">
              <article className="home-card mockexam-entry-card mockexam-entry-card-muted">
                <div className="mockexam-entry-card-copy">
                  <h3>模考模式 - 待更新</h3>
                  <p>完整仿真考试流程、统一计时和正式结果页会在这里接入。</p>
                </div>

                <ul className="mockexam-entry-card-points">
                  <li>完整考试态界面</li>
                  <li>全程计时与阶段控制</li>
                  <li>正式模考结果页</li>
                </ul>

                <div className="mockexam-entry-card-actions">
                  <button type="button" className="mockexam-home-secondary-button" disabled>
                    模考模式待更新
                  </button>
                </div>
              </article>

              <article className="home-card mockexam-entry-card mockexam-entry-card-highlight">
                <div className="mockexam-entry-card-copy">
                  <h3>练习模式</h3>
                  <p>当前所有已上线能力都在这里，包括单张试卷、组合试卷、继续作答、错题和成绩回看。</p>
                </div>

                <ul className="mockexam-entry-card-points">
                  <li>单张试卷与组合试卷练习</li>
                  <li>保存并退出，后续继续作答</li>
                  <li>错题本、收藏夹、练习记录</li>
                </ul>

                <div className="mockexam-entry-card-actions">
                  <button
                    type="button"
                    className="home-primary-button mockexam-home-primary-button"
                    onClick={() => navigate("/mockexam/practice")}
                  >
                    进入练习模式
                  </button>
                </div>
              </article>
            </div>
          </section>

          <section className="mockexam-quick-section">
            <div className="home-section-head">
              <div className="home-section-icon">
                <ArrowRight size={24} strokeWidth={2.1} />
              </div>
              <div>
                <h2 className="home-section-title">快捷入口</h2>
                <p className="home-section-subtitle">直接进入最近练习、错题和练习记录，不再使用按钮堆叠。</p>
              </div>
            </div>

            {progressMessage ? <div className="mockexam-inline-note">{progressMessage}</div> : null}

            <div className="home-shortcut-strip mockexam-shortcut-strip">
              <div className="home-shortcut-track mockexam-shortcut-track">
                {quickEntries.map((item, index) => {
                  const Icon = item.icon;
                  const primaryDescription =
                    item.key === "recent-activity" && latestProgress?.title
                      ? `继续 ${latestProgress.title}`
                      : item.description;
                  const secondaryDescription =
                    item.key === "recent-activity"
                      ? loadingProgress
                        ? "正在读取最近一次保存记录..."
                        : latestProgress
                          ? `已完成 ${latestProgress.answered_count || 0}/${latestProgress.total_questions || 0}`
                          : "当前没有未完成练习"
                      : item.description;

                  return (
                    <motion.button
                      key={item.key}
                      type="button"
                      className="home-shortcut-card"
                      initial={{ opacity: 0, y: 12 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.24, delay: 0.06 * index }}
                      onClick={() => handleQuickEntry(item.key)}
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
                        <p>{primaryDescription}</p>
                        <div className="mockexam-shortcut-meta">{secondaryDescription}</div>
                      </div>
                    </motion.button>
                  );
                })}
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
