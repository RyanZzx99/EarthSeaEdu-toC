import React, { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  RotateCcw,
} from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import {
  discardMockExamProgress,
  getMockExamPaperSet,
  getMockExamProgresses,
  getMockExamSubmission,
  getMockExamSubmissions,
} from "../api/mockexam";
import { LoadingOverlay } from "../components/LoadingPage";
import MockExamModeHeader from "../components/MockExamModeHeader";
import {
  estimatePaperDuration,
  formatDateTime,
  getApiError,
} from "../mockexam/pageHelpers";
import "../mockexam/mockexam.css";

function findSetProgress(items, paperSetId) {
  return [...(items || [])]
    .filter(
      (item) =>
        item?.source_kind === "paper_set" &&
        Number(item?.paper_set_id) === Number(paperSetId)
    )
    .sort(
      (left, right) =>
        new Date(right?.last_active_time || 0).getTime() -
        new Date(left?.last_active_time || 0).getTime()
    )[0];
}

function findSetSubmission(items, paperSetId) {
  return [...(items || [])]
    .filter(
      (item) =>
        item?.source_kind === "paper_set" &&
        Number(item?.paper_set_id) === Number(paperSetId)
    )
    .sort(
      (left, right) =>
        new Date(right?.create_time || 0).getTime() -
        new Date(left?.create_time || 0).getTime()
    )[0];
}

function renderActionTitle(action) {
  if (action === "results") {
    return "练习结果";
  }
  if (action === "continue") {
    return "继续练习";
  }
  if (action === "retry") {
    return "再次练习";
  }
  if (action === "restart") {
    return "重新开始";
  }
  return "开始练习";
}

function getContentLabel(value) {
  const normalized = String(value || "").toLowerCase();
  if (normalized === "reading") {
    return "阅读";
  }
  if (normalized === "listening") {
    return "听力";
  }
  if (normalized === "mixed") {
    return "混合";
  }
  return value || "综合";
}

export default function MockExamTestDetailPage() {
  const navigate = useNavigate();
  const { id, action = "start" } = useParams();
  const paperSetId = Number(id || 0);

  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [paperSet, setPaperSet] = useState(null);
  const [activeProgress, setActiveProgress] = useState(null);
  const [latestSubmission, setLatestSubmission] = useState(null);
  const [submissionDetail, setSubmissionDetail] = useState(null);
  const [restarting, setRestarting] = useState(false);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        setLoading(true);
        const [paperSetResponse, progressResponse, submissionResponse] = await Promise.all([
          getMockExamPaperSet(paperSetId),
          getMockExamProgresses({ limit: 50 }),
          getMockExamSubmissions({ limit: 50 }),
        ]);

        if (!active) {
          return;
        }

        const nextPaperSet = paperSetResponse.data || null;
        const nextProgress = findSetProgress(progressResponse.data?.items || [], paperSetId) || null;
        const nextSubmission =
          findSetSubmission(submissionResponse.data?.items || [], paperSetId) || null;

        setPaperSet(nextPaperSet);
        setActiveProgress(nextProgress);
        setLatestSubmission(nextSubmission);
        setMessage("");

        if (action === "results" && nextSubmission?.submission_id) {
          const detailResponse = await getMockExamSubmission(nextSubmission.submission_id);
          if (!active) {
            return;
          }
          setSubmissionDetail(detailResponse.data || null);
        } else {
          setSubmissionDetail(null);
        }
      } catch (error) {
        if (!active) {
          return;
        }

        setPaperSet(null);
        setActiveProgress(null);
        setLatestSubmission(null);
        setSubmissionDetail(null);
        setMessage(getApiError(error, "试卷详情加载失败，请稍后重试。"));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void bootstrap();

    return () => {
      active = false;
    };
  }, [action, paperSetId]);

  const duration = useMemo(() => {
    if (!paperSet) {
      return 0;
    }
    return estimatePaperDuration(paperSet.exam_content, Math.max(paperSet.paper_count || 1, 1));
  }, [paperSet]);

  const breakdownItems = submissionDetail?.result?.type_breakdown || [];

  async function handleRestart() {
    if (!activeProgress?.progress_id) {
      navigate(`/mockexam/run/paper-set/${paperSetId}`);
      return;
    }

    try {
      setRestarting(true);
      await discardMockExamProgress(activeProgress.progress_id);
      navigate(`/mockexam/run/paper-set/${paperSetId}`);
    } catch (error) {
      setMessage(getApiError(error, "重新开始失败，请稍后重试。"));
    } finally {
      setRestarting(false);
    }
  }

  function handlePrimaryAction() {
    if (action === "continue" && activeProgress?.progress_id) {
      navigate(`/mockexam/run/progress/${activeProgress.progress_id}`);
      return;
    }

    navigate(`/mockexam/run/paper-set/${paperSetId}`);
  }

  if (loading) {
    return (
      <div className="home-shell mockexam-mode-page">
        <MockExamModeHeader activeMode="practice" />
        <main className="mockexam-test-detail-main">
          <div className="mockexam-test-detail-container" />
        </main>
      </div>
    );
  }

  if (!paperSet) {
    return (
      <div className="home-shell mockexam-mode-page">
        <MockExamModeHeader activeMode="practice" />
        <main className="mockexam-test-detail-main">
          <div className="mockexam-test-detail-container">
            <div className="mockexam-page-note">{message || "未找到这套组合试卷。"}</div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="home-shell mockexam-mode-page">
      {restarting ? (
        <LoadingOverlay
          message="正在重新开始练习"
          submessage="请稍候，系统正在清理当前进度并准备新的练习记录"
        />
      ) : null}

      <MockExamModeHeader activeMode="practice" />

      <main className="mockexam-test-detail-main">
        <div className="mockexam-test-detail-container">
          <motion.button
            type="button"
            className="mockexam-test-detail-back"
            onClick={() => navigate("/mockexam/practice/test")}
            initial={{ opacity: 0, x: -18 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.28 }}
          >
            <ArrowLeft size={18} strokeWidth={2.1} />
            <span>返回试卷列表</span>
          </motion.button>

          <motion.section
            className="mockexam-test-detail-card"
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.36 }}
          >
            <div className="mockexam-test-detail-head">
              <div>
                <h1>{paperSet.set_name}</h1>
                <p className="mockexam-test-detail-subtitle">{renderActionTitle(action)}</p>
              </div>

              <div className="mockexam-test-detail-meta">
                <span>{paperSet.paper_count || 0} 张试卷</span>
                <span>|</span>
                <span>预计 {duration} 分钟</span>
                <span>|</span>
                <span>{getContentLabel(paperSet.exam_content || "Mixed")}</span>
              </div>
            </div>

            {message ? <div className="mockexam-page-note">{message}</div> : null}

            {action === "results" ? (
              <div className="mockexam-test-detail-stack">
                <section className="mockexam-test-detail-highlight success">
                  <div className="mockexam-test-detail-highlight-head">
                    <CheckCircle2 size={30} />
                    <div>
                      <h2>练习已完成</h2>
                      <p>
                        最近正确率 {latestSubmission?.score_percent ?? "--"}%
                        {latestSubmission?.create_time
                          ? ` · ${formatDateTime(latestSubmission.create_time)}`
                          : ""}
                      </p>
                    </div>
                  </div>

                  <div className="mockexam-test-detail-metrics">
                    <div>
                      <strong>{latestSubmission?.correct_count || 0}</strong>
                      <span>答对题数</span>
                    </div>
                    <div>
                      <strong>{latestSubmission?.wrong_count || 0}</strong>
                      <span>答错题数</span>
                    </div>
                    <div>
                      <strong>{latestSubmission?.total_questions || 0}</strong>
                      <span>总题数</span>
                    </div>
                  </div>
                </section>

                {breakdownItems.length ? (
                  <section className="mockexam-test-detail-breakdown">
                    <h3>题型统计</h3>
                    <div className="mockexam-test-detail-breakdown-grid">
                      {breakdownItems.map((item) => (
                        <article key={item.question_type}>
                          <strong>{item.question_type}</strong>
                          <span>共 {item.total_questions} 题</span>
                          <span>错 {item.wrong_count} 题</span>
                        </article>
                      ))}
                    </div>
                  </section>
                ) : null}

                <div className="mockexam-test-detail-actions">
                  {latestSubmission?.submission_id ? (
                    <button
                      type="button"
                      className="mockexam-test-list-button primary"
                      onClick={() => navigate(`/mockexam/results/${latestSubmission.submission_id}`)}
                    >
                      查看完整结果
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="mockexam-test-list-button secondary"
                    onClick={() => navigate(`/mockexam/practice/test/${paperSetId}/retry`)}
                  >
                    再次练习
                  </button>
                </div>
              </div>
            ) : null}

            {(action === "start" || action === "continue" || action === "retry") && (
              <div className="mockexam-test-detail-stack">
                <section className="mockexam-test-detail-highlight info">
                  <div className="mockexam-test-detail-highlight-head">
                    <Clock3 size={30} />
                    <div>
                      <h2>准备开始练习</h2>
                      <p>
                        预计用时 {duration} 分钟 · 共 {paperSet.paper_count || 0} 张试卷
                      </p>
                    </div>
                  </div>

                  <p className="mockexam-test-detail-description">
                    {action === "start" && "点击下方按钮，按整套组合试卷开始练习。"}
                    {action === "continue" &&
                      (activeProgress
                        ? `将从上次保存的位置继续，当前已完成 ${
                            activeProgress.answered_count || 0
                          }/${activeProgress.total_questions || 0}。`
                        : "当前没有可继续的未完成记录，点击后会从头开始。")}
                    {action === "retry" && "将重新开始这套组合试卷，生成一条新的练习记录。"}
                  </p>

                  {activeProgress?.last_active_time ? (
                    <p className="mockexam-test-detail-caption">
                      上次保存于 {formatDateTime(activeProgress.last_active_time)}
                    </p>
                  ) : null}
                </section>

                <div className="mockexam-test-detail-actions">
                  <button
                    type="button"
                    className="mockexam-test-list-button primary"
                    onClick={handlePrimaryAction}
                  >
                    {action === "continue" ? "继续作答" : "开始做题"}
                  </button>
                </div>
              </div>
            )}

            {action === "restart" ? (
              <div className="mockexam-test-detail-stack">
                <section className="mockexam-test-detail-highlight warning">
                  <div className="mockexam-test-detail-highlight-head">
                    <AlertCircle size={30} />
                    <div>
                      <h2>确认重新开始</h2>
                      <p>当前保存的进度将被清空</p>
                    </div>
                  </div>

                  <p className="mockexam-test-detail-description">
                    {activeProgress
                      ? `你当前已完成 ${activeProgress.answered_count || 0}/${
                          activeProgress.total_questions || 0
                        }，重新开始会作废这份未完成记录。`
                      : "当前没有未完成进度，确认后会直接开始新的试卷练习。"}
                  </p>

                  {activeProgress?.last_active_time ? (
                    <p className="mockexam-test-detail-caption">
                      最近保存于 {formatDateTime(activeProgress.last_active_time)}
                    </p>
                  ) : null}
                </section>

                <div className="mockexam-test-detail-actions">
                  <button
                    type="button"
                    className="mockexam-test-list-button secondary"
                    onClick={() => navigate("/mockexam/practice/test")}
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    className="mockexam-test-list-button primary"
                    disabled={restarting}
                    onClick={() => void handleRestart()}
                  >
                    <RotateCcw size={16} />
                    确认重新开始
                  </button>
                </div>
              </div>
            ) : null}
          </motion.section>
        </div>
      </main>
    </div>
  );
}
