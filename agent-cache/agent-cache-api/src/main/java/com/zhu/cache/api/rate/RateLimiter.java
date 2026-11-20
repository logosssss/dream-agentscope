package com.zhu.cache.api.rate;

import java.time.Duration;

/**
 * 缓存层：分布式限流契约（二期原语形状）。
 * 本轮不接线业务额度公式；调用方自行选键与速率。失败不可用。
 */
public interface RateLimiter {

    /**
     * 在给定窗口内尝试获取一个许可。
     *
     * @param key          限流键（建议经 {@code CacheKeys}）
     * @param rate         窗口内最大许可数；须 ≥ 1
     * @param rateInterval 窗口长度；须为正
     * @return true 表示拿到许可；false 表示超限
     */
    boolean tryAcquire(String key, long rate, Duration rateInterval);
}
