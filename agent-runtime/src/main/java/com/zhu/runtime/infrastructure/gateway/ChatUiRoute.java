package com.zhu.runtime.infrastructure.gateway;

import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;

/**
 * ChatUi 的发送路由。同步、流式和续跑共用，避免第一次对话把 main 钉成显式 agentId。
 */
final class ChatUiRoute {

    /** 只提供静态方法，不让三条发送路径各写一份路由。 */
    private ChatUiRoute() {
    }

    /**
     * 空标识或等于已启动的 main 时不改路由。
     * {@code mainAgentId} 必须是懒启动之后的值。还是 null 时，非空标识会被当成显式路由。
     */
    static SendOptions options(
            String userId, String sessionId, String tenantId, String agentId, String mainAgentId) {
        SendOptions options = SendOptions.of(userId, sessionId)
                .withRuntimeContext(AgentChannelRuntimeContextResolver.staging(tenantId, agentId));
        if (agentId != null && !agentId.isBlank() && !agentId.equals(mainAgentId)) {
            options = options.withAgentId(agentId);
        }
        return options;
    }
}
