package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.aichat.AiChatRequests;
import com.earthseaedu.backend.dto.aichat.AiChatResponses;
import com.earthseaedu.backend.service.AiChatDraftService;
import com.earthseaedu.backend.service.AiChatReadService;
import com.earthseaedu.backend.service.AiChatWriteService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 建档对话接口，负责会话、消息、档案表单、草稿和雷达结果的请求转发。 */
@RestController
@RequestMapping("/api/v1/ai-chat")
public class AiChatController {

    private final AiChatReadService aiChatReadService;
    private final AiChatWriteService aiChatWriteService;
    private final AiChatDraftService aiChatDraftService;

    public AiChatController(
        AiChatReadService aiChatReadService,
        AiChatWriteService aiChatWriteService,
        AiChatDraftService aiChatDraftService
    ) {
        this.aiChatReadService = aiChatReadService;
        this.aiChatWriteService = aiChatWriteService;
        this.aiChatDraftService = aiChatDraftService;
    }

    /** 获取当前学生的 AI 建档会话，可按需自动创建新会话。 */
    @GetMapping("/sessions/current")
    public AiChatResponses.CurrentSessionEnvelope getCurrentSession(
        @RequestParam("biz_domain") String bizDomain,
        @RequestParam(value = "create_if_missing", required = false, defaultValue = "0") Integer createIfMissing,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getCurrentSession(authorizationHeader, bizDomain, createIfMissing);
    }

    /** 获取指定 AI 建档会话详情。 */
    @GetMapping("/sessions/{sessionId}")
    public AiChatResponses.SessionDetailResponse getSessionDetail(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getSessionDetail(authorizationHeader, sessionId);
    }

    /** 分页获取指定 AI 建档会话的可见消息列表。 */
    @GetMapping("/sessions/{sessionId}/messages")
    public AiChatResponses.MessageListResponse getMessages(
        @PathVariable("sessionId") String sessionId,
        @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
        @RequestParam(value = "before_id", required = false) Long beforeId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getMessages(authorizationHeader, sessionId, limit, beforeId);
    }

    /** 获取指定 AI 建档会话的最终档案结果。 */
    @GetMapping("/sessions/{sessionId}/result")
    public AiChatResponses.ProfileResultResponse getResult(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getResult(authorizationHeader, sessionId);
    }

    /** 获取指定 AI 建档会话的雷达评分结果。 */
    @GetMapping("/sessions/{sessionId}/radar")
    public AiChatResponses.RadarResponse getRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getRadar(authorizationHeader, sessionId);
    }

    /** 获取指定 AI 建档会话对应的官方档案表单快照。 */
    @GetMapping("/sessions/{sessionId}/archive-form")
    public AiChatResponses.ArchiveFormResponse getArchiveForm(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getArchiveForm(authorizationHeader, sessionId);
    }

    /** 保存官方档案表单，并记录后续雷达重算需要的变更范围。 */
    @PostMapping("/sessions/{sessionId}/archive-form")
    public AiChatResponses.ArchiveFormMutationResponse saveArchiveForm(
        @PathVariable("sessionId") String sessionId,
        @Valid @RequestBody AiChatRequests.ArchiveFormSaveRequest payload,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatWriteService.saveArchiveForm(authorizationHeader, sessionId, payload.archiveForm());
    }

    /** 基于官方档案表单重新生成雷达评分。 */
    @PostMapping("/sessions/{sessionId}/archive-form/regenerate-radar")
    public Map<String, Object> regenerateArchiveRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.regenerateArchiveRadar(authorizationHeader, sessionId);
    }

    /** 获取指定 AI 建档会话的草稿档案。 */
    @GetMapping("/sessions/{sessionId}/draft")
    public Map<String, Object> getDraft(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.getDraftDetail(authorizationHeader, sessionId);
    }

    /** 将官方档案快照同步到 AI 建档草稿。 */
    @PostMapping("/sessions/{sessionId}/draft/sync-from-official")
    public Map<String, Object> syncDraftFromOfficial(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.syncFromOfficialSnapshot(authorizationHeader, sessionId);
    }

    /** 从最新对话中抽取档案补丁并合并到草稿。 */
    @PostMapping("/sessions/{sessionId}/draft/extract-latest-patch")
    public Map<String, Object> extractLatestDraftPatch(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.extractLatestPatch(authorizationHeader, sessionId);
    }

    /** 基于 AI 建档草稿重新生成雷达评分。 */
    @PostMapping("/sessions/{sessionId}/draft/regenerate-radar")
    public Map<String, Object> regenerateDraftRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.regenerateDraftRadar(authorizationHeader, sessionId);
    }
}
