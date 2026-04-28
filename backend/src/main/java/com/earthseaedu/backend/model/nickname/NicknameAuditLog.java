package com.earthseaedu.backend.model.nickname;

import java.time.LocalDateTime;

public class NicknameAuditLog {

    private Integer id;
    private String traceId;
    private String userId;
    private String scene;
    private String rawNickname;
    private String normalizedNickname;
    private String decision;
    private String hitSource;
    private Integer hitRuleId;
    private Integer hitPatternId;
    private String hitGroupCode;
    private String hitContent;
    private String message;
    private String clientIp;
    private String userAgent;
    private String appVersion;
    private String ruleVersionBatch;
    private String extraJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String deleteFlag;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getRawNickname() {
        return rawNickname;
    }

    public void setRawNickname(String rawNickname) {
        this.rawNickname = rawNickname;
    }

    public String getNormalizedNickname() {
        return normalizedNickname;
    }

    public void setNormalizedNickname(String normalizedNickname) {
        this.normalizedNickname = normalizedNickname;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getHitSource() {
        return hitSource;
    }

    public void setHitSource(String hitSource) {
        this.hitSource = hitSource;
    }

    public Integer getHitRuleId() {
        return hitRuleId;
    }

    public void setHitRuleId(Integer hitRuleId) {
        this.hitRuleId = hitRuleId;
    }

    public Integer getHitPatternId() {
        return hitPatternId;
    }

    public void setHitPatternId(Integer hitPatternId) {
        this.hitPatternId = hitPatternId;
    }

    public String getHitGroupCode() {
        return hitGroupCode;
    }

    public void setHitGroupCode(String hitGroupCode) {
        this.hitGroupCode = hitGroupCode;
    }

    public String getHitContent() {
        return hitContent;
    }

    public void setHitContent(String hitContent) {
        this.hitContent = hitContent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getRuleVersionBatch() {
        return ruleVersionBatch;
    }

    public void setRuleVersionBatch(String ruleVersionBatch) {
        this.ruleVersionBatch = ruleVersionBatch;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
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
