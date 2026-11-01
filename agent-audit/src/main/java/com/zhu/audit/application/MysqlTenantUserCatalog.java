package com.zhu.audit.application;

import com.zhu.audit.api.TenantUserCatalog;
import com.zhu.audit.api.TenantUserSummary;
import com.zhu.audit.infrastructure.persistence.TenantMapper;
import com.zhu.audit.infrastructure.persistence.TenantUserMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的租户用户目录。只在存储开关打开时创建。不写角色，也不停用。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTenantUserCatalog implements TenantUserCatalog {

    private static final String ACTIVE = "ACTIVE";

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final int NAME_LIMIT = 128;

    private final TenantMapper tenants;

    private final TenantUserMapper users;

    /** 先确认租户，再插入用户。重复的租户加用户由唯一键拒绝。 */
    public MysqlTenantUserCatalog(TenantMapper tenants, TenantUserMapper users) {
        this.tenants = tenants;
        this.users = users;
    }

    /**
     * 租户还没登记时拒绝，避免把用户挂到空租户上。
     * 不要求租户仍是 ACTIVE，也不按这张表拦截对话。
     */
    @Override
    public TenantUserSummary register(String tenantId, String userId, String displayName) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > TENANT_LIMIT
                || userId == null || userId.isBlank() || userId.length() > USER_LIMIT
                || (displayName != null && displayName.length() > NAME_LIMIT)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant user is invalid");
        }
        if (tenants.findByTenantId(tenantId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant not found");
        }
        String name = displayName == null || displayName.isBlank() ? null : displayName;
        TenantUser row = new TenantUser();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setDisplayName(name);
        row.setStatus(ACTIVE);
        try {
            users.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user already exists");
        }
        return new TenantUserSummary(tenantId, userId, name, ACTIVE);
    }
}
