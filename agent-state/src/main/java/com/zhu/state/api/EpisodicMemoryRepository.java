package com.zhu.state.api;

import java.util.List;

/**
 * 情景记忆的读写缝。按租户和用户切开。
 * 运行层抽取后写入，检索后合并。接入层和管理进程不要调用。
 */
public interface EpisodicMemoryRepository {

    /**
     * 追加一条短摘要。不存对话全文。
     * 重要程度只接受 1 到 5。
     * 写入成功返回自增 id，供向量主键使用。没有落行时返回 0。
     */
    long append(String tenantId, String userId, String sessionId, String summary, int salience);

    /**
     * 同一租户用户最近几条。按入库时间倒序。
     * 用户为空时返回空列表，不查库。
     */
    List<EpisodicMemoryItem> recent(String tenantId, String userId, int limit);

    /**
     * 按主键取同一租户用户的行。近邻 id 对不上账本时丢掉。
     * 用户或 id 列表为空时返回空列表，不查库。
     */
    List<EpisodicMemoryItem> findByIds(String tenantId, String userId, List<Long> ids);
}
