import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { getMe } from "../api/auth";
import {
  getMockExamFavorites,
  getMockExamPaper,
  getMockExamPaperSet,
  getMockExamProgress,
  getMockExamSubmission,
} from "../api/mockexam";
import { LoadingPage } from "../components/LoadingPage";
import { buildMockExamIeltsSrcDoc } from "../mockexam/mockexamFrame";
import "../mockexam/mockexam.css";

function toUserDisplayName(profile) {
  return profile?.nickname || profile?.mobile || "Student";
}

function formatApiError(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string" && detail.trim()) {
    return detail;
  }
  if (Array.isArray(detail) && detail.length) {
    const first = detail[0];
    if (typeof first === "string" && first.trim()) {
      return first;
    }
    if (first?.msg) {
      return String(first.msg);
    }
  }
  if (detail?.msg) {
    return String(detail.msg);
  }
  return fallback;
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
      if (data.type === "mockexam:submitted" && data.submission_id) {
        navigate(`/mockexam/results/${data.submission_id}`);
      }
      if (data.type === "mockexam:saved" && data.progress_id) {
        navigate("/mockexam");
      }
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
        let favoriteQuestionIds = [];

        let examCategory = "IELTS";
        let examContent = "";
        let title = "";
        let payload = null;
        let examPaperId = null;
        let paperSetId = null;
        let progressId = null;
        let submitUrl = "";
        let saveProgressUrl = "";
        let reviewMode = false;
        let initialAnswers = null;
        let initialMarked = null;
        let initialResult = null;
        let initialQuestionId = null;
        let initialQuestionIndex = null;
        let initialQuestionNo = null;
        const currentSourceType = sourceType || "paper";

        if (currentSourceType === "paper") {
          const paperResponse = await getMockExamPaper(sourceId);
          if (!active) {
            return;
          }
          const paper = paperResponse.data || {};
          examCategory = paper.exam_category || "IELTS";
          examContent = paper.exam_content || "";
          title = paper.paper_name || paper.paper_code || "";
          payload = paper.payload;
          examPaperId = paper.exam_paper_id;
          submitUrl = `/api/v1/mockexam/papers/${paper.exam_paper_id}/submit`;
          saveProgressUrl = `/api/v1/mockexam/papers/${paper.exam_paper_id}/progress`;
        } else if (currentSourceType === "paper-set") {
          const paperSetResponse = await getMockExamPaperSet(sourceId);
          if (!active) {
            return;
          }
          const paperSet = paperSetResponse.data || {};
          examCategory = paperSet.exam_category || "IELTS";
          examContent = paperSet.exam_content || "";
          title = paperSet.set_name || "";
          payload = paperSet.payload;
          paperSetId = paperSet.mockexam_paper_set_id;
          examPaperId = 0;
          submitUrl = `/api/v1/mockexam/paper-sets/${paperSet.mockexam_paper_set_id}/submit`;
          saveProgressUrl = `/api/v1/mockexam/paper-sets/${paperSet.mockexam_paper_set_id}/progress`;
        } else if (currentSourceType === "progress") {
          const progressResponse = await getMockExamProgress(sourceId);
          if (!active) {
            return;
          }
          const progress = progressResponse.data || {};
          examCategory = progress.exam_category || "IELTS";
          examContent = progress.exam_content || "";
          title = progress.title || "";
          payload = progress.payload;
          examPaperId = progress.exam_paper_id;
          paperSetId = progress.paper_set_id || null;
          progressId = progress.progress_id;
          if (progress.source_kind === "paper_set" && progress.paper_set_id) {
            submitUrl = `/api/v1/mockexam/paper-sets/${progress.paper_set_id}/submit`;
            saveProgressUrl = `/api/v1/mockexam/paper-sets/${progress.paper_set_id}/progress`;
          } else {
            submitUrl = `/api/v1/mockexam/papers/${progress.exam_paper_id}/submit`;
            saveProgressUrl = `/api/v1/mockexam/papers/${progress.exam_paper_id}/progress`;
          }
          initialAnswers = progress.answers || {};
          initialMarked = progress.marked || {};
          initialQuestionId = progress.current_question_id || null;
          initialQuestionIndex =
            typeof progress.current_question_index === "number"
              ? progress.current_question_index
              : null;
          initialQuestionNo = progress.current_question_no || null;
        } else if (currentSourceType === "submission-review") {
          const submissionResponse = await getMockExamSubmission(sourceId);
          if (!active) {
            return;
          }
          const submission = submissionResponse.data || {};
          examCategory = submission.exam_category || "IELTS";
          examContent = submission.exam_content || "";
          title = submission.title || "";
          payload = submission.payload;
          examPaperId = submission.exam_paper_id;
          paperSetId = submission.paper_set_id || null;
          reviewMode = true;
          initialAnswers = submission.answers || {};
          initialMarked = submission.marked || {};
          initialResult = submission.result || null;
        } else {
          setMessage("Unsupported mock exam source.");
          return;
        }

        if (currentSourceType === "paper-set" || paperSetId) {
          try {
            const favoritesResponse = await getMockExamFavorites({
              limit: 100,
            });
            if (!active) {
              return;
            }
            favoriteQuestionIds = (favoritesResponse.data?.items || [])
              .map((item) => item?.exam_question_id)
              .filter((value) => value != null);
          } catch (_error) {
            favoriteQuestionIds = [];
          }
        } else if (examPaperId != null) {
          try {
            const favoritesResponse = await getMockExamFavorites({
              exam_paper_id: examPaperId,
              limit: 100,
            });
            if (!active) {
              return;
            }
            favoriteQuestionIds = (favoritesResponse.data?.items || [])
              .map((item) => item?.exam_question_id)
              .filter((value) => value != null);
          } catch (_error) {
            favoriteQuestionIds = [];
          }
        }

        setFrameSrcDoc(
          buildMockExamIeltsSrcDoc({
            email: toUserDisplayName(profile),
            sourceType: currentSourceType,
            sourceId,
            examPaperId,
            paperSetId,
            progressId,
            sourceTitle: title,
            fileName: title,
            examCategory,
            examContent,
            payload,
            submitUrl,
            saveProgressUrl,
            favoriteToggleBaseUrl: "/api/v1/mockexam/questions/__EXAM_QUESTION_ID__/favorite",
            reviewMode,
            initialAnswers,
            initialMarked,
            initialResult,
            initialQuestionId,
            initialQuestionIndex,
            initialQuestionNo,
            initialFavoriteQuestionIds: favoriteQuestionIds,
          })
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(formatApiError(error, "Failed to load mock exam paper."));
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
    return <LoadingPage message="正在进入考试" submessage="请稍候，正在准备当前试卷内容" />;
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
