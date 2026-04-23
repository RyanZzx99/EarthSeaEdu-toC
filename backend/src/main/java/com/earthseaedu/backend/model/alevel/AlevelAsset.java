package com.earthseaedu.backend.model.alevel;

import java.time.LocalDateTime;

public class AlevelAsset {

    private Long alevelAssetId;
    private String ownerType;
    private Long ownerId;
    private String assetType;
    private String assetRole;
    private String assetName;
    private String sourcePath;
    private String storagePath;
    private String assetUrl;
    private String fileHash;
    private String mimeType;
    private Integer sourcePageNo;
    private String sourceBboxJson;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelAssetId() {
        return alevelAssetId;
    }

    public void setAlevelAssetId(Long alevelAssetId) {
        this.alevelAssetId = alevelAssetId;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getAssetRole() {
        return assetRole;
    }

    public void setAssetRole(String assetRole) {
        this.assetRole = assetRole;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getAssetUrl() {
        return assetUrl;
    }

    public void setAssetUrl(String assetUrl) {
        this.assetUrl = assetUrl;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
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
