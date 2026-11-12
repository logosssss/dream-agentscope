package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.domain.CallIdentity;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 运行层：产品条件闸（租户/角色/conditions）。Permission Gate **运行入口 = 当前 A**。
 * 无条件判定交框架 PermissionGate；不再 {@code checkPermission}、不落审批单、不 claim、不发审批事件。
 * 产品侧 ASK 运行期按 DENY。见 docs/permission-gate-落地.md。
 */
@Component
public class ApprovalMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(ApprovalMiddleware.class.getName());

    private final PermissionRuleService permissions;

    public ApprovalMiddleware(PermissionRuleService permissions) {
        this.permissions = permissions;
    }

    /**
     * 逐个工具只问产品闸：ALLOW 原样下放；DENY 或 ASK 从本次输入拿掉。
     * 无条件 ASK/DENY/ALLOW 由后续 Gate 处理，这里不得二次判引擎。
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
            keep(use, identity, rewritten);
        }
        return next.apply(new ActingInput(rewritten));
    }

    /**
     * 工具名为空无法匹配，原样留下。产品 DENY/ASK 丢弃；仅 ALLOW 留下。
     */
    private void keep(ToolUseBlock use, CallIdentity identity, List<ToolUseBlock> out) {
        if (use == null || use.getName() == null || use.getName().isBlank()) {
            out.add(use);
            return;
        }
        // 入参交给产品条件里的 maxLen / minLen；配置实现会忽略 Map。
        PermissionDecision product =
                permissions.decide(use.getName(), identity.tenantId(), null, use.getInput());
        PermissionDecision effective = asAllowOrDeny(product);
        if (effective == PermissionDecision.DENY) {
            if (product == PermissionDecision.ASK) {
                log.info("product ASK treated as DENY for tool " + use.getName());
            }
            return;
        }
        out.add(use);
    }

    /**
     * 产品闸对外只保留 ALLOW / DENY；ASK 收成 DENY，避免无审批表可 claim。
     */
    public static PermissionDecision asAllowOrDeny(PermissionDecision decision) {
        if (decision == null || decision == PermissionDecision.ALLOW) {
            return PermissionDecision.ALLOW;
        }
        return PermissionDecision.DENY;
    }
}
