package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.service.AuthService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.MockExamActService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ACT mock exam admin APIs. Handles ACT question-bank import separately from IELTS and A-Level.
 */
@RestController
@RequestMapping("/api/v1/mockexam/act")
public class MockExamActController {

    private final MockExamActService mockExamActService;
    private final AuthService authService;
    private final JwtService jwtService;

    public MockExamActController(MockExamActService mockExamActService, AuthService authService, JwtService jwtService) {
        this.mockExamActService = mockExamActService;
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * Query ACT practice filters. Returns the ACT exam type and available content options.
     */
    @GetMapping("/options")
    public Map<String, Object> getOptions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamActService.getOptions();
    }

    /**
     * Query ACT papers for practice. exam_content is optional and currently defaults to ACT.
     */
    @GetMapping("/papers")
    public Map<String, Object> listPapers(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamActService.listPapers(examContent);
    }

    /**
     * Query one ACT paper payload for the shared mock-exam runner page.
     */
    @GetMapping("/papers/{examPaperId}")
    public Map<String, Object> getPaper(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamActService.getPaper(examPaperId, examContent);
    }

    /**
     * Create an async ACT JSON import job. Supports zip, directory and multi-file upload modes.
     */
    @PostMapping(value = "/question-banks/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importActQuestionBank(
        @RequestParam("source_mode") String sourceMode,
        @RequestParam(value = "batch_name", required = false) String batchName,
        @RequestParam(value = "entry_paths_json", required = false) String entryPathsJson,
        @RequestParam("files") List<MultipartFile> files,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return mockExamActService.createImportJob(sourceMode, batchName, entryPathsJson, files);
    }

    /**
     * Query an ACT import job. Returns progress, counters, imported items and failures.
     */
    @GetMapping("/question-banks/import-jobs/{job_id}")
    public Map<String, Object> getActQuestionBankImportJob(
        @PathVariable("job_id") long jobId,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        authService.verifyInviteAdminKey(adminKey);
        return mockExamActService.getImportJobDetail(jobId);
    }
}
