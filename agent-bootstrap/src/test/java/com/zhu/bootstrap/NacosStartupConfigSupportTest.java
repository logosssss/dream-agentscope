package com.zhu.bootstrap;

import com.zhu.bootstrap.nacos.NacosAllowlist;
import com.zhu.bootstrap.nacos.NacosStartupConfig;
import com.zhu.bootstrap.nacos.NacosStartupConfigSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动与热更配置只收 nacos-allowlist.yml 里的键。不连接 Nacos。
 */
class NacosStartupConfigSupportTest {

    /** 白名单从 classpath yml 加载，业务键非空。 */
    @Test
    void allowlistLoadedFromClasspathYaml() {
        assertThat(NacosAllowlist.businessExact()).contains("agentscope.access.daily-limit");
        assertThat(NacosAllowlist.businessPrefixes()).contains("agentscope.permission.rules");
        assertThat(NacosAllowlist.infraExact()).contains(
                "spring.datasource.url",
                "spring.servlet.multipart.max-file-size",
                "spring.lifecycle.timeout-per-shutdown-phase",
                "spring.datasource.hikari.maximum-pool-size");
    }

    /** 业务允许的旋钮留下，数据源一类丢掉。 */
    @Test
    void businessKeepsOnlyAllowedKeys() {
        MapPropertySource source = NacosStartupConfigSupport.parseBusiness("""
                agentscope:
                  access:
                    daily-limit: 12
                    per-minute: 3
                  runtime:
                    model: dashscope:qwen-plus
                    max-iters: 8
                    timeout-seconds: 90
                    max-user-chars: 1000
                    max-write-bytes: 1024
                  auth:
                    tenant-id: demo
                  wx:
                    mini:
                      login-page: pages/login-confirm/index
                      env-version: trial
                      ticket-ttl-seconds: 120
                      app-id: should-drop
                  cache:
                    redis:
                      host: should-drop
                spring:
                  datasource:
                    url: jdbc:mysql://should-drop
                """);

        assertThat(source.getName()).isEqualTo(NacosStartupConfig.BUSINESS_PROPERTY_SOURCE);
        assertThat(source.getProperty("agentscope.access.daily-limit")).isEqualTo(12);
        assertThat(source.getProperty("agentscope.access.per-minute")).isEqualTo(3);
        assertThat(source.getProperty("agentscope.runtime.model")).isEqualTo("dashscope:qwen-plus");
        assertThat(source.getProperty("agentscope.runtime.max-iters")).isEqualTo(8);
        assertThat(source.getProperty("agentscope.runtime.timeout-seconds")).isEqualTo(90);
        assertThat(source.getProperty("agentscope.runtime.max-user-chars")).isEqualTo(1000);
        assertThat(source.getProperty("agentscope.runtime.max-write-bytes")).isEqualTo(1024);
        assertThat(source.getProperty("agentscope.auth.tenant-id")).isEqualTo("demo");
        assertThat(source.getProperty("agentscope.wx.mini.login-page")).isEqualTo("pages/login-confirm/index");
        assertThat(source.getProperty("agentscope.wx.mini.env-version")).isEqualTo("trial");
        assertThat(source.getProperty("agentscope.wx.mini.ticket-ttl-seconds")).isEqualTo(120);
        assertThat(source.getProperty("agentscope.wx.mini.app-id")).isNull();
        assertThat(source.getProperty("agentscope.cache.redis.host")).isNull();
        assertThat(source.getProperty("spring.datasource.url")).isNull();
    }

    /** permission.rules 列表展平键按前缀收下。 */
    @Test
    void businessKeepsPermissionRulePrefix() {
        MapPropertySource source = NacosStartupConfigSupport.parseBusiness("""
                agentscope:
                  permission:
                    rules:
                      - tool-id: shell
                        tenant-id: "*"
                        role-id: "*"
                        decision: DENY
                        priority: 1
                        enabled: true
                """);

        assertThat(source.getProperty("agentscope.permission.rules[0].tool-id")).isEqualTo("shell");
        assertThat(source.getProperty("agentscope.permission.rules[0].decision")).isEqualTo("DENY");
    }

    /** infra 收连接与密钥，丢掉业务旋钮。 */
    @Test
    void infraKeepsConnectionKeys() {
        MapPropertySource source = NacosStartupConfigSupport.parseInfra("""
                spring:
                  datasource:
                    url: jdbc:mysql://127.0.0.1:3306/agentscope
                    username: root
                    password: secret
                agentscope:
                  caller:
                    token: caller-token
                  cache:
                    redis:
                      host: 127.0.0.1
                      port: 6379
                  milvus:
                    uri: http://127.0.0.1:19530
                  access:
                    daily-limit: 9
                DASHSCOPE_API_KEY: sk-test
                """);

        assertThat(source.getName()).isEqualTo(NacosStartupConfig.INFRA_PROPERTY_SOURCE);
        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://127.0.0.1:3306/agentscope");
        assertThat(source.getProperty("agentscope.caller.token")).isEqualTo("caller-token");
        assertThat(source.getProperty("DASHSCOPE_API_KEY")).isEqualTo("sk-test");
        assertThat(source.getProperty("agentscope.access.daily-limit")).isNull();
    }

    /** 热更时全是白名单外键则拒绝。 */
    @Test
    void refreshRejectsWhenNoAllowedKeys() {
        assertThatThrownBy(() -> NacosStartupConfigSupport.parseBusinessRefresh("""
                spring:
                  datasource:
                    url: jdbc:mysql://should-reject
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no allowed keys");
    }

    /** 空白正文按启动失败。 */
    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> NacosStartupConfigSupport.parseBusiness(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> NacosStartupConfigSupport.parseInfra(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }
}
