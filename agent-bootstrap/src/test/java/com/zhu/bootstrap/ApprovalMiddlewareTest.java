package com.zhu.bootstrap;

import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 审批中间件只在产品规则为 ASK 时记单，并把这次工具调用标成 ASKING。
 */
class ApprovalMiddlewareTest {

    /** ALLOW 不产生审批，也不把工具标成等待确认。 */
    @Test
    void allowDoesNotRequestApproval() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        ToolUseBlock seen = act(PermissionDecision.ALLOW, published, recorded, "shell");

        assertThat(published).isEmpty();
        assertThat(recorded).isEmpty();
        assertThat(seen.getState()).isNotEqualTo(ToolCallState.ASKING);
    }

    /** ASK 记下审批、发出事件，并把这次工具调用标成 ASKING。 */
    @Test
    void askRecordsAndMarksTool() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        ToolUseBlock seen = act(PermissionDecision.ASK, published, recorded, "shell");

        assertThat(recorded).containsExactly("shell");
        assertThat(published).hasSize(1);
        assertThat(published.get(0).toolId()).isEqualTo("shell");
        assertThat(published.get(0).tenantId()).isEqualTo("tenant-a");
        assertThat(seen.getState()).isEqualTo(ToolCallState.ASKING);
    }

    /** 同会话同工具已有 PENDING 时只标 ASKING，不插新单、不发事件。 */
    @Test
    void existingPendingReusesWithoutNewRecord() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        PermissionRuleService permissions = (toolId, tenantId, roleId) -> PermissionDecision.ASK;
        ApprovalRecorder approvals = new ApprovalRecorder() {
            @Override
            public void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId) {
                recorded.add(toolId);
            }

            @Override
            public String findPending(String tenantId, String userId, String sessionId, String toolId) {
                return "ap-pending";
            }
        };
        ApprovalMiddleware middleware = new ApprovalMiddleware(
                permissions, approvals, event -> published.add((ApprovalRequestedEvent) event));
        ToolUseBlock use = new ToolUseBlock.Builder().id("tool-1").name("shell").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        AtomicReference<ToolUseBlock> forwarded = new AtomicReference<>();
        middleware.onActing(mock(Agent.class), context, new ActingInput(List.of(use)), input -> {
                    forwarded.set(input.toolCalls().get(0));
                    return Flux.empty();
                })
                .blockLast();

        assertThat(recorded).isEmpty();
        assertThat(published).isEmpty();
        assertThat(forwarded.get().getState()).isEqualTo(ToolCallState.ASKING);
    }

    /** 已批准的单放行同名工具，结束后回写成功，不再记一张新单。 */
    @Test
    void approvedClaimRunsToolAndRecordsSuccess() {
        String[] written = new String[2];
        PermissionRuleService permissions = (toolId, tenantId, roleId) -> PermissionDecision.ASK;
        ApprovalRecorder approvals = new ApprovalRecorder() {
            /** 这条路径不该再申请。 */
            @Override
            public void requested(String approvalId, String toolId, String userId, String sessionId, String tenantId) {
                throw new IllegalStateException("should not request");
            }

            /** 返回已有的批准单。 */
            @Override
            public String claimApproved(String tenantId, String userId, String sessionId, String toolId) {
                return "ap-9";
            }

            /** 记下回写的状态。 */
            @Override
            public void executed(String approvalId, String status) {
                written[0] = approvalId;
                written[1] = status;
            }
        };
        ApprovalMiddleware middleware = new ApprovalMiddleware(permissions, approvals, event -> {
        });
        ToolUseBlock use = new ToolUseBlock.Builder().id("tool-1").name("shell").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        AtomicReference<ToolUseBlock> forwarded = new AtomicReference<>();
        middleware.onActing(mock(Agent.class), context, new ActingInput(List.of(use)), input -> {
                    forwarded.set(input.toolCalls().get(0));
                    return Flux.empty();
                })
                .blockLast();

        assertThat(forwarded.get().getState()).isNotEqualTo(ToolCallState.ASKING);
        assertThat(written).containsExactly("ap-9", "SUCCEEDED");
    }

    /** DENY 不记审批，也不把这次工具交给后续执行。同一次里的其它工具仍保留。 */
    @Test
    void denyDropsOnlyThatTool() {
        List<ApprovalRequestedEvent> published = new ArrayList<>();
        List<String> recorded = new ArrayList<>();
        PermissionRuleService permissions = (toolId, tenantId, roleId) ->
                "shell".equals(toolId) ? PermissionDecision.DENY : PermissionDecision.ALLOW;
        ApprovalRecorder approvals = (approvalId, toolId, userId, sessionId, tenantId) -> recorded.add(toolId);
        ApplicationEventPublisher events = event -> published.add((ApprovalRequestedEvent) event);
        ApprovalMiddleware middleware = new ApprovalMiddleware(permissions, approvals, events);
        ToolUseBlock shell = new ToolUseBlock.Builder().id("tool-1").name("shell").input(Map.of()).build();
        ToolUseBlock http = new ToolUseBlock.Builder().id("tool-2").name("http").input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        middleware.onActing(mock(Agent.class), context, new ActingInput(List.of(shell, http)), input -> {
                    forwarded.set(input.toolCalls());
                    return Flux.empty();
                })
                .blockLast();

        assertThat(recorded).isEmpty();
        assertThat(published).isEmpty();
        assertThat(forwarded.get()).extracting(ToolUseBlock::getName).containsExactly("http");
    }

    private static ToolUseBlock act(
            PermissionDecision decision,
            List<ApprovalRequestedEvent> published,
            List<String> recorded,
            String toolName) {
        PermissionRuleService permissions = (toolId, tenantId, roleId) -> decision;
        ApprovalRecorder approvals = (approvalId, toolId, userId, sessionId, tenantId) -> recorded.add(toolId);
        ApplicationEventPublisher events = event -> published.add((ApprovalRequestedEvent) event);
        ApprovalMiddleware middleware = new ApprovalMiddleware(permissions, approvals, events);
        ToolUseBlock use = new ToolUseBlock.Builder().id("tool-1").name(toolName).input(Map.of()).build();
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        AtomicReference<ToolUseBlock> forwarded = new AtomicReference<>();
        middleware.onActing(mock(Agent.class), context, new ActingInput(List.of(use)), input -> {
                    forwarded.set(input.toolCalls().get(0));
                    return Flux.empty();
                })
                .blockLast();
        return forwarded.get();
    }
}
