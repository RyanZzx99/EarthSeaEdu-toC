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
    private final Mysql mysql = new Mysql();
    private final Ssh ssh = new Ssh();
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

    public Mysql getMysql() {
        return mysql;
    }

    public Ssh getSsh() {
        return ssh;
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

    public static class Mysql {
        private String host = "127.0.0.1";
        private int port = 3306;
        private String database = "earthseaedu";
        private String user = "root";
        private String password = "123456";
        private String charset = "utf8mb4";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }
    }

    public static class Ssh {
        private boolean enabled = false;
        private String host = "";
        private int port = 22;
        private String user = "";
        private String password = "";
        private String remoteBindHost = "127.0.0.1";
        private int remoteBindPort = 3306;
        private String localBindHost = "127.0.0.1";
        private int localBindPort = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRemoteBindHost() {
            return remoteBindHost;
        }

        public void setRemoteBindHost(String remoteBindHost) {
            this.remoteBindHost = remoteBindHost;
        }

        public int getRemoteBindPort() {
            return remoteBindPort;
        }

        public void setRemoteBindPort(int remoteBindPort) {
            this.remoteBindPort = remoteBindPort;
        }

        public String getLocalBindHost() {
            return localBindHost;
        }

        public void setLocalBindHost(String localBindHost) {
            this.localBindHost = localBindHost;
        }

        public int getLocalBindPort() {
            return localBindPort;
        }

        public void setLocalBindPort(int localBindPort) {
            this.localBindPort = localBindPort;
        }
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
        private int modelNonStreamRetryCount = 1;
        private double modelNonStreamRetryBackoffSeconds = 1.0;

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

        public int getModelNonStreamRetryCount() {
            return modelNonStreamRetryCount;
        }

        public void setModelNonStreamRetryCount(int modelNonStreamRetryCount) {
            this.modelNonStreamRetryCount = modelNonStreamRetryCount;
        }

        public double getModelNonStreamRetryBackoffSeconds() {
            return modelNonStreamRetryBackoffSeconds;
        }

        public void setModelNonStreamRetryBackoffSeconds(double modelNonStreamRetryBackoffSeconds) {
            this.modelNonStreamRetryBackoffSeconds = modelNonStreamRetryBackoffSeconds;
        }
    }
}
