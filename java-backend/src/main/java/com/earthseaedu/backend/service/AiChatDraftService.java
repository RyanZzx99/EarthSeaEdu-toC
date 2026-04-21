package com.earthseaedu.backend.service;

import java.util.Map;

/**
 * AI 建档草稿服务，负责草稿读取、快照同步、补丁抽取和雷达图重算。
 */
public interface AiChatDraftService {

    /**
     * 读取指定 AI 建档会话的草稿详情，草稿不存在时返回正式档案快照作为初始内容。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getDraftDetail(String authorizationHeader, String sessionId);

    /**
     * 将学生正式档案快照同步到指定 AI 建档草稿。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> syncFromOfficialSnapshot(String authorizationHeader, String sessionId);

    /**
     * 基于当前会话上下文抽取最新档案补丁并返回合并后的草稿数据。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> extractLatestPatch(String authorizationHeader, String sessionId);

    /**
     * 使用草稿档案重新生成 AI 建档雷达图。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> regenerateDraftRadar(String authorizationHeader, String sessionId);

    /**
     * 使用正式档案重新生成 AI 建档雷达图。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> regenerateArchiveRadar(String authorizationHeader, String sessionId);
}
