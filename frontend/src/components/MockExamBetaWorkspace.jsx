import React, { useEffect, useMemo, useState } from "react";
import { BookOpenCheck, LoaderCircle, PlayCircle } from "lucide-react";
import { getMockExamBetaOptions, getMockExamBetaPapers } from "../api/mockexam";

function openExamWindow(targetUrl) {
  const openedWindow = window.open("", "_blank");
  if (!openedWindow) {
    return false;
  }
  openedWindow.opener = null;
  openedWindow.location.href = targetUrl;
  return true;
}

function formatPaperMeta(item) {
  const parts = [];
  if (item.book_code) {
    parts.push(`Book ${item.book_code}`);
  }
  if (item.test_no) {
    parts.push(`Test ${item.test_no}`);
  }
  if (item.paper_code) {
    parts.push(item.paper_code);
  }
  return parts.join(" · ");
}

export default function MockExamBetaWorkspace() {
  const [options, setOptions] = useState({
    exam_category_options: [],
    content_options_map: {},
  });
  const [form, setForm] = useState({
    exam_category: "IELTS",
    exam_content: "Listening",
    exam_paper_id: "",
  });
  const [papers, setPapers] = useState([]);
  const [loadingBootstrap, setLoadingBootstrap] = useState(true);
  const [loadingPapers, setLoadingPapers] = useState(false);
  const [startingPaperId, setStartingPaperId] = useState("");
  const [message, setMessage] = useState("");

  const contentOptions = useMemo(
    () => options.content_options_map?.[form.exam_category] || [],
    [options.content_options_map, form.exam_category]
  );

  useEffect(() => {
    let active = true;

    async function bootstrapPage() {
      try {
        const response = await getMockExamBetaOptions();
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
          exam_category: initialCategory,
          exam_content: initialContent,
          exam_paper_id: "",
        });
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(error?.response?.data?.detail || "测试版模拟考试初始化失败，请稍后重试。");
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

    async function loadPapers() {
      try {
        setLoadingPapers(true);
        const response = await getMockExamBetaPapers({
          exam_category: form.exam_category,
          exam_content: form.exam_content || undefined,
        });
        if (!active) {
          return;
        }

        const nextPapers = response.data?.items || [];
        setPapers(nextPapers);
        setForm((previous) => ({
          ...previous,
          exam_paper_id: nextPapers.some(
            (item) => String(item.exam_paper_id) === String(previous.exam_paper_id)
          )
            ? previous.exam_paper_id
            : nextPapers[0]
              ? String(nextPapers[0].exam_paper_id)
              : "",
        }));
        setMessage(nextPapers.length ? "" : "当前筛选条件下还没有可用的测试版试卷。");
      } catch (error) {
        if (!active) {
          return;
        }
        setPapers([]);
        setMessage(error?.response?.data?.detail || "测试版试卷列表加载失败，请稍后重试。");
      } finally {
        if (active) {
          setLoadingPapers(false);
        }
      }
    }

    void loadPapers();

    return () => {
      active = false;
    };
  }, [form.exam_category, form.exam_content, loadingBootstrap]);

  function handleStartPaper(examPaperId) {
    const resolvedPaperId = String(examPaperId || form.exam_paper_id || "");
    if (!resolvedPaperId) {
      setMessage("请先选择一套测试版试卷。");
      return;
    }

    setStartingPaperId(resolvedPaperId);
    const opened = openExamWindow(`/mockexam/run/paper-beta/${resolvedPaperId}`);
    if (!opened) {
      setMessage("浏览器拦截了新页面，请允许弹窗后重试。");
    } else {
      setMessage("");
    }
    setStartingPaperId("");
  }

  return (
    <div className="mockexam-beta-layout">
      <div className="mockexam-panel">
        <div className="mockexam-panel-head">
          <div>
            <h2>模拟考试-测试</h2>
            <p>按新的 `exam_*` 结构化题库直接组装试卷，当前只开放 IELTS Reading / Listening。</p>
          </div>
          <span className="mockexam-panel-badge">
            <BookOpenCheck size={22} strokeWidth={2.1} />
          </span>
        </div>

        <div className="mockexam-form-grid mockexam-beta-form-grid">
          <div className="mockexam-field">
            <label htmlFor="mockexam-beta-category">考试类别</label>
            <select
              id="mockexam-beta-category"
              className="mockexam-select"
              value={form.exam_category}
              disabled={loadingBootstrap || loadingPapers}
              onChange={(event) => {
                const nextCategory = event.target.value;
                const nextContent = options.content_options_map?.[nextCategory]?.[0] || "";
                setForm({
                  exam_category: nextCategory,
                  exam_content: nextContent,
                  exam_paper_id: "",
                });
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
            <label htmlFor="mockexam-beta-content">考试内容</label>
            <select
              id="mockexam-beta-content"
              className="mockexam-select"
              value={form.exam_content}
              disabled={loadingBootstrap || loadingPapers}
              onChange={(event) =>
                setForm((previous) => ({
                  ...previous,
                  exam_content: event.target.value,
                  exam_paper_id: "",
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
            <label htmlFor="mockexam-beta-paper">测试版试卷</label>
            <select
              id="mockexam-beta-paper"
              className="mockexam-select"
              value={form.exam_paper_id}
              disabled={loadingBootstrap || loadingPapers || papers.length === 0}
              onChange={(event) =>
                setForm((previous) => ({
                  ...previous,
                  exam_paper_id: event.target.value,
                }))
              }
            >
              {papers.length === 0 ? <option value="">暂无可用试卷</option> : null}
              {papers.map((item) => (
                <option key={item.exam_paper_id} value={item.exam_paper_id}>
                  {item.paper_name}
                </option>
              ))}
            </select>
          </div>

          <div className="mockexam-actions">
            <button
              type="button"
              className="mockexam-button"
              disabled={loadingBootstrap || loadingPapers || !form.exam_paper_id}
              onClick={() => handleStartPaper(form.exam_paper_id)}
            >
              {startingPaperId && startingPaperId === String(form.exam_paper_id) ? "打开中..." : "开始测试版模考"}
            </button>
          </div>
        </div>

        {loadingPapers ? (
          <div className="mockexam-inline-note">
            <LoaderCircle size={16} strokeWidth={2.1} className="spin" /> 正在加载结构化试卷...
          </div>
        ) : null}

        {message ? <div className="mockexam-inline-note">{message}</div> : null}
      </div>

      <div className="mockexam-panel">
        <div className="mockexam-panel-head">
          <div>
            <h3>当前可用试卷</h3>
            <p>这里展示已经按新方案入库、且可直接被 runner 打开的试卷。</p>
          </div>
        </div>

        <div className="mockexam-beta-paper-list">
          {papers.map((item) => (
            <article key={item.exam_paper_id} className="mockexam-beta-paper-card">
              <div className="mockexam-beta-paper-copy">
                <div className="mockexam-beta-paper-tags">
                  <span>{item.exam_content}</span>
                  <span>{item.bank_name}</span>
                </div>
                <h4>{item.paper_name}</h4>
                <p>{formatPaperMeta(item) || item.module_name || "结构化试卷"}</p>
              </div>

              <button
                type="button"
                className="mockexam-secondary-button"
                disabled={startingPaperId === String(item.exam_paper_id)}
                onClick={() => handleStartPaper(item.exam_paper_id)}
              >
                <PlayCircle size={16} strokeWidth={2.1} />
                <span>打开试卷</span>
              </button>
            </article>
          ))}

          {!loadingPapers && papers.length === 0 ? (
            <div className="mockexam-beta-empty">当前筛选下没有结构化试卷。</div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
