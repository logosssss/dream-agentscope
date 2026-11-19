package com.zhu.cache.api.kv;

import java.time.Duration;

/**
 * 短文本 KV。会话票据、列表缓存等；不存放 Agent 状态或沙箱快照。失败不可用。
 */
public interface KvCache {

    /**
     * 读取。无键或过期返回 null。
     */
    String get(String key);

    /**
     * 覆盖写入并设置过期。
     */
    void put(String key, String value, Duration ttl);

    /**
     * 删除。键不存在时无操作。
     */
    void delete(String key);
}
