package com.earthseaedu.backend.model.mockexam;

import java.time.LocalDateTime;

public class MockExamAnswerAsset {

    private Long mockexamAnswerAssetId;
    private String ownerType;
    private Long ownerId;
    private String userId;
    private Long mockexamPaperRefId;
    private Long mockexamQuestionRefId;
    private String assetType;
    private String fileName;
    private String mimeType;
    private String sourcePath;
    private String storagePath;
    private String assetUrl;
    private String fileHash;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getMockexamAnswerAssetId() {
        return mockexamAnswerAssetId;
    }

    public void setMockexamAnswerAssetId(Long mockexamAnswerAssetId) {
        this.mockexamAnswerAssetId = mockexamAnswerAssetId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getMockexamPaperRefId() {
        return mockexamPaperRefId;
    }

    public void setMockexamPaperRefId(Long mockexamPaperRefId) {
        this.mockexamPaperRefId = mockexamPaperRefId;
    }

    public Long getMockexamQuestionRefId() {
        return mockexamQuestionRefId;
    }

    public void setMockexamQuestionRefId(Long mockexamQuestionRefId) {
        this.mockexamQuestionRefId = mockexamQuestionRefId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
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
