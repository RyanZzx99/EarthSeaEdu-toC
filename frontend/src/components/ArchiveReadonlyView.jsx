import React, { useMemo } from "react";
import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
} from "recharts";

const RADAR_LABELS = {
  academic: "学术成绩",
  language: "语言能力",
  standardized: "标化考试",
  competition: "学术竞赛",
  activity: "活动领导力",
  project: "项目实践",
};

const RADAR_COLORS = {
  academic: { from: "#2c4a8a", to: "#4f7ad6", bg: "rgba(44,74,138,0.10)" },
  language: { from: "#0f9f7c", to: "#34d399", bg: "rgba(15,159,124,0.10)" },
  standardized: { from: "#c77b18", to: "#f59e0b", bg: "rgba(245,158,11,0.12)" },
  competition: { from: "#9c4ddb", to: "#c084fc", bg: "rgba(156,77,219,0.12)" },
  activity: { from: "#cc4e74", to: "#f472b6", bg: "rgba(244,114,182,0.12)" },
  project: { from: "#0891b2", to: "#22d3ee", bg: "rgba(34,211,238,0.12)" },
};

function hasMeaningfulValue(value) {
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === "string") {
    return value.trim() !== "";
  }
  if (Array.isArray(value)) {
    return value.some((item) => hasMeaningfulValue(item));
  }
  if (typeof value === "object") {
    return Object.values(value).some((item) => hasMeaningfulValue(item));
  }
  return true;
}

function formatDateTime(value) {
  if (!value) {
    return "未生成";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function formatScalarValue(fieldMeta, value) {
  if (fieldMeta?.input_type === "checkbox") {
    return value ? "是" : "否";
  }

  if (value === null || value === undefined || value === "") {
    return "未填写";
  }

  if (Array.isArray(fieldMeta?.options) && fieldMeta.options.length > 0) {
    const matched = fieldMeta.options.find((item) => String(item.value) === String(value));
    if (matched?.label) {
      return matched.label;
    }
  }

  if (typeof value === "string") {
    if (/^\d{4}-\d{2}-\d{2}T/.test(value)) {
      return formatDateTime(value);
    }
    return value;
  }

  if (typeof value === "object") {
    return JSON.stringify(value, null, 2);
  }

  return String(value);
}

function getRenderableFields(tableMeta, row) {
  if (Array.isArray(tableMeta?.fields) && tableMeta.fields.length > 0) {
    return tableMeta.fields.filter((field) => !field.hidden);
  }

  return Object.keys(row || {}).map((key) => ({
    name: key,
    label: key,
    input_type: "text",
  }));
}

function ReadonlyFieldGrid({ row, tableMeta }) {
  const fields = getRenderableFields(tableMeta, row);

  return (
    <div className="teacher-archive-field-grid">
      {fields.map((field) => (
        <div key={field.name} className="teacher-archive-field-card">
          <span className="teacher-archive-field-label">{field.label || field.name}</span>
          <div className="teacher-archive-field-value">
            {formatScalarValue(field, row?.[field.name])}
          </div>
        </div>
      ))}
    </div>
  );
}

export default function ArchiveReadonlyView({ data }) {
  const radarScores = data?.radar_scores_json || {};
  const chartData = useMemo(
    () =>
      Object.entries(RADAR_LABELS).map(([key, label]) => ({
        subject: label,
        score: Number(radarScores?.[key]?.score || 0),
      })),
    [radarScores]
  );
  const tableOrder = data?.form_meta?.table_order || Object.keys(data?.archive_form || {});

  return (
    <div className="teacher-archive-result-stack">
      <section className="home-card teacher-student-meta-card">
        <div className="teacher-student-meta-head">
          <div>
            <h2>学生档案</h2>
            <p>当前只读展示正式档案快照与最近一次六维图结果。</p>
          </div>
          <span className="profile-status-badge">
            {data?.result_status || "暂无六维图结果"}
          </span>
        </div>

        <div className="teacher-student-meta-grid">
          <div>
            <span>学生ID</span>
            <strong>{data?.student?.user_id || "未知"}</strong>
          </div>
          <div>
            <span>手机号</span>
            <strong>{data?.student?.mobile || "未绑定"}</strong>
          </div>
          <div>
            <span>昵称</span>
            <strong>{data?.student?.nickname || "未设置"}</strong>
          </div>
          <div>
            <span>结果更新时间</span>
            <strong>{formatDateTime(data?.update_time)}</strong>
          </div>
        </div>
      </section>

      <section className="home-card teacher-archive-radar-card">
        <div className="profile-radar-top">
          <div className="profile-radar-visual-card">
            <div className="profile-radar-visual-head">
              <div>
                <h3>最新六维图</h3>
              </div>
            </div>

            <div className="profile-radar-chart-wrap">
              <ResponsiveContainer width="100%" height="100%">
                <RadarChart data={chartData} outerRadius="68%">
                  <PolarGrid stroke="rgba(148,163,184,0.30)" />
                  <PolarAngleAxis dataKey="subject" tick={{ fill: "#1e3a8a", fontSize: 13 }} />
                  <PolarRadiusAxis
                    angle={30}
                    domain={[0, 100]}
                    tick={{ fill: "rgba(30,58,138,0.70)", fontSize: 11 }}
                    axisLine={false}
                  />
                  <Radar
                    dataKey="score"
                    stroke="#2c7be5"
                    fill="rgba(44,123,229,0.28)"
                    strokeWidth={2.5}
                    dot={{
                      r: 4,
                      fill: "#ffffff",
                      stroke: "#2c7be5",
                      strokeWidth: 2,
                    }}
                  />
                </RadarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="profile-summary-box">
            <h3>综合总结</h3>
            <p>{data?.summary_text || "该学生暂未生成完整六维图总结。"}</p>
          </div>
        </div>

        <div className="profile-radar-score-grid">
          {Object.entries(RADAR_LABELS).map(([key, label]) => {
            const value = radarScores?.[key] || { score: 0, reason: "暂无该维度说明" };
            return (
              <div
                key={key}
                className="profile-radar-score-card"
                style={{
                  "--score-from": (RADAR_COLORS[key] || RADAR_COLORS.academic).from,
                  "--score-to": (RADAR_COLORS[key] || RADAR_COLORS.academic).to,
                  "--score-bg": (RADAR_COLORS[key] || RADAR_COLORS.academic).bg,
                }}
              >
                <div className="profile-radar-score-head">
                  <span>{label}</span>
                  <strong>{Number(value.score || 0)}</strong>
                </div>
                <div className="profile-radar-score-bar">
                  <div
                    className="profile-radar-score-bar-fill"
                    style={{ width: `${Number(value.score || 0)}%` }}
                  />
                </div>
                <p>{value.reason || "暂无说明"}</p>
              </div>
            );
          })}
        </div>
      </section>

      {tableOrder.map((tableName) => {
        const tableMeta = data?.form_meta?.tables?.[tableName];
        const tableValue = data?.archive_form?.[tableName];
        const tableLabel = tableMeta?.label || tableName;
        const hasValue = hasMeaningfulValue(tableValue);

        return (
          <section key={tableName} className="home-card teacher-archive-section">
            <div className="teacher-archive-section-head">
              <h3>{tableLabel}</h3>
              <span>
                {Array.isArray(tableValue)
                  ? `${tableValue.length} 条记录`
                  : hasValue
                    ? "已填写"
                    : "未填写"}
              </span>
            </div>

            {Array.isArray(tableValue) ? (
              tableValue.length > 0 ? (
                <div className="teacher-archive-row-list">
                  {tableValue.map((row, index) => (
                    <div key={`${tableName}-${index}`} className="teacher-archive-row-card">
                      <div className="teacher-archive-row-index">记录 {index + 1}</div>
                      <ReadonlyFieldGrid row={row || {}} tableMeta={tableMeta} />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="teacher-archive-empty">暂无记录</div>
              )
            ) : hasValue ? (
              <ReadonlyFieldGrid row={tableValue || {}} tableMeta={tableMeta} />
            ) : (
              <div className="teacher-archive-empty">该部分暂未填写</div>
            )}
          </section>
        );
      })}
    </div>
  );
}
