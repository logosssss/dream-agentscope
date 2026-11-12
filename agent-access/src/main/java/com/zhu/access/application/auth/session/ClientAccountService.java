package com.zhu.access.application.auth.session;

import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.api.dto.auth.LoginResponse;
import com.zhu.access.config.ClientAuthProperties;
import com.zhu.audit.api.tenant.ClientLoginStore;
import com.zhu.audit.api.tenant.RegisteredClient;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.security.Passwords;
import com.zhu.common.util.Ids;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 客户端注册和登录。成功后签发和原来一样的 Redis 会话。
 * 不接收租户和用户标识，也不做忘记密码。
 */
@Service
public class ClientAccountService {

    private final ClientLoginStore store;

    private final UserLoginService sessions;

    private final ClientAuthProperties auth;

    /** 没有额度门时跳过注册/登录分钟限流（单测构造）。 */
    private final QuotaGuard quotas;

    /** 存储没开时 store 为空，注册和登录按不可用处理。 */
    @Autowired
    public ClientAccountService(
            ObjectProvider<ClientLoginStore> stores,
            UserLoginService sessions,
            ClientAuthProperties auth,
            ObjectProvider<QuotaGuard> quotas) {
        this.store = stores == null ? null : stores.getIfAvailable();
        this.sessions = sessions;
        this.auth = auth;
        this.quotas = quotas == null ? null : quotas.getIfAvailable();
    }

    /** 测试直接传入存储，不经过 Spring。不限流。 */
    public ClientAccountService(ClientLoginStore store, UserLoginService sessions, ClientAuthProperties auth) {
        this(store, sessions, auth, null);
    }

    /** 测试注入额度门，验证注册/登录限流。 */
    public ClientAccountService(
            ClientLoginStore store, UserLoginService sessions, ClientAuthProperties auth, QuotaGuard quotas) {
        this.store = store;
        this.sessions = sessions;
        this.auth = auth;
        this.quotas = quotas;
    }

    /**
     * 校验账号和密码后建号，并立刻登录。
     * 账号已存在时不签发会话。先过分钟限流再写库。
     */
    public LoginResponse register(String accountName, String password) {
        String name;
        String secret;
        try {
            name = Passwords.name(accountName);
            secret = Passwords.password(password);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        rate("register", name);
        String userId = Ids.newId();
        String salt = Passwords.salt();
        RegisteredClient client = requireStore().registerPassword(
                auth.getTenantId(), name, salt, Passwords.hash(secret, salt), userId, name);
        return sessions.issue(client.tenantId(), client.userId(), shown(client.displayName()));
    }

    /**
     * 账号、密码或停用都回答同一句。短密码也走这句，不说明是格式问题。
     * 先过分钟限流，避免撞库刷接口。
     */
    public LoginResponse login(String accountName, String password) {
        String name = accountName == null ? "" : accountName.trim();
        String secret = password == null ? "" : password;
        rate("login", name.isEmpty() ? "_" : name);
        RegisteredClient client = requireStore().matchPassword(auth.getTenantId(), name, secret);
        if (client == null || !client.active()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号或密码不正确");
        }
        return sessions.issue(client.tenantId(), client.userId(), shown(client.displayName()));
    }

    /** 额度门缺失时跳过。 */
    private void rate(String action, String accountKey) {
        if (quotas != null) {
            quotas.checkAuthRate(action, accountKey);
        }
    }

    /**
     * 第一次确认建号，显示名用「微信用户」加标识末四位。
     * 已停用时不签发会话，调用方把票作废。
     */
    public LoginResponse wechat(String openId) {
        String userId = Ids.newId();
        String displayName = "微信用户" + userId.substring(userId.length() - 4);
        RegisteredClient client = requireStore().wechat(auth.getTenantId(), openId, userId, displayName);
        if (client == null || !client.active()) {
            throw new BusinessException(ErrorCode.CONFLICT, "登录已停用");
        }
        String shown = client.displayName() == null || client.displayName().isBlank()
                ? displayName
                : client.displayName();
        return sessions.issue(client.tenantId(), client.userId(), shown);
    }

    /** 没有凭证存储时不改走文件。 */
    private ClientLoginStore requireStore() {
        if (store == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "登录暂不可用");
        }
        return store;
    }

    /** 显示名空着时页面会改成「已登录」，这里仍给一个空串。 */
    private static String shown(String displayName) {
        return displayName == null ? "" : displayName;
    }
}
