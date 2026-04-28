package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * 正式档案持久化服务，负责将档案表单快照写入业务表。
 */
public interface BusinessProfilePersistenceService {

    /**
     * 将归档表单快照按正式档案业务表结构持久化。
     *
     * @param archiveForm 归档表单快照
     * @param studentId 学生 ID
     */
    void persistArchiveFormSnapshot(Map<String, Object> archiveForm, String studentId);
}
