package com.zhu.audit.application.tenant;

import com.zhu.audit.api.tenant.ClientLoginStore;
import com.zhu.audit.api.tenant.RegisteredClient;
import com.zhu.audit.infrastructure.persistence.tenant.LoginCredentialMapper;
import com.zhu.audit.infrastructure.persistence.tenant.TenantMapper;
import com.zhu.audit.infrastructure.persistence.tenant.TenantUserMapper;
import com.zhu.audit.infrastructure.persistence.entity.tenant.LoginCredential;
import com.zhu.audit.infrastructure.persistence.entity.tenant.TenantRecord;
import com.zhu.audit.infrastructure.persistence.entity.tenant.TenantUser;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.security.Passwords;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 上的客户端登录凭证。只在存储开关打开时创建。
 * 管理员不进这张表。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlClientLoginStore implements ClientLoginStore {

    private static final String ACTIVE = "ACTIVE";

    private final TenantMapper tenants;

    private final TenantUserMapper users;

    private final LoginCredentialMapper credentials;

    /** 租户、用户和凭证用同一个事务。 */
    public MysqlClientLoginStore(TenantMapper tenants, TenantUserMapper users, LoginCredentialMapper credentials) {
        this.tenants = tenants;
        this.users = users;
        this.credentials = credentials;
    }

    /**
     * 先确认租户仍启用，再写用户和密码行。
     * 唯一键冲突整段回滚，避免留下没有凭证的用户。
     */
    @Override
    @Transactional
    public RegisteredClient registerPassword(
            String tenantId,
            String loginName,
            String salt,
            String passwordHash,
            String userId,
            String displayName) {
        requireTenant(tenantId, "注册暂不可用");
        if (credentials.findByTenantAndLoginName(tenantId, loginName) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }
        insertUser(tenantId, userId, displayName);
        LoginCredential row = new LoginCredential();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setLoginName(loginName);
        row.setPasswordSalt(salt);
        row.setPasswordHash(passwordHash);
        try {
            credentials.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }
        return new RegisteredClient(tenantId, userId, displayName, true);
    }

    /**
     * 租户不可用单独抛出。密码和停用都返回 null。
     */
    @Override
    public RegisteredClient matchPassword(String tenantId, String loginName, String password) {
        requireTenant(tenantId, "登录暂不可用");
        if (loginName == null || loginName.isBlank()) {
            return null;
        }
        LoginCredential row = credentials.findByTenantAndLoginName(tenantId, loginName);
        if (row == null || !Passwords.matches(password, row.getPasswordSalt(), row.getPasswordHash())) {
            return null;
        }
        return activeClient(row);
    }

    /**
     * 已有 openid 不再插入。并发插入撞上唯一键时回滚，调用方可以再确认一次。
     */
    @Override
    @Transactional
    public RegisteredClient wechat(String tenantId, String openId, String userId, String displayName) {
        if (openId == null || openId.isBlank() || openId.length() > 64 || openId.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "微信身份无效");
        }
        LoginCredential existing = credentials.findByOpenId(openId);
        if (existing != null) {
            return activeClient(existing);
        }
        requireTenant(tenantId, "登录暂不可用");
        insertUser(tenantId, userId, displayName);
        LoginCredential row = new LoginCredential();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setOpenId(openId);
        try {
            credentials.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "微信身份无效");
        }
        return new RegisteredClient(tenantId, userId, displayName, true);
    }

    /** 没登记或不是 ACTIVE 时，注册和登录都不要往下写。 */
    private void requireTenant(String tenantId, String message) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, message);
        }
        TenantRecord tenant = tenants.findByTenantId(tenantId);
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, message);
        }
    }

    /** 用户行默认启用。显示名可以空。 */
    private void insertUser(String tenantId, String userId, String displayName) {
        TenantUser user = new TenantUser();
        user.setTenantId(tenantId);
        user.setUserId(userId);
        user.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName);
        user.setStatus(ACTIVE);
        users.insert(user);
    }

    /** 用户行不见或已停用时 active 为 false。不把 openid 带回。 */
    private RegisteredClient activeClient(LoginCredential row) {
        TenantUser user = users.findByTenantAndUser(row.getTenantId(), row.getUserId());
        boolean active = user != null && ACTIVE.equals(user.getStatus());
        String name = user == null || user.getDisplayName() == null ? "" : user.getDisplayName();
        return new RegisteredClient(row.getTenantId(), row.getUserId(), name, active);
    }
}
