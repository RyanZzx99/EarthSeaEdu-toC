import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  Clock3,
  Pause,
  Play,
  SkipBack,
  SkipForward,
  Star,
  Volume2,
} from "lucide-react";
import "../mockexam/practiceExercise.css";
import { translateMockExamSelection } from "../api/mockexam";
import {
  compareTextAnswer,
  evaluateQuestionMap,
  getAnswerDisplayText,
  getCorrectForBlank,
  getMatchingItems,
  getOptions,
  getQuestionDisplayNo,
  hasQuestionAnswer,
  inferQuestionType,
  isCorrectChoice,
} from "../mockexam/practiceExerciseUtils";

function formatTypeLabel(value) {
  const type = String(value || "").toLowerCase();
  if (type === "single") return "单选题";
  if (type === "multiple") return "多选题";
  if (type === "tfng") return "判断题";
  if (type === "blank") return "填空题";
  if (type === "cloze_inline") return "完形填空";
  if (type === "matching") return "匹配题";
  if (type === "essay") return "写作题";
  return value || "题目";
}

function formatMaxScoreLabel(value) {
  if (value == null || value === "") {
    return "";
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return "";
  }
  return `${numeric} mark${numeric === 1 ? "" : "s"}`;
}

function isActExamCategory(value) {
  return String(value || "").trim().toUpperCase() === "ACT";
}

function isAlevelExamCategory(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return normalized === "ALEVEL" || normalized === "A_LEVEL";
}

function isIeltsExamCategory(value) {
  const normalized = String(value || "").trim().toUpperCase();
  return !normalized || normalized === "IELTS";
}

function isActMathContent(value) {
  return String(value || "").trim().toLowerCase() === "math";
}

function isIeltsListeningContent(value) {
  return String(value || "").trim().toLowerCase() === "listening";
}

function isActStimulusTitle(value) {
  return /\bstimulus\b/i.test(String(value || "").trim());
}

function getPassageNavId(passage, index) {
  return String(passage?.id || `section-${index + 1}`);
}

function getPassageNavTitle(passage, index) {
  return String(passage?.title || "").trim() || `Section ${index + 1}`;
}

function getPassageQuestionList(passage) {
  if (Array.isArray(passage?.questions) && passage.questions.length) {
    return passage.questions.filter((question) => question?.id);
  }
  return (passage?.groups || []).flatMap((group) =>
    Array.isArray(group?.questions) ? group.questions.filter((question) => question?.id) : []
  );
}

function getFirstPassageQuestion(passage) {
  return getPassageQuestionList(passage).find((question) => Number.isFinite(question?.globalIndex)) || null;
}

function looksLikeHtml(value) {
  return /<[a-z][\s\S]*>/i.test(String(value || ""));
}

function ensureHtml(value) {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  if (looksLikeHtml(text)) {
    return text;
  }
  return text
    .split(/\n{2,}/)
    .map((line) => `<p>${line.replace(/\n/g, "<br />")}</p>`)
    .join("");
}

function stripHtml(value) {
  return String(value || "")
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

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function getAnswerDisplayHtml(question) {
  const correctText = getAnswerDisplayText(question);
  const questionType = inferQuestionType(question);
  if (questionType !== "single" && questionType !== "multiple") {
    return ensureHtml(correctText);
  }

  const correctOptions = getOptions(question).filter((option) =>
    isCorrectChoice(question, option.label)
  );
  if (!correctOptions.length) {
    return ensureHtml(correctText);
  }

  return correctOptions
    .map((option) => {
      const label = escapeHtml(String(option.label || "").trim());
      const content = ensureHtml(option.content || option.display || option.label);
      return [
        '<div class="practice-exam-answer-choice">',
        `<span class="practice-exam-answer-choice-label">${label}.</span>`,
        `<div class="practice-exam-answer-choice-content">${content}</div>`,
        "</div>",
      ].join("");
    })
    .join("");
}

function getQuestionMaterialUrl(material) {
  return String(
    material?.image_url || material?.asset_url || material?.assetUrl || material?.url || ""
  ).trim();
}

function splitChoiceTableCells(value) {
  const text = stripHtml(value);
  if (!text) {
    return [];
  }
  const separatorCells = text
    .split(/\s{2,}|\t+|\s*\|\s*/)
    .map((cell) => cell.trim())
    .filter(Boolean);
  if (separatorCells.length >= 2) {
    return separatorCells;
  }
  const wordCells = text.split(/\s+/).map((cell) => cell.trim()).filter(Boolean);
  if (wordCells.length < 2 || wordCells.length > 6) {
    return [];
  }
  if (wordCells.some((cell) => cell.length > 28 || /[.?!;:]/.test(cell))) {
    return [];
  }
  return wordCells;
}

function extractChoiceTableHeaders(question, columnCount) {
  const explicitHeaders = Array.isArray(question?.optionTable?.headers)
    ? question.optionTable.headers.map((item) => String(item || "").trim()).filter(Boolean)
    : [];
  if (explicitHeaders.length === columnCount) {
    return explicitHeaders;
  }

  const sourceText = stripHtml(`${question?.stem || ""}\n${question?.content || ""}`);
  const sourceLines = sourceText.split(/\n+/).map((line) => line.trim()).filter(Boolean);
  for (let index = sourceLines.length - 1; index >= 0; index -= 1) {
    const cells = sourceLines[index].split(/\s{2,}|\t+|\s*\|\s*/).map((cell) => cell.trim()).filter(Boolean);
    if (cells.length === columnCount) {
      return cells;
    }
  }
  if (columnCount === 3 && /average revenue/i.test(sourceText) && /total revenue/i.test(sourceText) && /profit/i.test(sourceText)) {
    return ["Average revenue", "Total revenue", "Profit"];
  }
  return [];
}

function buildChoiceTable(question) {
  const options = getOptions(question);
  if (options.length < 2 || options.length > 8) {
    return null;
  }
  const rows = options.map((option) => ({
    label: String(option.label || "").trim(),
    cells: splitChoiceTableCells(option.content || option.display || ""),
  }));
  const columnCount = rows[0]?.cells?.length || 0;
  if (columnCount < 2 || rows.some((row) => row.cells.length !== columnCount)) {
    return null;
  }
  const headers = extractChoiceTableHeaders(question, columnCount);
  const uniqueCellTexts = new Set(rows.flatMap((row) => row.cells.map((cell) => cell.toLowerCase())));
  const looksLikeRepeatedMatrix = uniqueCellTexts.size <= Math.max(2, columnCount);
  if (!headers.length && !looksLikeRepeatedMatrix) {
    return null;
  }
  return {
    headers,
    rows,
  };
}

function escapeRegExp(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function removeLeadingQuestionNoFromText(value, questionNo) {
  const text = String(value || "");
  const normalizedNo = String(questionNo || "").trim();
  if (!text || !normalizedNo) {
    return text;
  }
  const pattern = new RegExp(
    `^(\\s*)${escapeRegExp(normalizedNo)}(?:\\s*[.、:：\\-）\\)]?\\s*)`,
    "i"
  );
  return text.replace(pattern, "$1");
}

function stripLeadingQuestionNoHtml(value, questionNo) {
  const html = String(value || "").trim();
  const normalizedNo = String(questionNo || "").trim();
  if (!html || !normalizedNo) {
    return html;
  }

  if (typeof window === "undefined" || typeof DOMParser === "undefined") {
    return removeLeadingQuestionNoFromText(html, normalizedNo);
  }

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(`<div>${html}</div>`, "text/html");
    const root = doc.body.firstElementChild;
    if (!root) {
      return html;
    }

    const walker = doc.createTreeWalker(root, 4);
    let node = walker.nextNode();
    while (node) {
      const rawText = node.textContent || "";
      if (!rawText.trim()) {
        node = walker.nextNode();
        continue;
      }
      const cleanedText = removeLeadingQuestionNoFromText(rawText, normalizedNo);
      if (cleanedText !== rawText) {
        node.textContent = cleanedText;
      }
      break;
    }
    return root.innerHTML;
  } catch (_error) {
    return removeLeadingQuestionNoFromText(html, normalizedNo);
  }
}

function formatElapsed(seconds) {
  const total = Math.max(0, Number(seconds) || 0);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const remainder = total % 60;
  if (hours > 0) {
    return [hours, minutes, remainder].map((value) => String(value).padStart(2, "0")).join(":");
  }
  return `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function formatAudioTime(seconds) {
  const total = Math.max(0, Number(seconds) || 0);
  const minutes = Math.floor(total / 60);
  const remainder = Math.floor(total % 60);
  return `${minutes}:${String(remainder).padStart(2, "0")}`;
}

function getQuestionAnchorId(question) {
  return `practice-question-anchor-${question?.id || "unknown"}`;
}

function getQuestionNoValue(question) {
  return getQuestionDisplayNo(question) || String(question?.displayNo || "").trim();
}

const SELECTION_CONTEXT_WINDOW = 160;
const CHOICE_DRAG_THRESHOLD_PX = 5;
const SELECTION_ANCHOR_SELECTOR =
  ".practice-exam-material-block, .practice-exam-question-stem, .practice-exam-question-extra, .practice-exam-group-instructions, .practice-exam-option-card, .practice-exam-matching-row, .practice-exam-textarea, .practice-exam-inline-blank, p, li, td, th, h1, h2, h3, h4, h5, h6";
const TRANSLATABLE_INPUT_TYPES = new Set(["", "text", "search", "email", "url", "tel"]);

function normalizeSelectionPlainText(value) {
  return String(value || "")
    .replace(/\s+/g, " ")
    .trim();
}

function getSelectionNodeElement(node) {
  if (!node) {
    return null;
  }
  if (node.nodeType === 1) {
    return node;
  }
  return node.parentElement || null;
}

function isTranslatableInputElement(element) {
  if (!element || element.nodeType !== 1) {
    return false;
  }

  const tagName = String(element.tagName || "").toLowerCase();
  if (tagName === "textarea") {
    return true;
  }
  if (tagName !== "input") {
    return false;
  }

  const type = String(element.getAttribute("type") || "text").toLowerCase();
  return TRANSLATABLE_INPUT_TYPES.has(type);
}

function rememberChoicePointerStart(pointerStartRef, event) {
  pointerStartRef.current = {
    x: event.clientX,
    y: event.clientY,
  };
}

function shouldSkipChoiceActivation(pointerStartRef, event) {
  const selectionText =
    typeof window === "undefined" || typeof window.getSelection !== "function"
      ? ""
      : String(window.getSelection()?.toString() || "").trim();
  if (selectionText) {
    pointerStartRef.current = null;
    return true;
  }

  const start = pointerStartRef.current;
  pointerStartRef.current = null;
  if (!start || event.clientX == null || event.clientY == null) {
    return false;
  }

  return (
    Math.abs(event.clientX - start.x) > CHOICE_DRAG_THRESHOLD_PX ||
    Math.abs(event.clientY - start.y) > CHOICE_DRAG_THRESHOLD_PX
  );
}

function isSelectionFromInteractiveElement(element) {
  if (!element || typeof element.closest !== "function") {
    return false;
  }
  return Boolean(element.closest("select"));
}

function buildSelectionCacheKey(snapshot, targetLang = "zh-CN") {
  return [
    snapshot.scopeType,
    snapshot.passageId,
    snapshot.questionId,
    snapshot.selectedText,
    targetLang,
  ].join("|");
}

function createEmptySelectionTranslateState() {
  return {
    visible: false,
    loading: false,
    error: "",
    selectedText: "",
    translation: "",
    scopeType: "",
    questionId: "",
    questionType: "",
    passageId: "",
    surroundingTextBefore: "",
    surroundingTextAfter: "",
    anchorRect: null,
  };
}

function getRectSnapshot(rect) {
  if (!rect) {
    return null;
  }
  return {
    top: rect.top,
    left: rect.left,
    right: rect.right,
    bottom: rect.bottom,
    width: rect.width,
    height: rect.height,
  };
}

function getSelectionAnchorRect(range, scopeRoot) {
  if (!range) {
    return null;
  }

  const commonElement = getSelectionNodeElement(range.commonAncestorContainer);
  const startElement = getSelectionNodeElement(range.startContainer);
  const endElement = getSelectionNodeElement(range.endContainer);
  const candidates = [commonElement, startElement, endElement];

  for (const element of candidates) {
    if (!element || !scopeRoot || !scopeRoot.contains(element)) {
      continue;
    }
    let anchorElement = null;
    if (typeof element.matches === "function" && element.matches(SELECTION_ANCHOR_SELECTOR)) {
      anchorElement = element;
    } else if (typeof element.closest === "function") {
      anchorElement = element.closest(SELECTION_ANCHOR_SELECTOR);
    }
    if (!anchorElement || !scopeRoot.contains(anchorElement)) {
      continue;
    }
    const rect = anchorElement.getBoundingClientRect();
    if (rect.width || rect.height) {
      return getRectSnapshot(rect);
    }
  }

  return getRectSnapshot(range.getBoundingClientRect());
}

function buildGroupDisplayTitle(group) {
  const rawTitle = String(group?.title || "").trim();
  if (rawTitle) {
    return /^\d+(?:\.\d+)?$/.test(rawTitle) ? `Question ${rawTitle}` : rawTitle;
  }
  const list = Array.isArray(group?.questions) ? group.questions : [];
  const first = list.length ? getQuestionNoValue(list[0]) : "";
  const last = list.length ? getQuestionNoValue(list[list.length - 1]) : "";
  if (first && last && first !== last) {
    return `Questions ${first}-${last}`;
  }
  if (first) {
    return `Question ${first}`;
  }
  return "Questions";
}

function extractAudioDisplayName(url, fallbackTitle, index) {
  const safeFallback = String(fallbackTitle || "").trim();
  if (safeFallback) {
    return safeFallback;
  }
  const raw = String(url || "").trim();
  if (!raw) {
    return `音频 ${index + 1}`;
  }
  try {
    const pathname = new URL(raw, "http://localhost").pathname;
    const filename = pathname.split("/").pop() || "";
    return decodeURIComponent(filename.replace(/\.[^.]+$/, "") || `音频 ${index + 1}`);
  } catch (_error) {
    const filename = raw.split("/").pop() || "";
    return filename.replace(/\.[^.]+$/, "") || `音频 ${index + 1}`;
  }
}

function renderInlineHtml(content, blanks, answerMap, disabled, revealAnswers, onChange) {
  const text = String(content || "");
  const blankList = Array.isArray(blanks) ? blanks : [];
  const parts = text.split(/(\{\{\s*[^}]+\s*\}\}|\[\[\s*[^\]]+\s*\]\])/g);
  if (parts.length <= 1) {
    return (
      <div
        className="practice-exam-rich-html"
        dangerouslySetInnerHTML={{ __html: ensureHtml(text) }}
      />
    );
  }

  return (
    <div className="practice-exam-rich-html">
      {parts.map((part, index) => {
        const match = String(part).match(/^\{\{\s*([^}]+)\s*\}\}$|^\[\[\s*([^\]]+)\s*\]\]$/);
        if (!match) {
          return <span key={`segment-${index}`} dangerouslySetInnerHTML={{ __html: part }} />;
        }

        const blankId = String(match[1] || match[2] || "").trim();
        const answer = answerMap?.[blankId] ?? "";
        const correctAnswer = getCorrectForBlank({ blanks: blankList }, blankId);
        const isCorrect = revealAnswers && answer && compareTextAnswer(answer, correctAnswer);
        const isWrong = revealAnswers && answer && correctAnswer && !isCorrect;

        return (
          <span className="practice-exam-inline-blank-wrap" key={`blank-${blankId}-${index}`}>
            <input
              className={`practice-exam-inline-blank${isCorrect ? " is-correct" : ""}${isWrong ? " is-wrong" : ""}`}
              type="text"
              value={answer}
              disabled={disabled}
              onChange={(event) => onChange(blankId, event.target.value)}
            />
            {revealAnswers && correctAnswer ? (
              <span className="practice-exam-inline-answer">{correctAnswer}</span>
            ) : null}
          </span>
        );
      })}
    </div>
  );
}

function QuestionAnswerReveal({ question, evaluation, hideReferenceAnswer = false }) {
  const correctText = getAnswerDisplayText(question);
  const correctHtml = getAnswerDisplayHtml(question);
  const modelAnswer = String(question?.modelAnswer || "").trim();
  const analysis = String(question?.analysis || question?.explanation || "").trim();
  const markSchemeHtml = String(
    question?.markSchemeHtml || question?.markScheme?.mark_scheme_html || ""
  ).trim();
  const markSchemeText = String(
    question?.markSchemeText || question?.markScheme?.mark_scheme_text || ""
  ).trim();
  const markSchemeContentHtml = markSchemeHtml || ensureHtml(markSchemeText);
  const markSchemePlainText = stripHtml(markSchemeContentHtml);
  const shouldShowAnalysis =
    analysis && analysis !== markSchemeText && analysis !== markSchemePlainText;
  const markSchemePoints = Array.isArray(question?.markSchemePoints)
    ? question.markSchemePoints.filter(
        (item) =>
          item &&
          (String(item.guidance_text || "").trim() ||
            String(item.comments_text || "").trim() ||
            item.mark_value != null)
      )
    : [];

  let stateText = "当前题型暂不支持自动判分";
  let stateClass = "";
  if (!evaluation?.answered) {
    stateText = "当前未作答";
  } else if (evaluation?.gradable && evaluation?.correct) {
    stateText = "当前答案判定为正确";
    stateClass = " is-correct";
  } else if (evaluation?.gradable && !evaluation?.correct) {
    stateText = "当前答案判定为错误";
    stateClass = " is-wrong";
  } else if (evaluation?.answered) {
    stateText = "当前题型已作答，暂不支持自动判分";
  }

  return (
    <div className="practice-exam-answer-panel">
      <div>
        <div className="practice-exam-answer-title">答题状态</div>
        <div className={`practice-exam-answer-state${stateClass}`}>{stateText}</div>
      </div>
      {!hideReferenceAnswer && correctText ? (
        <div>
          <div className="practice-exam-answer-title">参考答案</div>
          <div
            className="practice-exam-answer-text"
            dangerouslySetInnerHTML={{ __html: correctHtml }}
          />
        </div>
      ) : null}
      {!hideReferenceAnswer && modelAnswer && modelAnswer !== correctText ? (
        <div>
          <div className="practice-exam-answer-title">范文示例</div>
          <div className="practice-exam-answer-text">{modelAnswer}</div>
        </div>
      ) : null}
      {markSchemeContentHtml ? (
        <div>
          <div className="practice-exam-answer-title">Mark Scheme</div>
          <div
            className="practice-exam-answer-text"
            dangerouslySetInnerHTML={{ __html: markSchemeContentHtml }}
          />
        </div>
      ) : null}
      {markSchemePoints.length ? (
        <div>
          <div className="practice-exam-answer-title">
            {markSchemeContentHtml ? "Mark Scheme Points" : "Mark Scheme"}
          </div>
          <div className="practice-exam-mark-scheme-list">
            {markSchemePoints.map((item, index) => {
              const pointCode = String(item.point_code || `P${index + 1}`).trim();
              const markValue = item.mark_value;
              const guidanceText = String(item.guidance_text || "").trim();
              const commentsText = String(item.comments_text || "").trim();
              return (
                <div
                  key={`${question?.id || "question"}-mark-point-${pointCode}-${index}`}
                  className="practice-exam-mark-scheme-item"
                >
                  <div className="practice-exam-mark-scheme-head">
                    <span className="practice-exam-mark-scheme-code">{pointCode}</span>
                    {markValue != null && String(markValue).trim() ? (
                      <span className="practice-exam-mark-scheme-score">{markValue} mark</span>
                    ) : null}
                  </div>
                  {guidanceText ? (
                    <div className="practice-exam-mark-scheme-guidance">{guidanceText}</div>
                  ) : null}
                  {commentsText ? (
                    <div className="practice-exam-mark-scheme-comments">{commentsText}</div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
      {shouldShowAnalysis ? (
        <div>
          <div className="practice-exam-answer-title">解析</div>
          <div className="practice-exam-answer-text">{analysis}</div>
        </div>
      ) : null}
    </div>
  );
}

function OptionQuestion({ question, answerValue, revealAnswers, disabled, onChange }) {
  const questionType = inferQuestionType(question);
  const isTfng = questionType === "tfng";
  const options = getOptions(question);
  const selectedValues = questionType === "multiple" ? new Set(answerValue || []) : null;
  const choiceTable = !isTfng ? buildChoiceTable(question) : null;
  const pointerStartRef = useRef(null);

  if (choiceTable) {
    return (
      <ChoiceTableQuestion
        question={question}
        answerValue={answerValue}
        revealAnswers={revealAnswers}
        disabled={disabled}
        onChange={onChange}
      />
    );
  }

  return (
    <div className="practice-exam-option-list">
      {options.map((option) => {
        const label = String(option.label || "").trim();
        const normalizedLabel = label.toUpperCase();
        const isSelected =
          questionType === "multiple"
            ? selectedValues.has(normalizedLabel)
            : String(answerValue || "").trim().toUpperCase() === normalizedLabel;
        const isCorrect = revealAnswers && isCorrectChoice(question, label);
        const isWrong = revealAnswers && isSelected && !isCorrect;
        const tfngToneClass =
          normalizedLabel === "TRUE"
            ? " is-true"
            : normalizedLabel === "FALSE"
              ? " is-false"
              : normalizedLabel === "NOT GIVEN"
                ? " is-not-given"
                : "";

        return (
          <button
            key={`${question.id}-${label}`}
            className={`practice-exam-option-card${isTfng ? " is-tfng" : ""}${isSelected ? " is-selected" : ""}${isCorrect ? " is-correct" : ""}${isWrong ? " is-wrong" : ""}`}
            disabled={disabled}
            onPointerDown={(event) => rememberChoicePointerStart(pointerStartRef, event)}
            onClick={(event) => {
              if (shouldSkipChoiceActivation(pointerStartRef, event)) {
                return;
              }
              if (questionType === "multiple") {
                const nextValues = new Set(answerValue || []);
                if (nextValues.has(normalizedLabel)) {
                  nextValues.delete(normalizedLabel);
                } else {
                  nextValues.add(normalizedLabel);
                }
                onChange([...nextValues]);
                return;
              }
              onChange(normalizedLabel);
            }}
          >
            <span className={`practice-exam-option-badge${isTfng ? ` is-tfng${tfngToneClass}` : ""}`}>
              {label}
            </span>
            {isTfng ? (
              <span className="practice-exam-option-content is-tfng">
                {isCorrect ? <span className="practice-exam-option-tag">正确答案</span> : null}
              </span>
            ) : (
              <span className="practice-exam-option-content">
                <div
                  className="practice-exam-option-html"
                  dangerouslySetInnerHTML={{
                    __html: ensureHtml(option.content || option.display || label),
                  }}
                />
                {isCorrect ? <span className="practice-exam-option-tag">正确答案</span> : null}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

function TextQuestion({ question, answerValue, disabled, onChange }) {
  const questionType = inferQuestionType(question);
  if (questionType === "essay") {
    const text = String(answerValue || "");
    return (
      <div className="practice-exam-text-answer">
        <textarea
          className="practice-exam-textarea"
          value={text}
          disabled={disabled}
          placeholder="请输入你的作答内容"
          onChange={(event) => onChange(event.target.value)}
        />
        <div className="practice-exam-word-count">
          当前字数：{stripHtml(text).split(/\s+/).filter(Boolean).length}
        </div>
      </div>
    );
  }

  return (
    <div className="practice-exam-text-answer">
      <input
        className="practice-exam-textarea"
        type="text"
        value={answerValue ?? ""}
        disabled={disabled}
        placeholder="请输入答案"
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

function MatchingQuestion({ question, answerValue, revealAnswers, disabled, onChange }) {
  const items = getMatchingItems(question);
  const options = getOptions(question);
  const selectedMap =
    answerValue && typeof answerValue === "object" && !Array.isArray(answerValue) ? answerValue : {};

  return (
    <div className="practice-exam-matching">
      <div className="practice-exam-matching-options">
        {options.map((option) => (
          <div className="practice-exam-matching-option" key={`${question.id}-option-${option.label}`}>
            <div className="practice-exam-matching-option-label">{option.label}</div>
            <div
              className="practice-exam-matching-option-content"
              dangerouslySetInnerHTML={{
                __html: ensureHtml(option.content || option.display || option.label),
              }}
            />
          </div>
        ))}
      </div>
      <div className="practice-exam-matching-rows">
        {items.map((item, index) => {
          const selected = String(selectedMap[item.id] || "").toUpperCase();
          const correct = String(item.answer || "").toUpperCase();
          return (
            <div className="practice-exam-matching-row" key={`${question.id}-row-${item.id || index}`}>
              <div className="practice-exam-matching-prompt">
                <strong>{item.id || index + 1}.</strong>{" "}
                {stripHtml(item.prompt || item.text || item.content || item.stem || "")}
              </div>
              <div className="practice-exam-matching-buttons">
                {options.map((option) => {
                  const label = String(option.label || "").toUpperCase();
                  const isSelected = selected === label;
                  const isCorrect = revealAnswers && correct === label;
                  const isWrong = revealAnswers && isSelected && correct && correct !== label;
                  return (
                    <button
                      key={`${question.id}-${item.id}-${label}`}
                      className={`practice-exam-match-btn${isSelected ? " is-selected" : ""}${isCorrect ? " is-correct" : ""}${isWrong ? " is-wrong" : ""}`}
                      disabled={disabled}
                      onClick={() => onChange({ ...selectedMap, [item.id]: label })}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function QuestionMaterialAssets({ question, materials }) {
  if (!materials.length) {
    return null;
  }
  return (
    <div className="practice-exam-question-materials">
      {materials.map((material, index) => {
        const imageUrl = getQuestionMaterialUrl(material);
        const title = String(material?.title || "").trim();
        const caption = String(material?.caption_text || material?.captionText || "").trim();
        const pageNo = material?.source_page_no || material?.sourcePageNo;
        return (
          <figure
            className="practice-exam-question-material"
            key={`${question?.id || "question"}-material-${material?.material_ref_id || index}`}
          >
            {title || pageNo ? (
              <figcaption className="practice-exam-question-material-title">
                {title || `Page ${pageNo}`}
              </figcaption>
            ) : null}
            <img
              src={imageUrl}
              alt={title || `Question material ${index + 1}`}
              loading="lazy"
              draggable={false}
            />
            {caption && caption !== title ? (
              <figcaption className="practice-exam-question-material-caption">{caption}</figcaption>
            ) : null}
          </figure>
        );
      })}
    </div>
  );
}

function ChoiceTableQuestion({ question, answerValue, revealAnswers, disabled, onChange }) {
  const questionType = inferQuestionType(question);
  const selectedValues = questionType === "multiple" ? new Set(answerValue || []) : null;
  const choiceTable = buildChoiceTable(question);
  const pointerStartRef = useRef(null);
  if (!choiceTable) {
    return null;
  }

  return (
    <div className="practice-exam-choice-table-wrap">
      <table className="practice-exam-choice-table">
        {choiceTable.headers.length ? (
          <thead>
            <tr>
              <th aria-label="Option" />
              {choiceTable.headers.map((header, index) => (
                <th key={`${question.id}-choice-header-${index}`}>{header}</th>
              ))}
              <th aria-label="Select" />
            </tr>
          </thead>
        ) : null}
        <tbody>
          {choiceTable.rows.map((row) => {
            const normalizedLabel = row.label.toUpperCase();
            const isSelected =
              questionType === "multiple"
                ? selectedValues.has(normalizedLabel)
                : String(answerValue || "").trim().toUpperCase() === normalizedLabel;
            const isCorrect = revealAnswers && isCorrectChoice(question, row.label);
            const isWrong = revealAnswers && isSelected && !isCorrect;
            return (
              <tr
                key={`${question.id}-choice-row-${row.label}`}
                className={`${isSelected ? "is-selected" : ""}${isCorrect ? " is-correct" : ""}${isWrong ? " is-wrong" : ""}`}
                onPointerDown={(event) => rememberChoicePointerStart(pointerStartRef, event)}
                onClick={(event) => {
                  if (disabled) {
                    return;
                  }
                  if (shouldSkipChoiceActivation(pointerStartRef, event)) {
                    return;
                  }
                  if (questionType === "multiple") {
                    const nextValues = new Set(answerValue || []);
                    if (nextValues.has(normalizedLabel)) {
                      nextValues.delete(normalizedLabel);
                    } else {
                      nextValues.add(normalizedLabel);
                    }
                    onChange([...nextValues]);
                    return;
                  }
                  onChange(normalizedLabel);
                }}
              >
                <th scope="row">{row.label}</th>
                {row.cells.map((cell, index) => (
                  <td key={`${question.id}-${row.label}-cell-${index}`}>{cell}</td>
                ))}
                <td className="practice-exam-choice-table-control">
                  <span
                    className={`practice-exam-choice-radio${isSelected ? " is-selected" : ""}${isCorrect ? " is-correct" : ""}${isWrong ? " is-wrong" : ""}`}
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function GroupInstructionCard({
  title,
  instructions,
  typeLabel,
  favorited,
  onToggleFavorite,
}) {
  if (!title && !instructions) {
    return null;
  }
  return (
    <div className="practice-exam-group-instruction-card">
      <div className="practice-exam-group-instruction-head">
        <div>
          {title ? <h4 className="practice-exam-group-instruction-title">{title}</h4> : null}
          {typeLabel ? <div className="practice-exam-group-type-chip">{typeLabel}</div> : null}
        </div>
        <button
          className={`practice-exam-icon-btn${favorited ? " is-starred" : ""}`}
          onClick={onToggleFavorite}
        >
          <Star size={18} fill={favorited ? "currentColor" : "none"} />
        </button>
      </div>
      {instructions ? (
        <div
          className="practice-exam-group-instructions"
          dangerouslySetInnerHTML={{ __html: ensureHtml(instructions) }}
        />
      ) : null}
    </div>
  );
}

function QuestionBlock({
  question,
  answerValue,
  revealAnswers,
  reviewMode,
  evaluation,
  onSelectQuestion,
  onSetAnswer,
  grouped = false,
  hideMaxScore = false,
}) {
  const questionType = inferQuestionType(question);
  const locked = reviewMode || revealAnswers;
  const questionNo = getQuestionNoValue(question);
  const isCurrent = question.globalIndex === question.currentQuestionIndex;
  const maxScoreLabel = hideMaxScore ? "" : formatMaxScoreLabel(question.maxScore);
  const questionMaterials = Array.isArray(question.questionMaterials)
    ? question.questionMaterials.filter((material) => getQuestionMaterialUrl(material))
    : [];
  const stemHtml = question.stem
    ? stripLeadingQuestionNoHtml(ensureHtml(question.stem), questionNo)
    : "";
  const extraHtml =
    questionType !== "cloze_inline" && question.content && question.content !== question.stem
      ? stripLeadingQuestionNoHtml(ensureHtml(question.content), questionNo)
      : "";

  return (
    <article
      className={`${grouped ? "practice-exam-question-block" : "practice-exam-question-card"}${isCurrent ? " is-current" : ""}`}
      data-selection-scope="question"
      data-question-id={question?.id || ""}
      data-passage-id={question?.passageId || ""}
      data-question-type={questionType}
      onClick={() => onSelectQuestion(question.globalIndex)}
    >
      <div id={getQuestionAnchorId(question)} className="practice-exam-question-anchor" />
      <div className="practice-exam-question-no-badge">{questionNo}</div>

      {stemHtml ? (
        <div
          className="practice-exam-question-stem"
          dangerouslySetInnerHTML={{ __html: stemHtml }}
        />
      ) : null}
      {extraHtml ? (
        <div
          className="practice-exam-question-extra"
          dangerouslySetInnerHTML={{ __html: extraHtml }}
        />
      ) : null}

      {maxScoreLabel ? (
        <div className="practice-exam-question-meta">
          <span>{maxScoreLabel}</span>
        </div>
      ) : null}

      <QuestionMaterialAssets question={question} materials={questionMaterials} />

      {questionType === "single" || questionType === "multiple" || questionType === "tfng" ? (
        <OptionQuestion
          question={question}
          answerValue={answerValue}
          revealAnswers={revealAnswers}
          disabled={locked}
          onChange={(value) => onSetAnswer(question, value)}
        />
      ) : null}

      {questionType === "blank" || questionType === "essay" ? (
        <TextQuestion
          question={question}
          answerValue={answerValue}
          disabled={locked}
          onChange={(value) => onSetAnswer(question, value)}
        />
      ) : null}

      {questionType === "cloze_inline" ? (
        <div className="practice-exam-text-answer">
          {renderInlineHtml(
            question.content || question.stem,
            question.blanks,
            answerValue || {},
            locked,
            revealAnswers,
            (blankId, value) =>
              onSetAnswer(question, { ...(answerValue || {}), [blankId]: value })
          )}
        </div>
      ) : null}

      {questionType === "matching" ? (
        <MatchingQuestion
          question={question}
          answerValue={answerValue}
          revealAnswers={revealAnswers}
          disabled={locked}
          onChange={(value) => onSetAnswer(question, value)}
        />
      ) : null}

      {reviewMode || revealAnswers ? (
        <QuestionAnswerReveal
          question={question}
          evaluation={evaluation}
        />
      ) : null}
    </article>
  );
}

export function PracticeExercisePage(props) {
  const sidePanePercent = 15;
  const minMaterialPercent = 28;
  const maxMaterialPercent = 72;
  const {
    title,
    moduleName,
    examCategory = "",
    examContent = "",
    questions = [],
    passages = [],
    answers = {},
    marked = {},
    currentQuestionIndex = 0,
    revealAnswers = false,
    reviewMode = false,
    paused = false,
    elapsedSeconds = 0,
    favoriteQuestionIds = new Set(),
    message = "",
    busy = false,
    busyText = "",
    onBack,
    backLabel = "保存并退出",
    onToggleRevealAnswers,
    onTogglePaused,
    onSelectQuestion,
    onSetAnswer,
    onToggleQuestionFavorite,
    onToggleQuestionFavoriteGroup,
    onSubmit,
    initialScrollQuestionId = "",
    initialScrollQuestionNo = "",
  } = props;

  const [selectionTranslateState, setSelectionTranslateState] = useState(
    createEmptySelectionTranslateState
  );
  const [selectionHint, setSelectionHint] = useState({ scopeType: "", text: "" });
  const [audioCurrentTime, setAudioCurrentTime] = useState(0);
  const [audioDuration, setAudioDuration] = useState(0);
  const [isAudioPlaying, setIsAudioPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [audioVolume, setAudioVolume] = useState(1);
  const [showAudioVolumeMenu, setShowAudioVolumeMenu] = useState(false);
  const [activeAudioId, setActiveAudioId] = useState("");
  const [expandedSectionIds, setExpandedSectionIds] = useState(() => new Set());
  const [materialPanePercent, setMaterialPanePercent] = useState((45 / 85) * 100);
  const [isResizing, setIsResizing] = useState(false);
  const [isStackedLayout, setIsStackedLayout] = useState(
    typeof window !== "undefined" ? window.innerWidth <= 1280 : false
  );
  const audioRef = useRef(null);
  const audioVolumeRef = useRef(null);
  const audioVolumeTrackRef = useRef(null);
  const layoutRef = useRef(null);
  const materialCardRef = useRef(null);
  const questionListRef = useRef(null);
  const tooltipRef = useRef(null);
  const hintTimerRef = useRef(null);
  const selectionAbortRef = useRef(null);
  const selectionCacheRef = useRef(new Map());

  const currentQuestion = questions[currentQuestionIndex] || questions[0] || null;
  const isActExam =
    isActExamCategory(examCategory) ||
    isActExamCategory(moduleName) ||
    isActExamCategory(examContent);
  const isAlevelExam =
    isAlevelExamCategory(examCategory) ||
    isAlevelExamCategory(moduleName) ||
    isAlevelExamCategory(examContent);
  const isIeltsExam = isIeltsExamCategory(examCategory) && !isActExam && !isAlevelExam;
  const isIeltsListeningExam =
    isIeltsExam &&
    (isIeltsListeningContent(examContent) ||
      isIeltsListeningContent(moduleName) ||
      isIeltsListeningContent(currentQuestion?.section));
  const isActMathExam =
    isActExam &&
    (isActMathContent(examContent) ||
      isActMathContent(moduleName) ||
      isActMathContent(currentQuestion?.section));
  const hideMaterialPane = isActMathExam || isIeltsListeningExam;
  const evaluationMap = useMemo(() => evaluateQuestionMap(questions, answers), [questions, answers]);

  const renderedPassages = useMemo(() => {
    if (passages.length) {
      return passages;
    }
    return [{ id: "default", title: title || "试卷", content: "", instructions: "", groups: [], questions }];
  }, [passages, questions, title]);

  const currentPassage = useMemo(() => {
    if (!renderedPassages.length) {
      return null;
    }
    return renderedPassages.find((item) => item.id === currentQuestion?.passageId) || renderedPassages[0];
  }, [currentQuestion?.passageId, renderedPassages]);
  const currentPassageIndex = currentPassage
    ? renderedPassages.findIndex((item) => item.id === currentPassage.id)
    : -1;
  const currentSectionNavId = currentPassage
    ? getPassageNavId(currentPassage, currentPassageIndex >= 0 ? currentPassageIndex : 0)
    : "";
  const nextIeltsPart = useMemo(() => {
    if (!isIeltsExam || currentPassageIndex < 0) {
      return null;
    }
    for (let index = currentPassageIndex + 1; index < renderedPassages.length; index += 1) {
      const passage = renderedPassages[index];
      const question = getFirstPassageQuestion(passage);
      if (question) {
        return {
          id: getPassageNavId(passage, index),
          title: getPassageNavTitle(passage, index),
          question,
        };
      }
    }
    return null;
  }, [currentPassageIndex, isIeltsExam, renderedPassages]);

  const audioSources = useMemo(() => {
    const seen = new Set();
    return renderedPassages
      .filter((passage) => String(passage?.audio || "").trim())
      .map((passage, index) => ({
        id: String(passage.id || `audio-${index}`),
        url: String(passage.audio || "").trim(),
        displayName: extractAudioDisplayName(passage.audio, passage.title, index),
      }))
      .filter((item) => {
        const key = `${item.id}::${item.url}`;
        if (seen.has(key)) {
          return false;
        }
        seen.add(key);
        return true;
      });
  }, [renderedPassages]);

  const activeAudioSource = useMemo(() => {
    if (!audioSources.length) {
      return null;
    }
    return audioSources.find((item) => item.id === activeAudioId) || audioSources[0];
  }, [activeAudioId, audioSources]);

  const currentDisplayNo = currentQuestion ? getQuestionNoValue(currentQuestion) || 1 : 0;
  const questionCount = questions.length;
  const currentPassageTitle = String(currentPassage?.title || "").trim();
  const shouldShowCurrentPassageTitle =
    Boolean(currentPassageTitle) && !(isActExam && isActStimulusTitle(currentPassageTitle));
  const materialHtml = ensureHtml(currentPassage?.content || "");
  const materialInstructions = ensureHtml(currentPassage?.instructions || "");
  const hasMaterialContent = Boolean(stripHtml(currentPassage?.content || ""));
  const shouldShowMaterialCard = !audioSources.length || hasMaterialContent;
  const questionPanePercent = hideMaterialPane ? 100 : 100 - materialPanePercent;
  const translateButtonActive = selectionTranslateState.visible;

  function clearSelectionHintTimer() {
    if (hintTimerRef.current) {
      window.clearTimeout(hintTimerRef.current);
      hintTimerRef.current = null;
    }
  }

  function showSelectionHint(scopeType, text) {
    clearSelectionHintTimer();
    setSelectionHint({ scopeType, text });
    hintTimerRef.current = window.setTimeout(() => {
      setSelectionHint({ scopeType: "", text: "" });
      hintTimerRef.current = null;
    }, 2200);
  }

  function closeSelectionTooltip({ clearSelection = false } = {}) {
    if (selectionAbortRef.current) {
      selectionAbortRef.current.abort();
      selectionAbortRef.current = null;
    }
    setSelectionTranslateState((previous) => {
      if (clearSelection) {
        return createEmptySelectionTranslateState();
      }
      if (!previous.visible && !previous.loading && !previous.error) {
        return previous;
      }
      return {
        ...previous,
        visible: false,
        loading: false,
        error: "",
      };
    });
  }

  function buildSelectionContext(range, contextElement) {
    if (!range || !contextElement) {
      return { surroundingTextBefore: "", surroundingTextAfter: "" };
    }

    try {
      const beforeRange = range.cloneRange();
      beforeRange.selectNodeContents(contextElement);
      beforeRange.setEnd(range.startContainer, range.startOffset);

      const afterRange = range.cloneRange();
      afterRange.selectNodeContents(contextElement);
      afterRange.setStart(range.endContainer, range.endOffset);

      return {
        surroundingTextBefore: normalizeSelectionPlainText(beforeRange.toString()).slice(
          -SELECTION_CONTEXT_WINDOW
        ),
        surroundingTextAfter: normalizeSelectionPlainText(afterRange.toString()).slice(
          0,
          SELECTION_CONTEXT_WINDOW
        ),
      };
    } catch (_error) {
      return { surroundingTextBefore: "", surroundingTextAfter: "" };
    }
  }

  function captureSelectionSnapshot(expectedScopeType = "") {
    if (typeof window === "undefined") {
      return { snapshot: null, reason: "当前环境不支持划词翻译" };
    }

    const activeElement = window.document?.activeElement;
    if (isTranslatableInputElement(activeElement)) {
      const selectionStart = Number(activeElement.selectionStart);
      const selectionEnd = Number(activeElement.selectionEnd);
      const value = String(activeElement.value || "");
      if (
        Number.isFinite(selectionStart) &&
        Number.isFinite(selectionEnd) &&
        selectionStart !== selectionEnd
      ) {
        const start = Math.max(0, Math.min(selectionStart, selectionEnd, value.length));
        const end = Math.max(0, Math.min(Math.max(selectionStart, selectionEnd), value.length));
        const selectedText = normalizeSelectionPlainText(value.slice(start, end));
        if (!selectedText) {
          return { snapshot: null, reason: "请选择要翻译的内容" };
        }

        const materialRoot = materialCardRef.current;
        const questionRoot = questionListRef.current;
        const startInMaterial = Boolean(materialRoot && materialRoot.contains(activeElement));
        const startInQuestion = Boolean(questionRoot && questionRoot.contains(activeElement));

        let scopeType = "";
        let questionId = "";
        let questionType = "";
        let passageId = "";

        if (startInMaterial) {
          scopeType = "material";
          passageId =
            String(materialRoot?.dataset?.passageId || "").trim() || String(currentPassage?.id || "");
        } else if (startInQuestion) {
          scopeType = "question";
          const questionElement = activeElement.closest("[data-selection-scope='question']");
          questionId = questionElement ? String(questionElement.dataset.questionId || "").trim() : "";
          questionType = questionElement ? String(questionElement.dataset.questionType || "").trim() : "";
          passageId = questionElement ? String(questionElement.dataset.passageId || "").trim() : "";
        } else {
          return { snapshot: null, reason: "请先在材料区或题目区选择内容" };
        }

        if (expectedScopeType && scopeType !== expectedScopeType) {
          return {
            snapshot: null,
            reason:
              expectedScopeType === "material"
                ? "请在材料区选择要翻译的内容"
                : "请在题目区选择要翻译的内容",
          };
        }

        const rect = getRectSnapshot(activeElement.getBoundingClientRect());
        if (!rect || (!rect.width && !rect.height)) {
          return { snapshot: null, reason: "当前选区无效，请重新选择" };
        }

        return {
          snapshot: {
            visible: false,
            loading: false,
            error: "",
            translation: "",
            selectedText,
            scopeType,
            questionId,
            questionType,
            passageId,
            surroundingTextBefore: normalizeSelectionPlainText(value.slice(0, start)).slice(
              -SELECTION_CONTEXT_WINDOW
            ),
            surroundingTextAfter: normalizeSelectionPlainText(value.slice(end)).slice(
              0,
              SELECTION_CONTEXT_WINDOW
            ),
            anchorRect: rect,
          },
          reason: "",
        };
      }
    }

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
      return { snapshot: null, reason: "请先选择要翻译的内容" };
    }

    const range = selection.getRangeAt(0);
    const selectedText = normalizeSelectionPlainText(selection.toString());
    if (!selectedText) {
      return { snapshot: null, reason: "请先选择要翻译的内容" };
    }
    const startElement = getSelectionNodeElement(range.startContainer);
    const endElement = getSelectionNodeElement(range.endContainer);
    if (!startElement || !endElement) {
      return { snapshot: null, reason: "当前选区无效，请重新选择" };
    }
    if (
      isSelectionFromInteractiveElement(startElement) ||
      isSelectionFromInteractiveElement(endElement)
    ) {
      return { snapshot: null, reason: "输入区域内容不支持划词翻译" };
    }

    const materialRoot = materialCardRef.current;
    const questionRoot = questionListRef.current;
    const startInMaterial = Boolean(materialRoot && materialRoot.contains(range.startContainer));
    const endInMaterial = Boolean(materialRoot && materialRoot.contains(range.endContainer));
    const startInQuestion = Boolean(questionRoot && questionRoot.contains(range.startContainer));
    const endInQuestion = Boolean(questionRoot && questionRoot.contains(range.endContainer));

    if ((startInMaterial || endInMaterial) && (startInQuestion || endInQuestion)) {
      return { snapshot: null, reason: "请不要跨材料区和题目区同时划词" };
    }

    let scopeType = "";
    let contextElement = null;
    let questionId = "";
    let questionType = "";
    let passageId = "";

    if (startInMaterial && endInMaterial) {
      scopeType = "material";
      contextElement = materialRoot;
      passageId =
        String(materialRoot?.dataset?.passageId || "").trim() || String(currentPassage?.id || "");
    } else if (startInQuestion && endInQuestion) {
      scopeType = "question";
      const startQuestionElement = startElement.closest("[data-selection-scope='question']");
      const endQuestionElement = endElement.closest("[data-selection-scope='question']");
      const sameQuestion =
        startQuestionElement && endQuestionElement && startQuestionElement === endQuestionElement;
      contextElement = sameQuestion ? startQuestionElement : questionRoot;
      questionId = sameQuestion ? String(startQuestionElement.dataset.questionId || "").trim() : "";
      questionType = sameQuestion ? String(startQuestionElement.dataset.questionType || "").trim() : "";
      passageId = sameQuestion ? String(startQuestionElement.dataset.passageId || "").trim() : "";
    } else {
      return { snapshot: null, reason: "请先在材料区或题目区选择内容" };
    }

    if (expectedScopeType && scopeType !== expectedScopeType) {
      return {
        snapshot: null,
        reason:
          expectedScopeType === "material"
            ? "请在材料区选择要翻译的内容"
            : "请在题目区选择要翻译的内容",
      };
    }

    const rect = getSelectionAnchorRect(range, contextElement);
    if (!rect || (!rect.width && !rect.height)) {
      return { snapshot: null, reason: "当前选区无效，请重新选择" };
    }

    const { surroundingTextBefore, surroundingTextAfter } = buildSelectionContext(
      range,
      contextElement
    );

    return {
      snapshot: {
        visible: false,
        loading: false,
        error: "",
        translation: "",
        selectedText,
        scopeType,
        questionId,
        questionType,
        passageId,
        surroundingTextBefore,
        surroundingTextAfter,
        anchorRect: rect,
      },
      reason: "",
    };
  }

  function syncSelectionSnapshot() {
    const { snapshot } = captureSelectionSnapshot();
    if (!snapshot) {
      setSelectionTranslateState((previous) => {
        if (
          !previous.selectedText &&
          !previous.translation &&
          !previous.visible &&
          !previous.loading &&
          !previous.error
        ) {
          return previous;
        }
        return createEmptySelectionTranslateState();
      });
      return;
    }

    setSelectionTranslateState((previous) => {
      const isSameSelection =
        previous.scopeType === snapshot.scopeType &&
        previous.questionId === snapshot.questionId &&
        previous.passageId === snapshot.passageId &&
        previous.selectedText === snapshot.selectedText;

      if (isSameSelection) {
        return {
          ...previous,
          ...snapshot,
          visible: previous.visible,
          loading: previous.loading,
          error: previous.error,
          translation: previous.translation,
        };
      }

      return {
        ...createEmptySelectionTranslateState(),
        ...snapshot,
      };
    });
  }

  async function runSelectionTranslate(snapshot, { force = false } = {}) {
    if (!snapshot?.selectedText || !snapshot.scopeType) {
      return;
    }

    const cacheKey = buildSelectionCacheKey(snapshot);
    const cachedResult = selectionCacheRef.current.get(cacheKey);
    if (!force && cachedResult?.translation) {
      setSelectionTranslateState((previous) => ({
        ...previous,
        ...snapshot,
        visible: true,
        loading: false,
        error: "",
        translation: cachedResult.translation,
      }));
      return;
    }

    if (selectionAbortRef.current) {
      selectionAbortRef.current.abort();
    }

    const controller = new AbortController();
    selectionAbortRef.current = controller;
    setSelectionTranslateState((previous) => ({
      ...previous,
      ...snapshot,
      visible: true,
      loading: true,
      error: "",
      translation: "",
    }));

    try {
      const response = await translateMockExamSelection(
        {
          selected_text: snapshot.selectedText,
          scope_type: snapshot.scopeType,
          module_name: moduleName || "",
          passage_id: snapshot.passageId || "",
          question_id: snapshot.questionId || "",
          question_type: snapshot.questionType || "",
          surrounding_text_before: snapshot.surroundingTextBefore || "",
          surrounding_text_after: snapshot.surroundingTextAfter || "",
          target_lang: "zh-CN",
        },
        { signal: controller.signal }
      );

      const translation = normalizeSelectionPlainText(response.data?.translation || "");
      if (!translation) {
        throw new Error("empty_translation");
      }

      selectionCacheRef.current.set(cacheKey, { translation });
      setSelectionTranslateState((previous) => ({
        ...previous,
        ...snapshot,
        visible: true,
        loading: false,
        error: "",
        translation,
      }));
    } catch (error) {
      if (error?.code === "ERR_CANCELED" || error?.name === "CanceledError") {
        return;
      }

      const responseDetail = error?.response?.data?.detail;
      const timeoutMessage =
        error?.code === "ECONNABORTED" ? "翻译请求超时，请稍后重试" : "";
      const errorMessage =
        typeof responseDetail === "string" && responseDetail
          ? responseDetail
          : timeoutMessage || "翻译失败，请稍后重试";

      setSelectionTranslateState((previous) => ({
        ...previous,
        ...snapshot,
        visible: true,
        loading: false,
        error: errorMessage,
        translation: "",
      }));
    } finally {
      if (selectionAbortRef.current === controller) {
        selectionAbortRef.current = null;
      }
    }
  }

  async function handleTranslateButtonClick() {
    const { snapshot, reason } = captureSelectionSnapshot();
    const cachedSnapshot =
      selectionTranslateState.selectedText && selectionTranslateState.scopeType
        ? selectionTranslateState
        : null;
    const effectiveSnapshot = snapshot || cachedSnapshot;
    if (!effectiveSnapshot) {
      showSelectionHint("global", reason || "请先选择要翻译的内容");
      return;
    }

    setSelectionHint({ scopeType: "", text: "" });
    await runSelectionTranslate(effectiveSnapshot);
  }

  const selectionTooltipLayout = useMemo(() => {
    if (!selectionTranslateState.visible || !selectionTranslateState.anchorRect) {
      return null;
    }

    const scopeRoot =
      selectionTranslateState.scopeType === "material"
        ? materialCardRef.current
        : questionListRef.current;
    const paneElement = scopeRoot?.closest(".practice-exam-pane-inner") || scopeRoot;
    if (!paneElement) {
      return null;
    }

    const paneRect = paneElement.getBoundingClientRect();
    const anchorRect = selectionTranslateState.anchorRect;
    const minWidth = 280;
    const maxWidth = 360;
    const availableWidth = Math.max(minWidth, paneRect.width - 24);
    const width = Math.min(maxWidth, availableWidth);
    const estimatedHeight = 190;
    const topSpace = anchorRect.top - paneRect.top;
    const canPlaceAbove = topSpace >= estimatedHeight;
    const minLeft = paneRect.left + 12;
    const maxLeft = Math.max(minLeft, paneRect.right - width - 12);
    const minTop = paneRect.top + 12;
    const maxTop = Math.max(minTop, paneRect.bottom - estimatedHeight - 12);

    let placement = "above";
    let left = Math.min(Math.max(anchorRect.left, minLeft), maxLeft);
    let top = Math.min(Math.max(anchorRect.top - estimatedHeight - 12, minTop), maxTop);

    if (!canPlaceAbove) {
      placement = "right";
      left = Math.min(Math.max(anchorRect.right + 12, minLeft), maxLeft);
      top = Math.min(Math.max(anchorRect.top, minTop), maxTop);
    }

    return {
      placement,
      style: {
        left,
        top,
        width,
      },
    };
  }, [selectionTranslateState]);

  function clampMaterialPanePercent(value) {
    return Math.min(maxMaterialPercent, Math.max(minMaterialPercent, value));
  }

  function updateMaterialPanePercent(clientX) {
    const layout = layoutRef.current;
    if (!layout) {
      return;
    }

    const rect = layout.getBoundingClientRect();
    const sidePaneWidth = rect.width * (sidePanePercent / 100);
    const resizableWidth = rect.width - sidePaneWidth;
    if (resizableWidth <= 0) {
      return;
    }

    const relativeX = clientX - rect.left;
    const nextPercent = (relativeX / resizableWidth) * 100;
    setMaterialPanePercent(clampMaterialPanePercent(nextPercent));
  }

  function scrollToQuestion(question, behavior = "smooth") {
    const targetId = getQuestionAnchorId(question);
    const element = document.getElementById(targetId);
    if (element) {
      element.scrollIntoView({ behavior, block: "start" });
    }
  }

  useEffect(() => {
    if ((!initialScrollQuestionId && !initialScrollQuestionNo) || !questions.length) {
      return undefined;
    }

    const targetQuestion =
      questions.find((item) => String(item?.id || "") === String(initialScrollQuestionId)) ||
      questions.find(
        (item) =>
          String(getQuestionNoValue(item) || item?.displayNo || "") === String(initialScrollQuestionNo)
      );
    if (!targetQuestion) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      scrollToQuestion(targetQuestion, "auto");
    }, 0);

    return () => window.clearTimeout(timer);
  }, [initialScrollQuestionId, initialScrollQuestionNo, questions]);

  useEffect(() => {
    const handleSelectionEvent = () => {
      syncSelectionSnapshot();
    };

    document.addEventListener("selectionchange", handleSelectionEvent);
    document.addEventListener("mouseup", handleSelectionEvent);
    document.addEventListener("keyup", handleSelectionEvent);
    document.addEventListener("select", handleSelectionEvent, true);

    return () => {
      document.removeEventListener("selectionchange", handleSelectionEvent);
      document.removeEventListener("mouseup", handleSelectionEvent);
      document.removeEventListener("keyup", handleSelectionEvent);
      document.removeEventListener("select", handleSelectionEvent, true);
    };
  }, [currentPassage?.id, questions]);

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        closeSelectionTooltip();
        setShowAudioVolumeMenu(false);
        setSelectionHint({ scopeType: "", text: "" });
      }
    };

    const handlePointerDown = (event) => {
      if (tooltipRef.current?.contains(event.target)) {
        return;
      }
      if (audioVolumeRef.current?.contains(event.target)) {
        return;
      }
      setShowAudioVolumeMenu(false);
      closeSelectionTooltip();
    };

    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("mousedown", handlePointerDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, []);

  useEffect(() => {
    const materialPane = materialCardRef.current?.closest(".practice-exam-pane-inner");
    const questionPane = questionListRef.current?.closest(".practice-exam-pane-inner");
    const handleScroll = () => closeSelectionTooltip();

    materialPane?.addEventListener("scroll", handleScroll, { passive: true });
    questionPane?.addEventListener("scroll", handleScroll, { passive: true });
    window.addEventListener("scroll", handleScroll, { passive: true });

    return () => {
      materialPane?.removeEventListener("scroll", handleScroll);
      questionPane?.removeEventListener("scroll", handleScroll);
      window.removeEventListener("scroll", handleScroll);
    };
  }, [currentPassage?.id, questions.length]);

  useEffect(() => {
    return () => {
      clearSelectionHintTimer();
      if (selectionAbortRef.current) {
        selectionAbortRef.current.abort();
        selectionAbortRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!audioSources.length) {
      setActiveAudioId("");
      return;
    }
    if (currentPassage?.audio) {
      setActiveAudioId(String(currentPassage.id));
      return;
    }
    setActiveAudioId((previous) => {
      if (previous && audioSources.some((item) => item.id === previous)) {
        return previous;
      }
      return audioSources[0].id;
    });
  }, [audioSources, currentPassage?.audio, currentPassage?.id]);

  useEffect(() => {
    if (!isIeltsExam || !currentSectionNavId) {
      return;
    }
    setExpandedSectionIds((previous) => {
      if (previous.has(currentSectionNavId)) {
        return previous;
      }
      const next = new Set(previous);
      next.add(currentSectionNavId);
      return next;
    });
  }, [currentSectionNavId, isIeltsExam]);

  useEffect(() => {
    const audio = audioRef.current;
    const src = activeAudioSource?.url || "";
    if (!audio || !src) {
      setAudioCurrentTime(0);
      setAudioDuration(0);
      setIsAudioPlaying(false);
      return undefined;
    }

    audio.pause();
    audio.currentTime = 0;
    audio.volume = audioVolume;
    audio.load();
    setAudioCurrentTime(0);
    setAudioDuration(0);
    setIsAudioPlaying(false);

    const handleLoaded = () => {
      setAudioDuration(audio.duration || 0);
      setAudioCurrentTime(audio.currentTime || 0);
    };
    const handleTimeUpdate = () => setAudioCurrentTime(audio.currentTime || 0);
    const handleEnded = () => setIsAudioPlaying(false);

    audio.addEventListener("loadedmetadata", handleLoaded);
    audio.addEventListener("timeupdate", handleTimeUpdate);
    audio.addEventListener("ended", handleEnded);
    return () => {
      audio.pause();
      audio.removeEventListener("loadedmetadata", handleLoaded);
      audio.removeEventListener("timeupdate", handleTimeUpdate);
      audio.removeEventListener("ended", handleEnded);
    };
  }, [activeAudioSource?.url]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackSpeed;
    }
  }, [playbackSpeed]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = audioVolume;
    }
  }, [audioVolume]);

  useEffect(() => {
    const handleResize = () => {
      setIsStackedLayout(window.innerWidth <= 1280);
    };

    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  useEffect(() => {
    if (!isResizing) {
      return undefined;
    }

    const handlePointerMove = (event) => {
      updateMaterialPanePercent(event.clientX);
    };

    const handlePointerUp = () => {
      setIsResizing(false);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    window.addEventListener("pointercancel", handlePointerUp);

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
      window.removeEventListener("pointercancel", handlePointerUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };
  }, [isResizing]);

  async function handleToggleAudio() {
    const audio = audioRef.current;
    if (!audio || !activeAudioSource?.url) {
      return;
    }
    if (isAudioPlaying) {
      audio.pause();
      setIsAudioPlaying(false);
      return;
    }
    try {
      await audio.play();
      setIsAudioPlaying(true);
    } catch (_error) {
      setIsAudioPlaying(false);
    }
  }

  function clampAudioVolume(value) {
    return Math.min(1, Math.max(0, value));
  }

  function updateAudioVolumeFromPointer(clientY) {
    const track = audioVolumeTrackRef.current;
    if (!track) {
      return;
    }
    const rect = track.getBoundingClientRect();
    if (!rect.height) {
      return;
    }
    const nextVolume = clampAudioVolume((clientY - rect.top) / rect.height);
    setAudioVolume(Number(nextVolume.toFixed(2)));
  }

  function handleAudioVolumePointerDown(event) {
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    updateAudioVolumeFromPointer(event.clientY);
  }

  function handleAudioVolumePointerMove(event) {
    if (event.pointerType === "mouse" && event.buttons !== 1) {
      return;
    }
    updateAudioVolumeFromPointer(event.clientY);
  }

  function handleAudioVolumeKeyDown(event) {
    const step = event.shiftKey ? 0.1 : 0.05;
    let nextVolume = audioVolume;
    if (event.key === "ArrowDown" || event.key === "ArrowRight") {
      nextVolume += step;
    } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
      nextVolume -= step;
    } else if (event.key === "Home") {
      nextVolume = 0;
    } else if (event.key === "End") {
      nextVolume = 1;
    } else {
      return;
    }
    event.preventDefault();
    setAudioVolume(Number(clampAudioVolume(nextVolume).toFixed(2)));
  }

  function renderAudioCard({ compact = false, showSourceList = true } = {}) {
    if (!audioSources.length) {
      return null;
    }
    const audioVolumePercent = Math.round(audioVolume * 100);
    const audioVolumeThumbTop = 15 + audioVolume * 82;
    return (
      <div className={`practice-exam-audio-card${compact ? " is-compact" : ""}`}>
        <div className="practice-exam-audio-head">
          <div className="practice-exam-audio-title">
            <Volume2 size={18} />
            <span>听力音频</span>
          </div>
          <button
            className="practice-exam-rate-btn"
            onClick={() =>
              setPlaybackSpeed((value) => (value >= 2 ? 1 : Number((value + 0.25).toFixed(2))))
            }
          >
            {playbackSpeed}x
          </button>
        </div>

        {showSourceList && audioSources.length > 1 ? (
          <div className="practice-exam-audio-source-list">
            {audioSources.map((source) => (
              <button
                key={source.id}
                className={`practice-exam-audio-source-btn${activeAudioSource?.id === source.id ? " is-active" : ""}`}
                onClick={() => setActiveAudioId(source.id)}
              >
                {source.displayName}
              </button>
            ))}
          </div>
        ) : null}

        <div className="practice-exam-audio-current-name">
          {activeAudioSource?.displayName || "当前音频"}
        </div>
        <audio ref={audioRef} preload="metadata" src={activeAudioSource?.url || ""} />
        <div className="practice-exam-audio-progress">
          <input
            type="range"
            min="0"
            max={audioDuration || 0}
            value={audioCurrentTime}
            onChange={(event) => {
              const audio = audioRef.current;
              const nextValue = Number(event.target.value);
              setAudioCurrentTime(nextValue);
              if (audio) {
                audio.currentTime = nextValue;
              }
            }}
          />
          <div className="practice-exam-audio-time">
            <span>{formatAudioTime(audioCurrentTime)}</span>
            <span>{formatAudioTime(audioDuration)}</span>
          </div>
        </div>
        <div className="practice-exam-audio-volume" ref={audioVolumeRef}>
          <button
            type="button"
            className={`practice-exam-audio-volume-btn${showAudioVolumeMenu ? " is-open" : ""}`}
            aria-expanded={showAudioVolumeMenu}
            aria-label="调整听力音量"
            onClick={() => setShowAudioVolumeMenu((value) => !value)}
          >
            <Volume2 size={16} />
            <span>{audioVolumePercent}%</span>
          </button>
          {showAudioVolumeMenu ? (
            <div className="practice-exam-audio-volume-popover">
              <div className="practice-exam-audio-volume-body">
                <div
                  ref={audioVolumeTrackRef}
                  className="practice-exam-audio-volume-track"
                  role="slider"
                  tabIndex={0}
                  aria-label="Volume"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={audioVolumePercent}
                  aria-orientation="vertical"
                  onPointerDown={handleAudioVolumePointerDown}
                  onPointerMove={handleAudioVolumePointerMove}
                  onKeyDown={handleAudioVolumeKeyDown}
                >
                  <span
                    className="practice-exam-audio-volume-fill"
                    style={{ height: `${audioVolumeThumbTop}px` }}
                  />
                  <span
                    className="practice-exam-audio-volume-thumb"
                    style={{ top: `${audioVolumeThumbTop}px` }}
                  />
                </div>
              </div>
            </div>
          ) : null}
        </div>
        <div className="practice-exam-audio-controls">
          <button
            className="practice-exam-audio-btn"
            onClick={() => {
              const audio = audioRef.current;
              if (!audio) {
                return;
              }
              audio.currentTime = Math.max(0, (audio.currentTime || 0) - 5);
            }}
          >
            <SkipBack size={18} />
          </button>
          <button className="practice-exam-audio-btn is-primary" onClick={handleToggleAudio}>
            {isAudioPlaying ? <Pause size={20} /> : <Play size={20} />}
          </button>
          <button
            className="practice-exam-audio-btn"
            onClick={() => {
              const audio = audioRef.current;
              if (!audio) {
                return;
              }
              audio.currentTime = Math.min(audioDuration || 0, (audio.currentTime || 0) + 5);
            }}
          >
            <SkipForward size={18} />
          </button>
        </div>
      </div>
    );
  }

  function toggleSectionNav(sectionId) {
    setExpandedSectionIds((previous) => {
      const next = new Set(previous);
      if (next.has(sectionId)) {
        next.delete(sectionId);
      } else {
        next.add(sectionId);
      }
      return next;
    });
  }

  function handleNavigatorQuestionClick(question, sectionId = "") {
    if (sectionId) {
      setExpandedSectionIds((previous) => {
        if (previous.has(sectionId)) {
          return previous;
        }
        const next = new Set(previous);
        next.add(sectionId);
        return next;
      });
    }
    if (Number.isFinite(question.globalIndex)) {
      onSelectQuestion(question.globalIndex);
    }
    window.setTimeout(() => scrollToQuestion(question, "smooth"), 0);
  }

  function handleNextPartClick() {
    if (!nextIeltsPart?.question) {
      return;
    }
    handleNavigatorQuestionClick(nextIeltsPart.question, nextIeltsPart.id);
  }

  function renderQuestionGridButton(question, sectionId = "") {
    const answered = hasQuestionAnswer(question, answers[question.id]);
    const isCurrent = question.globalIndex === currentQuestionIndex;
    const isMarked = Boolean(marked[question.id]);
    const questionNo = getQuestionNoValue(question) || question.displayNo;
    return (
      <button
        key={`grid-${sectionId || "flat"}-${question.id}`}
        className={`practice-exam-grid-btn${answered ? " is-answered" : ""}${isCurrent ? " is-current" : ""}${isMarked ? " is-marked" : ""}`}
        onClick={() => handleNavigatorQuestionClick(question, sectionId)}
      >
        {String(questionNo).padStart(2, "0")}
      </button>
    );
  }

  function renderIeltsQuestionNavigator() {
    return (
      <div className="practice-exam-section-nav">
        {renderedPassages.map((passage, passageIndex) => {
          const sectionId = getPassageNavId(passage, passageIndex);
          const passageQuestions = getPassageQuestionList(passage);
          if (!passageQuestions.length) {
            return null;
          }
          const expanded = expandedSectionIds.has(sectionId);
          const hasCurrentQuestion = passageQuestions.some(
            (question) => question.globalIndex === currentQuestionIndex
          );
          return (
            <div
              className={`practice-exam-section-nav-group${hasCurrentQuestion ? " is-current" : ""}`}
              key={`section-nav-${sectionId}`}
            >
              <button
                type="button"
                className="practice-exam-section-nav-head"
                aria-expanded={expanded}
                onClick={() => toggleSectionNav(sectionId)}
              >
                <span className="practice-exam-section-nav-title">
                  {getPassageNavTitle(passage, passageIndex)}
                </span>
                <span className="practice-exam-section-nav-meta">
                  {expanded ? "收起" : `${passageQuestions.length} 题`}
                </span>
              </button>
              {expanded ? (
                <div className="practice-exam-section-nav-grid">
                  {passageQuestions.map((question) => renderQuestionGridButton(question, sectionId))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    );
  }

  function renderQuestionNavigator() {
    if (isIeltsExam) {
      return renderIeltsQuestionNavigator();
    }
    return <div className="practice-exam-grid">{questions.map((question) => renderQuestionGridButton(question))}</div>;
  }

  function renderQuestionList() {
    const passagesToRender =
      isIeltsExam && currentPassage
        ? [{ passage: currentPassage, passageIndex: currentPassageIndex >= 0 ? currentPassageIndex : 0 }]
        : renderedPassages.map((passage, passageIndex) => ({ passage, passageIndex }));
    return passagesToRender.map(({ passage, passageIndex }) => {
      const passageTitle = String(passage?.title || "").trim();
      const shouldShowPassageHeading =
        (renderedPassages.length > 1 || Boolean(passageTitle)) &&
        !(isActExam && isActStimulusTitle(passageTitle));
      const sections =
        Array.isArray(passage.groups) && passage.groups.length
          ? passage.groups
          : [{ id: `${passage.id}-default`, title: "", instructions: "", questions: passage.questions || [] }];

      return (
        <section className="practice-exam-passage-group" key={passage.id || passageIndex}>
          {shouldShowPassageHeading ? (
            <div className="practice-exam-passage-heading">
              {passageTitle || `Passage ${passageIndex + 1}`}
            </div>
          ) : null}

          {sections.map((group, groupIndex) => {
            const groupDisplayTitle = buildGroupDisplayTitle(group);
            const visibleGroupTitle =
              isActExam && isActStimulusTitle(groupDisplayTitle) ? "" : groupDisplayTitle;
            const groupQuestions = group.questions || [];
            const groupQuestionType = groupQuestions.length ? inferQuestionType(groupQuestions[0]) : "";
            const favoriteGroupQuestions = groupQuestions.filter((question) => question?.exam_question_id);
            const groupFavorited =
              favoriteGroupQuestions.length > 0 &&
              favoriteGroupQuestions.every((question) =>
                favoriteQuestionIds.has(String(question?.exam_question_id || ""))
              );
            return (
              <div className="practice-exam-group-section" key={group.id || `${passage.id}-${groupIndex}`}>
                <GroupInstructionCard
                  title={visibleGroupTitle}
                  instructions={group.instructions}
                  typeLabel={formatTypeLabel(groupQuestionType)}
                  favorited={groupFavorited}
                  onToggleFavorite={() => onToggleQuestionFavoriteGroup(favoriteGroupQuestions)}
                />
                <div className="practice-exam-group-question-list">
                  <div className="practice-exam-question-group-card">
                    {groupQuestions.map((question) => (
                      <QuestionBlock
                        key={question.id}
                        grouped
                        question={{
                          ...question,
                          currentQuestionIndex,
                        }}
                        answerValue={answers[question.id]}
                        revealAnswers={revealAnswers}
                        reviewMode={reviewMode}
                        evaluation={evaluationMap[question.id]}
                        onSelectQuestion={onSelectQuestion}
                        onSetAnswer={onSetAnswer}
                        hideMaxScore={isActExam}
                      />
                    ))}
                  </div>
                </div>
              </div>
            );
          })}
        </section>
      );
    });
  }

  return (
    <div className="practice-exam-page">
      <header className="practice-exam-topbar">
        <div className="practice-exam-topbar-left">
          <button className="practice-exam-back-btn" onClick={onBack}>
            <ArrowLeft size={18} />
            <span>{backLabel}</span>
          </button>
          <div className="practice-exam-topbar-divider" />
          <div className="practice-exam-title-wrap">
            <div className="practice-exam-title-badge">练</div>
            <div>
              <h2>{title || (shouldShowCurrentPassageTitle ? currentPassageTitle : "") || "练习答题"}</h2>
              <p>
                {moduleName || "IELTS"} · 第 {currentDisplayNo} / {questionCount} 题
              </p>
            </div>
          </div>
        </div>

        <div className="practice-exam-topbar-right">
          <div className="practice-exam-translate-action">
            <button
              className={`practice-exam-mini-btn practice-exam-topbar-translate-btn${translateButtonActive ? " is-active" : ""}`}
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => handleTranslateButtonClick()}
            >
              显示翻译
            </button>
            {selectionHint.text ? (
              <span className="practice-exam-selection-hint">{selectionHint.text}</span>
            ) : null}
          </div>
          <div className="practice-exam-topbar-chip">
            第 {currentDisplayNo}/{questionCount} 题
          </div>
          <div className="practice-exam-topbar-chip is-time">
            <Clock3 size={16} />
            <span>{formatElapsed(elapsedSeconds)}</span>
          </div>
          <button
            className={`practice-exam-toggle-btn${reviewMode || revealAnswers ? " is-active" : ""}`}
            disabled={reviewMode}
            onClick={onToggleRevealAnswers}
          >
            {reviewMode ? "回看模式" : revealAnswers ? "已显示答案" : "显示答案"}
          </button>
          <button className="practice-exam-icon-btn" disabled={reviewMode} onClick={onTogglePaused}>
            {paused ? <Play size={18} /> : <Pause size={18} />}
          </button>
        </div>
      </header>

      <div className="practice-exam-layout" ref={layoutRef}>
        {!hideMaterialPane ? (
          <section
            className="practice-exam-material-pane"
            style={
              isStackedLayout
                ? undefined
                : { width: `calc((100% - ${sidePanePercent}%) * ${materialPanePercent / 100})` }
            }
          >
          <div className="practice-exam-pane-inner">
            <div className="practice-exam-pane-head">
              <h3>材料区（文章 / 听力说明）</h3>
            </div>

            {audioSources.length ? (
              <div className="practice-exam-audio-card">
                <div className="practice-exam-audio-head">
                  <div className="practice-exam-audio-title">
                    <Volume2 size={18} />
                    <span>听力音频</span>
                  </div>
                  <button
                    className="practice-exam-rate-btn"
                    onClick={() =>
                      setPlaybackSpeed((value) => (value >= 2 ? 1 : Number((value + 0.25).toFixed(2))))
                    }
                  >
                    {playbackSpeed}x
                  </button>
                </div>

                {audioSources.length > 1 ? (
                  <div className="practice-exam-audio-source-list">
                    {audioSources.map((source) => (
                      <button
                        key={source.id}
                        className={`practice-exam-audio-source-btn${activeAudioSource?.id === source.id ? " is-active" : ""}`}
                        onClick={() => setActiveAudioId(source.id)}
                      >
                        {source.displayName}
                      </button>
                    ))}
                  </div>
                ) : null}

                <div className="practice-exam-audio-current-name">
                  {activeAudioSource?.displayName || "当前音频"}
                </div>
                <audio ref={audioRef} preload="metadata" src={activeAudioSource?.url || ""} />
                <div className="practice-exam-audio-progress">
                  <input
                    type="range"
                    min="0"
                    max={audioDuration || 0}
                    value={audioCurrentTime}
                    onChange={(event) => {
                      const audio = audioRef.current;
                      const nextValue = Number(event.target.value);
                      setAudioCurrentTime(nextValue);
                      if (audio) {
                        audio.currentTime = nextValue;
                      }
                    }}
                  />
                  <div className="practice-exam-audio-time">
                    <span>{formatAudioTime(audioCurrentTime)}</span>
                    <span>{formatAudioTime(audioDuration)}</span>
                  </div>
                </div>
                <div className="practice-exam-audio-controls">
                  <button
                    className="practice-exam-audio-btn"
                    onClick={() => {
                      const audio = audioRef.current;
                      if (!audio) {
                        return;
                      }
                      audio.currentTime = Math.max(0, (audio.currentTime || 0) - 5);
                    }}
                  >
                    <SkipBack size={18} />
                  </button>
                  <button className="practice-exam-audio-btn is-primary" onClick={handleToggleAudio}>
                    {isAudioPlaying ? <Pause size={20} /> : <Play size={20} />}
                  </button>
                  <button
                    className="practice-exam-audio-btn"
                    onClick={() => {
                      const audio = audioRef.current;
                      if (!audio) {
                        return;
                      }
                      audio.currentTime = Math.min(audioDuration || 0, (audio.currentTime || 0) + 5);
                    }}
                  >
                    <SkipForward size={18} />
                  </button>
                </div>
              </div>
            ) : null}

            {shouldShowMaterialCard ? (
              <div
                ref={materialCardRef}
                className="practice-exam-material-card"
                data-selection-scope="material"
                data-passage-id={currentPassage?.id || ""}
              >
                {shouldShowCurrentPassageTitle ? (
                  <div className="practice-exam-material-label">{currentPassageTitle}</div>
                ) : null}
                {materialInstructions ? (
                  <div
                    className="practice-exam-material-block"
                    dangerouslySetInnerHTML={{ __html: materialInstructions }}
                  />
                ) : null}
                {materialHtml ? (
                  <div
                    className="practice-exam-material-block"
                    dangerouslySetInnerHTML={{ __html: materialHtml }}
                  />
                ) : (
                  <div className="practice-exam-empty">暂无材料</div>
                )}
              </div>
            ) : null}
          </div>
          </section>
        ) : null}

        {!hideMaterialPane ? (
          <div
          className={`practice-exam-resizer${isResizing ? " is-active" : ""}`}
          onPointerDown={(event) => {
            if (isStackedLayout) {
              return;
            }
            event.preventDefault();
            setIsResizing(true);
            updateMaterialPanePercent(event.clientX);
          }}
          role="separator"
          aria-orientation="vertical"
          aria-label="调整材料区和答题区宽度"
          />
        ) : null}

        <main
          className={`practice-exam-question-pane${hideMaterialPane ? " is-full-width" : ""}`}
          style={
            isStackedLayout
              ? undefined
              : { width: `calc((100% - ${sidePanePercent}%) * ${questionPanePercent / 100})` }
          }
        >
          <div className="practice-exam-pane-inner">
            <div className="practice-exam-pane-head">
              <h3>答题区</h3>
            </div>

            {isIeltsListeningExam ? renderAudioCard({ compact: true, showSourceList: false }) : null}
            {message ? <div className="practice-exam-message">{message}</div> : null}
            <div ref={questionListRef} className="practice-exam-question-list">
              {renderQuestionList()}
            </div>
          </div>
        </main>

        <aside className="practice-exam-side-pane">
          <div className="practice-exam-side-inner">
            <h3>题卡 / 帮助区</h3>
            {renderQuestionNavigator()}

            <div className="practice-exam-help-card">
              <h4>提示</h4>
              <div className="practice-exam-help-list">
                <div>所有题目可在中间区域连续作答，点击题号可以快速定位。</div>
                <div>橙色题号表示当前题目，蓝色题号表示已作答。</div>
                <div>“显示答案”开启后会锁定作答，仅用于练习查看。</div>
                <div>先划词再点顶部“显示翻译”，译文会显示在选中段落附近。</div>
              </div>
            </div>

            <div className={`practice-exam-submit-card${reviewMode ? " is-review" : ""}`}>
              {reviewMode ? (
                <div className="practice-exam-submit-note">当前为成绩回看模式，答案与对错状态已锁定。</div>
              ) : (
                <button className="practice-exam-submit-btn" disabled={busy} onClick={onSubmit}>
                  {busyText || "提交练习"}
                </button>
              )}
            </div>
          </div>
        </aside>
      </div>
      {nextIeltsPart ? (
        <button className="practice-exam-next-part-btn" type="button" onClick={handleNextPartClick}>
          <span>NEXT SECTION</span>
          <small>{nextIeltsPart.title}</small>
        </button>
      ) : null}
      {selectionTranslateState.visible && selectionTooltipLayout ? (
        <div
          ref={tooltipRef}
          className={`practice-exam-selection-tooltip is-${selectionTooltipLayout.placement}`}
          style={selectionTooltipLayout.style}
          role="dialog"
          aria-live="polite"
        >
          <div className="practice-exam-selection-tooltip-head">
            <div className="practice-exam-selection-tooltip-title">
              {selectionTranslateState.scopeType === "material" ? "材料翻译" : "题目翻译"}
            </div>
            <button
              className="practice-exam-selection-tooltip-close"
              type="button"
              onClick={() => closeSelectionTooltip()}
            >
              关闭
            </button>
          </div>
          <div className="practice-exam-selection-tooltip-selected">
            {selectionTranslateState.selectedText}
          </div>
          <div className="practice-exam-selection-tooltip-body">
            {selectionTranslateState.loading ? (
              <div className="practice-exam-selection-tooltip-loading">翻译中...</div>
            ) : null}
            {!selectionTranslateState.loading && selectionTranslateState.error ? (
              <div className="practice-exam-selection-tooltip-error">
                <span>{selectionTranslateState.error}</span>
                <button
                  className="practice-exam-selection-tooltip-retry"
                  type="button"
                  onClick={() => runSelectionTranslate(selectionTranslateState, { force: true })}
                >
                  重试
                </button>
              </div>
            ) : null}
            {!selectionTranslateState.loading &&
            !selectionTranslateState.error &&
            selectionTranslateState.translation ? (
              <div className="practice-exam-selection-tooltip-text">
                {selectionTranslateState.translation}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}
