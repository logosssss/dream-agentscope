package com.zhu.access.application.account;

import com.zhu.access.api.dto.account.QuotaMeter;
import com.zhu.access.api.dto.account.QuotaSnapshot;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.quota.QuotaUsageWriter;
import com.zhu.audit.api.quota.TenantQuotaLookup;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 对话进入执行内核之前的额度检查。重复请求先占幂等键，避免同一次提交把额度扣两次。
 * 请求次数在入口累加；token / 费用在出口记账后累加，入口只读已用量。
 */
@Service
public class QuotaGuard {

    private static final Logger log = Logger.getLogger(QuotaGuard.class.getName());

    /** 当日额度保留两天，跨日查询还能看到昨天的计数。 */
    public static final Duration QUOTA_TTL = Duration.ofHours(48);

    /** 重复提交的判断窗口。 */
    static final Duration IDEM_TTL = Duration.ofMinutes(10);

    /** 限流窗口长度，和键里的分钟对齐。 */
    static final Duration RATE_TTL = Duration.ofMinutes(1);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final AccessCache cache;

    private final AccessLimitProperties limits;

    private final Clock clock;

    /** MySQL 关闭时为空，当日上限只用进程配置。 */
    private final TenantQuotaLookup quotas;

    /** MySQL 关闭时为空。不限额的请求不写流水。 */
    private final QuotaUsageWriter usage;

    /**
     * 运行时用系统时钟。MySQL 打开时带上租户上限和流水，没有对应 bean 就跳过那一项。
     */
    @Autowired
    public QuotaGuard(
            AccessCache cache,
            AccessLimitProperties limits,
            ObjectProvider<TenantQuotaLookup> quotas,
            ObjectProvider<QuotaUsageWriter> usage) {
        this(cache, limits, Clock.systemUTC(), quotas.getIfAvailable(), usage.getIfAvailable());
    }

    /** 测试注入时钟，方便把窗口拨到下一分钟。不查租户上限，也不写流水。 */
    public QuotaGuard(AccessCache cache, AccessLimitProperties limits, Clock clock) {
        this(cache, limits, clock, null, null);
    }

    /** 测试注入租户上限。不写流水。 */
    public QuotaGuard(AccessCache cache, AccessLimitProperties limits, Clock clock, TenantQuotaLookup quotas) {
        this(cache, limits, clock, quotas, null);
    }

    /** 测试同时注入时钟、租户上限和流水。Spring 不用这个构造。 */
    public QuotaGuard(
            AccessCache cache,
            AccessLimitProperties limits,
            Clock clock,
            TenantQuotaLookup quotas,
            QuotaUsageWriter usage) {
        this.cache = cache;
        this.limits = limits;
        this.clock = clock;
        this.quotas = quotas;
        this.usage = usage;
    }

    /**
     * 限额为 0 的项目跳过。没有请求标识就不做幂等。
     * 租户有每日请求上限时用那一行，否则用进程配置。计数仍在 Redis。
     * 有 TOKENS / COST 行且已用量达到上限时拒绝对话；本次请求仍可在额度内跑完后记账。
     * 当日计数通过后才记流水。流水写失败不改变这次请求。超限的那一次不记。
     * 超限抛 QUOTA_EXCEEDED，重复标识抛 CONFLICT。
     */
    public void check(String tenantId, String userId, String endpoint, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            if (!cache.claim("idem:" + segment(requestId), IDEM_TTL)) {
                throw new BusinessException(ErrorCode.CONFLICT, "duplicate request");
            }
        }
        String user = segment(userId);
        String day = DAY.format(clock.instant());
        rejectIfTokenExceeded(tenantId, userId, day);
        rejectIfCostExceeded(tenantId, userId, day);
        long dailyLimit = limits.getDailyLimit();
        if (quotas != null) {
            Long override = quotas.dailyRequestLimit(tenantId);
            if (override != null) {
                dailyLimit = override;
            }
        }
        if (dailyLimit > 0) {
            long used = cache.increment(requestKey(tenantId, userId, day), QUOTA_TTL);
            if (used > dailyLimit) {
                throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "daily quota exceeded");
            }
            recordDailyRequest(tenantId, userId, endpoint);
        }
        int perMinute = limits.getPerMinute();
        if (perMinute > 0) {
            String window = MINUTE.format(clock.instant());
            long used = cache.increment("rate:" + segment(endpoint) + ":" + user + ":" + window, RATE_TTL);
            if (used > perMinute) {
                throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "rate limit exceeded");
            }
        }
    }

    /**
     * 注册/登录等未登录入口的分钟限流。键按动作和账号名（或匿名）分桶，不占对话日额度。
     * per-minute 为 0 时跳过。Redis 失败按不可用抛出。
     */
    public void checkAuthRate(String action, String accountKey) {
        int perMinute = limits.getPerMinute();
        if (perMinute < 1) {
            return;
        }
        String window = MINUTE.format(clock.instant());
        String key = "auth-rate:" + segment(action) + ":" + segment(accountKey) + ":" + window;
        long used = cache.increment(key, RATE_TTL);
        if (used > perMinute) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "rate limit exceeded");
        }
    }

    /**
     * 只读当日已用量和上限。不增加计数，也不写流水。
     * Redis 失败按不可用抛出，不改走内存。
     */
    public QuotaSnapshot snapshot(String tenantId, String userId) {
        String day = DAY.format(clock.instant());
        Long requestLimit = (long) limits.getDailyLimit();
        if (quotas != null) {
            Long override = quotas.dailyRequestLimit(tenantId);
            if (override != null) {
                requestLimit = override;
            }
        }
        Long tokenLimit = quotas == null ? null : quotas.dailyTokenLimit(tenantId);
        Long costLimit = quotas == null ? null : quotas.dailyCostLimit(tenantId);
        Long concurrentLimit = quotas == null ? null : quotas.concurrentRunLimit(tenantId);
        return new QuotaSnapshot(
                day,
                new QuotaMeter(cache.getCounter(requestKey(tenantId, userId, day)), requestLimit),
                new QuotaMeter(cache.getCounter(tokenKey(tenantId, userId, day)), tokenLimit),
                new QuotaMeter(cache.getCounter(costKey(tenantId, userId, day)), costLimit),
                new QuotaMeter(cache.getCounter(concurrentKey(tenantId)), concurrentLimit));
    }

    /**
     * 当日请求计数键。与 {@link #check} 共用。
     */
    public static String requestKey(String tenantId, String userId, String day) {
        return "quota:" + segment(tenantId) + ":" + segment(userId) + ":" + day;
    }

    /**
     * 同时运行占位键。与 {@link com.zhu.access.application.chat.run.ConcurrentRunGuard} 共用同一前缀。
     */
    public static String concurrentKey(String tenantId) {
        return "concurrent:" + segment(tenantId);
    }

    /**
     * 当日 token 计数键。与 {@link RedisDailyTokenAccumulator} 共用。
     */
    public static String tokenKey(String tenantId, String userId, String day) {
        return "quota-tokens:" + segment(tenantId) + ":" + segment(userId) + ":" + day;
    }

    /**
     * 当日费用微单位计数键。与 {@link RedisDailyCostAccumulator} 共用。
     */
    public static String costKey(String tenantId, String userId, String day) {
        return "quota-cost:" + segment(tenantId) + ":" + segment(userId) + ":" + day;
    }

    private void rejectIfTokenExceeded(String tenantId, String userId, String day) {
        if (quotas == null) {
            return;
        }
        Long tokenLimit = quotas.dailyTokenLimit(tenantId);
        if (tokenLimit == null || tokenLimit <= 0) {
            return;
        }
        long used = cache.getCounter(tokenKey(tenantId, userId, day));
        if (used >= tokenLimit) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "daily token quota exceeded");
        }
    }

    private void rejectIfCostExceeded(String tenantId, String userId, String day) {
        if (quotas == null) {
            return;
        }
        Long costLimit = quotas.dailyCostLimit(tenantId);
        if (costLimit == null || costLimit <= 0) {
            return;
        }
        long used = cache.getCounter(costKey(tenantId, userId, day));
        if (used >= costLimit) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "daily cost quota exceeded");
        }
    }

    /**
     * 流水失败只记一条警告。计数已经在 Redis 里，不能再把这次请求打成失败。
     */
    private void recordDailyRequest(String tenantId, String userId, String endpoint) {
        if (usage == null) {
            return;
        }
        try {
            usage.recordDailyRequest(tenantId, userId, endpoint);
        } catch (RuntimeException ex) {
            log.warning("quota usage record failed");
        }
    }

    /** 键的分段里不留冒号，避免租户标识把 quota 的四段拆乱。 */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(':', '_');
    }
}
