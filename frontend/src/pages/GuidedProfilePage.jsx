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

function buildInitialAnswer(question, bundle) {
  if (!question) {
    return {};
  }
  const existing = getQuestionAnswer(bundle, question.code);
  if (Object.keys(existing).length > 0) {
    return existing;
  }
  if (question.type === "single") {
    return { selected_value: "", custom_text: "" };
  }
  if (question.type === "multi") {
    return { selected_values: [], custom_text: "" };
  }
  return Object.fromEntries((question.fields || []).map((field) => [field.name, ""]));
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
    activity: "活动经历",
    project: "项目经历",
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
  const [activeQuestionCode, setActiveQuestionCode] = useState(null);
  const [answer, setAnswer] = useState({});

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
  }, [currentQuestion, bundle]);

  async function handleSubmit() {
    if (!session?.session_id || !currentQuestion) {
      return;
    }
    setSaving(true);
    setErrorMessage("");
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
    setSaving(true);
    setErrorMessage("");
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
            onClick={() => setAnswer((previous) => ({ ...previous, selected_value: option.value }))}
          >
            <span>{option.label}</span>
          </button>
        ))}
        {(question.allow_custom_text || answer.selected_value === "OTHER" || answer.selected_value === "CUSTOM") ? (
          <input
            className="guided-input"
            value={answer.custom_text || ""}
            onChange={(event) => setAnswer((previous) => ({ ...previous, custom_text: event.target.value }))}
            placeholder="补充说明"
          />
        ) : null}
      </div>
    );
  }

  function renderMultiQuestion(question) {
    const selectedValues = Array.isArray(answer.selected_values) ? answer.selected_values : [];
    const maxSelect = question.max_select || 99;
    return (
      <div className="guided-options">
        {(question.options || []).map((option) => {
          const selected = selectedValues.includes(option.value);
          const disabled = !selected && selectedValues.length >= maxSelect;
          return (
            <button
              type="button"
              key={option.value}
              className={selected ? "guided-option is-selected" : "guided-option"}
              disabled={disabled}
              onClick={() => {
                setAnswer((previous) => {
                  const values = Array.isArray(previous.selected_values) ? previous.selected_values : [];
                  return {
                    ...previous,
                    selected_values: values.includes(option.value)
                      ? values.filter((value) => value !== option.value)
                      : [...values, option.value].slice(0, maxSelect),
                  };
                });
              }}
            >
              <span>{option.label}</span>
            </button>
          );
        })}
        {selectedValues.includes("OTHER") ? (
          <input
            className="guided-input"
            value={answer.custom_text || ""}
            onChange={(event) => setAnswer((previous) => ({ ...previous, custom_text: event.target.value }))}
            placeholder="补充其他选项"
          />
        ) : null}
      </div>
    );
  }

  function renderField(field) {
    if (field.kind === "textarea") {
      return (
        <textarea
          className="guided-textarea"
          value={answer[field.name] || ""}
          onChange={(event) => setAnswer((previous) => ({ ...previous, [field.name]: event.target.value }))}
          placeholder={`请输入${field.label}`}
          rows={4}
        />
      );
    }
    if (field.kind === "select") {
      return (
        <select
          className="guided-input"
          value={answer[field.name] || ""}
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
        className="guided-input"
        value={answer[field.name] || ""}
        onChange={(event) => setAnswer((previous) => ({ ...previous, [field.name]: event.target.value }))}
        placeholder={`请输入${field.label}`}
      />
    );
  }

  function renderFormQuestion(question) {
    return (
      <div className="guided-field-grid">
        {(question.fields || []).map((field) => (
          <label className="guided-field" key={field.name}>
            <span>{field.label}</span>
            {renderField(field)}
          </label>
        ))}
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
              {!["single", "multi"].includes(currentQuestion.type) ? renderFormQuestion(currentQuestion) : null}

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

