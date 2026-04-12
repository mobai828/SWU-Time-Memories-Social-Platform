package com.example.logininterface.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String uploadDir = "./data/uploads";
    private String defaultLoginBackground = "https://images.unsplash.com/photo-1495344517868-8ebaf0a2044a?q=80&w=2560&auto=format&fit=crop";
    private int resetTokenMinutes = 30;
    private SwuIdm swuIdm = new SwuIdm();
    private Admin admin = new Admin();
    private BaiduCensor baiduCensor = new BaiduCensor();

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getDefaultLoginBackground() {
        return defaultLoginBackground;
    }

    public void setDefaultLoginBackground(String defaultLoginBackground) {
        this.defaultLoginBackground = defaultLoginBackground;
    }

    public int getResetTokenMinutes() {
        return resetTokenMinutes;
    }

    public void setResetTokenMinutes(int resetTokenMinutes) {
        this.resetTokenMinutes = resetTokenMinutes;
    }

    public SwuIdm getSwuIdm() {
        return swuIdm;
    }

    public void setSwuIdm(SwuIdm swuIdm) {
        this.swuIdm = swuIdm;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public BaiduCensor getBaiduCensor() {
        return baiduCensor;
    }

    public void setBaiduCensor(BaiduCensor baiduCensor) {
        this.baiduCensor = baiduCensor;
    }

    public static class BaiduCensor {
        private String appId;
        private String apiKey;
        private String secretKey;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class SwuIdm {
        private String loginUrl = "https://uaaap.swu.edu.cn/cas/login";
        private String logoutUrl = "https://uaaap.swu.edu.cn/cas/logout";
        private String serviceValidateUrl = "https://uaaap.swu.edu.cn/cas/serviceValidate";
        private String callbackPath = "/login/swu/callback";
        private boolean forceRenew = true;
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 15;

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getLogoutUrl() {
            return logoutUrl;
        }

        public void setLogoutUrl(String logoutUrl) {
            this.logoutUrl = logoutUrl;
        }

        public String getServiceValidateUrl() {
            return serviceValidateUrl;
        }

        public void setServiceValidateUrl(String serviceValidateUrl) {
            this.serviceValidateUrl = serviceValidateUrl;
        }

        public String getCallbackPath() {
            return callbackPath;
        }

        public void setCallbackPath(String callbackPath) {
            this.callbackPath = callbackPath;
        }

        public boolean isForceRenew() {
            return forceRenew;
        }

        public void setForceRenew(boolean forceRenew) {
            this.forceRenew = forceRenew;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }
    }

    public static class Admin {
        private String username = "admin";
        private String email = "admin@example.com";
        private String password = "Admin123456";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
