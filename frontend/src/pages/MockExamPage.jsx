import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { CheckCircle2, FileText, Play, Target, Timer, Zap } from "lucide-react";
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

const QUICK_ACTIONS = [
  { key: "continue", label: "继续上次练习", icon: Play },
  { key: "wrong", label: "我的错题", icon: Target },
  { key: "history", label: "练习记录", icon: FileText },
];

export default function MockExamPage() {
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
          message: getApiError(error, "模式页数据加载失败，请稍后重试。"),
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

  function handleQuickAction(key) {
    if (key === "continue") {
      if (summary.latestProgress?.progress_id) {
        navigate(`/mockexam/run/progress/${summary.latestProgress.progress_id}`);
        return;
      }
      navigate("/mockexam/practice");
      return;
    }

    if (key === "wrong") {
      if (summary.latestWrong?.exam_question_id) {
        navigate(`/mockexam/questions/${summary.latestWrong.exam_question_id}`);
        return;
      }
      navigate("/mockexam/practice/topic");
      return;
    }

    if (summary.latestSubmission?.submission_id) {
      navigate(`/mockexam/results/${summary.latestSubmission.submission_id}`);
      return;
    }

    navigate("/mockexam/practice/test");
  }

  return (
    <div className="home-shell mockexam-mode-page">
      <MockExamModeHeader activeMode="exam" />

      <main className="mockexam-mode-main">
        <div className="mockexam-mode-container mockexam-selection-layout">
          <motion.section
            className="mockexam-screen-title"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.32 }}
          >
            <h1>选择你的训练方式</h1>
            <p>模考用于评估能力，练习用于查漏补缺和提分</p>
          </motion.section>

          <section className="mockexam-selection-grid">
            <motion.article
              className="mockexam-selection-card"
              initial={{ opacity: 0, x: -18 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.34, delay: 0.06 }}
            >
              <div className="mockexam-selection-copy">
                <h2>模考模式</h2>
                <p>完全模拟真实 IELTS 考试界面</p>
              </div>

              <div className="mockexam-focus-panel mock">
                <div className="mockexam-focus-icon muted">
                  <Timer size={20} strokeWidth={2.2} />
                </div>
                <h3>严格计时</h3>
                <p>不可查看解析</p>
                <p>沉浸式模拟</p>
              </div>

              <ul className="mockexam-selection-list">
                <li>
                  <CheckCircle2 size={15} />
                  <span>完全还原考试 UI</span>
                </li>
                <li>
                  <CheckCircle2 size={15} />
                  <span>分 section 严格计时</span>
                </li>
                <li>
                  <CheckCircle2 size={15} />
                  <span>结果页统一</span>
                </li>
              </ul>

              <button
                type="button"
                className="mockexam-ghost-button"
                disabled
              >
                进入模考
              </button>
            </motion.article>

            <motion.article
              className="mockexam-selection-card practice"
              initial={{ opacity: 0, x: 18 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.34, delay: 0.12 }}
            >
              <div className="mockexam-selection-copy">
                <h2>练习模式</h2>
                <p>支持暂停、纠错、翻译与答案解析</p>
              </div>

              <div className="mockexam-focus-panel practice">
                <div className="mockexam-focus-icon practice">
                  <Zap size={20} strokeWidth={2.2} />
                </div>
                <h3>灵活练习</h3>
                <p>随时暂停</p>
                <p>即时纠错</p>
              </div>

              <ul className="mockexam-selection-list">
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>专项练习 + 试卷练习</span>
                </li>
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>纠错模式可显示翻译/答案/解析</span>
                </li>
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>支持继续</span>
                </li>
              </ul>

              <button
                type="button"
                className="mockexam-primary-button"
                onClick={() => navigate("/mockexam/practice")}
              >
                进入练习
              </button>
            </motion.article>
          </section>

          <motion.section
            className="mockexam-shortcut-panel"
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.32, delay: 0.18 }}
          >
            <div className="mockexam-shortcut-head">
              <h2>快捷入口</h2>
            </div>

            <div className="mockexam-shortcut-actions">
              {QUICK_ACTIONS.map((item) => {
                const Icon = item.icon;
                return (
                <button
                  key={item.key}
                  type="button"
                  className="mockexam-shortcut-button"
                  onClick={() => handleQuickAction(item.key)}
                >
                  <span className="mockexam-shortcut-icon">
                    <Icon size={16} strokeWidth={2.2} />
                  </span>
                  <span>{item.label}</span>
                </button>
                );
              })}
            </div>

            {summary.loading ? (
              <div className="mockexam-inline-message">
                <InlineLoading message="正在准备最近练习状态" size="sm" />
              </div>
            ) : null}
            {summary.message ? <p className="mockexam-inline-message">{summary.message}</p> : null}
          </motion.section>
        </div>
      </main>
    </div>
  );
}
