package com.earthseaedu.backend.model.nickname;

import java.time.LocalDateTime;

public class NicknameContactPattern {

    private Integer id;
    private Integer groupId;
    private String patternName;
    private String patternType;
    private String patternRegex;
    private String decision;
    private String status;
    private Integer priority;
    private String riskLevel;
    private String normalizedHint;
    private String note;
    private LocalDateTime effectiveStartTime;
    private LocalDateTime effectiveEndTime;
    private Integer versionNo;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getPatternName() {
        return patternName;
    }

    public void setPatternName(String patternName) {
        this.patternName = patternName;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }

    public String getPatternRegex() {
        return patternRegex;
    }

    public void setPatternRegex(String patternRegex) {
        this.patternRegex = patternRegex;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getNormalizedHint() {
        return normalizedHint;
    }

    public void setNormalizedHint(String normalizedHint) {
        this.normalizedHint = normalizedHint;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getEffectiveStartTime() {
        return effectiveStartTime;
    }

    public void setEffectiveStartTime(LocalDateTime effectiveStartTime) {
        this.effectiveStartTime = effectiveStartTime;
    }

    public LocalDateTime getEffectiveEndTime() {
        return effectiveEndTime;
    }

    public void setEffectiveEndTime(LocalDateTime effectiveEndTime) {
        this.effectiveEndTime = effectiveEndTime;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
