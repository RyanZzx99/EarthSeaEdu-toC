import React from "react";
import TeacherPortalLayout from "../components/TeacherPortalLayout";

export default function TeacherPage() {
  return (
    <TeacherPortalLayout
      heroTitle="教师端工作台"
      heroSubtitle="统一进入模拟考试管理和学生档案查看。"
      showShortcutStrip
    />
  );
}
