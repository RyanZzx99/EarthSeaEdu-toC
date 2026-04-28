package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * 首页标准问卷建档结果落正式档案表的持久化服务。
 */
public interface StudentProfileGuidedArchivePersistenceService {

    /**
     * 将当前问卷快照在同一事务中同步到正式档案表。
     *
     * @param archiveForm 标准问卷当前生成的档案快照
     * @param studentId 学生ID
     */
    void syncGuidedArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId);

    /**
     * 将标准问卷生成的档案快照落库到正式档案表。
     *
     * @param archiveForm 标准问卷生成的档案快照
     * @param studentId 学生ID
     */
    void persistGuidedArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId);
}
