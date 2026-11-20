package com.zhu.cache.api.bloom;

/**
 * 缓存层：布隆过滤器契约（二期原语形状）。
 * 可能误报、不会漏报（标准布隆语义）。本轮不接线业务；须先 {@link #tryInit}。
 * 失败不可用。
 */
public interface BloomFilter {

    /**
     * 初始化命名过滤器。已存在且参数不同时实现可拒绝或忽略（以 Redisson 为准）。
     *
     * @param name                 过滤器名（建议经 {@code CacheKeys}）
     * @param expectedInsertions   预期插入量；须 ≥ 1
     * @param falsePositiveProbability 误报率 (0,1)
     * @return true 表示本次完成初始化；false 表示已存在
     */
    boolean tryInit(String name, long expectedInsertions, double falsePositiveProbability);

    /**
     * 加入元素。未 init 时行为由实现定义（Redisson 通常失败）。
     */
    void add(String name, String value);

    /**
     * 可能包含：true 可能在集合中；false 一定不在。
     */
    boolean mightContain(String name, String value);
}
