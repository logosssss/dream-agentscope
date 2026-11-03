package com.zhu.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端自助注册进入的租户。管理员不使用这项。
 */
@ConfigurationProperties(prefix = "agentscope.auth")
public class ClientAuthProperties {

    /** 必须已经登记且为 ACTIVE。空白时按 demo。 */
    private String tenantId = "demo";

    /** 去掉空白。空白配置按 demo，避免注册写进空租户。 */
    public String getTenantId() {
        if (tenantId == null || tenantId.isBlank()) {
            return "demo";
        }
        return tenantId.trim();
    }

    /** 绑定配置。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
