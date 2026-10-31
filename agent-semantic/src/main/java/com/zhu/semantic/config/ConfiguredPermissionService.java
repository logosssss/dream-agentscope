package com.zhu.semantic.config;

import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import com.zhu.semantic.domain.PermissionMatcher;
import com.zhu.semantic.domain.PermissionRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按配置匹配工具权限。MySQL 关闭或没写开关时使用，测试走这条。
 * MySQL 打开后由数据表实现接管，容器里仍然只有一个 PermissionRuleService。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class ConfiguredPermissionService implements PermissionRuleService {

    private final List<PermissionRule> rules;

    /**
     * 装配时检查每条规则。决定写错、工具名为空、租户或角色为空，都拒绝启动。
     */
    public ConfiguredPermissionService(PermissionProperties properties) {
        List<PermissionRule> compiled = new ArrayList<>();
        List<PermissionProperties.Rule> source = properties.getRules();
        for (int i = 0; i < source.size(); i++) {
            compiled.add(compile(i, source.get(i)));
        }
        this.rules = List.copyOf(compiled);
    }

    /**
     * 比较交给 {@link PermissionMatcher}，和数据表使用同一套顺序。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId) {
        return PermissionMatcher.decide(rules, toolId, tenantId, roleId);
    }

    /** 把一条配置收成不可变规则。下标只用于报错，方便看出是第几条。 */
    private static PermissionRule compile(int index, PermissionProperties.Rule rule) {
        if (rule == null || rule.getToolId() == null || rule.getToolId().isBlank()) {
            throw new IllegalStateException("permission rule " + index + " requires tool-id");
        }
        String tenantId = requiredScope(index, "tenant-id", rule.getTenantId());
        String roleId = requiredScope(index, "role-id", rule.getRoleId());
        return new PermissionRule(
                rule.getToolId(),
                tenantId,
                roleId,
                decision(index, rule.getDecision()),
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
}
