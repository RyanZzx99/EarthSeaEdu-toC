import quizIeltsHtml from "./mockexam_quiz_ielts.html?raw";
import quizIeltsScript from "./mockexam_quiz_ielts.js?raw";

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function serializeInline(value) {
  return JSON.stringify(value)
    .replaceAll("<", "\\u003c")
    .replaceAll(">", "\\u003e")
    .replaceAll("&", "\\u0026");
}

export function buildMockExamIeltsSrcDoc({
  email,
  sourceType = "paper",
  sourceId = null,
  examPaperId = null,
  progressId = null,
  sourceTitle = "",
  examCategory = "",
  examContent = "",
  fileName,
  payload,
  submitUrl = "",
  saveProgressUrl = "",
  favoriteToggleBaseUrl = "",
  reviewMode = false,
  initialAnswers = null,
  initialMarked = null,
  initialResult = null,
  initialQuestionId = null,
  initialQuestionIndex = null,
  initialQuestionNo = null,
  initialFavoriteQuestionIds = [],
}) {
  const bootstrapScript = `<script>window.QUIZ_BOOTSTRAP = ${serializeInline({
    items: payload,
    sourceType,
    sourceId,
    examPaperId,
    progressId,
    sourceTitle: sourceTitle || fileName || "",
    examCategory,
    examContent,
    fileName,
    email,
    submitUrl,
    saveProgressUrl,
    favoriteToggleBaseUrl,
    reviewMode,
    initialAnswers,
    initialMarked,
    initialResult,
    initialQuestionId,
    initialQuestionIndex,
    initialQuestionNo,
    initialFavoriteQuestionIds,
  })};</script><script>${quizIeltsScript}</script>`;

  return quizIeltsHtml
    .replace(
      `<script src="{{ url_for('static', filename='js/tex-mml-chtml.min.js') }}"></script>`,
      `<script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>`
    )
    .replace(
      `<script src="{{ url_for('static', filename='js/crypto-js.min.js') }}"></script>`,
      `<script src="https://cdn.jsdelivr.net/npm/crypto-js@4.2.0/crypto-js.min.js"></script>`
    )
    .replace(
      `<script src="{{ url_for('static', filename='js/tailwind.js') }}"></script>`,
      `<script src="https://cdn.tailwindcss.com"></script>`
    )
    .replaceAll("{{ email }}", escapeHtml(email))
    .replace(
      /<script>\s*window\.QUIZ_BOOTSTRAP\s*=\s*\{[\s\S]*?<\/script>\s*<script src="\.\.\/static\/js\/quiz_ielts\.js"><\/script>/,
      bootstrapScript
    )
    .replace("</head>", `<base href="${window.location.origin}/"></head>`);
}
