package com.earthseaedu.backend.model.alevel;

import java.time.LocalDateTime;

public class AlevelQuestionOption {

    private Long alevelQuestionOptionId;
    private Long alevelQuestionId;
    private String optionKey;
    private String optionHtml;
    private String optionText;
    private String structureJson;
    private Integer sortOrder;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Long getAlevelQuestionOptionId() {
        return alevelQuestionOptionId;
    }

    public void setAlevelQuestionOptionId(Long alevelQuestionOptionId) {
        this.alevelQuestionOptionId = alevelQuestionOptionId;
    }

    public Long getAlevelQuestionId() {
        return alevelQuestionId;
    }

    public void setAlevelQuestionId(Long alevelQuestionId) {
        this.alevelQuestionId = alevelQuestionId;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public void setOptionKey(String optionKey) {
        this.optionKey = optionKey;
    }

    public String getOptionHtml() {
        return optionHtml;
    }

    public void setOptionHtml(String optionHtml) {
        this.optionHtml = optionHtml;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public String getStructureJson() {
        return structureJson;
    }

    public void setStructureJson(String structureJson) {
        this.structureJson = structureJson;
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
