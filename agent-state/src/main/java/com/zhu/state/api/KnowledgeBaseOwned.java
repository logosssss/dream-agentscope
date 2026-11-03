package com.zhu.state.api;

import java.time.LocalDateTime;

/**
 * 本人一个知识库的详情。比列表多创建时间，仍不含文档。
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
