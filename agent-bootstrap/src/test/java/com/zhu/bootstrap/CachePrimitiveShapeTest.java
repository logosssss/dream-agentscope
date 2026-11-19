package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.cache.api.CacheKeys;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 二期原语形状：限流与布隆经门面可调用；不连真实 Redis，不接业务额度。
 */
class CachePrimitiveShapeTest {

    @Test
    void rateLimiterFixedWindow() {
        FakeCacheFacade cache = new FakeCacheFacade();
        String key = CacheKeys.ofCapability("rate", "shape-demo");
        assertThat(cache.rateLimiters().tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(cache.rateLimiters().tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(cache.rateLimiters().tryAcquire(key, 2, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void bloomFilterExactStandIn() {
        FakeCacheFacade cache = new FakeCacheFacade();
        String name = CacheKeys.ofCapability("bloom", "shape-demo");
        assertThat(cache.bloomFilters().tryInit(name, 100, 0.01)).isTrue();
        assertThat(cache.bloomFilters().tryInit(name, 100, 0.01)).isFalse();
        assertThat(cache.bloomFilters().mightContain(name, "a")).isFalse();
        cache.bloomFilters().add(name, "a");
        assertThat(cache.bloomFilters().mightContain(name, "a")).isTrue();
        assertThat(cache.bloomFilters().mightContain(name, "b")).isFalse();
    }
}
