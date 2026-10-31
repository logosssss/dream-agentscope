package com.zhu.bootstrap;

import com.zhu.semantic.application.StoredPermissionService;
import com.zhu.semantic.contract.PermissionDecision;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 表里的权限规则和配置使用同一套比较。带条件的行先不生效。
 */
class StoredPermissionServiceTest {

    /** 更小的 priority 压过更具体的规则。 */
    @Test
    void priorityThenSpecificity() {
        ToolPermission star = row("shell", "*", "*", "ASK", null, 10);
        ToolPermission exact = row("shell", "tenant-a", "*", "DENY", null, 20);
        StoredPermissionService service = service(star, exact);

        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.ASK);
        assertThat(service.decide("other", "tenant-a", null)).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 带条件的拒绝先跳过，不能当成无条件拒绝。 */
    @Test
    void conditionsAreSkipped() {
        ToolPermission conditional = row("shell", "*", "*", "DENY", "{\"after\":\"18:00\"}", 1);
        StoredPermissionService service = service(conditional);

        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 命中的决定写错时失败。没命中的坏决定不影响别的租户。 */
    @Test
    void unknownDecisionFailsOnlyWhenMatched() {
        ToolPermission broken = row("shell", "tenant-a", "*", "NOPE", null, 1);
        ToolPermission other = row("shell", "*", "*", "ASK", null, 10);
        StoredPermissionService service = service(broken, other);

        assertThat(service.decide("shell", "tenant-b", null)).isEqualTo(PermissionDecision.ASK);
        assertThatThrownBy(() -> service.decide("shell", "tenant-a", null))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 测试用的规则来源，不连接数据库。 */
    private static StoredPermissionService service(ToolPermission... rows) {
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.selectEnabled(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> List.of(rows).stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getToolId()))
                        .toList());
        return new StoredPermissionService(mapper);
    }

    /** 造一行规则。enabled 默认打开。 */
    private static ToolPermission row(
            String toolId,
            String tenantId,
            String roleId,
            String decision,
            String conditions,
            int priority) {
        ToolPermission row = new ToolPermission();
        row.setToolId(toolId);
        row.setTenantId(tenantId);
        row.setRoleId(roleId);
        row.setDecision(decision);
        row.setConditions(conditions);
        row.setPriority(priority);
        row.setEnabled(true);
        return row;
    }
}
