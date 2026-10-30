package com.zhu.runtime.task;

import java.time.Instant;
import java.util.Map;

/**
 * 异步任务的消息体。当前只定义形状，真正发到 RocketMQ 要等 mode=mq。
 *
 * @param taskId    任务标识，也是以后的消息 key
 * @param type      任务类型，例如 doc-ingest
 * @param tenantId  租户
 * @param userId    用户
 * @param agentId   目标 Agent
 * @param sessionId 关联会话
 * @param payload   任务参数
 * @param createdAt 创建时间
 */
public record AgentTask(
        String taskId,
        String type,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
