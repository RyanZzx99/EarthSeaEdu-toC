package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.aichat.AiChatResponses;

/**
 * AI 建档会话读取服务，负责会话、消息、结果和档案表单查询。
 */
public interface AiChatReadService {

    /**
     * 读取或按需创建当前学生在指定业务域下的 AI 建档会话。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param bizDomain 业务域
     * @param createIfMissing 不存在时是否创建
     * @return 处理后的响应对象。
     */
    AiChatResponses.CurrentSessionEnvelope getCurrentSession(
        String authorizationHeader,
        String bizDomain,
        Integer createIfMissing
    );

    /**
     * 读取指定 AI 建档会话的详情。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    AiChatResponses.SessionDetailResponse getSessionDetail(String authorizationHeader, String sessionId);

    /**
     * 分页读取指定 AI 建档会话的消息列表。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param limit 最大返回条数
     * @param beforeId 只读取该消息 ID 之前的历史消息
     * @return 处理后的响应对象。
     */
    AiChatResponses.MessageListResponse getMessages(
        String authorizationHeader,
        String sessionId,
        Integer limit,
        Long beforeId
    );

    /**
     * 读取指定 AI 建档会话的建档结果。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    AiChatResponses.ProfileResultResponse getResult(String authorizationHeader, String sessionId);

    /**
     * 读取指定 AI 建档会话的雷达图结果。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    AiChatResponses.RadarResponse getRadar(String authorizationHeader, String sessionId);

    /**
     * 读取指定 AI 建档会话关联的归档表单数据。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    AiChatResponses.ArchiveFormResponse getArchiveForm(String authorizationHeader, String sessionId);
}
