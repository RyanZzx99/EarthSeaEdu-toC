package com.earthseaedu.backend.model.nickname;

import java.time.LocalDateTime;

public class NicknameRuleGroup {

    private Integer id;
    private String groupCode;
    private String groupName;
    private String groupType;
    private String scope;
    private String status;
    private Integer priority;
    private String description;
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

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
