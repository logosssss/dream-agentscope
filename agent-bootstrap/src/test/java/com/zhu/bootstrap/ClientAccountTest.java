package com.zhu.bootstrap;

import com.zhu.access.application.auth.ClientAccountService;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.auth.UserLoginService;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.config.ClientAuthProperties;
import com.zhu.audit.api.ClientLoginStore;
import com.zhu.audit.api.RegisteredClient;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 客户端注册和登录。存储用假实现，不连 MySQL。
 */
class ClientAccountTest {

    /** 注册成功直接带上账号名。密码不对和停用都是同一句。 */
    @Test
    void registerLogsInAndBadPasswordStaysVague() {
        FakeAccessCache cache = new FakeAccessCache();
        ClientLoginStore store = new ClientLoginStore() {
            @Override
            public RegisteredClient registerPassword(
                    String tenantId, String loginName, String salt, String passwordHash, String userId, String displayName) {
                return new RegisteredClient(tenantId, userId, displayName, true);
            }

            @Override
            public RegisteredClient matchPassword(String tenantId, String loginName, String password) {
                return null;
            }

            @Override
            public RegisteredClient wechat(String tenantId, String openId, String userId, String displayName) {
                return new RegisteredClient(tenantId, "old-user", "", false);
            }
        };
        ClientAccountService accounts = service(store, cache);

        var session = accounts.register("  cat  ", "secret1");
        assertThat(session.displayName()).isEqualTo("cat");
        assertThat(session.tenantId()).isEqualTo("demo");
        assertThat(session.userId()).isNotBlank();
        assertThat(cache.get("login-session:" + session.accessToken())).contains("demo\n");

        assertThatThrownBy(() -> accounts.login("cat", "nope"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码不正确");
        assertThatThrownBy(() -> accounts.register("ab", "secret1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> accounts.wechat("openid-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录已停用");
    }

    /** 没有凭证表时不改走内存。 */
    @Test
    void missingStoreIsUnavailable() {
        ClientAccountService accounts = service(null, new FakeAccessCache());
        assertThatThrownBy(() -> accounts.login("cat", "secret1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAVAILABLE);
    }

    /** 同一账号登录超过每分钟上限时拒收，不进入验密。 */
    @Test
    void loginIsRateLimited() {
        FakeAccessCache cache = new FakeAccessCache();
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setPerMinute(1);
        QuotaGuard quotas = new QuotaGuard(cache, limits, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        ClientLoginStore store = new ClientLoginStore() {
            @Override
            public RegisteredClient registerPassword(
                    String tenantId, String loginName, String salt, String passwordHash, String userId, String displayName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RegisteredClient matchPassword(String tenantId, String loginName, String password) {
                return new RegisteredClient(tenantId, "u1", loginName, true);
            }

            @Override
            public RegisteredClient wechat(String tenantId, String openId, String userId, String displayName) {
                throw new UnsupportedOperationException();
            }
        };
        UserLoginService sessions = new UserLoginService(tenantId -> {
        }, (tenantId, userId) -> {
        }, cache, limits);
        ClientAccountService accounts = new ClientAccountService(store, sessions, new ClientAuthProperties(), quotas);

        accounts.login("cat", "secret1");
        assertThatThrownBy(() -> accounts.login("cat", "secret1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED);
    }

    /** 门在测试里放行。租户用默认 demo。 */
    private static ClientAccountService service(ClientLoginStore store, FakeAccessCache cache) {
        UserLoginService sessions = new UserLoginService(tenantId -> {
        }, (tenantId, userId) -> {
        }, cache, new AccessLimitProperties());
        return new ClientAccountService(store, sessions, new ClientAuthProperties());
    }
}
