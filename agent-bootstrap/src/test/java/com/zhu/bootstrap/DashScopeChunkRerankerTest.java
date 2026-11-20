package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.rerank.DashScopeChunkReranker;
import com.zhu.state.api.knowledge.KnowledgeChunkItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 百炼重排解析与无密钥降级。不连接 DashScope。
 */
class DashScopeChunkRerankerTest {

    /** 没密钥时不发起调用，按 Hybrid 原序截断。 */
    @Test
    void missingKeyKeepsHybridOrder() {
        DashScopeChunkReranker reranker = new DashScopeChunkReranker("");
        List<KnowledgeChunkItem> ranked =
                reranker.rerank("请假", List.of(chunk("c1", "制度", "一"), chunk("c2", "制度", "二")), 1);
        assertThat(ranked).extracting(KnowledgeChunkItem::id).containsExactly("c1");
    }

    /** 请求体锁定模型与 top_n；文档串转义引号。 */
    @Test
    void payloadLocksModelAndTopN() {
        String body = DashScopeChunkReranker.payload("说\"你好\"", List.of("段\"落\""), 5);
        assertThat(body).contains("qwen3.7-text-rerank");
        assertThat(body).contains("\"top_n\":5");
        assertThat(body).doesNotContain("return_documents");
        assertThat(body).doesNotContain("instruct");
        assertThat(body).contains("说\\\"你好\\\"");
        assertThat(body).contains("段\\\"落\\\"");
    }

    /** 按 results 里 index 顺序回映射；非法 index 跳过。 */
    @Test
    void parseMapsIndicesInOrder() {
        List<Integer> indices = DashScopeChunkReranker.parseIndices(
                "{\"output\":{\"results\":[{\"index\":2,\"relevance_score\":0.9},"
                        + "{\"index\":0,\"relevance_score\":0.5},{\"index\":9}]}}");
        assertThat(indices).containsExactly(2, 0, 9);

        List<KnowledgeChunkItem> usable =
                List.of(chunk("a", "t", "A"), chunk("b", "t", "B"), chunk("c", "t", "C"));
        assertThat(DashScopeChunkReranker.mapByIndex(usable, indices, 2))
                .extracting(KnowledgeChunkItem::id)
                .containsExactly("c", "a");
    }

    /** 标题换行拼接后按字符截断；空正文剔除。 */
    @Test
    void documentTextJoinsAndTruncates() {
        assertThat(DashScopeChunkReranker.documentText(chunk("id", "标题", "正文")))
                .isEqualTo("标题\n正文");
        assertThat(DashScopeChunkReranker.documentText(chunk("id", "", "只有正文"))).isEqualTo("只有正文");
        assertThat(DashScopeChunkReranker.documentText(chunk("id", "t", "  "))).isNull();

        String longContent = "x".repeat(DashScopeChunkReranker.DOC_CHAR_LIMIT + 50);
        String truncated = DashScopeChunkReranker.documentText(chunk("id", "标题", longContent));
        assertThat(truncated).hasSize(DashScopeChunkReranker.DOC_CHAR_LIMIT);
        assertThat(truncated).startsWith("标题\n");
    }

    private static KnowledgeChunkItem chunk(String id, String title, String content) {
        return new KnowledgeChunkItem(id, "kb", "doc", 1, title, content, 0);
    }
}
