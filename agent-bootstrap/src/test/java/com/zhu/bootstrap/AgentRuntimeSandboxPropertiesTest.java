package com.zhu.bootstrap;

import com.zhu.runtime.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 Sandbox 配置骨架缺省值。不启动 Spring、不连 Docker。
 */
class AgentRuntimeSandboxPropertiesTest {

    /** 未配时默认关，镜像空串，推荐常量仍可用。 */
    @Test
    void sandboxDefaultsDisabledAndEmptyImage() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();

        assertThat(properties.getSandbox().isEnabled()).isFalse();
        assertThat(properties.getSandbox().getImage()).isEmpty();
        assertThat(AgentRuntimeProperties.Sandbox.DEFAULT_IMAGE).isEqualTo("ubuntu:24.04");
    }

    /** setSandbox(null) 回到默认关，避免热更清根后 NPE。 */
    @Test
    void setSandboxNullResetsToDisabled() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getSandbox().setEnabled(true);
        properties.setSandbox(null);

        assertThat(properties.getSandbox().isEnabled()).isFalse();
        assertThat(properties.getSandbox().getImage()).isEmpty();
    }
}
