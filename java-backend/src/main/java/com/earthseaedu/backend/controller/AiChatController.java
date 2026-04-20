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

    @GetMapping("/sessions/current")
    public AiChatResponses.CurrentSessionEnvelope getCurrentSession(
        @RequestParam("biz_domain") String bizDomain,
        @RequestParam(value = "create_if_missing", required = false, defaultValue = "0") Integer createIfMissing,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getCurrentSession(authorizationHeader, bizDomain, createIfMissing);
    }

    @GetMapping("/sessions/{sessionId}")
    public AiChatResponses.SessionDetailResponse getSessionDetail(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getSessionDetail(authorizationHeader, sessionId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public AiChatResponses.MessageListResponse getMessages(
        @PathVariable("sessionId") String sessionId,
        @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
        @RequestParam(value = "before_id", required = false) Long beforeId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getMessages(authorizationHeader, sessionId, limit, beforeId);
    }

    @GetMapping("/sessions/{sessionId}/result")
    public AiChatResponses.ProfileResultResponse getResult(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getResult(authorizationHeader, sessionId);
    }

    @GetMapping("/sessions/{sessionId}/radar")
    public AiChatResponses.RadarResponse getRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getRadar(authorizationHeader, sessionId);
    }

    @GetMapping("/sessions/{sessionId}/archive-form")
    public AiChatResponses.ArchiveFormResponse getArchiveForm(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatReadService.getArchiveForm(authorizationHeader, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/archive-form")
    public AiChatResponses.ArchiveFormMutationResponse saveArchiveForm(
        @PathVariable("sessionId") String sessionId,
        @Valid @RequestBody AiChatRequests.ArchiveFormSaveRequest payload,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatWriteService.saveArchiveForm(authorizationHeader, sessionId, payload.archiveForm());
    }

    @PostMapping("/sessions/{sessionId}/archive-form/regenerate-radar")
    public Map<String, Object> regenerateArchiveRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.regenerateArchiveRadar(authorizationHeader, sessionId);
    }

    @GetMapping("/sessions/{sessionId}/draft")
    public Map<String, Object> getDraft(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.getDraftDetail(authorizationHeader, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/draft/sync-from-official")
    public Map<String, Object> syncDraftFromOfficial(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.syncFromOfficialSnapshot(authorizationHeader, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/draft/extract-latest-patch")
    public Map<String, Object> extractLatestDraftPatch(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.extractLatestPatch(authorizationHeader, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/draft/regenerate-radar")
    public Map<String, Object> regenerateDraftRadar(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return aiChatDraftService.regenerateDraftRadar(authorizationHeader, sessionId);
    }
}
