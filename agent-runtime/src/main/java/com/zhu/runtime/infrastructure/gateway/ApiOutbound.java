package com.zhu.runtime.infrastructure.gateway;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.domain.TurnSignals;
import io.agentscope.core.agent.RuntimeContext;
import java.util.function.Predicate;

/**
 * API 通道的发送参数。同步和流式共用，未知标识在发出去之前就拒绝。
 */
final class ApiOutbound {

    /** 只提供静态方法，不让两条 API 入口各拼一份参数。 */
    private ApiOutbound() {
    }

    /**
     * 空标识表示 main，不改路由。非空标识必须已注册，否则按找不到拒绝。
     * {@code runId} 与 {@code signals} 进 caller 暂存：审计中间件靠运行号挂行，
     * 安全与人设中间件靠共享信号把档位交回收口。
     */
    static Target open(
            AgentRequest request, String agentId, Predicate<String> registered, String runId, TurnSignals signals) {
        String id = blankToNull(agentId);
        if (id != null && !registered.test(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "unknown agentId: " + id);
        }
        return new Target(
                request.userId(),
                request.sessionId(),
                id,
                request.userText(),
                AgentChannelRuntimeContextResolver.staging(request.tenantId(), id, runId, signals));
    }

    /** 空白不当成路由标识。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 一次 API 发送要带的身份和正文。agentId 为空表示 main。
     */
    record Target(String userId, String sessionId, String agentId, String text, RuntimeContext context) {
    }
}
