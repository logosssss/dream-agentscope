package com.zhu.runtime.application.usage;

import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.api.usage.DailyTokenAccumulator;
import com.zhu.runtime.api.usage.TokenUsageWriter;
import com.zhu.runtime.api.usage.UsageMeter;
import com.zhu.runtime.application.metrics.TurnMetrics;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 把模型用量写成一条审计，并写入 token 用量表。
 * 有当日 token 累加器时再加一把 Redis 计数，供下一次对话入口校验。
 * 明细只有模型名和数字，不放提示词或回复正文。
 */
@Service
public class AuditUsageMeter implements UsageMeter {

    private static final Logger log = Logger.getLogger(AuditUsageMeter.class.getName());

    private final AuditRecorder audits;

    private final TokenUsageWriter tokens;

    private final DailyTokenAccumulator dailyTokens;

    private final TurnMetrics metrics;

    /**
     * 累加器与指标都可缺省。各路失败互不影响，也不影响回复。
     * 唯一公开构造由 Spring 用，避免它在多构造之间退回无参。
     */
    @Autowired
    public AuditUsageMeter(
            AuditRecorder audits,
            TokenUsageWriter tokens,
            @Nullable DailyTokenAccumulator dailyTokens,
            @Nullable TurnMetrics metrics) {
        this.audits = audits;
        this.tokens = tokens;
        this.dailyTokens = dailyTokens;
        this.metrics = metrics;
    }

    /** 不带指标的构造：单测与没有 actuator 的装配用。 */
    public AuditUsageMeter(
            AuditRecorder audits, TokenUsageWriter tokens, @Nullable DailyTokenAccumulator dailyTokens) {
        this(audits, tokens, dailyTokens, null);
    }

    /**
     * 记账失败只记日志。用量写失败不能把已经生成的回复变成失败。
     */
    @Override
    public void onCall(
            String tenantId,
            String userId,
            String sessionId,
            String runId,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMillis) {
        try {
            audits.record(
                    "model.usage",
                    userId == null ? "" : userId,
                    sessionId,
                    runId,
                    detail(model, inputTokens, outputTokens, latencyMillis));
        } catch (RuntimeException ex) {
            log.warning("usage audit failed: " + ex.getMessage());
        }
        try {
            tokens.record(tenantId, userId, sessionId, runId, model, inputTokens, outputTokens);
        } catch (RuntimeException ex) {
            log.warning("usage record failed: " + ex.getMessage());
        }
        if (dailyTokens != null) {
            try {
                dailyTokens.add(tenantId, userId, safeSum(inputTokens, outputTokens));
            } catch (RuntimeException ex) {
                log.warning("daily token accumulate failed: " + ex.getMessage());
            }
        }
        if (metrics != null) {
            // 指标放在最后：库写失败也已经发生过，指标照样要能看出「这一轮烧了多少」。
            metrics.modelCall(model, latencyMillis, inputTokens, outputTokens);
        }
    }

    private static int safeSum(int inputTokens, int outputTokens) {
        long sum = (long) Math.max(0, inputTokens) + (long) Math.max(0, outputTokens);
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    /** 拼成一行，模型名里的换行去掉，避免审计明细被拆开。 */
    static String detail(String model, int inputTokens, int outputTokens, long latencyMillis) {
        String name = model == null ? "" : model.replace('\r', ' ').replace('\n', ' ');
        return "model=" + name
                + " inputTokens=" + inputTokens
                + " outputTokens=" + outputTokens
                + " latencyMillis=" + latencyMillis;
    }
}
