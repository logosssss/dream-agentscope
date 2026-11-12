package com.zhu.runtime.domain.middleware;

import com.zhu.audit.api.approval.ApprovalRecorder;
import com.zhu.audit.domain.event.ApprovalRequestedEvent;
import com.zhu.common.util.Ids;
import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 工具执行前的审批双闸。引擎管无条件规则，产品闸管租户/角色/conditions；合并取更严。
 * ASK 时落审批单并发布事件；claim 只能把 ASK 抬成放行，不能救 DENY。
 */
@Component
public class ApprovalMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(ApprovalMiddleware.class.getName());

    /** 引擎 checkPermission 上限；超时按引擎侧 ALLOW，避免卡死整轮 acting。 */
    private static final Duration ENGINE_TIMEOUT = Duration.ofSeconds(5);

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
     * 逐个工具：引擎 + 产品闸更严合并。ASK 标 ASKING 并落单；DENY 从输入拿掉；claim 仅覆盖 ASK。
     * 拿不到 {@link PermissionEngine} 时引擎侧按 ALLOW（单测 mock Agent、尚未挂 context 的路径）。
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
        PermissionEngine engine = PermissionEngineAccess.of(agent);
        List<ToolUseBlock> rewritten = new ArrayList<>(calls.size());
        List<String> claimed = new ArrayList<>();
        for (ToolUseBlock use : calls) {
            keep(use, identity, engine, rewritten, claimed);
        }
        try {
            return next.apply(new ActingInput(rewritten)).doFinally(signal -> finish(claimed, signal));
        } catch (RuntimeException ex) {
            finish(claimed, SignalType.ON_ERROR);
            throw ex;
        }
    }

    /**
     * 工具名为空无法匹配，原样留下。合并为 DENY 不记审批。
     * 合并为 ASK 时：先 claim 已批准；否则复用 PENDING 或新开单并标 ASKING。
     */
    private void keep(
            ToolUseBlock use,
            CallIdentity identity,
            PermissionEngine engine,
            List<ToolUseBlock> out,
            List<String> claimed) {
        if (use == null || use.getName() == null || use.getName().isBlank()) {
            out.add(use);
            return;
        }
        PermissionDecision engineDecision = engineDecide(engine, use);
        // 入参交给产品条件里的 maxLen / minLen；配置实现会忽略 Map。
        PermissionDecision productDecision =
                permissions.decide(use.getName(), identity.tenantId(), null, use.getInput());
        PermissionDecision decision = stricter(engineDecision, productDecision);
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
        String pending = approvals.findPending(tenantId, userId, sessionId, use.getName());
        if (pending != null) {
            out.add(use.withState(ToolCallState.ASKING));
            return;
        }
        String approvalId = Ids.newId();
        approvals.requested(approvalId, use.getName(), userId, sessionId, tenantId);
        events.publishEvent(new ApprovalRequestedEvent(
                approvalId, use.getName(), identity.tenantId(), identity.userId()));
        out.add(use.withState(ToolCallState.ASKING));
    }

    /**
     * 无引擎或调用失败时按 ALLOW，把判定权留给产品闸，避免误 DENY。
     */
    private static PermissionDecision engineDecide(PermissionEngine engine, ToolUseBlock use) {
        if (engine == null) {
            return PermissionDecision.ALLOW;
        }
        try {
            Map<String, Object> params = paramsOf(use);
            io.agentscope.core.permission.PermissionDecision raw = engine
                    .checkPermission(new ApprovalProbeTool(use.getName()), params)
                    .block(ENGINE_TIMEOUT);
            return toProduct(raw);
        } catch (RuntimeException ex) {
            log.warning("permission engine check failed; treat as ALLOW");
            return PermissionDecision.ALLOW;
        }
    }

    /** DENY > ASK > ALLOW。双闸合并与单测共用。 */
    public static PermissionDecision stricter(PermissionDecision left, PermissionDecision right) {
        PermissionDecision a = left == null ? PermissionDecision.ALLOW : left;
        PermissionDecision b = right == null ? PermissionDecision.ALLOW : right;
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(PermissionDecision decision) {
        if (decision == PermissionDecision.DENY) {
            return 2;
        }
        if (decision == PermissionDecision.ASK) {
            return 1;
        }
        return 0;
    }

    private static PermissionDecision toProduct(io.agentscope.core.permission.PermissionDecision raw) {
        if (raw == null || raw.getBehavior() == null) {
            return PermissionDecision.ALLOW;
        }
        PermissionBehavior behavior = raw.getBehavior();
        if (behavior == PermissionBehavior.DENY) {
            return PermissionDecision.DENY;
        }
        if (behavior == PermissionBehavior.ASK) {
            return PermissionDecision.ASK;
        }
        return PermissionDecision.ALLOW;
    }

    private static Map<String, Object> paramsOf(ToolUseBlock use) {
        Map<String, Object> input = use.getInput();
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(input);
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
