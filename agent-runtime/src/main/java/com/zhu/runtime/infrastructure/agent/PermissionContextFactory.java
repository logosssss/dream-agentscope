package com.zhu.runtime.infrastructure.agent;

import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.UnconditionalPermissionCatalog;
import com.zhu.semantic.contract.permission.UnconditionalToolRule;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 运行层：把产品无条件规则编成框架 {@link PermissionContextState}。
 * mode 固定 {@link PermissionMode#BYPASS}（未匹配 ALLOW）；ruleContent 固定 null 才能命中 ToolBase.matchRule。
 * 只在 build / 排空重建时调用，不在 acting 热路径改引擎。
 */
@Component
public class PermissionContextFactory {

    /**
     * 当目录为空时用来撑非 trivial 的占位工具名。
     * ruleContent 非 null，永远不会被 matchRule 命中。
     */
    static final String NON_TRIVIAL_MARKER = "__agentscope_permission_context__";

    private final UnconditionalPermissionCatalog catalog;

    /** 目录在 semantic 按 MySQL 开关二选一装配。 */
    public PermissionContextFactory(UnconditionalPermissionCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 每次 build 现读目录，便于热更后排空重建生效。
     * 无规则时仍挂 BYPASS，并加一条永不命中的 ALLOW 占位，避免 context 落回 trivial。
     */
    public PermissionContextState create() {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS);
        List<UnconditionalToolRule> rules = catalog.list();
        boolean injected = false;
        if (rules != null) {
            for (UnconditionalToolRule rule : rules) {
                if (rule == null || blank(rule.toolId()) || rule.decision() == null) {
                    continue;
                }
                add(builder, rule.toolId().trim(), rule.decision());
                injected = true;
            }
        }
        if (!injected) {
            builder.addAllowRule(
                    NON_TRIVIAL_MARKER,
                    new PermissionRule(
                            NON_TRIVIAL_MARKER,
                            "keep-non-trivial",
                            PermissionBehavior.ALLOW,
                            "agentscope"));
        }
        return builder.build();
    }

    /**
     * 产品决定映射到引擎对应桶。ruleContent 必须为 null。
     */
    private static void add(
            PermissionContextState.Builder builder, String toolId, PermissionDecision decision) {
        PermissionBehavior behavior = toBehavior(decision);
        PermissionRule engineRule = new PermissionRule(toolId, null, behavior, "agentscope");
        switch (behavior) {
            case ALLOW -> builder.addAllowRule(toolId, engineRule);
            case ASK -> builder.addAskRule(toolId, engineRule);
            case DENY -> builder.addDenyRule(toolId, engineRule);
            default -> {
                // PASSTHROUGH 不在产品三态里，忽略。
            }
        }
    }

    private static PermissionBehavior toBehavior(PermissionDecision decision) {
        return switch (decision) {
            case ALLOW -> PermissionBehavior.ALLOW;
            case ASK -> PermissionBehavior.ASK;
            case DENY -> PermissionBehavior.DENY;
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
