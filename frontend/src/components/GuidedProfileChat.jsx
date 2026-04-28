import React, { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "motion/react";
import { ArrowRight, Send, Sparkles, UserRound, X } from "lucide-react";
import {
  exitGuidedProfileSession,
  getCurrentGuidedProfileSession,
  submitGuidedProfileAnswer,
} from "../api/studentProfileGuided";
import { LoadingOverlay } from "./LoadingPage";

function getErrorMessage(error, fallback) {
  const detail = error?.response?.data?.detail;
  return typeof detail === "string" ? detail : fallback;
}

function getExistingAnswer(bundle, questionCode) {
  const answerRow = (bundle?.answers || []).find((item) => item.question_code === questionCode);
  return answerRow?.answer_json || {};
}

function buildEmptyRepeatableRow(question) {
  return Object.fromEntries((question?.row_fields || []).map((field) => [field.name, ""]));
}

function sanitizeNumericInput(value, inputType) {
  const text = String(value ?? "");
  if (inputType === "integer") {
    return text.replace(/\D/g, "");
  }
  if (inputType === "number") {
    const numericText = text.replace(/[^\d.]/g, "");
    const [integerPart, ...decimalParts] = numericText.split(".");
    return decimalParts.length > 0 ? `${integerPart}.${decimalParts.join("")}` : integerPart;
  }
  return text;
}

function resolveInputType(inputType) {
  return inputType === "date" ? "date" : "text";
}

function resolveInputMode(inputType) {
  if (inputType === "integer") {
    return "numeric";
  }
  if (inputType === "number") {
    return "decimal";
  }
  return undefined;
}

function resolveInputPattern(inputType) {
  return inputType === "integer" ? "[0-9]*" : undefined;
}

const REPEATABLE_UNIQUE_SELECT_FIELD_NAMES = new Set(["subject", "subject_id", "course_id", "ap_course_id"]);

function isUniqueRepeatableSelectField(field) {
  return field?.kind === "select" && REPEATABLE_UNIQUE_SELECT_FIELD_NAMES.has(field?.name);
}

function resolveRepeatableRowField(question, rows, row, rowIndex, field) {
  if (!isUniqueRepeatableSelectField(field)) {
    return field;
  }
  const currentValue = typeof row?.[field.name] === "string" ? row[field.name].trim() : "";
  const selectedValues = new Set(
    rows
      .filter((_, index) => index !== rowIndex)
      .map((item) => (typeof item?.[field.name] === "string" ? item[field.name].trim() : ""))
      .filter((value) => value)
  );
  return {
    ...field,
    options: (field.options || []).filter((option) => option.value === currentValue || !selectedValues.has(option.value)),
  };
}

function hasValue(value) {
  return String(value ?? "").trim() !== "";
}

function hasAnyRowValue(row) {
  return Object.values(row || {}).some(hasValue);
}

function shouldValidateRequiredField(question, answerValue, field) {
  if (!field?.required) {
    return false;
  }
  if (
    ["Q13", "Q14", "Q15"].includes(question?.code) &&
    field.name !== "has_experience" &&
    answerValue?.has_experience !== "yes"
  ) {
    return false;
  }
  return true;
}

function collectMissingRequiredLabels(question, answerValue) {
  if (!question || answerValue?.skip || answerValue?.skipped) {
    return [];
  }
  const missing = [];
  const collect = (fields, source, prefix = "") => {
    (fields || []).forEach((field) => {
      if (shouldValidateRequiredField(question, source, field) && !hasValue(source?.[field.name])) {
        missing.push(`${prefix}${field.label}`);
      }
    });
  };

  if (question.type === "repeatable_form") {
    collect(question.fields, answerValue);
    const rows = Array.isArray(answerValue?.rows) ? answerValue.rows : [];
    const minRows = Number(question.min_rows || 0);
    if (minRows > 0 && rows.length === 0) {
      missing.push("至少填写一条成绩");
    }
    const rowsToCheck = Math.max(rows.length, minRows);
    for (let index = 0; index < rowsToCheck; index += 1) {
      const row = rows[index] || {};
      if (index < minRows || hasAnyRowValue(row)) {
        collect(question.row_fields, row, `第${index + 1}条`);
      }
    }
    return missing;
  }

  collect(question.fields, answerValue);
  return missing;
}

function buildInitialAnswer(question, bundle) {
  if (!question) {
    return {};
  }
  const existingAnswer = getExistingAnswer(bundle, question.code);
  if (Object.keys(existingAnswer).length > 0 && !existingAnswer.skipped) {
    if (question.type === "single") {
      return {
        selected_value: existingAnswer.selected_value || "",
        custom_text: existingAnswer.custom_text || "",
      };
    }
    if (question.type === "multi") {
      return {
        selected_values: Array.isArray(existingAnswer.selected_values) ? existingAnswer.selected_values : [],
        custom_text: existingAnswer.custom_text || "",
      };
    }
    if (question.type === "repeatable_form") {
      const normalizedAnswer = Object.fromEntries((question.fields || []).map((field) => [field.name, existingAnswer[field.name] || ""]));
      const rows = Array.isArray(existingAnswer.rows)
        ? existingAnswer.rows.map((row) => ({
            ...buildEmptyRepeatableRow(question),
            ...row,
          }))
        : [];
      return {
        ...normalizedAnswer,
        rows: rows.length > 0 ? rows : [buildEmptyRepeatableRow(question)],
      };
    }
    const normalizedAnswer = Object.fromEntries((question.fields || []).map((field) => [field.name, existingAnswer[field.name] || ""]));
    if (question.code === "Q8") {
      if (!normalizedAnswer.average_score_100 && existingAnswer.average_score) {
        normalizedAnswer.average_score_100 = existingAnswer.average_score;
      }
      if (!normalizedAnswer.chs_subject_1_score_100 && existingAnswer.chinese_score) {
        normalizedAnswer.chs_subject_1_id = "CHS_CHINESE";
        normalizedAnswer.chs_subject_1_score_100 = existingAnswer.chinese_score;
      }
      if (!normalizedAnswer.chs_subject_2_score_100 && existingAnswer.math_score) {
        normalizedAnswer.chs_subject_2_id = "CHS_MATH";
        normalizedAnswer.chs_subject_2_score_100 = existingAnswer.math_score;
      }
      if (!normalizedAnswer.chs_subject_3_score_100 && existingAnswer.english_score) {
        normalizedAnswer.chs_subject_3_id = "CHS_ENGLISH";
        normalizedAnswer.chs_subject_3_score_100 = existingAnswer.english_score;
      }
    }
    return normalizedAnswer;
  }
  if (question.type === "single") {
    return { selected_value: "", custom_text: "" };
  }
  if (question.type === "multi") {
    return {
      selected_values: Array.isArray(question.default_selected_values) ? question.default_selected_values : [],
      custom_text: "",
    };
  }
  if (question.type === "repeatable_form") {
    return {
      ...Object.fromEntries((question.fields || []).map((field) => [field.name, ""])),
      rows: [buildEmptyRepeatableRow(question)],
    };
  }
  return Object.fromEntries((question.fields || []).map((field) => [field.name, ""]));
}

function isResultReady(bundle) {
  const status = bundle?.session?.session_status;
  return Boolean(bundle?.result && (status === "completed" || status === "exited"));
}

function isSameMessage(left, right) {
  return (
    left?.sequence_no === right?.sequence_no &&
    left?.message_role === right?.message_role &&
    left?.message_kind === right?.message_kind &&
    left?.question_code === right?.question_code &&
    left?.content === right?.content
  );
}

function dedupeRenderableMessages(messages) {
  const seenAssistantQuestions = new Set();
  return messages.filter((message) => {
    if (message?.message_role !== "assistant" || message?.message_kind !== "question" || !message?.question_code) {
      return true;
    }
    if (seenAssistantQuestions.has(message.question_code)) {
      return false;
    }
    seenAssistantQuestions.add(message.question_code);
    return true;
  });
}

export default function GuidedProfileChat({ onClose, onResultReady }) {
  const [bundle, setBundle] = useState(null);
  const [editingQuestionCode, setEditingQuestionCode] = useState(null);
  const [answer, setAnswer] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [actionHint, setActionHint] = useState("");
  const [visibleMessages, setVisibleMessages] = useState([]);
  const [multiSearchTextByCode, setMultiSearchTextByCode] = useState({});
  const bodyRef = useRef(null);
  const revealTimeoutsRef = useRef([]);
  const actionHintTimeoutRef = useRef(null);
  const previousMessagesRef = useRef([]);
  const previousVisibleCountRef = useRef(0);

  const session = bundle?.session || null;
  const currentQuestion = bundle?.current_question || null;
  const messages = useMemo(() => dedupeRenderableMessages(bundle?.messages || []), [bundle]);
  const displayedMessages = useMemo(() => {
    if (!editingQuestionCode) {
      return visibleMessages;
    }
    const targetIndex = visibleMessages.findIndex(
      (message) =>
        message?.message_role === "assistant" &&
        message?.message_kind === "question" &&
        message?.question_code === editingQuestionCode
    );
    return targetIndex >= 0 ? visibleMessages.slice(0, targetIndex + 1) : visibleMessages;
  }, [editingQuestionCode, visibleMessages]);
  const answerByCode = useMemo(() => {
    return Object.fromEntries((bundle?.answers || []).map((item) => [item.question_code, item.answer_json || {}]));
  }, [bundle?.answers]);
  const questionByCode = useMemo(() => {
    const result = {};
    (bundle?.questions || []).forEach((question) => {
      result[question.code] = question;
    });
    messages.forEach((message) => {
      if (message.question_code && message.payload_json && typeof message.payload_json === "object") {
        result[message.question_code] = {
          ...message.payload_json,
          ...(result[message.question_code] || {}),
        };
      }
    });
    return result;
  }, [bundle?.questions, messages]);
  const activeQuestion = editingQuestionCode ? questionByCode[editingQuestionCode] || null : currentQuestion;

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setErrorMessage("");
      try {
        const response = await getCurrentGuidedProfileSession(true);
        if (!active) {
          return;
        }
        setBundle(response.data);
      } catch (error) {
        if (active) {
          setErrorMessage(getErrorMessage(error, "加载快速建档失败"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setAnswer(buildInitialAnswer(activeQuestion, bundle));
  }, [activeQuestion, bundle]);

  useEffect(() => {
    return () => {
      revealTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
      revealTimeoutsRef.current = [];
      if (actionHintTimeoutRef.current) {
        window.clearTimeout(actionHintTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (loading) {
      return;
    }
    revealTimeoutsRef.current.forEach((timeoutId) => window.clearTimeout(timeoutId));
    revealTimeoutsRef.current = [];

    const previousMessages = previousMessagesRef.current;
    const nextMessages = messages;
    const isAppendOnly =
      previousMessages.length > 0 &&
      nextMessages.length > previousMessages.length &&
      previousMessages.every((message, index) => isSameMessage(message, nextMessages[index]));

    if (!isAppendOnly) {
      setVisibleMessages(nextMessages);
      previousMessagesRef.current = nextMessages;
      return;
    }

    setVisibleMessages(previousMessages);
    nextMessages.slice(previousMessages.length).forEach((message, index) => {
      const timeoutId = window.setTimeout(() => {
        setVisibleMessages((current) => {
          if (current.some((item) => isSameMessage(item, message))) {
            return current;
          }
          return [...current, message];
        });
      }, 100 * (index + 1));
      revealTimeoutsRef.current.push(timeoutId);
    });
    previousMessagesRef.current = nextMessages;
  }, [loading, messages]);

  useEffect(() => {
    if (loading) {
      return;
    }
    const container = bodyRef.current;
    if (!container) {
      return;
    }
    const shouldSmoothScroll = previousVisibleCountRef.current > 0 && visibleMessages.length > previousVisibleCountRef.current;
    const rafId = window.requestAnimationFrame(() => {
      container.scrollTo({
        top: container.scrollHeight,
        behavior: shouldSmoothScroll ? "smooth" : "auto",
      });
      previousVisibleCountRef.current = visibleMessages.length;
    });
    return () => window.cancelAnimationFrame(rafId);
  }, [loading, visibleMessages.length, activeQuestion?.code]);

  function applyNextBundle(nextBundle) {
    setEditingQuestionCode(null);
    setBundle(nextBundle);
    if (isResultReady(nextBundle)) {
      onResultReady?.(nextBundle.result, nextBundle.session);
    }
  }

  function showActionHint(message) {
    setActionHint(message);
    if (actionHintTimeoutRef.current) {
      window.clearTimeout(actionHintTimeoutRef.current);
    }
    actionHintTimeoutRef.current = window.setTimeout(() => {
      setActionHint("");
      actionHintTimeoutRef.current = null;
    }, 2200);
  }

  async function submitAnswer(nextAnswer = answer) {
    if (!session?.session_id || !activeQuestion) {
      return;
    }
    const missingRequiredLabels = collectMissingRequiredLabels(activeQuestion, nextAnswer);
    if (missingRequiredLabels.length > 0) {
      showActionHint("\u8bf7\u586b\u5199\u5fc5\u586b\u9879\uff1a" + missingRequiredLabels.join("\u3001"));
      return;
    }
    const totalQuestions = Number(activeQuestion.total || bundle?.questions?.length || 0);
    const willComplete =
      !editingQuestionCode &&
      Number(activeQuestion.index || 0) > 0 &&
      totalQuestions > 0 &&
      Number(activeQuestion.index || 0) >= totalQuestions;
    setSaving(true);
    setFinalizing(willComplete);
    setErrorMessage("");
    setActionHint("");
    try {
      const response = await submitGuidedProfileAnswer(session.session_id, activeQuestion.code, nextAnswer);
      applyNextBundle(response.data);
    } catch (error) {
      const message = getErrorMessage(error, "保存答案失败");
      if (message.includes("\u8bf7\u586b\u5199\u5fc5\u586b\u9879")) {
        showActionHint(message);
        return;
      }
      if (message.includes("\u8bf7\u5148\u586b\u5199")) {
        showActionHint(message);
        return;
      }
      if (message.includes("请填写必填项")) {
        showActionHint(message);
        return;
      }
      if (message.includes("请先填写") || message.includes("璇峰厛濉啓")) {
        showActionHint(message);
      } else {
        setErrorMessage(message);
      }
    } finally {
      setSaving(false);
      setFinalizing(false);
    }
  }

  async function skipQuestion() {
    await submitAnswer({ skip: true });
  }

  async function finishNow() {
    if (!session?.session_id) {
      return;
    }
    setSaving(true);
    setErrorMessage("");
    setActionHint("");
    try {
      const response = await exitGuidedProfileSession(session.session_id, "manual_exit");
      applyNextBundle(response.data);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "生成六维图失败"));
    } finally {
      setSaving(false);
    }
  }

  function handleEditQuestion(questionCode) {
    if (!questionCode) {
      return;
    }
    setEditingQuestionCode(questionCode);
    setErrorMessage("");
    setActionHint("");
  }

  function handleBackToPreviousQuestion() {
    const questionCodes = (bundle?.questions || []).map((question) => question.code);
    const anchorCode = activeQuestion?.code || currentQuestion?.code;
    const anchorIndex = questionCodes.indexOf(anchorCode);
    if (anchorIndex <= 0) {
      return;
    }
    setEditingQuestionCode(questionCodes[anchorIndex - 1]);
    setErrorMessage("");
    setActionHint("");
  }

  function renderSingle(question, options = {}) {
    const targetAnswer = options.answerValue || answer;
    const disabled = Boolean(options.disabled);
    const shouldShowCustomInput =
      question.allow_custom_text || targetAnswer.selected_value === "OTHER" || targetAnswer.selected_value === "CUSTOM";
    return (
      <div className="guided-chat-options">
        <select
          className="guided-chat-input"
          value={targetAnswer.selected_value || ""}
          disabled={disabled}
          onChange={(event) => {
            if (!disabled) {
              const nextValue = event.target.value;
              setAnswer((previous) => ({
                ...previous,
                selected_value: nextValue,
                custom_text:
                  question.allow_custom_text || nextValue === "OTHER" || nextValue === "CUSTOM"
                    ? previous.custom_text || ""
                    : "",
              }));
            }
          }}
        >
          <option value="">请选择</option>
          {(question.options || []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        {shouldShowCustomInput ? (
          <input
            className="guided-chat-input"
            type={resolveInputType(question.custom_input_type)}
            inputMode={resolveInputMode(question.custom_input_type)}
            pattern={resolveInputPattern(question.custom_input_type)}
            value={targetAnswer.custom_text || ""}
            disabled={disabled}
            onChange={(event) =>
              setAnswer((previous) => ({
                ...previous,
                custom_text: sanitizeNumericInput(event.target.value, question.custom_input_type),
              }))
            }
            placeholder={question.custom_placeholder || "补充说明"}
          />
        ) : null}
      </div>
    );
  }

  function renderMulti(question, options = {}) {
    const targetAnswer = options.answerValue || answer;
    const disabled = Boolean(options.disabled);
    const optionList = question.options || [];
    const selectedValues = Array.isArray(targetAnswer.selected_values) ? targetAnswer.selected_values : [];
    const maxSelect = question.max_select || 99;
    const exclusiveValues = new Set(question.exclusive_option_values || []);
    const optionByValue = new Map(optionList.map((option) => [option.value, option]));
    const selectedOptions = selectedValues.map((value) => optionByValue.get(value) || { value, label: value });
    const searchText = multiSearchTextByCode[question.code] || "";
    const normalizedSearchText = searchText.trim().toLowerCase();
    const filteredOptions =
      question.searchable && normalizedSearchText
        ? optionList.filter((option) => {
            const searchableText = `${option.label || ""} ${option.value || ""}`.toLowerCase();
            return searchableText.includes(normalizedSearchText);
          })
        : optionList;

    function toggleOption(optionValue) {
      if (disabled) {
        return;
      }
      setAnswer((previous) => {
        const values = Array.isArray(previous.selected_values) ? previous.selected_values : [];
        if (values.includes(optionValue)) {
          return {
            ...previous,
            selected_values: values.filter((value) => value !== optionValue),
          };
        }
        const nextValues = exclusiveValues.has(optionValue)
          ? [optionValue]
          : [...values.filter((value) => !exclusiveValues.has(value)), optionValue].slice(0, maxSelect);
        return {
          ...previous,
          selected_values: nextValues,
        };
      });
    }

    if (disabled) {
      return (
        <div className="guided-chat-multi-control">
          <div className="guided-chat-selected-tags">
            {selectedOptions.length > 0 ? (
              selectedOptions.map((option) => (
                <span key={option.value} className="guided-chat-selected-tag">
                  {option.label}
                </span>
              ))
            ) : (
              <span className="guided-chat-empty-selection">未填写</span>
            )}
          </div>
          {selectedValues.includes("OTHER") && targetAnswer.custom_text ? (
            <input className="guided-chat-input" value={targetAnswer.custom_text || ""} disabled />
          ) : null}
        </div>
      );
    }

    return (
      <div className={question.searchable ? "guided-chat-multi-control" : "guided-chat-options"}>
        {question.searchable ? (
          <>
            <input
              className="guided-chat-input"
              value={searchText}
              onChange={(event) =>
                setMultiSearchTextByCode((previous) => ({
                  ...previous,
                  [question.code]: event.target.value,
                }))
              }
              placeholder="输入关键词搜索选项"
            />
            <div className="guided-chat-selected-tags">
              {selectedOptions.length > 0 ? (
                selectedOptions.map((option) => (
                  <button
                    type="button"
                    key={option.value}
                    className="guided-chat-selected-tag"
                    onClick={() => toggleOption(option.value)}
                  >
                    {option.label}
                    <X size={12} />
                  </button>
                ))
              ) : (
                <span className="guided-chat-empty-selection">最多选择 {maxSelect} 项</span>
              )}
            </div>
          </>
        ) : null}
        <div className={question.searchable ? "guided-chat-option-list" : "guided-chat-options"}>
          {filteredOptions.map((option) => {
          const selected = selectedValues.includes(option.value);
          const optionDisabled = disabled || (!selected && selectedValues.length >= maxSelect);
          return (
            <button
              type="button"
              key={option.value}
              className={selected ? "guided-chat-option is-selected" : "guided-chat-option"}
              disabled={optionDisabled}
              onClick={() => {
                if (optionDisabled) {
                  return;
                }
                toggleOption(option.value);
              }}
            >
              {option.label}
            </button>
          );
          })}
          {filteredOptions.length === 0 ? <div className="guided-chat-empty-selection">没有匹配的选项</div> : null}
        </div>
        {selectedValues.includes("OTHER") ? (
          <input
            className="guided-chat-input"
            value={targetAnswer.custom_text || ""}
            disabled={disabled}
            onChange={(event) => setAnswer((previous) => ({ ...previous, custom_text: event.target.value }))}
            placeholder="补充其他选项"
          />
        ) : null}
      </div>
    );
  }

  function renderField(field, options = {}) {
    const targetAnswer = options.answerValue || answer;
    const disabled = Boolean(options.disabled);
    if (field.kind === "textarea") {
      return (
        <textarea
          className="guided-chat-input guided-chat-textarea"
          value={targetAnswer[field.name] || ""}
          disabled={disabled}
          onChange={(event) => setAnswer((previous) => ({ ...previous, [field.name]: event.target.value }))}
          placeholder={`请输入${field.label}`}
        />
      );
    }
    if (field.kind === "select") {
      return (
        <select
          className="guided-chat-input"
          value={targetAnswer[field.name] || ""}
          disabled={disabled}
          onChange={(event) => setAnswer((previous) => ({ ...previous, [field.name]: event.target.value }))}
        >
          <option value="">请选择</option>
          {(field.options || []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    }
    return (
      <input
        className="guided-chat-input"
        type={resolveInputType(field.input_type)}
        inputMode={resolveInputMode(field.input_type)}
        pattern={resolveInputPattern(field.input_type)}
        value={targetAnswer[field.name] || ""}
        disabled={disabled}
        onChange={(event) =>
          setAnswer((previous) => ({
            ...previous,
            [field.name]: sanitizeNumericInput(event.target.value, field.input_type),
          }))
        }
        placeholder={`请输入${field.label}`}
      />
    );
  }

  function renderForm(question, options = {}) {
    return (
      <div className="guided-chat-fields">
        {(question.fields || []).map((field) => (
          <label key={field.name}>
            <span>{field.label}</span>
            {renderField(field, options)}
          </label>
        ))}
      </div>
    );
  }

  function renderRepeatableRowField(question, field, rowIndex, row, disabled) {
    const value = row?.[field.name] || "";
    const updateRow = (nextValue) => {
      if (disabled) {
        return;
      }
      setAnswer((previous) => {
        const rows = Array.isArray(previous.rows) ? [...previous.rows] : [buildEmptyRepeatableRow(question)];
        rows[rowIndex] = {
          ...buildEmptyRepeatableRow(question),
          ...(rows[rowIndex] || {}),
          [field.name]: nextValue,
        };
        return { ...previous, rows };
      });
    };

    if (field.kind === "textarea") {
      return (
        <textarea
          className="guided-chat-input guided-chat-textarea"
          value={value}
          disabled={disabled}
          onChange={(event) => updateRow(event.target.value)}
          placeholder={`请输入${field.label}`}
        />
      );
    }
    if (field.kind === "select") {
      return (
        <select className="guided-chat-input" value={value} disabled={disabled} onChange={(event) => updateRow(event.target.value)}>
          <option value="">请选择</option>
          {(field.options || []).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    }
    return (
      <input
        className="guided-chat-input"
        type={resolveInputType(field.input_type)}
        inputMode={resolveInputMode(field.input_type)}
        pattern={resolveInputPattern(field.input_type)}
        value={value}
        disabled={disabled}
        onChange={(event) => updateRow(sanitizeNumericInput(event.target.value, field.input_type))}
        placeholder={`请输入${field.label}`}
      />
    );
  }

  function renderRepeatableForm(question, options = {}) {
    const targetAnswer = options.answerValue || answer;
    const disabled = Boolean(options.disabled);
    const rows =
      Array.isArray(targetAnswer.rows) && targetAnswer.rows.length > 0
        ? targetAnswer.rows
        : [buildEmptyRepeatableRow(question)];
    const minRows = Number(question.min_rows || 1);

    function addRow() {
      if (disabled) {
        return;
      }
      setAnswer((previous) => ({
        ...previous,
        rows: [...(Array.isArray(previous.rows) ? previous.rows : []), buildEmptyRepeatableRow(question)],
      }));
    }

    function removeRow(rowIndex) {
      if (disabled) {
        return;
      }
      setAnswer((previous) => {
        const nextRows = (Array.isArray(previous.rows) ? previous.rows : []).filter((_, index) => index !== rowIndex);
        return { ...previous, rows: nextRows.length > 0 ? nextRows : [buildEmptyRepeatableRow(question)] };
      });
    }

    return (
      <div className="guided-chat-repeatable">
        {question.fields?.length ? renderForm(question, { answerValue: targetAnswer, disabled }) : null}
        <div className="guided-chat-repeatable-list">
          {rows.map((row, rowIndex) => (
            <div className="guided-chat-repeatable-row" key={`row-${rowIndex}`}>
              <div className="guided-chat-repeatable-header">
                <span>科目 {rowIndex + 1}</span>
                {!disabled && rows.length > minRows ? (
                  <button type="button" className="guided-chat-row-remove" onClick={() => removeRow(rowIndex)}>
                    删除
                  </button>
                ) : null}
              </div>
              <div className="guided-chat-fields">
                {(question.row_fields || []).map((field) => {
                  const resolvedField = resolveRepeatableRowField(question, rows, row, rowIndex, field);
                  return (
                    <label key={field.name}>
                      <span>{resolvedField.label}</span>
                      {renderRepeatableRowField(question, resolvedField, rowIndex, row, disabled)}
                    </label>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
        {!disabled ? (
          <button type="button" className="guided-chat-add-row" onClick={addRow}>
            添加科目
          </button>
        ) : null}
      </div>
    );
  }

  function renderQuestionControls(question, options = {}) {
    if (!question) {
      return null;
    }
    const disabled = Boolean(options.disabled);
    const targetAnswer = options.answerValue || answer;
    const canSkip = question.skippable !== false;
    return (
      <div className={`guided-chat-question-panel ${disabled ? "is-readonly" : ""}`}>
        {question.type === "single" ? renderSingle(question, { answerValue: targetAnswer, disabled }) : null}
        {question.type === "multi" ? renderMulti(question, { answerValue: targetAnswer, disabled }) : null}
        {question.type === "repeatable_form" ? renderRepeatableForm(question, { answerValue: targetAnswer, disabled }) : null}
        {!["single", "multi", "repeatable_form"].includes(question.type) ? renderForm(question, { answerValue: targetAnswer, disabled }) : null}
        {disabled ? (
          <div className="guided-chat-action-row">
            <button type="button" className="guided-chat-skip" onClick={() => handleEditQuestion(question.code)}>
              修改此题
            </button>
          </div>
        ) : (
          <>
            {actionHint ? <div className="guided-chat-action-hint">{actionHint}</div> : null}
            <div className="guided-chat-action-row">
              {canSkip ? (
                <button type="button" className="guided-chat-skip" onClick={skipQuestion} disabled={saving}>
                  下一步，跳过此题
                </button>
              ) : null}
              <button type="button" className="guided-chat-submit" onClick={() => submitAnswer()} disabled={saving}>
                保存答案
                <Send size={15} />
              </button>
            </div>
          </>
        )}
      </div>
    );
  }

  const hasQuestionMessage = displayedMessages.some((message) => message.question_code === activeQuestion?.code);
  const isRevealingMessages = !editingQuestionCode && visibleMessages.length < messages.length;
  const canBackQuestion = (bundle?.questions || []).findIndex((question) => question.code === (activeQuestion?.code || currentQuestion?.code)) > 0;
  const activeQuestionTotal = activeQuestion?.total || bundle?.questions?.length || 0;

  return (
    <>
    <div className="home-ai-shell">
      <div className="home-ai-header">
        <div className="home-ai-header-brand">
          <div className="home-ai-brand-icon">
            <Sparkles size={18} strokeWidth={2.2} />
          </div>
          <div>
            <h3 className="home-ai-title">学生快速建档助手</h3>
            <p className="home-ai-subtitle">按固定问题流程采集信息，完成后生成六维图</p>
          </div>
        </div>
        <button type="button" className="home-ai-close" onClick={onClose} aria-label="关闭对话框">
          <X size={18} strokeWidth={2.4} />
        </button>
      </div>

      <div className="home-ai-body" ref={bodyRef}>
        {loading ? (
          <div className="home-ai-empty-state">
            <div className="home-ai-empty-icon">
              <Sparkles size={20} />
            </div>
            <div>
              <p className="home-ai-empty-title">正在准备快速建档</p>
              <p className="home-ai-empty-copy">系统正在读取你的问卷进度。</p>
            </div>
          </div>
        ) : null}

        {!loading && visibleMessages.length === 0 && currentQuestion ? (
          <motion.div
            className="home-ai-message-row home-ai-message-row-assistant"
            initial={{ opacity: 0, y: 10, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.24, ease: "easeOut" }}
          >
            <div className="home-ai-avatar home-ai-avatar-assistant">
              <Sparkles size={16} strokeWidth={2.1} />
            </div>
            <div className="home-ai-bubble home-ai-bubble-assistant">
              <p>{currentQuestion.title}</p>
              {renderQuestionControls(currentQuestion)}
            </div>
          </motion.div>
        ) : null}

        {!loading
          ? displayedMessages.map((message) => {
              const role = message.message_role;
              const isAssistant = role === "assistant";
              const questionForMessage =
                message.question_code && message.message_kind === "question"
                  ? questionByCode[message.question_code] || message.payload_json
                  : null;
              const showControls =
                isAssistant &&
                activeQuestion &&
                message.question_code === activeQuestion.code &&
                message.message_kind === "question";
              const readonlyAnswer = questionForMessage ? answerByCode[questionForMessage.code] : null;
              const showReadonlyControls =
                isAssistant &&
                questionForMessage &&
                !showControls &&
                readonlyAnswer &&
                Object.keys(readonlyAnswer).length > 0;
              return (
                <motion.div
                  key={`${message.sequence_no}-${message.question_code || "notice"}`}
                  className={`home-ai-message-row ${role === "user" ? "home-ai-message-row-user" : "home-ai-message-row-assistant"}`}
                  initial={{ opacity: 0, y: 10, scale: 0.98 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  transition={{ duration: 0.24, ease: "easeOut" }}
                >
                  {isAssistant ? (
                    <div className="home-ai-avatar home-ai-avatar-assistant">
                      <Sparkles size={16} strokeWidth={2.1} />
                    </div>
                  ) : null}
                  <div className={`home-ai-bubble ${role === "user" ? "home-ai-bubble-user" : "home-ai-bubble-assistant"}`}>
                    <p>{message.content}</p>
                    {showControls ? renderQuestionControls(activeQuestion) : null}
                    {showReadonlyControls
                      ? renderQuestionControls(questionForMessage, {
                          disabled: true,
                          answerValue: readonlyAnswer,
                        })
                      : null}
                  </div>
                  {role === "user" ? (
                    <div className="home-ai-avatar home-ai-avatar-user">
                      <UserRound size={16} strokeWidth={2.1} />
                    </div>
                  ) : null}
                </motion.div>
              );
            })
          : null}

        {!loading && activeQuestion && !hasQuestionMessage && !isRevealingMessages ? (
          <motion.div
            className="home-ai-message-row home-ai-message-row-assistant"
            initial={{ opacity: 0, y: 10, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.24, ease: "easeOut" }}
          >
            <div className="home-ai-avatar home-ai-avatar-assistant">
              <Sparkles size={16} strokeWidth={2.1} />
            </div>
            <div className="home-ai-bubble home-ai-bubble-assistant">
              <p>{activeQuestion.title}</p>
              {renderQuestionControls(activeQuestion)}
            </div>
          </motion.div>
        ) : null}
      </div>

      <div className="home-ai-footer">
        <div className="home-ai-status-row">
          <span className="home-ai-stage-badge">
            当前阶段：{session?.session_status === "completed" ? "已完成" : "问卷采集中"}
          </span>
          {activeQuestion ? (
            <span className="home-ai-status-copy">
              {activeQuestion.module_title} · 第 {activeQuestion.index || 1}/{activeQuestionTotal || 1} 题
            </span>
          ) : null}
        </div>
        <div className="home-ai-feedback-slot">
          {saving ? <p className="home-ai-hint">正在保存并更新问卷进度...</p> : null}
          {errorMessage ? <p className="home-ai-error">{errorMessage}</p> : null}
        </div>
        <div className="home-ai-action-row guided-chat-footer-actions">
          <button type="button" className="guided-chat-skip" onClick={handleBackToPreviousQuestion} disabled={saving || loading || !canBackQuestion}>
            返回上一题修改
          </button>
          <button type="button" className="home-ai-build-button" onClick={finishNow} disabled={saving || loading}>
            保存并生成六维图
            <ArrowRight size={18} strokeWidth={2.2} />
          </button>
        </div>
      </div>
    </div>
    {finalizing ? (
      <LoadingOverlay message="正在生成建档结果" submessage="系统正在保存最后一题，并同步生成六维图。" />
    ) : null}
    </>
  );
}
