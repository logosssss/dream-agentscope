package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.semantic.api.ToolPermissionSummary;
import com.zhu.semantic.application.MysqlToolPermissionCatalog;
import com.zhu.semantic.infrastructure.persistence.ToolPermissionMapper;
import com.zhu.semantic.infrastructure.persistence.entity.ToolPermission;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具权限规则登记与列表。不连接 MySQL，也不执行工具。
 */
class MysqlToolPermissionCatalogTest {

    /** 空白租户角色按全局；空白优先级按 100；conditions 固定空。 */
    @Test
    void registerDefaultsScopeAndPriority() {
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        MysqlToolPermissionCatalog catalog = new MysqlToolPermissionCatalog(mapper);

        ToolPermissionSummary saved = catalog.register("echo_text", " ", null, "ASK", null);

        ArgumentCaptor<ToolPermission> row = ArgumentCaptor.forClass(ToolPermission.class);
        verify(mapper).insert(row.capture());
        assertThat(row.getValue().getToolId()).isEqualTo("echo_text");
        assertThat(row.getValue().getTenantId()).isEqualTo("*");
        assertThat(row.getValue().getRoleId()).isEqualTo("*");
        assertThat(row.getValue().getDecision()).isEqualTo("ASK");
        assertThat(row.getValue().getPriority()).isEqualTo(100);
        assertThat(row.getValue().getConditions()).isNull();
        assertThat(row.getValue().getEnabled()).isTrue();
        assertThat(saved).isEqualTo(new ToolPermissionSummary("echo_text", "*", "*", "ASK", 100, true));
    }

    /** 非法决定拒绝。 */
    @Test
    void invalidDecisionIsBadRequest() {
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        MysqlToolPermissionCatalog catalog = new MysqlToolPermissionCatalog(mapper);

        assertThatThrownBy(() -> catalog.register("echo_text", null, null, "MAYBE", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    /** 同一规则键冲突。 */
    @Test
    void duplicateRuleIsConflict() {
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.insert(any(ToolPermission.class))).thenThrow(new DuplicateKeyException("dup"));
        MysqlToolPermissionCatalog catalog = new MysqlToolPermissionCatalog(mapper);

        assertThatThrownBy(() -> catalog.register("echo_text", "*", "*", "ASK", 100))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 列表把空优先级补成 100。 */
    @Test
    void recentMapsNullPriority() {
        ToolPermission row = new ToolPermission();
        row.setToolId("ping");
        row.setTenantId("*");
        row.setRoleId("*");
        row.setDecision("ALLOW");
        row.setEnabled(true);
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.recent(50)).thenReturn(List.of(row));

        assertThat(new MysqlToolPermissionCatalog(mapper).recent(50))
                .containsExactly(new ToolPermissionSummary("ping", "*", "*", "ALLOW", 100, true));
    }

    /** 停用只打仍启用的行。 */
    @Test
    void disableEnabledRule() {
        ToolPermission existing = new ToolPermission();
        existing.setToolId("echo_text");
        existing.setTenantId("*");
        existing.setRoleId("*");
        existing.setDecision("ASK");
        existing.setPriority(100);
        existing.setEnabled(true);
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.findByKey("echo_text", "*", "*")).thenReturn(existing);
        when(mapper.disableEnabled("echo_text", "*", "*")).thenReturn(1);

        assertThat(new MysqlToolPermissionCatalog(mapper).disable("echo_text", null, null))
                .isEqualTo(new ToolPermissionSummary("echo_text", "*", "*", "ASK", 100, false));
    }

    /** 改优先级不改启用。 */
    @Test
    void updatePriorityKeepsEnabled() {
        ToolPermission existing = new ToolPermission();
        existing.setToolId("echo_text");
        existing.setTenantId("*");
        existing.setRoleId("*");
        existing.setDecision("ASK");
        existing.setPriority(100);
        existing.setEnabled(true);
        ToolPermissionMapper mapper = mock(ToolPermissionMapper.class);
        when(mapper.findByKey("echo_text", "*", "*")).thenReturn(existing);
        when(mapper.updatePriority("echo_text", "*", "*", 10)).thenReturn(1);

        assertThat(new MysqlToolPermissionCatalog(mapper).updatePriority("echo_text", null, null, 10))
                .isEqualTo(new ToolPermissionSummary("echo_text", "*", "*", "ASK", 10, true));
    }
}
