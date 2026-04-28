package com.earthseaedu.backend.service;

import com.earthseaedu.backend.model.ai.AiPromptConfig;
import com.earthseaedu.backend.support.PageResult;
import java.util.List;
import java.util.Map;

/**
 * AI 配置管理服务，负责提示词配置和运行时配置的查询与维护。
 */
public interface AiConfigAdminService {

    /**
     * 按筛选条件分页查询 AI 提示词配置。
     *
     * @param bizDomain 业务域
     * @param promptStage 提示词阶段
     * @param status 状态筛选或目标状态
     * @param keyword 检索关键字
     * @param limit 最大返回条数
     * @return 分页结果。
     */
    PageResult<AiPromptConfig> listAiPromptConfigs(
        String bizDomain,
        String promptStage,
        String status,
        String keyword,
        int limit
    );

    /**
     * 更新指定 AI 提示词配置及模型调用参数。
     *
     * @param promptId 提示词配置 ID
     * @param promptName 提示词名称
     * @param promptContent 提示词内容
     * @param promptVersion 提示词版本
     * @param status 状态筛选或目标状态
     * @param outputFormat 输出格式
     * @param modelName 模型名称
     * @param temperature 采样温度
     * @param topP Top-p 采样参数
     * @param maxTokens 最大输出 token 数
     * @param variablesJson 提示词变量定义
     * @param remark 备注
     * @return 处理后的响应对象。
     */
    AiPromptConfig updateAiPromptConfig(
        long promptId,
        String promptName,
        String promptContent,
        String promptVersion,
        String status,
        String outputFormat,
        String modelName,
        Double temperature,
        Double topP,
        Integer maxTokens,
        Object variablesJson,
        String remark
    );

    /**
     * 将提示词配置转换为列表接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toAiPromptListPayload(AiPromptConfig row);

    /**
     * 将提示词配置转换为更新接口响应结构。
     *
     * @param row 待转换的数据行
     * @return 处理后的响应对象。
     */
    Map<String, Object> toAiPromptUpdatePayload(AiPromptConfig row);

    /**
     * 查询 AI 运行时配置列表。
     *
     * @return 处理后的响应对象。
     */
    List<Map<String, Object>> listAiRuntimeConfigs();

    /**
     * 更新指定 AI 运行时配置，必要时清除覆盖值。
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @param status 状态筛选或目标状态
     * @param remark 备注
     * @param clearOverride 是否清除覆盖值
     * @return 处理后的响应对象。
     */
    Map<String, Object> updateAiRuntimeConfig(
        String configKey,
        String configValue,
        String status,
        String remark,
        boolean clearOverride
    );
}
