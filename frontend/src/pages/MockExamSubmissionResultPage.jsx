import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, RotateCcw } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getMockExamSubmission } from "../api/mockexam";
import "../mockexam/mockexam.css";

function formatTime(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatQuestionType(value) {
  const type = String(value || "").toLowerCase();
  if (type === "single") return "单选题";
  if (type === "multiple") return "多选题";
  if (type === "tfng") return "判断题";
  if (type === "blank") return "填空题";
  if (type === "cloze_inline") return "完形填空";
  if (type === "matching") return "匹配题";
  if (type === "essay") return "写作题";
  return value || "未知题型";
}

function formatApiError(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string" && detail.trim()) {
    return detail;
  }
  if (Array.isArray(detail) && detail.length) {
    const messages = detail
      .map((item) => {
        if (typeof item === "string" && item.trim()) {
          return item.trim();
        }
        if (item?.msg) {
          const loc = Array.isArray(item.loc) ? item.loc.join(".") : "";
          return loc ? `${loc}: ${item.msg}` : String(item.msg);
        }
        return "";
      })
      .filter(Boolean);
    if (messages.length) {
      return messages.join("; ");
    }
  }
  if (detail?.msg) {
    return String(detail.msg);
  }
  return fallback;
}

export default function MockExamSubmissionResultPage() {
  const { submissionId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [submission, setSubmission] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadSubmission() {
      try {
        setLoading(true);
        const response = await getMockExamSubmission(submissionId);
        if (!active) {
          return;
        }
        setSubmission(response.data || null);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setSubmission(null);
        setMessage(formatApiError(error, "成绩结果加载失败"));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadSubmission();
    return () => {
      active = false;
    };
  }, [submissionId]);

  const result = submission?.result || {};
  const resultDetails = Array.isArray(result.details) ? result.details : [];
  const typeBreakdown = Array.isArray(result.type_breakdown) ? result.type_breakdown : [];
  const wrongDetails = useMemo(
    () => resultDetails.filter((item) => item?.gradable && item?.answered && !item?.correct),
    [resultDetails]
  );

  if (loading) {
    return <div className="mockexam-page"><div className="mockexam-shell" /></div>;
  }

  if (!submission) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">{message || "未找到成绩结果"}</div>
      </div>
    );
  }

  return (
    <div className="mockexam-page">
      <div className="mockexam-shell">
        <div className="mockexam-topbar">
          <div className="mockexam-topcopy">
            <button type="button" className="mockexam-back" onClick={() => navigate("/mockexam")}>
              <ArrowLeft size={16} strokeWidth={2.2} />
              返回模拟考试
            </button>
            <h1>交卷结果</h1>
            <p>{submission.title}</p>
          </div>
        </div>

        <div className="mockexam-result-grid">
          <section className="mockexam-panel">
            <div className="mockexam-panel-head">
              <div>
                <h2>成绩概览</h2>
                <p>
                  {submission.exam_category || "Mock Exam"}
                  {submission.exam_content ? ` / ${submission.exam_content}` : ""}
                  {` / ${formatTime(submission.create_time)}`}
                </p>
              </div>
            </div>

            <div className="mockexam-result-stats">
              <article className="mockexam-result-stat-card">
                <span>得分</span>
                <strong>{result.score_percent == null ? "--" : `${result.score_percent}%`}</strong>
              </article>
              <article className="mockexam-result-stat-card">
                <span>答对</span>
                <strong>
                  {result.correct_count || 0} / {result.gradable_questions || 0}
                </strong>
              </article>
              <article className="mockexam-result-stat-card">
                <span>答错</span>
                <strong>{result.wrong_count || 0}</strong>
              </article>
              <article className="mockexam-result-stat-card">
                <span>未答</span>
                <strong>{result.unanswered_count || 0}</strong>
              </article>
            </div>

            <div className="mockexam-history-actions mockexam-result-actions">
              <button
                type="button"
                className="mockexam-secondary-button"
                onClick={() => navigate(`/mockexam/run/submission-review/${submissionId}`)}
              >
                <RotateCcw size={15} strokeWidth={2.1} />
                <span>成绩回看</span>
              </button>
            </div>
          </section>

          <section className="mockexam-panel">
            <div className="mockexam-panel-head">
              <div>
                <h2>按题型统计</h2>
                <p>展示每个题型共有多少题，以及这次答错了多少题。</p>
              </div>
            </div>

            <div className="mockexam-type-breakdown">
              {typeBreakdown.map((item) => (
                <article key={item.question_type} className="mockexam-type-breakdown-card">
                  <div>
                    <h3>{formatQuestionType(item.question_type)}</h3>
                    <p>共 {item.total_questions || 0} 题</p>
                  </div>
                  <div className="mockexam-type-breakdown-metrics">
                    <span>答错 {item.wrong_count || 0}</span>
                    <span>答对 {item.correct_count || 0}</span>
                    <span>未答 {item.unanswered_count || 0}</span>
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>

        <section className="mockexam-panel">
          <div className="mockexam-panel-head">
            <div>
              <h2>错题明细</h2>
              <p>仅展示可判分且已作答但判错的题目。</p>
            </div>
          </div>

          <div className="mockexam-result-detail-list">
            {wrongDetails.map((item) => (
              <article key={item.question_id} className="mockexam-result-detail-card">
                <div className="mockexam-result-detail-head">
                  <strong>{item.question_no ? `Q${item.question_no}` : item.question_id}</strong>
                  <span>{formatQuestionType(item.type)}</span>
                </div>
                <p>{item.stem || "无题干摘要"}</p>
                {item.exam_question_id ? (
                  <div className="mockexam-history-actions">
                    <button
                      type="button"
                      className="mockexam-secondary-button"
                      onClick={() => navigate(`/mockexam/questions/${item.exam_question_id}`)}
                    >
                      查看题目
                    </button>
                  </div>
                ) : null}
              </article>
            ))}

            {!wrongDetails.length ? (
              <div className="mockexam-beta-empty">这次没有已作答的错题。</div>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}
