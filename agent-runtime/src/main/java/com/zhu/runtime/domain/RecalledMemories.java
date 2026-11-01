package com.zhu.runtime.domain;

import com.zhu.state.api.EpisodicMemoryItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次对话召回的情景记忆。放进 RuntimeContext，再由中间件追加到系统提示词。
 *
 * @param items 去重后最多 5 条，显著性高的在前，再按时间新到旧
 */
public record RecalledMemories(List<EpisodicMemoryItem> items) {

    /** 最近条数。 */
    public static final int RECENT = 3;

    /** 近邻条数。 */
    public static final int NEIGHBORS = 3;

    /** 合并后上限。 */
    public static final int LIMIT = 5;

    /**
     * 空列表收成不可变副本，避免调用方改掉上下文里的结果。
     */
    public RecalledMemories {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 没有召回时的空值。 */
    public static RecalledMemories empty() {
        return new RecalledMemories(List.of());
    }

    /**
     * 最近和近邻按 id 去重，再按显著性、时间排序，最多 5 条。
     * 同一行只留一份。时间空的排在后面。
     */
    public static RecalledMemories merge(List<EpisodicMemoryItem> recent, List<EpisodicMemoryItem> neighbors) {
        Map<Long, EpisodicMemoryItem> byId = new LinkedHashMap<>();
        putAll(byId, recent);
        putAll(byId, neighbors);
        List<EpisodicMemoryItem> merged = new ArrayList<>(byId.values());
        merged.sort(ORDER);
        if (merged.size() > LIMIT) {
            merged = merged.subList(0, LIMIT);
        }
        return new RecalledMemories(merged);
    }

    private static void putAll(Map<Long, EpisodicMemoryItem> byId, List<EpisodicMemoryItem> items) {
        if (items == null) {
            return;
        }
        for (EpisodicMemoryItem item : items) {
            if (item != null) {
                byId.putIfAbsent(item.id(), item);
            }
        }
    }

    private static final Comparator<EpisodicMemoryItem> ORDER =
            Comparator.comparingInt(EpisodicMemoryItem::salience).reversed()
                    .thenComparing(RecalledMemories::createdAt, Comparator.reverseOrder())
                    .thenComparingLong(EpisodicMemoryItem::id);

    /** 时间为空时当成最早，避免排到新记忆前面。 */
    private static LocalDateTime createdAt(EpisodicMemoryItem item) {
        return item.createdAt() == null ? LocalDateTime.MIN : item.createdAt();
    }
}
