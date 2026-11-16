package com.zhu.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.vector.api.KnowledgeFilter;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.application.AbsentVectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import com.zhu.vector.infrastructure.MilvusVectorStore;
import org.junit.jupiter.api.Test;

/**
 * 过滤条件和空实现不连接 Milvus。真实客户端只校验入参。
 * 情景集合路径已硬删。
 */
class VectorStoreTest {

    /** 引号被转义，条件仍是并且关系。 */
    @Test
    void knowledgeFilterRequiresBoth() {
        assertThat(KnowledgeFilter.of("user\"a", "agent\\b"))
                .isEqualTo("user_id == \"user\\\"a\" && agent_id == \"agent\\\\b\"");
    }

    /** 缺用户时不生成条件。 */
    @Test
    void blankUserIsRejected() {
        assertThatThrownBy(() -> KnowledgeFilter.of(" ", "agent-a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 空实现不连库，查询是空列表。 */
    @Test
    void absentStoreSkipsWrites() {
        AbsentVectorStore store = new AbsentVectorStore();
        store.upsert(new VectorRecord("knowledge", "1", new float[] {0.1f}, "", "u", "a"));
        store.deleteById("knowledge", "1");
        store.deleteByScope("knowledge", "t", "u", "a");
        assertThat(store.search(new VectorQuery("knowledge", new float[] {0.1f}, "", "u", "a", 3)))
                .isEmpty();
    }

    /** 真实客户端在连库前拒绝空身份，避免扫到别人的向量。 */
    @Test
    void milvusStoreRejectsBlankIdentity() {
        MilvusVectorStore store = new MilvusVectorStore(null);
        assertThatThrownBy(() -> store.search(new VectorQuery("knowledge", new float[] {0.1f}, "", " ", "a", 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.upsert(new VectorRecord(
                "knowledge", "1", new float[] {0.1f}, "t", " ", "a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.deleteById(" ", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new VectorHit("1", 0.9f).id()).isEqualTo("1");
        assertThat(MilvusCollections.DEFAULT_KNOWLEDGE).isEqualTo("knowledge");
    }
}
