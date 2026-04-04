import React, { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { useParams } from "react-router-dom";
import { getMe } from "../api/auth";
import { getMockExamExamSet, getMockExamQuestionBank } from "../api/mockexam";
import { buildMockExamIeltsSrcDoc } from "../mockexam/mockexamFrame";
import { loadQuickPracticePayload } from "../mockexam/mockexamStorage";
import "../mockexam/mockexam.css";

function toUserDisplayName(profile) {
  return profile?.nickname || profile?.mobile || "学生用户";
}

export default function MockExamRunnerPage() {
  const { sourceType, sourceId } = useParams();
  const [frameSrcDoc, setFrameSrcDoc] = useState("");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function bootstrapRunner() {
      try {
        const profileResponse = await getMe();
        if (!active) {
          return;
        }

        const profile = profileResponse.data || null;
        let examCategory = "";
        let title = "";
        let payload = null;
        let submitUrl = "";
        let inlinePayload = null;
        let currentSourceType = sourceType || "question-bank";

        if (currentSourceType === "question-bank") {
          const questionBankResponse = await getMockExamQuestionBank(sourceId);
          if (!active) {
            return;
          }
          const questionBank = questionBankResponse.data || {};
          examCategory = questionBank.exam_category || "";
          title = questionBank.file_name || "";
          payload = questionBank.payload;
          submitUrl = `/api/v1/mockexam/question-banks/${questionBank.id}/submit`;
        } else if (currentSourceType === "exam-set") {
          const examSetResponse = await getMockExamExamSet(sourceId);
          if (!active) {
            return;
          }
          const examSet = examSetResponse.data || {};
          examCategory = examSet.exam_category || "";
          title = examSet.name || "";
          payload = examSet.payload;
          submitUrl = `/api/v1/mockexam/exam-sets/${examSet.exam_sets_id}/submit`;
        } else if (currentSourceType === "quick-practice") {
          const quickPractice = loadQuickPracticePayload(sourceId);
          if (!quickPractice) {
            setMessage("随堂小练数据已失效，请返回上一页重新生成。");
            return;
          }
          examCategory = quickPractice.exam_category || "";
          title = quickPractice.label || "随堂小练";
          payload = quickPractice.payload;
          submitUrl = "/api/v1/mockexam/evaluate";
          inlinePayload = quickPractice.payload;
        } else {
          setMessage("不支持的模考来源。");
          return;
        }

        setFrameSrcDoc(
          buildMockExamIeltsSrcDoc({
            email: toUserDisplayName(profile),
            sourceType: currentSourceType,
            sourceId,
            questionBankId: currentSourceType === "question-bank" ? sourceId : null,
            fileName: title,
            payload,
            submitUrl,
            inlinePayload,
          })
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(error?.response?.data?.detail || "模考试卷加载失败，请稍后重试。");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void bootstrapRunner();

    return () => {
      active = false;
    };
  }, [sourceId, sourceType]);

  if (loading) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">
          <LoaderCircle size={18} strokeWidth={2.2} className="spin" />
          <span>正在载入模考试卷</span>
        </div>
      </div>
    );
  }

  if (!frameSrcDoc) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">{message || "暂无可显示的模考试卷"}</div>
      </div>
    );
  }

  return (
    <div className="mockexam-runner">
      <iframe title="模拟考试" className="mockexam-runner-frame" srcDoc={frameSrcDoc} />
    </div>
  );
}
