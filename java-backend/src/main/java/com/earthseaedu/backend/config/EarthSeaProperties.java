package com.earthseaedu.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earthsea")
public class EarthSeaProperties {

    private String appName = "EarthSeaEdu Java API";
    private boolean appDebug = true;
    private String frontendLoginPageUrl = "http://localhost:5173/login";
    private String backendCorsOrigins = "*";
    private String examAssetRoot = "";
    private String importJobRoot = "";
    private String inviteAdminKey = "";
    private int smsCodeExpireMinutes = 5;
    private int smsSendCooldownSeconds = 60;
    private int smsDailyLimit = 10;
    private final Jwt jwt = new Jwt();
    private final TencentCloud tencentcloud = new TencentCloud();
    private final Wechat wechat = new Wechat();
    private final AiRuntime aiRuntime = new AiRuntime();

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isAppDebug() {
        return appDebug;
    }

    public void setAppDebug(boolean appDebug) {
        this.appDebug = appDebug;
    }

    public String getFrontendLoginPageUrl() {
        return frontendLoginPageUrl;
    }

    public void setFrontendLoginPageUrl(String frontendLoginPageUrl) {
        this.frontendLoginPageUrl = frontendLoginPageUrl;
    }

    public String getBackendCorsOrigins() {
        return backendCorsOrigins;
    }

    public void setBackendCorsOrigins(String backendCorsOrigins) {
        this.backendCorsOrigins = backendCorsOrigins;
    }

    public String getExamAssetRoot() {
        return examAssetRoot;
    }

    public void setExamAssetRoot(String examAssetRoot) {
        this.examAssetRoot = examAssetRoot;
    }

    public String getImportJobRoot() {
        return importJobRoot;
    }

    public void setImportJobRoot(String importJobRoot) {
        this.importJobRoot = importJobRoot;
    }

    public String getInviteAdminKey() {
        return inviteAdminKey;
    }

    public void setInviteAdminKey(String inviteAdminKey) {
        this.inviteAdminKey = inviteAdminKey;
    }

    public int getSmsCodeExpireMinutes() {
        return smsCodeExpireMinutes;
    }

    public void setSmsCodeExpireMinutes(int smsCodeExpireMinutes) {
        this.smsCodeExpireMinutes = smsCodeExpireMinutes;
    }

    public int getSmsSendCooldownSeconds() {
        return smsSendCooldownSeconds;
    }

    public void setSmsSendCooldownSeconds(int smsSendCooldownSeconds) {
        this.smsSendCooldownSeconds = smsSendCooldownSeconds;
    }

    public int getSmsDailyLimit() {
        return smsDailyLimit;
    }

    public void setSmsDailyLimit(int smsDailyLimit) {
        this.smsDailyLimit = smsDailyLimit;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public TencentCloud getTencentcloud() {
        return tencentcloud;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public AiRuntime getAiRuntime() {
        return aiRuntime;
    }

    public static class Jwt {
        private String secretKey = "replace_this_with_a_very_strong_secret_key";
        private String algorithm = "HS256";
        private int accessTokenExpireMinutes = 60 * 24 * 7;
        private int bindTokenExpireMinutes = 10;

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public int getAccessTokenExpireMinutes() {
            return accessTokenExpireMinutes;
        }

        public void setAccessTokenExpireMinutes(int accessTokenExpireMinutes) {
            this.accessTokenExpireMinutes = accessTokenExpireMinutes;
        }

        public int getBindTokenExpireMinutes() {
            return bindTokenExpireMinutes;
        }

        public void setBindTokenExpireMinutes(int bindTokenExpireMinutes) {
            this.bindTokenExpireMinutes = bindTokenExpireMinutes;
        }
    }

    public static class TencentCloud {
        private String secretId = "";
        private String secretKey = "";
        private String smsRegion = "ap-guangzhou";
        private String smsSdkAppId = "";
        private String smsSignName = "";
        private String smsTemplateLogin = "";
        private String smsTemplateBindMobile = "";
        private boolean smsMock = true;

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getSmsRegion() {
            return smsRegion;
        }

        public void setSmsRegion(String smsRegion) {
            this.smsRegion = smsRegion;
        }

        public String getSmsSdkAppId() {
            return smsSdkAppId;
        }

        public void setSmsSdkAppId(String smsSdkAppId) {
            this.smsSdkAppId = smsSdkAppId;
        }

        public String getSmsSignName() {
            return smsSignName;
        }

        public void setSmsSignName(String smsSignName) {
            this.smsSignName = smsSignName;
        }

        public String getSmsTemplateLogin() {
            return smsTemplateLogin;
        }

        public void setSmsTemplateLogin(String smsTemplateLogin) {
            this.smsTemplateLogin = smsTemplateLogin;
        }

        public String getSmsTemplateBindMobile() {
            return smsTemplateBindMobile;
        }

        public void setSmsTemplateBindMobile(String smsTemplateBindMobile) {
            this.smsTemplateBindMobile = smsTemplateBindMobile;
        }

        public boolean isSmsMock() {
            return smsMock;
        }

        public void setSmsMock(boolean smsMock) {
            this.smsMock = smsMock;
        }
    }

    public static class Wechat {
        private String openAppId = "";
        private String openAppSecret = "";
        private String openRedirectUri = "";

        public String getOpenAppId() {
            return openAppId;
        }

        public void setOpenAppId(String openAppId) {
            this.openAppId = openAppId;
        }

        public String getOpenAppSecret() {
            return openAppSecret;
        }

        public void setOpenAppSecret(String openAppSecret) {
            this.openAppSecret = openAppSecret;
        }

        public String getOpenRedirectUri() {
            return openRedirectUri;
        }

        public void setOpenRedirectUri(String openRedirectUri) {
            this.openRedirectUri = openRedirectUri;
        }
    }

    public static class AiRuntime {
        private String modelBaseUrl = "";
        private String modelApiKey = "";
        private String modelDefaultName = "";
        private int modelConnectTimeoutSeconds = 10;
        private int modelReadTimeoutSeconds = 300;
        private int modelStreamReadTimeoutSeconds = 300;
        private double modelDefaultTemperature = 0.3;

        public String getModelBaseUrl() {
            return modelBaseUrl;
        }

        public void setModelBaseUrl(String modelBaseUrl) {
            this.modelBaseUrl = modelBaseUrl;
        }

        public String getModelApiKey() {
            return modelApiKey;
        }

        public void setModelApiKey(String modelApiKey) {
            this.modelApiKey = modelApiKey;
        }

        public String getModelDefaultName() {
            return modelDefaultName;
        }

        public void setModelDefaultName(String modelDefaultName) {
            this.modelDefaultName = modelDefaultName;
        }

        public int getModelConnectTimeoutSeconds() {
            return modelConnectTimeoutSeconds;
        }

        public void setModelConnectTimeoutSeconds(int modelConnectTimeoutSeconds) {
            this.modelConnectTimeoutSeconds = modelConnectTimeoutSeconds;
        }

        public int getModelReadTimeoutSeconds() {
            return modelReadTimeoutSeconds;
        }

        public void setModelReadTimeoutSeconds(int modelReadTimeoutSeconds) {
            this.modelReadTimeoutSeconds = modelReadTimeoutSeconds;
        }

        public int getModelStreamReadTimeoutSeconds() {
            return modelStreamReadTimeoutSeconds;
        }

        public void setModelStreamReadTimeoutSeconds(int modelStreamReadTimeoutSeconds) {
            this.modelStreamReadTimeoutSeconds = modelStreamReadTimeoutSeconds;
        }

        public double getModelDefaultTemperature() {
            return modelDefaultTemperature;
        }

        public void setModelDefaultTemperature(double modelDefaultTemperature) {
            this.modelDefaultTemperature = modelDefaultTemperature;
        }
    }

}
