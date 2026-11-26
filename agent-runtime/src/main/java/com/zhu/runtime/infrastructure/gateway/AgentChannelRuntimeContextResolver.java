package com.zhu.runtime.infrastructure.gateway;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextRequest;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextResolver;

/**
 * 运行层 Channel 上下文解析：把租户、Agent 等业务字段写入 RuntimeContext。
 * 挂在 Gateway 上，合并顺序仍是 caller → 本解析器 → Gateway 覆盖身份字段。
 * 业务键不要塞进 {@link MsgContext#extra()}，那会进会话闸键。
 */
public final class AgentChannelRuntimeContextResolver implements ChannelRuntimeContextResolver {

    static final String TENANT_ID = "tenantId";

    static final String AGENT_ID = "agentId";

    /**
     * 这一轮的运行标识。审计中间件在执行过程中写 {@code agent_event_audit.run_id}，
     * 所以必须在发出去之前就带上；执行完再取框架消息 id 就晚了。
     */
    static final String RUN_ID = "runId";

    /**
     * 调用方只带业务暂存，不在这里拼 userId/sessionId（由 SendOptions + Gateway 覆盖）。
     * 没有运行概念的入口（Gate 续跑、任务审计）传 null。
     */
    public static RuntimeContext staging(String tenantId, String agentId, String runId) {
        RuntimeContext.Builder builder = RuntimeContext.builder();
        putIfText(builder, TENANT_ID, tenantId);
        putIfText(builder, AGENT_ID, agentId);
        putIfText(builder, RUN_ID, runId);
        return builder.build();
    }

    /** 空白键不落，避免下游把空串当成一个有效的标识。 */
    private static void putIfText(RuntimeContext.Builder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.put(key, value);
        }
    }

    /**
     * 从 caller 暂存与 MsgContext 路由信息写出 tenantId/agentId/runId。
     * 返回新上下文作为 Gateway 合并基座；caller 为空时仍可只靠 MsgContext.extra 的 agentId。
     */
    @Override
    public RuntimeContext resolve(ChannelRuntimeContextRequest request) {
        RuntimeContext caller = request == null ? null : request.callerContext();
        MsgContext msg = request == null ? null : request.msgContext();
        // 以 caller 为基座，只补业务键；userId/sessionId 仍由 Gateway 覆盖。
        // 扩展项不会被基座带过来，所以三个业务键都要显式写一遍。
        RuntimeContext.Builder builder = RuntimeContext.builder(caller);
        putIfText(builder, TENANT_ID, stringExtra(caller, TENANT_ID));
        putIfText(builder, AGENT_ID, firstNonBlank(stringExtra(caller, AGENT_ID), msgExtra(msg, AGENT_ID)));
        putIfText(builder, RUN_ID, stringExtra(caller, RUN_ID));
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
