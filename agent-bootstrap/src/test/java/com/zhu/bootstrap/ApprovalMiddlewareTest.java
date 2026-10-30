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

    private static ToolUseBlock act(
            PermissionDecision decision,
            List<ApprovalRequestedEvent> published,
            List<String> recorded,
            String toolName) {
        PermissionRuleService permissions = (toolId, tenantId, roleId) -> decision;
        ApprovalRecorder approvals = (approvalId, toolId, userId) -> recorded.add(toolId);
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
