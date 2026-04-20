package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.mockexam.MockExamRequests;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.MockExamService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mockexam")
public class MockExamController {

    private final JwtService jwtService;
    private final MockExamService mockExamService;

    public MockExamController(JwtService jwtService, MockExamService mockExamService) {
        this.jwtService = jwtService;
        this.mockExamService = mockExamService;
    }

    @GetMapping("/options")
    public Map<String, Object> getOptions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getOptions();
    }

    @GetMapping("/papers")
    public Map<String, Object> listPapers(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_category", required = false) String examCategory,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.listPapers(examCategory, examContent);
    }

    @GetMapping("/papers/{examPaperId}")
    public Map<String, Object> getPaper(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getPaper(examPaperId);
    }

    @GetMapping("/paper-sets")
    public Map<String, Object> listPaperSets(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_category", required = false) String examCategory,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.listPaperSets(examCategory, examContent);
    }

    @GetMapping("/paper-sets/{paperSetId}")
    public Map<String, Object> getPaperSet(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long paperSetId
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getPaperSet(paperSetId);
    }

    @PostMapping("/papers/{examPaperId}/submit")
    public Map<String, Object> submitPaper(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId,
        @Valid @RequestBody MockExamRequests.SubmitRequest payload
    ) {
        return mockExamService.submitPaper(
            jwtService.requireCurrentUserId(authorizationHeader),
            examPaperId,
            payload
        );
    }

    @PostMapping("/paper-sets/{paperSetId}/submit")
    public Map<String, Object> submitPaperSet(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long paperSetId,
        @Valid @RequestBody MockExamRequests.SubmitRequest payload
    ) {
        return mockExamService.submitPaperSet(
            jwtService.requireCurrentUserId(authorizationHeader),
            paperSetId,
            payload
        );
    }

    @GetMapping("/submissions")
    public Map<String, Object> listSubmissions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_content", required = false) String examContent,
        @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        return mockExamService.listSubmissions(
            jwtService.requireCurrentUserId(authorizationHeader),
            examContent,
            limit
        );
    }

    @GetMapping("/submissions/{submissionId}")
    public Map<String, Object> getSubmission(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long submissionId
    ) {
        return mockExamService.getSubmission(
            jwtService.requireCurrentUserId(authorizationHeader),
            submissionId
        );
    }

    @GetMapping("/progress")
    public Map<String, Object> listProgresses(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        return mockExamService.listProgresses(
            jwtService.requireCurrentUserId(authorizationHeader),
            limit
        );
    }

    @GetMapping("/progress/{progressId}")
    public Map<String, Object> getProgress(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long progressId
    ) {
        return mockExamService.getProgress(
            jwtService.requireCurrentUserId(authorizationHeader),
            progressId
        );
    }

    @PostMapping("/papers/{examPaperId}/progress")
    public Map<String, Object> savePaperProgress(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId,
        @Valid @RequestBody MockExamRequests.ProgressSaveRequest payload
    ) {
        return mockExamService.savePaperProgress(
            jwtService.requireCurrentUserId(authorizationHeader),
            examPaperId,
            payload
        );
    }

    @PostMapping("/paper-sets/{paperSetId}/progress")
    public Map<String, Object> savePaperSetProgress(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long paperSetId,
        @Valid @RequestBody MockExamRequests.ProgressSaveRequest payload
    ) {
        return mockExamService.savePaperSetProgress(
            jwtService.requireCurrentUserId(authorizationHeader),
            paperSetId,
            payload
        );
    }

    @PostMapping("/progress/{progressId}/discard")
    public Map<String, Object> discardProgress(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long progressId
    ) {
        return mockExamService.discardProgress(
            jwtService.requireCurrentUserId(authorizationHeader),
            progressId
        );
    }

    @GetMapping("/favorites")
    public Map<String, Object> listFavorites(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_paper_id", required = false) Long examPaperId,
        @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        return mockExamService.listQuestionFavorites(
            jwtService.requireCurrentUserId(authorizationHeader),
            examPaperId,
            limit
        );
    }

    @GetMapping("/entity-favorites")
    public Map<String, Object> listEntityFavorites(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return mockExamService.listEntityFavorites(
            jwtService.requireCurrentUserId(authorizationHeader),
            limit
        );
    }

    @PostMapping("/translate-selection")
    public Map<String, Object> translateSelection(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @Valid @RequestBody MockExamRequests.SelectionTranslateRequest payload
    ) {
        return mockExamService.translateSelection(
            jwtService.requireCurrentUserId(authorizationHeader),
            payload
        );
    }

    @PostMapping("/questions/{examQuestionId}/favorite")
    public Map<String, Object> toggleQuestionFavorite(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examQuestionId,
        @Valid @RequestBody MockExamRequests.FavoriteToggleRequest payload
    ) {
        return mockExamService.toggleQuestionFavorite(
            jwtService.requireCurrentUserId(authorizationHeader),
            examQuestionId,
            payload
        );
    }

    @PostMapping("/papers/{examPaperId}/favorite")
    public Map<String, Object> togglePaperFavorite(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId,
        @Valid @RequestBody MockExamRequests.EntityFavoriteToggleRequest payload
    ) {
        return mockExamService.togglePaperFavorite(
            jwtService.requireCurrentUserId(authorizationHeader),
            examPaperId,
            payload
        );
    }

    @PostMapping("/paper-sets/{paperSetId}/favorite")
    public Map<String, Object> togglePaperSetFavorite(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long paperSetId,
        @Valid @RequestBody MockExamRequests.EntityFavoriteToggleRequest payload
    ) {
        return mockExamService.togglePaperSetFavorite(
            jwtService.requireCurrentUserId(authorizationHeader),
            paperSetId,
            payload
        );
    }

    @GetMapping("/wrong-questions")
    public Map<String, Object> listWrongQuestions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        return mockExamService.listWrongQuestions(
            jwtService.requireCurrentUserId(authorizationHeader),
            limit
        );
    }

    @PostMapping("/wrong-questions/resolve")
    public Map<String, Object> resolveWrongQuestions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @Valid @RequestBody MockExamRequests.WrongQuestionResolveRequest payload
    ) {
        return mockExamService.resolveWrongQuestions(
            jwtService.requireCurrentUserId(authorizationHeader),
            payload
        );
    }

    @GetMapping("/questions/{examQuestionId}")
    public Map<String, Object> getQuestionDetail(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examQuestionId
    ) {
        return mockExamService.getQuestionDetail(
            jwtService.requireCurrentUserId(authorizationHeader),
            examQuestionId
        );
    }
}
