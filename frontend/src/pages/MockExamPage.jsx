import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { ArrowLeft, BookOpenCheck, LoaderCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamOptions,
  getMockExamQuestionBanks,
} from "../api/mockexam";
import "../mockexam/mockexam.css";

function toMessage(type, text) {
  return { type, text };
}

export default function MockExamPage() {
  const navigate = useNavigate();
  const [options, setOptions] = useState({
    exam_category_options: [],
    content_options_map: {},
    supported_categories: [],
  });
  const [form, setForm] = useState({
    exam_category: "IELTS",
    exam_content: "Listening",
    question_bank_id: "",
  });
  const [questionBanks, setQuestionBanks] = useState([]);
  const [loadingBootstrap, setLoadingBootstrap] = useState(true);
  const [loadingQuestionBanks, setLoadingQuestionBanks] = useState(false);
  const [loadingExam, setLoadingExam] = useState(false);
  const [message, setMessage] = useState(null);

  const contentOptions = useMemo(() => {
    return options.content_options_map?.[form.exam_category] || [];
  }, [options.content_options_map, form.exam_category]);

  const isSupportedExam = useMemo(() => {
    return (options.supported_categories || []).includes(form.exam_category);
  }, [options.supported_categories, form.exam_category]);

  useEffect(() => {
    let active = true;

    async function bootstrapPage() {
      try {
        const optionsResponse = await getMockExamOptions();
        if (!active) {
          return;
        }

        const nextOptions = optionsResponse.data || {
          exam_category_options: [],
          content_options_map: {},
          supported_categories: [],
        };
        const initialCategory = nextOptions.exam_category_options?.[0] || "IELTS";
        const initialContent = nextOptions.content_options_map?.[initialCategory]?.[0] || "";

        setOptions(nextOptions);
        setForm({
          exam_category: initialCategory,
          exam_content: initialContent,
          question_bank_id: "",
        });
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(toMessage("error", error?.response?.data?.detail || "模拟考试页面初始化失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoadingBootstrap(false);
        }
      }
    }

    void bootstrapPage();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (loadingBootstrap || !form.exam_category) {
      return;
    }

    let active = true;

    async function fetchQuestionBanks() {
      setLoadingQuestionBanks(true);
      try {
        const response = await getMockExamQuestionBanks({
          exam_category: form.exam_category,
          exam_content: form.exam_content || undefined,
        });
        if (!active) {
          return;
        }

        const items = response.data?.items || [];
        const nextQuestionBankId = items[0] ? String(items[0].id) : "";
        setQuestionBanks(items);
        setForm((previous) => ({
          ...previous,
          question_bank_id: items.some((item) => String(item.id) === String(previous.question_bank_id))
            ? previous.question_bank_id
            : nextQuestionBankId,
        }));

        if (!items.length) {
          setMessage(toMessage("warn", "当前筛选条件下没有可用题库，请先上传并启用题库。"));
          return;
        }

        if ((options.supported_categories || []).includes(form.exam_category)) {
          setMessage(null);
        } else {
          setMessage(toMessage("warn", `${form.exam_category} 模考正在等待更新，当前仅开放 IELTS。`));
        }
      } catch (error) {
        if (!active) {
          return;
        }
        setQuestionBanks([]);
        setForm((previous) => ({
          ...previous,
          question_bank_id: "",
        }));
        setMessage(toMessage("error", error?.response?.data?.detail || "题库列表加载失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoadingQuestionBanks(false);
        }
      }
    }

    void fetchQuestionBanks();

    return () => {
      active = false;
    };
  }, [form.exam_category, form.exam_content, loadingBootstrap, options.supported_categories]);

  function handleExamCategoryChange(nextCategory) {
    const nextContent = options.content_options_map?.[nextCategory]?.[0] || "";
    setForm({
      exam_category: nextCategory,
      exam_content: nextContent,
      question_bank_id: "",
    });
  }

  function handleStartExam() {
    if (!form.question_bank_id) {
      setMessage(toMessage("warn", "请先选择题库文件。"));
      return;
    }

    if (!isSupportedExam) {
      setMessage(toMessage("warn", `${form.exam_category} 模考正在等待更新，当前仅开放 IELTS。`));
      return;
    }

    setLoadingExam(true);
    const targetUrl = `/mockexam/run/${form.question_bank_id}`;
    const openedWindow = window.open(targetUrl, "_blank", "noopener,noreferrer");

    if (!openedWindow) {
      window.location.assign(targetUrl);
      return;
    }

    setLoadingExam(false);
  }

  return (
    <div className="mockexam-page">
      <div className="mockexam-shell">
        <div className="mockexam-topbar">
          <div className="mockexam-topcopy">
            <button type="button" className="mockexam-back" onClick={() => navigate("/")}>
              <ArrowLeft size={16} strokeWidth={2.2} />
              返回首页
            </button>
            <h1>模拟考试</h1>
          </div>
        </div>

        <motion.section
          className="mockexam-panel"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
        >
          <div className="mockexam-panel-head">
            <div>
              <h2>选择题库</h2>
            </div>
            <div className="mockexam-panel-badge">
              <BookOpenCheck size={22} strokeWidth={2.2} />
            </div>
          </div>

          <div className="mockexam-form-grid">
            <div className="mockexam-field">
              <label htmlFor="mockexam-category">考试类别</label>
              <select
                id="mockexam-category"
                className="mockexam-select"
                value={form.exam_category}
                disabled={loadingBootstrap}
                onChange={(event) => handleExamCategoryChange(event.target.value)}
              >
                {(options.exam_category_options || []).map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </div>

            <div className="mockexam-field">
              <label htmlFor="mockexam-content">考试内容</label>
              <select
                id="mockexam-content"
                className="mockexam-select"
                value={form.exam_content}
                disabled={loadingBootstrap}
                onChange={(event) =>
                  setForm((previous) => ({
                    ...previous,
                    exam_content: event.target.value,
                    question_bank_id: "",
                  }))
                }
              >
                {contentOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </div>

            <div className="mockexam-field">
              <label htmlFor="mockexam-question-bank">文件名</label>
              <select
                id="mockexam-question-bank"
                className="mockexam-select"
                value={form.question_bank_id}
                disabled={loadingQuestionBanks || questionBanks.length === 0}
                onChange={(event) =>
                  setForm((previous) => ({
                    ...previous,
                    question_bank_id: event.target.value,
                  }))
                }
              >
                {questionBanks.length === 0 ? <option value="">暂无题库</option> : null}
                {questionBanks.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.file_name}
                  </option>
                ))}
              </select>
            </div>

            <div className="mockexam-actions">
              <button
                type="button"
                className="mockexam-button"
                onClick={handleStartExam}
                disabled={loadingBootstrap || loadingQuestionBanks || loadingExam || !form.question_bank_id}
              >
                {loadingExam ? (
                  <span className="mockexam-loading">
                    <LoaderCircle size={16} strokeWidth={2.3} className="spin" />
                    正在打开
                  </span>
                ) : (
                  "开始模考"
                )}
              </button>
            </div>
          </div>

          {message ? <div className={`mockexam-message ${message.type}`}>{message.text}</div> : null}
        </motion.section>
      </div>
    </div>
  );
}
