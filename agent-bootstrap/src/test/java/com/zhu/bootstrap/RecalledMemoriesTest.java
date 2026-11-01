package com.zhu.bootstrap;

import com.zhu.runtime.domain.RecalledMemories;
import com.zhu.state.api.EpisodicMemoryItem;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最近和近邻的合并规则。不连接 MySQL，也不改提示词。
 */
class RecalledMemoriesTest {

    /** 同一 id 只留一份。显著性优先，再按时间新到旧，最多 5 条。 */
    @Test
    void mergeDedupsAndCapsAtFive() {
        LocalDateTime newer = LocalDateTime.of(2026, 11, 1, 12, 0);
        List<EpisodicMemoryItem> recent = List.of(
                item(1, 2, newer),
                item(2, 2, newer.minusHours(1)),
                item(3, 4, newer.minusHours(2)));
        List<EpisodicMemoryItem> neighbors = List.of(
                item(3, 4, newer.minusHours(2)),
                item(6, 5, newer.minusHours(3)),
                item(7, 1, newer.plusHours(1)),
                item(8, 1, newer.minusHours(4)));

        RecalledMemories recalled = RecalledMemories.merge(recent, neighbors);

        assertThat(recalled.items()).extracting(EpisodicMemoryItem::id).containsExactly(6L, 3L, 1L, 2L, 7L);
    }

    /** 空输入仍是空结果，不会留下 null。 */
    @Test
    void emptyInputsStayEmpty() {
        assertThat(RecalledMemories.merge(null, null).items()).isEmpty();
        assertThat(RecalledMemories.empty().items()).isEmpty();
        assertThat(new RecalledMemories(new ArrayList<>()).items()).isEmpty();
    }

    /** 构造一条短记忆。 */
    private static EpisodicMemoryItem item(long id, int salience, LocalDateTime createdAt) {
        return new EpisodicMemoryItem(id, "t", "u", "s", "m" + id, salience, createdAt);
    }
}
