package com.zhu.runtime.api;

public record AgentRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String userText
) {
}
