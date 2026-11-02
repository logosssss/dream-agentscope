package com.zhu.bootstrap;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.infrastructure.tool.KnowledgeSearchTool;
import com.zhu.state.api.KnowledgeChunkItem;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import io.agentscope.core.agent.RuntimeContext;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hybrid knowledge_search。不连 Milvus / MySQL。
 */
class KnowledgeSearchToolTest {

    /** 向量命中优先；全文补足未见过的块。 */
    @Test
    void hybridMergesVectorThenFulltext() {
        TextEmbedder embedder = mock(TextEmbedder.class);
        when(embedder.embed("请假")).thenReturn(new float[MilvusCollections.DEFAULT_DIMENSIONS]);
        VectorStore vectors = mock(VectorStore.class);
        when(vectors.search(any(VectorQuery.class))).thenReturn(List.of(new VectorHit("c1", 0.9f)));
        KnowledgeRepository knowledge = mock(KnowledgeRepository.class);
        when(knowledge.findSuccessChunksByIds(eq("t1"), eq("u1"), eq("a1"), eq(List.of("c1"))))
                .thenReturn(List.of(chunk("c1", "向量命中正文")));
        when(knowledge.searchSuccessByFulltext(eq("t1"), eq("u1"), eq("a1"), eq("请假"), anyInt()))
                .thenReturn(List.of(chunk("c1", "全文重复"), chunk("c2", "全文补缺")));

        KnowledgeSearchTool tool = new KnowledgeSearchTool(vectors, knowledge, embedder);
        String out = tool.search("请假", context());

        assertThat(out).contains("向量命中正文").contains("全文补缺").doesNotContain("全文重复");
    }

    /** 嵌入失败时降级纯全文，仍能返回命中。 */
    @Test
    void degradesToFulltextWhenEmbedFails() {
        TextEmbedder embedder = mock(TextEmbedder.class);
        when(embedder.embed("制度")).thenThrow(new IllegalStateException("no key"));
        KnowledgeRepository knowledge = mock(KnowledgeRepository.class);
        when(knowledge.searchSuccessByFulltext(eq("t1"), eq("u1"), eq("a1"), eq("制度"), anyInt()))
                .thenReturn(List.of(chunk("c9", "制度全文")));
        when(knowledge.findSuccessChunksByIds(any(), any(), any(), anyList())).thenReturn(List.of());

        KnowledgeSearchTool tool =
                new KnowledgeSearchTool(mock(VectorStore.class), knowledge, embedder);
        String out = tool.search("制度", context());

        assertThat(out).contains("制度全文");
    }

    /** 身份或查询缺失时友好空结果。 */
    @Test
    void blankQueryOrIdentityReturnsFriendlyEmpty() {
        KnowledgeSearchTool tool = new KnowledgeSearchTool(
                mock(VectorStore.class), mock(KnowledgeRepository.class), mock(TextEmbedder.class));
        assertThat(tool.search("  ", context())).isEqualTo("未找到相关知识。");
        assertThat(tool.search("q", RuntimeContext.builder().userId("u1").sessionId("s1").build()))
                .isEqualTo("未找到相关知识。");
    }

    private static RuntimeContext context() {
        return RuntimeContext.builder()
                .userId("u1")
                .sessionId("s1")
                .put("tenantId", "t1")
                .put("agentId", "a1")
                .build();
    }

    private static KnowledgeChunkItem chunk(String id, String content) {
        return new KnowledgeChunkItem(id, "kb", "doc", 1, "制度", content, 0);
    }
}
