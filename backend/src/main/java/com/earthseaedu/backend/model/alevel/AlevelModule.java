package com.earthseaedu.backend.model.alevel;

import java.time.LocalDateTime;

public class AlevelModule {

    private Long alevelModuleId;
    private Long alevelPaperId;
    private String moduleCode;
    private String moduleName;
    private String moduleType;
    private String instructionsHtml;
    private String instructionsText;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelModuleId() {
        return alevelModuleId;
    }

    public void setAlevelModuleId(Long alevelModuleId) {
        this.alevelModuleId = alevelModuleId;
    }

    public Long getAlevelPaperId() {
        return alevelPaperId;
    }

    public void setAlevelPaperId(Long alevelPaperId) {
        this.alevelPaperId = alevelPaperId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getInstructionsHtml() {
        return instructionsHtml;
    }

    public void setInstructionsHtml(String instructionsHtml) {
        this.instructionsHtml = instructionsHtml;
    }

    public String getInstructionsText() {
        return instructionsText;
    }

    public void setInstructionsText(String instructionsText) {
        this.instructionsText = instructionsText;
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
