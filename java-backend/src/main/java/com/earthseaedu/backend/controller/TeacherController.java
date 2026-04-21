package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.teacher.TeacherRequests;
import com.earthseaedu.backend.dto.teacher.TeacherResponses;
import com.earthseaedu.backend.service.TeacherArchiveService;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.service.TeacherMockExamPaperSetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 教师端接口，负责教师档案查询和教师模考套卷管理的请求绑定。 */
@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController {

    private final JwtService jwtService;
    private final TeacherArchiveService teacherArchiveService;
    private final TeacherMockExamPaperSetService teacherMockExamPaperSetService;

    public TeacherController(
        JwtService jwtService,
        TeacherArchiveService teacherArchiveService,
        TeacherMockExamPaperSetService teacherMockExamPaperSetService
    ) {
        this.jwtService = jwtService;
        this.teacherArchiveService = teacherArchiveService;
        this.teacherMockExamPaperSetService = teacherMockExamPaperSetService;
    }

    /** 查询指定学生档案，入参为教师登录态和学生关键字，返回学生建档表单与 AI 结果。 */
    @GetMapping("/students/archive")
    public TeacherResponses.TeacherStudentArchiveLookupResponse getTeacherStudentArchive(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @org.springframework.web.bind.annotation.RequestParam String keyword
    ) {
        return teacherArchiveService.loadTeacherStudentArchiveBundle(
            jwtService.requireCurrentUserId(authorizationHeader),
            keyword
        );
    }

    /** 查询当前教师创建的模考套卷列表，返回套卷及试卷摘要。 */
    @GetMapping("/mockexam/paper-sets")
    public TeacherResponses.MockExamPaperSetListResponse listTeacherMockExamPaperSets(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return teacherMockExamPaperSetService.listTeacherMockExamPaperSets(
            jwtService.requireCurrentUserId(authorizationHeader)
        );
    }

    /** 创建教师模考套卷，入参为套卷名称、试卷 ID 列表和备注，返回创建后的套卷。 */
    @PostMapping("/mockexam/paper-sets")
    public TeacherResponses.TeacherMockExamPaperSetMutationResponse createTeacherMockExamPaperSet(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @Valid @RequestBody TeacherRequests.TeacherMockExamPaperSetCreateRequest payload
    ) {
        return new TeacherResponses.TeacherMockExamPaperSetMutationResponse(
            "ok",
            teacherMockExamPaperSetService.createTeacherMockExamPaperSet(
                jwtService.requireCurrentUserId(authorizationHeader),
                payload.setName(),
                payload.examPaperIds(),
                payload.remark()
            )
        );
    }

    /** 更新教师模考套卷启用状态，入参为套卷 ID 和目标状态，返回更新后的套卷。 */
    @PostMapping("/mockexam/paper-sets/{mockexamPaperSetId}/status")
    public TeacherResponses.TeacherMockExamPaperSetMutationResponse updateTeacherMockExamPaperSetStatus(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable long mockexamPaperSetId,
        @Valid @RequestBody TeacherRequests.TeacherMockExamPaperSetStatusUpdateRequest payload
    ) {
        return new TeacherResponses.TeacherMockExamPaperSetMutationResponse(
            "ok",
            teacherMockExamPaperSetService.updateTeacherMockExamPaperSetStatus(
                jwtService.requireCurrentUserId(authorizationHeader),
                mockexamPaperSetId,
                payload.status()
            )
        );
    }
}
