package com.zhu.state.api;

/**
 * 知识库一行摘要。接入层返回给前端。
 *
 * @param kbId     业务主键
 * @param tenantId 租户
 * @param userId   所有者
 * @param agentId  绑定 Agent
 * @param name     展示名
 */
public record KnowledgeBaseItem(
        String kbId,
        String tenantId,
        String userId,
        String agentId,
        String name) {
}
