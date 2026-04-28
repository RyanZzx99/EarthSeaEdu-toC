import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { useNavigate } from "react-router-dom";
import {
  AlertCircle,
  BarChart3,
  BookOpen,
  Calendar,
  ChevronDown,
  ChevronRight,
  Eye,
  Filter,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { getMockExamQuestionDetail, getMockExamWrongQuestions } from "../api/mockexam";
import { LoadingPage } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import {
  MOCK_EXAM_ALL_CATEGORY,
  MOCK_EXAM_ALL_CONTENT,
  formatDateTime,
  formatExamScopeLabel,
  getApiError,
  getExamCategoryLabel,
  getPaperContentLabel,
  isActCategory,
} from "../mockexam/pageHelpers";
import "../mockexam/mistakesPage.css";

const LIBRARY_TABS = [
  { key: "mistakes", label: "错题本", path: "/mockexam/mistakes" },
  { key: "history", label: "练习历史", path: "/mockexam/history" },
  { key: "favorites", label: "收藏夹", path: "/mockexam/favorites" },
];

function normalizeExamCategoryFilter(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized || MOCK_EXAM_ALL_CATEGORY;
}

function formatQuestionTypeLabel(value) {
  const type = String(value || "").trim().toLowerCase();
  const labels = {
    single: "单选题",
    single_choice: "单选题",
    multiple: "多选题",
    multiple_choice: "多选题",
    multiple_select: "多选题",
    tfng: "判断题",
    true_false_not_given: "判断题",
    yes_no_not_given: "判断题",
    blank: "填空题",
    cloze_inline: "完形填空",
    sentence_completion: "句子填空",
    form_completion: "表格填空",
    note_completion: "笔记填空",
    summary_completion: "摘要填空",
    table_completion: "表格填空",
    matching: "匹配题",
    map_labeling: "地图题",
    diagram_labeling: "图表题",
    short_answer: "简答题",
    essay: "写作题",
  };
  return labels[type] || "题目";
}

function formatFilterTypeOptionLabel(value) {
  return value === "题目" ? "完形填空" : value;
}

function normalizeHtmlText(value) {
  return String(value || "")
    .replace(/<(li|p|div|tr|br)\b[^>]*>/gi, " ")
    .replace(/<\/(li|p|div|tr)>/gi, " ")
    .replace(/\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]/g, " _____ ")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<\/p>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#39;/gi, "'")
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, " ")
    .trim();
}

function replacePlaceholderHtml(value) {
  return String(value || "").replace(
    /\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]/g,
    '<span class="mistakes-inline-blank">_____</span>'
  );
}

function hasMeaningfulHtml(value) {
  if (!String(value || "").trim()) {
    return false;
  }
  if (/<(img|audio|video|table|svg|iframe)\b/i.test(String(value))) {
    return true;
  }
  return Boolean(normalizeHtmlText(value));
}

function buildMistakePreviewText(item, detail) {
  const previewCandidates = [item?.preview_text, detail?.question?.stem, detail?.question?.content];
  for (const candidate of previewCandidates) {
    const text = normalizeHtmlText(candidate);
    if (text) {
      return text;
    }
  }
  if (item?.question_no != null && item?.question_no !== "") {
    return `题号 ${item.question_no}`;
  }
  return "当前题目";
}

function renderAnswer(question) {
  if (!question) {
    return "--";
  }

  if (question.type === "cloze_inline") {
    const blanks = Array.isArray(question.blanks) ? question.blanks : [];
    const answers = [...new Set(blanks.map((blank) => String(blank?.answer || "").trim()).filter(Boolean))];
    return answers.length ? answers.join(" / ") : "--";
  }

  if (Array.isArray(question.answer)) {
    const answers = question.answer.map((item) => String(item || "").trim()).filter(Boolean);
    return answers.length ? answers.join(", ") : "--";
  }

  if (question.answer && typeof question.answer === "object") {
    const answers = Object.values(question.answer)
      .map((item) => String(item || "").trim())
      .filter(Boolean);
    return answers.length ? [...new Set(answers)].join(" / ") : "--";
  }

  return String(question.answer || "").trim() || "--";
}

function QuestionDetailInline({ item }) {
  const detail = item?.detail;
  const question = detail?.question || null;
  const group = detail?.group || {};
  const section = detail?.section || {};
  const options = Array.isArray(question?.options)
    ? question.options
    : Array.isArray(group?.options)
      ? group.options
      : [];

  if (!detail || !question) {
    return <div className="mistakes-inline-detail-empty">当前题目详情暂时不可用。</div>;
  }

  return (
    <div className="mistakes-inline-detail">
      <div className="mistakes-inline-detail-meta">
        <span>{section.title || "当前材料"}</span>
        <span>{group.title || "当前题组"}</span>
        <span>{item.typeLabel}</span>
      </div>

      {group.instructions ? (
        <div
          className="mistakes-inline-detail-html"
          dangerouslySetInnerHTML={{ __html: replacePlaceholderHtml(group.instructions) }}
        />
      ) : null}

      {question.stem ? (
        <div
          className="mistakes-inline-detail-html"
          dangerouslySetInnerHTML={{ __html: replacePlaceholderHtml(question.stem) }}
        />
      ) : null}

      {question.content && question.content !== question.stem ? (
        <div
          className="mistakes-inline-detail-html"
          dangerouslySetInnerHTML={{ __html: replacePlaceholderHtml(question.content) }}
        />
      ) : null}

      {options.length ? (
        <div className="mistakes-inline-options">
          {options.map((option) => (
            <div
              key={`${item.key}-${option.label || option.content || option.display}`}
              className="mistakes-inline-option"
            >
              <strong>{option.label}</strong>
              <span
                dangerouslySetInnerHTML={{
                  __html: replacePlaceholderHtml(option.content || option.display || option.label),
                }}
              />
            </div>
          ))}
        </div>
      ) : null}

      <div className="mistakes-inline-answer-grid">
        <div className="mistakes-inline-answer-card">
          <span>标准答案</span>
          <strong>{renderAnswer(question)}</strong>
        </div>
        <div className="mistakes-inline-answer-card">
          <span>累计错误次数</span>
          <strong>{item.wrongCount}</strong>
        </div>
        <div className="mistakes-inline-answer-card">
          <span>最近错误时间</span>
          <strong>{formatDateTime(item.lastWrongTime) || "--"}</strong>
        </div>
      </div>

      {!hasMeaningfulHtml(detail.material_html) && section.audio ? (
        <div className="mistakes-inline-detail-note">
          当前为听力题，材料需要通过“重做题目”进入答题页查看。
        </div>
      ) : null}
    </div>
  );
}

export default function MockExamMistakesPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [summary, setSummary] = useState({
    total_questions: 0,
    total_wrong_count: 0,
    average_wrong_count: 0,
    most_common_type: null,
  });
  const [groups, setGroups] = useState([]);
  const [detailMap, setDetailMap] = useState(new Map());
  const [filterExamCategory, setFilterExamCategory] = useState(MOCK_EXAM_ALL_CATEGORY);
  const [filterExamContent, setFilterExamContent] = useState(MOCK_EXAM_ALL_CONTENT);
  const [filterType, setFilterType] = useState("all");
  const [sortBy, setSortBy] = useState("recent");
  const [expandedGroups, setExpandedGroups] = useState({});
  const [expandedDetails, setExpandedDetails] = useState({});

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const wrongResult = await getMockExamWrongQuestions({ limit: 200 });

        if (!active) {
          return;
        }

        const payload = wrongResult?.data || {};
        const nextSummary = payload.summary || {
          total_questions: 0,
          total_wrong_count: 0,
          average_wrong_count: 0,
          most_common_type: null,
        };
        const nextGroups = Array.isArray(payload.groups) ? payload.groups : [];

        setSummary(nextSummary);
        setGroups(nextGroups);

        const examQuestionIds = [
          ...new Set(
            nextGroups
              .flatMap((group) => (Array.isArray(group?.questions) ? group.questions : []))
              .map((item) => item?.exam_question_id)
              .filter((value) => value != null)
          ),
        ];

        const detailResults = await Promise.allSettled(
          examQuestionIds.map((examQuestionId) =>
            getMockExamQuestionDetail(examQuestionId).then((response) => ({
              key: String(examQuestionId),
              value: response.data || null,
            }))
          )
        );

        if (!active) {
          return;
        }

        const nextDetailMap = new Map();
        detailResults.forEach((result) => {
          if (result.status === "fulfilled" && result.value?.key) {
            nextDetailMap.set(result.value.key, result.value.value);
          }
        });

        setDetailMap(nextDetailMap);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }

        setSummary({
          total_questions: 0,
          total_wrong_count: 0,
          average_wrong_count: 0,
          most_common_type: null,
        });
        setGroups([]);
        setDetailMap(new Map());
        setMessage(getApiError(error, "错题本加载失败，请稍后重试。"));
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

  const uiGroups = useMemo(
    () =>
      groups.map((group, groupIndex) => ({
        key: `${group.exam_paper_id || 0}:${group.exam_group_id || 0}:${groupIndex}`,
        examPaperId: group.exam_paper_id,
        paperTitle: group.paper_title || "未命名试卷",
        paperCode: group.paper_code || "",
        examCategory: group.exam_category || "IELTS",
        examContent: group.exam_content || "",
        examScopeLabel: formatExamScopeLabel(group.exam_category, group.exam_content),
        groupId: group.exam_group_id,
        groupTitle: group.group_title || "题组",
        sectionTitle: group.section_title || "",
        latestTime: group.latest_wrong_time,
        totalWrongCount: Number(group.total_wrong_count || 0),
        wrongQuestionCount: Number(group.wrong_question_count || 0),
        questions: (Array.isArray(group.questions) ? group.questions : []).map((item, itemIndex) => {
          const examQuestionKey = String(item.exam_question_id || "");
          const detail = detailMap.get(examQuestionKey) || null;
          const typeLabel = formatQuestionTypeLabel(
            item.stat_type || item.question_type || detail?.question?.stat_type || detail?.question?.type
          );

          return {
            key: `${item.exam_question_id || item.question_id || "row"}-${itemIndex}`,
            examQuestionId: item.exam_question_id,
            examPaperId: item.exam_paper_id,
            examCategory: item.exam_category || group.exam_category || "IELTS",
            examContent: item.exam_content || group.exam_content || "",
            examSectionId: item.exam_section_id,
            examGroupId: item.exam_group_id,
            questionId: item.question_id,
            questionNo: item.question_no ?? detail?.question?.question_no ?? detail?.question?.displayNo ?? "--",
            typeLabel,
            previewText: buildMistakePreviewText(item, detail),
            wrongCount: Number(item.wrong_count || 0),
            lastWrongTime: item.last_wrong_time,
            detail,
          };
        }),
      })),
    [detailMap, groups]
  );

  const typeOptions = useMemo(() => {
    const values = new Set();
    uiGroups.forEach((group) => {
      group.questions.forEach((item) => values.add(item.typeLabel));
    });
    return ["all", ...values];
  }, [uiGroups]);

  const examCategoryOptions = useMemo(() => {
    const values = new Set(
      uiGroups
        .map((group) => normalizeExamCategoryFilter(group.examCategory))
        .filter(Boolean)
    );
    return [MOCK_EXAM_ALL_CATEGORY, ...values];
  }, [uiGroups]);

  const examContentOptions = useMemo(() => {
    const values = new Set(
      uiGroups
        .filter(
          (group) =>
            filterExamCategory === MOCK_EXAM_ALL_CATEGORY ||
            normalizeExamCategoryFilter(group.examCategory) === filterExamCategory
        )
        .map((group) => group.examContent)
        .filter(Boolean)
    );
    return [MOCK_EXAM_ALL_CONTENT, ...values];
  }, [filterExamCategory, uiGroups]);

  const filteredGroups = useMemo(() => {
    const nextGroups = uiGroups
      .map((group) => ({
        ...group,
        questions:
          filterType === "all"
            ? group.questions
            : group.questions.filter((item) => item.typeLabel === filterType),
      }))
      .filter((group) => {
        if (!group.questions.length) {
          return false;
        }
        if (
          filterExamCategory !== MOCK_EXAM_ALL_CATEGORY &&
          normalizeExamCategoryFilter(group.examCategory) !== filterExamCategory
        ) {
          return false;
        }
        if (filterExamContent !== MOCK_EXAM_ALL_CONTENT && group.examContent !== filterExamContent) {
          return false;
        }
        return true;
      });

    if (sortBy === "frequency") {
      return [...nextGroups].sort((left, right) => right.totalWrongCount - left.totalWrongCount);
    }

    return [...nextGroups].sort(
      (left, right) => new Date(right.latestTime || 0).getTime() - new Date(left.latestTime || 0).getTime()
    );
  }, [filterExamCategory, filterExamContent, filterType, sortBy, uiGroups]);

  function toggleGroup(groupKey) {
    setExpandedGroups((previous) => ({
      ...previous,
      [groupKey]: !previous[groupKey],
    }));
  }

  function toggleDetail(itemKey) {
    setExpandedDetails((previous) => ({
      ...previous,
      [itemKey]: !previous[itemKey],
    }));
  }

  function handleRedo(item) {
    if (!item?.examPaperId) {
      return;
    }

    const query = new URLSearchParams();
    if (item.examGroupId) {
      query.set("groupId", String(item.examGroupId));
    }
    if (item.questionId) {
      query.set("questionId", String(item.questionId));
    }
    if (item.questionNo != null && item.questionNo !== "") {
      query.set("questionNo", String(item.questionNo));
    }
    if (isActCategory(item.examCategory) && item.examContent) {
      query.set("examContent", item.examContent);
    }
    query.set("fromMistakes", "1");
    navigate(`/mockexam/run/paper/${item.examPaperId}?${query.toString()}`);
  }

  if (loading) {
    return <LoadingPage message="正在加载错题本" submessage="请稍候，正在整理你的错题记录" />;
  }

  return (
    <div className="mistakes-page-shell">
      <MockExamModeHeader
        activeMode="mistakes"
        tabs={LIBRARY_TABS}
        backButton={{ label: "返回模拟考试", path: "/mockexam" }}
      />

      <main className="mistakes-main">
        <div className="mistakes-container">
          <motion.div
            className="mistakes-page-header"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
          >
            <div className="mistakes-page-header-row">
              <AlertCircle className="mistakes-page-header-icon" />
              <h2>错题记录</h2>
            </div>
            <p>查看和复习你的错题，按题组归档并快速重做。</p>
          </motion.div>

          <div className="mistakes-summary-grid">
            <motion.div
              className="mistakes-summary-card tone-rose"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
            >
              <div className="mistakes-summary-icon">
                <BookOpen size={20} />
              </div>
              <div>
                <p>错题总数</p>
                <strong>{Number(summary.total_questions || 0)}</strong>
              </div>
            </motion.div>

            <motion.div
              className="mistakes-summary-card tone-blue"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="mistakes-summary-icon">
                <RefreshCw size={20} />
              </div>
              <div>
                <p>平均错误次数</p>
                <strong>{Number(summary.average_wrong_count || 0).toFixed(1)}</strong>
              </div>
            </motion.div>

            <motion.div
              className="mistakes-summary-card tone-amber"
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
            >
              <div className="mistakes-summary-icon">
                <BarChart3 size={20} />
              </div>
              <div>
                <p>最常错题型</p>
                <strong>{summary.most_common_type ? formatQuestionTypeLabel(summary.most_common_type) : "暂无"}</strong>
              </div>
            </motion.div>
          </div>

          <motion.section
            className="mistakes-filter-card"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.4 }}
          >
            <div className="mistakes-filter-label">
              <Filter size={16} />
              <span>题型：</span>
            </div>

            <select
              value={filterExamCategory}
              onChange={(event) => {
                setFilterExamCategory(event.target.value);
                setFilterExamContent(MOCK_EXAM_ALL_CONTENT);
              }}
              className="mistakes-filter-select"
            >
              {examCategoryOptions.map((option) => (
                <option key={option} value={option}>
                  {option === MOCK_EXAM_ALL_CATEGORY ? "全部考试" : getExamCategoryLabel(option)}
                </option>
              ))}
            </select>

            <select
              value={filterExamContent}
              onChange={(event) => setFilterExamContent(event.target.value)}
              className="mistakes-filter-select"
            >
              {examContentOptions.map((option) => (
                <option key={option} value={option}>
                  {option === MOCK_EXAM_ALL_CONTENT ? "全部学科" : getPaperContentLabel(option)}
                </option>
              ))}
            </select>

            <select
              value={filterType}
              onChange={(event) => setFilterType(event.target.value)}
              className="mistakes-filter-select"
            >
              {typeOptions.map((option) => (
                <option key={option} value={option}>
                  {option === "all" ? "全部题型" : formatFilterTypeOptionLabel(option)}
                </option>
              ))}
            </select>

            <div className="mistakes-filter-label">
              <span>排序：</span>
            </div>

            <select
              value={sortBy}
              onChange={(event) => setSortBy(event.target.value)}
              className="mistakes-filter-select"
            >
              <option value="recent">最近错误</option>
              <option value="frequency">错误次数</option>
            </select>

            {(filterExamCategory !== MOCK_EXAM_ALL_CATEGORY ||
              filterExamContent !== MOCK_EXAM_ALL_CONTENT ||
              filterType !== "all") ? (
              <button
                type="button"
                className="mistakes-filter-clear"
                onClick={() => {
                  setFilterExamCategory(MOCK_EXAM_ALL_CATEGORY);
                  setFilterExamContent(MOCK_EXAM_ALL_CONTENT);
                  setFilterType("all");
                }}
              >
                清除筛选
              </button>
            ) : null}
          </motion.section>

          {message ? <div className="mistakes-inline-message">{message}</div> : null}

          <div className="mistakes-group-list">
            {filteredGroups.map((group, index) => {
              const expanded = Boolean(expandedGroups[group.key]);
              return (
                <motion.article
                  key={group.key}
                  className="mistakes-group-card"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.4, delay: 0.45 + index * 0.05 }}
                >
                  <button type="button" className="mistakes-group-header" onClick={() => toggleGroup(group.key)}>
                    <div className="mistakes-group-header-copy">
                      <div className="mistakes-group-header-top">
                        <strong>{group.paperTitle}</strong>
                        <span className="mistakes-group-chip">{group.examScopeLabel}</span>
                      </div>
                      <p>
                        {group.groupTitle}
                        {group.sectionTitle ? ` · ${group.sectionTitle}` : ""}
                      </p>
                      <div className="mistakes-group-meta">
                        <span>错题 {group.wrongQuestionCount} 题</span>
                        <span>累计错误 {group.totalWrongCount} 次</span>
                        <span>最近：{formatDateTime(group.latestTime) || "--"}</span>
                      </div>
                    </div>
                    <div className="mistakes-group-chevron">
                      {expanded ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                    </div>
                  </button>

                  {expanded ? (
                    <div className="mistakes-question-list">
                      {group.questions.map((item) => {
                        const detailExpanded = Boolean(expandedDetails[item.key]);
                        return (
                          <div key={item.key} className="mistakes-question-row">
                            <div className="mistakes-question-main">
                              <div className="mistakes-question-header">
                                <span className="mistakes-question-no">Q{item.questionNo}</span>
                                <span className="mistakes-question-tag">{item.typeLabel}</span>
                              </div>

                              <h4>{item.previewText || `${group.paperTitle} - Q${item.questionNo}`}</h4>

                              <div className="mistakes-question-meta">
                                <span>
                                  <RefreshCw size={12} />
                                  错误 {item.wrongCount} 次
                                </span>
                                <span>
                                  <Calendar size={12} />
                                  最近：{formatDateTime(item.lastWrongTime) || "--"}
                                </span>
                              </div>
                            </div>

                            <div className="mistakes-question-actions">
                              <button type="button" className="mistakes-primary-button" onClick={() => handleRedo(item)}>
                                重做题目
                              </button>
                              <button
                                type="button"
                                className="mistakes-secondary-button"
                                onClick={() => toggleDetail(item.key)}
                              >
                                <Eye size={14} />
                                <span>{detailExpanded ? "收起详情" : "查看详情"}</span>
                              </button>
                              <button
                                type="button"
                                className="mistakes-danger-button is-disabled"
                                disabled
                                title="删除错题记录接口尚未接入"
                              >
                                <Trash2 size={14} />
                                <span>删除</span>
                              </button>
                            </div>

                            {detailExpanded ? <QuestionDetailInline item={item} /> : null}
                          </div>
                        );
                      })}
                    </div>
                  ) : null}
                </motion.article>
              );
            })}
          </div>

          {!filteredGroups.length ? (
            <motion.div
              className="mistakes-empty-card"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.5 }}
            >
              <AlertCircle size={52} />
              <h3>没有找到错题</h3>
              <p>调整筛选条件，或先去完成练习以积累错题记录。</p>
            </motion.div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
