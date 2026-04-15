import React from "react";
import { motion } from "motion/react";
import { CheckCircle2, Timer, Zap } from "lucide-react";
import { useNavigate } from "react-router-dom";
import MockExamModeHeader from "../components/MockExamModeHeader";
import MockExamRecentActivitySection from "../components/MockExamRecentActivitySection";
import "../mockexam/mockexam.css";

export default function MockExamPage() {
  const navigate = useNavigate();

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
            <h1>选择你的练习方式</h1>
            <p>模考模式入口先保留界面，当前已接入功能统一归类到练习模式。</p>
          </motion.section>

          <section className="mockexam-selection-grid">
            <motion.article
              className="mockexam-selection-card"
              initial={{ opacity: 0, x: -18 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.34, delay: 0.06 }}
            >
              <div className="mockexam-selection-copy">
                <h2>模考模式-待更新</h2>
                <p>完全模拟真实 IELTS 考试流程，后续按正式模考链路接入。</p>
              </div>

              <div className="mockexam-focus-panel mock">
                <div className="mockexam-focus-icon muted">
                  <Timer size={20} strokeWidth={2.2} />
                </div>
                <h3>严格计时</h3>
                <p>不可查看解析</p>
                <p>沉浸式作答</p>
              </div>

              <ul className="mockexam-selection-list">
                <li>
                  <CheckCircle2 size={15} />
                  <span>完整还原考试界面</span>
                </li>
                <li>
                  <CheckCircle2 size={15} />
                  <span>按 section 独立计时</span>
                </li>
                <li>
                  <CheckCircle2 size={15} />
                  <span>统一生成考试结果</span>
                </li>
              </ul>

              <button type="button" className="mockexam-ghost-button" disabled>
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
                <p>已接入当前所有试卷、错题、收藏、继续练习等完整功能。</p>
              </div>

              <div className="mockexam-focus-panel practice">
                <div className="mockexam-focus-icon practice">
                  <Zap size={20} strokeWidth={2.2} />
                </div>
                <h3>灵活练习</h3>
                <p>支持保存退出</p>
                <p>支持错题与收藏</p>
              </div>

              <ul className="mockexam-selection-list">
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>专项练习 + 试卷练习</span>
                </li>
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>查看结果、回看、错题复盘</span>
                </li>
                <li className="practice">
                  <CheckCircle2 size={15} />
                  <span>支持继续未完成练习</span>
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

          <MockExamRecentActivitySection />
        </div>
      </main>
    </div>
  );
}
