package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * 正式档案表单读取服务。
 */
public interface BusinessProfileFormService {

    /**
     * 读取学生正式档案快照和表单元数据。
     *
     * @param studentId 学生 ID
     * @return 包含 archive_form 和 form_meta 的表单数据包
     */
    Map<String, Object> loadBusinessProfileFormBundle(String studentId);

    /**
     * 读取学生正式档案业务表快照。
     *
     * @param studentId 学生 ID
     * @return 按业务表名组织的档案快照
     */
    Map<String, Object> loadBusinessProfileSnapshot(String studentId);

    /**
     * 构建正式档案表单元数据。
     *
     * @return 表单表顺序、字段定义和选项信息
     */
    Map<String, Object> buildBusinessProfileFormMeta();
}
