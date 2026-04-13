import React, { useEffect, useMemo, useState } from "react";
import { LoaderCircle } from "lucide-react";
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

export default function MockExamWorkspace({
  sourceMode = "paper",
  title = "",
  description = "",
  tags = [],
  buttonLabel = "开始练习",
  cardClassName = "",
}) {
  const [options, setOptions] = useState({
    exam_category_options: [],
    content_options_map: {},
  });
  const [form, setForm] = useState({
    exam_category: "IELTS",
    exam_content: sourceMode === "paper-set" ? "" : "Listening",
    source_id: "",
  });
  const [items, setItems] = useState([]);
  const [loadingBootstrap, setLoadingBootstrap] = useState(true);
  const [loadingItems, setLoadingItems] = useState(false);
  const [startingSourceId, setStartingSourceId] = useState("");
  const [message, setMessage] = useState("");

  const contentOptions = useMemo(() => {
    if (sourceMode === "paper-set") {
      return ["Listening", "Reading", "Mixed"];
    }
    return options.content_options_map?.[form.exam_category] || [];
  }, [form.exam_category, options.content_options_map, sourceMode]);

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
        const initialContent =
          sourceMode === "paper-set"
            ? ""
            : nextOptions.content_options_map?.[initialCategory]?.[0] || "Listening";

        setOptions(nextOptions);
        setForm({
          exam_category: initialCategory,
          exam_content: initialContent,
          source_id: "",
        });
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(getApiError(error, "初始化练习列表失败，请稍后重试。"));
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
  }, [sourceMode]);

  useEffect(() => {
    if (loadingBootstrap || !form.exam_category) {
      return undefined;
    }

    let active = true;

    async function loadItems() {
      try {
        setLoadingItems(true);
        const response =
          sourceMode === "paper-set"
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
        setForm((previous) => ({
          ...previous,
          source_id: nextItems[0] ? String(buildSourceId(nextItems[0], sourceMode)) : "",
        }));
        setMessage(
          nextItems.length
            ? ""
            : sourceMode === "paper-set"
              ? "当前筛选条件下还没有组合试卷。"
              : "当前筛选条件下还没有单张试卷。"
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setItems([]);
        setMessage(
          getApiError(
            error,
            sourceMode === "paper-set" ? "组合试卷列表加载失败，请稍后重试。" : "单张试卷列表加载失败，请稍后重试。"
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
  }, [form.exam_category, form.exam_content, loadingBootstrap, sourceMode]);

  function handleStart() {
    const resolvedSourceId = String(form.source_id || "");
    if (!resolvedSourceId) {
      setMessage(sourceMode === "paper-set" ? "请先选择一套组合试卷。" : "请先选择一张试卷。");
      return;
    }

    setStartingSourceId(resolvedSourceId);
    const opened = openExamWindow(`/mockexam/run/${sourceMode}/${resolvedSourceId}`);
    if (!opened) {
      setMessage("浏览器拦截了新页面，请允许弹窗后重试。");
    } else {
      setMessage("");
    }
    setStartingSourceId("");
  }

  return (
    <article className={`home-card mockexam-home-card ${cardClassName}`.trim()}>
      <div className="mockexam-home-card-copy">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>

      <div className="mockexam-home-tags">
        {tags.map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>

      <div className="mockexam-home-form-grid">
        <label className="mockexam-home-field">
          <span>考试类别</span>
          <select
            className="mockexam-select"
            value={form.exam_category}
            disabled={loadingBootstrap || loadingItems}
            onChange={(event) => {
              const nextCategory = event.target.value;
              setForm((previous) => ({
                ...previous,
                exam_category: nextCategory,
                exam_content:
                  sourceMode === "paper-set" ? "" : options.content_options_map?.[nextCategory]?.[0] || "",
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
        </label>

        <label className="mockexam-home-field">
          <span>考试内容</span>
          <select
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
            {sourceMode === "paper-set" ? <option value="">全部</option> : null}
            {contentOptions.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>

        <label className="mockexam-home-field mockexam-home-field-wide">
          <span>{sourceMode === "paper-set" ? "组合试卷" : "单张试卷"}</span>
          <select
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
              <option value="">{sourceMode === "paper-set" ? "暂无组合试卷" : "暂无单张试卷"}</option>
            ) : null}
            {items.map((item) => (
              <option key={buildSourceId(item, sourceMode)} value={buildSourceId(item, sourceMode)}>
                {buildSourceLabel(item, sourceMode)}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="mockexam-home-card-actions">
        <button
          type="button"
          className="home-primary-button mockexam-home-primary-button"
          disabled={loadingBootstrap || loadingItems || !form.source_id}
          onClick={handleStart}
        >
          {startingSourceId && startingSourceId === String(form.source_id) ? "正在打开..." : buttonLabel}
        </button>
      </div>

      {loadingItems ? (
        <div className="mockexam-inline-note">
          <LoaderCircle size={16} strokeWidth={2.1} className="spin" />
          {sourceMode === "paper-set" ? "正在加载组合试卷..." : "正在加载单张试卷..."}
        </div>
      ) : null}

      {message ? <div className="mockexam-inline-note">{message}</div> : null}
    </article>
  );
}
