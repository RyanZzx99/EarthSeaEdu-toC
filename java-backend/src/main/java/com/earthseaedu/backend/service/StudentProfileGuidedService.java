package com.earthseaedu.backend.service;

import java.util.List;
import java.util.Map;

/**
 * AI 建档引导问卷服务。
 */
public interface StudentProfileGuidedService {

    /**
     * 读取 AI 建档引导问卷的问题列表。
     *
     * @return 处理后的响应对象。
     */
    List<Map<String, Object>> listQuestions();

    /**
     * 读取或按需创建当前用户的 AI 建档引导会话包。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param createIfMissing 不存在时是否创建
     * @return 处理后的响应对象。
     */
    Map<String, Object> getOrCreateCurrentBundle(String authorizationHeader, boolean createIfMissing);

    /**
     * 重启当前用户的 AI 建档引导会话。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @return 处理后的响应对象。
     */
    Map<String, Object> restartCurrentSession(String authorizationHeader);

    /**
     * 读取指定 AI 建档引导会话包。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @return 处理后的响应对象。
     */
    Map<String, Object> getSessionBundle(String authorizationHeader, String sessionId);

    /**
     * 提交指定引导问题答案并返回更新后的会话状态。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param questionCode 问题编码
     * @param rawAnswer 原始答案内容
     * @return 处理后的响应对象。
     */
    Map<String, Object> submitGuidedAnswer(
        String authorizationHeader,
        String sessionId,
        String questionCode,
        Map<String, Object> rawAnswer
    );

    /**
     * 退出指定 AI 建档引导会话并记录触发原因。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param triggerReason 触发原因
     * @return 处理后的响应对象。
     */
    Map<String, Object> exitGuidedSession(String authorizationHeader, String sessionId, String triggerReason);

    /**
     * 根据引导问卷会话生成建档结果。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param triggerReason 触发原因
     * @return 处理后的响应对象。
     */
    Map<String, Object> generateGuidedResult(String authorizationHeader, String sessionId, String triggerReason);
}
