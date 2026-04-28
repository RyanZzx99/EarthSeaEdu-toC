package com.earthseaedu.backend.model.alevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlevelSourceBundleFile {

    private Long alevelSourceBundleFileId;
    private Long alevelSourceBundleId;
    private Long alevelSourceFileId;
    private String fileRole;
    private Integer isPrimary;
    private String matchStatus;
    private BigDecimal matchConfidence;
    private String matchEvidenceJson;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelSourceBundleFileId() {
        return alevelSourceBundleFileId;
    }

    public void setAlevelSourceBundleFileId(Long alevelSourceBundleFileId) {
        this.alevelSourceBundleFileId = alevelSourceBundleFileId;
    }

    public Long getAlevelSourceBundleId() {
        return alevelSourceBundleId;
    }

    public void setAlevelSourceBundleId(Long alevelSourceBundleId) {
        this.alevelSourceBundleId = alevelSourceBundleId;
    }

    public Long getAlevelSourceFileId() {
        return alevelSourceFileId;
    }

    public void setAlevelSourceFileId(Long alevelSourceFileId) {
        this.alevelSourceFileId = alevelSourceFileId;
    }

    public String getFileRole() {
        return fileRole;
    }

    public void setFileRole(String fileRole) {
        this.fileRole = fileRole;
    }

    public Integer getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Integer isPrimary) {
        this.isPrimary = isPrimary;
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

    public String getMatchEvidenceJson() {
        return matchEvidenceJson;
    }

    public void setMatchEvidenceJson(String matchEvidenceJson) {
        this.matchEvidenceJson = matchEvidenceJson;
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
