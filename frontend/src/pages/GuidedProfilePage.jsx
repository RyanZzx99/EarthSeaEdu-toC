import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, CheckCircle2, Home, RotateCcw, Send, Sparkles } from "lucide-react";
import { LoadingOverlay, LoadingPage } from "../components/LoadingPage";
import {
  exitGuidedProfileSession,
  getCurrentGuidedProfileSession,
  restartGuidedProfileSession,
  submitGuidedProfileAnswer,
} from "../api/studentProfileGuided";
import "../profileGuided.css";

function getErrorMessage(error, fallback) {
  const detail = error?.response?.data?.detail;
  if (typeof detail === "string") {
    return detail;
  }
  return fallback;
}

function getQuestionAnswer(bundle, questionCode) {
  const row = (bundle?.answers || []).find((item) => item.question_code === questionCode);
  return row?.answer_json || {};
}

function isAnswered(bundle, questionCode) {
  return (bundle?.answers || []).some((item) => item.question_code === questionCode);
}

function createEmptyRow(question) {
  return Object.fromEntries((question.row_fields || []).map((field) => [field.name, ""]));
}

function isExperienceQuestion(question) {
  return ["Q15", "Q16", "Q17"].includes(question?.code);
}

function hasValue(value) {
  if (value == null) {
    return false;
  }
  if (typeof value === "string") {
    return value.trim() !== "";
  }
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (typeof value === "object") {
    return Object.keys(value).length > 0;
  }
  return true;
}

function shouldValidateQuestionField(question, currentAnswer, fieldName) {
  if (!isExperienceQuestion(question)) {
    return true;
  }
  if (fieldName === "has_experience") {
    return true;
  }
  return currentAnswer?.has_experience === "yes";
}

function validateQuestionAnswer(question, currentAnswer) {
  const errors = {};
  if (!question) {
    return errors;
  }
  if (question.type === "single") {
    if (!hasValue(currentAnswer?.selected_value) && !hasValue(currentAnswer?.custom_text)) {
      errors.selected_value = "请先选择一个选项";
    }
    return errors;
  }
  if (question.type === "multi") {
    const selectedValues = Array.isArray(currentAnswer?.selected_values) ? currentAnswer.selected_values : [];
    if (selectedValues.length === 0 && !hasValue(currentAnswer?.custom_text)) {
      errors.selected_values = "请至少选择一项";
    }
    return errors;
  }

  (question.fields || []).forEach((field) => {
    if (!field.required || !shouldValidateQuestionField(question, currentAnswer, field.name)) {
      return;
    }
    if (!hasValue(currentAnswer?.[field.name])) {
      errors[field.name] = `请填写${field.label}`;
    }
  });

  if (question.type === "repeatable_form") {
    const rows = Array.isArray(currentAnswer?.rows) ? currentAnswer.rows : [];
    rows.forEach((row, rowIndex) => {
      const rowHasAnyValue = (question.row_fields || []).some((field) => hasValue(row?.[field.name]));
      if (!rowHasAnyValue) {
        return;
      }
      (question.row_fields || []).forEach((field) => {
        if (!field.required || hasValue(row?.[field.name])) {
          return;
        }
        errors[`rows.${rowIndex}.${field.name}`] = `第 ${rowIndex + 1} 行请填写${field.label}`;
      });
    });
  }

  return errors;
}

function buildInitialAnswer(question, bundle) {
  if (!question) {
    return {};
  }
  const existing = getQuestionAnswer(bundle, question.code);
  if (Object.keys(existing).length > 0) {
    if (question.type === "multi") {
      const validOptionValues = new Set((question.options || []).map((option) => option.value));
      const selectedValues = Array.isArray(existing.selected_values)
        ? existing.selected_values.filter((value) => validOptionValues.size === 0 || validOptionValues.has(value))
        : [];
      return {
        ...existing,
        selected_values: selectedValues,
      };
    }
    if (isExperienceQuestion(question) && !existing.has_experience) {
      return { ...existing, has_experience: "no" };
    }
    if (question.type === "repeatable_form") {
      return {
        ...Object.fromEntries((question.fields || []).map((field) => [field.name, existing[field.name] || ""])),
        ...existing,
        rows: Array.isArray(existing.rows) && existing.rows.length > 0
          ? existing.rows
          : Array.from({ length: Math.max(1, question.min_rows || 1) }, () => createEmptyRow(question)),
      };
    }
    return existing;
  }
  if (question.type === "single") {
    return { selected_value: "", custom_text: "" };
  }
  if (question.type === "multi") {
    return { selected_values: [], custom_text: "" };
  }
  if (question.type === "repeatable_form") {
    return {
      ...Object.fromEntries((question.fields || []).map((field) => [field.name, ""])),
      rows: Array.from({ length: Math.max(1, question.min_rows || 1) }, () => createEmptyRow(question)),
    };
  }
  const initialAnswer = Object.fromEntries((question.fields || []).map((field) => [field.name, ""]));
  if (isExperienceQuestion(question)) {
    initialAnswer.has_experience = "no";
  }
  return initialAnswer;
}

function formatDateTime(value) {
  if (!value) {
    return "--";
  }
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

function RadarPreview({ result }) {
  const scores = result?.radar_scores_json || {};
  const labels = {
    academic: "学术成绩",
    language: "语言能力",
    standardized: "标化考试",
    competition: "竞赛背景",
    activity: "活动 / 企业实习",
    project: "科研经历",
  };
  return (
    <section className="guided-result-card">
      <div>
        <p className="guided-eyebrow">建档结果</p>
        <h2>当前六维图已生成</h2>
        <p>{result?.summary_text || "系统已根据固定问卷答案生成当前建档结果。"}</p>
      </div>
      <div className="guided-score-grid">
        {Object.entries(labels).map(([key, label]) => (
          <div className="guided-score-item" key={key}>
            <span>{label}</span>
            <strong>{scores?.[key] ?? "--"}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function GuidedProfilePage() {
  const [bundle, setBundle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [activeQuestionCode, setActiveQuestionCode] = useState(null);
  const [answer, setAnswer] = useState({});
  const [multiSearchTextByCode, setMultiSearchTextByCode] = useState({});

  const session = bundle?.session || null;
  const currentQuestion = useMemo(() => {
    if (!bundle) {
      return null;
    }
    if (activeQuestionCode) {
      return (bundle.questions || []).find((question) => question.code === activeQuestionCode) || bundle.current_question;
    }
    return bundle.current_question;
  }, [activeQuestionCode, bundle]);

  const answeredCount = useMemo(() => {
    const visibleCodes = new Set((bundle?.questions || []).map((question) => question.code));
    return (bundle?.answers || []).filter((item) => visibleCodes.has(item.question_code)).length;
  }, [bundle]);

  useEffect(() => {
    let active = true;
    async function loadBundle() {
      setLoading(true);
      setErrorMessage("");
      try {
        const response = await getCurrentGuidedProfileSession(true);
        if (active) {
          setBundle(response.data);
          setActiveQuestionCode(response.data?.current_question?.code || null);
        }
      } catch (error) {
        if (active) {
          setErrorMessage(getErrorMessage(error, "加载固定问卷失败"));
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }
    void loadBundle();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setAnswer(buildInitialAnswer(currentQuestion, bundle));
    setFieldErrors({});
  }, [currentQuestion, bundle]);

  function clearFieldError(errorKey) {
    setFieldErrors((previous) => {
      if (!previous[errorKey]) {
        return previous;
      }
      const next = { ...previous };
      delete next[errorKey];
      return next;
    });
  }

  function handleFieldChange(fieldName, nextValue) {
    setAnswer((previous) => ({ ...previous, [fieldName]: nextValue }));
    clearFieldError(fieldName);
  }

  function handleRowFieldChange(rowIndex, fieldName, nextValue) {
    setAnswer((previous) => {
      const currentRows = Array.isArray(previous.rows) ? [...previous.rows] : [];
      currentRows[rowIndex] = { ...(currentRows[rowIndex] || {}), [fieldName]: nextValue };
      return { ...previous, rows: currentRows };
    });
    clearFieldError(`rows.${rowIndex}.${fieldName}`);
  }

  async function handleSubmit() {
    if (!session?.session_id || !currentQuestion) {
      return;
    }
    const validationErrors = validateQuestionAnswer(currentQuestion, answer);
    if (Object.keys(validationErrors).length > 0) {
      setFieldErrors(validationErrors);
      setErrorMessage(Object.values(validationErrors)[0] || "请先完善必填项");
      return;
    }
    setSaving(true);
    setErrorMessage("");
    setFieldErrors({});
    try {
      const response = await submitGuidedProfileAnswer(session.session_id, currentQuestion.code, answer);
      setBundle(response.data);
      setActiveQuestionCode(response.data?.current_question?.code || currentQuestion.code);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "保存答案失败"));
    } finally {
      setSaving(false);
    }
  }

  async function handleExit() {
    if (!session?.session_id) {
      return;
    }
    if (currentQuestion) {
      const validationErrors = validateQuestionAnswer(currentQuestion, answer);
      if (Object.keys(validationErrors).length > 0) {
        setFieldErrors(validationErrors);
        setErrorMessage(Object.values(validationErrors)[0] || "请先完善必填项");
        return;
      }
    }
    setSaving(true);
    setErrorMessage("");
    setFieldErrors({});
    try {
      const response = await exitGuidedProfileSession(session.session_id, "manual_exit");
      setBundle(response.data);
      setActiveQuestionCode(response.data?.current_question?.code || null);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "保存并生成结果失败"));
    } finally {
      setSaving(false);
    }
  }

  async function handleRestart() {
    setSaving(true);
    setErrorMessage("");
    try {
      const response = await restartGuidedProfileSession();
      setBundle(response.data);
      setActiveQuestionCode(response.data?.current_question?.code || null);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, "重新开始失败"));
    } finally {
      setSaving(false);
    }
  }

  function renderSingleQuestion(question) {
    return (
      <div className="guided-options">
        {(question.options || []).map((option) => (
          <button
            type="button"
            key={option.value}
            className={answer.selected_value === option.value ? "guided-option is-selected" : "guided-option"}
            onClick={() => {
              setAnswer((previous) => ({ ...previous, selected_value: option.value }));
              clearFieldError("selected_value");
            }}
          >
            <span>{option.label}</span>
          </button>
        ))}
        {(question.allow_custom_text || answer.selected_value === "OTHER" || answer.selected_value === "CUSTOM") ? (
          <input
            className="guided-input"
            value={answer.custom_text || ""}
            onChange={(event) => handleFieldChange("custom_text", event.target.value)}
            placeholder="补充说明"
          />
        ) : null}
        {fieldErrors.selected_value ? <div className="guided-field-error guided-options-error">{fieldErrors.selected_value}</div> : null}
      </div>
    );
  }

  function renderMultiQuestion(question) {
    const optionList = question.options || [];
    const selectedValues = Array.isArray(answer.selected_values) ? answer.selected_values : [];
    const maxSelect = question.max_select || 99;
    const exclusiveValues = new Set(question.exclusive_option_values || []);
    const optionByValue = new Map(optionList.map((option) => [option.value, option]));
    const selectedOptions = selectedValues.map((value) => optionByValue.get(value) || { value, label: value });
    const searchText = multiSearchTextByCode[question.code] || "";
    const normalizedSearchText = searchText.trim().toLowerCase();
    const filteredOptions = question.searchable
      ? optionList.filter((option) => {
          if (!normalizedSearchText) {
            return true;
          }
          const searchableText = `${option.label || ""} ${option.value || ""}`.toLowerCase();
          return searchableText.includes(normalizedSearchText);
        })
      : optionList;

    function toggleOption(optionValue) {
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
      clearFieldError("selected_values");
    }

    return (
      <div className={question.searchable ? "guided-chat-multi-control" : "guided-options"}>
        {question.searchable ? (
          <>
            <input
              className="guided-input"
              value={searchText}
              onChange={(event) =>
                setMultiSearchTextByCode((previous) => ({
                  ...previous,
                  [question.code]: event.target.value,
                }))
              }
              placeholder={`输入关键词搜索，可最多选择 ${maxSelect} 项`}
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
                  </button>
                ))
              ) : (
                <span className="guided-chat-empty-selection">最多选择 {maxSelect} 项</span>
              )}
            </div>
          </>
        ) : null}
        <div className={question.searchable ? "guided-chat-option-list" : "guided-options"}>
          {filteredOptions.map((option) => {
            const selected = selectedValues.includes(option.value);
            const disabled = !selected && selectedValues.length >= maxSelect && !exclusiveValues.has(option.value);
            return (
              <button
                type="button"
                key={option.value}
                className={selected ? "guided-option is-selected" : "guided-option"}
                disabled={disabled}
                onClick={() => {
                  if (disabled) {
                    return;
                  }
                  toggleOption(option.value);
                }}
              >
                <span>{option.label}</span>
              </button>
            );
          })}
          {filteredOptions.length === 0 ? <div className="guided-chat-empty-selection">没有匹配的选项</div> : null}
        </div>
        {selectedValues.includes("OTHER") ? (
          <input
            className="guided-input"
            value={answer.custom_text || ""}
            onChange={(event) => handleFieldChange("custom_text", event.target.value)}
            placeholder="补充其他选项"
          />
        ) : null}
        {fieldErrors.selected_values ? <div className="guided-field-error guided-options-error">{fieldErrors.selected_values}</div> : null}
      </div>
    );
  }

  function renderField(field, value, onChange, errorText) {
    const inputType = field.input_type || field.kind;
    if (field.kind === "textarea") {
      return (
        <textarea
          className={errorText ? "guided-textarea is-invalid" : "guided-textarea"}
          value={value || ""}
          onChange={(event) => onChange(event.target.value)}
          placeholder={`请输入${field.label}`}
          rows={4}
        />
      );
    }
    if (field.kind === "select") {
      return (
        <select
          className={errorText ? "guided-input is-invalid" : "guided-input"}
          value={value || ""}
          onChange={(event) => onChange(event.target.value)}
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
        type={inputType === "date" ? "date" : inputType === "number" ? "number" : "text"}
        className={errorText ? "guided-input is-invalid" : "guided-input"}
        value={value || ""}
        onChange={(event) => onChange(event.target.value)}
        placeholder={`请输入${field.label}`}
      />
    );
  }

  function renderFormQuestion(question) {
    return (
      <div className="guided-field-grid">
        {(question.fields || []).map((field) => (
          <label className="guided-field" key={field.name}>
            <span>
              {field.label}
              {field.required ? <i className="guided-required">*</i> : null}
            </span>
            {renderField(field, answer[field.name], (nextValue) => handleFieldChange(field.name, nextValue), fieldErrors[field.name])}
            {fieldErrors[field.name] ? <em className="guided-field-error">{fieldErrors[field.name]}</em> : null}
          </label>
        ))}
      </div>
    );
  }

  function renderRepeatableQuestion(question) {
    const rows = Array.isArray(answer.rows) ? answer.rows : [];
    const minRows = Math.max(1, question.min_rows || 1);
    return (
      <div className="guided-repeatable">
        {(question.fields || []).length > 0 ? (
          <div className="guided-field-grid">
            {(question.fields || []).map((field) => (
              <label className="guided-field" key={field.name}>
                <span>
                  {field.label}
                  {field.required ? <i className="guided-required">*</i> : null}
                </span>
                {renderField(field, answer[field.name], (nextValue) => handleFieldChange(field.name, nextValue), fieldErrors[field.name])}
                {fieldErrors[field.name] ? <em className="guided-field-error">{fieldErrors[field.name]}</em> : null}
              </label>
            ))}
          </div>
        ) : null}

        <div className="guided-repeatable-list">
          {rows.map((row, rowIndex) => (
            <div className="guided-repeatable-row" key={`row-${rowIndex}`}>
              <div className="guided-repeatable-row-grid">
                {(question.row_fields || []).map((field) => (
                  <label className="guided-field" key={`${field.name}-${rowIndex}`}>
                    <span>
                      {field.label}
                      {field.required ? <i className="guided-required">*</i> : null}
                    </span>
                    {renderField(
                      field,
                      row[field.name],
                      (nextValue) => handleRowFieldChange(rowIndex, field.name, nextValue),
                      fieldErrors[`rows.${rowIndex}.${field.name}`]
                    )}
                    {fieldErrors[`rows.${rowIndex}.${field.name}`] ? (
                      <em className="guided-field-error">{fieldErrors[`rows.${rowIndex}.${field.name}`]}</em>
                    ) : null}
                  </label>
                ))}
              </div>
              {rows.length > minRows ? (
                <div className="guided-repeatable-row-actions">
                  <button
                    type="button"
                    className="guided-row-remove"
                    onClick={() =>
                      setAnswer((previous) => ({
                        ...previous,
                        rows: (Array.isArray(previous.rows) ? previous.rows : []).filter((_, index) => index !== rowIndex),
                      }))
                    }
                  >
                    删除这一行
                  </button>
                </div>
              ) : null}
            </div>
          ))}
        </div>

        <div className="guided-repeatable-actions">
          <button
            type="button"
            className="guided-secondary"
            onClick={() =>
              setAnswer((previous) => ({
                ...previous,
                rows: [...(Array.isArray(previous.rows) ? previous.rows : []), createEmptyRow(question)],
              }))
            }
          >
            新增一行
          </button>
        </div>
      </div>
    );
  }

  if (loading) {
    return <LoadingPage message="正在进入标准问卷建档" submessage="正在准备问题流程" />;
  }

  const finished = session?.session_status === "completed" || session?.session_status === "exited";

  return (
    <div className="guided-page">
      {saving ? <LoadingOverlay message="正在保存" submessage="系统正在写入问卷答案和建档结果" /> : null}
      <header className="guided-nav">
        <button type="button" onClick={() => window.history.back()}>
          <ArrowLeft size={18} />
          返回上一页
        </button>
        <div className="guided-brand">
          <Sparkles size={18} />
          <span>标准问卷建档</span>
        </div>
        <button type="button" onClick={() => (window.location.href = "/")}>
          <Home size={18} />
          返回首页
        </button>
      </header>

      <main className="guided-shell">
        <aside className="guided-sidebar">
          <p className="guided-eyebrow">Profile Builder</p>
          <h1>学生基础信息采集</h1>
          <p className="guided-sidebar-copy">
            按固定问题流程采集目标国家、专业、校内成绩、语言、标化和经历信息。
          </p>
          <div className="guided-progress">
            <div>
              <strong>
                {answeredCount}/{bundle?.questions?.length || 0}
              </strong>
              <span>已完成</span>
            </div>
            <div className="guided-progress-bar">
              <i style={{ width: `${Math.min(100, (answeredCount / Math.max(1, bundle?.questions?.length || 1)) * 100)}%` }} />
            </div>
          </div>
          <div className="guided-question-list">
            {(bundle?.questions || []).map((question) => (
              <button
                type="button"
                key={question.code}
                className={question.code === currentQuestion?.code ? "is-active" : ""}
                onClick={() => setActiveQuestionCode(question.code)}
              >
                <span>{question.code}</span>
                <em>{question.module_title}</em>
                {isAnswered(bundle, question.code) ? <CheckCircle2 size={16} /> : null}
              </button>
            ))}
          </div>
        </aside>

        <section className="guided-main">
          <div className="guided-status-card">
            <span>会话状态：{session?.session_status || "--"}</span>
            <span>最近更新：{formatDateTime(session?.update_time)}</span>
          </div>

          {errorMessage ? <div className="guided-error">{errorMessage}</div> : null}

          {finished && bundle?.result ? <RadarPreview result={bundle.result} /> : null}

          {currentQuestion ? (
            <article className="guided-question-card">
              <div className="guided-question-heading">
                <div>
                  <p>{currentQuestion.module_title}</p>
                  <h2>{currentQuestion.title}</h2>
                </div>
                <span>
                  第 {currentQuestion.index}/{bundle?.questions?.length || 0} 题
                </span>
              </div>

              {currentQuestion.type === "single" ? renderSingleQuestion(currentQuestion) : null}
              {currentQuestion.type === "multi" ? renderMultiQuestion(currentQuestion) : null}
              {currentQuestion.type === "repeatable_form" ? renderRepeatableQuestion(currentQuestion) : null}
              {!["single", "multi", "repeatable_form"].includes(currentQuestion.type) ? renderFormQuestion(currentQuestion) : null}

              <div className="guided-actions">
                <button type="button" className="guided-secondary" onClick={handleExit}>
                  保存并生成结果
                </button>
                <button type="button" className="guided-primary" onClick={handleSubmit}>
                  保存答案
                  <Send size={16} />
                </button>
              </div>
            </article>
          ) : (
            <article className="guided-question-card">
              <h2>当前问卷已完成</h2>
              <p>你可以查看建档结果，或重新开始填写一份新的固定问卷。</p>
              <div className="guided-actions">
                <button type="button" className="guided-secondary" onClick={handleRestart}>
                  <RotateCcw size={16} />
                  重新开始
                </button>
                <button type="button" className="guided-primary" onClick={() => (window.location.href = "/profile")}>
                  查看正式档案
                </button>
              </div>
            </article>
          )}
        </section>
      </main>
    </div>
  );
}
