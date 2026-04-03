import React, { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { useParams } from "react-router-dom";
import { getMe } from "../api/auth";
import { getMockExamQuestionBank } from "../api/mockexam";
import { buildMockExamIeltsSrcDoc } from "../mockexam/mockexamFrame";
import "../mockexam/mockexam.css";

function toUserDisplayName(profile) {
  return profile?.nickname || profile?.mobile || "学生用户";
}

export default function MockExamRunnerPage() {
  const { questionBankId } = useParams();
  const [frameSrcDoc, setFrameSrcDoc] = useState("");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function bootstrapRunner() {
      try {
        const [profileResponse, questionBankResponse] = await Promise.all([
          getMe(),
          getMockExamQuestionBank(questionBankId),
        ]);
        if (!active) {
          return;
        }

        const profile = profileResponse.data || null;
        const questionBank = questionBankResponse.data || {};

        if (questionBank.exam_category !== "IELTS") {
          setMessage(`${questionBank.exam_category || "该考试"} 模考正在等待更新`);
          return;
        }

        setFrameSrcDoc(
          buildMockExamIeltsSrcDoc({
            email: toUserDisplayName(profile),
            questionBankId: questionBank.id,
            fileName: questionBank.file_name,
            payload: questionBank.payload,
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
  }, [questionBankId]);

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
      <iframe
        title="模拟考试"
        className="mockexam-runner-frame"
        srcDoc={frameSrcDoc}
      />
    </div>
  );
}
