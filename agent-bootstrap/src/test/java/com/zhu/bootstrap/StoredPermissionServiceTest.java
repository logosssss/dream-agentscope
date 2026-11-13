package com.zhu.bootstrap;

import com.zhu.semantic.application.permission.StoredPermissionService;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 表里的权限规则和配置使用同一套比较。时间窗与参数长度按条件解释，不能识别的仍跳过。
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

    /** 还不认识的键整条跳过，不能当成无条件拒绝。 */
    @Test
    void unknownConditionsAreSkipped() {
        ToolPermission conditional = row("shell", "*", "*", "DENY", "{\"paramMax\":10}", 1);
        StoredPermissionService service = service(conditional);

        assertThat(service.decide("shell", "tenant-a", null)).isEqualTo(PermissionDecision.ALLOW);
    }

    /** after 到达后这条 DENY 才生效。 */
    @Test
    void afterWindowAppliesAtUtcTime() {
        ToolPermission evening = row("shell", "*", "*", "DENY", "{\"after\":\"18:00\"}", 1);
        assertThat(serviceAt("2026-11-02T17:59:00Z", evening).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.ALLOW);
        assertThat(serviceAt("2026-11-02T18:00:00Z", evening).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.DENY);
    }

    /** 空白对象仍是无条件命中。 */
    @Test
    void emptyObjectMatches() {
        ToolPermission empty = row("shell", "*", "*", "DENY", "{}", 1);
        assertThat(service(empty).decide("shell", "t", null)).isEqualTo(PermissionDecision.DENY);
    }

    /** 坏 JSON 整条跳过，不能当成拒绝。 */
    @Test
    void invalidJsonIsSkipped() {
        ToolPermission broken = row("shell", "*", "*", "DENY", "not-json", 1);
        assertThat(service(broken).decide("shell", "t", null)).isEqualTo(PermissionDecision.ALLOW);
    }

    /** 起点晚于终点时按跨日窗；before 不含终点。 */
    @Test
    void overnightWindowWrapsUtcDay() {
        ToolPermission night = row("shell", "*", "*", "DENY", "{\"after\":\"22:00\",\"before\":\"06:00\"}", 1);
        assertThat(serviceAt("2026-11-02T12:00:00Z", night).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.ALLOW);
        assertThat(serviceAt("2026-11-02T23:00:00Z", night).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.DENY);
        assertThat(serviceAt("2026-11-02T03:00:00Z", night).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.DENY);
        assertThat(serviceAt("2026-11-02T06:00:00Z", night).decide("shell", "t", null))
                .isEqualTo(PermissionDecision.ALLOW);
    }

    /** maxLen 超界时这条 DENY 不生效，回落到默认允许。 */
    @Test
    void maxLenMissSkipsRule() {
        ToolPermission limited = row("shell", "*", "*", "DENY", "{\"maxLen\":{\"content\":3}}", 1);
        StoredPermissionService service = service(limited);

        assertThat(service.decide("shell", "t", null, Map.of("content", "abcd")))
                .isEqualTo(PermissionDecision.ALLOW);
        assertThat(service.decide("shell", "t", null, Map.of("content", "ab")))
                .isEqualTo(PermissionDecision.DENY);
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
        return serviceAt(null, rows);
    }

    /** instant 为空时用系统 UTC。 */
    private static StoredPermissionService serviceAt(String instant, ToolPermission... rows) {
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.selectEnabled(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> List.of(rows).stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getToolId()))
                        .toList());
        if (instant == null) {
            return new StoredPermissionService(mapper);
        }
        return new StoredPermissionService(mapper, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
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
