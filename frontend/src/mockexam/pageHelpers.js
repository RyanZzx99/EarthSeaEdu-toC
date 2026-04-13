export function getApiError(error, fallback) {
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

export function getPaperContentLabel(value) {
  const normalized = String(value || "").toLowerCase();
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

export function sortByTimeDesc(items, field = "create_time") {
  return [...(items || [])].sort((left, right) => {
    const leftTime = new Date(left?.[field] || 0).getTime();
    const rightTime = new Date(right?.[field] || 0).getTime();
    return rightTime - leftTime;
  });
}
