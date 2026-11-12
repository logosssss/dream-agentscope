package com.zhu.semantic.config;

import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.UnconditionalPermissionCatalog;
import com.zhu.semantic.contract.permission.UnconditionalToolRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 配置层：从 {@code agentscope.permission.rules} 抽出无条件规则给引擎。
 * MySQL 关闭时与 {@link ConfiguredPermissionService} 同源；配置项没有 conditions 列。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class ConfiguredUnconditionalPermissionCatalog implements UnconditionalPermissionCatalog {

    private static final String ANY = "*";

    private final PermissionProperties properties;

    /** 只挂配置引用，每次 list 现读，便于 Nacos 热更后排空重建拿到新规则。 */
    public ConfiguredUnconditionalPermissionCatalog(PermissionProperties properties) {
        this.properties = properties;
    }

    /**
     * 只要启用、工具名非空、租户与角色都是 {@code *}、决定可解析。
     * 写明租户/角色的行留给产品闸，不进引擎。
     */
    @Override
    public List<UnconditionalToolRule> list() {
        List<PermissionProperties.Rule> source = properties.getRules();
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<UnconditionalToolRule> out = new ArrayList<>();
        for (PermissionProperties.Rule rule : source) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            if (rule.getToolId() == null || rule.getToolId().isBlank()) {
                continue;
            }
            if (!ANY.equals(rule.getTenantId()) || !ANY.equals(rule.getRoleId())) {
                continue;
            }
            PermissionDecision decision = parse(rule.getDecision());
            if (decision == null) {
                continue;
            }
            out.add(new UnconditionalToolRule(rule.getToolId().trim(), decision));
        }
        return List.copyOf(out);
    }

    /** 决定写错时跳过该行，不让一次坏配置拖垮整个 context 装配。 */
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
}
