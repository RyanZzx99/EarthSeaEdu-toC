import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  AlertCircle,
  ArrowLeft,
  BarChart3,
  BookOpen,
  Calendar,
  CheckCircle2,
  Clock,
  Eye,
  RotateCcw,
  Target,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getMockExamSubmission } from "../api/mockexam";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { LoadingPage } from "../components/LoadingPage";
import {
  buildQuestionStore,
  getAnswerDisplayText,
  inferQuestionType,
  normalizeExamData,
} from "../mockexam/practiceExerciseUtils";
import "../mockexam/mockexam.css";
import "../mockexam/submissionResult.css";

const TEXT = {
  user: "\u7528\u6237",
  loadingTitle: "\u6b63\u5728\u52a0\u8f7d\u6210\u7ee9\u9875",
  loadingSub: "\u8bf7\u7a0d\u5019\uff0c\u6b63\u5728\u6574\u7406\u672c\u6b21\u4ea4\u5377\u7ed3\u679c",
  loadFailed: "\u6210\u7ee9\u7ed3\u679c\u52a0\u8f7d\u5931\u8d25",
  notFound: "\u672a\u627e\u5230\u6210\u7ee9\u7ed3\u679c",
  notFoundSub:
    "\u5f53\u524d\u63d0\u4ea4\u8bb0\u5f55\u4e0d\u5b58\u5728\u6216\u6682\u65f6\u65e0\u6cd5\u8bbf\u95ee\u3002",
  backPractice: "\u8fd4\u56de\u7ec3\u4e60\u6a21\u5f0f",
  backList: "\u8fd4\u56de\u8bd5\u5377\u5217\u8868",
  pageTitle: "\u4ea4\u5377\u7ed3\u679c",
  summaryTitle: "\u6210\u7ee9\u603b\u7ed3",
  accuracy: "\u6b63\u786e\u7387",
  correct: "\u7b54\u5bf9",
  wrong: "\u7b54\u9519",
  timeUsed: "\u7528\u65f6",
  finishedAt: "\u5b8c\u6210\u65f6\u95f4",
  notRecorded: "\u672a\u8bb0\u5f55",
  questionUnit: "\u9898",
  wrongPercentPrefix: "\u5360\u9519\u9898\u7684",
  reviewMode: "\u56de\u770b\u6a21\u5f0f",
  wrongStats: "\u9519\u9898\u7edf\u8ba1",
  wrongStatsSub:
    "\u5171 {count} \u9053\u9519\u9898\uff0c\u5efa\u8bae\u91cd\u70b9\u590d\u4e60\u4ee5\u4e0b\u9898\u578b",
  noWrongItems: "\u672c\u6b21\u6ca1\u6709\u5df2\u4f5c\u7b54\u4e14\u5224\u9519\u7684\u9898\u76ee\u3002",
  detailTitle: "\u9898\u76ee\u8be6\u60c5",
  colQuestionNo: "\u9898\u53f7",
  colType: "\u9898\u578b",
  colYourAnswer: "\u4f60\u7684\u7b54\u6848",
  colCorrectAnswer: "\u6b63\u786e\u7b54\u6848",
  colResult: "\u7ed3\u679c",
  colAction: "\u64cd\u4f5c",
  unanswered: "\u672a\u4f5c\u7b54",
  ungraded: "\u672a\u5224\u5206",
  correctResult: "\u6b63\u786e",
  wrongResult: "\u9519\u8bef",
  view: "\u67e5\u770b",
  emptyAction: "\u6682\u65e0",
  noRows: "\u5f53\u524d\u6ca1\u6709\u53ef\u5c55\u793a\u7684\u9898\u76ee\u8be6\u60c5\u3002",
  wrongBook: "\u8fdb\u5165\u9519\u9898\u672c",
  practiceHistory: "\u7ec3\u4e60\u8bb0\u5f55",
  continuePractice: "\u7ee7\u7eed\u7ec3\u4e60",
  backHome: "\u8fd4\u56de\u9996\u9875",
};

const LIBRARY_TABS = [
  { key: "mistakes", label: "错题本", path: "/mockexam/mistakes" },
  { key: "history", label: "练习历史", path: "/mockexam/history" },
  { key: "favorites", label: "收藏夹", path: "/mockexam/favorites" },
];

function replaceTemplate(template, values) {
  return Object.entries(values).reduce(
    (current, [key, value]) => current.replaceAll(`{${key}}`, String(value)),
    template
  );
}

function formatTime(value) {
  if (!value) {
    return "--";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "--";
  }
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatElapsedDuration(seconds) {
  const total = Math.max(0, Number(seconds) || 0);
  if (!total) {
    return TEXT.notRecorded;
  }
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const remainder = total % 60;
  if (hours > 0) {
    return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
  }
  return `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function formatApiError(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string" && detail.trim()) {
    return detail.trim();
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

function formatQuestionType(value) {
  const type = String(value || "").trim().toLowerCase();
  const labels = {
    single: "\u5355\u9009\u9898",
    multiple: "\u591a\u9009\u9898",
    tfng: "\u5224\u65ad\u9898",
    blank: "\u586b\u7a7a\u9898",
    cloze_inline: "\u8868\u683c\u586b\u7a7a",
    matching: "\u5339\u914d\u9898",
    essay: "\u5199\u4f5c\u9898",
    multiple_choice: "\u9009\u62e9\u9898",
    true_false_not_given: "\u5224\u65ad\u9898",
    yes_no_not_given: "\u5224\u65ad\u9898",
    sentence_completion: "\u53e5\u5b50\u586b\u7a7a",
    form_completion: "\u8868\u683c\u586b\u7a7a",
    note_completion: "\u7b14\u8bb0\u586b\u7a7a",
    summary_completion: "\u6458\u8981\u586b\u7a7a",
    table_completion: "\u8868\u683c\u586b\u7a7a",
  };
  return labels[type] || TEXT.colType;
}

function resolveBackTarget(submission) {
  if (submission?.source_kind === "paper_set" || submission?.paper_set_id) {
    return "/mockexam/practice/test";
  }
  return "/mockexam/practice/topic";
}

function extractStructuredAnswerValues(answerValue, question) {
  if (!answerValue || typeof answerValue !== "object" || Array.isArray(answerValue)) {
    return [];
  }

  const questionType = inferQuestionType(question || {});
  if (questionType === "cloze_inline") {
    const blanks = Array.isArray(question?.blanks) ? question.blanks : [];
    const orderedValues = blanks
      .map((blank) => answerValue?.[blank?.id])
      .map((value) => String(value ?? "").trim())
      .filter(Boolean);
    if (orderedValues.length) {
      return orderedValues;
    }
  }

  if (questionType === "matching") {
    const items = Array.isArray(question?.questions)
      ? question.questions
      : Array.isArray(question?.items)
        ? question.items
        : [];
    const orderedValues = items
      .map((item) => answerValue?.[item?.id])
      .map((value) => String(value ?? "").trim())
      .filter(Boolean);
    if (orderedValues.length) {
      return orderedValues;
    }
  }

  return Object.values(answerValue)
    .map((value) => String(value ?? "").trim())
    .filter(Boolean);
}

function buildAnswerPreview(answerValue, question) {
  const questionType = inferQuestionType(question || {});
  if (
    answerValue == null ||
    answerValue === "" ||
    (Array.isArray(answerValue) && !answerValue.length)
  ) {
    return TEXT.unanswered;
  }

  if (questionType === "multiple") {
    return Array.isArray(answerValue) ? answerValue.join(", ") : String(answerValue);
  }

  if (questionType === "cloze_inline" || questionType === "matching") {
    const values = extractStructuredAnswerValues(answerValue, question);
    return values.length ? values.join(" / ") : TEXT.unanswered;
  }

  if (typeof answerValue === "object") {
    const values = extractStructuredAnswerValues(answerValue, question);
    return values.length ? values.join(" / ") : TEXT.unanswered;
  }

  return String(answerValue).trim() || TEXT.unanswered;
}

function buildWrongBreakdown(result) {
  const items = Array.isArray(result?.type_breakdown) ? result.type_breakdown : [];
  return items
    .filter((item) => Number(item?.wrong_count || 0) > 0)
    .map((item) => ({
      key: String(item?.question_type || "unknown"),
      label: formatQuestionType(item?.question_type),
      count: Number(item?.wrong_count || 0),
      percent:
        Number(result?.wrong_count || 0) > 0
          ? Math.round((Number(item?.wrong_count || 0) / Number(result?.wrong_count || 0)) * 100)
          : 0,
    }));
}

function buildCorrectAnswerPreview(question) {
  if (!question) {
    return "--";
  }

  const questionType = inferQuestionType(question);

  if (questionType === "cloze_inline") {
    const answers = (Array.isArray(question?.blanks) ? question.blanks : [])
      .map((blank) => String(blank?.answer || "").trim())
      .filter(Boolean);
    return answers.length ? [...new Set(answers)].join(" / ") : "--";
  }

  if (questionType === "matching") {
    const items = Array.isArray(question?.questions)
      ? question.questions
      : Array.isArray(question?.items)
        ? question.items
        : [];
    const answers = items
      .map((item) => String(item?.answer || "").trim())
      .filter(Boolean);
    return answers.length ? answers.join(" / ") : "--";
  }

  return getAnswerDisplayText(question) || "--";
}

function buildQuestionRows(submission) {
  const payload = submission?.payload;
  const details = Array.isArray(submission?.result?.details) ? submission.result.details : [];
  const answers = submission?.answers && typeof submission.answers === "object" ? submission.answers : {};

  if (!payload || !details.length) {
    return [];
  }

  const normalized = normalizeExamData(payload);
  const store = buildQuestionStore(normalized);
  const questionMap = store.questionMap || {};

  return details.map((item, index) => {
    const question = questionMap[item?.question_id] || null;
    const isCorrect = Boolean(item?.correct);
    const answered = Boolean(item?.answered);
    const gradable = Boolean(item?.gradable);
    let status = TEXT.ungraded;

    if (gradable) {
      if (isCorrect) {
        status = TEXT.correctResult;
      } else if (answered) {
        status = TEXT.wrongResult;
      } else {
        status = TEXT.unanswered;
      }
    }

    return {
      key: item?.question_id || `row-${index}`,
      questionId: item?.question_id || question?.id || "",
      questionNo: item?.question_no ?? question?.question_no ?? question?.displayNo ?? index + 1,
      typeLabel: formatQuestionType(item?.type || item?.stat_type || inferQuestionType(question || {})),
      yourAnswer: buildAnswerPreview(answers[item?.question_id], question),
      correctAnswer: buildCorrectAnswerPreview(question),
      isCorrect,
      answered,
      status,
      examQuestionId: item?.exam_question_id || null,
    };
  });
}

export default function MockExamSubmissionResultPage() {
  const { submissionId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [submission, setSubmission] = useState(null);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const submissionResult = await getMockExamSubmission(submissionId);
        if (!active) {
          return;
        }
        setSubmission(submissionResult?.data || null);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setSubmission(null);
        setMessage(formatApiError(error, TEXT.loadFailed));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
  }, [submissionId]);

  const result = submission?.result || {};
  const scorePercent =
    typeof result?.score_percent === "number"
      ? result.score_percent
      : typeof submission?.score_percent === "number"
      ? submission.score_percent
      : null;
  const correctCount = Number(result?.correct_count || submission?.correct_count || 0);
  const wrongCount = Number(result?.wrong_count || submission?.wrong_count || 0);
  const totalQuestions = Number(result?.total_questions || submission?.total_questions || 0);
  const completedDate = formatTime(submission?.create_time);
  const elapsedDuration = formatElapsedDuration(submission?.elapsed_seconds);
  const wrongBreakdown = useMemo(() => buildWrongBreakdown(result), [result]);
  const questionRows = useMemo(() => buildQuestionRows(submission), [submission]);
  const backTarget = resolveBackTarget(submission);

  if (loading) {
    return <LoadingPage message={TEXT.loadingTitle} submessage={TEXT.loadingSub} />;
  }

  if (!submission) {
    return (
      <div className="result-page-shell">
        <div className="result-empty-card">
          <h1>{TEXT.notFound}</h1>
          <p>{message || TEXT.notFoundSub}</p>
          <button type="button" className="result-primary-button" onClick={() => navigate("/mockexam/practice")}>
            {TEXT.backPractice}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="result-page-shell">
      <MockExamModeHeader
        activeMode="history"
        tabs={LIBRARY_TABS}
        backButton={{ label: "返回模考界面", path: "/mockexam" }}
      />

      <main className="result-main">
        <div className="result-container">
          <motion.button
            type="button"
            className="result-back-button"
            onClick={() => navigate(backTarget)}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.4 }}
          >
            <ArrowLeft size={18} />
            <span>{TEXT.backList}</span>
          </motion.button>

          <motion.div
            className="result-page-header"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <div className="result-page-header-row">
              <CheckCircle2 className="result-page-header-icon" />
              <h2>{TEXT.pageTitle}</h2>
            </div>
            <p>{submission.title}</p>
          </motion.div>

          <div className="result-summary-grid">
            <motion.section
              className="result-card"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
            >
              <div className="result-card-title">
                <div className="result-card-icon result-card-icon-indigo">
                  <Target size={20} />
                </div>
                <h3>{TEXT.summaryTitle}</h3>
              </div>

              <div className="result-score-block">
                <div
                  className="result-score-ring"
                  style={{
                    background:
                      scorePercent == null
                        ? "conic-gradient(from 0deg, #e5e7eb 0deg, #e5e7eb 360deg)"
                        : `conic-gradient(from 0deg, #4f46e5 0deg, #4f46e5 ${Math.max(
                            0,
                            Math.min(360, scorePercent * 3.6)
                          )}deg, #e5e7eb ${Math.max(
                            0,
                            Math.min(360, scorePercent * 3.6)
                          )}deg, #e5e7eb 360deg)`,
                  }}
                >
                  <div className="result-score-ring-inner">
                    <p>{scorePercent == null ? "--" : `${scorePercent}%`}</p>
                    <span>{TEXT.accuracy}</span>
                  </div>
                </div>
              </div>

              <div className="result-summary-stats">
                <div className="result-stat-box result-stat-box-green">
                  <span>{TEXT.correct}</span>
                  <strong>{correctCount}</strong>
                  <small>
                    / {totalQuestions} {TEXT.questionUnit}
                  </small>
                </div>
                <div className="result-stat-box result-stat-box-red">
                  <span>{TEXT.wrong}</span>
                  <strong>{wrongCount}</strong>
                  <small>{TEXT.questionUnit}</small>
                </div>
              </div>

              <div className="result-meta-list">
                <div className="result-meta-row">
                  <div className="result-meta-label">
                    <Clock size={16} />
                    <span>{TEXT.timeUsed}</span>
                  </div>
                  <strong>{elapsedDuration}</strong>
                </div>
                <div className="result-meta-row">
                  <div className="result-meta-label">
                    <Calendar size={16} />
                    <span>{TEXT.finishedAt}</span>
                  </div>
                  <strong>{completedDate}</strong>
                </div>
              </div>

              <button
                type="button"
                className="result-primary-button result-review-button"
                onClick={() => navigate(`/mockexam/run/submission-review/${submissionId}`)}
              >
                <RotateCcw size={18} />
                <span>{TEXT.reviewMode}</span>
              </button>
            </motion.section>

            <motion.section
              className="result-card"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="result-card-title">
                <div className="result-card-icon result-card-icon-rose">
                  <AlertCircle size={20} />
                </div>
                <h3>{TEXT.wrongStats}</h3>
              </div>

              <p className="result-card-subtitle">
                {replaceTemplate(TEXT.wrongStatsSub, { count: wrongCount })}
              </p>

              <div className="result-breakdown-list">
                {wrongBreakdown.length ? (
                  wrongBreakdown.map((item, index) => (
                    <motion.div
                      key={item.key}
                      className="result-breakdown-card"
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ duration: 0.4, delay: 0.3 + index * 0.1 }}
                    >
                      <div className="result-breakdown-head">
                        <div className="result-breakdown-name">
                          <BookOpen size={15} />
                          <span>{item.label}</span>
                        </div>
                        <strong>
                          {item.count} {TEXT.questionUnit}
                        </strong>
                      </div>
                      <div className="result-breakdown-progress">
                        <div
                          className="result-breakdown-progress-fill"
                          style={{ width: `${Math.max(0, item.percent)}%` }}
                        />
                      </div>
                      <p>
                        {TEXT.wrongPercentPrefix} {item.percent}%
                      </p>
                    </motion.div>
                  ))
                ) : (
                  <div className="result-breakdown-empty">{TEXT.noWrongItems}</div>
                )}
              </div>
            </motion.section>
          </div>

          <motion.section
            className="result-table-card"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
          >
            <div className="result-table-header">
              <div className="result-table-title">
                <BarChart3 size={20} />
                <h3>{TEXT.detailTitle}</h3>
              </div>
            </div>

            <div className="result-table-wrap">
              <table className="result-table">
                <thead>
                  <tr>
                    <th>{TEXT.colQuestionNo}</th>
                    <th>{TEXT.colType}</th>
                    <th>{TEXT.colYourAnswer}</th>
                    <th>{TEXT.colCorrectAnswer}</th>
                    <th>{TEXT.colResult}</th>
                    <th>{TEXT.colAction}</th>
                  </tr>
                </thead>
                <tbody>
                  {questionRows.map((row) => (
                    <tr key={row.key} className={row.isCorrect ? "" : "result-table-row-wrong"}>
                      <td>
                        <span className="result-table-question-no">{`Q${row.questionNo}`}</span>
                      </td>
                      <td>
                        <span className="result-table-type-chip">{row.typeLabel}</span>
                      </td>
                      <td>
                        <span
                          className={`result-table-answer-chip${
                            row.isCorrect ? " is-correct" : row.answered ? " is-wrong" : ""
                          }`}
                        >
                          {row.yourAnswer}
                        </span>
                      </td>
                      <td>
                        <span className="result-table-answer-chip is-correct">{row.correctAnswer}</span>
                      </td>
                      <td>
                        <span
                          className={`result-table-status${
                            row.status === TEXT.correctResult
                              ? " is-correct"
                              : row.status === TEXT.wrongResult
                              ? " is-wrong"
                              : " is-pending"
                          }`}
                        >
                          {row.status === TEXT.correctResult ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                          <span>{row.status}</span>
                        </span>
                      </td>
                      <td>
                        {row.questionId || row.questionNo ? (
                          <button
                            type="button"
                            className="result-table-view"
                            onClick={() =>
                              navigate(
                                `/mockexam/run/submission-review/${submissionId}?questionId=${encodeURIComponent(
                                  row.questionId
                                )}&questionNo=${encodeURIComponent(row.questionNo)}`
                              )
                            }
                          >
                            <Eye size={14} />
                            <span>{TEXT.view}</span>
                          </button>
                        ) : (
                          <span className="result-table-view result-table-view-disabled">{TEXT.emptyAction}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {!questionRows.length ? <div className="result-breakdown-empty">{TEXT.noRows}</div> : null}
            </div>
          </motion.section>

          <motion.div
            className="result-bottom-actions"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
          >
            <button
              type="button"
              className="result-secondary-button result-secondary-indigo"
              onClick={() => navigate(backTarget)}
            >
              {TEXT.continuePractice}
            </button>
          </motion.div>
        </div>
      </main>
    </div>
  );
}
