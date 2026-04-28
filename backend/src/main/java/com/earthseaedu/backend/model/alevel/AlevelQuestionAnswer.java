package com.earthseaedu.backend.model.alevel;

import java.time.LocalDateTime;

public class AlevelQuestionAnswer {

    private Long alevelQuestionAnswerId;
    private Long alevelQuestionId;
    private String answerRaw;
    private String answerJson;
    private String markSchemeJson;
    private String markSchemeExcerptText;
    private String gradingMode;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelQuestionAnswerId() {
        return alevelQuestionAnswerId;
    }

    public void setAlevelQuestionAnswerId(Long alevelQuestionAnswerId) {
        this.alevelQuestionAnswerId = alevelQuestionAnswerId;
    }

    public Long getAlevelQuestionId() {
        return alevelQuestionId;
    }

    public void setAlevelQuestionId(Long alevelQuestionId) {
        this.alevelQuestionId = alevelQuestionId;
    }

    public String getAnswerRaw() {
        return answerRaw;
    }

    public void setAnswerRaw(String answerRaw) {
        this.answerRaw = answerRaw;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public void setAnswerJson(String answerJson) {
        this.answerJson = answerJson;
    }

    public String getMarkSchemeJson() {
        return markSchemeJson;
    }

    public void setMarkSchemeJson(String markSchemeJson) {
        this.markSchemeJson = markSchemeJson;
    }

    public String getMarkSchemeExcerptText() {
        return markSchemeExcerptText;
    }

    public void setMarkSchemeExcerptText(String markSchemeExcerptText) {
        this.markSchemeExcerptText = markSchemeExcerptText;
    }

    public String getGradingMode() {
        return gradingMode;
    }

    public void setGradingMode(String gradingMode) {
        this.gradingMode = gradingMode;
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
