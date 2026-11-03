package com.zhu.access.application;

import com.zhu.access.api.dto.LoginResponse;
import com.zhu.access.config.WxMiniProperties;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.common.util.Ids;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * 网站出小程序码，小程序确认后把登录结果放进 Redis。
 * 票不留在进程里，多实例都能对上。
 */
@Service
public class WxLoginService {

    /** 窗口内最多同时发出的票。过期后计数键自己消失。 */
    private static final int MAX_TICKETS = 100;

    private static final String WINDOW_KEY = "wx-login-window";

    private static final String TICKET_PREFIX = "wx-login-ticket:";

    private final WxMiniProperties settings;

    private final AccessCache cache;

    private final ClientAccountService accounts;

    private final WxRemote weixin;

    /** 正式进程用微信实现。测试可以换成假的出码。 */
    public WxLoginService(
            WxMiniProperties settings,
            AccessCache cache,
            ClientAccountService accounts,
            WxRemote weixin) {
        this.settings = settings;
        this.cache = cache;
        this.accounts = accounts;
        this.weixin = weixin;
    }

    /** 密钥没配齐时网页不显示扫码。 */
    public boolean enabled() {
        return settings.ready();
    }

    /**
     * 发出一张待确认的票，并返回小程序码。
     * 有效期只允许 1 到 10 分钟。窗口内超过 100 张就拒绝。
     */
    public WxTicket ticket() {
        requireReady();
        int ttl = settings.getTicketTtlSeconds();
        if (ttl < 60 || ttl > 600) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "有效期不对");
        }
        long issued = cache.increment(WINDOW_KEY, Duration.ofSeconds(ttl));
        if (issued > MAX_TICKETS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "码太多");
        }
        String ticketId = Ids.newId();
        cache.put(ticketKey(ticketId), "PENDING", Duration.ofSeconds(ttl));
        byte[] png = weixin.qrCode(ticketId);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        return new WxTicket(ticketId, ttl, dataUrl);
    }

    /**
     * 票不在或已过期时状态是 EXPIRED。确认后的登录结果只在这两分钟内能取走。
     */
    public WxPoll poll(String ticketId) {
        String raw = ticketId == null || ticketId.isBlank() ? null : cache.get(ticketKey(ticketId.trim()));
        if (raw == null || raw.isBlank() || "PENDING".equals(raw)) {
            return raw == null || raw.isBlank() ? WxPoll.expired() : WxPoll.pending();
        }
        LoginResponse login = decode(raw);
        if (login == null) {
            return WxPoll.expired();
        }
        return WxPoll.confirmed(login);
    }

    /**
     * 小程序交来票号和 code。同一张票只能确认一次。
     * 用户已停用时删掉票，网页轮询会看到过期。
     */
    public void confirm(String ticketId, String jsCode) {
        requireReady();
        if (ticketId == null || ticketId.isBlank() || jsCode == null || jsCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不完整");
        }
        String id = ticketId.trim();
        String lockKey = "wx-login-lock:" + id;
        if (!cache.lock(lockKey, "confirm", Duration.ofSeconds(30))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码已使用");
        }
        try {
            String raw = cache.get(ticketKey(id));
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码已过期，请刷新后重试");
            }
            if (!"PENDING".equals(raw)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码已使用");
            }
            String openId = weixin.openId(jsCode.trim());
            try {
                LoginResponse login = accounts.wechat(openId);
                cache.put(ticketKey(id), encode(login), Duration.ofSeconds(120));
            } catch (BusinessException ex) {
                if ("登录已停用".equals(ex.getMessage())) {
                    cache.delete(ticketKey(id));
                }
                throw ex;
            }
        } finally {
            cache.unlock(lockKey, "confirm");
        }
    }

    /** 没配密钥时出码和确认都拒绝。查票仍可以看已有的票。 */
    private void requireReady() {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "微信扫码登录未启用");
        }
    }

    /** 会话字段不含换行。显示名由注册时保证。 */
    private static String encode(LoginResponse login) {
        return "CONFIRMED\n" + login.accessToken() + "\n" + login.expiresIn() + "\n"
                + login.tenantId() + "\n" + login.userId() + "\n" + login.displayName();
    }

    /** 段数不对就当过期，避免把半截会话交给网页。 */
    private static LoginResponse decode(String raw) {
        String[] parts = raw.split("\n", -1);
        if (parts.length != 6 || !"CONFIRMED".equals(parts[0]) || parts[1].isBlank() || parts[4].isBlank()) {
            return null;
        }
        long expires;
        try {
            expires = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            return null;
        }
        return new LoginResponse(parts[1], expires, parts[3], parts[4], parts[5]);
    }

    /** 票的缓存键。 */
    private static String ticketKey(String ticketId) {
        return TICKET_PREFIX + ticketId;
    }

    /**
     * 出码结果。网页只拿票号、秒数和图片。
     *
     * @param ticketId      票号，也是小程序码里的 scene
     * @param expiresIn     有效秒数
     * @param qrcodeDataUrl PNG 的 data URL
     */
    public record WxTicket(String ticketId, long expiresIn, String qrcodeDataUrl) {
    }

    /**
     * 查票结果。未确认时 login 为空。
     *
     * @param status  PENDING、CONFIRMED 或 EXPIRED
     * @param login   确认后的会话。其它状态为空
     */
    public record WxPoll(String status, LoginResponse login) {

        /** 还没人确认。 */
        static WxPoll pending() {
            return new WxPoll("PENDING", null);
        }

        /** 票没了或解不开。 */
        static WxPoll expired() {
            return new WxPoll("EXPIRED", null);
        }

        /** 网页可以拿走的会话。 */
        static WxPoll confirmed(LoginResponse login) {
            return new WxPoll("CONFIRMED", login);
        }
    }
}
