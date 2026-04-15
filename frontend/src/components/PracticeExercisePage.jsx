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

function buildGroupDisplayTitle(group) {
  const rawTitle = String(group?.title || "").trim();
  if (rawTitle) {
    return rawTitle;
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
  const modelAnswer = String(question?.modelAnswer || "").trim();
  const analysis = String(question?.analysis || question?.explanation || "").trim();

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
          <div className="practice-exam-answer-text">{correctText}</div>
        </div>
      ) : null}
      {!hideReferenceAnswer && modelAnswer && modelAnswer !== correctText ? (
        <div>
          <div className="practice-exam-answer-title">范文示例</div>
          <div className="practice-exam-answer-text">{modelAnswer}</div>
        </div>
      ) : null}
      {analysis ? (
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
            onClick={() => {
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
  showQuestionTranslation,
  onSelectQuestion,
  onSetAnswer,
  grouped = false,
}) {
  const questionType = inferQuestionType(question);
  const locked = reviewMode || revealAnswers;
  const questionNo = getQuestionNoValue(question);
  const isCurrent = question.globalIndex === question.currentQuestionIndex;
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

      {showQuestionTranslation ? (
        <div className="practice-exam-translation-card">
          题目翻译功能待接入，当前先保留页面位置与交互按钮。
        </div>
      ) : null}

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

      {reviewMode ? (
        <QuestionAnswerReveal
          question={question}
          evaluation={evaluation}
          hideReferenceAnswer
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

  const [showMaterialTranslation, setShowMaterialTranslation] = useState(false);
  const [showQuestionTranslation, setShowQuestionTranslation] = useState(false);
  const [materialBookmarked, setMaterialBookmarked] = useState(false);
  const [audioCurrentTime, setAudioCurrentTime] = useState(0);
  const [audioDuration, setAudioDuration] = useState(0);
  const [isAudioPlaying, setIsAudioPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [activeAudioId, setActiveAudioId] = useState("");
  const [materialPanePercent, setMaterialPanePercent] = useState((45 / 85) * 100);
  const [isResizing, setIsResizing] = useState(false);
  const [isStackedLayout, setIsStackedLayout] = useState(
    typeof window !== "undefined" ? window.innerWidth <= 1280 : false
  );
  const audioRef = useRef(null);
  const layoutRef = useRef(null);

  const currentQuestion = questions[currentQuestionIndex] || questions[0] || null;
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
  const materialHtml = ensureHtml(currentPassage?.content || "");
  const materialInstructions = ensureHtml(currentPassage?.instructions || "");
  const hasMaterialContent = Boolean(stripHtml(currentPassage?.content || ""));
  const shouldShowMaterialCard = !audioSources.length || hasMaterialContent;
  const questionPanePercent = 100 - materialPanePercent;

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

  function renderQuestionList() {
    return renderedPassages.map((passage, passageIndex) => {
      const sections =
        Array.isArray(passage.groups) && passage.groups.length
          ? passage.groups
          : [{ id: `${passage.id}-default`, title: "", instructions: "", questions: passage.questions || [] }];

      return (
        <section className="practice-exam-passage-group" key={passage.id || passageIndex}>
          {renderedPassages.length > 1 || passage.title ? (
            <div className="practice-exam-passage-heading">
              {passage.title || `Passage ${passageIndex + 1}`}
            </div>
          ) : null}

          {sections.map((group, groupIndex) => {
            const groupDisplayTitle = buildGroupDisplayTitle(group);
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
                  title={groupDisplayTitle}
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
                        showQuestionTranslation={showQuestionTranslation}
                        onSelectQuestion={onSelectQuestion}
                        onSetAnswer={onSetAnswer}
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
              <h2>{title || currentPassage?.title || "练习答题"}</h2>
              <p>
                {moduleName || "IELTS"} · 第 {currentDisplayNo} / {questionCount} 题
              </p>
            </div>
          </div>
        </div>

        <div className="practice-exam-topbar-right">
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
              <div className="practice-exam-pane-actions">
                <button
                  className={`practice-exam-icon-btn${materialBookmarked ? " is-starred" : ""}`}
                  title="当前仅做本地收藏状态展示"
                  onClick={() => setMaterialBookmarked((value) => !value)}
                >
                  <Star size={18} fill={materialBookmarked ? "currentColor" : "none"} />
                </button>
                <button
                  className={`practice-exam-mini-btn${showMaterialTranslation ? " is-active" : ""}`}
                  onClick={() => setShowMaterialTranslation((value) => !value)}
                >
                  {showMaterialTranslation ? "隐藏翻译" : "显示翻译"}
                </button>
              </div>
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
              <div className="practice-exam-material-card">
              {currentPassage?.title ? (
                <div className="practice-exam-material-label">{currentPassage.title}</div>
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

            {showMaterialTranslation ? (
              <div className="practice-exam-translation-card">
                材料翻译功能待接入，当前先保留页面位置与交互按钮。
              </div>
            ) : null}
          </div>
        </section>

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

        <main
          className="practice-exam-question-pane"
          style={
            isStackedLayout
              ? undefined
              : { width: `calc((100% - ${sidePanePercent}%) * ${questionPanePercent / 100})` }
          }
        >
          <div className="practice-exam-pane-inner">
            <div className="practice-exam-pane-head">
              <h3>答题区</h3>
              <div className="practice-exam-pane-actions">
                <button
                  className={`practice-exam-mini-btn${showQuestionTranslation ? " is-active" : ""}`}
                  onClick={() => setShowQuestionTranslation((value) => !value)}
                >
                  {showQuestionTranslation ? "隐藏翻译" : "显示翻译"}
                </button>
              </div>
            </div>

            {message ? <div className="practice-exam-message">{message}</div> : null}
            <div className="practice-exam-question-list">{renderQuestionList()}</div>
          </div>
        </main>

        <aside className="practice-exam-side-pane">
          <div className="practice-exam-side-inner">
            <h3>题卡 / 帮助区</h3>
            <div className="practice-exam-grid">
              {questions.map((question) => {
                const answered = hasQuestionAnswer(question, answers[question.id]);
                const isCurrent = question.globalIndex === currentQuestionIndex;
                const isMarked = Boolean(marked[question.id]);
                const questionNo = getQuestionNoValue(question) || question.displayNo;
                return (
                  <button
                    key={`grid-${question.id}`}
                    className={`practice-exam-grid-btn${answered ? " is-answered" : ""}${isCurrent ? " is-current" : ""}${isMarked ? " is-marked" : ""}`}
                    onClick={() => {
                      onSelectQuestion(question.globalIndex);
                      scrollToQuestion(question, "smooth");
                    }}
                  >
                    {String(questionNo).padStart(2, "0")}
                  </button>
                );
              })}
            </div>

            <div className="practice-exam-help-card">
              <h4>提示</h4>
              <div className="practice-exam-help-list">
                <div>所有题目可在中间区域连续作答，点击题号可以快速定位。</div>
                <div>橙色题号表示当前题目，蓝色题号表示已作答。</div>
                <div>题目收藏已接真实接口；材料收藏当前仅保留本地状态展示。</div>
                <div>“显示答案”开启后会锁定作答，仅用于练习查看。</div>
                <div>题目翻译与材料翻译按钮已保留，当前未接后端翻译能力。</div>
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
    </div>
  );
}
