package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import java.util.Map;

/**
 * AI 建档写入服务，负责归档表单保存和会话结果更新。
 */
public interface AiChatWriteService {

    /**
     * 保存 AI 建档归档表单，并返回写入后的变更结果。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @param sessionId AI 建档会话 ID
     * @param archiveForm 归档表单快照
     * @return 处理后的响应对象。
     */
    AiChatResponses.ArchiveFormMutationResponse saveArchiveForm(
        String authorizationHeader,
        String sessionId,
        Map<String, Object> archiveForm
    );
}
