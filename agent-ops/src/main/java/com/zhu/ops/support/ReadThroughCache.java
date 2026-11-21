package com.zhu.ops.support;

import com.zhu.cache.api.CacheFacade;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * 审计层读穿缓存辅助：权威在 MySQL；Redis 只加速。
 * 读/写/删遇 {@code UNAVAILABLE} 不抛给业务，读当 miss、写删尽力而为。
 * 无 {@link CacheFacade} 时全部直通（测试或未装配）。
 */
public final class ReadThroughCache {

    private static final Logger LOG = Logger.getLogger(ReadThroughCache.class.getName());

    private final CacheFacade cache;

    private ReadThroughCache(CacheFacade cache) {
        this.cache = cache;
    }

    /** 无门面：所有操作空操作 / miss。 */
    public static ReadThroughCache absent() {
        return new ReadThroughCache(null);
    }

    /** 有门面则读穿；null 等同 {@link #absent()}。 */
    public static ReadThroughCache of(CacheFacade cache) {
        return cache == null ? absent() : new ReadThroughCache(cache);
    }

    /**
     * 读取。无键、无门面或 Redis 不可用返回 null。
     */
    public String get(String key) {
        if (cache == null || key == null || key.isBlank()) {
            return null;
        }
        try {
            return cache.kv().get(key);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.UNAVAILABLE) {
                LOG.warning("audit read-through cache get unavailable");
                return null;
            }
            throw ex;
        }
    }

    /**
     * 尽力写入。Redis 不可用不影响调用方已拿到的 MySQL 结果。
     */
    public void put(String key, String value, Duration ttl) {
        if (cache == null || key == null || key.isBlank() || value == null || ttl == null) {
            return;
        }
        try {
            cache.kv().put(key, value, ttl);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.UNAVAILABLE) {
                LOG.warning("audit read-through cache put unavailable");
                return;
            }
            throw ex;
        }
    }

    /**
     * 写路径失效。键不存在或 Redis 不可用均忽略。
     */
    public void delete(String key) {
        if (cache == null || key == null || key.isBlank()) {
            return;
        }
        try {
            cache.kv().delete(key);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.UNAVAILABLE) {
                LOG.warning("audit read-through cache delete unavailable");
                return;
            }
            throw ex;
        }
    }
}
