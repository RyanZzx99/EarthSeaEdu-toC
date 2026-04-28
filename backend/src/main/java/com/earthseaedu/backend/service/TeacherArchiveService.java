package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.teacher.TeacherResponses;

/**
 * 教师档案查询服务，负责按教师权限读取学生档案包。
 */
public interface TeacherArchiveService {

    /**
     * 按教师用户权限和检索关键字读取学生档案包。
     *
     * @param teacherUserId 教师用户 ID
     * @param keyword 检索关键字
     * @return 处理后的响应对象。
     */
    TeacherResponses.TeacherStudentArchiveLookupResponse loadTeacherStudentArchiveBundle(
        String teacherUserId,
        String keyword
    );
}
