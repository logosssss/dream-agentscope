package com.zhu.access.application.auth;

import com.zhu.access.api.dto.CurrentUser;
import com.zhu.audit.api.TenantUserLookup;
import com.zhu.audit.api.TenantUserSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 接入层看当前身份。先过账号门，再可选读登记行。
 * 不读登录剩余 TTL，也不签发令牌。
 */
@Component
public class CurrentUserQuery {

    private final AccessAccountGate accounts;

    private final ObjectProvider<TenantUserLookup> lookups;

    /** MySQL 关闭时没有查找口，只回请求头或会话里的标识。 */
    public CurrentUserQuery(AccessAccountGate accounts, ObjectProvider<TenantUserLookup> lookups) {
        this.accounts = accounts;
        this.lookups = lookups;
    }

    /**
     * 没有用户时由账号门拒绝。登记行找不到时仍返回标识，显示名留空。
     */
    public CurrentUser current() {
        accounts.requireActive();
        String userId = UserContext.get();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        TenantUserLookup lookup = lookups.getIfAvailable();
        if (lookup == null) {
            return new CurrentUser(tenantId, userId, null, null);
        }
        TenantUserSummary row = lookup.find(tenantId, userId);
        if (row == null) {
            return new CurrentUser(tenantId, userId, null, null);
        }
        return new CurrentUser(row.tenantId(), row.userId(), row.displayName(), row.status());
    }
}
