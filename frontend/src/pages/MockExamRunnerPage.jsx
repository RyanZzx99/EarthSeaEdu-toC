import React, { useEffect, useRef, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getMe } from "../api/auth";
import {
  getMockExamBetaPaper,
  getMockExamExamSet,
  getMockExamQuestionBank,
  getMockExamSubmission,
} from "../api/mockexam";
import { buildMockExamIeltsSrcDoc } from "../mockexam/mockexamFrame";
import { loadQuickPracticePayload } from "../mockexam/mockexamStorage";
import "../mockexam/mockexam.css";

function toUserDisplayName(profile) {
  return profile?.nickname || profile?.mobile || "Student";
}

function deriveQuickPracticeExamContent(quickPractice) {
  const contents = Array.from(
    new Set((quickPractice?.picked_items || []).map((item) => item?.exam_content).filter(Boolean))
  );
  return contents.length === 1 ? contents[0] : "";
}

export default function MockExamRunnerPage() {
  const { sourceType, sourceId } = useParams();
  const navigate = useNavigate();
  const frameRef = useRef(null);
  const [frameSrcDoc, setFrameSrcDoc] = useState("");
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    function handleMessage(event) {
      if (!frameRef.current || event.source !== frameRef.current.contentWindow) {
        return;
      }
      const data = event.data || {};
      if (data.type !== "mockexam:submitted" || !data.submission_id) {
        return;
      }
      navigate(`/mockexam/results/${data.submission_id}`);
    }

    window.addEventListener("message", handleMessage);
    return () => {
      window.removeEventListener("message", handleMessage);
    };
  }, [navigate]);

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
        let examContent = "";
        let title = "";
        let payload = null;
        let submitUrl = "";
        let inlinePayload = null;
        let reviewMode = false;
        let initialAnswers = null;
        let initialMarked = null;
        let initialResult = null;
        const currentSourceType = sourceType || "question-bank";

        if (currentSourceType === "question-bank") {
          const questionBankResponse = await getMockExamQuestionBank(sourceId);
          if (!active) {
            return;
          }
          const questionBank = questionBankResponse.data || {};
          examCategory = questionBank.exam_category || "";
          examContent = questionBank.exam_content || "";
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
        } else if (currentSourceType === "paper-beta") {
          const betaPaperResponse = await getMockExamBetaPaper(sourceId);
          if (!active) {
            return;
          }
          const betaPaper = betaPaperResponse.data || {};
          examCategory = betaPaper.exam_category || "";
          examContent = betaPaper.exam_content || "";
          title = betaPaper.paper_name || betaPaper.paper_code || "";
          payload = betaPaper.payload;
          submitUrl = `/api/v1/mockexam/beta/papers/${betaPaper.exam_paper_id}/submit`;
        } else if (currentSourceType === "quick-practice") {
          const quickPractice = loadQuickPracticePayload(sourceId);
          if (!quickPractice) {
            setMessage("Quick practice data expired. Rebuild it from the previous page.");
            return;
          }
          examCategory = quickPractice.exam_category || "";
          examContent = deriveQuickPracticeExamContent(quickPractice);
          title = quickPractice.label || "Quick Practice";
          payload = quickPractice.payload;
          submitUrl = "/api/v1/mockexam/evaluate";
          inlinePayload = quickPractice.payload;
        } else if (currentSourceType === "submission-review") {
          const submissionResponse = await getMockExamSubmission(sourceId);
          if (!active) {
            return;
          }
          const submission = submissionResponse.data || {};
          examCategory = submission.exam_category || "";
          examContent = submission.exam_content || "";
          title = submission.title || "";
          payload = submission.payload;
          reviewMode = true;
          initialAnswers = submission.answers || {};
          initialMarked = submission.marked || {};
          initialResult = submission.result || null;
        } else {
          setMessage("Unsupported mock exam source.");
          return;
        }

        setFrameSrcDoc(
          buildMockExamIeltsSrcDoc({
            email: toUserDisplayName(profile),
            sourceType: currentSourceType,
            sourceId,
            questionBankId: currentSourceType === "question-bank" ? sourceId : null,
            sourceTitle: title,
            fileName: title,
            examCategory,
            examContent,
            payload,
            submitUrl,
            inlinePayload,
            reviewMode,
            initialAnswers,
            initialMarked,
            initialResult,
          })
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(error?.response?.data?.detail || "Failed to load mock exam paper.");
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
  }, [navigate, sourceId, sourceType]);

  if (loading) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">
          <LoaderCircle size={18} strokeWidth={2.2} className="spin" />
          <span>Loading mock exam paper...</span>
        </div>
      </div>
    );
  }

  if (!frameSrcDoc) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">{message || "No mock exam paper available."}</div>
      </div>
    );
  }

  return (
    <div className="mockexam-runner">
      <iframe
        ref={frameRef}
        title="Mock Exam Runner"
        className="mockexam-runner-frame"
        srcDoc={frameSrcDoc}
      />
    </div>
  );
}
