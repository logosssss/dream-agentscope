package com.zhu.state.api;

/**
 * 情景记忆的写入缝。按租户和用户切开。
 * 运行层抽取后写入。接入层和管理进程不要调用。
 */
public interface EpisodicMemoryRepository {

    /**
     * 追加一条短摘要。不存对话全文。
     * 重要程度只接受 1 到 5。
     */
    void append(String tenantId, String userId, String sessionId, String summary, int salience);
}
