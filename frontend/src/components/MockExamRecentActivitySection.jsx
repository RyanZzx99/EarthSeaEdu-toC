import React, { useEffect, useState } from "react";
import { motion } from "motion/react";
import {
  AlertCircle,
  BarChart3,
  ChevronRight,
  Play,
  Star,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  getMockExamEntityFavorites,
  getMockExamProgresses,
  getMockExamSubmissions,
  getMockExamWrongQuestions,
} from "../api/mockexam";
import { InlineLoading } from "./LoadingPage";
import { getApiError } from "../mockexam/pageHelpers";

function buildContinueCopy(progress) {
  if (!progress) {
    return ["暂无未完成练习", "从专项练习或试卷练习开始"];
  }

  return [
    progress.title || "最近一次练习",
    `已完成 ${progress.answered_count || 0}/${progress.total_questions || 0}`,
  ];
}

function buildWrongCopy(group) {
  if (!group) {
    return ["暂无错题记录", "开始练习后会在这里显示"];
  }

  const title = group.paper_title || group.group_title || "最近错题";
  const wrongCount = Number(group.wrong_question_count || 0);
  return [title, `共错 ${wrongCount} 题`];
}

function buildHistoryCopy(submission) {
  if (!submission) {
    return ["暂无练习记录", "完成练习后可查看结果"];
  }

  return [
    submission.title || "最近一次练习",
    typeof submission.score_percent === "number"
      ? `正确率 ${submission.score_percent}%`
      : "已生成练习记录",
  ];
}

function buildFavoriteCopy(entityFavorite) {
  if (!entityFavorite) {
    return ["暂无收藏记录", "收藏题包或试卷后会在这里显示"];
  }

  return [
    entityFavorite.title || "最近一次收藏",
    entityFavorite.target_type === "paper_set" ? "最近收藏试卷" : "最近收藏题包",
  ];
}

export default function MockExamRecentActivitySection() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState({
    loading: true,
    message: "",
    latestProgress: null,
    latestWrongGroup: null,
    latestSubmission: null,
    latestEntityFavorite: null,
  });

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        const [
          progressResponse,
          wrongResponse,
          submissionResponse,
          entityFavoriteResponse,
        ] = await Promise.all([
          getMockExamProgresses({ limit: 1 }),
          getMockExamWrongQuestions({ limit: 200 }),
          getMockExamSubmissions({ limit: 1 }),
          getMockExamEntityFavorites({ limit: 1 }),
        ]);

        if (!active) {
          return;
        }

        setSummary({
          loading: false,
          message: "",
          latestProgress: progressResponse.data?.items?.[0] || null,
          latestWrongGroup: wrongResponse.data?.groups?.[0] || null,
          latestSubmission: submissionResponse.data?.items?.[0] || null,
          latestEntityFavorite: entityFavoriteResponse.data?.items?.[0] || null,
        });
      } catch (error) {
        if (!active) {
          return;
        }

        setSummary({
          loading: false,
          message: getApiError(error, "最近活动加载失败，请稍后重试。"),
          latestProgress: null,
          latestWrongGroup: null,
          latestSubmission: null,
          latestEntityFavorite: null,
        });
      }
    }

    void bootstrap();

    return () => {
      active = false;
    };
  }, []);

  const recentCards = [
    {
      key: "continue",
      title: "继续上次练习",
      lines: buildContinueCopy(summary.latestProgress),
      icon: Play,
      tone: "indigo",
      onClick: () => {
        if (summary.latestProgress?.progress_id) {
          navigate(`/mockexam/run/progress/${summary.latestProgress.progress_id}`);
          return;
        }
        navigate("/mockexam/practice/topic");
      },
    },
    {
      key: "wrong",
      title: "我的错题",
      lines: buildWrongCopy(summary.latestWrongGroup),
      icon: AlertCircle,
      tone: "rose",
      onClick: () => navigate("/mockexam/mistakes"),
    },
    {
      key: "history",
      title: "练习记录",
      lines: buildHistoryCopy(summary.latestSubmission),
      icon: BarChart3,
      tone: "emerald",
      onClick: () => navigate("/mockexam/history"),
    },
    {
      key: "favorites",
      title: "收藏夹",
      lines: buildFavoriteCopy(summary.latestEntityFavorite),
      icon: Star,
      tone: "amber",
      onClick: () => navigate("/mockexam/favorites"),
    },
  ];

  return (
    <motion.section
      className="mockexam-practice-recent-panel"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.4 }}
    >
      <h3>最近活动</h3>

      <div className="mockexam-practice-recent-grid">
        {recentCards.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.key}
              type="button"
              className={`mockexam-practice-recent-card tone-${item.tone}`}
              onClick={item.onClick}
            >
              <div className="mockexam-practice-recent-head">
                <div className="mockexam-practice-recent-icon">
                  <Icon size={18} />
                </div>
                <ChevronRight size={16} className="mockexam-practice-recent-arrow" />
              </div>

              <h4>{item.title}</h4>

              {summary.loading ? (
                <InlineLoading message="正在准备最近活动" size="sm" />
              ) : (
                item.lines.map((line, index) => (
                  <p
                    key={`${item.key}-${index}`}
                    className={
                      index === item.lines.length - 1
                        ? "mockexam-practice-recent-meta"
                        : undefined
                    }
                  >
                    {line}
                  </p>
                ))
              )}
            </button>
          );
        })}
      </div>

      {summary.message ? <p className="mockexam-inline-message">{summary.message}</p> : null}
    </motion.section>
  );
}
