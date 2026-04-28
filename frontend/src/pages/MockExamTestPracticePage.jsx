import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { Star } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  getMockExamActOptions,
  getMockExamActPapers,
  getMockExamAlevelOptions,
  getMockExamAlevelPapers,
  getMockExamEntityFavorites,
  getMockExamOptions,
  getMockExamPaperSets,
  getMockExamProgresses,
  getMockExamSubmissions,
  toggleMockExamPaperFavorite,
  toggleMockExamPaperSetFavorite,
} from "../api/mockexam";
import { InlineLoading } from "../components/LoadingPage";
import MockExamExamFilterSections from "../components/MockExamExamFilterSections";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { buildFavoriteSummarySets } from "../mockexam/favoriteUtils";
import {
  MOCK_EXAM_ALL_CONTENT,
  MOCK_EXAM_CATEGORY_ACT,
  MOCK_EXAM_CATEGORY_ALEVEL,
  MOCK_EXAM_CATEGORY_IELTS,
  estimatePaperDuration,
  formatDateTime,
  getApiError,
  getExamContentOptions,
  getPaperContentLabel,
  getPaperMetricLabel,
  isAlevelCategory,
  isActCategory,
  isSinglePaperExamCategory,
  mergeMockExamOptions,
  sortByTimeDesc,
} from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";

function getStatusMapKey(item, sourceKind) {
  const rawId = sourceKind === "paper" ? item?.exam_paper_id : item?.paper_set_id;
  const id = Number(rawId || 0);
  return Number.isFinite(id) && id !== 0 ? id : null;
}

function buildProgressMap(items, sourceKind) {
  return sortByTimeDesc(items || [], "last_active_time").reduce((result, item) => {
    const key = getStatusMapKey(item, sourceKind);
    if (item?.source_kind !== sourceKind || key === null) {
      return result;
    }
    if (!result.has(key)) {
      result.set(key, item);
    }
    return result;
  }, new Map());
}

function buildSubmissionMap(items, sourceKind) {
  return sortByTimeDesc(items || [], "create_time").reduce((result, item) => {
    const key = getStatusMapKey(item, sourceKind);
    if (item?.source_kind !== sourceKind || key === null) {
      return result;
    }
    if (!result.has(key)) {
      result.set(key, item);
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

function buildPaperStatus(item, progressMap, submissionMap) {
  const examPaperId = Number(item?.exam_paper_id || 0);
  const progress = progressMap.get(examPaperId);
  if (progress) {
    return {
      type: "in_progress",
      label: "进行中",
      progress,
      submission: null,
    };
  }

  const submission = submissionMap.get(examPaperId);
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

function getDurationMinutes(item, multiplier = 1) {
  const durationSeconds = Number(item?.duration_seconds || 0);
  if (durationSeconds > 0) {
    return Math.ceil(durationSeconds / 60);
  }
  return estimatePaperDuration(item.exam_content, multiplier);
}

function buildRunPath(examPaperId, examCategory, examContent) {
  const params = new URLSearchParams();
  if (isActCategory(examCategory) && examContent && examContent !== MOCK_EXAM_ALL_CONTENT) {
    params.set("examContent", examContent);
  }
  const query = params.toString();
  return `/mockexam/run/paper/${examPaperId}${query ? `?${query}` : ""}`;
}

function normalizeExamCategoryParam(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if ([MOCK_EXAM_CATEGORY_IELTS, MOCK_EXAM_CATEGORY_ALEVEL, MOCK_EXAM_CATEGORY_ACT].includes(normalized)) {
    return normalized;
  }
  return MOCK_EXAM_CATEGORY_IELTS;
}

function buildUserStateParams(examCategory, examContent, limit = 50) {
  const params = { exam_category: examCategory, limit };
  if (examContent && examContent !== MOCK_EXAM_ALL_CONTENT) {
    params.exam_content = examContent;
  }
  return params;
}

export default function MockExamTestPracticePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedExamCategory = normalizeExamCategoryParam(searchParams.get("examCategory"));
  const requestedPaperId = Number(searchParams.get("paperId") || 0);
  const highlightedPaperId = Number.isFinite(requestedPaperId) && requestedPaperId !== 0 ? requestedPaperId : null;
  const [examCategory, setExamCategory] = useState(requestedExamCategory);
  const [examContent, setExamContent] = useState(searchParams.get("examContent") || MOCK_EXAM_ALL_CONTENT);
  const [options, setOptions] = useState({});
  const [paperSets, setPaperSets] = useState([]);
  const [progressItems, setProgressItems] = useState([]);
  const [submissionItems, setSubmissionItems] = useState([]);
  const [favoritePaperIds, setFavoritePaperIds] = useState(new Set());
  const [favoritePaperSetIds, setFavoritePaperSetIds] = useState(new Set());
  const [busyPaperIds, setBusyPaperIds] = useState(new Set());
  const [busyPaperSetIds, setBusyPaperSetIds] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (searchParams.has("examCategory")) {
      setExamCategory(requestedExamCategory);
      if (!searchParams.has("examContent")) {
        setExamContent(MOCK_EXAM_ALL_CONTENT);
      }
    }
    if (searchParams.has("examContent")) {
      setExamContent(searchParams.get("examContent") || MOCK_EXAM_ALL_CONTENT);
    }
  }, [requestedExamCategory, searchParams]);

  useEffect(() => {
    let active = true;

    async function loadOptions() {
      try {
        const [baseResponse, alevelResponse, actResponse] = await Promise.all([
          getMockExamOptions(),
          getMockExamAlevelOptions(),
          getMockExamActOptions(),
        ]);
        if (active) {
          setOptions(mergeMockExamOptions(baseResponse.data || {}, alevelResponse.data || {}, actResponse.data || {}));
        }
      } catch {
        if (active) {
          setOptions(mergeMockExamOptions({}, {}, {}));
        }
      }
    }

    void loadOptions();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const paperParams = examContent === MOCK_EXAM_ALL_CONTENT ? {} : { exam_content: examContent };
        const paperListRequest = isActCategory(examCategory)
          ? getMockExamActPapers(paperParams)
          : isAlevelCategory(examCategory)
            ? getMockExamAlevelPapers(paperParams)
            : getMockExamPaperSets({
              exam_category: MOCK_EXAM_CATEGORY_IELTS,
              ...paperParams,
            });
        const [paperSetResponse, progressResponse, submissionResponse, favoriteResponse] = await Promise.all([
          paperListRequest,
          getMockExamProgresses(buildUserStateParams(examCategory, examContent, 50)),
          getMockExamSubmissions(buildUserStateParams(examCategory, examContent, 50)),
          getMockExamEntityFavorites({ exam_category: examCategory, limit: 200 }),
        ]);

        if (!active) {
          return;
        }

        const favoriteSummary = buildFavoriteSummarySets(favoriteResponse.data?.items || []);
        setPaperSets(paperSetResponse.data?.items || []);
        setProgressItems(progressResponse.data?.items || []);
        setSubmissionItems(submissionResponse.data?.items || []);
        setFavoritePaperIds(new Set(favoriteSummary.paperIds));
        setFavoritePaperSetIds(new Set(favoriteSummary.paperSetIds));
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setPaperSets([]);
        setProgressItems([]);
        setSubmissionItems([]);
        setFavoritePaperIds(new Set());
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
  }, [examCategory, examContent]);

  const sourceKind = isSinglePaperExamCategory(examCategory) ? "paper" : "paper_set";
  const progressMap = useMemo(() => buildProgressMap(progressItems, sourceKind), [progressItems, sourceKind]);
  const submissionMap = useMemo(() => buildSubmissionMap(submissionItems, sourceKind), [submissionItems, sourceKind]);
  const examCategoryOptions = options.exam_category_options || [
    MOCK_EXAM_CATEGORY_IELTS,
    MOCK_EXAM_CATEGORY_ALEVEL,
    MOCK_EXAM_CATEGORY_ACT,
  ];
  const contentOptions = getExamContentOptions(options, examCategory);
  const contentOptionKey = contentOptions.join("|");

  useEffect(() => {
    if (isActCategory(examCategory) && examContent === MOCK_EXAM_ALL_CONTENT && contentOptions.length) {
      setExamContent(contentOptions[0]);
    }
  }, [contentOptionKey, examCategory, examContent]);

  useEffect(() => {
    if (loading || !highlightedPaperId) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      document
        .querySelector(`[data-exam-paper-id="${highlightedPaperId}"]`)
        ?.scrollIntoView({ block: "center", behavior: "smooth" });
    }, 80);
    return () => window.clearTimeout(timer);
  }, [highlightedPaperId, loading, paperSets]);

  function handleExamCategoryChange(nextCategory) {
    setExamCategory(nextCategory);
    const nextContentOptions = getExamContentOptions(options, nextCategory);
    setExamContent(isActCategory(nextCategory) && nextContentOptions.length ? nextContentOptions[0] : MOCK_EXAM_ALL_CONTENT);
  }

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
      setMessage(getApiError(error, "试卷收藏状态更新失败，请稍后重试。"));
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

          <motion.div
            className="mockexam-filter-card mockexam-test-filter-card"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.1 }}
          >
            <h2>筛选条件</h2>
            <MockExamExamFilterSections
              examCategory={examCategory}
              examCategoryOptions={examCategoryOptions}
              examContent={examContent}
              contentOptions={contentOptions}
              onExamCategoryChange={handleExamCategoryChange}
              onExamContentChange={setExamContent}
              allowAllContent={!isActCategory(examCategory)}
            />
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
                if (isSinglePaperExamCategory(examCategory)) {
                  const examPaperId = Number(item.exam_paper_id || 0);
                  const status = buildPaperStatus(item, progressMap, submissionMap);
                  const duration = getDurationMinutes(item);
                  const isFavorited = favoritePaperIds.has(examPaperId);
                  const isBusy = busyPaperIds.has(examPaperId);

                  return (
                    <motion.article
                      key={examPaperId || item.paper_code || index}
                      className={`mockexam-test-list-card${
                        highlightedPaperId === examPaperId ? " is-highlighted" : ""
                      }`}
                      data-exam-paper-id={examPaperId || undefined}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.4, delay: 0.3 + index * 0.05 }}
                    >
                      <div className="mockexam-test-list-card-copy">
                        <h2>{item.paper_name || item.paper_code}</h2>

                        <div className="mockexam-test-list-meta">
                          <span>{getPaperMetricLabel(item)}</span>
                          <span>|</span>
                          <span>预计{duration}分钟</span>
                          <span>|</span>
                          <span>{getPaperContentLabel(item.exam_content)}</span>
                          {status.type === "in_progress" && status.progress ? (
                            <>
                              <span>|</span>
                              <span>
                                已完成{status.progress.answered_count || 0}/
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
                          onClick={() => handleTogglePaperFavorite(examPaperId)}
                          disabled={isBusy || !examPaperId}
                          title={isFavorited ? "取消收藏试卷" : "收藏试卷"}
                        >
                          <Star size={18} fill={isFavorited ? "currentColor" : "none"} />
                          <span>{isBusy ? "处理中..." : "收藏试卷"}</span>
                        </button>

                        {status.type === "not_started" ? (
                          <button
                            type="button"
                            className="mockexam-test-list-button primary"
                            disabled={!examPaperId}
                            onClick={() => navigate(buildRunPath(examPaperId, examCategory, examContent))}
                          >
                            开始练习
                          </button>
                        ) : null}

                        {status.type === "in_progress" ? (
                          <>
                            <button
                              type="button"
                              className="mockexam-test-list-button primary"
                              onClick={() => navigate(`/mockexam/run/progress/${status.progress.progress_id}`)}
                            >
                              继续练习
                            </button>
                            <button
                              type="button"
                              className="mockexam-test-list-button secondary"
                              disabled={!examPaperId}
                              onClick={() => navigate(buildRunPath(examPaperId, examCategory, examContent))}
                            >
                              重新练习
                            </button>
                          </>
                        ) : null}

                        {status.type === "completed" ? (
                          <>
                            {status.submission?.submission_id ? (
                              <button
                                type="button"
                                className="mockexam-test-list-button secondary"
                                onClick={() => navigate(`/mockexam/results/${status.submission.submission_id}`)}
                              >
                                查看结果
                              </button>
                            ) : null}
                            <button
                              type="button"
                              className="mockexam-test-list-button secondary"
                              disabled={!examPaperId}
                              onClick={() => navigate(buildRunPath(examPaperId, examCategory, examContent))}
                            >
                              再次练习
                            </button>
                          </>
                        ) : null}
                      </div>
                    </motion.article>
                  );
                }

                const status = buildPaperSetStatus(item, progressMap, submissionMap);
                const duration = getDurationMinutes(item, Math.max(item.paper_count || 1, 1));
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
              <div className="mockexam-page-note">
                {isSinglePaperExamCategory(examCategory)
                  ? `当前条件下暂无可用 ${getPaperContentLabel(examCategory)} 试卷。`
                  : "当前还没有可用的组合试卷。"}
              </div>
            ) : null}
          </motion.div>
        </div>
      </main>
    </div>
  );
}
