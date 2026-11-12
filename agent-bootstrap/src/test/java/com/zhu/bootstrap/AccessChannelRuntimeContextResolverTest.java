package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.gateway.AccessChannelRuntimeContextResolver;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.ChannelRuntimeContextRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 接入层 resolver：caller 暂存写出 tenantId/agentId，空白键不落，身份字段不在这里拼。
 */
class AccessChannelRuntimeContextResolverTest {

    private final AccessChannelRuntimeContextResolver resolver = new AccessChannelRuntimeContextResolver();

    /** staging 只带非空白业务键，不含 userId/sessionId。 */
    @Test
    void stagingKeepsBusinessKeysOnly() {
        RuntimeContext staging = AccessChannelRuntimeContextResolver.staging("demo", "echo-v1");
        assertThat(staging.get("tenantId", String.class)).isEqualTo("demo");
        assertThat(staging.get("agentId", String.class)).isEqualTo("echo-v1");
        assertThat(staging.getUserId()).isNull();
        assertThat(staging.getSessionId()).isNull();
    }

    /** 空白租户和 Agent 不写入暂存。 */
    @Test
    void stagingSkipsBlank() {
        RuntimeContext staging = AccessChannelRuntimeContextResolver.staging("  ", null);
        assertThat(staging.get("tenantId", String.class)).isNull();
        assertThat(staging.get("agentId", String.class)).isNull();
    }

    /** resolve 从 caller 暂存取出业务键，仍不写 userId/sessionId。 */
    @Test
    void resolveCopiesBusinessKeysFromCaller() {
        RuntimeContext resolved = resolver.resolve(new ChannelRuntimeContextRequest(
                "chatui",
                null,
                null,
                null,
                AccessChannelRuntimeContextResolver.staging("tenant-a", "companion-v1")));
        assertThat(resolved.get("tenantId", String.class)).isEqualTo("tenant-a");
        assertThat(resolved.get("agentId", String.class)).isEqualTo("companion-v1");
        assertThat(resolved.getUserId()).isNull();
        assertThat(resolved.getSessionId()).isNull();
    }

    /** request 为空时仍返回可用上下文，不抛。 */
    @Test
    void resolveNullRequestStaysEmpty() {
        RuntimeContext resolved = resolver.resolve(null);
        assertThat(resolved.get("tenantId", String.class)).isNull();
        assertThat(resolved.get("agentId", String.class)).isNull();
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
                AccessChannelRuntimeContextResolver.staging("demo", null)));
        assertThat(resolved.get("tenantId", String.class)).isEqualTo("demo");
        assertThat(resolved.get("agentId", String.class)).isEqualTo("echo-v1");
    }
}
