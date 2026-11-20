package com.zhu.cache.redis.bloom;

import com.zhu.cache.api.bloom.BloomFilter;
import com.zhu.cache.redis.RedisCallSupport;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

/**
 * Redis 族：基于 Redisson {@link RBloomFilter} 的布隆实现。
 * 须先 {@link #tryInit}；失败按 E1。
 */
public class RedissonBloomFilter implements BloomFilter {

    private final RedissonClient redisson;

    /**
     * @param redisson 已连上的客户端
     */
    public RedissonBloomFilter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryInit(String name, long expectedInsertions, double falsePositiveProbability) {
        if (name == null || name.isBlank() || expectedInsertions < 1L
                || falsePositiveProbability <= 0D || falsePositiveProbability >= 1D) {
            return false;
        }
        return RedisCallSupport.call(() -> {
            RBloomFilter<String> filter = redisson.getBloomFilter(name);
            return filter.tryInit(expectedInsertions, falsePositiveProbability);
        });
    }

    @Override
    public void add(String name, String value) {
        if (name == null || name.isBlank() || value == null) {
            return;
        }
        RedisCallSupport.run(() -> {
            RBloomFilter<String> filter = redisson.getBloomFilter(name);
            filter.add(value);
        });
    }

    @Override
    public boolean mightContain(String name, String value) {
        if (name == null || name.isBlank() || value == null) {
            return false;
        }
        return RedisCallSupport.call(() -> {
            RBloomFilter<String> filter = redisson.getBloomFilter(name);
            return filter.contains(value);
        });
    }
}
