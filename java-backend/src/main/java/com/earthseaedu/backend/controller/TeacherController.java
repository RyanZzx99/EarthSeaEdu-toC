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

    @GetMapping("/mockexam/paper-sets")
    public TeacherResponses.MockExamPaperSetListResponse listTeacherMockExamPaperSets(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return teacherMockExamPaperSetService.listTeacherMockExamPaperSets(
            jwtService.requireCurrentUserId(authorizationHeader)
        );
    }

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
