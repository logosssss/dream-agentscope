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
import java.util.logging.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 工具执行前的审批。ASK 时落审批单并发布事件，接入层订阅后推送。运行层不依赖接入层。
 * 用 Middleware，不用已标删除的 Hook。
 */
@Component
public class ApprovalMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(ApprovalMiddleware.class.getName());

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
     * 逐个工具看产品规则。ASK 的调用换成 ASKING 再交给后续中间件。DENY 的调用从这次输入里拿掉。
     * 已批准且还没执行的单会放行这次同名工具，结束后回写状态。管理进程不执行工具。
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
        List<String> claimed = new ArrayList<>();
        for (ToolUseBlock use : calls) {
            keep(use, identity, rewritten, claimed);
        }
        try {
            return next.apply(new ActingInput(rewritten)).doFinally(signal -> finish(claimed, signal));
        } catch (RuntimeException ex) {
            finish(claimed, SignalType.ON_ERROR);
            throw ex;
        }
    }

    /**
     * 工具名为空无法匹配规则，原样留下。DENY 不记审批，也不交给后续执行。
     * 有未执行的批准单时原样放行。否则 ASK 记单、发事件，并把状态标成等待确认。
     */
    private void keep(ToolUseBlock use, CallIdentity identity, List<ToolUseBlock> out, List<String> claimed) {
        if (use == null || use.getName() == null || use.getName().isBlank()) {
            out.add(use);
            return;
        }
        PermissionDecision decision = permissions.decide(use.getName(), identity.tenantId(), null);
        if (decision == PermissionDecision.DENY) {
            return;
        }
        if (decision != PermissionDecision.ASK) {
            out.add(use);
            return;
        }
        String userId = identity.userId() == null ? "" : identity.userId();
        String tenantId = identity.tenantId() == null ? "" : identity.tenantId();
        String sessionId = identity.sessionId() == null ? "" : identity.sessionId();
        String approved = approvals.claimApproved(tenantId, userId, sessionId, use.getName());
        if (approved != null) {
            claimed.add(approved);
            out.add(use);
            return;
        }
        String approvalId = Ids.newId();
        approvals.requested(approvalId, use.getName(), userId, sessionId, tenantId);
        events.publishEvent(new ApprovalRequestedEvent(
                approvalId, use.getName(), identity.tenantId(), identity.userId()));
        out.add(use.withState(ToolCallState.ASKING));
    }

    /**
     * 回写失败只记日志。工具调用已经结束，不能再因为记账失败把结果改掉。
     */
    private void finish(List<String> claimed, SignalType signal) {
        if (claimed.isEmpty()) {
            return;
        }
        String status = signal == SignalType.ON_COMPLETE ? "SUCCEEDED"
                : signal == SignalType.CANCEL ? "CANCELLED" : "FAILED";
        for (String approvalId : claimed) {
            try {
                approvals.executed(approvalId, status);
            } catch (RuntimeException ex) {
                log.warning("approval execution record failed");
            }
        }
    }
}
