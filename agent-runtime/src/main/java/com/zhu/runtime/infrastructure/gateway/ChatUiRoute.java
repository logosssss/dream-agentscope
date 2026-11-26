package com.zhu.runtime.infrastructure.gateway;

import com.zhu.runtime.domain.TurnSignals;
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
     * {@code runId} 与 {@code signals} 进 caller 暂存：审计中间件靠运行号挂行，
     * 安全与人设中间件靠共享信号把档位交回收口；没有运行的入口两个都传 null。
     */
    static SendOptions options(
            String userId,
            String sessionId,
            String tenantId,
            String agentId,
            String mainAgentId,
            String runId,
            TurnSignals signals) {
        SendOptions options = SendOptions.of(userId, sessionId)
                .withRuntimeContext(
                        AgentChannelRuntimeContextResolver.staging(tenantId, agentId, runId, signals));
        if (agentId != null && !agentId.isBlank() && !agentId.equals(mainAgentId)) {
            options = options.withAgentId(agentId);
        }
        return options;
    }
}
