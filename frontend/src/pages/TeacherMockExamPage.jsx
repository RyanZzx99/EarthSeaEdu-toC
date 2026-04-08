import React from "react";
import MockExamWorkspace from "../components/MockExamWorkspace";
import TeacherPortalLayout from "../components/TeacherPortalLayout";
import "../mockexam/mockexam.css";

export default function TeacherMockExamPage() {
  return (
    <TeacherPortalLayout
      heroTitle="教师端模拟考试"
      heroSubtitle="在教师端统一管理组合试卷，同时保留题库和试卷两种开考方式。"
      showShortcutStrip={false}
    >
      <MockExamWorkspace showExamSetManagement showQuickPractice />
    </TeacherPortalLayout>
  );
}
