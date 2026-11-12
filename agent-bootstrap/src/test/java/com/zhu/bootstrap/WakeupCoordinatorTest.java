package com.zhu.bootstrap;

import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import com.zhu.access.application.chat.gateway.WakeupCoordinator;
import com.zhu.runtime.config.AgentRuntimeProperties;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.bus.MessageBus;
import io.agentscope.harness.agent.gateway.WakeupDispatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wakeup Coordinator：开关、令牌硬失败、enqueue、忙会话跳过。不连 Redis/MySQL/MQ。
 */
class WakeupCoordinatorTest {

    /** 默认关：enqueue 返回 false，不启 Dispatcher。 */
    @Test
    void disabledEnqueueReturnsFalse() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        WakeupCoordinator coordinator = new WakeupCoordinator(properties, emptyGateways(), "token-ok");
        coordinator.run(new DefaultApplicationArguments());

        assertThat(coordinator.isEnabled()).isFalse();
        assertThat(coordinator.enqueue("u", "s", "a").block(Duration.ofSeconds(2))).isFalse();
        coordinator.shutdown();
    }

    /** enabled=true 但令牌空白 → 启动硬失败。 */
    @Test
    void enabledWithoutTokenFailsHard() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWakeup().setEnabled(true);
        WakeupCoordinator coordinator = new WakeupCoordinator(properties, emptyGateways(), " ");

        assertThatThrownBy(() -> coordinator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
    }

    /** enabled=true 且令牌合法：enqueue 成功；忙会话由框架 Dispatcher 跳过。 */
    @Test
    void enabledEnqueueAndBusySessionSkip() throws Exception {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWakeup().setEnabled(true);
        WakeupCoordinator coordinator =
                new WakeupCoordinator(properties, emptyGateways(), "secure-wakeup-token");
        coordinator.run(new DefaultApplicationArguments());

        assertThat(coordinator.enqueue("u1", "busy-session", "companion-v1")
                .block(Duration.ofSeconds(2)))
                .isTrue();

        // 直接用同构 Bus + Dispatcher 验证忙跳过（与 Coordinator 内 Target 契约一致）。
        MessageBus bus = new com.zhu.runtime.infrastructure.wakeup.InMemoryMessageBus();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch idleDone = new CountDownLatch(1);
        List<String> seen = new ArrayList<>();
        WakeupDispatcher dispatcher = new WakeupDispatcher(bus, new WakeupDispatcher.WakeupTarget() {
            @Override
            public boolean isSessionRunning(String sessionId) {
                return "busy-session".equals(sessionId);
            }

            @Override
            public Mono<Msg> runWakeup(String sessionId) {
                runs.incrementAndGet();
                seen.add(sessionId);
                idleDone.countDown();
                return Mono.empty();
            }
        });
        dispatcher.start();
        bus.enqueueWakeup("u", "busy-session", "a").block(Duration.ofSeconds(2));
        bus.enqueueWakeup("u", "idle-session", "a").block(Duration.ofSeconds(2));
        assertThat(idleDone.await(3, TimeUnit.SECONDS)).isTrue();
        // 给 busy 跳过一点时间；最终只应跑 idle。
        Thread.sleep(200);
        assertThat(seen).containsExactly("idle-session");
        assertThat(runs.get()).isEqualTo(1);
        dispatcher.close();
        bus.close();
        coordinator.shutdown();
    }

    private static ObjectProvider<AccessChannelGateway> emptyGateways() {
        return new ObjectProvider<>() {
            @Override
            public AccessChannelGateway getObject() {
                return null;
            }

            @Override
            public AccessChannelGateway getObject(Object... args) {
                return null;
            }

            @Override
            public AccessChannelGateway getIfAvailable() {
                return null;
            }

            @Override
            public AccessChannelGateway getIfUnique() {
                return null;
            }
        };
    }
}
