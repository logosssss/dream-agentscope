package com.zhu.runtime.application.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 运行层的指标埋点：模型调用与一轮对话的结局。
 *
 * <p>为什么单独一个类：这两件事现在在库里只有「行」，线上想回答「模型慢不慢、失败率多少、
 * token 烧得多快」只能翻审计表。HTTP 维度的东西由 actuator 自带的
 * {@code http.server.requests} 补，这里只补它看不见的两类。
 *
 * <p>标签只放低基数的值（模型坐标、通道、结局），不放租户或用户——那会把时序库撑爆。
 *
 * <p>没有 {@link MeterRegistry}（只跑单测、或进程没引 actuator）时全部空转：
 * 记账与对话都不允许因为指标失败。
 */
@Component
public class TurnMetrics {

    /** 一轮对话正常走完。 */
    public static final String TURN_OK = "ok";

    /** 一轮对话抛错。 */
    public static final String TURN_ERROR = "error";

    /** 流式被取消。 */
    public static final String TURN_CANCELLED = "cancelled";

    /** 通道名：页面对话。与 {@code ChannelGateway} 的 ChatUi 一致。 */
    public static final String CHANNEL_CHATUI = "chatui";

    /** 通道名：API 对话。 */
    public static final String CHANNEL_API = "api";

    private final MeterRegistry meters;

    /** 注册表可缺席：没引 actuator 的进程照常跑，指标空转。 */
    public TurnMetrics(ObjectProvider<MeterRegistry> meters) {
        this.meters = meters == null ? null : meters.getIfAvailable();
    }

    /**
     * 记一轮对话的耗时与结局。
     *
     * @param channel       通道：{@link #CHANNEL_CHATUI} / {@link #CHANNEL_API}
     * @param outcome       {@link #TURN_OK} / {@link #TURN_ERROR} / {@link #TURN_CANCELLED}
     * @param durationNanos 这一轮从进执行缝到结束的耗时
     */
    public void turn(String channel, String outcome, long durationNanos) {
        if (meters == null) {
            return;
        }
        try {
            String ch = text(channel);
            String result = text(outcome);
            meters.counter("agentscope.turn", "channel", ch, "outcome", result).increment();
            meters.timer("agentscope.turn.duration", "channel", ch, "outcome", result)
                    .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 指标失败不影响对话。
        }
    }

    /**
     * 记一次模型调用的耗时与 token。租户/用户不进标签。
     *
     * @param model         模型坐标，如 {@code dashscope:qwen-plus}
     * @param latencyMillis 这一轮的耗时
     * @param inputTokens   输入 token
     * @param outputTokens  输出 token
     */
    public void modelCall(String model, long latencyMillis, int inputTokens, int outputTokens) {
        if (meters == null) {
            return;
        }
        try {
            String name = text(model);
            meters.timer("agentscope.model.latency", "model", name)
                    .record(Math.max(0L, latencyMillis), TimeUnit.MILLISECONDS);
            meters.counter("agentscope.model.tokens", "model", name, "direction", "input")
                    .increment(Math.max(0, inputTokens));
            meters.counter("agentscope.model.tokens", "model", name, "direction", "output")
                    .increment(Math.max(0, outputTokens));
        } catch (RuntimeException ignored) {
            // 指标失败不影响记账。
        }
    }

    /** 空标签会让时序库出现一条无名序列，统一折成 unknown。 */
    private static String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
