package com.zhu.access.application;

import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 对话进入执行内核之前的额度检查。重复请求先占幂等键，避免同一次提交把额度扣两次。
 */
@Service
public class QuotaGuard {

    /** 当日额度保留两天，跨日查询还能看到昨天的计数。 */
    static final Duration QUOTA_TTL = Duration.ofHours(48);

    /** 重复提交的判断窗口。 */
    static final Duration IDEM_TTL = Duration.ofMinutes(10);

    /** 限流窗口长度，和键里的分钟对齐。 */
    static final Duration RATE_TTL = Duration.ofMinutes(1);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final AccessCache cache;

    private final AccessLimitProperties limits;

    private final Clock clock;

    /** 运行时用系统时钟。带时钟的构造留给测试。 */
    @Autowired
    public QuotaGuard(AccessCache cache, AccessLimitProperties limits) {
        this(cache, limits, Clock.systemUTC());
    }

    /** 测试注入时钟，方便把窗口拨到下一分钟。Spring 不用这个构造。 */
    public QuotaGuard(AccessCache cache, AccessLimitProperties limits, Clock clock) {
        this.cache = cache;
        this.limits = limits;
        this.clock = clock;
    }

    /**
     * 限额为 0 的项目跳过。没有请求标识就不做幂等。
     * 超限抛 QUOTA_EXCEEDED，重复标识抛 CONFLICT。
     */
    public void check(String tenantId, String userId, String endpoint, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            if (!cache.claim("idem:" + segment(requestId), IDEM_TTL)) {
                throw new BusinessException(ErrorCode.CONFLICT, "duplicate request");
            }
        }
        String user = segment(userId);
        String tenant = segment(tenantId);
        int dailyLimit = limits.getDailyLimit();
        if (dailyLimit > 0) {
            String day = DAY.format(clock.instant());
            long used = cache.increment("quota:" + tenant + ":" + user + ":" + day, QUOTA_TTL);
            if (used > dailyLimit) {
                throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "daily quota exceeded");
            }
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

    /** 键的分段里不留冒号，避免租户标识把 quota 的四段拆乱。 */
    private static String segment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(':', '_');
    }
}
