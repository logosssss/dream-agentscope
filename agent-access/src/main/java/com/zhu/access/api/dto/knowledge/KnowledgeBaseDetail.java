package com.zhu.access.api.dto.knowledge;

/**
 * 本人本租户的一个知识库。带创建时间，不读文档。
 *
 * @param kbId      业务主键
 * @param tenantId  租户，没有时是空串
 * @param userId    所有者
 * @param agentId   绑定 Agent
 * @param name      展示名
 * @param createdAt 建库时间，UTC，没有时区后缀；没有时为空串
 */
public record KnowledgeBaseDetail(
        String kbId,
        String tenantId,
        String userId,
        String agentId,
        String name,
        String createdAt) {
}
