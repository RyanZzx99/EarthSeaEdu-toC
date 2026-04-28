export function getApiError(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string" && detail.trim()) {
    return detail.trim();
  }
  if (Array.isArray(detail) && detail.length) {
    const first = detail[0];
    if (typeof first === "string" && first.trim()) {
      return first.trim();
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

export function formatDateTime(value) {
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

export function estimatePaperDuration(examContent, multiplier = 1) {
  const normalized = String(examContent || "").toLowerCase();
  if (normalized === "reading") {
    return 60 * multiplier;
  }
  if (normalized === "listening") {
    return 30 * multiplier;
  }
  return 45 * multiplier;
}

export const MOCK_EXAM_ALL_CONTENT = "All";
export const MOCK_EXAM_ALL_CATEGORY = "All";
export const MOCK_EXAM_CATEGORY_IELTS = "IELTS";
export const MOCK_EXAM_CATEGORY_ALEVEL = "ALEVEL";
export const MOCK_EXAM_CATEGORY_ACT = "ACT";

export function isAlevelCategory(value) {
  return String(value || "").toUpperCase() === MOCK_EXAM_CATEGORY_ALEVEL;
}

export function isActCategory(value) {
  return String(value || "").toUpperCase() === MOCK_EXAM_CATEGORY_ACT;
}

export function isSinglePaperExamCategory(value) {
  return isAlevelCategory(value) || isActCategory(value);
}

export function getExamCategoryLabel(value) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === MOCK_EXAM_CATEGORY_ALEVEL) {
    return "A-Level";
  }
  if (normalized === MOCK_EXAM_CATEGORY_ACT) {
    return "ACT";
  }
  if (normalized === MOCK_EXAM_CATEGORY_IELTS) {
    return "IELTS";
  }
  return value || "考试";
}

export function mergeMockExamOptions(baseOptions, alevelOptions, actOptions) {
  const contentOptionsMap = {
    ...(baseOptions?.content_options_map || {}),
    ...(alevelOptions?.content_options_map || {}),
    ...(actOptions?.content_options_map || {}),
  };
  if (!contentOptionsMap[MOCK_EXAM_CATEGORY_ALEVEL] && Array.isArray(alevelOptions?.content_options)) {
    contentOptionsMap[MOCK_EXAM_CATEGORY_ALEVEL] = alevelOptions.content_options;
  }
  if (!contentOptionsMap[MOCK_EXAM_CATEGORY_ACT] && Array.isArray(actOptions?.content_options)) {
    contentOptionsMap[MOCK_EXAM_CATEGORY_ACT] = actOptions.content_options;
  }

  const categories = [
    ...(Array.isArray(baseOptions?.exam_category_options) ? baseOptions.exam_category_options : []),
    ...(Array.isArray(alevelOptions?.exam_category_options) ? alevelOptions.exam_category_options : []),
    ...(Array.isArray(actOptions?.exam_category_options) ? actOptions.exam_category_options : []),
  ].filter(Boolean);
  const examCategoryOptions = [...new Set(categories.length ? categories : [
    MOCK_EXAM_CATEGORY_IELTS,
    MOCK_EXAM_CATEGORY_ALEVEL,
    MOCK_EXAM_CATEGORY_ACT,
  ])];

  return {
    ...baseOptions,
    content_options_map: contentOptionsMap,
    exam_category_options: examCategoryOptions,
  };
}

export function getExamContentOptions(options, examCategory) {
  const rawOptions = options?.content_options_map?.[examCategory] || [];
  return [...new Set((Array.isArray(rawOptions) ? rawOptions : []).filter(Boolean))];
}

export function getPaperMetricLabel(item) {
  if (isAlevelCategory(item?.exam_category)) {
    if (item?.total_score !== null && item?.total_score !== undefined && item?.total_score !== "") {
      return `${item.total_score}分`;
    }
    return "A-Level";
  }
  if (isActCategory(item?.exam_category)) {
    if (item?.total_score !== null && item?.total_score !== undefined && item?.total_score !== "") {
      return `${item.total_score} pts`;
    }
    return "ACT";
  }
  return `${item?.question_count || 40}题`;
}

export function getPaperContentLabel(value) {
  const normalized = String(value || "").toLowerCase();
  if (normalized === "act") {
    return "ACT";
  }
  if (normalized === "reading") {
    return "阅读";
  }
  if (normalized === "listening") {
    return "听力";
  }
  if (normalized === "mixed") {
    return "混合";
  }
  return value || "综合";
}

export function formatExamScopeLabel(examCategory, examContent) {
  const categoryLabel = getExamCategoryLabel(examCategory);
  const content = String(examContent || "").trim();
  if (!content || content.toLowerCase() === "all") {
    return categoryLabel;
  }
  const contentLabel = getPaperContentLabel(content);
  if (!contentLabel || contentLabel === categoryLabel) {
    return categoryLabel;
  }
  return `${categoryLabel} · ${contentLabel}`;
}

export function sortByTimeDesc(items, field = "create_time") {
  return [...(items || [])].sort((left, right) => {
    const leftTime = new Date(left?.[field] || 0).getTime();
    const rightTime = new Date(right?.[field] || 0).getTime();
    return rightTime - leftTime;
  });
}
