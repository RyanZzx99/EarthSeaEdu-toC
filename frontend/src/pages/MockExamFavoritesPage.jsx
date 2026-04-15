import React, { useCallback, useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  BookCopy,
  Calendar,
  Eye,
  Filter,
  FolderOpen,
  Layers3,
  Star,
  Trash2,
  X,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamEntityFavorites,
  getMockExamFavorites,
  toggleMockExamFavorite,
  toggleMockExamPaperFavorite,
  toggleMockExamPaperSetFavorite,
} from "../api/mockexam";
import MockExamModeHeader from "../components/MockExamModeHeader";
import { LoadingPage } from "../components/LoadingPage";
import { getApiError } from "../mockexam/pageHelpers";
import "../mockexam/favoritesPage.css";

const LIBRARY_TABS = [
  { key: "mistakes", label: "错题本", path: "/mockexam/mistakes" },
  { key: "history", label: "练习历史", path: "/mockexam/history" },
  { key: "favorites", label: "收藏夹", path: "/mockexam/favorites" },
];

function formatSubject(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (normalized === "reading") {
    return "阅读";
  }
  if (normalized === "listening") {
    return "听力";
  }
  if (normalized === "mixed") {
    return "混合";
  }
  return "未分类";
}

function formatDateLabel(value) {
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

function compareByLatestTime(left, right) {
  const leftTime = new Date(left?.latestTime || 0).getTime();
  const rightTime = new Date(right?.latestTime || 0).getTime();
  return rightTime - leftTime;
}

function buildEntityPath(entity) {
  if (entity.kind === "paper_set" && entity.paperSetId) {
    return `/mockexam/run/paper-set/${entity.paperSetId}?fromFavorites=1`;
  }
  if (entity.kind === "paper" && entity.examPaperId) {
    return `/mockexam/run/paper/${entity.examPaperId}?fromFavorites=1`;
  }
  if (entity.kind === "group") {
    const query = new URLSearchParams();
    if (entity.examGroupId) {
      query.set("groupId", String(entity.examGroupId));
    }
    if (entity.firstQuestionId) {
      query.set("questionId", entity.firstQuestionId);
    }
    if (entity.firstQuestionNo) {
      query.set("questionNo", entity.firstQuestionNo);
    }
    query.set("fromFavorites", "1");

    if (entity.sourceKind === "paper_set" && entity.paperSetId) {
      return `/mockexam/run/paper-set/${entity.paperSetId}?${query.toString()}`;
    }
    if (entity.examPaperId) {
      return `/mockexam/run/paper/${entity.examPaperId}?${query.toString()}`;
    }
  }
  return "";
}

function normalizeQuestionFavorite(item) {
  return {
    examQuestionId: Number(item?.exam_question_id || 0),
    examPaperId: Number(item?.exam_paper_id || 0),
    paperSetId: item?.paper_set_id ? Number(item.paper_set_id) : null,
    paperTitle: item?.paper_title || "未命名内容",
    sourceKind: item?.source_kind === "paper_set" ? "paper_set" : "paper",
    subject: formatSubject(item?.exam_content),
    examGroupId: item?.exam_group_id ? Number(item.exam_group_id) : null,
    groupTitle: item?.group_title || item?.section_title || "未命名题组",
    questionId: item?.question_id ? String(item.question_id) : "",
    questionNo: item?.question_no ? String(item.question_no) : "",
    createTime: item?.create_time || null,
    createTimeLabel: formatDateLabel(item?.create_time),
  };
}

function normalizeEntityFavorite(item) {
  const targetType = item?.target_type === "paper_set" ? "paper_set" : "paper";
  return {
    kind: targetType,
    key: `${targetType}:${item?.target_id || 0}`,
    targetId: Number(item?.target_id || 0),
    examPaperId: item?.exam_paper_id ? Number(item.exam_paper_id) : null,
    paperSetId: item?.paper_set_id ? Number(item.paper_set_id) : null,
    title: item?.title || "未命名内容",
    subject: formatSubject(item?.exam_content),
    latestTime: item?.create_time || null,
    latestTimeLabel: formatDateLabel(item?.create_time),
    badgeLabel: targetType === "paper_set" ? "试卷" : "题包",
    subtitle: targetType === "paper_set" ? "组合试卷收藏" : "整套题包收藏",
    favoriteCountLabel: targetType === "paper_set" ? "试卷收藏" : "题包收藏",
  };
}

export default function MockExamFavoritesPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [busyKey, setBusyKey] = useState("");
  const [filterKind, setFilterKind] = useState("all");
  const [filterSubject, setFilterSubject] = useState("all");
  const [questionItems, setQuestionItems] = useState([]);
  const [entityItems, setEntityItems] = useState([]);

  const loadFavorites = useCallback(async () => {
    setLoading(true);
    try {
      const [questionResponse, entityResponse] = await Promise.all([
        getMockExamFavorites({ limit: 200 }),
        getMockExamEntityFavorites({ limit: 200 }),
      ]);
      setQuestionItems(Array.isArray(questionResponse.data?.items) ? questionResponse.data.items : []);
      setEntityItems(Array.isArray(entityResponse.data?.items) ? entityResponse.data.items : []);
      setMessage("");
    } catch (error) {
      setQuestionItems([]);
      setEntityItems([]);
      setMessage(getApiError(error, "收藏夹加载失败，请稍后重试。"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFavorites();
  }, [loadFavorites]);

  const groupCards = useMemo(() => {
    const normalized = questionItems.map(normalizeQuestionFavorite);
    const groupMap = new Map();

    normalized.forEach((item) => {
      const sourceId = item.sourceKind === "paper_set" ? item.paperSetId || 0 : item.examPaperId || 0;
      const key = `group:${item.sourceKind}:${sourceId}:${item.examGroupId || 0}`;
      const current = groupMap.get(key);
      if (current) {
        current.questionIds.add(item.examQuestionId);
        if (item.createTime && (!current.latestTime || new Date(item.createTime) > new Date(current.latestTime))) {
          current.latestTime = item.createTime;
          current.latestTimeLabel = item.createTimeLabel;
        }
        return;
      }

      groupMap.set(key, {
        key,
        kind: "group",
        badgeLabel: "题组",
        subtitle: item.paperTitle,
        title: item.groupTitle,
        subject: item.subject,
        sourceKind: item.sourceKind,
        examPaperId: item.examPaperId,
        paperSetId: item.paperSetId,
        examGroupId: item.examGroupId,
        latestTime: item.createTime,
        latestTimeLabel: item.createTimeLabel,
        questionIds: new Set(item.examQuestionId ? [item.examQuestionId] : []),
        firstQuestionId: item.questionId || "",
        firstQuestionNo: item.questionNo || "",
      });
    });

    return [...groupMap.values()]
      .map((item) => ({
        ...item,
        favoriteCountLabel: `已收藏 ${item.questionIds.size} 题`,
      }))
      .sort(compareByLatestTime);
  }, [questionItems]);

  const entityCards = useMemo(
    () => entityItems.map(normalizeEntityFavorite).sort(compareByLatestTime),
    [entityItems]
  );

  const allCards = useMemo(
    () => [...entityCards, ...groupCards].sort(compareByLatestTime),
    [entityCards, groupCards]
  );

  const subjectOptions = useMemo(() => {
    const values = new Set(allCards.map((item) => item.subject).filter(Boolean));
    return ["all", ...values];
  }, [allCards]);

  const filteredCards = useMemo(
    () =>
      allCards.filter((item) => {
        if (filterKind !== "all" && item.kind !== filterKind) {
          return false;
        }
        if (filterSubject !== "all" && item.subject !== filterSubject) {
          return false;
        }
        return true;
      }),
    [allCards, filterKind, filterSubject]
  );

  const summary = useMemo(
    () => ({
      total: allCards.length,
      groupCount: groupCards.length,
      entityCount: entityCards.length,
    }),
    [allCards.length, entityCards.length, groupCards.length]
  );

  async function handleRemove(entity) {
    setBusyKey(entity.key);
    try {
      if (entity.kind === "paper" && entity.examPaperId) {
        await toggleMockExamPaperFavorite(entity.examPaperId, { is_favorite: false });
      } else if (entity.kind === "paper_set" && entity.paperSetId) {
        await toggleMockExamPaperSetFavorite(entity.paperSetId, { is_favorite: false });
      } else {
        await Promise.all(
          [...entity.questionIds].map((examQuestionId) =>
            toggleMockExamFavorite(examQuestionId, { is_favorite: false })
          )
        );
      }
      await loadFavorites();
    } catch (error) {
      setMessage(getApiError(error, "取消收藏失败，请稍后重试。"));
    } finally {
      setBusyKey("");
    }
  }

  if (loading) {
    return <LoadingPage message="正在加载收藏夹" submessage="请稍候，正在整理你的收藏内容" />;
  }

  return (
    <div className="favorites-page-shell">
      <MockExamModeHeader
        activeMode="favorites"
        tabs={LIBRARY_TABS}
        backButton={{ label: "返回模拟考试", path: "/mockexam" }}
      />

      <main className="favorites-page-main">
        <div className="favorites-page-container">
          <motion.div
            className="favorites-page-header"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <div className="favorites-page-header-row">
              <Star className="favorites-page-header-icon" />
              <h2>收藏夹</h2>
            </div>
            <p>按题组、题包、试卷整理你的收藏内容。</p>
          </motion.div>

          <div className="favorites-summary-grid">
            <motion.div
              className="favorites-summary-card tone-amber"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.08 }}
            >
              <div className="favorites-summary-icon tone-amber">
                <Star size={20} />
              </div>
              <div>
                <p>收藏内容</p>
                <strong>{summary.total}</strong>
              </div>
            </motion.div>

            <motion.div
              className="favorites-summary-card tone-blue"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.16 }}
            >
              <div className="favorites-summary-icon tone-blue">
                <FolderOpen size={20} />
              </div>
              <div>
                <p>题组</p>
                <strong>{summary.groupCount}</strong>
              </div>
            </motion.div>

            <motion.div
              className="favorites-summary-card tone-purple"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.24 }}
            >
              <div className="favorites-summary-icon tone-purple">
                <Layers3 size={20} />
              </div>
              <div>
                <p>题包 / 试卷</p>
                <strong>{summary.entityCount}</strong>
              </div>
            </motion.div>
          </div>

          <motion.section
            className="favorites-filter-card"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.32 }}
          >
            <div className="favorites-filter-row">
              <div className="favorites-filter-label">
                <Filter size={16} />
                <span>筛选：</span>
              </div>

              <select
                value={filterKind}
                onChange={(event) => setFilterKind(event.target.value)}
                className="favorites-filter-select"
              >
                <option value="all">全部内容</option>
                <option value="group">题组</option>
                <option value="paper">题包</option>
                <option value="paper_set">试卷</option>
              </select>

              <select
                value={filterSubject}
                onChange={(event) => setFilterSubject(event.target.value)}
                className="favorites-filter-select"
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

              {(filterKind !== "all" || filterSubject !== "all") ? (
                <button
                  type="button"
                  className="favorites-clear-button"
                  onClick={() => {
                    setFilterKind("all");
                    setFilterSubject("all");
                  }}
                >
                  <X size={14} />
                  <span>清除筛选</span>
                </button>
              ) : null}
            </div>
          </motion.section>

          {message ? <div className="favorites-inline-note is-error">{message}</div> : null}

          <div className="favorites-group-list">
            {filteredCards.map((entity, index) => {
              const path = buildEntityPath(entity);
              return (
                <motion.article
                  key={entity.key}
                  className="favorites-group-card"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.4, delay: 0.12 + index * 0.04 }}
                >
                  <div className="favorites-group-head">
                    <div className="favorites-group-copy">
                      <div className="favorites-group-badges">
                        <span
                          className={`favorites-badge ${
                            entity.kind === "paper_set"
                              ? "tone-purple"
                              : entity.kind === "paper"
                                ? "tone-blue"
                                : "tone-slate"
                          }`}
                        >
                          {entity.badgeLabel}
                        </span>
                        <span className="favorites-badge tone-indigo">{entity.subject}</span>
                        <span className="favorites-badge tone-gray">{entity.favoriteCountLabel}</span>
                      </div>

                      <h3>{entity.title}</h3>
                      <p>{entity.subtitle}</p>

                      <div className="favorites-group-meta">
                        <span>
                          <Calendar size={13} />
                          最近收藏：{entity.latestTimeLabel}
                        </span>
                      </div>
                    </div>

                    <div className="favorites-group-actions">
                      <button
                        type="button"
                        className="favorites-action-primary"
                        onClick={() => path && navigate(path)}
                        disabled={!path || busyKey === entity.key}
                      >
                        <Eye size={14} />
                        <span>查看</span>
                      </button>
                      <button
                        type="button"
                        className="favorites-action-danger"
                        onClick={() => handleRemove(entity)}
                        disabled={busyKey === entity.key}
                      >
                        <Trash2 size={14} />
                        <span>{busyKey === entity.key ? "处理中..." : "取消收藏"}</span>
                      </button>
                    </div>
                  </div>
                </motion.article>
              );
            })}
          </div>

          {!filteredCards.length ? (
            <motion.div
              className="favorites-empty-card"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.4 }}
            >
              <BookCopy className="favorites-empty-icon" />
              <h3>暂无收藏</h3>
              <p>在练习页点击收藏题包、收藏试卷或收藏题目后，这里会显示对应内容。</p>
            </motion.div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
