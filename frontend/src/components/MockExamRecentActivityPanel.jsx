import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, Clock3, PlayCircle, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { discardMockExamProgress, getMockExamProgresses } from "../api/mockexam";
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

export default function MockExamRecentActivityPanel() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [expanded, setExpanded] = useState(false);
  const [loadedOnce, setLoadedOnce] = useState(false);

  async function loadItems() {
    try {
      setLoading(true);
      const response = await getMockExamProgresses({ limit: 10 });
      setItems(response.data?.items || []);
      setMessage("");
    } catch (error) {
      setItems([]);
      setMessage(getApiError(error, "最近活动加载失败"));
    } finally {
      setLoading(false);
      setLoadedOnce(true);
    }
  }

  useEffect(() => {
    if (!expanded || loadedOnce) {
      return;
    }
    void loadItems();
  }, [expanded, loadedOnce]);

  async function handleDiscard(progressId) {
    try {
      await discardMockExamProgress(progressId);
      await loadItems();
    } catch (error) {
      setMessage(getApiError(error, "放弃进度失败"));
    }
  }

  return (
    <div className="mockexam-panel">
      <div className={`mockexam-panel-head ${expanded ? "" : "mockexam-panel-head-collapsed"}`}>
        <div>
          <h3>最近活动</h3>
          <p>这里保留未交卷的模考，保存后可以继续作答。</p>
        </div>
        <div className="mockexam-panel-head-actions">
          <span className="mockexam-panel-badge">
            <Clock3 size={22} strokeWidth={2.1} />
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
              <InlineLoading message="正在准备最近活动" size="sm" />
            </div>
          ) : null}

          {!loading && message ? <div className="mockexam-inline-note">{message}</div> : null}

          {!loading && !message ? (
            <div className="mockexam-history-list">
              {items.map((item) => (
                <article key={item.progress_id} className="mockexam-history-card">
                  <div className="mockexam-history-copy">
                    <div className="mockexam-history-tags">
                      <span>未完成</span>
                      <span>{item.source_kind === "paper_set" ? "组合试卷" : "单张试卷"}</span>
                      {item.exam_content ? <span>{item.exam_content}</span> : null}
                    </div>
                    <h4>{item.title}</h4>
                    <p>
                      已答 {item.answered_count || 0} / {item.total_questions || 0}
                      {item.current_question_no ? ` · 停在 ${item.current_question_no}` : ""}
                      {item.last_active_time ? ` · ${formatTime(item.last_active_time)}` : ""}
                    </p>
                  </div>

                  <div className="mockexam-history-actions">
                    <button
                      type="button"
                      className="mockexam-secondary-button"
                      onClick={() => navigate(`/mockexam/run/progress/${item.progress_id}`)}
                    >
                      <PlayCircle size={15} strokeWidth={2.1} />
                      <span>继续作答</span>
                    </button>
                    <button
                      type="button"
                      className="mockexam-tertiary-button"
                      onClick={() => void handleDiscard(item.progress_id)}
                    >
                      <Trash2 size={15} strokeWidth={2.1} />
                      <span>放弃</span>
                    </button>
                  </div>
                </article>
              ))}

              {!items.length ? <div className="mockexam-beta-empty">暂无未完成试卷</div> : null}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
