import React from "react";
import { motion } from "motion/react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
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
          <MockExamWorkspace showExamSetManagement={false} showQuickPractice />
        </motion.div>
      </div>
    </div>
  );
}
