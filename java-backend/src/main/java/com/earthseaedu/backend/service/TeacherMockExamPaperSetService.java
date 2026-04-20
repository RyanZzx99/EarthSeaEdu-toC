package com.earthseaedu.backend.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.earthseaedu.backend.dto.teacher.TeacherResponses;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.mapper.ExamPaperQueryMapper;
import com.earthseaedu.backend.mapper.MockExamPaperSetItemMapper;
import com.earthseaedu.backend.mapper.MockExamPaperSetMapper;
import com.earthseaedu.backend.mapper.UserMapper;
import com.earthseaedu.backend.model.auth.User;
import com.earthseaedu.backend.model.mockexam.ExamPaperSummary;
import com.earthseaedu.backend.model.mockexam.MockExamPaperSet;
import com.earthseaedu.backend.model.mockexam.MockExamPaperSetItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherMockExamPaperSetService {

    private static final String EXAM_CATEGORY_IELTS = "IELTS";

    private final UserMapper userMapper;
    private final MockExamPaperSetMapper mockExamPaperSetMapper;
    private final MockExamPaperSetItemMapper mockExamPaperSetItemMapper;
    private final ExamPaperQueryMapper examPaperQueryMapper;

    public TeacherMockExamPaperSetService(
        UserMapper userMapper,
        MockExamPaperSetMapper mockExamPaperSetMapper,
        MockExamPaperSetItemMapper mockExamPaperSetItemMapper,
        ExamPaperQueryMapper examPaperQueryMapper
    ) {
        this.userMapper = userMapper;
        this.mockExamPaperSetMapper = mockExamPaperSetMapper;
        this.mockExamPaperSetItemMapper = mockExamPaperSetItemMapper;
        this.examPaperQueryMapper = examPaperQueryMapper;
    }

    public TeacherResponses.MockExamPaperSetListResponse listTeacherMockExamPaperSets(String teacherUserId) {
        requireActiveTeacherUser(teacherUserId);
        List<MockExamPaperSet> rows = mockExamPaperSetMapper.findByCreatedBy(teacherUserId);
        return new TeacherResponses.MockExamPaperSetListResponse(buildPaperSetItems(rows, false));
    }

    @Transactional(rollbackFor = Exception.class)
    public TeacherResponses.MockExamPaperSetItem createTeacherMockExamPaperSet(
        String teacherUserId,
        String setName,
        List<Long> examPaperIds,
        String remark
    ) {
        requireActiveTeacherUser(teacherUserId);
        String normalizedSetName = CharSequenceUtil.trim(setName);
        if (CharSequenceUtil.isBlank(normalizedSetName)) {
            throw badRequest("set_name is required");
        }
        if (normalizedSetName.length() > 255) {
            throw badRequest("set_name must be <= 255 chars");
        }

        List<Long> requestedPaperIds = dedupePositiveIds(examPaperIds);
        if (requestedPaperIds.size() < 2) {
            throw badRequest("At least two exam papers are required");
        }

        Map<Long, ExamPaperSummary> paperMap = loadExamPaperMap(requestedPaperIds, true);
        List<ExamPaperSummary> orderedPapers = new ArrayList<>();
        for (Long examPaperId : requestedPaperIds) {
            ExamPaperSummary paper = paperMap.get(examPaperId);
            if (paper == null) {
                throw badRequest("Exam paper #" + examPaperId + " does not exist or is disabled");
            }
            orderedPapers.add(paper);
        }

        LocalDateTime now = LocalDateTime.now();
        MockExamPaperSet paperSet = new MockExamPaperSet();
        paperSet.setSetName(normalizedSetName);
        paperSet.setExamCategory(EXAM_CATEGORY_IELTS);
        paperSet.setExamContent(computeExamContent(orderedPapers));
        paperSet.setPaperCount(orderedPapers.size());
        paperSet.setStatus(1);
        paperSet.setCreatedBy(teacherUserId);
        String normalizedRemark = CharSequenceUtil.trim(remark);
        paperSet.setRemark(CharSequenceUtil.isBlank(normalizedRemark) ? null : normalizedRemark);
        paperSet.setCreateTime(now);
        paperSet.setUpdateTime(now);
        paperSet.setDeleteFlag("1");
        mockExamPaperSetMapper.insert(paperSet);

        for (int index = 0; index < orderedPapers.size(); index++) {
            MockExamPaperSetItem item = new MockExamPaperSetItem();
            item.setMockexamPaperSetId(paperSet.getMockexamPaperSetId());
            item.setExamPaperId(orderedPapers.get(index).getExamPaperId());
            item.setSortOrder(index + 1);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            item.setDeleteFlag("1");
            mockExamPaperSetItemMapper.insert(item);
        }

        return loadTeacherOwnedPaperSetOrThrow(
            teacherUserId,
            paperSet.getMockexamPaperSetId(),
            false
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public TeacherResponses.MockExamPaperSetItem updateTeacherMockExamPaperSetStatus(
        String teacherUserId,
        long mockexamPaperSetId,
        int status
    ) {
        requireActiveTeacherUser(teacherUserId);
        if (status != 0 && status != 1) {
            throw badRequest("status must be 0 or 1");
        }

        MockExamPaperSet paperSet = mockExamPaperSetMapper.findActiveById(mockexamPaperSetId);
        if (paperSet == null) {
            throw badRequest("Paper set does not exist or is disabled");
        }
        if (!teacherUserId.equals(paperSet.getCreatedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to update this paper set");
        }

        mockExamPaperSetMapper.updateStatus(mockexamPaperSetId, status, LocalDateTime.now());
        return loadTeacherOwnedPaperSetOrThrow(teacherUserId, mockexamPaperSetId, false);
    }

    private TeacherResponses.MockExamPaperSetItem loadTeacherOwnedPaperSetOrThrow(
        String teacherUserId,
        long mockexamPaperSetId,
        boolean requireEnabledPaper
    ) {
        MockExamPaperSet paperSet = mockExamPaperSetMapper.findActiveById(mockexamPaperSetId);
        if (paperSet == null) {
            throw badRequest("Paper set does not exist or is disabled");
        }
        if (!teacherUserId.equals(paperSet.getCreatedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to access this paper set");
        }
        List<TeacherResponses.MockExamPaperSetItem> items = buildPaperSetItems(List.of(paperSet), requireEnabledPaper);
        if (items.isEmpty()) {
            throw badRequest("Paper set does not exist or is disabled");
        }
        return items.get(0);
    }

    private List<TeacherResponses.MockExamPaperSetItem> buildPaperSetItems(
        List<MockExamPaperSet> rows,
        boolean requireEnabledPaper
    ) {
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }

        List<Long> paperSetIds = rows.stream()
            .map(MockExamPaperSet::getMockexamPaperSetId)
            .filter(id -> id != null && id > 0)
            .toList();
        List<MockExamPaperSetItem> setItems = paperSetIds.isEmpty()
            ? List.of()
            : mockExamPaperSetItemMapper.findBySetIds(paperSetIds);

        Map<Long, List<MockExamPaperSetItem>> setItemMap = new LinkedHashMap<>();
        Set<Long> examPaperIds = new LinkedHashSet<>();
        for (MockExamPaperSetItem item : setItems) {
            setItemMap.computeIfAbsent(item.getMockexamPaperSetId(), key -> new ArrayList<>()).add(item);
            if (item.getExamPaperId() != null && item.getExamPaperId() > 0) {
                examPaperIds.add(item.getExamPaperId());
            }
        }

        Map<Long, ExamPaperSummary> paperMap = loadExamPaperMap(new ArrayList<>(examPaperIds), requireEnabledPaper);
        List<TeacherResponses.MockExamPaperSetItem> result = new ArrayList<>();
        for (MockExamPaperSet row : rows) {
            List<MockExamPaperSetItem> items = setItemMap.getOrDefault(row.getMockexamPaperSetId(), List.of());
            List<Long> paperIds = new ArrayList<>();
            List<String> paperNames = new ArrayList<>();
            for (MockExamPaperSetItem item : items) {
                ExamPaperSummary paper = paperMap.get(item.getExamPaperId());
                if (paper == null) {
                    continue;
                }
                paperIds.add(paper.getExamPaperId());
                paperNames.add(resolvePaperName(paper));
            }
            result.add(
                new TeacherResponses.MockExamPaperSetItem(
                    row.getMockexamPaperSetId(),
                    row.getSetName(),
                    row.getExamCategory(),
                    row.getExamContent(),
                    row.getPaperCount(),
                    row.getStatus(),
                    row.getRemark(),
                    paperIds,
                    paperNames,
                    row.getCreateTime(),
                    row.getUpdateTime()
                )
            );
        }
        return result;
    }

    private Map<Long, ExamPaperSummary> loadExamPaperMap(List<Long> examPaperIds, boolean requireEnabled) {
        List<Long> normalizedIds = dedupePositiveIds(examPaperIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        List<ExamPaperSummary> rows = examPaperQueryMapper.findByIds(normalizedIds, requireEnabled);
        Map<Long, ExamPaperSummary> result = new LinkedHashMap<>();
        for (ExamPaperSummary row : rows) {
            result.put(row.getExamPaperId(), row);
        }
        return result;
    }

    private List<Long> dedupePositiveIds(List<Long> values) {
        LinkedHashSet<Long> deduped = new LinkedHashSet<>();
        if (values != null) {
            for (Long value : values) {
                if (value != null && value > 0) {
                    deduped.add(value);
                }
            }
        }
        return new ArrayList<>(deduped);
    }

    private String computeExamContent(List<ExamPaperSummary> papers) {
        LinkedHashSet<String> contents = new LinkedHashSet<>();
        for (ExamPaperSummary paper : papers) {
            contents.add(resolvePaperContent(paper));
        }
        if (contents.size() == 1) {
            return contents.iterator().next();
        }
        return "Mixed";
    }

    private String resolvePaperContent(ExamPaperSummary paper) {
        if (paper != null && "listening".equalsIgnoreCase(CharSequenceUtil.blankToDefault(paper.getSubjectType(), ""))) {
            return "Listening";
        }
        return "Reading";
    }

    private String resolvePaperName(ExamPaperSummary paper) {
        if (paper == null) {
            return "";
        }
        if (CharSequenceUtil.isNotBlank(paper.getPaperName())) {
            return paper.getPaperName();
        }
        if (CharSequenceUtil.isNotBlank(paper.getPaperCode())) {
            return paper.getPaperCode();
        }
        return "Paper " + paper.getExamPaperId();
    }

    private void requireActiveTeacherUser(String teacherUserId) {
        User user = userMapper.findActiveById(teacherUserId);
        if (user == null || !"active".equals(user.getStatus()) || !"1".equals(user.getIsTeacher())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Teacher portal is not enabled");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
