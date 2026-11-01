package com.zhu.audit.application;

import com.zhu.audit.api.TenantUserCatalog;
import com.zhu.audit.api.TenantUserGate;
import com.zhu.audit.api.TenantUserSummary;
import com.zhu.audit.infrastructure.persistence.TenantMapper;
import com.zhu.audit.infrastructure.persistence.TenantUserMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * MySQL 上的租户用户目录。只在存储开关打开时创建。不写角色。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlTenantUserCatalog implements TenantUserCatalog, TenantUserGate {

    private static final String ACTIVE = "ACTIVE";

    private static final String DISABLED = "DISABLED";

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
     * 不要求租户仍是 ACTIVE。对话检查走 {@link #requireActive(String, String)}。
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

    /**
     * 空标识、查不到，或状态不是 ACTIVE，都不能继续这次请求。
     * 不把停用分成另一种错误，调用方只需要知道现在不能用。
     */
    @Override
    public void requireActive(String tenantId, String userId) {
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenantId and userId are required");
        }
        TenantUser row = users.findByTenantAndUser(tenantId, userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
        }
        if (!ACTIVE.equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is not active");
        }
    }

    /**
     * 先读再按 ACTIVE 更新，避免覆盖已经停用的行。
     * 不改显示名，也不删会话。
     */
    @Override
    public TenantUserSummary disable(String tenantId, String userId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > TENANT_LIMIT
                || userId == null || userId.isBlank() || userId.length() > USER_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant user is invalid");
        }
        TenantUser existing = users.findByTenantAndUser(tenantId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
        }
        if (!ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is not active");
        }
        int updated = users.disableActive(tenantId, userId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is not active");
        }
        return new TenantUserSummary(existing.getTenantId(), existing.getUserId(), existing.getDisplayName(), DISABLED);
    }

    /**
     * 先读再按 DISABLED 更新，避免覆盖已经可用的行。
     * 不改显示名，也不要求租户当前可用。
     */
    @Override
    public TenantUserSummary restore(String tenantId, String userId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > TENANT_LIMIT
                || userId == null || userId.isBlank() || userId.length() > USER_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenant user is invalid");
        }
        TenantUser existing = users.findByTenantAndUser(tenantId, userId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
        }
        if (!DISABLED.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is already active");
        }
        int updated = users.restoreDisabled(tenantId, userId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is already active");
        }
        return new TenantUserSummary(existing.getTenantId(), existing.getUserId(), existing.getDisplayName(), ACTIVE);
    }

    /**
     * 租户还没登记时拒绝。空结果返回空列表。
     * 不按用户状态再滤一层，停用也要能看见才能恢复。
     */
    @Override
    public List<TenantUserSummary> recent(String tenantId, int limit) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > TENANT_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tenantId is required");
        }
        if (tenants.findByTenantId(tenantId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "tenant not found");
        }
        List<TenantUser> rows = users.recent(tenantId, AuditQueryLimits.normalize(limit));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new TenantUserSummary(
                        row.getTenantId(),
                        row.getUserId(),
                        row.getDisplayName(),
                        row.getStatus()))
                .toList();
    }
}
