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

    /** 尚无允许键时解析成功，属性源为空，数据源一类键进不来。 */
    @Test
    void dropsAllKeysWhenWhitelistEmpty() {
        MapPropertySource source = NacosAdminStartupConfigSupport.parse("""
                agentscope:
                  admin:
                    note: startup-config-present
                  access:
                    daily-limit: 9
                spring:
                  datasource:
                    url: jdbc:mysql://should-drop
                """);

        assertThat(source.getName()).isEqualTo(NacosAdminStartupConfig.PROPERTY_SOURCE);
        assertThat(source.getPropertyNames()).isEmpty();
        assertThat(source.getProperty("agentscope.admin.note")).isNull();
        assertThat(source.getProperty("agentscope.access.daily-limit")).isNull();
        assertThat(source.getProperty("spring.datasource.url")).isNull();
    }

    /** 空白正文按启动失败。 */
    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> NacosAdminStartupConfigSupport.parse(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }
}
