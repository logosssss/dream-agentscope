package com.zhu.admin;

import com.zhu.admin.nacos.NacosAdminStartupConfig;
import com.zhu.admin.nacos.NacosAdminStartupConfigSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理进程启动配置：正文必须非空，白名单外键丢掉。不连接 Nacos。
 */
class NacosAdminStartupConfigSupportTest {

    /** 业务白名单为空时解析成功，属性源为空，数据源一类键进不来。 */
    @Test
    void dropsAllKeysWhenBusinessWhitelistEmpty() {
        MapPropertySource source = NacosAdminStartupConfigSupport.parseBusiness("""
                agentscope:
                  admin:
                    note: startup-config-present
                  access:
                    daily-limit: 9
                spring:
                  datasource:
                    url: jdbc:mysql://should-drop
                """);

        assertThat(source.getName()).isEqualTo(NacosAdminStartupConfig.BUSINESS_PROPERTY_SOURCE);
        assertThat(source.getPropertyNames()).isEmpty();
        assertThat(source.getProperty("agentscope.admin.note")).isNull();
        assertThat(source.getProperty("agentscope.access.daily-limit")).isNull();
        assertThat(source.getProperty("spring.datasource.url")).isNull();
    }

    /** infra 只收连接与管理令牌。 */
    @Test
    void infraKeepsConnectionKeys() {
        MapPropertySource source = NacosAdminStartupConfigSupport.parseInfra("""
                spring:
                  datasource:
                    url: jdbc:mysql://127.0.0.1:3306/agentscope
                    username: root
                    password: secret
                agentscope:
                  admin:
                    token: admin-token
                  cache:
                    redis:
                      host: 127.0.0.1
                      port: 6379
                  milvus:
                    uri: http://127.0.0.1:19530
                  access:
                    daily-limit: 1
                """);

        assertThat(source.getName()).isEqualTo(NacosAdminStartupConfig.INFRA_PROPERTY_SOURCE);
        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://127.0.0.1:3306/agentscope");
        assertThat(source.getProperty("agentscope.admin.token")).isEqualTo("admin-token");
        assertThat(source.getProperty("agentscope.cache.redis.host")).isEqualTo("127.0.0.1");
        assertThat(source.getProperty("agentscope.milvus.uri")).isEqualTo("http://127.0.0.1:19530");
        assertThat(source.getProperty("agentscope.access.daily-limit")).isNull();
    }

    /** 空白正文按启动失败。 */
    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> NacosAdminStartupConfigSupport.parseBusiness(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> NacosAdminStartupConfigSupport.parseInfra(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }
}
