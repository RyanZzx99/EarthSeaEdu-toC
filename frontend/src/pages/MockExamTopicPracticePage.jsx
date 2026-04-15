import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { CheckCircle2, Circle, Clock, Star } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamEntityFavorites,
  getMockExamPapers,
  toggleMockExamPaperFavorite,
} from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { buildFavoriteSummarySets } from "../mockexam/favoriteUtils";
import {
  estimatePaperDuration,
  getApiError,
  getPaperContentLabel,
} from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";

function buildPaperTags(item, selectedSubject) {
  const subjectLabel =
    selectedSubject === "All"
      ? getPaperContentLabel(item.exam_content)
      : selectedSubject === "Listening"
        ? "听力"
        : "阅读";
  return [subjectLabel, item.bank_name].filter(Boolean);
}

export default function MockExamTopicPracticePage() {
  const navigate = useNavigate();
  const [subject, setSubject] = useState("All");
  const [papers, setPapers] = useState([]);
  const [favoritePaperIds, setFavoritePaperIds] = useState(new Set());
  const [busyPaperIds, setBusyPaperIds] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function loadPapers() {
      try {
        setLoading(true);
        const [paperResponse, favoriteResponse] = await Promise.all([
          getMockExamPapers({
            exam_category: "IELTS",
            ...(subject === "All" ? {} : { exam_content: subject }),
          }),
          getMockExamEntityFavorites({ limit: 200 }),
        ]);

        if (!active) {
          return;
        }

        const favoriteSummary = buildFavoriteSummarySets(favoriteResponse.data?.items || []);
        setPapers(Array.isArray(paperResponse.data?.items) ? paperResponse.data.items : []);
        setFavoritePaperIds(new Set(favoriteSummary.paperIds));
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setPapers([]);
        setFavoritePaperIds(new Set());
        setMessage(getApiError(error, "专项练习题包加载失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadPapers();
    return () => {
      active = false;
    };
  }, [subject]);

  async function handleTogglePaperFavorite(examPaperId) {
    const nextState = !favoritePaperIds.has(examPaperId);
    setBusyPaperIds((previous) => new Set(previous).add(examPaperId));
    try {
      await toggleMockExamPaperFavorite(examPaperId, { is_favorite: nextState });
      setFavoritePaperIds((previous) => {
        const next = new Set(previous);
        if (nextState) {
          next.add(examPaperId);
        } else {
          next.delete(examPaperId);
        }
        return next;
      });
      setMessage("");
    } catch (error) {
      setMessage(getApiError(error, "题包收藏状态更新失败，请稍后重试。"));
    } finally {
      setBusyPaperIds((previous) => {
        const next = new Set(previous);
        next.delete(examPaperId);
        return next;
      });
    }
  }

  return (
    <div className="home-shell mockexam-mode-page">
      <MockExamModeHeader activeMode="practice" />

      <main className="mockexam-topic-page-main">
        <div className="mockexam-topic-page-container">
          <motion.div
            className="mockexam-topic-page-title"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1>专项练习选题</h1>
            <p>按科目筛选题包。</p>
          </motion.div>

          <div className="mockexam-topic-layout">
            <motion.aside
              className="mockexam-filter-card mockexam-topic-filter-card"
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
            >
              <h2>筛选条件</h2>

              <section className="mockexam-filter-section">
                <h3>科目</h3>
                <button
                  type="button"
                  className={`mockexam-radio-row${subject === "All" ? " active" : ""}`}
                  onClick={() => setSubject("All")}
                >
                  <span className="mockexam-check-icon">
                    {subject === "All" ? <CheckCircle2 size={16} /> : <Circle size={16} />}
                  </span>
                  <span>全部</span>
                </button>
                <button
                  type="button"
                  className={`mockexam-radio-row${subject === "Reading" ? " active" : ""}`}
                  onClick={() => setSubject("Reading")}
                >
                  <span className="mockexam-check-icon">
                    {subject === "Reading" ? <CheckCircle2 size={16} /> : <Circle size={16} />}
                  </span>
                  <span>阅读</span>
                </button>
                <button
                  type="button"
                  className={`mockexam-radio-row${subject === "Listening" ? " active" : ""}`}
                  onClick={() => setSubject("Listening")}
                >
                  <span className="mockexam-check-icon">
                    {subject === "Listening" ? <CheckCircle2 size={16} /> : <Circle size={16} />}
                  </span>
                  <span>听力</span>
                </button>
              </section>
            </motion.aside>

            <motion.section
              className="mockexam-paper-list-shell mockexam-topic-shell"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="mockexam-topic-list-header">
                <h2>推荐题包</h2>
              </div>

              {loading ? (
                <div className="mockexam-page-note">
                  <InlineLoading message="正在准备专项题包" />
                </div>
              ) : null}

              {!loading && message ? <div className="mockexam-page-note">{message}</div> : null}

              <div className="mockexam-paper-list mockexam-topic-scroll">
                {!loading &&
                  papers.map((item, index) => {
                    const isFavorited = favoritePaperIds.has(item.exam_paper_id);
                    const isBusy = busyPaperIds.has(item.exam_paper_id);
                    return (
                      <motion.article
                        key={item.exam_paper_id}
                        className="mockexam-paper-item mockexam-topic-paper-item"
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.35, delay: 0.3 + index * 0.05 }}
                      >
                        <div className="mockexam-paper-item-copy">
                          <h3>{item.paper_name}</h3>
                          <div className="mockexam-paper-item-meta">
                            <span>40题</span>
                            <span>|</span>
                            <span className="mockexam-topic-meta-clock">
                              <Clock size={14} />
                              预计{estimatePaperDuration(item.exam_content)}分钟
                            </span>
                            <span>|</span>
                            <span>{getPaperContentLabel(item.exam_content)}</span>
                          </div>
                          <div className="mockexam-paper-item-tags">
                            {buildPaperTags(item, subject).map((tag) => (
                              <span key={`${item.exam_paper_id}-${tag}`}>{tag}</span>
                            ))}
                          </div>
                        </div>

                        <div className="mockexam-topic-action-row">
                          <button
                            type="button"
                            className={`mockexam-entity-favorite-btn${isFavorited ? " is-starred" : ""}`}
                            onClick={() => handleTogglePaperFavorite(item.exam_paper_id)}
                            disabled={isBusy}
                            title={isFavorited ? "取消收藏题包" : "收藏题包"}
                          >
                            <Star size={18} fill={isFavorited ? "currentColor" : "none"} />
                            <span>{isBusy ? "处理中..." : "收藏题包"}</span>
                          </button>

                          <button
                            type="button"
                            className={`mockexam-topic-start-button${index === 0 ? " primary" : ""}`}
                            onClick={() => navigate(`/mockexam/run/paper/${item.exam_paper_id}`)}
                          >
                            开始练习
                          </button>
                        </div>
                      </motion.article>
                    );
                  })}

                {!loading && !papers.length ? (
                  <div className="mockexam-page-note">当前条件下暂无可用题包。</div>
                ) : null}
              </div>
            </motion.section>
          </div>
        </div>
      </main>
    </div>
  );
}
