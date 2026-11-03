package com.zhu.access.application;

import com.zhu.audit.api.TenantGate;
import com.zhu.audit.api.TenantUserGate;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 接入层在对话以外的接口上检查登录会话里的租户和用户。
 * 没有会话按未登录拒绝。请求头不能再指定身份。
 */
@Component
public class AccessAccountGate {

    private final TenantGate tenants;

    private final TenantUserGate tenantUsers;

    /** MySQL 关闭时两道门都是空实现。 */
    public AccessAccountGate(TenantGate tenants, TenantUserGate tenantUsers) {
        this.tenants = tenants;
        this.tenantUsers = tenantUsers;
    }

    /**
     * 没有用户时拒绝。租户空白按空串交给门，打开 MySQL 后空租户过不了。
     * 停用走管理接口，这里不改状态。
     */
    public void requireActive() {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login is required");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        tenants.requireActive(tenantId);
        tenantUsers.requireActive(tenantId, userId);
    }
}
