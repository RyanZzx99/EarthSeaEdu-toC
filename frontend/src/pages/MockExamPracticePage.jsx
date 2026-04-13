import React, { useEffect, useMemo, useState } from "react";
import { ChevronRight, Clock3, Heart, ListTodo, LoaderCircle, Trophy } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import MockExamFavoritesPanel from "../components/MockExamFavoritesPanel";
import MockExamModeHeader from "../components/MockExamModeHeader";
import MockExamRecentActivityPanel from "../components/MockExamRecentActivityPanel";
import MockExamSubmissionHistoryPanel from "../components/MockExamSubmissionHistoryPanel";
import MockExamWorkspace from "../components/MockExamWorkspace";
import MockExamWrongBookPanel from "../components/MockExamWrongBookPanel";
import {
  getMockExamFavorites,
  getMockExamProgresses,
  getMockExamSubmissions,
  getMockExamWrongQuestions,
} from "../api/mockexam";
import "../mockexam/mockexam.css";

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

function scrollToSection(sectionId) {
  const target = document.getElementById(sectionId);
  if (target) {
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

export default function MockExamPracticePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [summary, setSummary] = useState({
    loading: true,
    message: "",
    latestProgress: null,
    wrongItems: [],
    submissionItems: [],
    favoriteItems: [],
  });

  useEffect(() => {
    let active = true;

    async function loadSummary() {
      try {
        const [progressResponse, wrongResponse, submissionResponse, favoriteResponse] = await Promise.all([
          getMockExamProgresses({ limit: 1 }),
          getMockExamWrongQuestions({ limit: 12 }),
          getMockExamSubmissions({ limit: 12 }),
          getMockExamFavorites({ limit: 12 }),
        ]);

        if (!active) {
          return;
        }

        setSummary({
          loading: false,
          message: "",
          latestProgress: progressResponse.data?.items?.[0] || null,
          wrongItems: wrongResponse.data?.items || [],
          submissionItems: submissionResponse.data?.items || [],
          favoriteItems: favoriteResponse.data?.items || [],
        });
      } catch (error) {
        if (!active) {
          return;
        }

        setSummary((previous) => ({
          ...previous,
          loading: false,
          message: getApiError(error, "练习概览加载失败，请稍后重试。"),
        }));
      }
    }

    void loadSummary();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const sectionId = location.hash.replace("#", "").trim();
    if (!sectionId) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      scrollToSection(sectionId);
    }, 120);

    return () => {
      window.clearTimeout(timer);
    };
  }, [location.hash]);

  const averageAccuracy = useMemo(() => {
    const scoredItems = summary.submissionItems.filter((item) => typeof item.score_percent === "number");
    if (!scoredItems.length) {
      return null;
    }
    const total = scoredItems.reduce((result, item) => result + Number(item.score_percent || 0), 0);
    return Math.round(total / scoredItems.length);
  }, [summary.submissionItems]);

  const quickCards = [
    {
      key: "recent-activity",
      title: "继续上次练习",
      subtitle: summary.latestProgress?.title || "暂无未完成练习",
      meta: summary.latestProgress
        ? `已完成 ${summary.latestProgress.answered_count || 0}/${summary.latestProgress.total_questions || 0}`
        : "从单张试卷或组合试卷开始新的练习",
      icon: Clock3,
      onClick: () => {
        if (summary.latestProgress?.progress_id) {
          navigate(`/mockexam/run/progress/${summary.latestProgress.progress_id}`);
          return;
        }
        scrollToSection("recent-activity");
      },
    },
    {
      key: "wrong-book",
      title: "我的错题",
      subtitle: summary.wrongItems.length ? `最近错题 ${summary.wrongItems.length} 题` : "暂无错题记录",
      meta: summary.wrongItems[0]?.preview_text || "错题会按题型和作答记录沉淀到这里",
      icon: ListTodo,
      onClick: () => scrollToSection("wrong-book"),
    },
    {
      key: "submission-history",
      title: "练习记录",
      subtitle: summary.submissionItems.length ? `最近练习 ${summary.submissionItems.length} 次` : "暂无练习记录",
      meta: averageAccuracy == null ? "完成交卷后会在这里展示平均正确率" : `平均正确率 ${averageAccuracy}%`,
      icon: Trophy,
      onClick: () => scrollToSection("submission-history"),
    },
    {
      key: "favorites",
      title: "收藏夹",
      subtitle: summary.favoriteItems.length ? `已收藏 ${summary.favoriteItems.length} 题` : "暂无收藏题目",
      meta: summary.favoriteItems[0]?.question_no
        ? `最近收藏 Q${summary.favoriteItems[0].question_no}`
        : "重要题目和高频回看题可以统一收在这里",
      icon: Heart,
      onClick: () => scrollToSection("favorites"),
    },
  ];

  return (
    <div className="home-shell mockexam-home-shell">
      <MockExamModeHeader activeMode="practice" />

      <main className="home-main mockexam-home-main">
        <div className="home-content-wrap mockexam-home-content mockexam-home-content-flat">
          <section className="mockexam-entry-section">
            <div className="home-section-head">
              <div className="home-section-icon">
                <Clock3 size={24} strokeWidth={2.1} />
              </div>
              <div>
                <h2 className="home-section-title">练习模式</h2>
                <p className="home-section-subtitle">
                  专项练习使用单张试卷，适合拆卷训练。试卷练习使用教师组合好的组合试卷，适合整套训练。
                </p>
              </div>
            </div>

            <div className="mockexam-entry-grid">
              <MockExamWorkspace
                sourceMode="paper"
                title="专项练习"
                description="选择单张试卷，按 Listening 或 Reading 单独训练。"
                tags={["单张试卷", "拆卷练习", "快速补弱"]}
                buttonLabel="开始专项练习"
              />

              <MockExamWorkspace
                sourceMode="paper-set"
                title="试卷练习"
                description="选择教师端组合好的试卷，按整套流程完成训练。"
                tags={["组合试卷", "教师编排", "整套练习"]}
                buttonLabel="开始试卷练习"
                cardClassName="mockexam-home-card-highlight"
              />
            </div>
          </section>

          <section className="mockexam-quick-section">
            <div className="home-section-head">
              <div className="home-section-icon">
                <ListTodo size={24} strokeWidth={2.1} />
              </div>
              <div>
                <h2 className="home-section-title">练习概览</h2>
                <p className="home-section-subtitle">继续练习、错题、成绩回看和收藏都从这里进入。</p>
              </div>
            </div>

            {summary.loading ? (
              <div className="mockexam-inline-note">
                <LoaderCircle size={16} strokeWidth={2.1} className="spin" />
                正在加载练习概览...
              </div>
            ) : null}

            {!summary.loading && summary.message ? <div className="mockexam-inline-note">{summary.message}</div> : null}

            {!summary.loading && !summary.message ? (
              <div className="mockexam-summary-grid">
                {quickCards.map((item) => {
                  const Icon = item.icon;
                  return (
                    <button key={item.key} type="button" className="mockexam-summary-card" onClick={item.onClick}>
                      <span className="mockexam-summary-icon">
                        <Icon size={18} strokeWidth={2.1} />
                      </span>
                      <strong>{item.title}</strong>
                      <span>{item.subtitle}</span>
                      <em>{item.meta}</em>
                      <ChevronRight size={16} strokeWidth={2.1} />
                    </button>
                  );
                })}
              </div>
            ) : null}
          </section>

          <section className="mockexam-panel-stack">
            <div id="recent-activity">
              <MockExamRecentActivityPanel />
            </div>
            <div id="wrong-book">
              <MockExamWrongBookPanel />
            </div>
            <div id="submission-history">
              <MockExamSubmissionHistoryPanel />
            </div>
            <div id="favorites">
              <MockExamFavoritesPanel />
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
