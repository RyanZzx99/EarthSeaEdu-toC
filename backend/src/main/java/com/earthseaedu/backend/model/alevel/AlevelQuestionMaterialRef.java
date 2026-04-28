package com.earthseaedu.backend.model.alevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlevelQuestionMaterialRef {

    private Long alevelQuestionMaterialRefId;
    private Long alevelQuestionId;
    private String materialType;
    private String relationType;
    private String displayMode;
    private Long alevelSourceFileId;
    private Long alevelPdfPageId;
    private Long alevelContentBlockId;
    private Long alevelAssetId;
    private Integer sourcePageNo;
    private String sourceBboxJson;
    private String title;
    private String captionHtml;
    private String captionText;
    private String structureJson;
    private String matchStatus;
    private BigDecimal matchConfidence;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelQuestionMaterialRefId() {
        return alevelQuestionMaterialRefId;
    }

    public void setAlevelQuestionMaterialRefId(Long alevelQuestionMaterialRefId) {
        this.alevelQuestionMaterialRefId = alevelQuestionMaterialRefId;
    }

    public Long getAlevelQuestionId() {
        return alevelQuestionId;
    }

    public void setAlevelQuestionId(Long alevelQuestionId) {
        this.alevelQuestionId = alevelQuestionId;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(String displayMode) {
        this.displayMode = displayMode;
    }

    public Long getAlevelSourceFileId() {
        return alevelSourceFileId;
    }

    public void setAlevelSourceFileId(Long alevelSourceFileId) {
        this.alevelSourceFileId = alevelSourceFileId;
    }

    public Long getAlevelPdfPageId() {
        return alevelPdfPageId;
    }

    public void setAlevelPdfPageId(Long alevelPdfPageId) {
        this.alevelPdfPageId = alevelPdfPageId;
    }

    public Long getAlevelContentBlockId() {
        return alevelContentBlockId;
    }

    public void setAlevelContentBlockId(Long alevelContentBlockId) {
        this.alevelContentBlockId = alevelContentBlockId;
    }

    public Long getAlevelAssetId() {
        return alevelAssetId;
    }

    public void setAlevelAssetId(Long alevelAssetId) {
        this.alevelAssetId = alevelAssetId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCaptionHtml() {
        return captionHtml;
    }

    public void setCaptionHtml(String captionHtml) {
        this.captionHtml = captionHtml;
    }

    public String getCaptionText() {
        return captionText;
    }

    public void setCaptionText(String captionText) {
        this.captionText = captionText;
    }

    public String getStructureJson() {
        return structureJson;
    }

    public void setStructureJson(String structureJson) {
        this.structureJson = structureJson;
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
