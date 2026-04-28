package com.earthseaedu.backend.model.mockexam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MockExamQuestionRef {

    private Long mockexamQuestionRefId;
    private Long mockexamPaperRefId;
    private String sourceType;
    private Long sourceQuestionId;
    private String sourceQuestionCode;
    private String questionNoDisplay;
    private String questionType;
    private String responseMode;
    private String statType;
    private BigDecimal maxScore;
    private String previewText;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getMockexamQuestionRefId() {
        return mockexamQuestionRefId;
    }

    public void setMockexamQuestionRefId(Long mockexamQuestionRefId) {
        this.mockexamQuestionRefId = mockexamQuestionRefId;
    }

    public Long getMockexamPaperRefId() {
        return mockexamPaperRefId;
    }

    public void setMockexamPaperRefId(Long mockexamPaperRefId) {
        this.mockexamPaperRefId = mockexamPaperRefId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceQuestionId() {
        return sourceQuestionId;
    }

    public void setSourceQuestionId(Long sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public String getSourceQuestionCode() {
        return sourceQuestionCode;
    }

    public void setSourceQuestionCode(String sourceQuestionCode) {
        this.sourceQuestionCode = sourceQuestionCode;
    }

    public String getQuestionNoDisplay() {
        return questionNoDisplay;
    }

    public void setQuestionNoDisplay(String questionNoDisplay) {
        this.questionNoDisplay = questionNoDisplay;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public String getStatType() {
        return statType;
    }

    public void setStatType(String statType) {
        this.statType = statType;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
