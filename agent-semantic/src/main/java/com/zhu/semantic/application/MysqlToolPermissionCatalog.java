package com.zhu.semantic.application;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.tool.ToolPermissionCatalog;
import com.zhu.semantic.api.tool.ToolPermissionSummary;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 登记、停用、恢复和改优先级工具权限规则。只在存储开关打开时创建。不执行工具。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlToolPermissionCatalog implements ToolPermissionCatalog {

    private static final String GLOBAL = "*";

    private static final int DEFAULT_PRIORITY = 100;

    private static final int ID_LIMIT = 128;

    private static final int SCOPE_LIMIT = 64;

    private static final Set<String> DECISIONS = Set.of("ALLOW", "ASK", "DENY");

    private final ToolPermissionMapper rules;

    /** 同一工具+租户+角色由唯一键拒绝。 */
    public MysqlToolPermissionCatalog(ToolPermissionMapper rules) {
        this.rules = rules;
    }

    /**
     * 空白租户/角色按全局 `*`。空白优先级按 100。
     * conditions 固定为空，避免登记后因未识别 JSON 被整条跳过却难以排查。
     */
    @Override
    public ToolPermissionSummary register(
            String toolId, String tenantId, String roleId, String decision, Integer priority) {
        ScopeKey key = scopeKey(toolId, tenantId, roleId);
        if (decision == null || decision.isBlank() || !DECISIONS.contains(decision)
                || (priority != null && priority < 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool permission is invalid");
        }
        int savedPriority = priority == null ? DEFAULT_PRIORITY : priority;
        ToolPermission row = new ToolPermission();
        row.setToolId(key.toolId());
        row.setTenantId(key.tenantId());
        row.setRoleId(key.roleId());
        row.setDecision(decision);
        row.setConditions(null);
        row.setPriority(savedPriority);
        row.setEnabled(true);
        try {
            rules.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool permission already exists");
        }
        return new ToolPermissionSummary(
                key.toolId(), key.tenantId(), key.roleId(), decision, savedPriority, true);
    }

    /**
     * 先确认存在且启用，再按启用行更新，避免覆盖已停用行。
     */
    @Override
    public ToolPermissionSummary disable(String toolId, String tenantId, String roleId) {
        ScopeKey key = scopeKey(toolId, tenantId, roleId);
        ToolPermission existing = require(key);
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool permission is not enabled");
        }
        int updated = rules.disableEnabled(key.toolId(), key.tenantId(), key.roleId());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool permission is not enabled");
        }
        return summary(existing, false);
    }

    /**
     * 先确认存在且停用，再按停用行更新，避免覆盖已启用行。
     */
    @Override
    public ToolPermissionSummary restore(String toolId, String tenantId, String roleId) {
        ScopeKey key = scopeKey(toolId, tenantId, roleId);
        ToolPermission existing = require(key);
        if (Boolean.TRUE.equals(existing.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool permission is already enabled");
        }
        int updated = rules.restoreDisabled(key.toolId(), key.tenantId(), key.roleId());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tool permission is already enabled");
        }
        return summary(existing, true);
    }

    /**
     * 改优先级。优先级不能为空且不能小于 0。不改决定与启用。
     */
    @Override
    public ToolPermissionSummary updatePriority(
            String toolId, String tenantId, String roleId, Integer priority) {
        ScopeKey key = scopeKey(toolId, tenantId, roleId);
        if (priority == null || priority < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool permission is invalid");
        }
        ToolPermission existing = require(key);
        int updated = rules.updatePriority(key.toolId(), key.tenantId(), key.roleId(), priority);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tool permission not found");
        }
        return new ToolPermissionSummary(
                existing.getToolId(),
                existing.getTenantId() == null ? GLOBAL : existing.getTenantId(),
                existing.getRoleId() == null ? GLOBAL : existing.getRoleId(),
                existing.getDecision(),
                priority,
                Boolean.TRUE.equals(existing.getEnabled()));
    }

    /**
     * 空结果返回空列表。不按启用再滤一层，停用也要能看见。
     * 不带回 conditions。
     */
    @Override
    public List<ToolPermissionSummary> recent(int limit) {
        List<ToolPermission> rows = rules.recent(CatalogQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new ToolPermissionSummary(
                        row.getToolId(),
                        row.getTenantId() == null ? GLOBAL : row.getTenantId(),
                        row.getRoleId() == null ? GLOBAL : row.getRoleId(),
                        row.getDecision(),
                        row.getPriority() == null ? DEFAULT_PRIORITY : row.getPriority(),
                        Boolean.TRUE.equals(row.getEnabled())))
                .toList();
    }

    /** 空白租户/角色按全局。工具标识不能空。 */
    private ScopeKey scopeKey(String toolId, String tenantId, String roleId) {
        if (toolId == null || toolId.isBlank() || toolId.length() > ID_LIMIT
                || (tenantId != null && tenantId.length() > SCOPE_LIMIT)
                || (roleId != null && roleId.length() > SCOPE_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool permission is invalid");
        }
        String savedTenant = tenantId == null || tenantId.isBlank() ? GLOBAL : tenantId;
        String savedRole = roleId == null || roleId.isBlank() ? GLOBAL : roleId;
        return new ScopeKey(toolId, savedTenant, savedRole);
    }

    /** 没有这一行按找不到。 */
    private ToolPermission require(ScopeKey key) {
        ToolPermission existing = rules.findByKey(key.toolId(), key.tenantId(), key.roleId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tool permission not found");
        }
        return existing;
    }

    /** 用库里的决定与优先级，只改启用标记。 */
    private static ToolPermissionSummary summary(ToolPermission existing, boolean enabled) {
        return new ToolPermissionSummary(
                existing.getToolId(),
                existing.getTenantId() == null ? GLOBAL : existing.getTenantId(),
                existing.getRoleId() == null ? GLOBAL : existing.getRoleId(),
                existing.getDecision(),
                existing.getPriority() == null ? DEFAULT_PRIORITY : existing.getPriority(),
                enabled);
    }

    /** 规则三元组。 */
    private record ScopeKey(String toolId, String tenantId, String roleId) {
    }
}
