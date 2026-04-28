package com.earthseaedu.backend.model.alevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AlevelPdfPage {

    private Long alevelPdfPageId;
    private Long alevelSourceFileId;
    private Long alevelPaperId;
    private Integer pageNo;
    private String pageLabel;
    private BigDecimal pageWidthPt;
    private BigDecimal pageHeightPt;
    private Integer rotation;
    private String textContent;
    private String textNormalized;
    private Long renderAssetId;
    private String renderStoragePath;
    private String renderAssetUrl;
    private String thumbnailAssetUrl;
    private Integer imageWidthPx;
    private Integer imageHeightPx;
    private Integer renderDpi;
    private String contentHash;
    private String extractionJson;
    private String renderStatus;
    private String errorMessage;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelPdfPageId() {
        return alevelPdfPageId;
    }

    public void setAlevelPdfPageId(Long alevelPdfPageId) {
        this.alevelPdfPageId = alevelPdfPageId;
    }

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

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public String getPageLabel() {
        return pageLabel;
    }

    public void setPageLabel(String pageLabel) {
        this.pageLabel = pageLabel;
    }

    public BigDecimal getPageWidthPt() {
        return pageWidthPt;
    }

    public void setPageWidthPt(BigDecimal pageWidthPt) {
        this.pageWidthPt = pageWidthPt;
    }

    public BigDecimal getPageHeightPt() {
        return pageHeightPt;
    }

    public void setPageHeightPt(BigDecimal pageHeightPt) {
        this.pageHeightPt = pageHeightPt;
    }

    public Integer getRotation() {
        return rotation;
    }

    public void setRotation(Integer rotation) {
        this.rotation = rotation;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getTextNormalized() {
        return textNormalized;
    }

    public void setTextNormalized(String textNormalized) {
        this.textNormalized = textNormalized;
    }

    public Long getRenderAssetId() {
        return renderAssetId;
    }

    public void setRenderAssetId(Long renderAssetId) {
        this.renderAssetId = renderAssetId;
    }

    public String getRenderStoragePath() {
        return renderStoragePath;
    }

    public void setRenderStoragePath(String renderStoragePath) {
        this.renderStoragePath = renderStoragePath;
    }

    public String getRenderAssetUrl() {
        return renderAssetUrl;
    }

    public void setRenderAssetUrl(String renderAssetUrl) {
        this.renderAssetUrl = renderAssetUrl;
    }

    public String getThumbnailAssetUrl() {
        return thumbnailAssetUrl;
    }

    public void setThumbnailAssetUrl(String thumbnailAssetUrl) {
        this.thumbnailAssetUrl = thumbnailAssetUrl;
    }

    public Integer getImageWidthPx() {
        return imageWidthPx;
    }

    public void setImageWidthPx(Integer imageWidthPx) {
        this.imageWidthPx = imageWidthPx;
    }

    public Integer getImageHeightPx() {
        return imageHeightPx;
    }

    public void setImageHeightPx(Integer imageHeightPx) {
        this.imageHeightPx = imageHeightPx;
    }

    public Integer getRenderDpi() {
        return renderDpi;
    }

    public void setRenderDpi(Integer renderDpi) {
        this.renderDpi = renderDpi;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getExtractionJson() {
        return extractionJson;
    }

    public void setExtractionJson(String extractionJson) {
        this.extractionJson = extractionJson;
    }

    public String getRenderStatus() {
        return renderStatus;
    }

    public void setRenderStatus(String renderStatus) {
        this.renderStatus = renderStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
