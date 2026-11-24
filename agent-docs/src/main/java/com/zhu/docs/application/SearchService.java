package com.zhu.docs.application;

import com.zhu.docs.api.DocsEmbedder;
import com.zhu.docs.api.DocsSearch;
import com.zhu.docs.api.DocsSearchHit;
import com.zhu.docs.api.DocsSearchQuery;
import com.zhu.docs.api.DocsSearchResult;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 文档搜索。先取向量近邻 20 条，过滤后再留最多 8 条。
 * 还没有用六条样例测过分数，所以不做阈值截断；一条向量都没有时才用 SQL LIKE。
 * 重排先不开，避免在命中率还没看过时多一次模型调用。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class SearchService implements DocsSearch {

    /** 向量候选。比最终展示多，给身份和主题过滤留余量。 */
    static final int CANDIDATES = 20;

    /** 卡片最多这么多。调用方把 limit 写得再大也停在这里。 */
    static final int PAGE = 8;

    /** 问句过长时只取前面。嵌入和 LIKE 都不该吃整篇粘贴。 */
    private static final int QUERY_LIMIT = 200;

    private static final Logger LOG = Logger.getLogger(SearchService.class.getName());

    private final DocsSearchIndex index;

    private final DocsEmbedder embedder;

    private final ObjectProvider<DocsVectors> vectors;

    private final DocsProperties properties;

    /** 向量实现可以缺席。缺席时只走关键词，不改成内存向量。 */
    public SearchService(
            DocsSearchIndex index,
            DocsEmbedder embedder,
            ObjectProvider<DocsVectors> vectors,
            DocsProperties properties) {
        this.index = index;
        this.embedder = embedder;
        this.vectors = vectors;
        this.properties = properties;
    }

    /**
     * 空问句直接空列表。非法主题或来源也空，避免把过滤条件丢掉后搜出全部。
     */
    @Override
    public DocsSearchResult search(String userId, DocsSearchQuery query) {
        String text = query == null || query.q() == null ? "" : query.q().trim();
        if (text.isEmpty()) {
            return new DocsSearchResult(List.of());
        }
        if (text.length() > QUERY_LIMIT) {
            text = text.substring(0, QUERY_LIMIT);
        }
        String topic = token(query.topic());
        if (query.topic() != null && !query.topic().isBlank() && topic == null) {
            return new DocsSearchResult(List.of());
        }
        if (topic != null && !DocsTopicClassifier.TOPICS.contains(topic)) {
            return new DocsSearchResult(List.of());
        }
        String source = token(query.source());
        if (query.source() != null && !query.source().isBlank() && source == null) {
            return new DocsSearchResult(List.of());
        }
        int limit = limit(query.limit());
        boolean owner = isOwner(userId);
        List<DocsSearchHit> vectorHits = fromVectors(text, topic, source, owner, limit);
        if (!vectorHits.isEmpty()) {
            return new DocsSearchResult(vectorHits);
        }
        return new DocsSearchResult(fromKeyword(text, topic, source, owner, limit));
    }

    /**
     * 嵌入失败或 Milvus 没有近邻时返回空，调用方再走关键词。
     * 维度不对的向量不当成结果。
     */
    private List<DocsSearchHit> fromVectors(String text, String topic, String source, boolean owner, int limit) {
        DocsVectors store = vectors.getIfAvailable();
        if (store == null) {
            return List.of();
        }
        float[] vector = embedder.embed(text);
        if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
            return List.of();
        }
        List<DocsVectorHit> found;
        try {
            found = store.search(vector, filter(owner, topic, source), CANDIDATES);
        } catch (RuntimeException ex) {
            LOG.warning("docs vector search failed");
            return List.of();
        }
        if (found == null || found.isEmpty()) {
            return List.of();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (DocsVectorHit hit : found) {
            if (hit != null && hit.id() != null && !hit.id().isBlank()) {
                scores.putIfAbsent(hit.id(), hit.score());
            }
        }
        if (scores.isEmpty()) {
            return List.of();
        }
        Map<String, DocsSearchChunk> chunks = new LinkedHashMap<>();
        for (DocsSearchChunk chunk : index.chunksByIds(scores.keySet(), owner)) {
            if (chunk != null && chunk.chunkId() != null) {
                chunks.putIfAbsent(chunk.chunkId(), chunk);
            }
        }
        List<DocsSearchHit> hits = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            DocsSearchChunk chunk = chunks.get(entry.getKey());
            if (chunk == null) {
                continue;
            }
            hits.add(toHit(chunk, entry.getValue()));
            if (hits.size() == limit) {
                break;
            }
        }
        return List.copyOf(hits);
    }

    /** 关键词只在向量没有可展示命中时使用。分数固定为 0。 */
    private List<DocsSearchHit> fromKeyword(String text, String topic, String source, boolean owner, int limit) {
        List<DocsSearchHit> hits = new ArrayList<>();
        for (DocsSearchChunk chunk : index.likeChunks(likePattern(text), blank(topic), blank(source), owner, limit)) {
            if (chunk == null) {
                continue;
            }
            hits.add(toHit(chunk, 0d));
        }
        return List.copyOf(hits);
    }

    private static DocsSearchHit toHit(DocsSearchChunk chunk, double score) {
        return new DocsSearchHit(
                chunk.docId(),
                chunk.title() == null ? "" : chunk.title(),
                chunk.heading() == null ? "" : chunk.heading(),
                chunk.anchor() == null ? "" : chunk.anchor(),
                DocsSnippet.around(chunk.markdown(), chunk.text()),
                score);
    }

    /**
     * 身份、主题、来源都写进 Milvus 表达式。只拼接已经校验过的标记，不拼问句。
     * {@code or} 的优先级低于 {@code and}，所以可见性要加括号。
     */
    static String filter(boolean owner, String topic, String source) {
        String visibility = owner
                ? "(visibility == \"public\" or visibility == \"private\")"
                : "visibility == \"public\"";
        StringBuilder expression = new StringBuilder(visibility);
        if (topic != null) {
            expression.append(" and topic == \"").append(topic).append('"');
        }
        if (source != null) {
            expression.append(" and source == \"").append(source).append('"');
        }
        return expression.toString();
    }

    /**
     * LIKE 通配符按字面量处理。两侧百分号是「包含」，不是用户输入。
     */
    static String likePattern(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** 空和小于 1 都按一页。大于一页的请求裁到 8。 */
    private static int limit(Integer requested) {
        if (requested == null || requested < 1) {
            return PAGE;
        }
        return Math.min(requested, PAGE);
    }

    /** 只接受短标记。问句里的引号不能进过滤表达式。 */
    private static String token(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9_-]{1,64}")) {
            return null;
        }
        return trimmed;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    /** 空用户不是所有者。名单为空时谁都不能看私有文档。 */
    private boolean isOwner(String userId) {
        return userId != null && !userId.isBlank() && properties.ownerUserIds().contains(userId);
    }
}
