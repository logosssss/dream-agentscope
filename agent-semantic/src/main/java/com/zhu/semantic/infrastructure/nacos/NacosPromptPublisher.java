package com.zhu.semantic.infrastructure.nacos;

import com.zhu.semantic.infrastructure.prompt.PromptRelease;
import com.zhu.semantic.infrastructure.prompt.PromptSink;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.json.JsonParserFactory;

/**
 * 把校验过的提示词发到 Nacos 的提示词管理接口。每次发布一个新版本，并让 latest 指向它。
 * 不是启动组件。地址为空、或对方拒绝时失败，不改已经在跑的监听器。
 */
public final class NacosPromptPublisher implements PromptSink {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;

    private final String namespace;

    private final String username;

    private final String password;

    private final Clock clock;

    private final HttpClient http;

    private String accessToken;

    private boolean loggedIn;

    /**
     * 地址可以不带协议，这时按 http 补上。账号为空则不登录。
     * 时钟用来生成版本号，测试可以固定它。
     */
    public NacosPromptPublisher(String serverAddr, String namespace, String username, String password, Clock clock) {
        this.baseUrl = base(serverAddr);
        this.namespace = namespace == null ? "" : namespace;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * 目录先校验再发布。没有目录时不访问网络。
     */
    public void release(Path directory) {
        PromptRelease.publish(directory, this);
    }

    /**
     * 新版本号是 1.0.秒。先建草稿，再提交，再发布。任一步失败都停。
     * 不把模板或口令写进异常。
     */
    @Override
    public void publish(String key, String body) {
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("prompt server is required");
        }
        String version = "1.0." + clock.instant().getEpochSecond();
        login();
        post("/nacos/v3/admin/ai/prompt/draft", form(key, version, body, true));
        post("/nacos/v3/admin/ai/prompt/submit", form(key, version, null, false));
        post("/nacos/v3/admin/ai/prompt/publish", form(key, version, null, false));
    }

    /** 没配账号就跳过。登录失败不带上口令。 */
    private void login() {
        if (loggedIn || username.isBlank()) {
            loggedIn = true;
            return;
        }
        String payload = encode("username") + "=" + encode(username) + "&" + encode("password") + "=" + encode(password);
        HttpResponse<String> response = send("/nacos/v3/auth/user/login", payload);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("prompt login failed");
        }
        accessToken = tokenOf(response.body());
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("prompt login failed");
        }
        loggedIn = true;
    }

    /** 管理接口返回的 code 不是 0 时，不把响应正文带出去。 */
    private void post(String path, String form) {
        HttpResponse<String> response = send(path, form);
        if (response.statusCode() != 200 || !accepted(response.body())) {
            throw new IllegalStateException("prompt publish failed");
        }
    }

    /** 带上已经拿到的令牌。超时和连接失败都算这次发布失败。 */
    private HttpResponse<String> send(String path, String form) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (accessToken != null && !accessToken.isBlank()) {
            request.header("accessToken", accessToken);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("prompt publish failed");
        }
    }

    /** 草稿才带模板。命名空间为空时不传，让服务端用自己的默认值。 */
    private String form(String key, String version, String body, boolean draft) {
        StringBuilder form = new StringBuilder();
        append(form, "promptKey", key);
        append(form, "version", version);
        if (draft) {
            append(form, "targetVersion", version);
            append(form, "template", body == null ? "" : body);
        }
        if (!namespace.isBlank()) {
            append(form, "namespaceId", namespace);
        }
        return form.toString();
    }

    /** 表单字段按顺序拼接。值里的空格和换行要编码。 */
    private static void append(StringBuilder form, String name, String value) {
        if (!form.isEmpty()) {
            form.append('&');
        }
        form.append(encode(name)).append('=').append(encode(value));
    }

    /** code 为 0 才算成功。解析不了的正文也算失败。 */
    private static boolean accepted(String body) {
        try {
            Object code = JsonParserFactory.getJsonParser().parseMap(body).get("code");
            return code instanceof Number number && number.intValue() == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 令牌可以在 data 里，也可以在最外层。没有就当登录失败。 */
    private static String tokenOf(String body) {
        try {
            Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(body);
            Object data = root.get("data");
            if (data instanceof Map<?, ?> nested && nested.get("accessToken") != null) {
                return String.valueOf(nested.get("accessToken"));
            }
            Object token = root.get("accessToken");
            return token == null ? null : String.valueOf(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 没写协议时补 http。空白地址留空，等真正发布时再拒绝。 */
    private static String base(String serverAddr) {
        if (serverAddr == null || serverAddr.isBlank()) {
            return "";
        }
        String trimmed = serverAddr.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
    }

    /** 表单编码。空值编码成空串。 */
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
