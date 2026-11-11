package com.zhu.access.application.chat;

import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.TenantQuotaLookup;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.config.AgentRuntimeProperties;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 租户同时进行中的对话上限。计数在 Redis，对话结束就减一。
 * 没有行或上限 0 时不占位。进程崩溃后靠过期放开，避免永久占满。
 * TTL 跟运行超时配置走，热更后新占位用新值。
 */
@Service
public class ConcurrentRunGuard {

    /** 比单次调用超时多留的时间，避免对话还没结束键就过期。 */
    private static final Duration EXTRA = Duration.ofSeconds(30);

    private final AccessCache cache;

    private final AgentRuntimeProperties runtime;

    private final Integer fixedTimeoutSeconds;

    /** MySQL 关闭时为空，不限额。 */
    private final TenantQuotaLookup quotas;

    /**
     * 正式装配：超时从运行配置读取。
     */
    @Autowired
    public ConcurrentRunGuard(
            AccessCache cache,
            AgentRuntimeProperties runtime,
            ObjectProvider<TenantQuotaLookup> quotas) {
        this.cache = cache;
        this.runtime = runtime;
        this.fixedTimeoutSeconds = null;
        this.quotas = quotas.getIfAvailable();
    }

    /** 测试注入固定超时。没有上限时不占位。 */
    public ConcurrentRunGuard(AccessCache cache, int timeoutSeconds, TenantQuotaLookup quotas) {
        this.cache = cache;
        this.runtime = null;
        this.fixedTimeoutSeconds = timeoutSeconds < 1 ? 120 : timeoutSeconds;
        this.quotas = quotas;
    }

    /** 测试不查租户上限。 */
    public ConcurrentRunGuard(AccessCache cache, int timeoutSeconds) {
        this(cache, timeoutSeconds, (TenantQuotaLookup) null);
    }

    /** 当前占位 TTL。超时小于 1 时按 120 秒。 */
    private Duration ttl() {
        int seconds = fixedTimeoutSeconds != null
                ? fixedTimeoutSeconds
                : (runtime.getTimeoutSeconds() < 1 ? 120 : runtime.getTimeoutSeconds());
        return Duration.ofSeconds(seconds).plus(EXTRA);
    }

    /**
     * 占一个名额。超限抛 QUOTA_EXCEEDED。Redis 不可用时抛 UNAVAILABLE，不放行。
     */
    public Slot open(String tenantId) {
        if (quotas == null) {
            return Slot.idle();
        }
        Long limit = quotas.concurrentRunLimit(tenantId);
        if (limit == null || limit <= 0L) {
            return Slot.idle();
        }
        String key = "concurrent:" + segment(tenantId);
        Duration ttl = ttl();
        long used = cache.add(key, 1L, ttl);
        if (used > limit) {
            rollback(key);
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "concurrent run quota exceeded");
        }
        return new Slot(key);
    }

    /**
     * 归还名额。空槽或重复释放没有副作用。
     * Redis 不可用时吞掉：键会过期，不能把已经完成的回复盖成失败。
     */
    public void close(Slot slot) {
        if (slot == null || slot.key() == null || slot.key().isBlank()) {
            return;
        }
        try {
            cache.add(slot.key(), -1L, ttl());
        } catch (BusinessException error) {
            if (error.errorCode() != ErrorCode.UNAVAILABLE) {
                throw error;
            }
        }
    }

    private void rollback(String key) {
        try {
            cache.add(key, -1L, ttl());
        } catch (BusinessException error) {
            if (error.errorCode() != ErrorCode.UNAVAILABLE) {
                throw error;
            }
        }
    }

    /** 键的分段里不留冒号，避免租户标识把两段拆乱。 */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(':', '_');
    }

    /**
     * 一次占位。key 为空表示这次没有限额。
     *
     * @param key Redis 计数键，没有限额时是 null
     */
    public record Slot(String key) {

        /** 不占 Redis 的空槽。 */
        public static Slot idle() {
            return new Slot(null);
        }
    }
}
