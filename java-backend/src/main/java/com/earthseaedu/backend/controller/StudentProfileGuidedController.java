package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.guided.StudentProfileGuidedRequests;
import com.earthseaedu.backend.service.StudentProfileGuidedService;
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

/** AI 建档引导问卷接口，负责路由、请求绑定和转发到服务层。 */
@RestController
@RequestMapping("/api/v1/student-profile/guided")
public class StudentProfileGuidedController {

    private final StudentProfileGuidedService guidedService;

    public StudentProfileGuidedController(StudentProfileGuidedService guidedService) {
        this.guidedService = guidedService;
    }

    /** 查询 AI 建档问卷题目配置，返回前端渲染所需的题目列表。 */
    @GetMapping("/questions")
    public Map<String, Object> listGuidedQuestions() {
        return Map.of("questions", guidedService.listQuestions());
    }

    /** 查询当前用户的建档会话，可按参数决定是否自动创建新会话。 */
    @GetMapping("/current")
    public Map<String, Object> getCurrentGuidedSession(
        @RequestParam(value = "create_if_missing", required = false, defaultValue = "1") Integer createIfMissing,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.getOrCreateCurrentBundle(
            authorizationHeader,
            createIfMissing != null && createIfMissing == 1
        );
    }

    /** 重开当前用户的 AI 建档会话，旧会话会被标记为退出。 */
    @PostMapping("/restart")
    public Map<String, Object> restartGuidedSession(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.restartCurrentSession(authorizationHeader);
    }

    /** 查询指定 AI 建档会话详情，返回会话、消息、答案和结果。 */
    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getGuidedSession(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.getSessionBundle(authorizationHeader, sessionId);
    }

    /** 提交单题答案，保存答案并推进下一题或触发建档结果生成。 */
    @PostMapping("/sessions/{sessionId}/answers")
    public Map<String, Object> submitAnswer(
        @PathVariable("sessionId") String sessionId,
        @Valid @RequestBody StudentProfileGuidedRequests.GuidedAnswerPayload payload,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.submitGuidedAnswer(
            authorizationHeader,
            sessionId,
            payload.questionCode(),
            payload.answer()
        );
    }

    /** 退出当前 AI 建档会话，并基于已有答案生成阶段性结果。 */
    @PostMapping("/sessions/{sessionId}/exit")
    public Map<String, Object> exitSession(
        @PathVariable("sessionId") String sessionId,
        @Valid @RequestBody StudentProfileGuidedRequests.GuidedExitPayload payload,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        String triggerReason = payload.triggerReason() == null || payload.triggerReason().isBlank()
            ? "manual_exit"
            : payload.triggerReason();
        return guidedService.exitGuidedSession(authorizationHeader, sessionId, triggerReason);
    }

    /** 手动重新生成 AI 建档结果，返回最新结果内容。 */
    @PostMapping("/sessions/{sessionId}/result")
    public Map<String, Object> regenerateResult(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return Map.of("result", guidedService.generateGuidedResult(authorizationHeader, sessionId, "manual_regenerate"));
    }
}
