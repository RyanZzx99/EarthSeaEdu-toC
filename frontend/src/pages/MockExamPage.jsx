import React from "react";
import { motion } from "motion/react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import MockExamWorkspace from "../components/MockExamWorkspace";
import MockExamFavoritesPanel from "../components/MockExamFavoritesPanel";
import MockExamRecentActivityPanel from "../components/MockExamRecentActivityPanel";
import MockExamSubmissionHistoryPanel from "../components/MockExamSubmissionHistoryPanel";
import MockExamWrongBookPanel from "../components/MockExamWrongBookPanel";
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
            <p>支持单张试卷和教师组合试卷，保留继续作答、收藏、错题本和成绩回看。</p>
          </div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
        >
          <MockExamWorkspace />
          <div className="mockexam-page-spacer" />
          <MockExamRecentActivityPanel />
          <div className="mockexam-page-spacer" />
          <MockExamFavoritesPanel />
          <div className="mockexam-page-spacer" />
          <MockExamWrongBookPanel />
          <div className="mockexam-page-spacer" />
          <MockExamSubmissionHistoryPanel />
        </motion.div>
      </div>
    </div>
  );
}
