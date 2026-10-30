package com.zhu.access.api.dto;

/**
 * 聊天接口的入参。和运行层的 AgentRequest 分开，接入层可以改字段而不带动执行内核。
 *
 * @param agentId      可选，空则用运行配置的默认 Agent
 * @param tenantId     租户
 * @param userId       用户
 * @param sessionId    会话
 * @param systemPrompt 本次系统提示词
 * @param text         用户输入
 */
public record ChatRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String text
) {
}
