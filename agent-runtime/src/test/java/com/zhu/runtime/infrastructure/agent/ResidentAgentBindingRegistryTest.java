package com.zhu.runtime.infrastructure.agent;

import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 产品 Binding 注册表校验：唯一 main、agentId 不冲突、不占用 echo。
 */
class ResidentAgentBindingRegistryTest {

    /** 正常：一个 main 可解析。 */
    @Test
    void acceptsSingleMain() {
        ResidentAgentBinding main = binding("companion-v1", true);
        ResidentAgentBindingRegistry registry = new ResidentAgentBindingRegistry(List.of(main));
        assertThat(registry.main()).isSameAs(main);
        assertThat(registry.all()).containsExactly(main);
    }

    /** 缺少 main 启动失败。 */
    @Test
    void rejectsMissingMain() {
        assertThatThrownBy(() -> new ResidentAgentBindingRegistry(List.of(binding("a", false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one main");
    }

    /** 占用 echo 标识失败。 */
    @Test
    void rejectsEchoId() {
        assertThatThrownBy(() -> new ResidentAgentBindingRegistry(
                        List.of(binding(HarnessAgentFactory.ECHO_AGENT_ID, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("echo");
    }

    private static ResidentAgentBinding binding(String agentId, boolean main) {
        return new ResidentAgentBinding() {
            @Override
            public String agentId() {
                return agentId;
            }

            @Override
            public boolean mainAgent() {
                return main;
            }

            @Override
            public void customize(HarnessAgent.Builder builder) {
                // 注册表测不装配。
            }
        };
    }
}
