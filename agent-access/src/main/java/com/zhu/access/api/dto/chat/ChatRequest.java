package com.zhu.access.api.dto.chat;

import com.zhu.access.domain.channel.AccessChannel;
import java.util.List;

/**
 * 聊天接口的入参。和运行层的 AgentRequest 分开，接入层可以改字段而不带动执行内核。
 *
 * @param agentId        可选，空则用运行配置的默认 Agent
 * @param tenantId       已忽略。身份只来自登录会话
 * @param userId         已忽略。身份只来自登录会话
 * @param sessionId      会话
 * @param systemPrompt   已忽略。人设只来自产品提示词
 * @param text           用户输入；可与附件一起为空，由服务端补默认句
 * @param channel        接入通道。空则按 WEB，不传给运行层
 * @param attachmentIds  先上传得到的附件 id；仅搭子 WEB 聊天使用，最多 3 个
 */
public record ChatRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String text,
        AccessChannel channel,
        List<String> attachmentIds
) {

    /**
     * 旧七段构造兼容（无附件）。
     */
    public ChatRequest(
            String agentId,
            String tenantId,
            String userId,
            String sessionId,
            String systemPrompt,
            String text,
            AccessChannel channel) {
        this(agentId, tenantId, userId, sessionId, systemPrompt, text, channel, List.of());
    }

    /** 没写通道时视为页面或开放 HTTP，避免空值传到后面再猜。 */
    public AccessChannel channelOrDefault() {
        return channel == null ? AccessChannel.WEB : channel;
    }

    /** 规范化附件 id 列表。 */
    public List<String> attachmentIdsOrEmpty() {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(attachmentIds);
    }
}
