package com.zhu.bootstrap;

import com.zhu.runtime.domain.RecalledMemories;
import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.semantic.config.product.CompanionPersona;
import com.zhu.semantic.contract.safety.RiskLevel;
import com.zhu.state.api.memory.EpisodicMemoryItem;
import com.zhu.state.api.memory.EpisodicMemoryRepository;
import com.zhu.state.api.memory.UserProfileItem;
import com.zhu.state.api.memory.UserProfileRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 普通和升高先召回再写短摘要，并把短画像和召回追加到系统提示词。
 * 危机不召回、不写、不注入。不连接 MySQL、Milvus，也不调用模型。
 */
class CompanionMemoryMiddlewareTest {

    /** 闲聊写下摘要、相识阶段和情景向量。摘要不含换行。 */
    @Test
    void normalTurnWritesShortMemory() {
        Fixture fixture = fixture(false, true, false);

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天去散步了\n顺便买菜");

        assertThat(fixture.appends).containsExactly("t|u|s|今天去散步了 顺便买菜|2");
        assertThat(fixture.profiles).containsExactly("t|u|ACQUAINTANCE");
        assertThat(fixture.vectors).hasSize(1);
        assertThat(fixture.vectors.get(0).id()).isEqualTo("7");
        assertThat(context.get(RecalledMemories.class).items()).isEmpty();
    }

    /** 升高档仍写，重要程度更高。 */
    @Test
    void elevatedTurnUsesHigherSalience() {
        Fixture fixture = fixture(false, true, false);

        complete(fixture.middleware, RiskLevel.ELEVATED, "我有点难受");

        assertThat(fixture.appends).containsExactly("t|u|s|我有点难受|4");
        assertThat(fixture.vectors).hasSize(1);
    }

    /** 危机档不落记忆，也不召回。 */
    @Test
    void criticalTurnSkipsMemory() {
        Fixture fixture = fixture(false, true, false);
        fixture.recent.add(item(1, 2, LocalDateTime.of(2026, 11, 1, 10, 0)));

        RuntimeContext context = complete(fixture.middleware, RiskLevel.CRITICAL, "我想自杀");

        assertThat(fixture.appends).isEmpty();
        assertThat(fixture.profiles).isEmpty();
        assertThat(fixture.vectors).isEmpty();
        assertThat(fixture.searches).isEmpty();
        assertThat(context.get(RecalledMemories.class).items()).isEmpty();
    }

    /** 超长用户输入截成短句。写库或写向量失败不往外抛。 */
    @Test
    void longTextIsClippedAndWriteFailureIsSwallowed() {
        Fixture fixture = fixture(false, true, false);

        complete(fixture.middleware, RiskLevel.NORMAL, "a".repeat(100));

        assertThat(fixture.appends).hasSize(1);
        assertThat(fixture.appends.get(0).split("\\|", -1)[3]).hasSize(80);

        Fixture failing = fixture(true, true, false);
        complete(failing.middleware, RiskLevel.NORMAL, "今天聊了散步");
    }

    /** 嵌入失败时 MySQL 行仍在，不写向量，召回只留最近。 */
    @Test
    void embedFailureKeepsMysqlOnly() {
        Fixture fixture = fixture(false, false, false);
        fixture.recent.add(item(1, 2, LocalDateTime.of(2026, 11, 1, 10, 0)));

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天聊了散步");

        assertThat(fixture.appends).containsExactly("t|u|s|今天聊了散步|2");
        assertThat(fixture.vectors).isEmpty();
        assertThat(context.get(RecalledMemories.class).items()).extracting(EpisodicMemoryItem::id).containsExactly(1L);
    }

    /** 最近和近邻去重后按显著性再时间，最多 5 条。不改用户原文。 */
    @Test
    void recallMergesRecentAndNeighbors() {
        Fixture fixture = fixture(false, true, false);
        LocalDateTime ten = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime nine = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime eight = LocalDateTime.of(2026, 11, 1, 8, 0);
        LocalDateTime seven = LocalDateTime.of(2026, 11, 1, 7, 0);
        LocalDateTime eleven = LocalDateTime.of(2026, 11, 1, 11, 0);
        fixture.recent.addAll(List.of(item(1, 2, ten), item(2, 2, nine), item(3, 4, eight)));
        fixture.neighbors.addAll(List.of(item(3, 4, eight), item(6, 5, seven), item(7, 1, eleven)));
        fixture.hits.add(new VectorHit("3", 0.9f));
        fixture.hits.add(new VectorHit("6", 0.8f));
        fixture.hits.add(new VectorHit("7", 0.7f));

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天去散步了");

        assertThat(context.get(RecalledMemories.class).items())
                .extracting(EpisodicMemoryItem::id)
                .containsExactly(6L, 3L, 1L, 2L, 7L);
        assertThat(fixture.searches).hasSize(1);
        assertThat(fixture.searches.get(0).collection()).isEqualTo(MilvusCollections.EPISODIC_MEMORY);
        assertThat(fixture.searches.get(0).limit()).isEqualTo(3);
    }

    /** Milvus 挂了只用最近 3 条。 */
    @Test
    void recallFallsBackToMysqlWhenVectorFails() {
        Fixture fixture = fixture(false, true, true);
        fixture.recent.add(item(1, 2, LocalDateTime.of(2026, 11, 1, 10, 0)));
        fixture.recent.add(item(2, 3, LocalDateTime.of(2026, 11, 1, 9, 0)));

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天聊了散步");

        assertThat(context.get(RecalledMemories.class).items())
                .extracting(EpisodicMemoryItem::id)
                .containsExactly(2L, 1L);
        assertThat(fixture.appends).isNotEmpty();
    }

    /** 普通档追加短画像和召回摘要。备注和阶段从表里读。 */
    @Test
    void injectsProfileAndRecalledItems() {
        Fixture fixture = fixture(false, true, false);
        fixture.recent.add(item(1, 2, LocalDateTime.of(2026, 11, 1, 10, 0)));
        fixture.storedProfile = new UserProfileItem("t", "u", "FAMILIAR", "喜欢散步");

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天去散步了");

        assertThat(prompt(fixture.middleware, context, "你是搭子。")).isEqualTo("""
                你是搭子。
                关系阶段：熟悉
                备注：喜欢散步
                你还记得这些事：
                - m1""");
    }

    /** 危机档保持当前话术，不追加记忆。 */
    @Test
    void crisisKeepsCurrentPrompt() {
        Fixture fixture = fixture(false, true, false);
        fixture.recent.add(item(1, 2, LocalDateTime.of(2026, 11, 1, 10, 0)));
        fixture.storedProfile = new UserProfileItem("t", "u", "CLOSE", "旧备注");

        RuntimeContext context = complete(fixture.middleware, RiskLevel.CRITICAL, "我想自杀");

        assertThat(prompt(fixture.middleware, context, "危机话术")).isEqualTo("危机话术");
    }

    /** 画像读失败退回锁定阶段。已经注入过的话术不再追加。 */
    @Test
    void injectFailureFallsBackAndSkipsDuplicate() {
        Fixture fixture = fixture(false, true, false);
        fixture.failProfileRead = true;

        RuntimeContext context = complete(fixture.middleware, RiskLevel.NORMAL, "今天聊了散步");

        assertThat(prompt(fixture.middleware, context, "人设")).isEqualTo("""
                人设
                关系阶段：相识""");
        assertThat(prompt(fixture.middleware, context, "人设\n你还记得这些事：\n- 旧的"))
                .isEqualTo("人设\n你还记得这些事：\n- 旧的");
    }

    /** 走完中间件的成功路径，先召回再写。 */
    private static RuntimeContext complete(CompanionMemoryMiddleware middleware, RiskLevel level, String userText) {
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
        return context;
    }

    /** 走完提示词注入。不调用模型。 */
    private static String prompt(CompanionMemoryMiddleware middleware, RuntimeContext context, String current) {
        return middleware.onSystemPrompt(mock(Agent.class), context, current).block();
    }

    /** 假仓库和假向量口。failSearch 只让近邻失败。 */
    private static Fixture fixture(boolean failWrites, boolean embedOk, boolean failSearch) {
        Fixture fixture = new Fixture();
        fixture.middleware = new CompanionMemoryMiddleware(
                fixture.memories(failWrites),
                fixture.profiles(failWrites),
                new CompanionPersona(),
                text -> {
                    if (!embedOk) {
                        return new float[0];
                    }
                    float[] vector = new float[MilvusCollections.DEFAULT_DIMENSIONS];
                    vector[0] = 1f;
                    return vector;
                },
                fixture.store(failWrites, failSearch));
        return fixture;
    }

    /** 构造一条短记忆。摘要只用来区分 id。 */
    private static EpisodicMemoryItem item(long id, int salience, LocalDateTime createdAt) {
        return new EpisodicMemoryItem(id, "t", "u", "s", "m" + id, salience, createdAt);
    }

    /**
     * 一次测试用的写入和召回记录。
     */
    private static final class Fixture {

        private final List<String> appends = new ArrayList<>();

        private final List<String> profiles = new ArrayList<>();

        private final List<VectorRecord> vectors = new ArrayList<>();

        private final List<VectorQuery> searches = new ArrayList<>();

        private final List<EpisodicMemoryItem> recent = new ArrayList<>();

        private final List<EpisodicMemoryItem> neighbors = new ArrayList<>();

        private final List<VectorHit> hits = new ArrayList<>();

        /** 注入时读到的短画像。空表示表上没有行。 */
        private UserProfileItem storedProfile;

        /** 画像读失败用来确认对话仍继续。 */
        private boolean failProfileRead;

        private CompanionMemoryMiddleware middleware;

        /** 写入和最近、按 id 查都走这份假数据。 */
        private EpisodicMemoryRepository memories(boolean failWrites) {
            return new EpisodicMemoryRepository() {
                /** 失败分支用来确认异常被中间件吞掉。 */
                @Override
                public long append(String tenant, String user, String session, String summary, int salience) {
                    if (failWrites) {
                        throw new IllegalStateException("db");
                    }
                    appends.add(tenant + "|" + user + "|" + session + "|" + summary + "|" + salience);
                    return 7L;
                }

                /** 最近几条来自测试预先放好的列表。 */
                @Override
                public List<EpisodicMemoryItem> recent(String tenant, String user, int limit) {
                    return List.copyOf(recent);
                }

                /** 近邻回表也用预先放好的列表，不按 id 再筛。 */
                @Override
                public List<EpisodicMemoryItem> findByIds(String tenant, String user, List<Long> ids) {
                    return List.copyOf(neighbors);
                }
            };
        }

        /** 失败分支用来确认异常被中间件吞掉。 */
        private UserProfileRepository profiles(boolean failWrites) {
            return new UserProfileRepository() {
                /** 写入失败不往外抛。 */
                @Override
                public void upsert(String tenant, String user, String stage, String notes) {
                    if (failWrites) {
                        throw new IllegalStateException("db");
                    }
                    profiles.add(tenant + "|" + user + "|" + stage);
                }

                /** 读失败按空画像处理。 */
                @Override
                public UserProfileItem find(String tenant, String user) {
                    if (failProfileRead) {
                        throw new IllegalStateException("db");
                    }
                    return storedProfile;
                }
            };
        }

        /** 近邻失败时不写搜索结果。 */
        private VectorStore store(boolean failWrites, boolean failSearch) {
            return new VectorStore() {
                /** 失败分支用来确认异常被中间件吞掉。 */
                @Override
                public void upsert(VectorRecord record) {
                    if (failWrites) {
                        throw new IllegalStateException("milvus");
                    }
                    vectors.add(record);
                }

                /** 记下查询条件，命中来自测试预先放好的列表。 */
                @Override
                public List<VectorHit> search(VectorQuery query) {
                    if (failSearch) {
                        throw new IllegalStateException("milvus");
                    }
                    searches.add(query);
                    return List.copyOf(hits);
                }

                /** 批准删除仍不执行。 */
                @Override
                public void deleteById(String collection, String id) {
                }

                /** 按范围删是预留缝。 */
                @Override
                public void deleteByScope(String collection, String tenantId, String userId, String agentId) {
                }
            };
        }
    }
}
