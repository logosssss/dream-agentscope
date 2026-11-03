package com.zhu.bootstrap;

import com.zhu.access.application.ClientAccountService;
import com.zhu.access.application.UserLoginService;
import com.zhu.access.application.WxLoginService;
import com.zhu.access.application.WxRemote;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.config.ClientAuthProperties;
import com.zhu.access.config.WxMiniProperties;
import com.zhu.audit.api.ClientLoginStore;
import com.zhu.audit.api.RegisteredClient;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 微信出码和确认。不访问微信接口。
 */
class WxLoginTest {

    /** 没配密钥时不能出码。配齐后确认一次，网页能取走会话。 */
    @Test
    void confirmPutsSessionOnTheTicket() {
        WxMiniProperties settings = ready();
        FakeAccessCache cache = new FakeAccessCache();
        WxLoginService wx = service(settings, cache, activeStore());

        assertThatThrownBy(() -> new WxLoginService(new WxMiniProperties(), cache, accounts(cache, activeStore()), remote())
                .ticket())
                .isInstanceOf(BusinessException.class)
                .hasMessage("微信扫码登录未启用");

        WxLoginService.WxTicket ticket = wx.ticket();
        assertThat(ticket.qrcodeDataUrl()).startsWith("data:image/png;base64,");
        assertThat(wx.poll(ticket.ticketId()).status()).isEqualTo("PENDING");

        wx.confirm(ticket.ticketId(), "js-code");
        WxLoginService.WxPoll done = wx.poll(ticket.ticketId());
        assertThat(done.status()).isEqualTo("CONFIRMED");
        assertThat(done.login().displayName()).startsWith("微信用户");
        assertThat(done.login().accessToken()).isNotBlank();

        assertThatThrownBy(() -> wx.confirm(ticket.ticketId(), "js-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("二维码已使用");
    }

    /** 已停用的微信用户不留下可取走的会话。 */
    @Test
    void disabledWechatExpiresTheTicket() {
        FakeAccessCache cache = new FakeAccessCache();
        WxLoginService wx = service(ready(), cache, new ClientLoginStore() {
            @Override
            public RegisteredClient registerPassword(
                    String tenantId, String loginName, String salt, String passwordHash, String userId, String displayName) {
                return null;
            }

            @Override
            public RegisteredClient matchPassword(String tenantId, String loginName, String password) {
                return null;
            }

            @Override
            public RegisteredClient wechat(String tenantId, String openId, String userId, String displayName) {
                return new RegisteredClient(tenantId, userId, displayName, false);
            }
        });
        WxLoginService.WxTicket ticket = wx.ticket();
        assertThatThrownBy(() -> wx.confirm(ticket.ticketId(), "js-code"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录已停用");
        assertThat(wx.poll(ticket.ticketId()).status()).isEqualTo("EXPIRED");
    }

    /** 窗口内第 101 张票拒绝。不要求真的去画码。 */
    @Test
    void tooManyTicketsAreRejected() {
        WxMiniProperties settings = ready();
        FakeAccessCache cache = new FakeAccessCache();
        WxLoginService wx = service(settings, cache, activeStore());
        BusinessException error = null;
        for (int i = 0; i < 101; i++) {
            try {
                wx.ticket();
            } catch (BusinessException ex) {
                error = ex;
            }
        }
        assertThat(error).isNotNull();
        assertThat(error.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(error.getMessage()).isEqualTo("码太多");
    }

    private static WxLoginService service(WxMiniProperties settings, FakeAccessCache cache, ClientLoginStore store) {
        return new WxLoginService(settings, cache, accounts(cache, store), remote());
    }

    private static ClientAccountService accounts(FakeAccessCache cache, ClientLoginStore store) {
        UserLoginService sessions = new UserLoginService(tenantId -> {
        }, (tenantId, userId) -> {
        }, cache, new AccessLimitProperties());
        return new ClientAccountService(store, sessions, new ClientAuthProperties());
    }

    private static ClientLoginStore activeStore() {
        return new ClientLoginStore() {
            @Override
            public RegisteredClient registerPassword(
                    String tenantId, String loginName, String salt, String passwordHash, String userId, String displayName) {
                return null;
            }

            @Override
            public RegisteredClient matchPassword(String tenantId, String loginName, String password) {
                return null;
            }

            @Override
            public RegisteredClient wechat(String tenantId, String openId, String userId, String displayName) {
                return new RegisteredClient(tenantId, userId, displayName, true);
            }
        };
    }

    private static WxRemote remote() {
        return new WxRemote() {
            @Override
            public String openId(String jsCode) {
                return "openid-1";
            }

            @Override
            public byte[] qrCode(String scene) {
                return new byte[] {1, 2, 3};
            }
        };
    }

    private static WxMiniProperties ready() {
        WxMiniProperties settings = new WxMiniProperties();
        settings.setAppId("wx-test");
        settings.setAppSecret("secret");
        return settings;
    }
}
