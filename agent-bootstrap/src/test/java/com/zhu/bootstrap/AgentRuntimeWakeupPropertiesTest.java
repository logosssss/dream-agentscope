package com.zhu.bootstrap;

import com.zhu.runtime.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 Wakeup 配置骨架缺省值。不启动 Dispatcher、不连 MessageBus。
 */
class AgentRuntimeWakeupPropertiesTest {

    /** 未配时默认关。 */
    @Test
    void wakeupDefaultsDisabled() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        assertThat(properties.getWakeup().isEnabled()).isFalse();
    }

    /** setWakeup(null) 回到默认关，避免热更清根后 NPE。 */
    @Test
    void setWakeupNullResetsToDisabled() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWakeup().setEnabled(true);
        properties.setWakeup(null);
        assertThat(properties.getWakeup().isEnabled()).isFalse();
    }
}
