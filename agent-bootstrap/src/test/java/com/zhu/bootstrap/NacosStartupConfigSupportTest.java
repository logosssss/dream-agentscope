package com.zhu.bootstrap;

import com.zhu.bootstrap.nacos.NacosStartupConfig;
import com.zhu.bootstrap.nacos.NacosStartupConfigSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动配置只收白名单键。不连接 Nacos。
 */
class NacosStartupConfigSupportTest {

    /** 允许的旋钮留下，数据源一类丢掉。 */
    @Test
    void keepsOnlyAllowedKeys() {
        MapPropertySource source = NacosStartupConfigSupport.parse("""
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
                  cache:
                    redis:
                      host: should-drop
                spring:
                  datasource:
                    url: jdbc:mysql://should-drop
                """);

        assertThat(source.getName()).isEqualTo(NacosStartupConfig.PROPERTY_SOURCE);
        assertThat(source.getProperty("agentscope.access.daily-limit")).isEqualTo(12);
        assertThat(source.getProperty("agentscope.access.per-minute")).isEqualTo(3);
        assertThat(source.getProperty("agentscope.runtime.model")).isEqualTo("dashscope:qwen-plus");
        assertThat(source.getProperty("agentscope.runtime.max-iters")).isEqualTo(8);
        assertThat(source.getProperty("agentscope.runtime.timeout-seconds")).isEqualTo(90);
        assertThat(source.getProperty("agentscope.runtime.max-user-chars")).isEqualTo(1000);
        assertThat(source.getProperty("agentscope.runtime.max-write-bytes")).isEqualTo(1024);
        assertThat(source.getProperty("agentscope.cache.redis.host")).isNull();
        assertThat(source.getProperty("spring.datasource.url")).isNull();
    }

    /** 空白正文按启动失败。 */
    @Test
    void blankContentRejected() {
        assertThatThrownBy(() -> NacosStartupConfigSupport.parse(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }
}
