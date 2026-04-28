package com.earthseaedu.backend.model.nickname;

import java.time.LocalDateTime;

public class NicknameRulePublishLog {

    private Integer id;
    private String publishBatchNo;
    private String scope;
    private String changeSummary;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String snapshotJson;
    private String rollbackBatchNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPublishBatchNo() {
        return publishBatchNo;
    }

    public void setPublishBatchNo(String publishBatchNo) {
        this.publishBatchNo = publishBatchNo;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(String publishedBy) {
        this.publishedBy = publishedBy;
    }

    public LocalDateTime getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(LocalDateTime publishedTime) {
        this.publishedTime = publishedTime;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getRollbackBatchNo() {
        return rollbackBatchNo;
    }

    public void setRollbackBatchNo(String rollbackBatchNo) {
        this.rollbackBatchNo = rollbackBatchNo;
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
