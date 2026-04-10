import React from "react";
import { motion } from "motion/react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import MockExamSubmissionHistoryPanel from "../components/MockExamSubmissionHistoryPanel";
import MockExamBetaWorkspace from "../components/MockExamBetaWorkspace";
import "../mockexam/mockexam.css";

export default function MockExamBetaPage() {
  const navigate = useNavigate();

  return (
    <div className="mockexam-page">
      <div className="mockexam-shell">
        <div className="mockexam-topbar">
          <div className="mockexam-topcopy">
            <button type="button" className="mockexam-back" onClick={() => navigate("/mockexam")}>
              <ArrowLeft size={16} strokeWidth={2.2} />
              返回模拟考试
            </button>
            <h1>模拟考试-测试</h1>
            <p>这条链路直接读取新方案下的结构化 IELTS 试卷，用来验证入库结果和 runner 兼容性。</p>
          </div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
        >
          <MockExamBetaWorkspace />
          <div className="mockexam-page-spacer" />
          <MockExamSubmissionHistoryPanel
            title="测试版成绩回看"
            description="这里保留结构化试卷链路下的交卷记录，可直接回看结果和作答。"
            sourceType="paper-beta"
            emptyText="暂无测试版成绩记录"
          />
        </motion.div>
      </div>
    </div>
  );
}
