import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  Calendar,
  CheckCircle2,
  Clock,
  Eye,
  Filter,
  History,
  RotateCcw,
  Star,
  Target,
  TrendingUp,
  X,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamEntityFavorites,
  getMockExamSubmissions,
  toggleMockExamPaperFavorite,
  toggleMockExamPaperSetFavorite,
} from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { buildFavoriteSummarySets } from "../mockexam/favoriteUtils";
import { getApiError } from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";
import "../mockexam/historyPage.css";

const LIBRARY_TABS = [
  { key: "mistakes", label: "错题本", path: "/mockexam/mistakes" },
  { key: "history", label: "练习历史", path: "/mockexam/history" },
  { key: "favorites", label: "收藏夹", path: "/mockexam/favorites" },
];

function formatExamContentLabel(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (normalized === "reading") {
    return "阅读";
  }
  if (normalized === "listening") {
    return "听力";
  }
  if (!normalized) {
    return "未分类";
  }
  return String(value);
}

function formatModeLabel(value) {
  return value === "mock" ? "模考模式" : "练习模式";
}

function formatDateLabel(value) {
  if (!value) {
    return "--";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "--";
  }
  return date.toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

function formatPercent(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "--";
  }
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1);
}

function formatMinutes(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return "--";
  }
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1);
}

function getRecordTimestamp(value) {
  const timestamp = new Date(value || 0).getTime();
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function buildRetryPath(record) {
  if (record.sourceKind === "paper_set" && record.paperSetId) {
    return `/mockexam/run/paper-set/${record.paperSetId}`;
  }
  if (record.examPaperId) {
    return `/mockexam/run/paper/${record.examPaperId}`;
  }
  return "";
}

export default function MockExamHistoryPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [filterSubject, setFilterSubject] = useState("all");
  const [filterMode, setFilterMode] = useState("all");
  const [sortBy, setSortBy] = useState("recent");
  const [items, setItems] = useState([]);
  const [favoritePaperIds, setFavoritePaperIds] = useState(new Set());
  const [favoritePaperSetIds, setFavoritePaperSetIds] = useState(new Set());
  const [busyKeys, setBusyKeys] = useState(new Set());

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const [submissionResponse, favoriteResponse] = await Promise.all([
          getMockExamSubmissions({ limit: 100 }),
          getMockExamEntityFavorites({ limit: 200 }),
        ]);
        if (!active) {
          return;
        }

        const favoriteSummary = buildFavoriteSummarySets(favoriteResponse.data?.items || []);
        setItems(Array.isArray(submissionResponse.data?.items) ? submissionResponse.data.items : []);
        setFavoritePaperIds(new Set(favoriteSummary.paperIds));
        setFavoritePaperSetIds(new Set(favoriteSummary.paperSetIds));
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setItems([]);
        setFavoritePaperIds(new Set());
        setFavoritePaperSetIds(new Set());
        setMessage(getApiError(error, "练习历史记录加载失败，请稍后重试。"));
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

  const historyRecords = useMemo(
    () =>
      items.map((item) => ({
        id: item.submission_id,
        title: item.title || "未命名练习",
        subject: formatExamContentLabel(item.exam_content),
        mode: "practice",
        modeLabel: formatModeLabel("practice"),
        completedDate: item.create_time,
        completedDateLabel: formatDateLabel(item.create_time),
        completedTimestamp: getRecordTimestamp(item.create_time),
        elapsedSeconds: Number(item.elapsed_seconds || 0),
        duration: Number(item.elapsed_seconds || 0) > 0 ? Number(item.elapsed_seconds || 0) / 60 : null,
        totalQuestions: Number(item.total_questions || 0),
        correctAnswers: Number(item.correct_count || 0),
        accuracy: Number.isFinite(Number(item.score_percent)) ? Number(item.score_percent) : null,
        sourceKind: item.source_kind || "paper",
        paperSetId: item.paper_set_id || null,
        examPaperId: item.exam_paper_id || null,
      })),
    [items]
  );

  const totalPractices = historyRecords.length;

  const avgAccuracy = useMemo(() => {
    const accuracyList = historyRecords
      .map((item) => item.accuracy)
      .filter((value) => Number.isFinite(value));
    if (!accuracyList.length) {
      return "--";
    }
    const average = accuracyList.reduce((sum, value) => sum + value, 0) / accuracyList.length;
    return formatPercent(average);
  }, [historyRecords]);

  const totalMinutes = useMemo(() => {
    const durationList = historyRecords
      .map((item) => Number(item.elapsedSeconds || 0) / 60)
      .filter((value) => Number.isFinite(value) && value > 0);
    if (!durationList.length) {
      return "--";
    }
    return formatMinutes(durationList.reduce((sum, value) => sum + value, 0));
  }, [historyRecords]);

  const subjectOptions = useMemo(() => {
    const values = new Set(historyRecords.map((item) => item.subject).filter(Boolean));
    return ["all", ...values];
  }, [historyRecords]);

  const filteredHistory = useMemo(() => {
    const filtered = historyRecords.filter((item) => {
      if (filterSubject !== "all" && item.subject !== filterSubject) {
        return false;
      }
      if (filterMode !== "all" && item.mode !== filterMode) {
        return false;
      }
      return true;
    });

    function compareByRecent(left, right) {
      return (right.completedTimestamp || 0) - (left.completedTimestamp || 0) || right.id - left.id;
    }

    if (sortBy === "accuracy") {
      return [...filtered].sort(
        (left, right) => (right.accuracy || 0) - (left.accuracy || 0) || compareByRecent(left, right)
      );
    }

    if (sortBy === "duration") {
      return [...filtered].sort(
        (left, right) => (right.elapsedSeconds || 0) - (left.elapsedSeconds || 0) || compareByRecent(left, right)
      );
    }

    return [...filtered].sort(compareByRecent);
  }, [filterMode, filterSubject, historyRecords, sortBy]);

  async function handleToggleFavorite(record) {
    const key = `${record.sourceKind}:${record.paperSetId || record.examPaperId || 0}`;
    const isPaperSet = record.sourceKind === "paper_set" && record.paperSetId;
    const currentState = isPaperSet
      ? favoritePaperSetIds.has(record.paperSetId)
      : favoritePaperIds.has(record.examPaperId);
    const nextState = !currentState;

    setBusyKeys((previous) => new Set(previous).add(key));
    try {
      if (isPaperSet) {
        await toggleMockExamPaperSetFavorite(record.paperSetId, { is_favorite: nextState });
        setFavoritePaperSetIds((previous) => {
          const next = new Set(previous);
          if (nextState) {
            next.add(record.paperSetId);
          } else {
            next.delete(record.paperSetId);
          }
          return next;
        });
      } else if (record.examPaperId) {
        await toggleMockExamPaperFavorite(record.examPaperId, { is_favorite: nextState });
        setFavoritePaperIds((previous) => {
          const next = new Set(previous);
          if (nextState) {
            next.add(record.examPaperId);
          } else {
            next.delete(record.examPaperId);
          }
          return next;
        });
      }
      setMessage("");
    } catch (error) {
      setMessage(getApiError(error, "收藏状态更新失败，请稍后重试。"));
    } finally {
      setBusyKeys((previous) => {
        const next = new Set(previous);
        next.delete(key);
        return next;
      });
    }
  }

  return (
    <div className="history-page-shell">
      <MockExamModeHeader
        activeMode="history"
        tabs={LIBRARY_TABS}
        backButton={{ label: "返回模拟考试", path: "/mockexam" }}
      />

      <main className="history-page-main">
        <div className="history-page-container">
          <motion.div
            className="history-page-header"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <div className="history-page-header-row">
              <History className="history-page-header-icon" />
              <h2>练习历史</h2>
            </div>
            <p>查看你的练习记录和进步轨迹。</p>
          </motion.div>

          <div className="history-summary-grid">
            <motion.div
              className="history-summary-card tone-indigo"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
            >
              <div className="history-summary-icon">
                <CheckCircle2 size={20} />
              </div>
              <div>
                <p>完成练习</p>
                <strong>{totalPractices}</strong>
              </div>
            </motion.div>

            <motion.div
              className="history-summary-card tone-green"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="history-summary-icon">
                <Target size={20} />
              </div>
              <div>
                <p>平均正确率</p>
                <strong>{avgAccuracy === "--" ? "--" : `${avgAccuracy}%`}</strong>
              </div>
            </motion.div>

            <motion.div
              className="history-summary-card tone-orange"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
            >
              <div className="history-summary-icon">
                <Clock size={20} />
              </div>
              <div>
                <p>累计练习时长</p>
                <strong>{totalMinutes === "--" ? "--" : `${totalMinutes}分钟`}</strong>
              </div>
            </motion.div>
          </div>

          <motion.section
            className="history-filter-card"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
          >
            <div className="history-filter-label">
              <Filter size={16} />
              <span>筛选：</span>
            </div>

            <select
              value={filterSubject}
              onChange={(event) => setFilterSubject(event.target.value)}
              className="history-filter-select"
            >
              <option value="all">全部科目</option>
              {subjectOptions
                .filter((option) => option !== "all")
                .map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
            </select>

            <select
              value={filterMode}
              onChange={(event) => setFilterMode(event.target.value)}
              className="history-filter-select"
            >
              <option value="all">全部模式</option>
              <option value="practice">练习模式</option>
              <option value="mock">模考模式</option>
            </select>

            <div className="history-filter-label subtle">
              <span>排序方式：</span>
            </div>

            <select
              value={sortBy}
              onChange={(event) => setSortBy(event.target.value)}
              className="history-filter-select"
            >
              <option value="recent">最近完成</option>
              <option value="accuracy">正确率</option>
              <option value="duration">用时</option>
            </select>

            {(filterSubject !== "all" || filterMode !== "all") ? (
              <button
                type="button"
                className="history-filter-clear"
                onClick={() => {
                  setFilterSubject("all");
                  setFilterMode("all");
                }}
              >
                <X size={14} />
                <span>清除筛选</span>
              </button>
            ) : null}
          </motion.section>

          {loading ? (
            <div className="history-inline-note">
              <InlineLoading message="正在加载练习历史" size="sm" />
            </div>
          ) : null}

          {!loading && message ? <div className="history-inline-note is-error">{message}</div> : null}

          {!loading ? (
            <div className="history-record-list">
              {filteredHistory.map((record, index) => {
                const retryPath = buildRetryPath(record);
                const isPaperSet = record.sourceKind === "paper_set" && record.paperSetId;
                const isFavorited = isPaperSet
                  ? favoritePaperSetIds.has(record.paperSetId)
                  : favoritePaperIds.has(record.examPaperId);
                const busyKey = `${record.sourceKind}:${record.paperSetId || record.examPaperId || 0}`;
                const isBusy = busyKeys.has(busyKey);

                return (
                  <motion.article
                    key={record.id}
                    className="history-record-card"
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.4, delay: 0.5 + index * 0.05 }}
                  >
                    <div className="history-record-body">
                      <div className="history-record-copy">
                        <div className="history-record-badges">
                          <span className="history-badge tone-blue">{record.modeLabel}</span>
                          <span className="history-badge tone-indigo">{record.subject}</span>
                        </div>

                        <h4>{record.title}</h4>

                        <div className="history-record-stats">
                          <div className="history-record-stat">
                            <p>正确率</p>
                            <div className="history-record-stat-value">
                              <TrendingUp
                                size={14}
                                className={
                                  (record.accuracy || 0) >= 80
                                    ? "tone-success"
                                    : (record.accuracy || 0) >= 60
                                      ? "tone-warning"
                                      : "tone-danger"
                                }
                              />
                              <span
                                className={
                                  (record.accuracy || 0) >= 80
                                    ? "tone-success"
                                    : (record.accuracy || 0) >= 60
                                      ? "tone-warning"
                                      : "tone-danger"
                                }
                              >
                                {record.accuracy == null ? "--" : `${formatPercent(record.accuracy)}%`}
                              </span>
                            </div>
                          </div>

                          <div className="history-record-stat">
                            <p>答对题数</p>
                            <span className="plain-value">
                              {record.correctAnswers}/{record.totalQuestions}
                            </span>
                          </div>

                          <div className="history-record-stat">
                            <p>用时</p>
                            <div className="history-record-stat-value">
                              <Clock size={14} className="icon-muted" />
                              <span className="plain-value">
                                {record.duration == null ? "未记录" : `${formatMinutes(record.duration)}分钟`}
                              </span>
                            </div>
                          </div>

                          <div className="history-record-stat">
                            <p>完成日期</p>
                            <div className="history-record-stat-value">
                              <Calendar size={14} className="icon-muted" />
                              <span className="plain-value">{record.completedDateLabel}</span>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div className="history-record-actions">
                        <button
                          type="button"
                          className={`mockexam-entity-favorite-btn${isFavorited ? " is-starred" : ""}`}
                          onClick={() => handleToggleFavorite(record)}
                          disabled={isBusy}
                        >
                          <Star size={18} fill={isFavorited ? "currentColor" : "none"} />
                          <span>{isBusy ? "处理中..." : isPaperSet ? "收藏试卷" : "收藏题包"}</span>
                        </button>

                        <button
                          type="button"
                          className="history-action-primary"
                          onClick={() => navigate(`/mockexam/results/${record.id}`)}
                        >
                          <Eye size={14} />
                          <span>查看结果</span>
                        </button>

                        <button
                          type="button"
                          className="history-action-secondary"
                          onClick={() => retryPath && navigate(retryPath)}
                          disabled={!retryPath}
                        >
                          <RotateCcw size={14} />
                          <span>再次练习</span>
                        </button>
                      </div>
                    </div>
                  </motion.article>
                );
              })}

              {!filteredHistory.length ? (
                <motion.div
                  className="history-empty-card"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.6 }}
                >
                  <History className="history-empty-icon" />
                  <h3>暂无练习记录</h3>
                  <p>开始你的第一次练习吧。</p>
                </motion.div>
              ) : null}
            </div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
