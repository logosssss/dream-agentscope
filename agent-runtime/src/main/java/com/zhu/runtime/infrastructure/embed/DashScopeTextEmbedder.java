package com.zhu.runtime.infrastructure.embed;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用 DashScope 兼容接口做嵌入。密钥不是启动门槛。
 * 失败只打日志，不把原文、密钥或响应正文带出去。
 */
@Component
public class DashScopeTextEmbedder implements TextEmbedder {

    private static final Logger log = Logger.getLogger(DashScopeTextEmbedder.class.getName());

    /** 嵌入模型锁定，换模型要先重建集合。 */
    static final String MODEL = "text-embedding-v4";

    /** 兼容模式嵌入地址。不要改成别的厂商。 */
    static final URI ENDPOINT = URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings");

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final String apiKey;

    private final HttpClient http;

    /**
     * 密钥只做 trim。空串表示这次进程没配，对话仍可启动。
     * 只有这一条构造器，避免容器去找无参构造。
     */
    public DashScopeTextEmbedder(@Value("${DASHSCOPE_API_KEY:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * 没密钥、空白文本或响应对不上维度时返回空数组。
     * 中断时恢复中断标记，仍不往外抛。
     */
    @Override
    public float[] embed(String text) {
        if (apiKey.isEmpty() || text == null || text.isBlank()) {
            return new float[0];
        }
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(text), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warning("embedding failed");
                return new float[0];
            }
            float[] vector = parse(response.body());
            if (vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                log.warning("embedding failed");
                return new float[0];
            }
            return vector;
        } catch (IOException ex) {
            log.warning("embedding failed");
            return new float[0];
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warning("embedding failed");
            return new float[0];
        }
    }

    /**
     * 只抽出 embedding 数组。维度对不上就丢掉，避免写进错误集合。
     */
    public static float[] parse(String body) {
        if (body == null || body.isBlank()) {
            return new float[0];
        }
        int field = body.indexOf("\"embedding\"");
        int open = field < 0 ? -1 : body.indexOf('[', field);
        int close = open < 0 ? -1 : body.indexOf(']', open);
        if (open < 0 || close < 0) {
            return new float[0];
        }
        String raw = body.substring(open + 1, close).trim();
        if (raw.isEmpty()) {
            return new float[0];
        }
        String[] parts = raw.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                values.add(Float.parseFloat(token));
            } catch (NumberFormatException ex) {
                return new float[0];
            }
        }
        if (values.size() != MilvusCollections.DEFAULT_DIMENSIONS) {
            return new float[0];
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i);
        }
        return vector;
    }

    /** 摘要已经截过，这里只转义 JSON 字符串，不改原意。 */
    public static String payload(String text) {
        return "{\"model\":\"" + MODEL
                + "\",\"input\":\"" + escape(text)
                + "\",\"dimensions\":" + MilvusCollections.DEFAULT_DIMENSIONS
                + "}";
    }

    /** 只转义 JSON 字符串里的特殊字符，控制字符压成空格。 */
    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' || ch == '"') {
                escaped.append('\\').append(ch);
            } else if (ch < ' ') {
                escaped.append(' ');
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}
