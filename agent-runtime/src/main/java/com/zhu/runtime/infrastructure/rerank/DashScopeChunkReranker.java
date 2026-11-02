package com.zhu.runtime.infrastructure.rerank;

import com.zhu.runtime.domain.knowledge.ChunkReranker;
import com.zhu.state.api.KnowledgeChunkItem;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 运行层基础设施：百炼文本重排。密钥不是启动门槛。
 * 失败只打短日志，退回 Hybrid 原序截断，不抛给模型线程。
 */
@Component
public class DashScopeChunkReranker implements ChunkReranker {

    private static final Logger log = Logger.getLogger(DashScopeChunkReranker.class.getName());

    /** 重排模型锁定；换模型要先过评审。 */
    static final String MODEL = "qwen3.7-text-rerank";

    /** 固定主机，不引入 WorkspaceId。 */
    static final URI ENDPOINT =
            URI.create("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank");

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** 单条文档串字符上限，与切块字符口径一致。 */
    public static final int DOC_CHAR_LIMIT = 2000;

    private final String apiKey;

    private final HttpClient http;

    /**
     * 密钥只做 trim。空串表示这次进程没配，检索仍可走 Hybrid 原序。
     * 只有这一条构造器，避免容器去找无参构造。
     */
    public DashScopeChunkReranker(@Value("${DASHSCOPE_API_KEY:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * 无密钥、空白查询或候选空时直接原序截断。
     * HTTP / 解析失败同样降级，不把正文或密钥带进日志。
     */
    @Override
    public List<KnowledgeChunkItem> rerank(String query, List<KnowledgeChunkItem> candidates, int topK) {
        List<KnowledgeChunkItem> safe = candidates == null ? List.of() : candidates;
        int limit = topK < 1 ? 5 : topK;
        String q = query == null ? "" : query.trim();
        if (apiKey.isEmpty() || q.isEmpty() || safe.isEmpty()) {
            return clip(safe, limit);
        }
        List<KnowledgeChunkItem> usable = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        for (KnowledgeChunkItem chunk : safe) {
            if (chunk == null) {
                continue;
            }
            String text = documentText(chunk);
            if (text == null) {
                continue;
            }
            usable.add(chunk);
            documents.add(text);
        }
        if (usable.isEmpty()) {
            return clip(safe, limit);
        }
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(q, documents, limit), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warning("rerank failed");
                return clip(safe, limit);
            }
            List<KnowledgeChunkItem> ranked = mapByIndex(usable, parseIndices(response.body()), limit);
            if (ranked.isEmpty()) {
                log.warning("rerank failed");
                return clip(safe, limit);
            }
            return ranked;
        } catch (IOException ex) {
            log.warning("rerank failed");
            return clip(safe, limit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warning("rerank failed");
            return clip(safe, limit);
        }
    }

    /**
     * 按 API 返回的 index 回映射本地候选。越界或重复跳过。
     */
    public static List<KnowledgeChunkItem> mapByIndex(
            List<KnowledgeChunkItem> usable, List<Integer> indices, int topK) {
        if (usable == null || usable.isEmpty() || indices == null || indices.isEmpty()) {
            return List.of();
        }
        List<KnowledgeChunkItem> ranked = new ArrayList<>(Math.min(topK, usable.size()));
        Set<Integer> seen = new HashSet<>();
        for (Integer index : indices) {
            if (index == null || index < 0 || index >= usable.size() || !seen.add(index)) {
                continue;
            }
            ranked.add(usable.get(index));
            if (ranked.size() >= topK) {
                break;
            }
        }
        return ranked;
    }

    /**
     * 只抽出 results 里按序出现的 index。解析失败返回空列表，由调用方降级。
     */
    public static List<Integer> parseIndices(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        int resultsField = body.indexOf("\"results\"");
        int open = resultsField < 0 ? -1 : body.indexOf('[', resultsField);
        if (open < 0) {
            return List.of();
        }
        int depth = 0;
        int close = -1;
        for (int i = open; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    close = i;
                    break;
                }
            }
        }
        if (close < 0) {
            return List.of();
        }
        String array = body.substring(open + 1, close);
        List<Integer> indices = new ArrayList<>();
        int cursor = 0;
        while (cursor < array.length()) {
            int field = array.indexOf("\"index\"", cursor);
            if (field < 0) {
                break;
            }
            int colon = array.indexOf(':', field + 7);
            if (colon < 0) {
                break;
            }
            int start = colon + 1;
            while (start < array.length() && Character.isWhitespace(array.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < array.length() && Character.isDigit(array.charAt(end))) {
                end++;
            }
            if (end == start) {
                cursor = field + 7;
                continue;
            }
            try {
                indices.add(Integer.parseInt(array.substring(start, end)));
            } catch (NumberFormatException ex) {
                return List.of();
            }
            cursor = end;
        }
        return indices;
    }

    /**
     * 拼百炼 text-rerank 请求体。不传 return_documents / instruct。
     */
    public static String payload(String query, List<String> documents, int topN) {
        StringBuilder json = new StringBuilder(256 + documents.size() * 64);
        json.append("{\"model\":\"").append(MODEL).append("\",\"input\":{\"query\":\"")
                .append(escape(query))
                .append("\",\"documents\":[");
        for (int i = 0; i < documents.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(escape(documents.get(i))).append('"');
        }
        json.append("]},\"parameters\":{\"top_n\":").append(topN).append("}}");
        return json.toString();
    }

    /**
     * titleHint 非空则换行拼接；空 content 返回 null 表示剔除。
     * 按 Java 字符长度截到 DOC_CHAR_LIMIT。
     */
    public static String documentText(KnowledgeChunkItem chunk) {
        if (chunk == null || chunk.content() == null || chunk.content().isBlank()) {
            return null;
        }
        String content = chunk.content();
        String title = chunk.titleHint();
        String raw = title == null || title.isBlank() ? content : title + "\n" + content;
        if (raw.length() <= DOC_CHAR_LIMIT) {
            return raw;
        }
        return raw.substring(0, DOC_CHAR_LIMIT);
    }

    /** 保留原顺序，截到 topK；跳过 null。 */
    static List<KnowledgeChunkItem> clip(List<KnowledgeChunkItem> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK < 1) {
            return List.of();
        }
        List<KnowledgeChunkItem> out = new ArrayList<>(Math.min(topK, candidates.size()));
        for (KnowledgeChunkItem chunk : candidates) {
            if (chunk == null) {
                continue;
            }
            out.add(chunk);
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
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
