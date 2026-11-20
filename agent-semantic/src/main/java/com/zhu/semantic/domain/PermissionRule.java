package com.zhu.semantic.domain;

import com.zhu.semantic.contract.permission.PermissionDecision;

/**
 * 一条已经校验过的工具权限规则。产品语义内部使用，运行层只看决定结果。
 */
public final class PermissionRule {

    private static final String ANY = "*";

    private final String toolId;

    private final String tenantId;

    private final String roleId;

    private final PermissionDecision decision;

    private final int priority;

    private final boolean enabled;

    /**
     * 调用方要先保证工具名、范围和决定都有效。
     */
    public PermissionRule(
            String toolId,
            String tenantId,
            String roleId,
            PermissionDecision decision,
            int priority,
            boolean enabled) {
        this.toolId = toolId;
        this.tenantId = tenantId;
        this.roleId = roleId;
        this.decision = decision;
        this.priority = priority;
        this.enabled = enabled;
    }

    /** 这条规则的决定。调用方只在命中后读取。 */
    public PermissionDecision decision() {
        return decision;
    }

    /** 工具名精确匹配。* 只放行对应维度，不匹配别的工具。 */
    public boolean matches(String tool, String tenant, String role) {
        if (!enabled || toolId == null || !toolId.equals(tool)) {
            return false;
        }
        return scopeMatches(tenantId, tenant) && scopeMatches(roleId, role);
    }

    /** priority 更小，或同样优先但更具体、更严。 */
    public boolean outranks(PermissionRule other) {
        if (priority != other.priority) {
            return priority < other.priority;
        }
        if (specificity() != other.specificity()) {
            return specificity() > other.specificity();
        }
        return strictness(decision) > strictness(other.decision);
    }

    /** * 匹配任意值，包括调用方没带租户或角色。 */
    private static boolean scopeMatches(String ruleScope, String actual) {
        if (ANY.equals(ruleScope)) {
            return true;
        }
        return ruleScope != null && ruleScope.equals(actual);
    }

    /** 写明租户和角色各算一分。* 不加分。 */
    private int specificity() {
        int score = 0;
        if (!ANY.equals(tenantId)) {
            score++;
        }
        if (!ANY.equals(roleId)) {
            score++;
        }
        return score;
    }

    /** 冲突时拒绝比询问更严，询问比允许更严。 */
    private static int strictness(PermissionDecision value) {
        if (value == PermissionDecision.DENY) {
            return 2;
        }
        if (value == PermissionDecision.ASK) {
            return 1;
        }
        return 0;
    }
}
