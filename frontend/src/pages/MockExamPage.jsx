import React from "react";
import { motion } from "motion/react";
import { ArrowLeft, FlaskConical } from "lucide-react";
import { useNavigate } from "react-router-dom";
import MockExamSubmissionHistoryPanel from "../components/MockExamSubmissionHistoryPanel";
import MockExamWorkspace from "../components/MockExamWorkspace";
import "../mockexam/mockexam.css";

export default function MockExamPage() {
  const navigate = useNavigate();

  return (
    <div className="mockexam-page">
      <div className="mockexam-shell">
        <div className="mockexam-topbar">
          <div className="mockexam-topcopy">
            <button type="button" className="mockexam-back" onClick={() => navigate("/")}>
              <ArrowLeft size={16} strokeWidth={2.2} />
              返回首页
            </button>
            <h1>模拟考试</h1>
            <p>选择题库或试卷直接进入模考，组合试卷管理已移至教师端。</p>
          </div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
        >
          <div className="mockexam-panel mockexam-entry-panel">
            <div className="mockexam-panel-head">
              <div>
                <h2>测试版入口</h2>
                <p>如果你要验证新题库方案下的结构化试卷，可以从这里进入 `模拟考试-测试`。</p>
              </div>
              <span className="mockexam-panel-badge">
                <FlaskConical size={22} strokeWidth={2.1} />
              </span>
            </div>

            <div className="mockexam-entry-actions">
              <button
                type="button"
                className="mockexam-secondary-button"
                onClick={() => navigate("/mockexam-beta")}
              >
                进入模拟考试-测试
              </button>
            </div>
          </div>

          <div className="mockexam-page-spacer" />
          <MockExamWorkspace showExamSetManagement={false} showQuickPractice />
          <div className="mockexam-page-spacer" />
          <MockExamSubmissionHistoryPanel />
        </motion.div>
      </div>
    </div>
  );
}
