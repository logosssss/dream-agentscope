package com.zhu.runtime.domain.middleware;

import com.zhu.audit.api.ApprovalRecorder;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.common.util.Ids;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 工具执行前的审批。ASK 时落审批单并发布事件，接入层订阅后推送。运行层不依赖接入层。
 * 用 Middleware，不用已标删除的 Hook。
 */
@Component
public class ApprovalMiddleware implements MiddlewareBase {

    private final PermissionRuleService permissions;

    private final ApprovalRecorder approvals;

    private final ApplicationEventPublisher events;

    public ApprovalMiddleware(
            PermissionRuleService permissions,
            ApprovalRecorder approvals,
            ApplicationEventPublisher events) {
        this.permissions = permissions;
        this.approvals = approvals;
        this.events = events;
    }

    /**
     * 逐个工具看产品规则。ASK 的调用换成 ASKING 再交给后续中间件。
     * 不往 PermissionEngine 追加规则：默认上下文是 trivial，引擎规则这次判定读不到；
     * 一旦把上下文改成非 trivial，没匹配到的工具会走默认 ASK，误伤同一次调用里的其它工具。
     */
    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext context,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        List<ToolUseBlock> calls = input.toolCalls();
        if (calls == null || calls.isEmpty()) {
            return next.apply(input);
        }
        CallIdentity identity = CallIdentity.from(context);
        List<ToolUseBlock> rewritten = new ArrayList<>(calls.size());
        for (ToolUseBlock use : calls) {
            rewritten.add(holdIfAsk(use, identity));
        }
        return next.apply(new ActingInput(rewritten));
    }

    /** 工具名为空无法匹配规则，原样放过。ASK 时记单、发事件，并把状态标成等待确认。 */
    private ToolUseBlock holdIfAsk(ToolUseBlock use, CallIdentity identity) {
        if (use == null || use.getName() == null || use.getName().isBlank()) {
            return use;
        }
        PermissionDecision decision = permissions.decide(use.getName(), identity.tenantId(), null);
        if (decision != PermissionDecision.ASK) {
            return use;
        }
        String approvalId = Ids.newId();
        String userId = identity.userId() == null ? "" : identity.userId();
        approvals.requested(approvalId, use.getName(), userId);
        events.publishEvent(new ApprovalRequestedEvent(
                approvalId, use.getName(), identity.tenantId(), identity.userId()));
        return use.withState(ToolCallState.ASKING);
    }
}
