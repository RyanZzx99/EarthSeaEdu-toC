package com.earthseaedu.backend.model.alevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlevelQuestionMarkScheme {

    private Long alevelQuestionMarkSchemeId;
    private Long alevelQuestionId;
    private Long alevelSourceFileId;
    private String questionKey;
    private BigDecimal markValue;
    private String answerSummaryText;
    private String markSchemeText;
    private String markSchemeHtml;
    private String markSchemeJson;
    private Integer sourcePageStart;
    private Integer sourcePageEnd;
    private String sourceBboxJson;
    private String rawExcerptHash;
    private String matchStatus;
    private BigDecimal matchConfidence;
    private String gradingMode;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelQuestionMarkSchemeId() {
        return alevelQuestionMarkSchemeId;
    }

    public void setAlevelQuestionMarkSchemeId(Long alevelQuestionMarkSchemeId) {
        this.alevelQuestionMarkSchemeId = alevelQuestionMarkSchemeId;
    }

    public Long getAlevelQuestionId() {
        return alevelQuestionId;
    }

    public void setAlevelQuestionId(Long alevelQuestionId) {
        this.alevelQuestionId = alevelQuestionId;
    }

    public Long getAlevelSourceFileId() {
        return alevelSourceFileId;
    }

    public void setAlevelSourceFileId(Long alevelSourceFileId) {
        this.alevelSourceFileId = alevelSourceFileId;
    }

    public String getQuestionKey() {
        return questionKey;
    }

    public void setQuestionKey(String questionKey) {
        this.questionKey = questionKey;
    }

    public BigDecimal getMarkValue() {
        return markValue;
    }

    public void setMarkValue(BigDecimal markValue) {
        this.markValue = markValue;
    }

    public String getAnswerSummaryText() {
        return answerSummaryText;
    }

    public void setAnswerSummaryText(String answerSummaryText) {
        this.answerSummaryText = answerSummaryText;
    }

    public String getMarkSchemeText() {
        return markSchemeText;
    }

    public void setMarkSchemeText(String markSchemeText) {
        this.markSchemeText = markSchemeText;
    }

    public String getMarkSchemeHtml() {
        return markSchemeHtml;
    }

    public void setMarkSchemeHtml(String markSchemeHtml) {
        this.markSchemeHtml = markSchemeHtml;
    }

    public String getMarkSchemeJson() {
        return markSchemeJson;
    }

    public void setMarkSchemeJson(String markSchemeJson) {
        this.markSchemeJson = markSchemeJson;
    }

    public Integer getSourcePageStart() {
        return sourcePageStart;
    }

    public void setSourcePageStart(Integer sourcePageStart) {
        this.sourcePageStart = sourcePageStart;
    }

    public Integer getSourcePageEnd() {
        return sourcePageEnd;
    }

    public void setSourcePageEnd(Integer sourcePageEnd) {
        this.sourcePageEnd = sourcePageEnd;
    }

    public String getSourceBboxJson() {
        return sourceBboxJson;
    }

    public void setSourceBboxJson(String sourceBboxJson) {
        this.sourceBboxJson = sourceBboxJson;
    }

    public String getRawExcerptHash() {
        return rawExcerptHash;
    }

    public void setRawExcerptHash(String rawExcerptHash) {
        this.rawExcerptHash = rawExcerptHash;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public BigDecimal getMatchConfidence() {
        return matchConfidence;
    }

    public void setMatchConfidence(BigDecimal matchConfidence) {
        this.matchConfidence = matchConfidence;
    }

    public String getGradingMode() {
        return gradingMode;
    }

    public void setGradingMode(String gradingMode) {
        this.gradingMode = gradingMode;
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
