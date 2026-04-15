import React from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router-dom";
import MockExamModeHeader from "../components/MockExamModeHeader";
import MockExamRecentActivitySection from "../components/MockExamRecentActivitySection";
import "../mockexam/mockexam.css";

export default function MockExamPracticePage() {
  const navigate = useNavigate();

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
                <p>按题型、题量快速刷题</p>
              </div>

              <div className="mockexam-practice-source-tags">
                <span className="active">阅读</span>
                <span>听力</span>
                <span>题型分类</span>
                <span>灵活练习</span>
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

          <MockExamRecentActivitySection />
        </div>
      </main>
    </div>
  );
}
