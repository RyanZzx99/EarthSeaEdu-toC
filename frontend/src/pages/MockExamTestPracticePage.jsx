import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { Star } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamEntityFavorites,
  getMockExamPaperSets,
  getMockExamProgresses,
  getMockExamSubmissions,
  toggleMockExamPaperSetFavorite,
} from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { buildFavoriteSummarySets } from "../mockexam/favoriteUtils";
import {
  estimatePaperDuration,
  formatDateTime,
  getApiError,
  sortByTimeDesc,
} from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";

function buildProgressMap(items) {
  return sortByTimeDesc(items || [], "last_active_time").reduce((result, item) => {
    if (item?.source_kind !== "paper_set" || !item?.paper_set_id) {
      return result;
    }
    if (!result.has(item.paper_set_id)) {
      result.set(item.paper_set_id, item);
    }
    return result;
  }, new Map());
}

function buildSubmissionMap(items) {
  return sortByTimeDesc(items || [], "create_time").reduce((result, item) => {
    if (item?.source_kind !== "paper_set" || !item?.paper_set_id) {
      return result;
    }
    if (!result.has(item.paper_set_id)) {
      result.set(item.paper_set_id, item);
    }
    return result;
  }, new Map());
}

function buildPaperSetStatus(item, progressMap, submissionMap) {
  const progress = progressMap.get(item.mockexam_paper_set_id);
  if (progress) {
    return {
      type: "in_progress",
      label: "进行中",
      progress,
      submission: null,
    };
  }

  const submission = submissionMap.get(item.mockexam_paper_set_id);
  if (submission) {
    return {
      type: "completed",
      label: "已完成",
      progress: null,
      submission,
    };
  }

  return {
    type: "not_started",
    label: "未开始",
    progress: null,
    submission: null,
  };
}

export default function MockExamTestPracticePage() {
  const navigate = useNavigate();
  const [paperSets, setPaperSets] = useState([]);
  const [progressItems, setProgressItems] = useState([]);
  const [submissionItems, setSubmissionItems] = useState([]);
  const [favoritePaperSetIds, setFavoritePaperSetIds] = useState(new Set());
  const [busyPaperSetIds, setBusyPaperSetIds] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const [paperSetResponse, progressResponse, submissionResponse, favoriteResponse] = await Promise.all([
          getMockExamPaperSets({ exam_category: "IELTS" }),
          getMockExamProgresses({ limit: 50 }),
          getMockExamSubmissions({ limit: 50 }),
          getMockExamEntityFavorites({ limit: 200 }),
        ]);

        if (!active) {
          return;
        }

        const favoriteSummary = buildFavoriteSummarySets(favoriteResponse.data?.items || []);
        setPaperSets(paperSetResponse.data?.items || []);
        setProgressItems(progressResponse.data?.items || []);
        setSubmissionItems(submissionResponse.data?.items || []);
        setFavoritePaperSetIds(new Set(favoriteSummary.paperSetIds));
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setPaperSets([]);
        setProgressItems([]);
        setSubmissionItems([]);
        setFavoritePaperSetIds(new Set());
        setMessage(getApiError(error, "试卷练习列表加载失败，请稍后重试。"));
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
  }, []);

  const progressMap = useMemo(() => buildProgressMap(progressItems), [progressItems]);
  const submissionMap = useMemo(() => buildSubmissionMap(submissionItems), [submissionItems]);

  async function handleTogglePaperSetFavorite(mockexamPaperSetId) {
    const nextState = !favoritePaperSetIds.has(mockexamPaperSetId);
    setBusyPaperSetIds((previous) => new Set(previous).add(mockexamPaperSetId));
    try {
      await toggleMockExamPaperSetFavorite(mockexamPaperSetId, { is_favorite: nextState });
      setFavoritePaperSetIds((previous) => {
        const next = new Set(previous);
        if (nextState) {
          next.add(mockexamPaperSetId);
        } else {
          next.delete(mockexamPaperSetId);
        }
        return next;
      });
      setMessage("");
    } catch (error) {
      setMessage(getApiError(error, "试卷收藏状态更新失败，请稍后重试。"));
    } finally {
      setBusyPaperSetIds((previous) => {
        const next = new Set(previous);
        next.delete(mockexamPaperSetId);
        return next;
      });
    }
  }

  return (
    <div className="home-shell mockexam-mode-page">
      <MockExamModeHeader activeMode="practice" />

      <main className="mockexam-test-list-main">
        <div className="mockexam-test-list-container">
          <motion.div
            className="mockexam-test-list-title"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <h1>试卷练习选卷</h1>
            <p>按整套试卷练习，可继续未完成进度。</p>
          </motion.div>

          {loading ? (
            <div className="mockexam-page-note">
              <InlineLoading message="正在准备试卷练习列表" />
            </div>
          ) : null}
          {!loading && message ? <div className="mockexam-page-note">{message}</div> : null}

          <motion.div
            className="mockexam-test-list-stack"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            {!loading &&
              paperSets.map((item, index) => {
                const status = buildPaperSetStatus(item, progressMap, submissionMap);
                const duration = estimatePaperDuration(
                  item.exam_content,
                  Math.max(item.paper_count || 1, 1)
                );
                const isFavorited = favoritePaperSetIds.has(item.mockexam_paper_set_id);
                const isBusy = busyPaperSetIds.has(item.mockexam_paper_set_id);

                return (
                  <motion.article
                    key={item.mockexam_paper_set_id}
                    className="mockexam-test-list-card"
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.4, delay: 0.3 + index * 0.05 }}
                  >
                    <div className="mockexam-test-list-card-copy">
                      <h2>{item.set_name}</h2>

                      <div className="mockexam-test-list-meta">
                        <span>{item.paper_count || 0}张试卷</span>
                        <span>|</span>
                        <span>预计{duration}分钟</span>
                        {status.type === "in_progress" && status.progress ? (
                          <>
                            <span>|</span>
                            <span>
                              已完成 {status.progress.answered_count || 0}/
                              {status.progress.total_questions || 0}
                            </span>
                          </>
                        ) : null}
                        {status.type === "completed" && status.submission ? (
                          <>
                            <span>|</span>
                            <span>最近正确率 {status.submission.score_percent ?? "--"}%</span>
                          </>
                        ) : null}
                      </div>

                      <div className="mockexam-test-list-badges">
                        <span className={`mockexam-status-pill ${status.type}`}>{status.label}</span>
                        {status.type === "in_progress" && status.progress?.last_active_time ? (
                          <span className="mockexam-test-list-time">
                            上次保存于 {formatDateTime(status.progress.last_active_time)}
                          </span>
                        ) : null}
                        {status.type === "completed" && status.submission?.create_time ? (
                          <span className="mockexam-test-list-time">
                            最近完成于 {formatDateTime(status.submission.create_time)}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    <div className="mockexam-test-list-actions">
                      <button
                        type="button"
                        className={`mockexam-entity-favorite-btn${isFavorited ? " is-starred" : ""}`}
                        onClick={() => handleTogglePaperSetFavorite(item.mockexam_paper_set_id)}
                        disabled={isBusy}
                        title={isFavorited ? "取消收藏试卷" : "收藏试卷"}
                      >
                        <Star size={18} fill={isFavorited ? "currentColor" : "none"} />
                        <span>{isBusy ? "处理中..." : "收藏试卷"}</span>
                      </button>

                      {status.type === "not_started" ? (
                        <button
                          type="button"
                          className="mockexam-test-list-button primary"
                          onClick={() =>
                            navigate(`/mockexam/practice/test/${item.mockexam_paper_set_id}/start`)
                          }
                        >
                          开始练习
                        </button>
                      ) : null}

                      {status.type === "in_progress" ? (
                        <>
                          <button
                            type="button"
                            className="mockexam-test-list-button primary"
                            onClick={() =>
                              navigate(`/mockexam/practice/test/${item.mockexam_paper_set_id}/continue`)
                            }
                          >
                            继续练习
                          </button>
                          <button
                            type="button"
                            className="mockexam-test-list-button secondary"
                            onClick={() =>
                              navigate(`/mockexam/practice/test/${item.mockexam_paper_set_id}/restart`)
                            }
                          >
                            重新开始
                          </button>
                        </>
                      ) : null}

                      {status.type === "completed" ? (
                        <>
                          <button
                            type="button"
                            className="mockexam-test-list-button secondary"
                            onClick={() =>
                              navigate(`/mockexam/practice/test/${item.mockexam_paper_set_id}/results`)
                            }
                          >
                            查看结果
                          </button>
                          <button
                            type="button"
                            className="mockexam-test-list-button secondary"
                            onClick={() =>
                              navigate(`/mockexam/practice/test/${item.mockexam_paper_set_id}/retry`)
                            }
                          >
                            再次练习
                          </button>
                        </>
                      ) : null}
                    </div>
                  </motion.article>
                );
              })}

            {!loading && !paperSets.length ? (
              <div className="mockexam-page-note">当前还没有可用的组合试卷。</div>
            ) : null}
          </motion.div>
        </div>
      </main>
    </div>
  );
}
