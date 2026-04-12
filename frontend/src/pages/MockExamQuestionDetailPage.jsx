import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeft, Heart, LoaderCircle } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { getMockExamQuestionDetail, toggleMockExamFavorite } from "../api/mockexam";
import "../mockexam/mockexam.css";

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function textToHtml(text) {
  const normalized = String(text ?? "").trim();
  if (!normalized) {
    return "";
  }
  return normalized
    .split(/\r?\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => `<p>${escapeHtml(line)}</p>`)
    .join("");
}

function hasMeaningfulHtml(value) {
  const html = String(value ?? "").trim();
  if (!html) {
    return false;
  }
  if (/<(img|audio|video|table|svg|iframe)\b/i.test(html)) {
    return true;
  }
  const text = html
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
  return Boolean(text);
}

function renderAnswer(question) {
  if (!question) {
    return "--";
  }

  if (question.type === "cloze_inline") {
    const blanks = Array.isArray(question.blanks) ? question.blanks : [];
    const answers = [...new Set(blanks.map((blank) => String(blank?.answer ?? "").trim()).filter(Boolean))];
    return answers.length ? answers.join(" / ") : "--";
  }

  if (Array.isArray(question.answer)) {
    const answers = question.answer.map((item) => String(item ?? "").trim()).filter(Boolean);
    return answers.length ? answers.join(", ") : "--";
  }

  if (question.answer && typeof question.answer === "object") {
    const answers = Object.values(question.answer)
      .map((item) => String(item ?? "").trim())
      .filter(Boolean);
    return answers.length ? [...new Set(answers)].join(" / ") : "--";
  }

  const answerText = String(question.answer ?? "").trim();
  return answerText || "--";
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

function buildMaterialHtml(detail) {
  const backendMaterialHtml = detail?.material_html;
  if (hasMeaningfulHtml(backendMaterialHtml)) {
    return backendMaterialHtml;
  }

  const sectionContent = detail?.section?.content;
  if (hasMeaningfulHtml(sectionContent)) {
    return sectionContent;
  }

  const sectionInstructionsHtml = textToHtml(detail?.section?.instructions);
  if (hasMeaningfulHtml(sectionInstructionsHtml)) {
    return sectionInstructionsHtml;
  }

  const groupInstructions = detail?.group?.instructions;
  if (hasMeaningfulHtml(groupInstructions)) {
    return groupInstructions;
  }

  if (detail?.section?.audio) {
    return "<p>本题仅提供听力音频材料，请播放上方音频后作答。</p>";
  }

  return "<p>暂无材料</p>";
}

export default function MockExamQuestionDetailPage() {
  const { examQuestionId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [detail, setDetail] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadDetail() {
      try {
        const response = await getMockExamQuestionDetail(examQuestionId);
        if (!active) {
          return;
        }
        setDetail(response.data || null);
        setMessage("");
      } catch (error) {
        if (!active) {
          return;
        }
        setDetail(null);
        setMessage(getApiError(error, "题目详情加载失败"));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadDetail();
    return () => {
      active = false;
    };
  }, [examQuestionId]);

  const question = detail?.question || null;
  const group = detail?.group || {};
  const section = detail?.section || {};
  const options = useMemo(() => question?.options || group?.options || [], [group?.options, question?.options]);
  const materialHtml = useMemo(() => buildMaterialHtml(detail), [detail]);

  async function handleToggleFavorite() {
    if (!detail) {
      return;
    }
    try {
      await toggleMockExamFavorite(detail.exam_question_id, {
        is_favorite: !detail.is_favorite,
      });
      setDetail((previous) => (previous ? { ...previous, is_favorite: !previous.is_favorite } : previous));
    } catch (error) {
      setMessage(getApiError(error, "收藏操作失败"));
    }
  }

  if (loading) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">
          <LoaderCircle size={18} strokeWidth={2.2} className="spin" />
          <span>正在加载题目详情...</span>
        </div>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">{message || "未找到题目详情"}</div>
      </div>
    );
  }

  return (
    <div className="mockexam-page">
      <div className="mockexam-shell">
        <div className="mockexam-topbar">
          <div className="mockexam-topcopy">
            <button type="button" className="mockexam-back" onClick={() => navigate("/mockexam")}>
              <ArrowLeft size={16} strokeWidth={2.2} />
              返回模拟考试
            </button>
            <h1>{detail.paper_name}</h1>
            <p>
              {detail.exam_category}
              {detail.exam_content ? ` / ${detail.exam_content}` : ""}
              {question?.question_no ? ` / Q${question.question_no}` : ""}
            </p>
          </div>
        </div>

        {message ? <div className="mockexam-inline-note">{message}</div> : null}

        <div className="mockexam-detail-layout">
          <section className="mockexam-panel">
            <div className="mockexam-panel-head">
              <div>
                <h2>{section.title || "材料区"}</h2>
                <p>阅读文章或听力材料</p>
              </div>
            </div>
            {section.audio ? (
              <div className="passage-audio">
                <div className="text-[11px] uppercase tracking-wide text-slate-500 mb-2">Listening Audio</div>
                <audio controls preload="none" src={section.audio} />
              </div>
            ) : null}
            <div className="mockexam-detail-html" dangerouslySetInnerHTML={{ __html: materialHtml }} />
          </section>

          <section className="mockexam-panel">
            <div className="mockexam-panel-head">
              <div>
                <h2>{group.title || "题目"}</h2>
                <p>错题次数 {detail.wrong_count || 0}</p>
              </div>
              <button
                type="button"
                className="mockexam-secondary-button"
                onClick={() => void handleToggleFavorite()}
              >
                <Heart size={16} strokeWidth={2.1} fill={detail.is_favorite ? "currentColor" : "none"} />
                <span>{detail.is_favorite ? "已收藏" : "加入收藏"}</span>
              </button>
            </div>

            {group.instructions ? (
              <div className="mockexam-detail-html" dangerouslySetInnerHTML={{ __html: group.instructions }} />
            ) : null}

            <article className="mockexam-question-detail-card">
              <div className="mockexam-history-tags">
                {question?.question_no ? <span>Q{question.question_no}</span> : null}
                {question?.type ? <span>{question.type}</span> : null}
                {question?.stat_type ? <span>{question.stat_type}</span> : null}
              </div>
              {question?.stem ? (
                <div className="mockexam-detail-html" dangerouslySetInnerHTML={{ __html: question.stem }} />
              ) : null}
              {question?.content ? (
                <div className="mockexam-detail-html" dangerouslySetInnerHTML={{ __html: question.content }} />
              ) : null}

              {options.length ? (
                <div className="mockexam-detail-option-list">
                  {options.map((item) => (
                    <div key={`${item.label}-${item.content}`} className="mockexam-detail-option">
                      <strong>{item.label}</strong>
                      <span dangerouslySetInnerHTML={{ __html: item.content || "" }} />
                    </div>
                  ))}
                </div>
              ) : null}

              <div className="answer-panel">
                <h4>标准答案</h4>
                <p>{renderAnswer(question)}</p>
              </div>
            </article>
          </section>
        </div>
      </div>
    </div>
  );
}
