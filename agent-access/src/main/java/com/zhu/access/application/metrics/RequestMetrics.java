package com.zhu.access.application.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 请求被拒的指标：哪一类闸门在拒、拒了多少。
 *
 * <p>为什么需要：`http.server.requests{status}` 只告诉你「有多少请求非 2xx」，
 * 分不出是额度用光、并发占满、还是会话被别人的账号占着——而这三件事的处理办法完全不同。
 *
 * <p>为什么按文案分类：闸门分散在 access / ops 两层，各自抛 {@code BusinessException}，
 * 统一出口只有 {@code AccessExceptionHandler}。与其给十几个构造函数加依赖，不如在出口按
 * **我们自己写的固定文案**归一次类；文案改了会掉进 {@code other}，由
 * {@code RequestMetricsTest} 钉住每一个已知文案，改名时当场红。
 */
@Component
public class RequestMetrics {

    /** 文案 → 原因。键就是我们抛出去的那句话，不含用户输入。 */
    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("rate limit exceeded", "rate_limit"),
            Map.entry("daily quota exceeded", "quota_daily"),
            Map.entry("daily token quota exceeded", "quota_tokens"),
            Map.entry("daily cost quota exceeded", "quota_cost"),
            Map.entry("concurrent run quota exceeded", "quota_concurrent"),
            Map.entry("duplicate request", "duplicate_request"),
            Map.entry("session belongs to another user", "session_owned"),
            Map.entry("tenant is not active", "tenant_inactive"),
            Map.entry("tenant user is not active", "tenant_user_inactive"),
            Map.entry("login is required", "unauthenticated"));

    /** 没有登记过的文案。数量本身也是信号：说明出现了一类没被建模的拒绝。 */
    public static final String OTHER = "other";

    private final MeterRegistry meters;

    /** 注册表可缺席：没引 actuator 时指标空转，异常处理照常。 */
    public RequestMetrics(ObjectProvider<MeterRegistry> meters) {
        this.meters = meters == null ? null : meters.getIfAvailable();
    }

    /**
     * 记一次拒绝。
     *
     * @param reason 由 {@link #reason(String)} 归类得到
     * @param code   错误码，例如 {@code QUOTA_EXCEEDED}
     */
    public void rejected(String reason, String code) {
        if (meters == null) {
            return;
        }
        try {
            meters.counter(
                            "agentscope.request.rejected",
                            "reason", text(reason),
                            "code", text(code))
                    .increment();
        } catch (RuntimeException ignored) {
            // 指标失败不影响响应。
        }
    }

    /** 按固定文案归类。没登记过的归 {@link #OTHER}，不猜。 */
    public static String reason(String message) {
        if (message == null) {
            return OTHER;
        }
        return REASONS.getOrDefault(message.trim(), OTHER);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
