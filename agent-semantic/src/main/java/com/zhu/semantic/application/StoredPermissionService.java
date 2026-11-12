package com.zhu.semantic.application;

import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.PermissionRuleService;
import com.zhu.semantic.domain.PermissionConditions;
import com.zhu.semantic.domain.PermissionMatcher;
import com.zhu.semantic.domain.PermissionRule;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 按 tool_permission 表匹配工具权限。MySQL 打开时创建，替换配置里的规则。
 * 时间窗按 UTC；参数长度看 maxLen / minLen；不能解释的 JSON 仍跳过整条，避免当成无条件生效。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class StoredPermissionService implements PermissionRuleService {

    private static final Logger log = Logger.getLogger(StoredPermissionService.class.getName());

    private final ToolPermissionMapper rules;

    private final Clock clock;

    /** 生产用系统 UTC。测试再拨时钟。不要注入容器里的 Clock，避免和额度时钟绑在一起。 */
    @Autowired
    public StoredPermissionService(ToolPermissionMapper rules) {
        this(rules, Clock.systemUTC());
    }

    /** 测试注入时钟，验证 after / before 是否命中。 */
    public StoredPermissionService(ToolPermissionMapper rules, Clock clock) {
        this.rules = rules;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 无入参时按缺参数长度 0 解释条件，和对话前预判一致。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId) {
        return decide(toolId, tenantId, roleId, null);
    }

    /**
     * 只读这个工具仍启用的行。命中的决定写错时拒绝这次调用，不能当成允许。
     * 表是空的则允许，和没写配置时一样。
     */
    @Override
    public PermissionDecision decide(String toolId, String tenantId, String roleId, Map<String, ?> params) {
        if (toolId == null || toolId.isBlank()) {
            return PermissionDecision.ALLOW;
        }
        List<PermissionRule> matched = new ArrayList<>();
        for (ToolPermission row : rules.selectEnabled(toolId)) {
            PermissionRule rule = toRule(row, toolId, tenantId, roleId, clock, params);
            if (rule != null) {
                matched.add(rule);
            }
        }
        return PermissionMatcher.decide(matched, toolId, tenantId, roleId);
    }

    /**
     * 没命中的行直接丢掉。时间窗未到、参数超界或 JSON 不能解释时也丢掉。
     * 命中且决定写错时抛出，不能把拒绝配错后当成允许。
     */
    private static PermissionRule toRule(
            ToolPermission row,
            String toolId,
            String tenantId,
            String roleId,
            Clock clock,
            Map<String, ?> params) {
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
        PermissionConditions.Outcome outcome = PermissionConditions.evaluate(row.getConditions(), clock, params);
        if (outcome == PermissionConditions.Outcome.UNKNOWN) {
            log.warning("permission rule with conditions was skipped");
            return null;
        }
        if (outcome == PermissionConditions.Outcome.MISS) {
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
