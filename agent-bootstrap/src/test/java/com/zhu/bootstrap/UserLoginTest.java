package com.zhu.bootstrap;

import com.zhu.access.api.dto.LoginResponse;
import com.zhu.access.application.LoginSessionCodec;
import com.zhu.access.application.UserLoginService;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.audit.api.TenantGate;
import com.zhu.audit.api.TenantUserGate;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 登录签发 Redis 会话。不连真实 Redis，也不连 MySQL。
 */
class UserLoginTest {

    /** 核对通过后写入缓存，退出后读不到。 */
    @Test
    void loginStoresSessionAndLogoutClears() {
        FakeAccessCache cache = new FakeAccessCache();
        AccessLimitProperties limits = new AccessLimitProperties();
        limits.setLoginSessionSeconds(3600);
        UserLoginService logins = new UserLoginService(openTenant(), openUser(), cache, limits);

        LoginResponse session = logins.login("tenant-a", "user-a");

        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.expiresIn()).isEqualTo(3600);
        assertThat(session.tenantId()).isEqualTo("tenant-a");
        assertThat(session.userId()).isEqualTo("user-a");
        assertThat(cache.get(LoginSessionCodec.key(session.accessToken())))
                .isEqualTo(LoginSessionCodec.encode("tenant-a", "user-a"));

        logins.logout(session.accessToken());
        assertThat(cache.get(LoginSessionCodec.key(session.accessToken()))).isNull();
    }

    /** 空白用户或含换行的标识拒绝。停用账号按门抛错上抛。 */
    @Test
    void rejectsInvalidOrInactive() {
        FakeAccessCache cache = new FakeAccessCache();
        UserLoginService logins = new UserLoginService(openTenant(), openUser(), cache, new AccessLimitProperties());
        assertThatThrownBy(() -> logins.login("t", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> logins.login("t\nx", "u"))
                .isInstanceOf(BusinessException.class);

        TenantUserGate closed = (tenantId, userId) -> {
            throw new BusinessException(ErrorCode.CONFLICT, "tenant user is not active");
        };
        UserLoginService denied = new UserLoginService(openTenant(), closed, cache, new AccessLimitProperties());
        assertThatThrownBy(() -> denied.login("t", "u"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 编解码只接受恰好一段分隔。 */
    @Test
    void codecRoundTrip() {
        assertThat(LoginSessionCodec.decode(LoginSessionCodec.encode("t", "u")))
                .isEqualTo(new LoginSessionCodec.Identity("t", "u"));
        assertThat(LoginSessionCodec.decode("only-tenant")).isNull();
        assertThat(LoginSessionCodec.decode("a\nb\nc")).isNull();
    }

    private static TenantGate openTenant() {
        return tenantId -> {
        };
    }

    private static TenantUserGate openUser() {
        return (tenantId, userId) -> {
        };
    }
}
