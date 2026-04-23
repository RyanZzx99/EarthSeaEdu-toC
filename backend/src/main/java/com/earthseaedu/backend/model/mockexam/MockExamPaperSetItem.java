package com.earthseaedu.backend.model.mockexam;

import java.time.LocalDateTime;

public class MockExamPaperSetItem {

    private Long mockexamPaperSetItemId;
    private Long mockexamPaperSetId;
    private Long examPaperId;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getMockexamPaperSetItemId() {
        return mockexamPaperSetItemId;
    }

    public void setMockexamPaperSetItemId(Long mockexamPaperSetItemId) {
        this.mockexamPaperSetItemId = mockexamPaperSetItemId;
    }

    public Long getMockexamPaperSetId() {
        return mockexamPaperSetId;
    }

    public void setMockexamPaperSetId(Long mockexamPaperSetId) {
        this.mockexamPaperSetId = mockexamPaperSetId;
    }

    public Long getExamPaperId() {
        return examPaperId;
    }

    public void setExamPaperId(Long examPaperId) {
        this.examPaperId = examPaperId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(String deleteFlag) {
        this.deleteFlag = deleteFlag;
    }
}
