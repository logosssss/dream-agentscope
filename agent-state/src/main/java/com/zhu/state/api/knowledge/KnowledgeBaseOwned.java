package com.zhu.state.api.knowledge;

import java.time.LocalDateTime;

/**
 * 本人一个知识库。带建库时间，仍不含文档。详情接口用它做归属确认。
 *
 * @param kbId      业务主键
 * @param tenantId  租户
 * @param userId    所有者
 * @param agentId   绑定 Agent
 * @param name      展示名
 * @param createdAt 建库时间
 */
public record KnowledgeBaseOwned(
        String kbId,
        String tenantId,
        String userId,
        String agentId,
        String name,
        LocalDateTime createdAt) {
}
