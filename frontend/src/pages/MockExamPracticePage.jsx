import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { AlertCircle, BarChart3, ChevronRight, Play } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamProgresses,
  getMockExamSubmissions,
  getMockExamWrongQuestions,
} from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { getApiError } from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";

function buildContinueCopy(progress) {
  if (!progress) {
    return ["暂无未完成练习", "从专项练习或试卷练习开始"];
  }

  return [
    progress.title || "最近一次练习",
    `已完成 ${progress.answered_count || 0}/${progress.total_questions || 0}`,
  ];
}

function buildWrongCopy(wrong) {
  if (!wrong) {
    return ["最近新增 0 题", "当前还没有错题记录"];
  }

  return [
    `最近新增 ${wrong.wrong_count || 1} 题`,
    wrong.preview_text || `题号 Q${wrong.question_no || "--"}`,
  ];
}

function buildHistoryCopy(submission) {
  if (!submission) {
    return ["本周练习 0 次", "平均正确率 --"];
  }

  return [
    submission.title || "最近一次练习",
    typeof submission.score_percent === "number"
      ? `平均正确率 ${submission.score_percent}%`
      : "已生成练习记录",
  ];
}

export default function MockExamPracticePage() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState({
    loading: true,
    message: "",
    latestProgress: null,
    latestWrong: null,
    latestSubmission: null,
  });

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        const [progressResponse, wrongResponse, submissionResponse] = await Promise.all([
          getMockExamProgresses({ limit: 1 }),
          getMockExamWrongQuestions({ limit: 1 }),
          getMockExamSubmissions({ limit: 1 }),
        ]);

        if (!active) {
          return;
        }

        setSummary({
          loading: false,
          message: "",
          latestProgress: progressResponse.data?.items?.[0] || null,
          latestWrong: wrongResponse.data?.items?.[0] || null,
          latestSubmission: submissionResponse.data?.items?.[0] || null,
        });
      } catch (error) {
        if (!active) {
          return;
        }

        setSummary({
          loading: false,
          message: getApiError(error, "练习模式首页加载失败，请稍后重试。"),
          latestProgress: null,
          latestWrong: null,
          latestSubmission: null,
        });
      }
    }

    void bootstrap();

    return () => {
      active = false;
    };
  }, []);

  const recentCards = [
    {
      key: "continue",
      title: "继续上次练习",
      lines: buildContinueCopy(summary.latestProgress),
      icon: Play,
      tone: "indigo",
      onClick: () => {
        if (summary.latestProgress?.progress_id) {
          navigate(`/mockexam/run/progress/${summary.latestProgress.progress_id}`);
          return;
        }
        navigate("/mockexam/practice/topic");
      },
    },
    {
      key: "wrong",
      title: "我的错题",
      lines: buildWrongCopy(summary.latestWrong),
      icon: AlertCircle,
      tone: "rose",
      onClick: () => {
        if (summary.latestWrong?.exam_question_id) {
          navigate(`/mockexam/questions/${summary.latestWrong.exam_question_id}`);
          return;
        }
        navigate("/mockexam/practice/topic");
      },
    },
    {
      key: "history",
      title: "练习记录",
      lines: buildHistoryCopy(summary.latestSubmission),
      icon: BarChart3,
      tone: "emerald",
      onClick: () => {
        if (summary.latestSubmission?.submission_id) {
          navigate(`/mockexam/results/${summary.latestSubmission.submission_id}`);
          return;
        }
        navigate("/mockexam/practice/test");
      },
    },
  ];

  return (
    <div className="home-shell mockexam-mode-page">
      <MockExamModeHeader activeMode="practice" />

      <main className="mockexam-practice-main">
        <div className="mockexam-practice-container">
          <motion.div
            className="mockexam-practice-title"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1>练习模式</h1>
            <p>更适合提分，而不是单纯模拟</p>
          </motion.div>

          <div className="mockexam-practice-source-grid">
            <motion.article
              className="mockexam-practice-source-card"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="mockexam-practice-source-copy">
                <h2>专项练习</h2>
                <p>按题型、难度、题量快速刷题</p>
              </div>

              <div className="mockexam-practice-source-tags">
                <span className="active">阅读</span>
                <span>听力</span>
                <span>题型分类</span>
                <span>难度可选</span>
              </div>

              <button
                type="button"
                className="mockexam-practice-source-primary"
                onClick={() => navigate("/mockexam/practice/topic")}
              >
                开始专项练习
              </button>
            </motion.article>

            <motion.article
              className="mockexam-practice-source-card"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
            >
              <div className="mockexam-practice-source-copy">
                <h2>试卷练习</h2>
                <p>按整套试卷练习，可暂停、可纠错</p>
              </div>

              <div className="mockexam-practice-source-tags">
                <span className="active">整卷结构</span>
                <span>保存进度</span>
                <span>答案解析</span>
                <span>错题复盘</span>
              </div>

              <button
                type="button"
                className="mockexam-practice-source-primary"
                onClick={() => navigate("/mockexam/practice/test")}
              >
                开始试卷练习
              </button>
            </motion.article>
          </div>

          <motion.section
            className="mockexam-practice-recent-panel"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
          >
            <h3>最近活动</h3>

            <div className="mockexam-practice-recent-grid">
              {recentCards.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.key}
                    type="button"
                    className={`mockexam-practice-recent-card tone-${item.tone}`}
                    onClick={item.onClick}
                  >
                    <div className="mockexam-practice-recent-head">
                      <div className="mockexam-practice-recent-icon">
                        <Icon size={18} />
                      </div>
                      <ChevronRight size={16} className="mockexam-practice-recent-arrow" />
                    </div>

                    <h4>{item.title}</h4>

                    {summary.loading ? (
                      <InlineLoading message="正在准备最近练习状态" size="sm" />
                    ) : (
                      item.lines.map((line, index) => (
                        <p
                          key={`${item.key}-${line}`}
                          className={
                            index === item.lines.length - 1
                              ? "mockexam-practice-recent-meta"
                              : undefined
                          }
                        >
                          {line}
                        </p>
                      ))
                    )}
                  </button>
                );
              })}
            </div>

            {summary.message ? <p className="mockexam-inline-message">{summary.message}</p> : null}
          </motion.section>
        </div>
      </main>
    </div>
  );
}
