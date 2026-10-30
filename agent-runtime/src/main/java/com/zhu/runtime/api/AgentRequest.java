package com.zhu.runtime.api;

/**
 * 一次执行的入参。接入层组装它，运行层只消费，不从文本里猜产品语义。
 *
 * @param agentId      状态命名空间，空则用运行配置里的默认值
 * @param tenantId     租户
 * @param userId       用户，必填
 * @param sessionId    会话，必填
 * @param systemPrompt 本次系统提示词，空则用占位符
 * @param userText     用户输入，必填
 */
public record AgentRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String userText
) {
}
