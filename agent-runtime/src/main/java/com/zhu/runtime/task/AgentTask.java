package com.zhu.runtime.task;

import java.time.Instant;
import java.util.Map;

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
