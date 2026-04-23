package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.teacher.TeacherResponses;
import java.util.List;

/**
 * 教师模考试卷集服务，负责教师侧试卷集查询、创建和状态维护。
 */
public interface TeacherMockExamPaperSetService {

    /**
     * 查询教师创建的模考试卷集列表。
     *
     * @param teacherUserId 教师用户 ID
     * @return 处理后的响应对象。
     */
    TeacherResponses.MockExamPaperSetListResponse listTeacherMockExamPaperSets(String teacherUserId);

    /**
     * 为教师创建模考试卷集。
     *
     * @param teacherUserId 教师用户 ID
     * @param setName 试卷集名称
     * @param examPaperIds 试卷 ID 列表
     * @param remark 备注
     * @return 处理后的响应对象。
     */
    TeacherResponses.MockExamPaperSetItem createTeacherMockExamPaperSet(
        String teacherUserId,
        String setName,
        List<Long> examPaperIds,
        String remark
    );

    /**
     * 更新教师模考试卷集状态。
     *
     * @param teacherUserId 教师用户 ID
     * @param mockexamPaperSetId 教师试卷集 ID
     * @param status 状态筛选或目标状态
     * @return 处理后的响应对象。
     */
    TeacherResponses.MockExamPaperSetItem updateTeacherMockExamPaperSetStatus(
        String teacherUserId,
        long mockexamPaperSetId,
        int status
    );
}
