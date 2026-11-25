package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 懒启动。不连 Redis，也不建 Gateway。
 */
class StartedChannelTest {

    /** 字段已经有值时不再启动。 */
    @Test
    void publishedValueSkipsStart() {
        AtomicInteger starts = new AtomicInteger();
        String value = StartedChannel.open(new Object(), () -> "chat", starts::incrementAndGet, "missing");
        assertThat(value).isEqualTo("chat");
        assertThat(starts.get()).isZero();
    }

    /** 启动后仍为空就按给定文案失败。启动自己抛出的异常不改写。 */
    @Test
    void missingAfterStartFails() {
        assertThatThrownBy(() -> StartedChannel.open(new Object(), () -> null, () -> {
        }, "channel gateway is not started"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("channel gateway is not started");
        assertThatThrownBy(() -> StartedChannel.open(new Object(), () -> null, () -> {
            throw new IllegalArgumentException("boom");
        }, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
    }

    /** 两个线程同时进来只启动一次，并且都拿到发布后的值。 */
    @Test
    void concurrentOpenStartsOnce() throws Exception {
        Object lock = new Object();
        AtomicInteger starts = new AtomicInteger();
        AtomicReference<String> published = new AtomicReference<>();
        Callable<String> open = () -> StartedChannel.open(lock, published::get, () -> {
            starts.incrementAndGet();
            try {
                Thread.sleep(40);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            published.set("api");
        }, "api channel gateway is not started");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> done = new ArrayList<>();
            done.add(pool.submit(open));
            done.add(pool.submit(open));
            assertThat(done.get(0).get()).isEqualTo("api");
            assertThat(done.get(1).get()).isEqualTo("api");
            assertThat(starts.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
