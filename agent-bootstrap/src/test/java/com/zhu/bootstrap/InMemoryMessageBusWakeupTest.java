package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.wakeup.InMemoryMessageBus;
import io.agentscope.harness.agent.bus.BusEntry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 进程内 Bus 的 enqueueWakeup / subscribeWakeup 契约。不启 Dispatcher、不连外部中间件。
 */
class InMemoryMessageBusWakeupTest {

    /** enqueue 后队列可读，并往 wakeup_signal 发信号。 */
    @Test
    void enqueueWakeupPushesQueueAndSignal() {
        InMemoryMessageBus bus = new InMemoryMessageBus();
        AtomicInteger signals = new AtomicInteger();
        Disposable sub = bus.subscribeWakeup().subscribe(payload -> signals.incrementAndGet());

        bus.enqueueWakeup("u1", "s1", "companion-v1").block(Duration.ofSeconds(2));

        List<BusEntry> drained = bus.queueDrain("agentscope:wakeups", 8).block(Duration.ofSeconds(2));
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).payload()).containsEntry("userId", "u1");
        assertThat(drained.get(0).payload()).containsEntry("sessionId", "s1");
        assertThat(drained.get(0).payload()).containsEntry("agentId", "companion-v1");
        assertThat(signals.get()).isEqualTo(1);

        sub.dispose();
        bus.close();
    }

    /** 关闭后再操作抛错。 */
    @Test
    void closedBusRejectsOps() {
        InMemoryMessageBus bus = new InMemoryMessageBus();
        bus.close();
        assertThatThrownBy(() -> bus.queuePush("k", java.util.Map.of()).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
