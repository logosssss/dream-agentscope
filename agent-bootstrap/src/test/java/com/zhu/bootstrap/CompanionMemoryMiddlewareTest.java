package com.zhu.bootstrap;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.semantic.config.CompanionPersona;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileRepository;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 普通和升高写短摘要，并在有行 id 后写向量。危机不写。
 * 不连接 MySQL、Milvus，也不调用模型。
 */
class CompanionMemoryMiddlewareTest {

    /** 闲聊写下摘要、相识阶段和情景向量。摘要不含换行。 */
    @Test
    void normalTurnWritesShortMemory() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        List<VectorRecord> vectors = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, vectors, false, true);

        complete(middleware, RiskLevel.NORMAL, "今天去散步了\n顺便买菜");

        assertThat(memories).containsExactly("t|u|s|今天去散步了 顺便买菜|2");
        assertThat(profiles).containsExactly("t|u|ACQUAINTANCE");
        assertThat(vectors).hasSize(1);
        assertThat(vectors.get(0).collection()).isEqualTo(MilvusCollections.EPISODIC_MEMORY);
        assertThat(vectors.get(0).id()).isEqualTo("7");
        assertThat(vectors.get(0).tenantId()).isEqualTo("t");
        assertThat(vectors.get(0).userId()).isEqualTo("u");
        assertThat(vectors.get(0).agentId()).isEqualTo("companion-v1");
        assertThat(vectors.get(0).vector()).hasSize(MilvusCollections.DEFAULT_DIMENSIONS);
    }

    /** 升高档仍写，重要程度更高。 */
    @Test
    void elevatedTurnUsesHigherSalience() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        List<VectorRecord> vectors = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, vectors, false, true);

        complete(middleware, RiskLevel.ELEVATED, "我有点难受");

        assertThat(memories).containsExactly("t|u|s|我有点难受|4");
        assertThat(profiles).containsExactly("t|u|ACQUAINTANCE");
        assertThat(vectors).hasSize(1);
        assertThat(vectors.get(0).id()).isEqualTo("7");
    }

    /** 危机档不落记忆，也不改画像或向量。 */
    @Test
    void criticalTurnSkipsMemory() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        List<VectorRecord> vectors = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, vectors, false, true);

        complete(middleware, RiskLevel.CRITICAL, "我想自杀");

        assertThat(memories).isEmpty();
        assertThat(profiles).isEmpty();
        assertThat(vectors).isEmpty();
    }

    /** 超长用户输入截成短句。写库或写向量失败不往外抛。 */
    @Test
    void longTextIsClippedAndWriteFailureIsSwallowed() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        List<VectorRecord> vectors = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, vectors, false, true);

        complete(middleware, RiskLevel.NORMAL, "a".repeat(100));

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0)).endsWith("|2");
        assertThat(memories.get(0).split("\\|", -1)[3]).hasSize(80);
        assertThat(vectors).hasSize(1);

        CompanionMemoryMiddleware failing = middleware(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true, true);
        complete(failing, RiskLevel.NORMAL, "今天聊了散步");
    }

    /** 嵌入失败时 MySQL 行仍在，不写向量。 */
    @Test
    void embedFailureKeepsMysqlOnly() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        List<VectorRecord> vectors = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, vectors, false, false);

        complete(middleware, RiskLevel.NORMAL, "今天聊了散步");

        assertThat(memories).containsExactly("t|u|s|今天聊了散步|2");
        assertThat(profiles).containsExactly("t|u|ACQUAINTANCE");
        assertThat(vectors).isEmpty();
    }

    /** 走完中间件的成功路径，触发 remember。 */
    private static void complete(CompanionMemoryMiddleware middleware, RiskLevel level, String userText) {
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .put("agentId", "companion-v1")
                .put(RiskLevel.class, level)
                .build();
        AgentInput input = new AgentInput(List.of(
                Msg.builder().role(MsgRole.USER).textContent(userText).build()));
        middleware.onAgent(mock(Agent.class), context, input, ignored -> Flux.empty()).blockLast();
    }

    /** 假仓库回固定 id 7。嵌入失败时返回空向量。 */
    private static CompanionMemoryMiddleware middleware(
            List<String> memories,
            List<String> profiles,
            List<VectorRecord> vectors,
            boolean failWrites,
            boolean embedOk) {
        EpisodicMemoryRepository memoryStore = (tenant, user, session, summary, salience) -> {
            if (failWrites) {
                throw new IllegalStateException("db");
            }
            memories.add(tenant + "|" + user + "|" + session + "|" + summary + "|" + salience);
            return 7L;
        };
        UserProfileRepository profileStore = (tenant, user, stage, notes) -> {
            if (failWrites) {
                throw new IllegalStateException("db");
            }
            profiles.add(tenant + "|" + user + "|" + stage);
        };
        TextEmbedder embedder = text -> {
            if (!embedOk) {
                return new float[0];
            }
            float[] vector = new float[MilvusCollections.DEFAULT_DIMENSIONS];
            vector[0] = 1f;
            return vector;
        };
        VectorStore store = new RecordingVectorStore(vectors, failWrites);
        return new CompanionMemoryMiddleware(memoryStore, profileStore, new CompanionPersona(), embedder, store);
    }

    /**
     * 测试用向量缝。只记下写入，近邻和删除用不到。
     */
    private static final class RecordingVectorStore implements VectorStore {

        private final List<VectorRecord> vectors;

        private final boolean failWrites;

        /** 只记下写入。failWrites 时 upsert 抛错。 */
        private RecordingVectorStore(List<VectorRecord> vectors, boolean failWrites) {
            this.vectors = vectors;
            this.failWrites = failWrites;
        }

        /** 失败分支用来确认异常被中间件吞掉。 */
        @Override
        public void upsert(VectorRecord record) {
            if (failWrites) {
                throw new IllegalStateException("milvus");
            }
            vectors.add(record);
        }

        /** 这一刀还不检索。 */
        @Override
        public List<VectorHit> search(VectorQuery query) {
            return List.of();
        }

        /** 批准删除仍不执行，测试里用不到。 */
        @Override
        public void deleteById(String collection, String id) {
        }

        /** 按范围删是预留缝，这里不调用。 */
        @Override
        public void deleteByScope(String collection, String tenantId, String userId, String agentId) {
        }
    }
}
