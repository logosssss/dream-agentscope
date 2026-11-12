package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Permission Gate 落地 ②：确认 Middleware 不再读引擎；引擎 ASK 不能再压过产品 ALLOW。
 */
class ApprovalMiddlewareDualGateTest {

    /**
     * 引擎挂 ASK、产品 ALLOW：收缩后中间件只认产品 ALLOW，工具仍下放给 Gate。
     * （旧双闸会合并成 ASK 并落单。）
     */
    @Test
    void productAllowIgnoresEngineAsk() {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        PermissionRuleService product = (toolId, tenantId, roleId) -> PermissionDecision.ALLOW;
        ApprovalMiddleware middleware = new ApprovalMiddleware(product);
        ToolUseBlock use = new ToolUseBlock.Builder().id("t1").name("shell").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .build();
        middleware
                .onActing(
                        agentWith(engineAsking("shell")),
                        context,
                        new ActingInput(List.of(use)),
                        input -> {
                            forwarded.set(input.toolCalls());
                            return Flux.empty();
                        })
                .blockLast();
        assertThat(forwarded.get()).extracting(ToolUseBlock::getName).containsExactly("shell");
    }

    /** 产品 DENY 仍丢掉工具，与引擎无关。 */
    @Test
    void productDenyDropsRegardlessOfEngine() {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        PermissionRuleService product = (toolId, tenantId, roleId) -> PermissionDecision.DENY;
        ApprovalMiddleware middleware = new ApprovalMiddleware(product);
        ToolUseBlock use = new ToolUseBlock.Builder().id("t1").name("shell").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .build();
        middleware
                .onActing(
                        agentWith(engineBypass()),
                        context,
                        new ActingInput(List.of(use)),
                        input -> {
                            forwarded.set(input.toolCalls());
                            return Flux.empty();
                        })
                .blockLast();
        assertThat(forwarded.get()).isEmpty();
    }

    private static ReActAgent agentWith(PermissionEngine engine) {
        ReActAgent agent = mock(ReActAgent.class);
        when(agent.getPermissionEngine()).thenReturn(engine);
        return agent;
    }

    private static PermissionEngine engineAsking(String tool) {
        PermissionContextState context = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .addAskRule(tool, new PermissionRule(tool, null, PermissionBehavior.ASK, "test"))
                .build();
        return new PermissionEngine(context);
    }

    private static PermissionEngine engineBypass() {
        PermissionContextState context = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .addAllowRule(
                        "__keep__",
                        new PermissionRule("__keep__", "x", PermissionBehavior.ALLOW, "test"))
                .build();
        return new PermissionEngine(context);
    }
}
