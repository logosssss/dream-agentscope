package com.zhu.state.api.memory;

import java.time.LocalDateTime;

/**
 * 一条情景记忆。给运行层检索用，不带向量。
 *
 * @param id        MySQL 行 id，也是向量主键
 * @param tenantId  租户，可能是空串
 * @param userId    用户
 * @param sessionId 来源会话，可能是空串
 * @param summary   短摘要
 * @param salience  1 到 5
 * @param createdAt 入库时间，可能为空
 */
public record EpisodicMemoryItem(
        long id,
        String tenantId,
        String userId,
        String sessionId,
        String summary,
        int salience,
        LocalDateTime createdAt) {
}
