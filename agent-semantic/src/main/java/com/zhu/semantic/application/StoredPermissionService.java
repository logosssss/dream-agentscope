package com.zhu.semantic.application;

import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.contract.PermissionRuleService;
import com.zhu.semantic.domain.PermissionMatcher;
import com.zhu.semantic.domain.PermissionRule;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按 tool_permission 表匹配工具权限。MySQL 打开时创建，替换配置里的规则。
 * 带条件的行先跳过，避免把还不能解释的条件当成无条件生效。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class StoredPermissionService implements PermissionRuleService {

    private static final Logger log = Logger.getLogger(StoredPermissionService.class.getName());

    private final ToolPermissionMapper rules;

    public StoredPermissionService(ToolPermissionMapper rules) {
        this.rules = rules;
    }

    /**
     * 只读这个工具仍启用的行。命中的决定写错时拒绝这次调用，不能当成允许。
     * 表是空的则允许，和没写配置时一样。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId) {
        if (toolId == null || toolId.isBlank()) {
            return PermissionDecision.ALLOW;
        }
        List<PermissionRule> matched = new ArrayList<>();
        for (ToolPermission row : rules.selectEnabled(toolId)) {
            PermissionRule rule = toRule(row, toolId, tenantId, roleId);
            if (rule != null) {
                matched.add(rule);
            }
        }
        return PermissionMatcher.decide(matched, toolId, tenantId, roleId);
    }

    /**
     * 没命中的行直接丢掉。命中但带条件的行跳过。
     * 命中且决定写错时抛出，不能把拒绝配错后当成允许。
     */
    private static PermissionRule toRule(ToolPermission row, String toolId, String tenantId, String roleId) {
        if (row == null || blank(row.getToolId()) || blank(row.getTenantId()) || blank(row.getRoleId())) {
            return null;
        }
        int priority = row.getPriority() == null ? 100 : row.getPriority();
        boolean enabled = row.getEnabled() == null || row.getEnabled();
        PermissionRule probe = new PermissionRule(
                row.getToolId(), row.getTenantId(), row.getRoleId(), PermissionDecision.ALLOW, priority, enabled);
        if (!probe.matches(toolId, tenantId, roleId)) {
            return null;
        }
        if (!blank(row.getConditions())) {
            log.warning("permission rule with conditions was skipped");
            return null;
        }
        PermissionDecision decision = decision(row.getDecision());
        if (decision == null) {
            throw new IllegalStateException("permission rule has unknown decision");
        }
        return new PermissionRule(row.getToolId(), row.getTenantId(), row.getRoleId(), decision, priority, enabled);
    }

    /** 只接受三种决定。写错返回 null，由调用方决定是否中断。 */
    private static PermissionDecision decision(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return PermissionDecision.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 空白范围不能当成全部租户。全部必须写成 *。 */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
