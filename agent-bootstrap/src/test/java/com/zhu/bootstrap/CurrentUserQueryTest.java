package com.zhu.bootstrap;

import com.zhu.access.api.dto.CurrentUser;
import com.zhu.access.application.auth.AccessAccountGate;
import com.zhu.access.application.auth.CurrentUserQuery;
import com.zhu.audit.api.TenantUserLookup;
import com.zhu.audit.api.TenantUserSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 当前身份先过账号门。登记行可选。不连接 MySQL。
 */
class CurrentUserQueryTest {

    /** 其它测试可能把用户留在线程上。这里先清掉。 */
    @BeforeEach
    void resetIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 线程身份用完就清，避免串到下一条。 */
    @AfterEach
    void clearIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 没有用户时拒绝。没有查找口时只回标识。有行时带显示名。 */
    @Test
    void currentReadsIdentityAndOptionalRow() {
        AccessAccountGate open = new AccessAccountGate(tenantId -> {
        }, (tenantId, userId) -> {
        });
        CurrentUserQuery withoutLookup = new CurrentUserQuery(open, lookups(null));
        assertThatThrownBy(withoutLookup::current)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        UserContext.set("u");
        TenantContext.set("t");
        assertThat(withoutLookup.current()).isEqualTo(new CurrentUser("t", "u", null, null));

        TenantUserLookup lookup = mock(TenantUserLookup.class);
        when(lookup.find("t", "u")).thenReturn(new TenantUserSummary("t", "u", "示例", "ACTIVE"));
        CurrentUserQuery withLookup = new CurrentUserQuery(open, lookups(lookup));
        assertThat(withLookup.current()).isEqualTo(new CurrentUser("t", "u", "示例", "ACTIVE"));
    }

    /** 测试里用固定的查找结果，避免把 ObjectProvider 的其余方法都实现一遍。 */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantUserLookup> lookups(TenantUserLookup lookup) {
        ObjectProvider<TenantUserLookup> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(lookup);
        return providers;
    }
}
