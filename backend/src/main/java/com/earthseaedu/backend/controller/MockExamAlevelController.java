package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.MockExamAlevelService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A-Level 模拟考试接口，后续 A-Level 专属能力统一放在该入口下。
 */
@RestController
@RequestMapping("/api/v1/mockexam/alevel")
public class MockExamAlevelController {

    private final JwtService jwtService;
    private final MockExamAlevelService mockExamAlevelService;

    public MockExamAlevelController(JwtService jwtService, MockExamAlevelService mockExamAlevelService) {
        this.jwtService = jwtService;
        this.mockExamAlevelService = mockExamAlevelService;
    }

    /**
     * 查询 A-Level 练习筛选项，返回可用科目列表。
     */
    @GetMapping("/options")
    public Map<String, Object> getOptions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamAlevelService.getOptions();
    }

    /**
     * 查询 A-Level 可用试卷，exam_content 可选。
     */
    @GetMapping("/papers")
    public Map<String, Object> listPapers(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamAlevelService.listPapers(examContent);
    }

    /**
     * 查询 A-Level 单张试卷详情，用于进入答题页。
     */
    @GetMapping("/papers/{examPaperId}")
    public Map<String, Object> getPaper(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamAlevelService.getPaper(examPaperId);
    }
}
