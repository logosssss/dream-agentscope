package com.zhu.bootstrap;

import com.zhu.audit.api.approval.ApprovalRecorder;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PermissionEngine 落地 ⑤：双闸更严合并与 claim 边界。
 */
class ApprovalMiddlewareDualGateTest {

    /** 引擎 ASK、产品 ALLOW → 合并 ASK，落单。 */
    @Test
    void engineAskBeatsProductAllow() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        ToolUseBlock seen = act(
                engineAsking("shell"),
                (toolId, tenantId, roleId) -> PermissionDecision.ALLOW,
                published,
                recorded,
                "shell");
        assertThat(recorded).containsExactly("shell");
        assertThat(published).hasSize(1);
        assertThat(seen.getState()).isEqualTo(ToolCallState.ASKING);
    }

    /** 引擎 ALLOW、产品 DENY → 合并 DENY，丢掉工具；claim 也不会走到。 */
    @Test
    void productDenyBeatsEngineAllow() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        PermissionRuleService product = (toolId, tenantId, roleId) ->
                "shell".equals(toolId) ? PermissionDecision.DENY : PermissionDecision.ALLOW;
        ApprovalRecorder approvals = new ApprovalRecorder() {
            @Override
            public void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId) {
                recorded.add(toolId);
            }

            @Override
            public String claimApproved(String tenantId, String userId, String sessionId, String toolId) {
                throw new IllegalStateException("DENY must not claim");
            }
        };
        ApprovalMiddleware middleware = new ApprovalMiddleware(
                product, approvals, event -> published.add((ApprovalRequestedEvent) event));
        ToolUseBlock shell = new ToolUseBlock.Builder().id("t1").name("shell").input(Map.of()).build();
        ToolUseBlock http = new ToolUseBlock.Builder().id("t2").name("http").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .build();
        middleware
                .onActing(
                        agentWith(engineBypass()),
                        context,
                        new ActingInput(List.of(shell, http)),
                        input -> {
                            forwarded.set(input.toolCalls());
                            return reactor.core.publisher.Flux.empty();
                        })
                .blockLast();
        assertThat(recorded).isEmpty();
        assertThat(published).isEmpty();
        assertThat(forwarded.get()).extracting(ToolUseBlock::getName).containsExactly("http");
    }

    /** 合并为 DENY 时，即使有批准单也不放行。 */
    @Test
    void claimCannotRescueDeny() {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        PermissionRuleService product = (toolId, tenantId, roleId) -> PermissionDecision.DENY;
        ApprovalRecorder approvals = new ApprovalRecorder() {
            @Override
            public void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId) {
                throw new IllegalStateException("should not request");
            }

            @Override
            public String claimApproved(String tenantId, String userId, String sessionId, String toolId) {
                return "ap-should-not-use";
            }
        };
        ApprovalMiddleware middleware = new ApprovalMiddleware(product, approvals, event -> {
        });
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
                            return reactor.core.publisher.Flux.empty();
                        })
                .blockLast();
        assertThat(forwarded.get()).isEmpty();
    }

    /** 静态更严合并：DENY 压过 ASK，ASK 压过 ALLOW。 */
    @Test
    void stricterMergeOrder() {
        assertThat(ApprovalMiddleware.stricter(PermissionDecision.ALLOW, PermissionDecision.ASK))
                .isEqualTo(PermissionDecision.ASK);
        assertThat(ApprovalMiddleware.stricter(PermissionDecision.ASK, PermissionDecision.DENY))
                .isEqualTo(PermissionDecision.DENY);
        assertThat(ApprovalMiddleware.stricter(PermissionDecision.DENY, PermissionDecision.ALLOW))
                .isEqualTo(PermissionDecision.DENY);
    }

    private static ToolUseBlock act(
            PermissionEngine engine,
            PermissionRuleService product,
            List<ApprovalRequestedEvent> published,
            List<String> recorded,
            String toolName) {
        ApprovalRecorder approvals = (approvalId, toolId, userId, sessionId, tenantId) -> recorded.add(toolId);
        ApprovalMiddleware middleware = new ApprovalMiddleware(
                product, approvals, event -> published.add((ApprovalRequestedEvent) event));
        ToolUseBlock use = new ToolUseBlock.Builder().id("tool-1").name(toolName).input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        AtomicReference<ToolUseBlock> forwarded = new AtomicReference<>();
        middleware
                .onActing(agentWith(engine), context, new ActingInput(List.of(use)), input -> {
                    forwarded.set(input.toolCalls().get(0));
                    return reactor.core.publisher.Flux.empty();
                })
                .blockLast();
        return forwarded.get();
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
