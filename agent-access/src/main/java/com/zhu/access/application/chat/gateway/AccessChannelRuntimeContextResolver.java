package com.zhu.access.application.chat.gateway;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextRequest;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextResolver;

/**
 * 接入层 Channel 上下文解析：把租户、Agent 等业务字段写入 RuntimeContext。
 * 挂在 Gateway 上，合并顺序仍是 caller → 本解析器 → Gateway 覆盖身份字段。
 * 业务键不要塞进 {@link MsgContext#extra()}，那会进会话闸键。
 */
public final class AccessChannelRuntimeContextResolver implements ChannelRuntimeContextResolver {

    static final String TENANT_ID = "tenantId";

    static final String AGENT_ID = "agentId";

    /**
     * 调用方只带业务暂存，不在这里拼 userId/sessionId（由 SendOptions + Gateway 覆盖）。
     */
    public static RuntimeContext staging(String tenantId, String agentId) {
        RuntimeContext.Builder builder = RuntimeContext.builder();
        if (tenantId != null && !tenantId.isBlank()) {
            builder.put(TENANT_ID, tenantId);
        }
        if (agentId != null && !agentId.isBlank()) {
            builder.put(AGENT_ID, agentId);
        }
        return builder.build();
    }

    /**
     * 从 caller 暂存与 MsgContext 路由信息写出 tenantId/agentId。
     * 返回新上下文作为 Gateway 合并基座；caller 为空时仍可只靠 MsgContext.extra 的 agentId。
     */
    @Override
    public RuntimeContext resolve(ChannelRuntimeContextRequest request) {
        RuntimeContext caller = request == null ? null : request.callerContext();
        MsgContext msg = request == null ? null : request.msgContext();
        // 以 caller 为基座，只补业务键；userId/sessionId 仍由 Gateway 覆盖。
        RuntimeContext.Builder builder = RuntimeContext.builder(caller);
        String tenantId = stringExtra(caller, TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            builder.put(TENANT_ID, tenantId);
        }
        String agentId = firstNonBlank(stringExtra(caller, AGENT_ID), msgExtra(msg, AGENT_ID));
        if (agentId != null && !agentId.isBlank()) {
            builder.put(AGENT_ID, agentId);
        }
        return builder.build();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String stringExtra(RuntimeContext context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String msgExtra(MsgContext context, String key) {
        if (context == null || context.extra() == null || key == null) {
            return null;
        }
        return context.extra().get(key);
    }
}
