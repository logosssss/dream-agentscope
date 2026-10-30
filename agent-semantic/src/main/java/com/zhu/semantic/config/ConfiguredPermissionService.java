package com.zhu.semantic.config;

import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 按配置匹配工具权限。没有命中的规则时返回 ALLOW。
 * 容器里只能有这一个 PermissionRuleService。
 */
@Service
public class ConfiguredPermissionService implements PermissionRuleService {

    private static final String ANY = "*";

    private final List<CompiledRule> rules;

    /**
     * 装配时检查每条规则。决定写错、工具名为空、租户或角色为空，都拒绝启动。
     */
    public ConfiguredPermissionService(PermissionProperties properties) {
        List<CompiledRule> compiled = new ArrayList<>();
        List<PermissionProperties.Rule> source = properties.getRules();
        for (int i = 0; i < source.size(); i++) {
            compiled.add(compile(i, source.get(i)));
        }
        this.rules = List.copyOf(compiled);
    }

    /**
     * 先按工具、租户、角色筛选。多条同时命中时，priority 更小的赢。
     * priority 相同则写明租户或角色的更具体。还相同则 DENY 高于 ASK，ASK 高于 ALLOW。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId) {
        if (toolId == null || toolId.isBlank()) {
            return PermissionDecision.ALLOW;
        }
        CompiledRule winner = null;
        for (CompiledRule rule : rules) {
            if (!rule.matches(toolId, tenantId, roleId)) {
                continue;
            }
            if (winner == null || rule.outranks(winner)) {
                winner = rule;
            }
        }
        return winner == null ? PermissionDecision.ALLOW : winner.decision;
    }

    /** 把一条配置收成不可变规则。下标只用于报错，方便看出是第几条。 */
    private static CompiledRule compile(int index, PermissionProperties.Rule rule) {
        if (rule == null || rule.getToolId() == null || rule.getToolId().isBlank()) {
            throw new IllegalStateException("permission rule " + index + " requires tool-id");
        }
        String tenantId = requiredScope(index, "tenant-id", rule.getTenantId());
        String roleId = requiredScope(index, "role-id", rule.getRoleId());
        PermissionDecision decision = decision(index, rule.getDecision());
        return new CompiledRule(
                rule.getToolId(),
                tenantId,
                roleId,
                decision,
                rule.getPriority(),
                rule.isEnabled());
    }

    /** 范围不能空。* 是显式的全部，不是漏配。 */
    private static String requiredScope(int index, String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("permission rule " + index + " requires " + field);
        }
        return value;
    }

    /** 只接受三种决定。大小写必须与枚举一致。 */
    private static PermissionDecision decision(int index, String raw) {
        if (raw == null) {
            throw new IllegalStateException("permission rule " + index + " requires decision");
        }
        try {
            return PermissionDecision.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("permission rule " + index + " has unknown decision");
        }
    }

    /**
     * 一条已经校验过的规则。
     */
    private static final class CompiledRule {

        private final String toolId;

        private final String tenantId;

        private final String roleId;

        private final PermissionDecision decision;

        private final int priority;

        private final boolean enabled;

        private CompiledRule(
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

        /** 工具名精确匹配。* 只放行对应维度，不匹配别的工具。 */
        private boolean matches(String tool, String tenant, String role) {
            if (!enabled || !toolId.equals(tool)) {
                return false;
            }
            return scopeMatches(tenantId, tenant) && scopeMatches(roleId, role);
        }

        /** priority 更小，或同样优先但更具体、更严。 */
        private boolean outranks(CompiledRule other) {
            if (priority != other.priority) {
                return priority < other.priority;
            }
            if (specificity() != other.specificity()) {
                return specificity() > other.specificity();
            }
            return strictness(decision) > strictness(other.decision);
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
    }

    /** * 匹配任意值，包括调用方没带租户或角色。 */
    private static boolean scopeMatches(String ruleScope, String actual) {
        if (ANY.equals(ruleScope)) {
            return true;
        }
        return ruleScope.equals(actual);
    }

    /** 冲突时拒绝比询问更严，询问比允许更严。 */
    private static int strictness(PermissionDecision decision) {
        if (decision == PermissionDecision.DENY) {
            return 2;
        }
        if (decision == PermissionDecision.ASK) {
            return 1;
        }
        return 0;
    }
}
