package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlTenantUserCatalog;
import com.zhu.audit.infrastructure.persistence.TenantMapper;
import com.zhu.audit.infrastructure.persistence.TenantUserMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantRecord;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户用户只挂在已登记的租户上。列表含停用。不连接 MySQL，也不写角色。
 */
class TenantUserCatalogTest {

    /** 没有租户时不插入。有租户时写入可用用户，同一用户再挂一次按冲突处理。 */
    @Test
    void registerRequiresTheTenant() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        assertThatThrownBy(() -> catalog.register("t", "u", "示例"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(users, never()).insert(any(TenantUser.class));

        when(tenants.findByTenantId("t")).thenReturn(new TenantRecord());
        assertThat(catalog.register("t", "u", " ")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.displayName()).isNull();
            assertThat(saved.status()).isEqualTo("ACTIVE");
        });
        verify(users).insert(any(TenantUser.class));

        when(users.insert(any(TenantUser.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("t", "u", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 只有 ACTIVE 能过。没有或不在用时拒绝。 */
    @Test
    void onlyActiveUserCanCall() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        TenantUser active = new TenantUser();
        active.setStatus("ACTIVE");
        when(users.findByTenantAndUser("t", "u")).thenReturn(active);
        assertThatCode(() -> catalog.requireActive("t", "u")).doesNotThrowAnyException();

        TenantUser disabled = new TenantUser();
        disabled.setStatus("DISABLED");
        when(users.findByTenantAndUser("t", "u")).thenReturn(disabled);
        assertThatThrownBy(() -> catalog.requireActive("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(users.findByTenantAndUser("t", "missing")).thenReturn(null);
        assertThatThrownBy(() -> catalog.requireActive("t", "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 空白用户不查。没有行返回空，停用也返回显示名。 */
    @Test
    void findReturnsRowOrNull() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        assertThat(catalog.find("t", " ")).isNull();
        verify(users, never()).findByTenantAndUser("t", " ");

        when(users.findByTenantAndUser("t", "missing")).thenReturn(null);
        assertThat(catalog.find("t", "missing")).isNull();

        TenantUser disabled = new TenantUser();
        disabled.setTenantId("t");
        disabled.setUserId("u");
        disabled.setDisplayName("示例");
        disabled.setStatus("DISABLED");
        when(users.findByTenantAndUser("t", "u")).thenReturn(disabled);
        assertThat(catalog.find("t", "u")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.displayName()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("DISABLED");
        });
    }

    /** 只停还在用的用户。没有或已经停用按找不到或冲突。 */
    @Test
    void disableUpdatesActiveOnly() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        assertThatThrownBy(() -> catalog.disable("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(users, never()).disableActive("t", "u");

        TenantUser active = new TenantUser();
        active.setTenantId("t");
        active.setUserId("u");
        active.setDisplayName("示例");
        active.setStatus("ACTIVE");
        when(users.findByTenantAndUser("t", "u")).thenReturn(active);
        when(users.disableActive("t", "u")).thenReturn(1);
        assertThat(catalog.disable("t", "u")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.displayName()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("DISABLED");
        });

        TenantUser disabled = new TenantUser();
        disabled.setStatus("DISABLED");
        when(users.findByTenantAndUser("t", "u")).thenReturn(disabled);
        assertThatThrownBy(() -> catalog.disable("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(users).disableActive("t", "u");
    }

    /** 只恢复已经停用的用户。没有或已经可用按找不到或冲突。 */
    @Test
    void restoreUpdatesDisabledOnly() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        assertThatThrownBy(() -> catalog.restore("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(users, never()).restoreDisabled("t", "u");

        TenantUser disabled = new TenantUser();
        disabled.setTenantId("t");
        disabled.setUserId("u");
        disabled.setDisplayName("示例");
        disabled.setStatus("DISABLED");
        when(users.findByTenantAndUser("t", "u")).thenReturn(disabled);
        when(users.restoreDisabled("t", "u")).thenReturn(1);
        assertThat(catalog.restore("t", "u")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEqualTo("t");
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.displayName()).isEqualTo("示例");
            assertThat(saved.status()).isEqualTo("ACTIVE");
        });

        TenantUser active = new TenantUser();
        active.setStatus("ACTIVE");
        when(users.findByTenantAndUser("t", "u")).thenReturn(active);
        assertThatThrownBy(() -> catalog.restore("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(users).restoreDisabled("t", "u");
    }

    /** 没有租户时不查用户。有租户时列出含停用的行。 */
    @Test
    void recentRequiresTheTenant() {
        TenantMapper tenants = mock(TenantMapper.class);
        TenantUserMapper users = mock(TenantUserMapper.class);
        MysqlTenantUserCatalog catalog = new MysqlTenantUserCatalog(tenants, users);

        assertThatThrownBy(() -> catalog.recent(" ", 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(users, never()).recent(any(), anyInt());

        assertThatThrownBy(() -> catalog.recent("t", 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(users, never()).recent(any(), anyInt());

        when(tenants.findByTenantId("t")).thenReturn(new TenantRecord());
        TenantUser disabled = new TenantUser();
        disabled.setTenantId("t");
        disabled.setUserId("u");
        disabled.setDisplayName("示例");
        disabled.setStatus("DISABLED");
        when(users.recent("t", 50)).thenReturn(List.of(disabled));
        assertThat(catalog.recent("t", 0)).singleElement().satisfies(saved -> {
            assertThat(saved.userId()).isEqualTo("u");
            assertThat(saved.status()).isEqualTo("DISABLED");
        });
    }
}
