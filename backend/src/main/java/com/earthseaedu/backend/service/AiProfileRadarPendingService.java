package com.earthseaedu.backend.service;

import java.util.Collection;
import java.util.Map;

/**
 * AI 建档雷达图待更新变更服务。
 */
public interface AiProfileRadarPendingService {

    /**
     * 比较正式档案表单前后快照，并累积雷达图待更新字段。
     *
     * @param studentId 学生 ID
     * @param sessionId AI 建档会话 ID
     * @param bizDomain 业务域
     * @param previousProfileJson 变更前档案快照
     * @param currentProfileJson 变更后档案快照
     */
    void accumulateArchiveFormChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        Map<String, Object> previousProfileJson,
        Map<String, Object> currentProfileJson
    );

    /**
     * 按补丁变更字段累积雷达图待更新记录。
     *
     * @param studentId 学生 ID
     * @param sessionId AI 建档会话 ID
     * @param bizDomain 业务域
     * @param changedFields 本次发生变化的字段集合
     * @param changeSource 变更来源
     * @param changeRemark 变更备注
     * @return 处理后的响应对象。
     */
    Map<String, Object> accumulatePatchChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        Collection<String> changedFields,
        String changeSource,
        String changeRemark
    );

    /**
     * 在雷达图重算完成后重置待更新变更记录。
     *
     * @param studentId 学生 ID
     * @param sessionId AI 建档会话 ID
     * @param bizDomain 业务域
     * @param lastProfileResultId 最近一次雷达图结果 ID
     */
    void resetPendingRadarChanges(
        String studentId,
        String sessionId,
        String bizDomain,
        long lastProfileResultId
    );
}
