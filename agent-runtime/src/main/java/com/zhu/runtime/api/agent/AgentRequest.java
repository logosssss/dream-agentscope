package com.zhu.runtime.api.agent;

import java.util.List;

/**
 * 运行层对外入参：一次执行。接入层组装它，运行层只消费，不从文本里猜产品语义。
 *
 * @param agentId      状态命名空间，空则用运行配置里的默认值
 * @param tenantId     租户
 * @param userId       用户，必填
 * @param sessionId    会话，必填
 * @param systemPrompt 本次系统提示词，空则用占位符
 * @param userText     用户输入，必填（仅附件时由接入层补默认句）
 * @param images       本轮多模态图片；空列表表示纯文本。后续轮次不应再带历史图
 */
public record AgentRequest(
        String agentId,
        String tenantId,
        String userId,
        String sessionId,
        String systemPrompt,
        String userText,
        List<AgentImagePart> images
) {

    /**
     * 规范化图片列表，避免 null。
     */
    public AgentRequest {
        images = images == null || images.isEmpty() ? List.of() : List.copyOf(images);
    }

    /**
     * 纯文本兼容构造。测试与旧调用方仍可只传六段。
     */
    public AgentRequest(
            String agentId,
            String tenantId,
            String userId,
            String sessionId,
            String systemPrompt,
            String userText) {
        this(agentId, tenantId, userId, sessionId, systemPrompt, userText, List.of());
    }

    /** 本轮是否带图。 */
    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }
}
