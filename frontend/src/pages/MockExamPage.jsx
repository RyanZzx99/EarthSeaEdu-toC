import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  ArrowLeft,
  BookOpenCheck,
  Layers3,
  LoaderCircle,
  PlayCircle,
  Plus,
  Shuffle,
  Trash2,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  buildMockExamQuickPractice,
  createMockExamExamSet,
  deleteMockExamExamSet,
  getMockExamExamSets,
  getMockExamOptions,
  getMockExamQuestionBanks,
  updateMockExamExamSetStatus,
} from "../api/mockexam";
import {
  createQuickPracticeStorageId,
  saveQuickPracticePayload,
} from "../mockexam/mockexamStorage";
import "../mockexam/mockexam.css";

function toMessage(type, text) {
  return { type, text };
}

function toggleSelection(list, value) {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

function openExamWindow(targetUrl) {
  const openedWindow = window.open("", "_blank");
  if (!openedWindow) {
    return false;
  }
  openedWindow.opener = null;
  openedWindow.location.href = targetUrl;
  return true;
}

function formatExamSetMode(mode) {
  return mode === "random" ? "随机规则" : "手动组卷";
}

function formatSetStatus(status) {
  return status === "1" ? "启用" : "停用";
}

export default function MockExamPage() {
  const navigate = useNavigate();
  const [options, setOptions] = useState({
    exam_category_options: [],
    content_options_map: {},
    supported_categories: [],
  });
  const [selectorForm, setSelectorForm] = useState({
    source_kind: "question-bank",
    exam_category: "IELTS",
    exam_content: "Listening",
    question_bank_id: "",
    exam_set_id: "",
  });
  const [createForm, setCreateForm] = useState({
    name: "",
    mode: "manual",
    exam_category: "IELTS",
    question_bank_ids: [],
    exam_contents: ["Listening"],
    per_content: 1,
    extra_count: 0,
    total_count: 3,
  });
  const [quickPracticeForm, setQuickPracticeForm] = useState({
    exam_category: "IELTS",
    exam_contents: ["Listening"],
    count: 3,
  });
  const [questionBanks, setQuestionBanks] = useState([]);
  const [examSets, setExamSets] = useState([]);
  const [manualQuestionBanks, setManualQuestionBanks] = useState([]);
  const [loadingBootstrap, setLoadingBootstrap] = useState(true);
  const [loadingSelector, setLoadingSelector] = useState(false);
  const [loadingManualQuestionBanks, setLoadingManualQuestionBanks] = useState(false);
  const [loadingCreate, setLoadingCreate] = useState(false);
  const [loadingQuickPractice, setLoadingQuickPractice] = useState(false);
  const [loadingStartExam, setLoadingStartExam] = useState(false);
  const [actingExamSetId, setActingExamSetId] = useState("");
  const [message, setMessage] = useState(null);
  const [createMessage, setCreateMessage] = useState("");
  const [quickPracticeMessage, setQuickPracticeMessage] = useState("");

  const selectorContentOptions = useMemo(
    () => options.content_options_map?.[selectorForm.exam_category] || [],
    [options.content_options_map, selectorForm.exam_category]
  );
  const createContentOptions = useMemo(
    () => options.content_options_map?.[createForm.exam_category] || [],
    [options.content_options_map, createForm.exam_category]
  );
  const quickPracticeContentOptions = useMemo(
    () => options.content_options_map?.[quickPracticeForm.exam_category] || [],
    [options.content_options_map, quickPracticeForm.exam_category]
  );
  const enabledExamSets = useMemo(
    () => examSets.filter((item) => item.status === "1"),
    [examSets]
  );
  const isSupportedSelectorCategory = useMemo(
    () => (options.supported_categories || []).includes(selectorForm.exam_category),
    [options.supported_categories, selectorForm.exam_category]
  );

  useEffect(() => {
    let active = true;

    async function bootstrapPage() {
      try {
        const response = await getMockExamOptions();
        if (!active) {
          return;
        }

        const nextOptions = response.data || {
          exam_category_options: [],
          content_options_map: {},
          supported_categories: [],
        };
        const initialCategory = nextOptions.exam_category_options?.[0] || "IELTS";
        const initialContent = nextOptions.content_options_map?.[initialCategory]?.[0] || "";

        setOptions(nextOptions);
        setSelectorForm({
          source_kind: "question-bank",
          exam_category: initialCategory,
          exam_content: initialContent,
          question_bank_id: "",
          exam_set_id: "",
        });
        setCreateForm({
          name: "",
          mode: "manual",
          exam_category: initialCategory,
          question_bank_ids: [],
          exam_contents: initialContent ? [initialContent] : [],
          per_content: 1,
          extra_count: 0,
          total_count: 3,
        });
        setQuickPracticeForm({
          exam_category: initialCategory,
          exam_contents: initialContent ? [initialContent] : [],
          count: 3,
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
    if (loadingBootstrap || !selectorForm.exam_category) {
      return;
    }

    let active = true;

    async function loadSelectorResources() {
      setLoadingSelector(true);
      try {
        const [questionBankResponse, examSetResponse] = await Promise.all([
          getMockExamQuestionBanks({
            exam_category: selectorForm.exam_category,
            exam_content: selectorForm.exam_content || undefined,
          }),
          getMockExamExamSets({
            exam_category: selectorForm.exam_category,
            exam_content: selectorForm.exam_content || undefined,
          }),
        ]);
        if (!active) {
          return;
        }

        const nextQuestionBanks = questionBankResponse.data?.items || [];
        const nextExamSets = examSetResponse.data?.items || [];
        const nextEnabledExamSets = nextExamSets.filter((item) => item.status === "1");

        setQuestionBanks(nextQuestionBanks);
        setExamSets(nextExamSets);
        setSelectorForm((previous) => ({
          ...previous,
          question_bank_id: nextQuestionBanks.some((item) => String(item.id) === String(previous.question_bank_id))
            ? previous.question_bank_id
            : nextQuestionBanks[0]
              ? String(nextQuestionBanks[0].id)
              : "",
          exam_set_id: nextEnabledExamSets.some((item) => String(item.exam_sets_id) === String(previous.exam_set_id))
            ? previous.exam_set_id
            : nextEnabledExamSets[0]
              ? String(nextEnabledExamSets[0].exam_sets_id)
              : "",
        }));

        if (!(options.supported_categories || []).includes(selectorForm.exam_category)) {
          setMessage(toMessage("warn", `${selectorForm.exam_category} 模考正在等待更新，当前仅开放 IELTS。`));
          return;
        }

        if (selectorForm.source_kind === "question-bank" && nextQuestionBanks.length === 0) {
          setMessage(toMessage("warn", "当前筛选条件下没有可用题库，请先上传并启用题库。"));
          return;
        }

        if (selectorForm.source_kind === "exam-set" && nextEnabledExamSets.length === 0) {
          setMessage(toMessage("warn", "当前筛选条件下没有可用试卷，请先在下方创建组合试卷。"));
          return;
        }

        setMessage(null);
      } catch (error) {
        if (!active) {
          return;
        }
        setQuestionBanks([]);
        setExamSets([]);
        setMessage(toMessage("error", error?.response?.data?.detail || "模考资源加载失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoadingSelector(false);
        }
      }
    }

    void loadSelectorResources();

    return () => {
      active = false;
    };
  }, [loadingBootstrap, selectorForm.exam_category, selectorForm.exam_content, selectorForm.source_kind, options.supported_categories]);

  useEffect(() => {
    if (loadingBootstrap || !createForm.exam_category) {
      return;
    }

    let active = true;

    async function loadManualQuestionBanks() {
      setLoadingManualQuestionBanks(true);
      try {
        const response = await getMockExamQuestionBanks({
          exam_category: createForm.exam_category,
        });
        if (!active) {
          return;
        }

        const items = response.data?.items || [];
        setManualQuestionBanks(items);
        setCreateForm((previous) => ({
          ...previous,
          question_bank_ids: previous.question_bank_ids.filter((id) =>
            items.some((item) => String(item.id) === String(id))
          ),
        }));
      } catch (error) {
        if (!active) {
          return;
        }
        setManualQuestionBanks([]);
        setCreateMessage(error?.response?.data?.detail || "组卷题库列表加载失败。");
      } finally {
        if (active) {
          setLoadingManualQuestionBanks(false);
        }
      }
    }

    void loadManualQuestionBanks();

    return () => {
      active = false;
    };
  }, [createForm.exam_category, loadingBootstrap]);

  function handleSelectorCategoryChange(nextCategory) {
    const nextContent = options.content_options_map?.[nextCategory]?.[0] || "";
    setMessage(null);
    setSelectorForm((previous) => ({
      ...previous,
      exam_category: nextCategory,
      exam_content: nextContent,
      question_bank_id: "",
      exam_set_id: "",
    }));
  }

  function handleStartExam() {
    if (selectorForm.source_kind === "question-bank" && !selectorForm.question_bank_id) {
      setMessage(toMessage("warn", "请先选择题库文件。"));
      return;
    }

    if (selectorForm.source_kind === "exam-set" && !selectorForm.exam_set_id) {
      setMessage(toMessage("warn", "请先选择组合试卷。"));
      return;
    }

    if (!isSupportedSelectorCategory) {
      setMessage(toMessage("warn", `${selectorForm.exam_category} 模考正在等待更新，当前仅开放 IELTS。`));
      return;
    }

    setLoadingStartExam(true);
    const targetUrl =
      selectorForm.source_kind === "question-bank"
        ? `/mockexam/run/question-bank/${selectorForm.question_bank_id}`
        : `/mockexam/run/exam-set/${selectorForm.exam_set_id}`;
    const opened = openExamWindow(targetUrl);
    if (!opened) {
      setMessage(toMessage("warn", "浏览器拦截了新页面，请允许弹窗后重试。"));
    }
    setLoadingStartExam(false);
  }

  async function refreshExamSetList() {
    const response = await getMockExamExamSets({
      exam_category: selectorForm.exam_category,
      exam_content: selectorForm.exam_content || undefined,
    });
    const items = response.data?.items || [];
    setExamSets(items);
    setSelectorForm((previous) => {
      const nextEnabledExamSets = items.filter((item) => item.status === "1");
      return {
        ...previous,
        exam_set_id: nextEnabledExamSets.some((item) => String(item.exam_sets_id) === String(previous.exam_set_id))
          ? previous.exam_set_id
          : nextEnabledExamSets[0]
            ? String(nextEnabledExamSets[0].exam_sets_id)
            : "",
      };
    });
  }

  async function handleCreateExamSet() {
    setCreateMessage("");

    if (!createForm.name.trim()) {
      setCreateMessage("请输入组合试卷名称。");
      return;
    }

    if (createForm.mode === "manual" && createForm.question_bank_ids.length === 0) {
      setCreateMessage("手动组卷至少选择一个题库。");
      return;
    }

    try {
      setLoadingCreate(true);
      await createMockExamExamSet({
        name: createForm.name.trim(),
        mode: createForm.mode,
        exam_category: createForm.exam_category,
        question_bank_ids: createForm.question_bank_ids.map((item) => Number(item)),
        exam_contents: createForm.exam_contents,
        per_content: Number(createForm.per_content) || 1,
        extra_count: Number(createForm.extra_count) || 0,
        total_count: Number(createForm.total_count) || 3,
      });

      setCreateMessage("组合试卷创建成功。");
      setCreateForm((previous) => ({
        ...previous,
        name: "",
        question_bank_ids: [],
      }));
      await refreshExamSetList();
    } catch (error) {
      setCreateMessage(error?.response?.data?.detail || "组合试卷创建失败。");
    } finally {
      setLoadingCreate(false);
    }
  }

  async function handleBuildQuickPractice() {
    setQuickPracticeMessage("");

    if (!(options.supported_categories || []).includes(quickPracticeForm.exam_category)) {
      setQuickPracticeMessage(`${quickPracticeForm.exam_category} 模考正在等待更新，当前仅开放 IELTS。`);
      return;
    }

    try {
      setLoadingQuickPractice(true);
      const response = await buildMockExamQuickPractice({
        exam_category: quickPracticeForm.exam_category,
        exam_contents: quickPracticeForm.exam_contents,
        count: Number(quickPracticeForm.count) || 3,
      });
      const data = response.data || {};
      const storageId = createQuickPracticeStorageId();
      saveQuickPracticePayload(storageId, data);
      setQuickPracticeMessage(
        data.picked_items?.length
          ? `已组合 ${data.picked_items.length} 份题库，正在打开新页面。`
          : "随堂小练已生成，正在打开新页面。"
      );
      const opened = openExamWindow(`/mockexam/run/quick-practice/${storageId}`);
      if (!opened) {
        setQuickPracticeMessage("浏览器拦截了新页面，请允许弹窗后重试。");
      }
    } catch (error) {
      setQuickPracticeMessage(error?.response?.data?.detail || "随堂小练生成失败。");
    } finally {
      setLoadingQuickPractice(false);
    }
  }

  async function handleToggleExamSetStatus(item) {
    try {
      setActingExamSetId(String(item.exam_sets_id));
      await updateMockExamExamSetStatus(item.exam_sets_id, {
        status: item.status === "1" ? "0" : "1",
      });
      await refreshExamSetList();
    } catch (error) {
      setMessage(toMessage("error", error?.response?.data?.detail || "试卷状态更新失败。"));
    } finally {
      setActingExamSetId("");
    }
  }

  async function handleDeleteExamSet(item) {
    const confirmed = window.confirm(`确认删除“${item.name}”吗？`);
    if (!confirmed) {
      return;
    }

    try {
      setActingExamSetId(String(item.exam_sets_id));
      await deleteMockExamExamSet(item.exam_sets_id);
      await refreshExamSetList();
    } catch (error) {
      setMessage(toMessage("error", error?.response?.data?.detail || "试卷删除失败。"));
    } finally {
      setActingExamSetId("");
    }
  }

  function handleStartExamSet(item) {
    if (item.status !== "1") {
      setMessage(toMessage("warn", "当前试卷已停用，请先启用后再开始模考。"));
      return;
    }
    if (!(options.supported_categories || []).includes(item.exam_category)) {
      setMessage(toMessage("warn", `${item.exam_category} 模考正在等待更新，当前仅开放 IELTS。`));
      return;
    }
    const opened = openExamWindow(`/mockexam/run/exam-set/${item.exam_sets_id}`);
    if (!opened) {
      setMessage(toMessage("warn", "浏览器拦截了新页面，请允许弹窗后重试。"));
    }
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
              <h2>开始模考</h2>
            </div>
            <div className="mockexam-panel-badge">
              <BookOpenCheck size={22} strokeWidth={2.2} />
            </div>
          </div>

          <div className="mockexam-segmented">
            <button
              type="button"
              className={selectorForm.source_kind === "question-bank" ? "mockexam-segmented-button active" : "mockexam-segmented-button"}
              onClick={() => setSelectorForm((previous) => ({ ...previous, source_kind: "question-bank" }))}
            >
              选择题目
            </button>
            <button
              type="button"
              className={selectorForm.source_kind === "exam-set" ? "mockexam-segmented-button active" : "mockexam-segmented-button"}
              onClick={() => setSelectorForm((previous) => ({ ...previous, source_kind: "exam-set" }))}
            >
              选择试卷
            </button>
          </div>

          <div className="mockexam-form-grid">
            <div className="mockexam-field">
              <label htmlFor="mockexam-category">考试类别</label>
              <select
                id="mockexam-category"
                className="mockexam-select"
                value={selectorForm.exam_category}
                disabled={loadingBootstrap}
                onChange={(event) => handleSelectorCategoryChange(event.target.value)}
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
                value={selectorForm.exam_content}
                disabled={loadingBootstrap}
                onChange={(event) =>
                  setSelectorForm((previous) => ({
                    ...previous,
                    exam_content: event.target.value,
                    question_bank_id: "",
                    exam_set_id: "",
                  }))
                }
              >
                {selectorContentOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </div>

            {selectorForm.source_kind === "question-bank" ? (
              <div className="mockexam-field">
                <label htmlFor="mockexam-question-bank">文件名</label>
                <select
                  id="mockexam-question-bank"
                  className="mockexam-select"
                  value={selectorForm.question_bank_id}
                  disabled={loadingSelector || questionBanks.length === 0}
                  onChange={(event) =>
                    setSelectorForm((previous) => ({
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
            ) : (
              <div className="mockexam-field">
                <label htmlFor="mockexam-exam-set">组合试卷</label>
                <select
                  id="mockexam-exam-set"
                  className="mockexam-select"
                  value={selectorForm.exam_set_id}
                  disabled={loadingSelector || enabledExamSets.length === 0}
                  onChange={(event) =>
                    setSelectorForm((previous) => ({
                      ...previous,
                      exam_set_id: event.target.value,
                    }))
                  }
                >
                  {enabledExamSets.length === 0 ? <option value="">暂无试卷</option> : null}
                  {enabledExamSets.map((item) => (
                    <option key={item.exam_sets_id} value={item.exam_sets_id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="mockexam-actions">
              <button
                type="button"
                className="mockexam-button"
                onClick={handleStartExam}
                disabled={
                  loadingBootstrap ||
                  loadingSelector ||
                  loadingStartExam ||
                  (selectorForm.source_kind === "question-bank" && !selectorForm.question_bank_id) ||
                  (selectorForm.source_kind === "exam-set" && !selectorForm.exam_set_id)
                }
              >
                {loadingStartExam ? (
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

        <div className="mockexam-management-grid">
          <motion.section
            className="mockexam-panel"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, delay: 0.04 }}
          >
            <div className="mockexam-panel-head">
              <div>
                <h3>创建组合试卷</h3>
              </div>
              <div className="mockexam-panel-badge">
                <Plus size={22} strokeWidth={2.2} />
              </div>
            </div>

            <div className="mockexam-manage-grid">
              <div className="mockexam-field">
                <label>试卷名称</label>
                <input
                  className="mockexam-input"
                  value={createForm.name}
                  onChange={(event) => {
                    setCreateMessage("");
                    setCreateForm((previous) => ({ ...previous, name: event.target.value }));
                  }}
                  placeholder="例如：IELTS 听力组合卷 01"
                />
              </div>
              <div className="mockexam-field">
                <label>组卷模式</label>
                <select
                  className="mockexam-select"
                  value={createForm.mode}
                  onChange={(event) => {
                    setCreateMessage("");
                    setCreateForm((previous) => ({ ...previous, mode: event.target.value }));
                  }}
                >
                  <option value="manual">手动组卷</option>
                  <option value="random">随机规则</option>
                </select>
              </div>
              <div className="mockexam-field">
                <label>考试类别</label>
                <select
                  className="mockexam-select"
                  value={createForm.exam_category}
                  onChange={(event) => {
                    const nextCategory = event.target.value;
                    const nextContent = options.content_options_map?.[nextCategory]?.[0] || "";
                    setCreateMessage("");
                    setCreateForm((previous) => ({
                      ...previous,
                      exam_category: nextCategory,
                      question_bank_ids: [],
                      exam_contents: nextContent ? [nextContent] : [],
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
            </div>

            {createForm.mode === "manual" ? (
              <div className="mockexam-select-list">
                {loadingManualQuestionBanks ? (
                  <div className="mockexam-empty">正在加载可选题库…</div>
                ) : manualQuestionBanks.length === 0 ? (
                  <div className="mockexam-empty">当前考试类别下还没有可用题库。</div>
                ) : (
                  manualQuestionBanks.map((item) => (
                    <label key={item.id} className="mockexam-select-item">
                      <input
                        type="checkbox"
                        checked={createForm.question_bank_ids.includes(String(item.id))}
                        onChange={() =>
                          setCreateForm((previous) => ({
                            ...previous,
                            question_bank_ids: toggleSelection(previous.question_bank_ids, String(item.id)),
                          }))
                        }
                      />
                      <span>{item.file_name}</span>
                      <em>{item.exam_content}</em>
                    </label>
                  ))
                )}
              </div>
            ) : (
              <div className="mockexam-random-builder">
                <div className="mockexam-chip-grid">
                  {createContentOptions.map((item) => (
                    <button
                      key={item}
                      type="button"
                      className={createForm.exam_contents.includes(item) ? "mockexam-chip-button active" : "mockexam-chip-button"}
                      onClick={() =>
                        setCreateForm((previous) => ({
                          ...previous,
                          exam_contents: toggleSelection(previous.exam_contents, item),
                        }))
                      }
                    >
                      {item}
                    </button>
                  ))}
                </div>
                <div className="mockexam-manage-grid">
                  <div className="mockexam-field">
                    <label>每个内容随机份数</label>
                    <input
                      className="mockexam-input"
                      type="number"
                      min="0"
                      max="20"
                      value={createForm.per_content}
                      onChange={(event) => setCreateForm((previous) => ({ ...previous, per_content: event.target.value }))}
                    />
                  </div>
                  <div className="mockexam-field">
                    <label>额外随机份数</label>
                    <input
                      className="mockexam-input"
                      type="number"
                      min="0"
                      max="20"
                      value={createForm.extra_count}
                      onChange={(event) => setCreateForm((previous) => ({ ...previous, extra_count: event.target.value }))}
                    />
                  </div>
                  <div className="mockexam-field">
                    <label>目标总份数</label>
                    <input
                      className="mockexam-input"
                      type="number"
                      min="1"
                      max="50"
                      value={createForm.total_count}
                      onChange={(event) => setCreateForm((previous) => ({ ...previous, total_count: event.target.value }))}
                    />
                  </div>
                </div>
              </div>
            )}

            <div className="mockexam-inline-actions">
              <button type="button" className="mockexam-secondary-button" disabled={loadingCreate} onClick={handleCreateExamSet}>
                {loadingCreate ? "创建中..." : "创建组合试卷"}
              </button>
            </div>
            {createMessage ? <div className="mockexam-inline-note">{createMessage}</div> : null}
          </motion.section>

          <motion.section
            className="mockexam-panel"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, delay: 0.08 }}
          >
            <div className="mockexam-panel-head">
              <div>
                <h3>随堂小练</h3>
              </div>
              <div className="mockexam-panel-badge">
                <Shuffle size={22} strokeWidth={2.2} />
              </div>
            </div>

            <div className="mockexam-manage-grid">
              <div className="mockexam-field">
                <label>考试类别</label>
                <select
                  className="mockexam-select"
                  value={quickPracticeForm.exam_category}
                  onChange={(event) => {
                    const nextCategory = event.target.value;
                    const nextContent = options.content_options_map?.[nextCategory]?.[0] || "";
                    setQuickPracticeMessage("");
                    setQuickPracticeForm((previous) => ({
                      ...previous,
                      exam_category: nextCategory,
                      exam_contents: nextContent ? [nextContent] : [],
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
                <label>组合份数</label>
                <input
                  className="mockexam-input"
                  type="number"
                  min="1"
                  max="20"
                  value={quickPracticeForm.count}
                  onChange={(event) => setQuickPracticeForm((previous) => ({ ...previous, count: event.target.value }))}
                />
              </div>
            </div>

            <div className="mockexam-chip-grid">
              {quickPracticeContentOptions.map((item) => (
                <button
                  key={item}
                  type="button"
                  className={quickPracticeForm.exam_contents.includes(item) ? "mockexam-chip-button active" : "mockexam-chip-button"}
                  onClick={() =>
                    setQuickPracticeForm((previous) => ({
                      ...previous,
                      exam_contents: toggleSelection(previous.exam_contents, item),
                    }))
                  }
                >
                  {item}
                </button>
              ))}
            </div>

            <div className="mockexam-inline-actions">
              <button type="button" className="mockexam-secondary-button" disabled={loadingQuickPractice} onClick={handleBuildQuickPractice}>
                {loadingQuickPractice ? "生成中..." : "立即生成并开始"}
              </button>
            </div>
            {quickPracticeMessage ? <div className="mockexam-inline-note">{quickPracticeMessage}</div> : null}
          </motion.section>
        </div>

        <motion.section
          className="mockexam-panel"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, delay: 0.12 }}
        >
          <div className="mockexam-panel-head">
            <div>
              <h3>组合试卷列表</h3>
            </div>
            <div className="mockexam-panel-badge">
              <Layers3 size={22} strokeWidth={2.2} />
            </div>
          </div>

          {examSets.length === 0 ? (
            <div className="mockexam-empty">当前筛选条件下还没有组合试卷。</div>
          ) : (
            <div className="mockexam-set-list">
              {examSets.map((item) => (
                <div key={item.exam_sets_id} className="mockexam-set-card">
                  <div className="mockexam-set-head">
                    <div>
                      <h4>{item.name}</h4>
                      <div className="mockexam-set-meta">
                        <span>{formatExamSetMode(item.mode)}</span>
                        <span>{item.exam_category}</span>
                        <span>{item.content_summary || "随机内容"}</span>
                        <span>{item.part_count} 份题库</span>
                      </div>
                    </div>
                    <span className={item.status === "1" ? "mockexam-status-chip active" : "mockexam-status-chip"}>
                      {formatSetStatus(item.status)}
                    </span>
                  </div>

                  {item.question_bank_names?.length ? (
                    <div className="mockexam-set-files">{item.question_bank_names.join(" / ")}</div>
                  ) : null}

                  <div className="mockexam-card-actions">
                    <button
                      type="button"
                      className="mockexam-action-button primary"
                      onClick={() => handleStartExamSet(item)}
                      disabled={actingExamSetId === String(item.exam_sets_id)}
                    >
                      <PlayCircle size={15} strokeWidth={2.2} />
                      开始模考
                    </button>
                    <button
                      type="button"
                      className="mockexam-action-button"
                      onClick={() => handleToggleExamSetStatus(item)}
                      disabled={actingExamSetId === String(item.exam_sets_id)}
                    >
                      {item.status === "1" ? "停用" : "启用"}
                    </button>
                    <button
                      type="button"
                      className="mockexam-action-button danger"
                      onClick={() => handleDeleteExamSet(item)}
                      disabled={actingExamSetId === String(item.exam_sets_id)}
                    >
                      <Trash2 size={15} strokeWidth={2.1} />
                      删除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </motion.section>
      </div>
    </div>
  );
}
