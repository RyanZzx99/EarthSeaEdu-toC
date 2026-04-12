import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, Heart, LoaderCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMockExamFavorites } from "../api/mockexam";

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

export default function MockExamFavoritesPanel() {
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

    async function loadItems() {
      try {
        setLoading(true);
        const response = await getMockExamFavorites({ limit: 20 });
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
        setMessage(getApiError(error, "收藏列表加载失败"));
      } finally {
        if (active) {
          setLoading(false);
          setLoadedOnce(true);
        }
      }
    }

    void loadItems();
    return () => {
      active = false;
    };
  }, [expanded, loadedOnce]);

  return (
    <div className="mockexam-panel">
      <div className={`mockexam-panel-head ${expanded ? "" : "mockexam-panel-head-collapsed"}`}>
        <div>
          <h3>收藏夹</h3>
          <p>收藏的题目会保留阅读文章或听力材料的上下文。</p>
        </div>
        <div className="mockexam-panel-head-actions">
          <span className="mockexam-panel-badge">
            <Heart size={22} strokeWidth={2.1} />
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
              <LoaderCircle size={16} strokeWidth={2.1} className="spin" /> 正在加载收藏题目...
            </div>
          ) : null}

          {!loading && message ? <div className="mockexam-inline-note">{message}</div> : null}

          {!loading && !message ? (
            <div className="mockexam-history-list">
              {items.map((item) => (
                <article key={item.exam_question_id} className="mockexam-history-card">
                  <div className="mockexam-history-copy">
                    <div className="mockexam-history-tags">
                      <span>{item.exam_content || "IELTS"}</span>
                      {item.question_no ? <span>Q{item.question_no}</span> : null}
                    </div>
                    <h4>{item.paper_title}</h4>
                    <p>{item.preview_text || item.question_id}</p>
                    <p>{formatTime(item.create_time)}</p>
                  </div>

                  <div className="mockexam-history-actions">
                    <button
                      type="button"
                      className="mockexam-secondary-button"
                      onClick={() => navigate(`/mockexam/questions/${item.exam_question_id}`)}
                    >
                      查看题目
                    </button>
                  </div>
                </article>
              ))}

              {!items.length ? <div className="mockexam-beta-empty">暂无收藏题目</div> : null}
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
