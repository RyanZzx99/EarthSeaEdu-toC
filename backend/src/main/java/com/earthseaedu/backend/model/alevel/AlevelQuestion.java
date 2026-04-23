package com.earthseaedu.backend.model.alevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlevelQuestion {

    private Long alevelQuestionId;
    private Long alevelPaperId;
    private Long alevelModuleId;
    private Long parentQuestionId;
    private String questionCode;
    private String questionNoDisplay;
    private String markSchemeQuestionKey;
    private String questionType;
    private String responseMode;
    private Integer autoGradable;
    private String stemHtml;
    private String stemText;
    private String contentHtml;
    private String contentText;
    private String answerInputSchemaJson;
    private BigDecimal maxScore;
    private Integer sourcePageNo;
    private String sourceBboxJson;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelQuestionId() {
        return alevelQuestionId;
    }

    public void setAlevelQuestionId(Long alevelQuestionId) {
        this.alevelQuestionId = alevelQuestionId;
    }

    public Long getAlevelPaperId() {
        return alevelPaperId;
    }

    public void setAlevelPaperId(Long alevelPaperId) {
        this.alevelPaperId = alevelPaperId;
    }

    public Long getAlevelModuleId() {
        return alevelModuleId;
    }

    public void setAlevelModuleId(Long alevelModuleId) {
        this.alevelModuleId = alevelModuleId;
    }

    public Long getParentQuestionId() {
        return parentQuestionId;
    }

    public void setParentQuestionId(Long parentQuestionId) {
        this.parentQuestionId = parentQuestionId;
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public void setQuestionCode(String questionCode) {
        this.questionCode = questionCode;
    }

    public String getQuestionNoDisplay() {
        return questionNoDisplay;
    }

    public void setQuestionNoDisplay(String questionNoDisplay) {
        this.questionNoDisplay = questionNoDisplay;
    }

    public String getMarkSchemeQuestionKey() {
        return markSchemeQuestionKey;
    }

    public void setMarkSchemeQuestionKey(String markSchemeQuestionKey) {
        this.markSchemeQuestionKey = markSchemeQuestionKey;
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

    public Integer getAutoGradable() {
        return autoGradable;
    }

    public void setAutoGradable(Integer autoGradable) {
        this.autoGradable = autoGradable;
    }

    public String getStemHtml() {
        return stemHtml;
    }

    public void setStemHtml(String stemHtml) {
        this.stemHtml = stemHtml;
    }

    public String getStemText() {
        return stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getAnswerInputSchemaJson() {
        return answerInputSchemaJson;
    }

    public void setAnswerInputSchemaJson(String answerInputSchemaJson) {
        this.answerInputSchemaJson = answerInputSchemaJson;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getSourcePageNo() {
        return sourcePageNo;
    }

    public void setSourcePageNo(Integer sourcePageNo) {
        this.sourcePageNo = sourcePageNo;
    }

    public String getSourceBboxJson() {
        return sourceBboxJson;
    }

    public void setSourceBboxJson(String sourceBboxJson) {
        this.sourceBboxJson = sourceBboxJson;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
