package com.zhu.bootstrap;

import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.AgentShutdown;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 停机等待时间跟单次调用超时走，并且停机前仍接受新调用。
 */
class AgentShutdownTest {

    /** 单例会留在进程里，每条用例结束后恢复，避免影响别的测试。 */
    @AfterEach
    void resetShutdown() {
        GracefulShutdownManager.getInstance().resetForTesting();
    }

    /** 配置的超时写进停机管理器。未完成的推理保留。 */
    @Test
    void usesCallTimeout() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setTimeoutSeconds(15);
        AgentShutdown shutdown = new AgentShutdown(properties);

        shutdown.run(new DefaultApplicationArguments());

        GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
        assertThat(manager.getConfig().shutdownTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(manager.getConfig().partialReasoningPolicy()).isEqualTo(PartialReasoningPolicy.SAVE);
        assertThat(manager.isAcceptingRequests()).isTrue();
        assertThat(shutdown.middleware()).isNotNull();
    }

    /** 超时小于 1 时仍等待 120 秒，避免配成 0 后立刻打断在途调用。 */
    @Test
    void zeroTimeoutFallsBack() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setTimeoutSeconds(0);
        new AgentShutdown(properties).run(new DefaultApplicationArguments());

        assertThat(GracefulShutdownManager.getInstance().getConfig().shutdownTimeout())
                .isEqualTo(Duration.ofSeconds(120));
    }
}
