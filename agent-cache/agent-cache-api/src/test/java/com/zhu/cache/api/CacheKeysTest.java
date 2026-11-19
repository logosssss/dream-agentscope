package com.zhu.cache.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 键拼接约定：前缀、租户段、跳过空白。
 */
class CacheKeysTest {

    @Test
    void ofCapabilityWithoutTenant() {
        assertThat(CacheKeys.ofCapability("quota", "day", "u1")).isEqualTo("agentscope:quota:day:u1");
    }

    @Test
    void ofWithTenant() {
        assertThat(CacheKeys.of("lock", "t1", "session", "s1"))
                .isEqualTo("agentscope:lock:t1:session:s1");
    }

    @Test
    void blankTenantSkipped() {
        assertThat(CacheKeys.of("kv", "  ", "list")).isEqualTo("agentscope:kv:list");
    }

    @Test
    void blankPartsSkipped() {
        assertThat(CacheKeys.of("kv", null, "a", "", "b")).isEqualTo("agentscope:kv:a:b");
    }

    /** 微信 access_token：平台级，无租户段，按 AppID 隔离。 */
    @Test
    void wxAccessTokenKey() {
        assertThat(CacheKeys.ofCapability("wx", "access-token", "wxapp-1"))
                .isEqualTo("agentscope:wx:access-token:wxapp-1");
    }

    /** 审计读穿：门禁与额度上限键形。 */
    @Test
    void auditGateAndQuotaLimitKeys() {
        assertThat(CacheKeys.of("gate", "t1", "tenant-active"))
                .isEqualTo("agentscope:gate:t1:tenant-active");
        assertThat(CacheKeys.of("gate", "t1", "user-active", "u1"))
                .isEqualTo("agentscope:gate:t1:user-active:u1");
        assertThat(CacheKeys.of("quota-limit", "t1", "REQUESTS"))
                .isEqualTo("agentscope:quota-limit:t1:REQUESTS");
    }
}
