package com.earthseaedu.backend.service;

import java.util.Collection;
import java.util.Map;

/**
 * AI 提示词运行服务，负责装配提示词、调用模型并解析结构化输出。
 */
public interface AiPromptRuntimeService {

    /**
     * 按提示词配置执行 AI 请求，并返回模型内容和运行元数据。
     *
     * @param promptKey 提示词配置键
     * @param context 实时连接上下文
     * @return 处理后的响应对象。
     */
    PromptRuntimeResult executePrompt(String promptKey, Map<String, Object> context);

    /**
     * 按提示词配置执行 AI 请求，并返回模型内容和运行元数据。
     *
     * @param promptKey 提示词配置键
     * @param context 实时连接上下文
     * @param allowedStatuses 允许使用的配置状态集合
     * @return 处理后的响应对象。
     */
    PromptRuntimeResult executePrompt(
        String promptKey,
        Map<String, Object> context,
        Collection<String> allowedStatuses
    );

    /**
     * 将模型输出解析为 JSON 对象，解析失败时按业务场景抛出异常。
     *
     * @param rawText 待解析的模型输出文本
     * @param sceneName 解析失败时使用的业务场景名称
     * @return 处理后的响应对象。
     */
    Map<String, Object> parseJsonObject(String rawText, String sceneName);

    /**
     * AI 提示词运行结果，封装提示词、模型和原始响应信息。
     *
     * @param promptKey 提示词配置键
     * @param modelName 模型名称
     * @param content 消息内容
     * @param rawResponseJson 模型原始响应 JSON
     */
    record PromptRuntimeResult(
        String promptKey,
        String modelName,
        String content,
        String rawResponseJson
    ) {
    }
}
