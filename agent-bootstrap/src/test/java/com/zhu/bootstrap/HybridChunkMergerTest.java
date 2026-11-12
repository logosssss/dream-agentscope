package com.zhu.bootstrap;

import com.zhu.runtime.domain.knowledge.HybridChunkMerger;
import com.zhu.state.api.knowledge.KnowledgeChunkItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hybrid 合并：向量优先、全文补缺、按 id 去重。不连库。
 */
class HybridChunkMergerTest {

    /** 向量顺序保留；全文只补未见过的块；压到 topK。 */
    @Test
    void vectorFirstThenFulltextFill() {
        KnowledgeChunkItem v1 = chunk("c1", "向量甲");
        KnowledgeChunkItem v2 = chunk("c2", "向量乙");
        KnowledgeChunkItem f2 = chunk("c2", "全文乙");
        KnowledgeChunkItem f3 = chunk("c3", "全文丙");
        KnowledgeChunkItem f4 = chunk("c4", "全文丁");

        List<KnowledgeChunkItem> merged = HybridChunkMerger.merge(
                List.of(v1, v2), List.of(f2, f3, f4), 3);

        assertThat(merged).extracting(KnowledgeChunkItem::id).containsExactly("c1", "c2", "c3");
        assertThat(merged.get(1).content()).isEqualTo("向量乙");
    }

    /** topK 非法时按 5；空输入返回空。 */
    @Test
    void emptyAndDefaultTopK() {
        assertThat(HybridChunkMerger.merge(null, null, 0)).isEmpty();
        assertThat(HybridChunkMerger.merge(List.of(), List.of(chunk("a", "x")), 0))
                .extracting(KnowledgeChunkItem::id)
                .containsExactly("a");
    }

    private static KnowledgeChunkItem chunk(String id, String content) {
        return new KnowledgeChunkItem(id, "kb", "doc", 1, "t", content, 0);
    }
}
