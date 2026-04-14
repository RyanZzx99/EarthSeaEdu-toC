const TFNG_CHOICES = [
  { value: "TRUE", label: "TRUE" },
  { value: "FALSE", label: "FALSE" },
  { value: "NOT GIVEN", label: "NOT GIVEN" },
];

export function safeClone(value) {
  if (value == null) {
    return value;
  }
  if (typeof value !== "object") {
    return value;
  }
  try {
    return JSON.parse(JSON.stringify(value));
  } catch (_error) {
    return value;
  }
}

export function safeParseJsonLike(value) {
  if (value == null) {
    return null;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed || trimmed === "null" || trimmed === "undefined") {
      return null;
    }
    try {
      const parsed = JSON.parse(trimmed);
      if (typeof parsed === "string") {
        const inner = parsed.trim();
        if (inner && (inner.startsWith("{") || inner.startsWith("["))) {
          try {
            return JSON.parse(inner);
          } catch (_error) {
            return parsed;
          }
        }
      }
      return parsed;
    } catch (_error) {
      return null;
    }
  }
  if (typeof value === "object") {
    return safeClone(value);
  }
  return null;
}

function htmlToPlainText(value) {
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

export function normalizeTextValue(value) {
  return htmlToPlainText(value).replace(/\s+/g, " ").trim().toLowerCase();
}

export function normalizeLabelValue(value) {
  return String(value ?? "").trim().toUpperCase();
}

export function normalizeTfngValue(value) {
  const text = normalizeLabelValue(value);
  if (text === "T" || text === "TRUE") {
    return "TRUE";
  }
  if (text === "F" || text === "FALSE") {
    return "FALSE";
  }
  if (text === "NG" || text === "NOT GIVEN" || text === "NOTGIVEN") {
    return "NOT GIVEN";
  }
  return "";
}

function normalizeMarkedValue(value) {
  if (value === true || value === false) {
    return value;
  }
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (normalized === "true" || normalized === "1") {
      return true;
    }
    if (normalized === "false" || normalized === "0" || normalized === "") {
      return false;
    }
  }
  if (typeof value === "number") {
    return value === 1;
  }
  return false;
}

export function normalizeMarkedMap(seed = {}) {
  const result = {};
  Object.keys(seed || {}).forEach((key) => {
    result[key] = normalizeMarkedValue(seed[key]);
  });
  return result;
}

export function parseOptions(raw) {
  let source = [];
  if (raw == null) {
    source = [];
  } else if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (trimmed) {
      try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) {
          source = parsed;
        } else if (parsed && Array.isArray(parsed.options)) {
          source = parsed.options;
        }
      } catch (_error) {
        source = [];
      }
    }
  } else if (Array.isArray(raw)) {
    source = raw;
  } else if (typeof raw === "object" && Array.isArray(raw.options)) {
    source = raw.options;
  }

  const result = [];
  source.forEach((item) => {
    if (item == null) {
      return;
    }
    let label = "";
    let content = "";
    if (typeof item === "object") {
      label = String(item.label ?? item.value ?? "").trim();
      content =
        item.content ??
        item.text ??
        item.value ??
        item.label ??
        item.Content ??
        "";
    } else {
      content = String(item);
    }

    const finalLabel = label || String.fromCharCode(65 + result.length);
    const finalContent = String(content ?? "").trim();
    if (!finalContent) {
      return;
    }

    result.push({
      label: finalLabel.trim(),
      content: finalContent,
      display: item && typeof item === "object" && item.display ? item.display : finalLabel.trim(),
    });
  });

  return result;
}

export function getMatchingItems(question) {
  if (!question || typeof question !== "object") {
    return [];
  }
  if (Array.isArray(question.questions)) {
    return question.questions;
  }
  if (Array.isArray(question.items)) {
    return question.items;
  }
  return [];
}

export function getTfngChoices(question) {
  if (question && Array.isArray(question.choices) && question.choices.length) {
    return question.choices
      .map((choice) => {
        if (typeof choice === "string") {
          const normalized = normalizeTfngValue(choice);
          return normalized ? { value: normalized, label: normalized } : null;
        }
        const normalized = normalizeTfngValue(choice?.value || choice?.label || "");
        if (!normalized) {
          return null;
        }
        return {
          value: normalized,
          label: String(choice?.label || choice?.value || normalized).toUpperCase(),
        };
      })
      .filter(Boolean);
  }
  if (question?.allowNG === false) {
    return [
      { value: "TRUE", label: "TRUE" },
      { value: "FALSE", label: "FALSE" },
    ];
  }
  return TFNG_CHOICES.map((item) => ({ ...item }));
}

function getGroupList(passage) {
  if (!passage || typeof passage !== "object") {
    return [];
  }
  if (Array.isArray(passage.groups)) {
    return passage.groups;
  }
  if (Array.isArray(passage.questionGroups)) {
    return passage.questionGroups;
  }
  if (Array.isArray(passage.sets)) {
    return passage.sets;
  }
  return [];
}

function getGroupQuestions(group) {
  if (!group || typeof group !== "object") {
    return [];
  }
  if (Array.isArray(group.questions)) {
    return group.questions;
  }
  if (Array.isArray(group.items)) {
    return group.items;
  }
  return [];
}

function findCorrectIndex(options, answer) {
  const answerText = String(answer ?? "").trim();
  if (!answerText) {
    return -1;
  }

  const asLabel = normalizeLabelValue(answerText);
  for (let index = 0; index < options.length; index += 1) {
    if (normalizeLabelValue(options[index].label) === asLabel) {
      return index;
    }
  }

  const asContent = normalizeTextValue(answerText);
  for (let index = 0; index < options.length; index += 1) {
    if (normalizeTextValue(options[index].content) === asContent) {
      return index;
    }
  }
  return -1;
}

function resolveTfngFromOptions(answer, options) {
  const targetLabel = normalizeLabelValue(answer);
  if (!targetLabel) {
    return "";
  }
  const parsedOptions = parseOptions(options);
  const matched = parsedOptions.find((item) => normalizeLabelValue(item.label) === targetLabel);
  if (!matched) {
    return "";
  }
  return normalizeTfngValue(matched.content || matched.label || "");
}

function normalizeTfngAnswer(question) {
  if (!question || question.type !== "tfng") {
    return;
  }
  const direct = normalizeTfngValue(question.answer);
  if (direct) {
    question.answer = direct;
    return;
  }

  let mapped = "";
  if (question.options) {
    mapped = resolveTfngFromOptions(question.answer, question.options);
  }
  if (!mapped) {
    const raw = normalizeLabelValue(question.answer);
    if (raw === "A") {
      mapped = "TRUE";
    } else if (raw === "B") {
      mapped = "FALSE";
    } else if (raw === "C") {
      mapped = "NOT GIVEN";
    }
  }
  if (mapped) {
    question.answer = mapped;
  }
}

function normalizeQuestion(question, context) {
  const normalized = { ...(question || {}) };
  const typeHint = String(normalized.type || context.groupType || "").toLowerCase();
  const optionHint = normalized.options || normalized.answerOptions || context.groupOptions;

  normalized.type = typeHint || (optionHint ? "single" : "blank");
  normalized.id = normalized.id || `${context.passageId}-Q${context.localIndex + 1}`;
  normalized.stem =
    normalized.stem || normalized.question || normalized.text || normalized.statement || "";

  if (!normalized.stem && normalized.type !== "cloze_inline") {
    normalized.stem = normalized.content || "";
  }

  normalized.options =
    normalized.options || normalized.answerOptions || context.groupOptions || null;
  if (context.groupChoices && !normalized.choices) {
    normalized.choices = context.groupChoices;
  }

  if (normalized.type === "cloze_inline") {
    let inlineContent = normalized.content || "";
    if (!inlineContent) {
      const stemText = String(normalized.stem || "");
      if (stemText.includes("{{") || stemText.includes("[[")) {
        inlineContent = stemText;
        normalized.stem = "";
      }
    }
    normalized.content = inlineContent;
    if (!Array.isArray(normalized.blanks) || !normalized.blanks.length) {
      const blanks = [];
      const matcher = /\{\{\s*([^}]+)\s*\}\}|\[\[\s*([^\]]+)\s*\]\]/g;
      let match;
      while ((match = matcher.exec(inlineContent)) !== null) {
        blanks.push({
          id: String(match[1] || match[2] || "").trim(),
          answer: "",
        });
      }
      normalized.blanks = blanks;
    }
  }

  normalized.passageId = context.passageId;
  normalized.passageTitle = context.passageTitle;
  normalized.passageIndex = context.passageIndex;
  normalized.localIndex = context.localIndex;
  normalized.section = normalized.section || context.section;
  normalized.difficulty =
    normalized.difficulty || context.groupDifficulty || context.passageDifficulty || "Standard";
  normalized.groupId = context.groupId || null;
  normalized.groupTitle = context.groupTitle || "";
  normalized.groupInstructions = context.groupInstructions || "";
  normalized.groupIndex = context.groupIndex;
  normalized.groupLocalIndex = context.groupLocalIndex;

  if (normalized.type === "tfng") {
    normalizeTfngAnswer(normalized);
  }

  return normalized;
}

function convertLegacyPayload(payload) {
  const reserved = { module: true, passages: true };
  const passages = [];
  if (!payload || typeof payload !== "object") {
    return passages;
  }

  Object.keys(payload).forEach((sectionKey) => {
    if (reserved[sectionKey]) {
      return;
    }
    const difficultyMap = payload[sectionKey];
    if (!difficultyMap || typeof difficultyMap !== "object") {
      return;
    }
    Object.keys(difficultyMap).forEach((difficultyKey) => {
      const questionList = Array.isArray(difficultyMap[difficultyKey])
        ? difficultyMap[difficultyKey]
        : [];
      if (!questionList.length) {
        return;
      }
      passages.push({
        id: `${sectionKey}-${difficultyKey}`,
        title: `${sectionKey} ${difficultyKey}`,
        content: questionList[0]?.stimulus || "",
        questions: questionList,
      });
    });
  });

  return passages;
}

export function normalizeExamData(payload) {
  const parsed = safeParseJsonLike(payload);
  let base = parsed;
  if (!base || !Array.isArray(base.passages) || !base.passages.length) {
    const legacyPassages = convertLegacyPayload(base);
    if (legacyPassages.length) {
      base = {
        module: base?.module || "Practice",
        passages: legacyPassages,
      };
    } else {
      base = { module: "Practice", passages: [] };
    }
  }

  const moduleName = base.module || "Practice";
  const normalizedPassages = (base.passages || []).map((passage, passageIndex) => {
    const passageId = passage.id || `P${passageIndex + 1}`;
    const title = passage.title || `Passage ${passageIndex + 1}`;
    const content = passage.content || passage.stimulus || "";
    const audio = passage.audio || passage.audioUrl || passage.audio_url || "";
    const instructions = passage.instructions || passage.instruction || "";
    const groupList = getGroupList(passage);
    let localIndex = 0;
    let flatQuestions = [];
    let normalizedGroups = [];

    if (groupList.length) {
      normalizedGroups = groupList
        .map((group, groupIndex) => {
          const groupId = group.id || `${passageId}-G${groupIndex + 1}`;
          const groupTitle = group.title || group.name || group.label || "";
          const groupInstructions = group.instructions || group.instruction || group.stem || "";
          const groupType = String(group.type || "").toLowerCase();
          const groupOptions = group.options || group.answerOptions || null;
          const groupChoices = group.choices || null;
          const groupQuestions = getGroupQuestions(group);
          const normalizedQuestions = groupQuestions.map((entry, questionIndex) => {
            const normalizedQuestion = normalizeQuestion(entry, {
              passageId,
              passageTitle: title,
              passageIndex,
              passageDifficulty: passage.difficulty,
              section: moduleName,
              groupId,
              groupTitle,
              groupInstructions,
              groupIndex,
              groupLocalIndex: questionIndex,
              groupType,
              groupOptions,
              groupChoices,
              groupDifficulty: group.difficulty,
              localIndex,
            });
            localIndex += 1;
            flatQuestions.push(normalizedQuestion);
            return normalizedQuestion;
          });

          return {
            id: groupId,
            title: groupTitle,
            instructions: groupInstructions,
            type: groupType,
            options: groupOptions,
            choices: groupChoices,
            index: groupIndex,
            questions: normalizedQuestions,
          };
        })
        .filter((group) => group.questions.length > 0);
    }

    if (!flatQuestions.length) {
      const directQuestions = Array.isArray(passage.questions) ? passage.questions : [];
      flatQuestions = directQuestions.map((entry, questionIndex) =>
        normalizeQuestion(entry, {
          passageId,
          passageTitle: title,
          passageIndex,
          passageDifficulty: passage.difficulty,
          section: moduleName,
          groupId: null,
          groupTitle: "",
          groupInstructions: "",
          groupIndex: null,
          groupLocalIndex: questionIndex,
          groupType: "",
          groupOptions: null,
          groupChoices: null,
          groupDifficulty: null,
          localIndex: questionIndex,
        })
      );
    }

    return {
      id: passageId,
      title,
      content,
      audio,
      instructions,
      groups: normalizedGroups,
      questions: flatQuestions,
    };
  });

  return {
    module: moduleName,
    passages: normalizedPassages,
  };
}

export function buildQuestionStore(examData) {
  const questionMap = {};
  const questions = [];
  (examData?.passages || []).forEach((passage) => {
    passage.questions = (passage.questions || []).map((question) => {
      const current = { ...question };
      current.globalIndex = questions.length;
      current.displayNo = questions.length + 1;
      questionMap[current.id] = current;
      questions.push(current);
      return current;
    });
    passage.groups = (passage.groups || []).map((group) => ({
      ...group,
      questions: (group.questions || []).map((question) => questionMap[question.id] || question),
    }));
  });

  return {
    examData,
    questions,
    questionMap,
  };
}

export function getDefaultAnswerValue(question) {
  const type = inferQuestionType(question);
  if (type === "multiple") {
    return [];
  }
  if (type === "cloze_inline" || type === "matching") {
    return {};
  }
  if (type === "blank" || type === "essay") {
    return "";
  }
  return null;
}

export function buildInitialAnswersMap(questions, seed = {}) {
  const result = {};
  (questions || []).forEach((question) => {
    const seededValue = seed?.[question.id];
    const type = inferQuestionType(question);
    if (seededValue === undefined) {
      result[question.id] = getDefaultAnswerValue(question);
      return;
    }

    if (type === "multiple") {
      const values = Array.isArray(seededValue)
        ? seededValue
        : seededValue == null
        ? []
        : String(seededValue)
            .split(/[,;|/]/)
            .map((item) => item.trim())
            .filter(Boolean);
      result[question.id] = values.map((item) => normalizeLabelValue(item));
      return;
    }

    if (type === "cloze_inline" || type === "matching") {
      result[question.id] =
        seededValue && typeof seededValue === "object" && !Array.isArray(seededValue)
          ? { ...seededValue }
          : {};
      return;
    }

    if (type === "blank" || type === "essay") {
      result[question.id] = seededValue == null ? "" : String(seededValue);
      return;
    }

    result[question.id] = seededValue;
  });

  return result;
}

export function buildInitialMarkedMap(questions, seed = {}) {
  const normalized = normalizeMarkedMap(seed);
  const result = {};
  (questions || []).forEach((question) => {
    result[question.id] = normalized[question.id] || false;
  });
  return result;
}

export function hasQuestionAnswer(question, answerValue) {
  const type = inferQuestionType(question);
  if (type === "matching") {
    const items = getMatchingItems(question);
    if (!items.length || !answerValue || typeof answerValue !== "object" || Array.isArray(answerValue)) {
      return false;
    }
    return items.every((item) => {
      const value = answerValue[item.id];
      return value != null && String(value).trim() !== "";
    });
  }

  if (type === "cloze_inline") {
    const blanks = Array.isArray(question?.blanks) ? question.blanks : [];
    if (!blanks.length || !answerValue || typeof answerValue !== "object" || Array.isArray(answerValue)) {
      return false;
    }
    return blanks.every((blank) => {
      const value = answerValue[blank.id];
      return value != null && String(value).trim() !== "";
    });
  }

  if (Array.isArray(answerValue)) {
    return answerValue.length > 0;
  }

  return answerValue != null && String(answerValue).trim() !== "";
}

function normalizeMultiAnswerValues(answer) {
  if (answer == null) {
    return [];
  }
  const values = Array.isArray(answer) ? answer : String(answer).split(/[,;|/]+/);
  const deduped = [];
  const seen = new Set();
  values.forEach((item) => {
    const normalized = normalizeLabelValue(item);
    if (normalized && !seen.has(normalized)) {
      seen.add(normalized);
      deduped.push(normalized);
    }
  });
  return deduped;
}

function splitExpectedTextVariants(answer) {
  if (answer == null) {
    return [];
  }
  const text = String(answer).trim();
  if (!text) {
    return [];
  }

  const parts = text
    .split(/[|;/]/)
    .flatMap((part) => part.split(/\s+or\s+/i))
    .map((part) => normalizeTextValue(part))
    .filter(Boolean);

  return [...new Set(parts)];
}

export function compareTextAnswer(userAnswer, expectedAnswer) {
  const userNormalized = normalizeTextValue(userAnswer);
  if (!userNormalized) {
    return false;
  }
  const variants = splitExpectedTextVariants(expectedAnswer);
  return variants.includes(userNormalized);
}

export function inferQuestionType(question) {
  const rawType = String(question?.type || "").trim().toLowerCase();
  if (
    rawType === "single" ||
    rawType === "multiple" ||
    rawType === "tfng" ||
    rawType === "blank" ||
    rawType === "essay" ||
    rawType === "cloze_inline" ||
    rawType === "matching"
  ) {
    return rawType;
  }

  if (rawType === "multiplechoice" || rawType === "single_choice") {
    return "single";
  }
  if (rawType === "multi" || rawType === "multiple_choice") {
    return "multiple";
  }
  if (rawType === "truefalse" || rawType === "true_false" || rawType === "tf" || rawType === "not_given") {
    return "tfng";
  }

  if (Array.isArray(question?.blanks) && question.blanks.length) {
    return "cloze_inline";
  }
  if (Array.isArray(question?.questions) && question.questions.length) {
    return "matching";
  }
  if (Array.isArray(question?.items) && question.items.length) {
    return "matching";
  }

  const options = parseOptions(question?.options);
  const answerText = String(question?.answer || "").trim();
  const answerUpper = normalizeLabelValue(answerText);
  if (options.length) {
    if (answerUpper === "TRUE" || answerUpper === "FALSE" || answerUpper === "NOT GIVEN") {
      return "tfng";
    }
    const optionLabels = new Set(options.map((option) => normalizeLabelValue(option.label)));
    if (Array.isArray(question?.answer)) {
      return "multiple";
    }
    if (/[,;|/]/.test(answerText) && !optionLabels.has(answerUpper)) {
      return "multiple";
    }
    return "single";
  }

  if (question?.wordLimit || question?.modelAnswer) {
    return "essay";
  }

  return "blank";
}

export function evaluateQuestion(question, answerValue) {
  const questionType = inferQuestionType(question);
  let answered = false;
  let correct = false;
  let gradable = true;

  if (questionType === "matching") {
    const items = getMatchingItems(question);
    const userMap = answerValue && typeof answerValue === "object" && !Array.isArray(answerValue)
      ? answerValue
      : {};
    const expectedMap = Object.fromEntries(
      items
        .filter((item) => item && typeof item === "object")
        .map((item) => [String(item.id), item.answer])
    );
    answered =
      Object.keys(expectedMap).length > 0 &&
      Object.keys(expectedMap).every((key) => normalizeTextValue(userMap[key]));
    if (Object.keys(expectedMap).length) {
      correct = Object.keys(expectedMap).every(
        (key) => normalizeLabelValue(userMap[key]) === normalizeLabelValue(expectedMap[key])
      );
    } else {
      gradable = false;
    }
    return { type: questionType, answered, correct, gradable };
  }

  if (questionType === "cloze_inline") {
    const blanks = Array.isArray(question?.blanks) ? question.blanks : [];
    const userMap = answerValue && typeof answerValue === "object" && !Array.isArray(answerValue)
      ? answerValue
      : {};
    const expectedMap = Object.fromEntries(
      blanks
        .filter((item) => item && typeof item === "object")
        .map((item) => [String(item.id), item.answer])
    );
    answered =
      Object.keys(expectedMap).length > 0 &&
      Object.keys(expectedMap).every((key) => normalizeTextValue(userMap[key]));
    if (Object.keys(expectedMap).length) {
      correct = Object.keys(expectedMap).every((key) =>
        compareTextAnswer(userMap[key], expectedMap[key])
      );
    } else {
      gradable = false;
    }
    return { type: questionType, answered, correct, gradable };
  }

  if (questionType === "multiple") {
    const expected = new Set(normalizeMultiAnswerValues(question?.answer));
    const userValues = Array.isArray(answerValue)
      ? answerValue
      : answerValue == null
      ? []
      : [answerValue];
    const userSet = new Set(normalizeMultiAnswerValues(userValues));
    answered = userSet.size > 0;
    if (expected.size > 0) {
      correct =
        expected.size === userSet.size &&
        [...expected].every((item) => userSet.has(item));
    } else {
      gradable = false;
    }
    return { type: questionType, answered, correct, gradable };
  }

  if (questionType === "tfng") {
    const expected = normalizeTfngValue(question?.answer);
    const user = normalizeTfngValue(answerValue);
    answered = Boolean(user);
    if (expected) {
      correct = user === expected;
    } else {
      gradable = false;
    }
    return { type: questionType, answered, correct, gradable };
  }

  if (questionType === "single") {
    const options = parseOptions(question?.options);
    const answerText = String(question?.answer || "").trim();
    const expectedTextNormalized = normalizeTextValue(answerText);
    let expectedLabel = "";
    const labelSet = new Set(options.map((option) => normalizeLabelValue(option.label)));
    if (labelSet.has(normalizeLabelValue(answerText))) {
      expectedLabel = normalizeLabelValue(answerText);
    } else {
      const matched = options.find(
        (option) => expectedTextNormalized && normalizeTextValue(option.content) === expectedTextNormalized
      );
      if (matched) {
        expectedLabel = normalizeLabelValue(matched.label);
      }
    }

    const userLabel = normalizeLabelValue(answerValue);
    answered = Boolean(normalizeTextValue(answerValue));
    if (expectedLabel) {
      correct = userLabel === expectedLabel;
    } else if (expectedTextNormalized) {
      correct = normalizeTextValue(answerValue) === expectedTextNormalized;
    } else {
      gradable = false;
    }
    return { type: questionType, answered, correct, gradable };
  }

  if (questionType === "essay") {
    answered = Boolean(normalizeTextValue(answerValue));
    return { type: questionType, answered, correct: false, gradable: false };
  }

  const expected = question?.answer;
  answered = Boolean(normalizeTextValue(answerValue));
  if (normalizeTextValue(expected)) {
    correct = compareTextAnswer(answerValue, expected);
  } else {
    gradable = false;
  }
  return { type: questionType, answered, correct, gradable };
}

export function evaluateQuestionMap(questions, answersMap) {
  return (questions || []).reduce((accumulator, question) => {
    accumulator[question.id] = evaluateQuestion(question, answersMap?.[question.id]);
    return accumulator;
  }, {});
}

export function getOptions(question) {
  if (!question) {
    return [];
  }
  if (question.type === "tfng") {
    return getTfngChoices(question).map((choice) => ({
      label: choice.value,
      content: choice.label,
      display: choice.label,
    }));
  }
  return parseOptions(question.options);
}

export function getCorrectLabels(question) {
  if (!question) {
    return [];
  }
  const type = inferQuestionType(question);
  if (type === "multiple") {
    return normalizeMultiAnswerValues(question.answer);
  }
  if (type === "tfng") {
    const normalized = normalizeTfngValue(question.answer);
    return normalized ? [normalized] : [];
  }
  if (type === "single") {
    const options = getOptions(question);
    const index = findCorrectIndex(options, question.answer);
    if (index !== -1) {
      return [normalizeLabelValue(options[index].label)];
    }
    const fallback = String(question.answer ?? "").trim();
    return fallback ? [normalizeLabelValue(fallback)] : [];
  }
  if (type === "blank" || type === "essay") {
    const text = String(question.answer || question.modelAnswer || "").trim();
    return text ? [text] : [];
  }
  const raw = String(question.answer || "").trim();
  return raw ? [raw] : [];
}

export function isCorrectChoice(question, choiceLabel) {
  const type = inferQuestionType(question);
  if (type === "tfng") {
    return getCorrectLabels(question).includes(normalizeTfngValue(choiceLabel));
  }
  return getCorrectLabels(question).includes(normalizeLabelValue(choiceLabel));
}

export function getCorrectForBlank(question, blankId) {
  if (!question || !Array.isArray(question.blanks)) {
    return "";
  }
  const target = question.blanks.find((item) => String(item?.id) === String(blankId));
  return target ? String(target.answer || "") : "";
}

export function getAnswerDisplayText(question) {
  if (!question) {
    return "";
  }

  const type = inferQuestionType(question);
  if (type === "tfng") {
    return normalizeTfngValue(question.answer);
  }
  if (type === "cloze_inline") {
    const blanks = Array.isArray(question.blanks) ? question.blanks : [];
    return blanks
      .map((blank, index) => {
        const label = blank.label || blank.id || `Blank ${index + 1}`;
        return `${label}: ${blank.answer || ""}`;
      })
      .join(" / ");
  }
  if (type === "matching") {
    const items = getMatchingItems(question);
    return items
      .map((item) => `${String(item.id)}: ${item.answer || ""}`)
      .join(" / ");
  }
  if (type === "multiple" || type === "single") {
    const options = getOptions(question);
    const labels = getCorrectLabels(question);
    if (!labels.length) {
      return String(question.answer || "").trim();
    }
    return labels
      .map((label) => {
        const matched = options.find(
          (option) => normalizeLabelValue(option.label) === normalizeLabelValue(label)
        );
        return matched ? `${label}. ${htmlToPlainText(matched.content).trim()}` : label;
      })
      .join(" / ");
  }
  if (type === "blank" || type === "essay") {
    return String(question.answer || question.modelAnswer || "").trim();
  }
  return String(question.answer || "").trim();
}

export function getQuestionDisplayNo(question) {
  if (!question) {
    return "";
  }
  return String(question.question_no || question.displayNo || "").trim();
}

export function formatQuestionTypeLabel(value) {
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

export function formatStatTypeLabel(value) {
  const text = String(value || "").trim().toLowerCase();
  if (!text) {
    return "";
  }
  if (text === "sentence_completion") return "句子填空";
  if (text === "form_completion") return "表格填空";
  if (text === "note_completion") return "笔记填空";
  if (text === "summary_completion") return "摘要填空";
  if (text === "true_false_not_given") return "判断题";
  if (text === "matching") return "匹配题";
  if (text === "multiple_choice") return "选择题";
  return value;
}
