package com.zhu.access.application;

import com.zhu.access.api.dto.LoginResponse;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.audit.api.TenantGate;
import com.zhu.audit.api.TenantUserGate;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 用户登录。核对租户用户后签发 Redis 会话令牌。
 * 这不是调用方令牌，也不做密码校验。Redis 失败按 503。
 */
@Service
public class UserLoginService {

    private static final int TENANT_LIMIT = 64;

    private static final int USER_LIMIT = 128;

    private static final long DEFAULT_TTL_SECONDS = 86_400L;

    private final TenantGate tenants;

    private final TenantUserGate tenantUsers;

    private final AccessCache cache;

    private final AccessLimitProperties limits;

    /** 正式进程里 AccessCache 是 Redis。测试用假实现。 */
    public UserLoginService(
            TenantGate tenants,
            TenantUserGate tenantUsers,
            AccessCache cache,
            AccessLimitProperties limits) {
        this.tenants = tenants;
        this.tenantUsers = tenantUsers;
        this.cache = cache;
        this.limits = limits;
    }

    /**
     * 空白租户按空串。用户必须非空。
     * MySQL 打开时门会拒绝未登记或停用的账号。关闭时门放行，只留给测试。
     */
    public LoginResponse login(String tenantId, String userId) {
        String tenant = tenantId == null || tenantId.isBlank() ? "" : tenantId.trim();
        String user = userId == null ? "" : userId.trim();
        if (user.isBlank() || user.length() > USER_LIMIT || tenant.length() > TENANT_LIMIT
                || tenant.indexOf('\n') >= 0 || user.indexOf('\n') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "login is invalid");
        }
        tenants.requireActive(tenant);
        tenantUsers.requireActive(tenant, user);
        long ttlSeconds = ttlSeconds();
        String token = Ids.newId();
        cache.put(LoginSessionCodec.key(token), LoginSessionCodec.encode(tenant, user), Duration.ofSeconds(ttlSeconds));
        return new LoginResponse(token, ttlSeconds, tenant, user);
    }

    /**
     * 删掉会话键。令牌空白时什么都不做。
     * 键不存在也算成功，避免重复退出吵起来。
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        cache.delete(LoginSessionCodec.key(accessToken.trim()));
    }

    /** 配置小于 1 时按一天。 */
    private long ttlSeconds() {
        int configured = limits.getLoginSessionSeconds();
        return configured < 1 ? DEFAULT_TTL_SECONDS : configured;
    }
}
