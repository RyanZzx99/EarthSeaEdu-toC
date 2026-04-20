package com.earthseaedu.backend.model.auth;

import java.time.LocalDateTime;

public class InviteCode {

    private Integer id;
    private String code;
    private String inviteScene;
    private String status;
    private String issuedToMobile;
    private String issuedByUserId;
    private String usedByUserId;
    private LocalDateTime issuedTime;
    private LocalDateTime usedTime;
    private LocalDateTime expiresTime;
    private String note;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getInviteScene() {
        return inviteScene;
    }

    public void setInviteScene(String inviteScene) {
        this.inviteScene = inviteScene;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssuedToMobile() {
        return issuedToMobile;
    }

    public void setIssuedToMobile(String issuedToMobile) {
        this.issuedToMobile = issuedToMobile;
    }

    public String getIssuedByUserId() {
        return issuedByUserId;
    }

    public void setIssuedByUserId(String issuedByUserId) {
        this.issuedByUserId = issuedByUserId;
    }

    public String getUsedByUserId() {
        return usedByUserId;
    }

    public void setUsedByUserId(String usedByUserId) {
        this.usedByUserId = usedByUserId;
    }

    public LocalDateTime getIssuedTime() {
        return issuedTime;
    }

    public void setIssuedTime(LocalDateTime issuedTime) {
        this.issuedTime = issuedTime;
    }

    public LocalDateTime getUsedTime() {
        return usedTime;
    }

    public void setUsedTime(LocalDateTime usedTime) {
        this.usedTime = usedTime;
    }

    public LocalDateTime getExpiresTime() {
        return expiresTime;
    }

    public void setExpiresTime(LocalDateTime expiresTime) {
        this.expiresTime = expiresTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
