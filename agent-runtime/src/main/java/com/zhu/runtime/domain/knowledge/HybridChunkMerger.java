package com.zhu.runtime.domain.knowledge;

import com.zhu.state.api.KnowledgeChunkItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid 检索去重合并。向量优先，全文补缺，压到 topK。
 */
public final class HybridChunkMerger {

    private HybridChunkMerger() {
    }

    /**
     * 按 id 去重。先保留 vector 顺序，再追加 fulltext 中未见过的块。
     * topK 小于 1 时按 5。
     */
    public static List<KnowledgeChunkItem> merge(
            List<KnowledgeChunkItem> vectorHits, List<KnowledgeChunkItem> fulltextHits, int topK) {
        int limit = topK < 1 ? 5 : topK;
        Map<String, KnowledgeChunkItem> ordered = new LinkedHashMap<>();
        append(ordered, vectorHits, limit);
        append(ordered, fulltextHits, limit);
        return new ArrayList<>(ordered.values());
    }

    private static void append(Map<String, KnowledgeChunkItem> ordered, List<KnowledgeChunkItem> hits, int limit) {
        if (hits == null || ordered.size() >= limit) {
            return;
        }
        for (KnowledgeChunkItem hit : hits) {
            if (hit == null || hit.id() == null || hit.id().isBlank()) {
                continue;
            }
            if (ordered.containsKey(hit.id())) {
                continue;
            }
            ordered.put(hit.id(), hit);
            if (ordered.size() >= limit) {
                return;
            }
        }
    }
}
