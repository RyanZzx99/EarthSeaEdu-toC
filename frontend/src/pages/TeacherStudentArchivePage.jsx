import React, { useState } from "react";
import { motion } from "motion/react";
import { LoaderCircle, Search } from "lucide-react";
import { getTeacherStudentArchive } from "../api/teacher";
import ArchiveReadonlyView from "../components/ArchiveReadonlyView";
import TeacherPortalLayout from "../components/TeacherPortalLayout";

export default function TeacherStudentArchivePage() {
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [archiveResult, setArchiveResult] = useState(null);

  async function handleSearch() {
    const normalizedKeyword = keyword.trim();
    if (!normalizedKeyword) {
      setMessage({ type: "warn", text: "请输入学生ID或手机号。" });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);
      const response = await getTeacherStudentArchive({
        keyword: normalizedKeyword,
      });
      setArchiveResult(response.data || null);
      setMessage(null);
    } catch (error) {
      setArchiveResult(null);
      setMessage({
        type: "error",
        text: error?.response?.data?.detail || "学生档案加载失败，请稍后重试。",
      });
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(event) {
    if (event.key === "Enter") {
      event.preventDefault();
      void handleSearch();
    }
  }

  return (
    <TeacherPortalLayout
      heroTitle="教师端学生档案"
      heroSubtitle="可按学生ID或手机号快速查看正式档案、六维图和最新结构化结果。"
      showShortcutStrip={false}
    >
      <motion.section
        className="home-card teacher-archive-search-panel"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
      >
        <div className="teacher-archive-search-head">
          <div>
            <h2>查询学生档案</h2>
            <p>支持输入学生ID或手机号。该页面仅查看，不支持修改。</p>
          </div>
        </div>

        <div className="teacher-archive-search-row">
          <div className="teacher-archive-search-input">
            <Search size={18} strokeWidth={2.1} />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="请输入学生ID或手机号"
            />
          </div>

          <button
            type="button"
            className="primary-btn teacher-archive-search-button"
            onClick={handleSearch}
            disabled={loading}
          >
            {loading ? (
              <>
                <LoaderCircle size={16} strokeWidth={2.2} className="spin" />
                查询中
              </>
            ) : (
              "查询档案"
            )}
          </button>
        </div>

        {message ? <div className={`mockexam-message ${message.type}`}>{message.text}</div> : null}
      </motion.section>

      {archiveResult ? (
        <ArchiveReadonlyView data={archiveResult} />
      ) : (
        <section className="home-card teacher-archive-empty-panel">
          <h3>等待查询</h3>
          <p>输入学生ID或手机号后，这里会展示该学生的正式档案和最新六维图结果。</p>
        </section>
      )}
    </TeacherPortalLayout>
  );
}
