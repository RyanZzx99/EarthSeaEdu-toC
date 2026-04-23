package com.earthseaedu.backend.model.alevel;

import java.time.LocalDateTime;

public class AlevelSourceFile {

    private Long alevelSourceFileId;
    private Long alevelPaperId;
    private String bundleCode;
    private String sourceFileType;
    private String sourceFileName;
    private String sourceFileHash;
    private String storagePath;
    private String assetUrl;
    private Integer pageCount;
    private String parseStatus;
    private String parseResultJson;
    private String parseWarningJson;
    private Integer importVersion;
    private Integer isVerified;
    private Integer status;
    private String errorMessage;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelSourceFileId() {
        return alevelSourceFileId;
    }

    public void setAlevelSourceFileId(Long alevelSourceFileId) {
        this.alevelSourceFileId = alevelSourceFileId;
    }

    public Long getAlevelPaperId() {
        return alevelPaperId;
    }

    public void setAlevelPaperId(Long alevelPaperId) {
        this.alevelPaperId = alevelPaperId;
    }

    public String getBundleCode() {
        return bundleCode;
    }

    public void setBundleCode(String bundleCode) {
        this.bundleCode = bundleCode;
    }

    public String getSourceFileType() {
        return sourceFileType;
    }

    public void setSourceFileType(String sourceFileType) {
        this.sourceFileType = sourceFileType;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceFileHash() {
        return sourceFileHash;
    }

    public void setSourceFileHash(String sourceFileHash) {
        this.sourceFileHash = sourceFileHash;
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

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseResultJson() {
        return parseResultJson;
    }

    public void setParseResultJson(String parseResultJson) {
        this.parseResultJson = parseResultJson;
    }

    public String getParseWarningJson() {
        return parseWarningJson;
    }

    public void setParseWarningJson(String parseWarningJson) {
        this.parseWarningJson = parseWarningJson;
    }

    public Integer getImportVersion() {
        return importVersion;
    }

    public void setImportVersion(Integer importVersion) {
        this.importVersion = importVersion;
    }

    public Integer getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Integer isVerified) {
        this.isVerified = isVerified;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
