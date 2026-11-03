package com.zhu.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 现有小程序的出码参数。AppSecret 只从环境变量来，不写进仓库。
 */
@ConfigurationProperties(prefix = "agentscope.wx.mini")
public class WxMiniProperties {

    /** 小程序 AppID。空白表示扫码关闭。 */
    private String appId = "";

    /** 小程序密钥。空白表示扫码关闭。 */
    private String appSecret = "";

    /** 确认页路径。码里带的是票号，不是用户。 */
    private String loginPage = "pages/login-confirm/index";

    /** 小程序码的版本。正式环境用 release。 */
    private String envVersion = "release";

    /** 票的有效秒数。短于 60 或长于 600 时出码会拒绝。 */
    private int ticketTtlSeconds = 300;

    /** AppID。 */
    public String getAppId() {
        return appId;
    }

    /** 绑定 AppID。 */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** 密钥。 */
    public String getAppSecret() {
        return appSecret;
    }

    /** 绑定密钥。 */
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    /** 确认页。空白时用默认页，避免把码开到错误路径。 */
    public String getLoginPage() {
        if (loginPage == null || loginPage.isBlank()) {
            return "pages/login-confirm/index";
        }
        return loginPage.trim();
    }

    /** 绑定确认页。 */
    public void setLoginPage(String loginPage) {
        this.loginPage = loginPage;
    }

    /** 小程序码版本。 */
    public String getEnvVersion() {
        if (envVersion == null || envVersion.isBlank()) {
            return "release";
        }
        return envVersion.trim();
    }

    /** 绑定版本。 */
    public void setEnvVersion(String envVersion) {
        this.envVersion = envVersion;
    }

    /** 票的有效秒数。 */
    public int getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    /** 绑定有效秒数。 */
    public void setTicketTtlSeconds(int ticketTtlSeconds) {
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    /** 两边都有值才允许出码和确认。 */
    public boolean ready() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
