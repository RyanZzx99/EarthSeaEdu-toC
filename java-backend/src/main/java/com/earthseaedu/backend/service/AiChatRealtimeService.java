package com.earthseaedu.backend.service;

import java.util.List;
import java.util.Map;

/**
 * AI 建档实时会话服务，负责 WebSocket 连接、消息保存和流式回复编排。
 */
public interface AiChatRealtimeService {

    /**
     * 建立 AI 建档实时连接上下文，并返回会话当前处理状态。
     *
     * @param accessToken 访问令牌
     * @param requestedSessionId 客户端请求的会话 ID
     * @param requestedStudentId 客户端请求的学生 ID
     * @param bizDomain 业务域
     * @return 处理后的响应对象。
     */
    ConnectionInitResult connect(
        String accessToken,
        String requestedSessionId,
        String requestedStudentId,
        String bizDomain
    );

    /**
     * 保存实时会话中的用户消息并推进消息序号。
     *
     * @param context 实时连接上下文
     * @param content 消息内容
     * @return 处理后的响应对象。
     */
    SavedMessage saveUserMessage(ConnectionContext context, String content);

    /**
     * 根据实时会话上下文和最新用户输入生成 AI 助手回复。
     *
     * @param context 实时连接上下文
     * @param latestUserText 最新用户输入
     * @return 生成或解析出的字符串结果。
     */
    String generateAssistant(ConnectionContext context, String latestUserText);

    /**
     * 保存实时会话中的助手回复及流式分片统计。
     *
     * @param context 实时连接上下文
     * @param content 消息内容
     * @param streamChunkCount 流式输出分片数量
     * @return 处理后的响应对象。
     */
    SavedMessage saveAssistantMessage(ConnectionContext context, String content, int streamChunkCount);

    /**
     * 基于当前会话上下文抽取最新档案补丁并返回合并后的草稿数据。
     *
     * @param context 实时连接上下文
     * @return 处理后的响应对象。
     */
    Map<String, Object> extractLatestPatch(ConnectionContext context);

    /**
     * 更新实时会话的处理阶段和会话阶段。
     *
     * @param context 实时连接上下文
     * @param stage 目标处理阶段
     * @param conversationPhase 目标会话阶段
     * @return 处理后的响应对象。
     */
    Map<String, Object> setStage(
        ConnectionContext context,
        String stage,
        String conversationPhase
    );

    /**
     * 读取实时会话推送所需的当前会话载荷。
     *
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> currentSessionPayload(String sessionId);

    /**
     * AI 建档实时连接上下文，封装鉴权、会话、学生和业务域信息。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param studentId 学生 ID
     * @param bizDomain 业务域
     */
    record ConnectionContext(
        String authorizationHeader,
        String sessionId,
        String studentId,
        String bizDomain
    ) {
    }

    /**
     * AI 建档实时连接初始化结果，封装上下文和会话阶段信息。
     *
     * @param context 实时连接上下文
     * @param currentStage 当前处理阶段
     * @param currentRound 当前会话轮次
     * @param missingDimensions 仍需补充的档案维度
     */
    record ConnectionInitResult(
        ConnectionContext context,
        String currentStage,
        int currentRound,
        List<Object> missingDimensions
    ) {
    }

    /**
     * AI 建档实时消息保存结果，封装消息 ID、序号和轮次。
     *
     * @param messageId 消息 ID
     * @param sequenceNo 消息序号
     * @param currentRound 当前会话轮次
     */
    record SavedMessage(
        long messageId,
        int sequenceNo,
        int currentRound
    ) {
    }
}
