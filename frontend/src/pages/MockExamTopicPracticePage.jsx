import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import { CheckCircle2, Circle, Clock } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getMockExamPapers } from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
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
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function loadPapers() {
      try {
        setLoading(true);
        const response = await getMockExamPapers({
          exam_category: "IELTS",
          ...(subject === "All" ? {} : { exam_content: subject }),
        });

        if (!active) {
          return;
        }

        setPapers(response.data?.items || []);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setPapers([]);
        setMessage(getApiError(error, "专项练习题库加载失败，请稍后重试。"));
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
            <p>按科目筛选</p>
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
                <h2>推荐专项</h2>
              </div>

              {loading ? (
                <div className="mockexam-page-note">
                  <InlineLoading message="正在准备推荐专项" />
                </div>
              ) : null}

              {!loading && message ? <div className="mockexam-page-note">{message}</div> : null}

              <div className="mockexam-paper-list mockexam-topic-scroll">
                {!loading &&
                  !message &&
                  papers.map((item, index) => (
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
                          <span>{subject === "Listening" ? "40题" : "40题"}</span>
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

                      <button
                        type="button"
                        className={`mockexam-topic-start-button${index === 0 ? " primary" : ""}`}
                        onClick={() => navigate(`/mockexam/run/paper/${item.exam_paper_id}`)}
                      >
                        开始练习
                      </button>
                    </motion.article>
                  ))}

                {!loading && !message && !papers.length ? (
                  <div className="mockexam-page-note">当前条件下暂无可用专项。</div>
                ) : null}
              </div>
            </motion.section>
          </div>
        </div>
      </main>
    </div>
  );
}
