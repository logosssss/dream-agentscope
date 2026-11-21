package com.zhu.ops.quota;

import com.zhu.ops.support.QueryLimits;
import com.zhu.ops.support.ReadThroughCache;
import com.zhu.readmodel.persistence.quota.TenantQuotaMapper;
import com.zhu.readmodel.entity.quota.TenantQuotaRecord;
import com.zhu.cache.api.CacheFacade;
import com.zhu.cache.api.CacheKeys;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 审计层：MySQL 租户每日请求、token、费用与同时运行上限。只在存储开关打开时创建。
 * Lookup 短 TTL 读穿 Redis；写路径尽力删键。不写已用量。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTenantQuota implements TenantQuotaLookup, TenantQuotaCatalog {

    private static final Logger log = Logger.getLogger(MysqlTenantQuota.class.getName());

    private static final String REQUESTS = "REQUESTS";

    private static final String TOKENS = "TOKENS";

    private static final String COST = "COST";

    private static final String CONCURRENT_RUNS = "CONCURRENT_RUNS";

    private static final String DAILY = "DAILY";

    private static final String CURRENT = "CURRENT";

    /** 无启用行时的哨兵，避免反复打库。 */
    private static final String NONE = "__none__";

    private static final Duration LIMIT_TTL = Duration.ofSeconds(60);

    private final TenantQuotaMapper quotas;

    private final ReadThroughCache readCache;

    /**
     * Spring：可选注入 CacheFacade；测试可只传 Mapper。
     */
    @Autowired
    public MysqlTenantQuota(TenantQuotaMapper quotas, @Autowired(required = false) CacheFacade cache) {
        this.quotas = quotas;
        this.readCache = ReadThroughCache.of(cache);
    }

    /** 测试无缓存。 */
    public MysqlTenantQuota(TenantQuotaMapper quotas) {
        this(quotas, null);
    }

    /**
     * 空标识当作没有单独上限。查库失败时拒绝对话，不退回进程配置。
     */
    @Override
    public Long dailyRequestLimit(String tenantId) {
        return cachedLookup(tenantId, REQUESTS, () -> quotas.findDailyRequestLimit(tenantId));
    }

    @Override
    public Long dailyTokenLimit(String tenantId) {
        return cachedLookup(tenantId, TOKENS, () -> quotas.findDailyTokenLimit(tenantId));
    }

    @Override
    public Long dailyCostLimit(String tenantId) {
        return cachedLookup(tenantId, COST, () -> quotas.findDailyCostLimit(tenantId));
    }

    @Override
    public Long concurrentRunLimit(String tenantId) {
        return cachedLookup(tenantId, CONCURRENT_RUNS, () -> quotas.findConcurrentRunLimit(tenantId));
    }

    /**
     * 标识为空、超长，或上限为负时拒绝。0 可以写入，表示这个租户不限额。
     * 同一租户再登记一次按冲突处理，不覆盖旧上限。
     */
    @Override
    public TenantQuotaSummary setDailyRequests(String tenantId, Long limit) {
        return insert(tenantId, limit, REQUESTS, DAILY);
    }

    @Override
    public TenantQuotaSummary setDailyTokens(String tenantId, Long limit) {
        return insert(tenantId, limit, TOKENS, DAILY);
    }

    @Override
    public TenantQuotaSummary setDailyCost(String tenantId, Long limit) {
        return insert(tenantId, limit, COST, DAILY);
    }

    @Override
    public TenantQuotaSummary setConcurrentRuns(String tenantId, Long limit) {
        return insert(tenantId, limit, CONCURRENT_RUNS, CURRENT);
    }

    /**
     * 只改已有行的上限。没有行不插入。
     * 0 仍表示这个租户不限额。不改启用标记。
     */
    @Override
    public TenantQuotaSummary updateDailyRequests(String tenantId, Long limit) {
        return update(tenantId, limit, REQUESTS, DAILY, quotas::updateDailyRequestLimit);
    }

    @Override
    public TenantQuotaSummary updateDailyTokens(String tenantId, Long limit) {
        return update(tenantId, limit, TOKENS, DAILY, quotas::updateDailyTokenLimit);
    }

    @Override
    public TenantQuotaSummary updateDailyCost(String tenantId, Long limit) {
        return update(tenantId, limit, COST, DAILY, quotas::updateDailyCostLimit);
    }

    @Override
    public TenantQuotaSummary updateConcurrentRuns(String tenantId, Long limit) {
        return update(tenantId, limit, CONCURRENT_RUNS, CURRENT, quotas::updateConcurrentRunLimit);
    }

    /**
     * 空结果返回空列表。只看 REQUESTS / DAILY。已用量不在这张表。
     */
    @Override
    public List<TenantQuotaSummary> recent(int limit) {
        return mapRows(quotas.recent(QueryLimits.normalize(limit)));
    }

    @Override
    public List<TenantQuotaSummary> recentTokens(int limit) {
        return mapRows(quotas.recentTokens(QueryLimits.normalize(limit)));
    }

    @Override
    public List<TenantQuotaSummary> recentCosts(int limit) {
        return mapRows(quotas.recentCosts(QueryLimits.normalize(limit)));
    }

    @Override
    public List<TenantQuotaSummary> recentConcurrent(int limit) {
        return mapRows(quotas.recentConcurrent(QueryLimits.normalize(limit)));
    }

    private Long cachedLookup(String tenantId, String quotaType, LimitQuery query) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        String key = limitKey(tenantId, quotaType);
        String cached = readCache.get(key);
        if (cached != null) {
            if (NONE.equals(cached)) {
                return null;
            }
            try {
                return Long.parseLong(cached);
            } catch (NumberFormatException ex) {
                readCache.delete(key);
            }
        }
        Long value = lookup(tenantId, query);
        readCache.put(key, value == null ? NONE : Long.toString(value), LIMIT_TTL);
        return value;
    }

    private Long lookup(String tenantId, LimitQuery query) {
        try {
            return query.get();
        } catch (DataAccessException ex) {
            log.warning("tenant quota lookup failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant quota is unavailable");
        }
    }

    private TenantQuotaSummary insert(String tenantId, Long limit, String type, String period) {
        requireWritable(tenantId, limit);
        TenantQuotaRecord row = new TenantQuotaRecord();
        row.setTenantId(tenantId);
        row.setQuotaType(type);
        row.setPeriod(period);
        row.setLimitValue(limit);
        row.setEnabled(1);
        try {
            quotas.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant quota already exists");
        }
        readCache.delete(limitKey(tenantId, type));
        return new TenantQuotaSummary(tenantId, type, period, limit);
    }

    private TenantQuotaSummary update(String tenantId, Long limit, String type, String period, LimitUpdate updater) {
        requireWritable(tenantId, limit);
        int updated = updater.apply(tenantId, limit);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant quota not found");
        }
        readCache.delete(limitKey(tenantId, type));
        return new TenantQuotaSummary(tenantId, type, period, limit);
    }

    private static void requireWritable(String tenantId, Long limit) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64 || limit == null || limit < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant quota is invalid");
        }
    }

    private static List<TenantQuotaSummary> mapRows(List<TenantQuotaRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new TenantQuotaSummary(
                        row.getTenantId(),
                        row.getQuotaType(),
                        row.getPeriod(),
                        row.getLimitValue() == null ? 0L : row.getLimitValue()))
                .toList();
    }

    private static String limitKey(String tenantId, String quotaType) {
        return CacheKeys.of("quota-limit", tenantId, quotaType);
    }

    @FunctionalInterface
    private interface LimitQuery {
        Long get();
    }

    @FunctionalInterface
    private interface LimitUpdate {
        int apply(String tenantId, Long limit);
    }
}
