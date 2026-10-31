package com.zhu.semantic.domain;

import com.zhu.semantic.contract.PermissionDecision;
import java.util.List;

/**
 * 从一组规则里选出一次调用的决定。配置和数据表共用同一套比较。
 */
public final class PermissionMatcher {

    private PermissionMatcher() {
    }

    /**
     * 工具名为空时直接允许，避免空调用被某条规则误伤。
     * 没有命中时允许。多条命中时 priority 更小的赢。
     */
    public static PermissionDecision decide(List<PermissionRule> rules, String toolId, String tenantId, String roleId) {
        if (toolId == null || toolId.isBlank() || rules == null || rules.isEmpty()) {
            return PermissionDecision.ALLOW;
        }
        PermissionRule winner = null;
        for (PermissionRule rule : rules) {
            if (rule == null || !rule.matches(toolId, tenantId, roleId)) {
                continue;
            }
            if (winner == null || rule.outranks(winner)) {
                winner = rule;
            }
        }
        return winner == null ? PermissionDecision.ALLOW : winner.decision();
    }
}
