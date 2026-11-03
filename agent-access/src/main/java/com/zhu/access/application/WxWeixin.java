package com.zhu.access.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.access.config.WxMiniProperties;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/**
 * 用配置里的小程序换 openid，并生成确认页的小程序码。
 * 密钥和 code 不写日志。
 */
@Component
public class WxWeixin implements WxRemote {

    private static final Logger log = Logger.getLogger(WxWeixin.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WxMiniProperties settings;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Object tokenLock = new Object();

    private String accessToken = "";

    private long accessTokenUntil;

    /** 正式进程用配置里的 AppID 和密钥。没配齐时出码会先被拦住。 */
    public WxWeixin(WxMiniProperties settings) {
        this.settings = settings;
    }

    /**
     * code 换 openid。微信返回错误码时不把响应原文抛出去。
     */
    @Override
    public String openId(String jsCode) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + enc(settings.getAppId())
                + "&secret=" + enc(settings.getAppSecret())
                + "&js_code=" + enc(jsCode)
                + "&grant_type=authorization_code";
        JsonNode node = read(get(url));
        int err = node.path("errcode").asInt(0);
        if (err != 0) {
            log.warning("wx code2session errcode=" + err);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "微信登录失败，请重试");
        }
        String openId = node.path("openid").asText("");
        if (openId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "微信登录失败，请重试");
        }
        return openId;
    }

    /**
     * 票号放进 scene。令牌过期时刷新一次再要码。
     */
    @Override
    public byte[] qrCode(String scene) {
        byte[] raw = requestQr(scene, false);
        if (errorCode(raw) == 40001 || errorCode(raw) == 42001) {
            clearToken();
            raw = requestQr(scene, true);
        }
        if (raw == null || raw.length == 0 || raw[0] == '{') {
            log.warning("wx qrcode errcode=" + errorCode(raw));
            throw new BusinessException(ErrorCode.UNAVAILABLE, "生成小程序码失败");
        }
        return raw;
    }

    /** 强制刷新时丢掉缓存的 access_token。 */
    private byte[] requestQr(String scene, boolean forceToken) {
        String token = accessToken(forceToken);
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + enc(token);
        byte[] body;
        try {
            body = JSON.writeValueAsBytes(Map.of(
                    "scene", scene,
                    "page", settings.getLoginPage(),
                    "check_path", false,
                    "env_version", settings.getEnvVersion(),
                    "width", 280));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "生成小程序码失败");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception ex) {
            log.warning("wx qrcode request failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "生成小程序码失败");
        }
    }

    /** 提前 200 秒刷新，避免卡在过期边界。 */
    private String accessToken(boolean force) {
        synchronized (tokenLock) {
            long now = System.currentTimeMillis();
            if (!force && !accessToken.isBlank() && now < accessTokenUntil) {
                return accessToken;
            }
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                    + enc(settings.getAppId()) + "&secret=" + enc(settings.getAppSecret());
            JsonNode node = read(get(url));
            int err = node.path("errcode").asInt(0);
            if (err != 0 || node.path("access_token").asText("").isBlank()) {
                log.warning("wx access_token errcode=" + err);
                throw new BusinessException(ErrorCode.UNAVAILABLE, "生成小程序码失败");
            }
            accessToken = node.path("access_token").asText();
            int expires = node.path("expires_in").asInt(7200);
            accessTokenUntil = now + Math.max(60, expires - 200L) * 1000L;
            return accessToken;
        }
    }

    /** 微信说令牌失效时清掉，下次重新要。 */
    private void clearToken() {
        synchronized (tokenLock) {
            accessToken = "";
            accessTokenUntil = 0;
        }
    }

    /** 网络失败不把地址和密钥带出去。 */
    private String get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception ex) {
            log.warning("wx http failed");
            throw new BusinessException(ErrorCode.UNAVAILABLE, "微信登录失败，请重试");
        }
    }

    /** 解析失败按微信不可用，不把原文返回给页面。 */
    private static JsonNode read(String body) {
        try {
            return JSON.readTree(body == null ? "" : body);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "微信登录失败，请重试");
        }
    }

    /** 小程序码成功时是 PNG。JSON 开头才是错误码。 */
    private static int errorCode(byte[] raw) {
        if (raw == null || raw.length == 0 || raw[0] != '{') {
            return 0;
        }
        try {
            return JSON.readTree(raw).path("errcode").asInt(0);
        } catch (Exception ex) {
            return 0;
        }
    }

    /** 查询参数编码。空值按空串，避免拼出 null。 */
    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
