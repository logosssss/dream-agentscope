package com.zhu.access.api.dto;

import com.zhu.access.domain.channel.AccessChannel;

/**
 * 聊天接口的入参。和运行层的 AgentRequest 分开，接入层可以改字段而不带动执行内核。
 *
 * @param agentId      可选，空则用运行配置的默认 Agent
 * @param tenantId     租户。空则用请求头 X-Tenant-Id
 * @param userId       用户。空则用请求头 X-User-Id
 * @param sessionId    会话
 * @param systemPrompt 已忽略。人设只来自产品提示词
 * @param text         用户输入
 * @param channel      接入通道。空则按 WEB，不传给运行层
 */
public record ChatRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String text,
        AccessChannel channel
) {

    /** 没写通道时视为页面或开放 HTTP，避免空值传到后面再猜。 */
    public AccessChannel channelOrDefault() {
        return channel == null ? AccessChannel.WEB : channel;
    }
}
