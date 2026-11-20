package com.zhu.cache.redis;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.springframework.dao.DataAccessException;

/**
 * Redis 调用收口（E1）。失败统一 UNAVAILABLE，不回落内存；日志不带地址原文。
 * 供本模块各实现包复用；业务模块不要直接调用。
 */
public final class RedisCallSupport {

    private static final Logger LOG = Logger.getLogger(RedisCallSupport.class.getName());

    private RedisCallSupport() {
    }

    /**
     * 执行动作；{@link DataAccessException} 与其它运行时失败均视为不可用。
     */
    public static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            LOG.warning("redis call failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        } catch (RuntimeException ex) {
            LOG.warning("redis call failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "redis is unavailable");
        }
    }

    /**
     * 无返回值动作。
     */
    public static void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }
}
