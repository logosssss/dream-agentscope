package com.zhu.access.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 拒绝指标：按固定文案归类，按原因与错误码计数。
 *
 * <p>归类靠文案匹配，所以这里把闸门抛的每一句都钉住——改名时这条测试当场红，
 * 而不是 Grafana 面板静默变空。
 */
class RequestMetricsTest {

    /** 每个已知文案都要归到自己的原因，别掉进 other。 */
    @Test
    void knownMessagesMapToTheirReason() {
        assertThat(RequestMetrics.reason("rate limit exceeded")).isEqualTo("rate_limit");
        assertThat(RequestMetrics.reason("daily quota exceeded")).isEqualTo("quota_daily");
        assertThat(RequestMetrics.reason("daily token quota exceeded")).isEqualTo("quota_tokens");
        assertThat(RequestMetrics.reason("daily cost quota exceeded")).isEqualTo("quota_cost");
        assertThat(RequestMetrics.reason("concurrent run quota exceeded")).isEqualTo("quota_concurrent");
        assertThat(RequestMetrics.reason("duplicate request")).isEqualTo("duplicate_request");
        assertThat(RequestMetrics.reason("session belongs to another user")).isEqualTo("session_owned");
        assertThat(RequestMetrics.reason("tenant is not active")).isEqualTo("tenant_inactive");
        assertThat(RequestMetrics.reason("tenant user is not active")).isEqualTo("tenant_user_inactive");
        assertThat(RequestMetrics.reason("login is required")).isEqualTo("unauthenticated");
    }

    /** 没登记过的文案与空值都进 other，不猜。 */
    @Test
    void unknownMessagesFallIntoSafetyNet() {
        assertThat(RequestMetrics.reason("something new")).isEqualTo(RequestMetrics.OTHER);
        assertThat(RequestMetrics.reason(null)).isEqualTo(RequestMetrics.OTHER);
        assertThat(RequestMetrics.reason("  ")).isEqualTo(RequestMetrics.OTHER);
    }

    /** 计数带原因与错误码两个标签。 */
    @Test
    void rejectedCountsByReasonAndCode() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestMetrics metrics = new RequestMetrics(provider(registry));

        metrics.rejected(RequestMetrics.reason("concurrent run quota exceeded"), "QUOTA_EXCEEDED");
        metrics.rejected(RequestMetrics.reason("concurrent run quota exceeded"), "QUOTA_EXCEEDED");

        assertThat(registry.get("agentscope.request.rejected")
                        .tag("reason", "quota_concurrent")
                        .tag("code", "QUOTA_EXCEEDED")
                        .counter()
                        .count())
                .isEqualTo(2.0);
    }

    /** 没有注册表时空转，不抛。 */
    @Test
    void missingRegistryIsSilent() {
        RequestMetrics metrics = new RequestMetrics(provider(null));

        assertThatCode(() -> metrics.rejected("quota_daily", "QUOTA_EXCEEDED")).doesNotThrowAnyException();
    }

    private static ObjectProvider<MeterRegistry> provider(MeterRegistry registry) {
        return new ObjectProvider<>() {

            @Override
            public MeterRegistry getObject() {
                if (registry == null) {
                    throw new IllegalStateException("no registry");
                }
                return registry;
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }
        };
    }
}
