package com.zhu.bootstrap;

import com.zhu.semantic.config.ConfiguredPermissionService;
import com.zhu.semantic.config.PermissionProperties;
import com.zhu.semantic.contract.PermissionDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 权限规则按工具、租户和角色匹配。没有命中时允许。
 */
class PermissionRuleTest {

    /** 空规则和没写到的工具都放行。 */
    @Test
    void missesAllow() {
        ConfiguredPermissionService service = new ConfiguredPermissionService(properties());
        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 更小的 priority 压过更具体的规则。相同 priority 时写明租户的更优先。 */
    @Test
    void priorityThenSpecificity() {
        PermissionProperties.Rule star = rule("shell", "*", "*", "ASK", 10, true);
        PermissionProperties.Rule exact = rule("shell", "tenant-a", "*", "ALLOW", 20, true);
        PermissionProperties.Rule same = rule("shell", "tenant-a", "*", "DENY", 10, true);
        ConfiguredPermissionService service = new ConfiguredPermissionService(properties(star, exact, same));

        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.DENY);
        assertThat(service.decide("shell", "tenant-b", "ops")).isEqualTo(PermissionDecision.ASK);
    }

    /** 关闭的规则不参与。角色没带时不能命中写明了角色的规则。 */
    @Test
    void disabledAndRoleStayOut() {
        PermissionProperties.Rule disabled = rule("shell", "*", "*", "DENY", 1, false);
        PermissionProperties.Rule role = rule("shell", "*", "ops", "ASK", 1, true);
        ConfiguredPermissionService service = new ConfiguredPermissionService(properties(disabled, role));

        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.ALLOW);
        assertThat(service.decide("shell", "tenant-a", "ops")).isEqualTo(PermissionDecision.ASK);
    }

    /** 决定写错时第一次判定就失败，不能把拒绝配成一个不认识的词后静默放行。 */
    @Test
    void unknownDecisionFailsOnDecide() {
        PermissionProperties.Rule broken = rule("shell", "*", "*", "NOPE", 100, true);
        ConfiguredPermissionService service = new ConfiguredPermissionService(properties(broken));
        assertThatThrownBy(() -> service.decide("shell", "tenant-a", null))
                .isInstanceOf(IllegalStateException.class);
    }

    private static PermissionProperties properties(PermissionProperties.Rule... rules) {
        PermissionProperties properties = new PermissionProperties();
        properties.setRules(List.of(rules));
        return properties;
    }

    private static PermissionProperties.Rule rule(
            String toolId,
            String tenantId,
            String roleId,
            String decision,
            int priority,
            boolean enabled) {
        PermissionProperties.Rule rule = new PermissionProperties.Rule();
        rule.setToolId(toolId);
        rule.setTenantId(tenantId);
        rule.setRoleId(roleId);
        rule.setDecision(decision);
        rule.setPriority(priority);
        rule.setEnabled(enabled);
        return rule;
    }
}
