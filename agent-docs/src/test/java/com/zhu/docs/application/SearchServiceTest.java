package com.zhu.docs.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.docs.api.DocsEmbedder;
import com.zhu.docs.api.DocsSearchHit;
import com.zhu.docs.api.DocsSearchQuery;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 搜索编排：有向量就不再走关键词；没有向量才 LIKE。不连 Milvus。
 */
class SearchServiceTest {

    /** 向量命中按分数顺序留下，关键词计数保持 0。 */
    @Test
    void vectorHitsComeFirst() {
        FakeIndex index = new FakeIndex();
        index.byId.add(chunk("c1", "d1", "公开", "闸门", "gate", "闸门顺序为什么重要"));
        index.byId.add(chunk("c2", "d2", "另一篇", "其它", "other", "无关"));
        FakeVectors vectors = new FakeVectors(List.of(
                new DocsVectorHit("c2", 0.2),
                new DocsVectorHit("c1", 0.9)));
        SearchService service = service(index, text -> vector(), vectors);

        List<DocsSearchHit> hits = service.search(null, new DocsSearchQuery("闸门顺序", null, null, null)).hits();

        assertThat(hits).extracting(DocsSearchHit::docId).containsExactly("d2", "d1");
        assertThat(hits.get(0).score()).isEqualTo(0.2);
        assertThat(index.likeCalls).isZero();
        assertThat(vectors.filter).isEqualTo("visibility == \"public\"");
    }

    /** 嵌入失败时改走关键词，并且匿名看不到私有块。 */
    @Test
    void keywordFallbackHidesPrivate() {
        FakeIndex index = new FakeIndex();
        index.like.add(chunk("c-private", "secret", "私有", "面试", "a", "只有主人能看"));
        index.like.add(chunk("c-public", "open", "回滚", "发布", "rollback", "生产环境怎么回滚"));
        SearchService service = service(index, text -> new float[0], new FakeVectors(List.of()));

        List<DocsSearchHit> hits = service.search(null, new DocsSearchQuery("回滚", "ops", null, 8)).hits();

        assertThat(hits).extracting(DocsSearchHit::docId).containsExactly("open");
        assertThat(hits.get(0).score()).isZero();
        assertThat(index.likeOwner).isFalse();
        assertThat(index.likeTopic).isEqualTo("ops");
    }

    /** 空问句不嵌入。非法主题也不搜。 */
    @Test
    void blankAndUnknownTopicStayEmpty() {
        int[] embeds = {0};
        FakeIndex index = new FakeIndex();
        SearchService service = service(index, text -> {
            embeds[0]++;
            return vector();
        }, new FakeVectors(List.of(new DocsVectorHit("c1", 1))));

        assertThat(service.search("owner", new DocsSearchQuery("  ", null, null, null)).hits()).isEmpty();
        assertThat(service.search(null, new DocsSearchQuery("闸门", "nope", null, null)).hits()).isEmpty();
        assertThat(embeds[0]).isZero();
        assertThat(index.likeCalls).isZero();
    }

    /** 向量给了 9 条可见块时，页面只留 8 条。 */
    @Test
    void pageStopsAtEight() {
        FakeIndex index = new FakeIndex();
        List<DocsVectorHit> found = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            index.byId.add(chunk("c" + i, "d" + i, "标题" + i, "节", "a" + i, "正文" + i));
            found.add(new DocsVectorHit("c" + i, 1));
        }
        SearchService service = service(index, text -> vector(), new FakeVectors(found));

        assertThat(service.search(null, new DocsSearchQuery("正文", null, null, 50)).hits()).hasSize(8);
    }

    private static SearchService service(FakeIndex index, DocsEmbedder embedder, FakeVectors vectors) {
        DocsProperties properties = new DocsProperties();
        properties.setOwnerUserIds(List.of("owner"));
        return new SearchService(index, embedder, provider(vectors), properties);
    }

    private static ObjectProvider<DocsVectors> provider(DocsVectors vectors) {
        return new ObjectProvider<>() {
            @Override
            public DocsVectors getIfAvailable() {
                return vectors;
            }
        };
    }

    private static float[] vector() {
        return new float[MilvusCollections.DEFAULT_DIMENSIONS];
    }

    private static DocsSearchChunk chunk(
            String id, String docId, String title, String heading, String anchor, String text) {
        return new DocsSearchChunk(id, docId, title, heading, anchor, text, "前文" + text + "后文");
    }

    /** 只记住搜索会读的两处。可见性在这里丢掉私有 id。 */
    private static final class FakeIndex implements DocsSearchIndex {

        private final List<DocsSearchChunk> byId = new ArrayList<>();

        private final List<DocsSearchChunk> like = new ArrayList<>();

        private int likeCalls;

        private boolean likeOwner;

        private String likeTopic = "";

        @Override
        public List<DocsSearchChunk> chunksByIds(Collection<String> ids, boolean owner) {
            List<DocsSearchChunk> found = new ArrayList<>();
            for (DocsSearchChunk chunk : byId) {
                if (ids.contains(chunk.chunkId()) && (owner || !chunk.docId().equals("secret"))) {
                    found.add(chunk);
                }
            }
            return found;
        }

        @Override
        public List<DocsSearchChunk> likeChunks(String pattern, String topic, String source, boolean owner, int limit) {
            likeCalls++;
            likeOwner = owner;
            likeTopic = topic;
            List<DocsSearchChunk> found = new ArrayList<>();
            for (DocsSearchChunk chunk : like) {
                if (!owner && "secret".equals(chunk.docId())) {
                    continue;
                }
                found.add(chunk);
                if (found.size() == limit) {
                    break;
                }
            }
            return found;
        }
    }

    /** 记录过滤表达式，命中表由测试给定。 */
    private static final class FakeVectors implements DocsVectors {

        private final List<DocsVectorHit> hits;

        private String filter = "";

        private FakeVectors(List<DocsVectorHit> hits) {
            this.hits = hits;
        }

        @Override
        public java.util.Set<String> present(Collection<String> ids) {
            return java.util.Set.of();
        }

        @Override
        public void upsert(String id, float[] vector, String docId, String topic, String visibility, String source) {
        }

        @Override
        public void delete(Collection<String> ids) {
        }

        @Override
        public List<DocsVectorHit> search(float[] vector, String filter, int topK) {
            this.filter = filter;
            return hits;
        }
    }
}
