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

@RestController
@RequestMapping("/api/v1/student-profile/guided")
public class StudentProfileGuidedController {

    private final StudentProfileGuidedService guidedService;

    public StudentProfileGuidedController(StudentProfileGuidedService guidedService) {
        this.guidedService = guidedService;
    }

    @GetMapping("/questions")
    public Map<String, Object> listGuidedQuestions() {
        return Map.of("questions", guidedService.listQuestions());
    }

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

    @PostMapping("/restart")
    public Map<String, Object> restartGuidedSession(
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.restartCurrentSession(authorizationHeader);
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getGuidedSession(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return guidedService.getSessionBundle(authorizationHeader, sessionId);
    }

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

    @PostMapping("/sessions/{sessionId}/result")
    public Map<String, Object> regenerateResult(
        @PathVariable("sessionId") String sessionId,
        @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return Map.of("result", guidedService.generateGuidedResult(authorizationHeader, sessionId, "manual_regenerate"));
    }
}
