import React, { useEffect, useState } from "react";
import { LoaderCircle, RotateCcw, Trophy } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMockExamSubmissions } from "../api/mockexam";

function formatSourceType(value) {
  if (value === "paper-beta") {
    return "测试版";
  }
  if (value === "question-bank") {
    return "题库";
  }
  if (value === "exam-set") {
    return "组合试卷";
  }
  if (value === "quick-practice") {
    return "随堂小练";
  }
  return value || "未知来源";
}

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

export default function MockExamSubmissionHistoryPanel({
  title = "成绩回看",
  description = "最近交卷记录会保留在这里，可直接回看结果和答题过程。",
  sourceType = "",
  limit = 10,
  emptyText = "暂无成绩记录",
}) {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function loadSubmissions() {
      try {
        setLoading(true);
        const response = await getMockExamSubmissions({
          source_type: sourceType || undefined,
          limit,
        });
        if (!active) {
          return;
        }
        setItems(response.data?.items || []);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setItems([]);
        setMessage(error?.response?.data?.detail || "成绩记录加载失败");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadSubmissions();

    return () => {
      active = false;
    };
  }, [limit, sourceType]);

  return (
    <div className="mockexam-panel">
      <div className="mockexam-panel-head">
        <div>
          <h3>{title}</h3>
          <p>{description}</p>
        </div>
        <span className="mockexam-panel-badge">
          <Trophy size={22} strokeWidth={2.1} />
        </span>
      </div>

      {loading ? (
        <div className="mockexam-inline-note">
          <LoaderCircle size={16} strokeWidth={2.1} className="spin" /> 正在加载成绩记录...
        </div>
      ) : null}

      {!loading && message ? <div className="mockexam-inline-note">{message}</div> : null}

      {!loading && !message ? (
        <div className="mockexam-history-list">
          {items.map((item) => (
            <article key={item.submission_id} className="mockexam-history-card">
              <div className="mockexam-history-copy">
                <div className="mockexam-history-tags">
                  <span>{formatSourceType(item.source_type)}</span>
                  <span>{item.exam_category || "Mock Exam"}</span>
                  {item.exam_content ? <span>{item.exam_content}</span> : null}
                </div>
                <h4>{item.title}</h4>
                <p>{formatTime(item.create_time)}</p>
              </div>

              <div className="mockexam-history-score">
                <strong>{item.score_percent == null ? "--" : `${item.score_percent}%`}</strong>
                <span>
                  答对 {item.correct_count || 0} / {item.gradable_questions || 0}
                </span>
                <span>答错 {item.wrong_count || 0}</span>
              </div>

              <div className="mockexam-history-actions">
                <button
                  type="button"
                  className="mockexam-secondary-button"
                  onClick={() => navigate(`/mockexam/results/${item.submission_id}`)}
                >
                  查看结果
                </button>
                <button
                  type="button"
                  className="mockexam-tertiary-button"
                  onClick={() => navigate(`/mockexam/run/submission-review/${item.submission_id}`)}
                >
                  <RotateCcw size={15} strokeWidth={2.1} />
                  <span>回看答题</span>
                </button>
              </div>
            </article>
          ))}

          {!items.length ? <div className="mockexam-beta-empty">{emptyText}</div> : null}
        </div>
      ) : null}
    </div>
  );
}
