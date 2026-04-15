import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, RotateCcw, Trophy } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMockExamSubmissions } from "../api/mockexam";
import { InlineLoading } from "./LoadingPage";

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

function getApiError(error, fallback) {
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

export default function MockExamSubmissionHistoryPanel({
  title = "成绩回看",
  description = "这里保留最近交卷记录，可以直接查看结果或回看答题过程。",
  limit = 10,
  emptyText = "暂无成绩记录",
}) {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [loadedOnce, setLoadedOnce] = useState(false);

  useEffect(() => {
    if (!expanded || loadedOnce) {
      return undefined;
    }

    let active = true;

    async function loadSubmissions() {
      try {
        setLoading(true);
        const response = await getMockExamSubmissions({ limit });
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
        setMessage(getApiError(error, "成绩记录加载失败"));
      } finally {
        if (active) {
          setLoading(false);
          setLoadedOnce(true);
        }
      }
    }

    void loadSubmissions();

    return () => {
      active = false;
    };
  }, [expanded, limit, loadedOnce]);

  return (
    <div className="mockexam-panel">
      <div className={`mockexam-panel-head ${expanded ? "" : "mockexam-panel-head-collapsed"}`}>
        <div>
          <h3>{title}</h3>
          <p>{description}</p>
        </div>
        <div className="mockexam-panel-head-actions">
          <span className="mockexam-panel-badge">
            <Trophy size={22} strokeWidth={2.1} />
          </span>
          <button
            type="button"
            className="mockexam-panel-toggle"
            aria-expanded={expanded}
            onClick={() => setExpanded((previous) => !previous)}
          >
            {expanded ? <ChevronDown size={16} strokeWidth={2.1} /> : <ChevronRight size={16} strokeWidth={2.1} />}
            <span>{expanded ? "收起" : "展开"}</span>
          </button>
        </div>
      </div>

      {expanded ? (
        <>
          {loading ? (
            <div className="mockexam-inline-note">
              <InlineLoading message="正在准备练习记录" size="sm" />
            </div>
          ) : null}

          {!loading && message ? <div className="mockexam-inline-note">{message}</div> : null}

          {!loading && !message ? (
            <div className="mockexam-history-list">
              {items.map((item) => (
                <article key={item.submission_id} className="mockexam-history-card">
                  <div className="mockexam-history-copy">
                    <div className="mockexam-history-tags">
                      <span>{item.exam_category || "Mock Exam"}</span>
                      <span>{item.source_kind === "paper_set" ? "组合试卷" : "单张试卷"}</span>
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
        </>
      ) : null}
    </div>
  );
}
