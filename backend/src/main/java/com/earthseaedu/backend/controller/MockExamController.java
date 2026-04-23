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

/** 模考接口，负责路由、鉴权入口和请求参数绑定。 */
@RestController
@RequestMapping("/api/v1/mockexam")
public class MockExamController {

    private final JwtService jwtService;
    private final MockExamService mockExamService;

    public MockExamController(JwtService jwtService, MockExamService mockExamService) {
        this.jwtService = jwtService;
        this.mockExamService = mockExamService;
    }

    /** 查询模考筛选项，返回支持的考试类型和内容类型。 */
    @GetMapping("/options")
    public Map<String, Object> getOptions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getOptions();
    }

    /** 查询可用模考试卷列表，可按考试类型和内容类型过滤。 */
    @GetMapping("/papers")
    public Map<String, Object> listPapers(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_category", required = false) String examCategory,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.listPapers(examCategory, examContent);
    }

    /** 查询单套模考试卷详情，返回试卷基础信息和答题载荷。 */
    @GetMapping("/papers/{examPaperId}")
    public Map<String, Object> getPaper(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long examPaperId
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getPaper(examPaperId);
    }

    /** 查询模考套卷列表，可按考试类型和内容类型过滤。 */
    @GetMapping("/paper-sets")
    public Map<String, Object> listPaperSets(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam(value = "exam_category", required = false) String examCategory,
        @RequestParam(value = "exam_content", required = false) String examContent
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.listPaperSets(examCategory, examContent);
    }

    /** 查询单个模考套卷详情，返回套卷信息和合并后的答题载荷。 */
    @GetMapping("/paper-sets/{paperSetId}")
    public Map<String, Object> getPaperSet(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long paperSetId
    ) {
        jwtService.requireCurrentUserId(authorizationHeader);
        return mockExamService.getPaperSet(paperSetId);
    }

    /** 提交单套试卷答案，生成提交记录并同步错题本。 */
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

    /** 提交套卷答案，生成提交记录并同步错题本。 */
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

    /** 查询当前用户模考提交历史，可按内容类型过滤。 */
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

    /** 查询单次模考提交详情，返回答题记录、标记和评估结果。 */
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

    /** 查询当前用户未完成的模考进度列表。 */
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

    /** 查询单条未完成模考进度，返回载荷、答案和标记状态。 */
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

    /** 保存单套试卷练习进度，用于继续作答。 */
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

    /** 保存套卷练习进度，用于继续作答。 */
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

    /** 放弃未完成的模考进度，将进度标记为失效。 */
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

    /** 查询当前用户收藏的模考题目，可按试卷过滤。 */
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

    /** 查询当前用户收藏的试卷和套卷。 */
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

    /** 翻译模考页面选中文本，返回结构化翻译结果。 */
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

    /** 收藏或取消收藏单个模考题目。 */
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

    /** 收藏或取消收藏单套模考试卷。 */
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

    /** 收藏或取消收藏模考套卷。 */
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

    /** 查询当前用户错题本，按试卷和题组聚合返回。 */
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

    /** 批量移除错题本中的题目，将错题状态标记为已处理。 */
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

    /** 查询单题详情，返回题干、材料、答案选项和用户状态。 */
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
