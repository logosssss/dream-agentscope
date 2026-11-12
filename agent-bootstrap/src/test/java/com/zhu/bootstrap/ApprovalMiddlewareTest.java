package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Permission Gate 落地 ②：产品条件闸收缩后的行为。
 * 不再落单 / claim / 标 ASKING；产品 ASK 当 DENY。
 */
class ApprovalMiddlewareTest {

    /** ALLOW 原样下放，不改状态。 */
    @Test
    void allowPassesThrough() {
        ToolUseBlock seen = act(PermissionDecision.ALLOW, "shell");
        assertThat(seen).isNotNull();
        assertThat(seen.getName()).isEqualTo("shell");
        assertThat(seen.getState()).isNotEqualTo(ToolCallState.ASKING);
    }

    /** 产品 ASK 运行期当 DENY，工具从输入拿掉。 */
    @Test
    void productAskDropsTool() {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        actList(
                (toolId, tenantId, roleId) -> PermissionDecision.ASK,
                List.of(use("tool-1", "shell")),
                forwarded);
        assertThat(forwarded.get()).isEmpty();
    }

    /** DENY 只丢掉该工具，同批其它 ALLOW 仍保留。 */
    @Test
    void denyDropsOnlyThatTool() {
        PermissionRuleService permissions = (toolId, tenantId, roleId) ->
                "shell".equals(toolId) ? PermissionDecision.DENY : PermissionDecision.ALLOW;
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        actList(permissions, List.of(use("tool-1", "shell"), use("tool-2", "http")), forwarded);
        assertThat(forwarded.get()).extracting(ToolUseBlock::getName).containsExactly("http");
    }

    /** asAllowOrDeny：null/ALLOW→ALLOW，ASK/DENY→DENY。 */
    @Test
    void asAllowOrDenyMapping() {
        assertThat(ApprovalMiddleware.asAllowOrDeny(null)).isEqualTo(PermissionDecision.ALLOW);
        assertThat(ApprovalMiddleware.asAllowOrDeny(PermissionDecision.ALLOW))
                .isEqualTo(PermissionDecision.ALLOW);
        assertThat(ApprovalMiddleware.asAllowOrDeny(PermissionDecision.ASK))
                .isEqualTo(PermissionDecision.DENY);
        assertThat(ApprovalMiddleware.asAllowOrDeny(PermissionDecision.DENY))
                .isEqualTo(PermissionDecision.DENY);
    }

    private static ToolUseBlock act(PermissionDecision decision, String toolName) {
        AtomicReference<List<ToolUseBlock>> forwarded = new AtomicReference<>();
        actList((toolId, tenantId, roleId) -> decision, List.of(use("tool-1", toolName)), forwarded);
        List<ToolUseBlock> calls = forwarded.get();
        return calls == null || calls.isEmpty() ? null : calls.get(0);
    }

    private static void actList(
            PermissionRuleService permissions,
            List<ToolUseBlock> tools,
            AtomicReference<List<ToolUseBlock>> forwarded) {
        ApprovalMiddleware middleware = new ApprovalMiddleware(permissions);
        RuntimeContext context = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .build();
        middleware
                .onActing(mock(Agent.class), context, new ActingInput(tools), input -> {
                    forwarded.set(input.toolCalls());
                    return Flux.empty();
                })
                .blockLast();
    }

    private static ToolUseBlock use(String id, String name) {
        return new ToolUseBlock.Builder().id(id).name(name).input(Map.of()).build();
    }
}
