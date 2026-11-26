package com.zhu.runtime.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 指标埋点：模型调用与一轮对话的结局。
 *
 * <p>这些数在库里的行看不到——线上要回答「模型慢不慢、失败率多少、token 烧得多快」，
 * 只能靠它们。所以这里连标签一起断言，避免改名后 Grafana 面板静默变空。
 */
class TurnMetricsTest {

    /** 模型调用：一条耗时 + 两条 token 计数，标签是模型坐标与方向。 */
    @Test
    void modelCallRecordsLatencyAndTokens() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnMetrics metrics = new TurnMetrics(provider(registry));

        metrics.modelCall("dashscope:qwen-plus", 120, 30, 40);

        assertThat(registry.get("agentscope.model.latency")
                        .tag("model", "dashscope:qwen-plus")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("agentscope.model.tokens")
                        .tag("model", "dashscope:qwen-plus")
                        .tag("direction", "input")
                        .counter()
                        .count())
                .isEqualTo(30);
        assertThat(registry.get("agentscope.model.tokens")
                        .tag("model", "dashscope:qwen-plus")
                        .tag("direction", "output")
                        .counter()
                        .count())
                .isEqualTo(40);
    }

    /** 一轮对话：按通道与结局各一条计数，耗时单独一条。 */
    @Test
    void turnRecordsOutcomeByChannel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnMetrics metrics = new TurnMetrics(provider(registry));

        metrics.turn(TurnMetrics.CHANNEL_CHATUI, TurnMetrics.TURN_OK, 5_000_000L);
        metrics.turn(TurnMetrics.CHANNEL_API, TurnMetrics.TURN_ERROR, 1_000_000L);

        assertThat(registry.get("agentscope.turn")
                        .tag("channel", "chatui")
                        .tag("outcome", "ok")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("agentscope.turn")
                        .tag("channel", "api")
                        .tag("outcome", "error")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("agentscope.turn.duration")
                        .tag("channel", "chatui")
                        .timer()
                        .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(5.0);
    }

    /** 空标签折成 unknown，避免时序库里出现无名序列。 */
    @Test
    void blankTagsBecomeUnknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurnMetrics metrics = new TurnMetrics(provider(registry));

        metrics.turn("  ", null, 1);

        assertThat(registry.get("agentscope.turn")
                        .tag("channel", "unknown")
                        .tag("outcome", "unknown")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    /** 没有注册表（单测、或进程没引 actuator）时空转，不抛。 */
    @Test
    void missingRegistryIsSilent() {
        TurnMetrics metrics = new TurnMetrics(provider(null));

        assertThatCode(() -> {
            metrics.turn(TurnMetrics.CHANNEL_CHATUI, TurnMetrics.TURN_OK, 1);
            metrics.modelCall("dashscope:qwen-plus", 1, 1, 1);
        }).doesNotThrowAnyException();
    }

    /** 只实现取用得到的两个方法，其余走 ObjectProvider 的默认实现。 */
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
