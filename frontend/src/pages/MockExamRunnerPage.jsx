import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  getMockExamFavorites,
  getMockExamPaper,
  getMockExamPaperSet,
  getMockExamProgress,
  getMockExamSubmission,
  resolveMockExamWrongQuestions,
  saveMockExamPaperSetProgress,
  saveMockExamProgress,
  submitMockExamPaper,
  submitMockExamPaperSet,
  toggleMockExamFavorite,
} from "../api/mockexam";
import ActionDialog from "../components/ActionDialog";
import { LoadingOverlay, LoadingPage } from "../components/LoadingPage";
import { PracticeExercisePage } from "../components/PracticeExercisePage";
import {
  buildInitialAnswersMap,
  buildInitialMarkedMap,
  buildQuestionStore,
  normalizeExamData,
} from "../mockexam/practiceExerciseUtils";

function formatApiError(error, fallback) {
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

function resolveSourceKind(sourceType, row) {
  if (sourceType === "paper-set") {
    return "paper-set";
  }
  if (sourceType === "progress" || sourceType === "submission-review") {
    return row?.source_kind === "paper_set" ? "paper-set" : "paper";
  }
  return "paper";
}

function resolveInitialQuestionIndex(questions, { questionId, questionIndex, questionNo }) {
  if (typeof questionIndex === "number" && questionIndex >= 0 && questionIndex < questions.length) {
    return questionIndex;
  }

  if (questionId) {
    const matchedIndex = questions.findIndex((item) => String(item.id) === String(questionId));
    if (matchedIndex >= 0) {
      return matchedIndex;
    }
  }

  if (questionNo != null && questionNo !== "") {
    const matchedIndex = questions.findIndex(
      (item) => String(item.question_no || item.displayNo || "") === String(questionNo)
    );
    if (matchedIndex >= 0) {
      return matchedIndex;
    }
  }

  return questions.length ? 0 : -1;
}

function filterExamDataByGroup(examData, groupId) {
  const normalizedGroupId = String(groupId || "").trim();
  if (!normalizedGroupId) {
    return examData;
  }

  const filteredPassages = (examData?.passages || [])
    .map((passage) => {
      const filteredGroups = (passage.groups || []).filter(
        (group) =>
          String(group?.id || "") === normalizedGroupId ||
          (group?.questions || []).some(
            (question) =>
              String(question?.exam_group_id || "") === normalizedGroupId ||
              String(question?.group_id || "") === normalizedGroupId ||
              String(question?.groupId || "") === normalizedGroupId
          )
      );
      if (!filteredGroups.length) {
        return null;
      }

      const questionIds = new Set(
        filteredGroups.flatMap((group) => (group.questions || []).map((question) => String(question?.id || "")))
      );
      const filteredQuestions = (passage.questions || []).filter((question) =>
        questionIds.has(String(question?.id || ""))
      );

      return {
        ...passage,
        groups: filteredGroups,
        questions: filteredQuestions,
      };
    })
    .filter(Boolean);

  if (!filteredPassages.length) {
    return examData;
  }

  return {
    ...examData,
    passages: filteredPassages,
  };
}

export default function MockExamRunnerPage() {
  const { sourceType = "paper", sourceId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [loading, setLoading] = useState(true);
  const [busyState, setBusyState] = useState("");
  const [message, setMessage] = useState("");
  const [session, setSession] = useState(null);
  const [answers, setAnswers] = useState({});
  const [marked, setMarked] = useState({});
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [revealAnswers, setRevealAnswers] = useState(false);
  const [paused, setPaused] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [favoriteIds, setFavoriteIds] = useState([]);
  const [dialogState, setDialogState] = useState(null);
  const dialogResolverRef = useRef(null);

  const favoriteQuestionIds = useMemo(
    () => new Set((favoriteIds || []).map((value) => String(value))),
    [favoriteIds]
  );

  const requestedQuestionId = searchParams.get("questionId") || null;
  const requestedQuestionNo = searchParams.get("questionNo") || null;
  const requestedGroupId = searchParams.get("groupId") || null;
  const fromMistakes = searchParams.get("fromMistakes") === "1";
  const requestedQuestionIndexRaw = searchParams.get("questionIndex");
  const requestedQuestionIndex =
    requestedQuestionIndexRaw != null && requestedQuestionIndexRaw !== ""
      ? Number(requestedQuestionIndexRaw)
      : null;

  useEffect(() => {
    let active = true;

    async function bootstrapRunner() {
      try {
        setLoading(true);
        setMessage("");

        let row = null;
        let payload = null;
        let title = "";
        let moduleName = "";
        let examPaperId = null;
        let paperSetId = null;
        let progressId = null;
        let reviewMode = false;
        let submissionId = null;
        let initialAnswers = {};
        let initialMarked = {};
        let initialQuestionId = requestedQuestionId || null;
        let initialQuestionIndex = Number.isFinite(requestedQuestionIndex) ? requestedQuestionIndex : null;
        let initialQuestionNo = requestedQuestionNo || null;

        if (sourceType === "paper") {
          const response = await getMockExamPaper(sourceId);
          row = response.data || {};
          payload = row.payload;
          title = row.paper_name || row.paper_code || "";
          moduleName = row.module_name || row.exam_content || "IELTS";
          examPaperId = row.exam_paper_id;
        } else if (sourceType === "paper-set") {
          const response = await getMockExamPaperSet(sourceId);
          row = response.data || {};
          payload = row.payload;
          title = row.set_name || "";
          moduleName = row.exam_content || "IELTS";
          paperSetId = row.mockexam_paper_set_id;
        } else if (sourceType === "progress") {
          const response = await getMockExamProgress(sourceId);
          row = response.data || {};
          payload = row.payload;
          title = row.title || row.paper_code || "";
          moduleName = row.exam_content || "IELTS";
          examPaperId = row.exam_paper_id;
          paperSetId = row.paper_set_id || null;
          progressId = row.progress_id;
          initialAnswers = row.answers || {};
          initialMarked = row.marked || {};
          initialQuestionId = requestedQuestionId || row.current_question_id || null;
          initialQuestionIndex = Number.isFinite(requestedQuestionIndex)
            ? requestedQuestionIndex
            : typeof row.current_question_index === "number"
              ? row.current_question_index
              : null;
          initialQuestionNo = requestedQuestionNo || row.current_question_no || null;
        } else if (sourceType === "submission-review") {
          const response = await getMockExamSubmission(sourceId);
          row = response.data || {};
          payload = row.payload;
          title = row.title || row.paper_code || "";
          moduleName = row.exam_content || "IELTS";
          examPaperId = row.exam_paper_id;
          paperSetId = row.paper_set_id || null;
          reviewMode = true;
          submissionId = row.submission_id || Number(sourceId);
          initialAnswers = row.answers || {};
          initialMarked = row.marked || {};
          initialQuestionId = requestedQuestionId;
          initialQuestionIndex = Number.isFinite(requestedQuestionIndex) ? requestedQuestionIndex : null;
          initialQuestionNo = requestedQuestionNo;
        } else {
          throw new Error("不支持的考试来源");
        }

        const normalizedExamData = filterExamDataByGroup(normalizeExamData(payload), requestedGroupId);
        const store = buildQuestionStore(normalizedExamData);
        const initialIndex = resolveInitialQuestionIndex(store.questions, {
          questionId: initialQuestionId,
          questionIndex: initialQuestionIndex,
          questionNo: initialQuestionNo,
        });

        let favorites = [];
        try {
          const favoriteResponse =
            resolveSourceKind(sourceType, row) === "paper-set" || paperSetId
              ? await getMockExamFavorites({ limit: 200 })
              : await getMockExamFavorites({
                  exam_paper_id: examPaperId,
                  limit: 200,
                });
          favorites = (favoriteResponse.data?.items || [])
            .map((item) => item?.exam_question_id)
            .filter((value) => value != null);
        } catch (_error) {
          favorites = [];
        }

        if (!active) {
          return;
        }

        setSession({
          title,
          moduleName: moduleName || normalizedExamData.module || "IELTS",
          examData: normalizedExamData,
          questions: store.questions,
          passages: normalizedExamData.passages || [],
          sourceKind: resolveSourceKind(sourceType, row),
          examPaperId,
          paperSetId,
          progressId,
          reviewMode,
          submissionId,
        });
        setAnswers(buildInitialAnswersMap(store.questions, initialAnswers));
        setMarked(buildInitialMarkedMap(store.questions, initialMarked));
        setCurrentQuestionIndex(initialIndex >= 0 ? initialIndex : 0);
        setRevealAnswers(false);
        setPaused(false);
        setElapsedSeconds(Number(row?.elapsed_seconds || 0));
        setFavoriteIds(favorites);
      } catch (error) {
        if (!active) {
          return;
        }
        setMessage(formatApiError(error, "考试页面加载失败"));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void bootstrapRunner();

    return () => {
      active = false;
    };
  }, [
    requestedGroupId,
    requestedQuestionId,
    requestedQuestionIndex,
    requestedQuestionNo,
    sourceId,
    sourceType,
  ]);

  useEffect(() => {
    if (!session || session.reviewMode || paused) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setElapsedSeconds((previous) => previous + 1);
    }, 1000);
    return () => {
      window.clearInterval(timer);
    };
  }, [paused, session]);

  function openDialog(config) {
    return new Promise((resolve) => {
      dialogResolverRef.current = resolve;
      setDialogState(config);
    });
  }

  function closeDialog(result) {
    const resolver = dialogResolverRef.current;
    dialogResolverRef.current = null;
    setDialogState(null);
    if (resolver) {
      resolver(result);
    }
  }

  async function handleSaveAndExit() {
    if (!session || session.reviewMode) {
      if (session?.submissionId) {
        navigate(`/mockexam/results/${session.submissionId}`);
      } else {
        navigate("/mockexam");
      }
      return;
    }

    const currentQuestion = session.questions[currentQuestionIndex];
    if (!currentQuestion) {
      setMessage("当前没有可保存的题目");
      return;
    }

    try {
      setBusyState("saving");
      setMessage("");
      const payload = {
        progress_id: session.progressId,
        payload: session.examData,
        answers,
        marked,
        current_question_id: currentQuestion.id || null,
        current_question_index: currentQuestionIndex,
        current_question_no: String(currentQuestion.question_no || currentQuestion.displayNo || ""),
        elapsed_seconds: elapsedSeconds,
      };
      const response =
        session.sourceKind === "paper-set"
          ? await saveMockExamPaperSetProgress(session.paperSetId, payload)
          : await saveMockExamProgress(session.examPaperId, payload);
      const nextProgressId = response.data?.item?.progress_id || session.progressId;
      setSession((previous) => (previous ? { ...previous, progressId: nextProgressId } : previous));
      navigate("/mockexam");
    } catch (error) {
      setMessage(formatApiError(error, "保存进度失败"));
    } finally {
      setBusyState("");
    }
  }

  async function handleSubmit() {
    if (!session || session.reviewMode || !session.questions.length) {
      return;
    }

    const shouldSubmit = await openDialog({
      title: "确认提交当前作答？",
      message: "提交后将进入成绩结果页。",
      confirmText: "确认提交",
      cancelText: "继续作答",
      tone: "default",
    });
    if (!shouldSubmit) {
      return;
    }

    try {
      setBusyState("submitting");
      setMessage("");
      const payload = {
        answers,
        marked,
        progress_id: session.progressId,
        elapsed_seconds: elapsedSeconds,
      };
      const response =
        session.sourceKind === "paper-set"
          ? await submitMockExamPaperSet(session.paperSetId, payload)
          : await submitMockExamPaper(session.examPaperId, payload);
      const submissionId = response.data?.submission?.submission_id;
      if (!submissionId) {
        throw new Error("交卷成功但未返回 submission_id");
      }

      const wrongbookReview = response.data?.wrongbook_review;
      if (fromMistakes && wrongbookReview) {
        const allCorrect = Boolean(wrongbookReview.all_correct);
        const wrongCount = Number(wrongbookReview.wrong_count || 0);
        const activeWrongQuestionCount = Number(wrongbookReview.active_wrong_question_count || 0);
        const examQuestionIds = Array.isArray(wrongbookReview.exam_question_ids)
          ? wrongbookReview.exam_question_ids
          : [];

        if (allCorrect) {
          const shouldRemove = await openDialog({
            title: "本次重做已全部答对",
            message: "是否移出错题本？",
            confirmText: "移出错题本",
            cancelText: "暂不移出",
            tone: "success",
          });

          if (shouldRemove && examQuestionIds.length) {
            try {
              await resolveMockExamWrongQuestions({
                exam_question_ids: examQuestionIds,
              });
            } catch (_error) {
              await openDialog({
                title: "移出错题本失败",
                message: "本次交卷已成功，但移出错题本失败。请稍后在错题本页面重试。",
                confirmText: "我知道了",
                hideCancel: true,
                tone: "danger",
              });
            }
          }
        } else {
          await openDialog({
            title: `本次仍有 ${wrongCount} 道答错`,
            message: `已继续记录到错题本。当前该组仍有 ${activeWrongQuestionCount} 道错题记录。`,
            confirmText: "我知道了",
            hideCancel: true,
            tone: "danger",
          });
        }
      }

      navigate(`/mockexam/results/${submissionId}`);
    } catch (error) {
      setMessage(formatApiError(error, "交卷失败"));
    } finally {
      setBusyState("");
    }
  }

  async function handleToggleQuestionFavorite(question) {
    if (!question?.exam_question_id) {
      return;
    }
    const key = String(question.exam_question_id);
    const nextState = !favoriteQuestionIds.has(key);
    try {
        await toggleMockExamFavorite(question.exam_question_id, {
          is_favorite: nextState,
          source_kind: session?.sourceKind === "paper-set" ? "paper_set" : "paper",
          paper_set_id: session?.sourceKind === "paper-set" ? session?.paperSetId || null : null,
        });
      setFavoriteIds((previous) =>
        nextState
          ? [...previous.filter((item) => String(item) !== key), question.exam_question_id]
          : previous.filter((item) => String(item) !== key)
      );
    } catch (error) {
      setMessage(formatApiError(error, "题目收藏操作失败"));
    }
  }

  async function handleToggleQuestionFavoriteGroup(groupQuestions) {
    const actionableQuestions = (groupQuestions || []).filter((question) => question?.exam_question_id);
    if (!actionableQuestions.length) {
      return;
    }

    const keys = actionableQuestions.map((question) => String(question.exam_question_id));
    const nextState = !keys.every((key) => favoriteQuestionIds.has(key));

    try {
      await Promise.all(
          actionableQuestions.map((question) =>
            toggleMockExamFavorite(question.exam_question_id, {
              is_favorite: nextState,
              source_kind: session?.sourceKind === "paper-set" ? "paper_set" : "paper",
              paper_set_id: session?.sourceKind === "paper-set" ? session?.paperSetId || null : null,
            })
          )
        );

      setFavoriteIds((previous) => {
        const nextMap = new Map(previous.map((item) => [String(item), item]));
        if (nextState) {
          actionableQuestions.forEach((question) => {
            nextMap.set(String(question.exam_question_id), question.exam_question_id);
          });
        } else {
          keys.forEach((key) => {
            nextMap.delete(key);
          });
        }
        return [...nextMap.values()];
      });
    } catch (error) {
      setMessage(formatApiError(error, "题组收藏操作失败"));
    }
  }

  if (loading) {
    return <LoadingPage message="正在进入考试" submessage="请稍候，正在准备当前试卷内容" />;
  }

  if (!session) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">{message || "暂无可用试卷内容"}</div>
      </div>
    );
  }

  if (!session.questions.length) {
    return (
      <div className="mockexam-runner-state">
        <div className="mockexam-runner-card">
          {message || "当前试卷未解析出题目数据，请检查该试卷 payload。"}
        </div>
      </div>
    );
  }

  return (
    <>
      <PracticeExercisePage
        title={session.title}
        moduleName={session.moduleName}
        questions={session.questions}
        passages={session.passages}
        answers={answers}
        marked={marked}
        currentQuestionIndex={currentQuestionIndex}
        revealAnswers={revealAnswers}
        reviewMode={session.reviewMode}
        paused={paused}
        elapsedSeconds={elapsedSeconds}
        favoriteQuestionIds={favoriteQuestionIds}
        initialScrollQuestionId={requestedQuestionId || ""}
        initialScrollQuestionNo={requestedQuestionNo || ""}
        message={message}
        busy={Boolean(busyState)}
        busyText={busyState === "saving" ? "保存中..." : busyState === "submitting" ? "交卷中..." : ""}
        onBack={handleSaveAndExit}
        backLabel={session.reviewMode ? "返回成绩页" : "保存并退出"}
        onToggleRevealAnswers={() => setRevealAnswers((previous) => !previous)}
        onTogglePaused={() => setPaused((previous) => !previous)}
        onSelectQuestion={setCurrentQuestionIndex}
        onSetAnswer={(question, value) => {
          setCurrentQuestionIndex(question.globalIndex);
          setAnswers((previous) => ({ ...previous, [question.id]: value }));
        }}
        onToggleQuestionFavorite={handleToggleQuestionFavorite}
        onToggleQuestionFavoriteGroup={handleToggleQuestionFavoriteGroup}
        onSubmit={handleSubmit}
      />
      {busyState ? (
        <LoadingOverlay
          message={busyState === "saving" ? "正在保存进度" : "正在提交作答"}
          submessage={
            busyState === "saving" ? "请稍候，即将返回模拟考试页" : "请稍候，正在生成本次成绩结果"
          }
        />
      ) : null}
      {dialogState ? (
        <ActionDialog
          title={dialogState.title}
          message={dialogState.message}
          confirmText={dialogState.confirmText}
          cancelText={dialogState.cancelText}
          hideCancel={dialogState.hideCancel}
          tone={dialogState.tone}
          onConfirm={() => closeDialog(true)}
          onCancel={() => closeDialog(false)}
        />
      ) : null}
    </>
  );
}
