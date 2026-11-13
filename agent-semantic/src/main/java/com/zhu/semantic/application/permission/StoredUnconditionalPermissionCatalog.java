package com.zhu.semantic.application.permission;

import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.UnconditionalPermissionCatalog;
import com.zhu.semantic.contract.permission.UnconditionalToolRule;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 应用层：从 tool_permission 抽出无条件全局规则给引擎。
 * MySQL 打开时与 {@link StoredPermissionService} 同源；有 conditions / 非通配范围的行不进列表。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class StoredUnconditionalPermissionCatalog implements UnconditionalPermissionCatalog {

    private final ToolPermissionMapper rules;

    /** Mapper 只在 MySQL 打开时存在。 */
    public StoredUnconditionalPermissionCatalog(ToolPermissionMapper rules) {
        this.rules = rules;
    }

    /**
     * 查询已在 SQL 侧滤掉非 {@code *} 范围与非空 conditions。
     * 决定写错的行跳过，避免装配失败。
     */
    @Override
    public List<UnconditionalToolRule> list() {
        List<ToolPermission> rows = rules.selectEnabledGlobalUnconditional();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UnconditionalToolRule> out = new ArrayList<>();
        for (ToolPermission row : rows) {
            if (row == null || blank(row.getToolId())) {
                continue;
            }
            PermissionDecision decision = parse(row.getDecision());
            if (decision == null) {
                continue;
            }
            out.add(new UnconditionalToolRule(row.getToolId().trim(), decision));
        }
        return List.copyOf(out);
    }

    private static PermissionDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PermissionDecision.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}