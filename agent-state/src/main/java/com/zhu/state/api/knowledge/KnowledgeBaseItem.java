package com.zhu.state.api.knowledge;

import java.time.LocalDateTime;

/**
 * 知识库一行。接入层直接返回给前端，列表和建库回执都带建库时间，不含文档。
 *
 * @param kbId      业务主键
 * @param tenantId  租户
 * @param userId    所有者
 * @param agentId   绑定 Agent
 * @param name      展示名
 * @param createdAt 建库时间，UTC，没有时区；库行没有这一列时为空
 */
public record KnowledgeBaseItem(
        String kbId,
        String tenantId,
        String userId,
        String agentId,
        String name,
        LocalDateTime createdAt) {
}
