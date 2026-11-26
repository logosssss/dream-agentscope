package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.runtime.domain.TurnSignals;
import com.zhu.runtime.infrastructure.gateway.AgentChannelRuntimeContextResolver;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 接入层 resolver：caller 暂存写出 tenantId/agentId/runId 与这一轮的共享信号，
 * 空白键不落，身份字段不在这里拼。
 */
class AgentChannelRuntimeContextResolverTest {

    private final AgentChannelRuntimeContextResolver resolver = new AgentChannelRuntimeContextResolver();

    /** staging 只带非空白业务键，不含 userId/sessionId。 */
    @Test
    void stagingKeepsBusinessKeysOnly() {
        RuntimeContext staging = AgentChannelRuntimeContextResolver.staging("demo", "echo-v1", "run-1", null);
        assertThat(staging.get("tenantId", String.class)).isEqualTo("demo");
        assertThat(staging.get("agentId", String.class)).isEqualTo("echo-v1");
        assertThat(staging.get("runId", String.class)).isEqualTo("run-1");
        assertThat(staging.getUserId()).isNull();
        assertThat(staging.getSessionId()).isNull();
    }

    /** 空白租户、Agent 和运行号不写入暂存。 */
    @Test
    void stagingSkipsBlank() {
        RuntimeContext staging = AgentChannelRuntimeContextResolver.staging("  ", null, "", null);
        assertThat(staging.get("tenantId", String.class)).isNull();
        assertThat(staging.get("agentId", String.class)).isNull();
        assertThat(staging.get("runId", String.class)).isNull();
    }

    /** resolve 从 caller 暂存取出业务键，仍不写 userId/sessionId。 */
    @Test
    void resolveCopiesBusinessKeysFromCaller() {
        RuntimeContext resolved = resolver.resolve(new ChannelRuntimeContextRequest(
                "chatui",
                null,
                null,
                null,
                AgentChannelRuntimeContextResolver.staging("tenant-a", "companion-v1", null, null)));
        assertThat(resolved.get("tenantId", String.class)).isEqualTo("tenant-a");
        assertThat(resolved.get("agentId", String.class)).isEqualTo("companion-v1");
        assertThat(resolved.getUserId()).isNull();
        assertThat(resolved.getSessionId()).isNull();
    }

    /**
     * 运行号必须活过 resolve：中间件读的是 resolve 之后的上下文。
     * 扩展字段不会被 builder(caller) 带过来，所以这里显式写一遍，漏了就是审计行 run_id 恒空。
     */
    @Test
    void resolveKeepsRunId() {
        RuntimeContext resolved = resolver.resolve(new ChannelRuntimeContextRequest(
                "chatui",
                null,
                null,
                null,
                AgentChannelRuntimeContextResolver.staging("demo", "companion-v1", "run-7", null)));
        assertThat(resolved.get("runId", String.class)).isEqualTo("run-7");
    }

    /**
     * 共享信号要按同一个实例活过 resolve：中间件往这个对象写档位，收口读它。
     * 复制成新实例等于没写，危机输出红线与人设检查又回到恒不触发。
     */
    @Test
    void resolveKeepsTheSameTurnSignalsInstance() {
        TurnSignals signals = new TurnSignals();
        RuntimeContext resolved = resolver.resolve(new ChannelRuntimeContextRequest(
                "chatui",
                null,
                null,
                null,
                AgentChannelRuntimeContextResolver.staging("demo", "companion-v1", "run-7", signals)));
        assertThat(resolved.get(TurnSignals.class)).isSameAs(signals);
    }

    /** request 为空时仍返回可用上下文，不抛。 */
    @Test
    void resolveNullRequestStaysEmpty() {
        RuntimeContext resolved = resolver.resolve(null);
        assertThat(resolved.get("tenantId", String.class)).isNull();
        assertThat(resolved.get("agentId", String.class)).isNull();
        assertThat(resolved.get("runId", String.class)).isNull();
        assertThat(resolved.get(TurnSignals.class)).isNull();
    }

    /** caller 没有 agentId 时，可从 MsgContext.extra 补上。 */
    @Test
    void resolveFallsBackToMsgExtraAgentId() {
        MsgContext msg = new MsgContext("chatui", null, null, null, null, Map.of("agentId", "echo-v1"));
        RuntimeContext resolved = resolver.resolve(new ChannelRuntimeContextRequest(
                "chatui",
                null,
                msg,
                null,
                AgentChannelRuntimeContextResolver.staging("demo", null, null, null)));
        assertThat(resolved.get("tenantId", String.class)).isEqualTo("demo");
        assertThat(resolved.get("agentId", String.class)).isEqualTo("echo-v1");
    }
}
