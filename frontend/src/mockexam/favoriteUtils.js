import { buildQuestionStore, normalizeExamData } from "./practiceExerciseUtils";

function stripHtml(value) {
  return String(value ?? "")
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<\/p>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#39;/gi, "'")
    .replace(/&quot;/gi, '"');
}

export function stripPlaceholderTokens(value) {
  return stripHtml(value)
    .replace(/\[\[\s*[^\]]+\s*\]\]/g, "_____")
    .replace(/\{\{\s*[^}]+\s*\}\}/g, "_____")
    .replace(/\s+/g, " ")
    .trim();
}

export function extractPayloadQuestionIds(payload) {
  const normalized = normalizeExamData(payload);
  const store = buildQuestionStore(normalized);
  return [...new Set(
    (store.questions || [])
      .map((question) => Number(question?.exam_question_id || 0))
      .filter((value) => Number.isFinite(value) && value > 0)
  )];
}

export function buildFavoriteSummarySets(items) {
  const paperIds = new Set();
  const paperSetIds = new Set();

  (items || []).forEach((item) => {
    const targetType =
      item?.target_type === "paper_set" || item?.source_kind === "paper_set"
        ? "paper_set"
        : "paper";
    if (targetType === "paper_set") {
      const paperSetId = Number(item?.paper_set_id || item?.target_id || 0);
      if (paperSetId > 0) {
        paperSetIds.add(paperSetId);
      }
      return;
    }

    const examPaperId = Number(item?.exam_paper_id || item?.target_id || 0);
    if (Number.isFinite(examPaperId) && examPaperId !== 0) {
      paperIds.add(examPaperId);
    }
  });

  return {
    paperIds,
    paperSetIds,
  };
}
