import React, { useEffect, useMemo, useState } from "react";
import { BookOpenCheck, LoaderCircle } from "lucide-react";
import { getMockExamOptions, getMockExamPapers, getMockExamPaperSets } from "../api/mockexam";

function openExamWindow(targetUrl) {
  const openedWindow = window.open("", "_blank");
  if (!openedWindow) {
    return false;
  }
  openedWindow.opener = null;
  openedWindow.location.href = targetUrl;
  return true;
}

function getApiError(error, fallback) {
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

function buildSourceId(item, sourceMode) {
  return sourceMode === "paper-set" ? item?.mockexam_paper_set_id : item?.exam_paper_id;
}

function buildSourceLabel(item, sourceMode) {
  return sourceMode === "paper-set" ? item?.set_name : item?.paper_name;
}

export default function MockExamWorkspace() {
  const [options, setOptions] = useState({
    exam_category_options: [],
    content_options_map: {},
  });
  const [form, setForm] = useState({
    source_mode: "paper",
    exam_category: "IELTS",
    exam_content: "Listening",
    source_id: "",
  });
  const [items, setItems] = useState([]);
  const [loadingBootstrap, setLoadingBootstrap] = useState(true);
  const [loadingItems, setLoadingItems] = useState(false);
  const [startingSourceId, setStartingSourceId] = useState("");
  const [message, setMessage] = useState("");

  const contentOptions = useMemo(() => {
    if (form.source_mode === "paper-set") {
      return ["Listening", "Reading", "Mixed"];
    }
    return options.content_options_map?.[form.exam_category] || [];
  }, [form.source_mode, form.exam_category, options.content_options_map]);

  useEffect(() => {
    let active = true;

    async function bootstrapPage() {
      try {
        const response = await getMockExamOptions();
        if (!active) {
          return;
        }

        const nextOptions = response.data || {
          exam_category_options: ["IELTS"],
          content_options_map: { IELTS: ["Listening", "Reading"] },
        };
        const initialCategory = nextOptions.exam_category_options?.[0] || "IELTS";
        const initialContent = nextOptions.content_options_map?.[initialCategory]?.[0] || "Listening";

        setOptions(nextOptions);
        setForm({
          source_mode: "paper",
          exam_category: initialCategory,
          exam_content: initialContent,
          source_id: "",
        });
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(getApiError(error, "初始化模考选项失败，请稍后重试。"));
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
      return undefined;
    }

    let active = true;

    async function loadItems() {
      try {
        setLoadingItems(true);
        const response =
          form.source_mode === "paper-set"
            ? await getMockExamPaperSets({
                exam_category: form.exam_category,
                exam_content: form.exam_content || undefined,
              })
            : await getMockExamPapers({
                exam_category: form.exam_category,
                exam_content: form.exam_content || undefined,
              });
        if (!active) {
          return;
        }

        const nextItems = response.data?.items || [];
        setItems(nextItems);
        setForm((previous) => {
          const currentIdStillExists = nextItems.some(
            (item) => String(buildSourceId(item, previous.source_mode)) === String(previous.source_id)
          );
          return {
            ...previous,
            source_id: currentIdStillExists ? previous.source_id : nextItems[0] ? String(buildSourceId(nextItems[0], previous.source_mode)) : "",
          };
        });
        setMessage(
          nextItems.length
            ? ""
            : form.source_mode === "paper-set"
              ? "当前筛选条件下还没有组合试卷。"
              : "当前筛选条件下还没有试卷。"
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setItems([]);
        setMessage(
          getApiError(
            error,
            form.source_mode === "paper-set" ? "组合试卷列表加载失败，请稍后重试。" : "试卷列表加载失败，请稍后重试。"
          )
        );
      } finally {
        if (active) {
          setLoadingItems(false);
        }
      }
    }

    void loadItems();

    return () => {
      active = false;
    };
  }, [form.exam_category, form.exam_content, form.source_mode, loadingBootstrap]);

  function switchSourceMode(nextMode) {
    setForm((previous) => ({
      ...previous,
      source_mode: nextMode,
      exam_content:
        nextMode === "paper-set"
          ? ""
          : options.content_options_map?.[previous.exam_category]?.[0] || "Listening",
      source_id: "",
    }));
  }

  function handleStartPaper() {
    const resolvedSourceId = String(form.source_id || "");
    if (!resolvedSourceId) {
      setMessage(form.source_mode === "paper-set" ? "请先选择一套组合试卷。" : "请先选择一套试卷。");
      return;
    }

    setStartingSourceId(resolvedSourceId);
    const opened = openExamWindow(`/mockexam/run/${form.source_mode}/${resolvedSourceId}`);
    if (!opened) {
      setMessage("浏览器拦截了新页面，请允许弹窗后重试。");
    } else {
      setMessage("");
    }
    setStartingSourceId("");
  }

  return (
    <div className="mockexam-beta-layout">
      <div className="mockexam-panel">
        <div className="mockexam-panel-head">
          <div>
            <h2>选择试卷</h2>
            <p>基于结构化题库直接组装试卷，当前支持单张试卷和教师组合试卷。</p>
          </div>
          <span className="mockexam-panel-badge">
            <BookOpenCheck size={22} strokeWidth={2.1} />
          </span>
        </div>

        <div className="mockexam-segmented">
          <button
            type="button"
            className={`mockexam-segmented-button ${form.source_mode === "paper" ? "active" : ""}`}
            onClick={() => switchSourceMode("paper")}
          >
            单张试卷
          </button>
          <button
            type="button"
            className={`mockexam-segmented-button ${form.source_mode === "paper-set" ? "active" : ""}`}
            onClick={() => switchSourceMode("paper-set")}
          >
            组合试卷
          </button>
        </div>

        <div className="mockexam-form-grid mockexam-beta-form-grid">
          <div className="mockexam-field">
            <label htmlFor="mockexam-category">考试类别</label>
            <select
              id="mockexam-category"
              className="mockexam-select"
              value={form.exam_category}
              disabled={loadingBootstrap || loadingItems}
              onChange={(event) => {
                const nextCategory = event.target.value;
                setForm((previous) => ({
                  ...previous,
                  exam_category: nextCategory,
                  exam_content:
                    previous.source_mode === "paper-set"
                      ? ""
                      : options.content_options_map?.[nextCategory]?.[0] || "",
                  source_id: "",
                }));
              }}
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
              disabled={loadingBootstrap || loadingItems}
              onChange={(event) =>
                setForm((previous) => ({
                  ...previous,
                  exam_content: event.target.value,
                  source_id: "",
                }))
              }
            >
              {form.source_mode === "paper-set" ? <option value="">全部</option> : null}
              {contentOptions.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </div>

          <div className="mockexam-field">
            <label htmlFor="mockexam-paper">
              {form.source_mode === "paper-set" ? "组合试卷" : "试卷"}
            </label>
            <select
              id="mockexam-paper"
              className="mockexam-select"
              value={form.source_id}
              disabled={loadingBootstrap || loadingItems || items.length === 0}
              onChange={(event) =>
                setForm((previous) => ({
                  ...previous,
                  source_id: event.target.value,
                }))
              }
            >
              {items.length === 0 ? (
                <option value="">{form.source_mode === "paper-set" ? "暂无组合试卷" : "暂无试卷"}</option>
              ) : null}
              {items.map((item) => (
                <option
                  key={buildSourceId(item, form.source_mode)}
                  value={buildSourceId(item, form.source_mode)}
                >
                  {buildSourceLabel(item, form.source_mode)}
                </option>
              ))}
            </select>
          </div>

          <div className="mockexam-actions">
            <button
              type="button"
              className="mockexam-button"
              disabled={loadingBootstrap || loadingItems || !form.source_id}
              onClick={handleStartPaper}
            >
              {startingSourceId && startingSourceId === String(form.source_id) ? "打开中..." : "开始考试"}
            </button>
          </div>
        </div>

        {loadingItems ? (
          <div className="mockexam-inline-note">
            <LoaderCircle size={16} strokeWidth={2.1} className="spin" />
            {form.source_mode === "paper-set" ? " 正在加载组合试卷..." : " 正在加载试卷..."}
          </div>
        ) : null}

        {message ? <div className="mockexam-inline-note">{message}</div> : null}
      </div>
    </div>
  );
}
