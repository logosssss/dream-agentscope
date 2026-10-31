package com.zhu.access.infrastructure.cache;

import java.time.Duration;

/**
 * 接入层计数和会话列表缓存。不存放 Agent 状态或沙箱快照。
 */
public interface AccessCache {

    /**
     * 把计数加一。键第一次出现时开始计算过期时间，之后的增加不延长过期。
     *
     * @return 增加之后的值，从 1 开始
     */
    long increment(String key, Duration ttl);

    /**
     * 占住这个键。已经有人占着且未过期时返回 false。
     */
    boolean claim(String key, Duration ttl);

    /**
     * 用令牌占住这个键。已经有人占着且未过期时返回 false。
     * 令牌用来释放，避免把别人刚占上的键删掉。
     */
    boolean lock(String key, String token, Duration ttl);

    /**
     * 只有令牌还对得上才释放。过期或令牌不符时什么都不做。
     */
    void unlock(String key, String token);

    /**
     * 读取一段短文本。没有或已过期时返回 null。不存放 Agent 状态。
     */
    String get(String key);

    /**
     * 写入一段短文本并设置过期。用于会话列表缓存，不延长已有额度键。
     */
    void put(String key, String value, Duration ttl);

    /**
     * 删掉一个缓存键。键不存在时什么都不做。
     */
    void delete(String key);
}
