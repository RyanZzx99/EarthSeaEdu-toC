import React, { useEffect, useMemo, useState } from "react";
import { LoaderCircle, RefreshCcw } from "lucide-react";
import TeacherPortalLayout from "../components/TeacherPortalLayout";
import {
  createTeacherMockExamPaperSet,
  getMockExamPapers,
  getTeacherMockExamPaperSets,
  updateTeacherMockExamPaperSetStatus,
} from "../api/mockexam";
import "../mockexam/mockexam.css";

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

function formatTime(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function TeacherMockExamPage() {
  const [form, setForm] = useState({
    set_name: "",
    remark: "",
    exam_content: "",
  });
  const [availablePapers, setAvailablePapers] = useState([]);
  const [selectedPaperIds, setSelectedPaperIds] = useState([]);
  const [paperSets, setPaperSets] = useState([]);
  const [loadingPapers, setLoadingPapers] = useState(true);
  const [loadingSets, setLoadingSets] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState("");

  const selectedCount = selectedPaperIds.length;

  const sortedAvailablePapers = useMemo(
    () =>
      [...availablePapers].sort((left, right) =>
        String(left.paper_name || left.paper_code || "").localeCompare(
          String(right.paper_name || right.paper_code || ""),
          "zh-CN"
        )
      ),
    [availablePapers]
  );

  async function loadPaperSets() {
    try {
      setLoadingSets(true);
      const response = await getTeacherMockExamPaperSets();
      setPaperSets(response.data?.items || []);
      setMessage("");
    } catch (error) {
      setPaperSets([]);
      setMessage(getApiError(error, "组合试卷列表加载失败。"));
    } finally {
      setLoadingSets(false);
    }
  }

  useEffect(() => {
    let active = true;

    async function loadPapers() {
      try {
        setLoadingPapers(true);
        const response = await getMockExamPapers({
          exam_category: "IELTS",
          exam_content: form.exam_content || undefined,
        });
        if (!active) {
          return;
        }
        setAvailablePapers(response.data?.items || []);
        setSelectedPaperIds((previous) =>
          previous.filter((paperId) =>
            (response.data?.items || []).some((item) => Number(item.exam_paper_id) === Number(paperId))
          )
        );
      } catch (error) {
        if (!active) {
          return;
        }
        setAvailablePapers([]);
        setMessage(getApiError(error, "试卷列表加载失败。"));
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
  }, [form.exam_content]);

  useEffect(() => {
    void loadPaperSets();
  }, []);

  function togglePaperSelection(examPaperId) {
    setSelectedPaperIds((previous) =>
      previous.includes(examPaperId)
        ? previous.filter((item) => item !== examPaperId)
        : [...previous, examPaperId]
    );
  }

  async function handleCreatePaperSet() {
    if (!form.set_name.trim()) {
      setMessage("请输入组合试卷名称。");
      return;
    }
    if (selectedPaperIds.length < 2) {
      setMessage("组合试卷至少选择两张试卷。");
      return;
    }

    try {
      setSubmitting(true);
      await createTeacherMockExamPaperSet({
        set_name: form.set_name.trim(),
        exam_paper_ids: selectedPaperIds,
        remark: form.remark.trim() || null,
      });
      setForm((previous) => ({
        ...previous,
        set_name: "",
        remark: "",
      }));
      setSelectedPaperIds([]);
      setMessage("组合试卷已创建。");
      await loadPaperSets();
    } catch (error) {
      setMessage(getApiError(error, "组合试卷创建失败。"));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleTogglePaperSetStatus(item) {
    try {
      await updateTeacherMockExamPaperSetStatus(item.mockexam_paper_set_id, {
        status: item.status === 1 ? 0 : 1,
      });
      await loadPaperSets();
    } catch (error) {
      setMessage(getApiError(error, "组合试卷状态更新失败。"));
    }
  }

  return (
    <TeacherPortalLayout
      heroTitle="组合试卷管理"
      heroSubtitle="基于结构化题库组合多张试卷，供学生端直接选择并开始考试。"
      showShortcutStrip={false}
    >
      <div className="mockexam-management-grid">
        <section className="mockexam-panel">
          <div className="mockexam-panel-head">
            <div>
              <h2>创建组合试卷</h2>
              <p>可混合选择 Reading / Listening，多张试卷将按你勾选的顺序组合。</p>
            </div>
          </div>

          <div className="mockexam-manage-grid">
            <div className="mockexam-field">
              <label htmlFor="teacher-mockexam-set-name">组合试卷名称</label>
              <input
                id="teacher-mockexam-set-name"
                className="mockexam-input"
                value={form.set_name}
                onChange={(event) =>
                  setForm((previous) => ({
                    ...previous,
                    set_name: event.target.value,
                  }))
                }
                placeholder="例如：4月周测 A 卷"
              />
            </div>

            <div className="mockexam-field">
              <label htmlFor="teacher-mockexam-content-filter">试卷筛选</label>
              <select
                id="teacher-mockexam-content-filter"
                className="mockexam-select"
                value={form.exam_content}
                onChange={(event) =>
                  setForm((previous) => ({
                    ...previous,
                    exam_content: event.target.value,
                  }))
                }
              >
                <option value="">全部</option>
                <option value="Listening">Listening</option>
                <option value="Reading">Reading</option>
              </select>
            </div>

            <div className="mockexam-field">
              <label htmlFor="teacher-mockexam-remark">备注</label>
              <input
                id="teacher-mockexam-remark"
                className="mockexam-input"
                value={form.remark}
                onChange={(event) =>
                  setForm((previous) => ({
                    ...previous,
                    remark: event.target.value,
                  }))
                }
                placeholder="可选"
              />
            </div>
          </div>

          <div className="mockexam-inline-note">
            已选 {selectedCount} 张试卷
            {form.exam_content ? ` / 当前筛选 ${form.exam_content}` : ""}
          </div>

          <div className="mockexam-select-list">
            {loadingPapers ? (
              <div className="mockexam-inline-note">
                <LoaderCircle size={16} strokeWidth={2.1} className="spin" /> 正在加载试卷...
              </div>
            ) : null}

            {!loadingPapers && sortedAvailablePapers.map((item) => (
              <label key={item.exam_paper_id} className="mockexam-select-item">
                <input
                  type="checkbox"
                  checked={selectedPaperIds.includes(item.exam_paper_id)}
                  onChange={() => togglePaperSelection(item.exam_paper_id)}
                />
                <div>
                  <strong>{item.paper_name}</strong>
                  <em>
                    {item.exam_content}
                    {item.paper_code ? ` / ${item.paper_code}` : ""}
                  </em>
                </div>
                <span>{item.bank_name}</span>
              </label>
            ))}

            {!loadingPapers && !sortedAvailablePapers.length ? (
              <div className="mockexam-beta-empty">当前筛选下暂无可选试卷</div>
            ) : null}
          </div>

          <div className="mockexam-inline-actions">
            <button
              type="button"
              className="mockexam-button"
              disabled={submitting || loadingPapers}
              onClick={() => void handleCreatePaperSet()}
            >
              {submitting ? "创建中..." : "创建组合试卷"}
            </button>
          </div>

          {message ? <div className="mockexam-inline-note">{message}</div> : null}
        </section>

        <section className="mockexam-panel">
          <div className="mockexam-panel-head">
            <div>
              <h2>已创建组合试卷</h2>
              <p>这里只展示你创建的组合试卷，可启停控制学生端是否可见。</p>
            </div>
            <button
              type="button"
              className="mockexam-panel-toggle"
              onClick={() => void loadPaperSets()}
            >
              <RefreshCcw size={15} strokeWidth={2.1} />
              <span>刷新</span>
            </button>
          </div>

          <div className="mockexam-history-list">
            {loadingSets ? (
              <div className="mockexam-inline-note">
                <LoaderCircle size={16} strokeWidth={2.1} className="spin" /> 正在加载组合试卷...
              </div>
            ) : null}

            {!loadingSets && paperSets.map((item) => (
              <article key={item.mockexam_paper_set_id} className="mockexam-history-card">
                <div className="mockexam-history-copy">
                  <div className="mockexam-history-tags">
                    <span>{item.status === 1 ? "启用中" : "已停用"}</span>
                    {item.exam_content ? <span>{item.exam_content}</span> : null}
                  </div>
                  <h4>{item.set_name}</h4>
                  <p>
                    共 {item.paper_count || 0} 张
                    {item.paper_names?.length ? ` / ${item.paper_names.join(" + ")}` : ""}
                    {item.create_time ? ` / ${formatTime(item.create_time)}` : ""}
                  </p>
                  {item.remark ? <p>{item.remark}</p> : null}
                </div>

                <div className="mockexam-history-actions">
                  <button
                    type="button"
                    className="mockexam-secondary-button"
                    onClick={() => void handleTogglePaperSetStatus(item)}
                  >
                    {item.status === 1 ? "停用" : "启用"}
                  </button>
                </div>
              </article>
            ))}

            {!loadingSets && !paperSets.length ? (
              <div className="mockexam-beta-empty">你还没有创建组合试卷</div>
            ) : null}
          </div>
        </section>
      </div>
    </TeacherPortalLayout>
  );
}
