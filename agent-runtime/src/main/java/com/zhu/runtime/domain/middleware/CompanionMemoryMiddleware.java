package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.domain.CallIdentity;
import com.zhu.runtime.domain.RecalledMemories;
import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.semantic.contract.PersonaContract;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryItem;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileRepository;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 对话开始时召回情景记忆，成功结束后再写短摘要和画像。危机不召回也不写。
 * 摘要只留用户输入的短句，不存对话全文，也不调用对话模型。
 * 检索是 MySQL 最近 3 条加近邻 3 个 id，去重后最多 5 条。Milvus 挂了只用 MySQL。
 * 结果只放进上下文，不拼进提示词。写失败只打日志，不改变已经生成的回复。
 */
@Component
public class CompanionMemoryMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(CompanionMemoryMiddleware.class.getName());

    /** 短摘要上限。表允许 500，这里先压成一句。 */
    static final int SUMMARY_LIMIT = 80;

    private final EpisodicMemoryRepository memories;

    private final UserProfileRepository profiles;

    private final PersonaContract persona;

    private final TextEmbedder embedder;

    private final VectorStore vectors;

    /** 向量口和嵌入口都由容器注入。测试关掉启动依赖时向量口是空实现。 */
    public CompanionMemoryMiddleware(
            EpisodicMemoryRepository memories,
            UserProfileRepository profiles,
            PersonaContract persona,
            TextEmbedder embedder,
            VectorStore vectors) {
        this.memories = memories;
        this.profiles = profiles;
        this.persona = persona;
        this.embedder = embedder;
        this.vectors = vectors;
    }

    /**
     * 先召回再往下走。结束后再写。失败或取消不落库。
     * 档位由安全门事先放进上下文。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        recall(context, input);
        return next.apply(input).doOnComplete(() -> remember(context, input));
    }

    /**
     * 危机跳过。MySQL 和近邻分开取，一处失败不影响另一处。
     * 不改系统提示词，也不改用户原文。
     */
    void recall(RuntimeContext context, AgentInput input) {
        if (context == null) {
            return;
        }
        if (critical(context)) {
            context.put(RecalledMemories.class, RecalledMemories.empty());
            return;
        }
        CallIdentity identity = CallIdentity.from(context);
        List<EpisodicMemoryItem> recent = recentOf(identity);
        List<EpisodicMemoryItem> neighbors = neighborsOf(identity, agentId(context), input);
        context.put(RecalledMemories.class, RecalledMemories.merge(recent, neighbors));
    }

    /** 读库失败当成没有最近记忆，对话继续。 */
    private List<EpisodicMemoryItem> recentOf(CallIdentity identity) {
        try {
            return memories.recent(identity.tenantId(), identity.userId(), RecalledMemories.RECENT);
        } catch (RuntimeException ex) {
            log.warning("episodic memory recent failed");
            return List.of();
        }
    }

    /**
     * 嵌入或近邻失败只用 MySQL。向量 id 对不上数字时丢掉。
     */
    private List<EpisodicMemoryItem> neighborsOf(CallIdentity identity, String agentId, AgentInput input) {
        try {
            String query = clip(userText(input));
            if (query.isEmpty() || identity.userId() == null || identity.userId().isBlank()
                    || agentId == null || agentId.isBlank()) {
                return List.of();
            }
            float[] vector = embedder.embed(query);
            if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                return List.of();
            }
            String tenant = identity.tenantId() == null || identity.tenantId().isBlank() ? "" : identity.tenantId();
            List<Long> ids = idsOf(vectors.search(new VectorQuery(
                    MilvusCollections.EPISODIC_MEMORY,
                    vector,
                    tenant,
                    identity.userId(),
                    agentId,
                    RecalledMemories.NEIGHBORS)));
            if (ids.isEmpty()) {
                return List.of();
            }
            return memories.findByIds(identity.tenantId(), identity.userId(), ids);
        } catch (RuntimeException ex) {
            log.warning("episodic memory neighbor failed");
            return List.of();
        }
    }

    /** 只收下能对上 MySQL 行 id 的主键。 */
    private static List<Long> idsOf(List<VectorHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (VectorHit hit : hits) {
            if (hit == null || hit.id() == null || hit.id().isBlank()) {
                continue;
            }
            try {
                long id = Long.parseLong(hit.id());
                if (id > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 对不上账本的主键丢掉，不中断这次召回。
            }
        }
        return ids;
    }

    /**
     * 危机跳过。摘要为空也跳过。
     * MySQL、画像和向量分开写，一处失败不影响另外两处，也不挡回复。
     */
    void remember(RuntimeContext context, AgentInput input) {
        if (critical(context)) {
            return;
        }
        String summary = clip(userText(input));
        if (summary.isEmpty()) {
            return;
        }
        CallIdentity identity = CallIdentity.from(context);
        appendMemory(identity, agentId(context), summary, salience(context));
        upsertProfile(identity);
    }

    /** 普通陪伴 2，升高 4。危机不会走到这里。 */
    static int salience(RuntimeContext context) {
        if (context != null && context.get(RiskLevel.class) == RiskLevel.ELEVATED) {
            return 4;
        }
        return 2;
    }

    /** 换行压成空格，超出上限就截断。不把整段对话留下来。 */
    static String clip(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (compact.length() <= SUMMARY_LIMIT) {
            return compact;
        }
        return compact.substring(0, SUMMARY_LIMIT);
    }

    /**
     * 先落 MySQL。没有行 id 就不嵌入，避免向量主键对不上账本。
     */
    private void appendMemory(CallIdentity identity, String agentId, String summary, int salience) {
        long id;
        try {
            id = memories.append(identity.tenantId(), identity.userId(), identity.sessionId(), summary, salience);
        } catch (RuntimeException ex) {
            log.warning("episodic memory record failed");
            return;
        }
        if (id <= 0) {
            return;
        }
        writeVector(identity, agentId, id, summary);
    }

    /**
     * 只写刚落库的短摘要。没密钥、维度不对或 Milvus 失败都丢掉这次向量。
     */
    private void writeVector(CallIdentity identity, String agentId, long id, String summary) {
        try {
            float[] vector = embedder.embed(summary);
            if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                return;
            }
            if (identity.userId() == null || identity.userId().isBlank() || agentId == null || agentId.isBlank()) {
                return;
            }
            String tenant = identity.tenantId() == null || identity.tenantId().isBlank() ? "" : identity.tenantId();
            vectors.upsert(new VectorRecord(
                    MilvusCollections.EPISODIC_MEMORY,
                    Long.toString(id),
                    vector,
                    tenant,
                    identity.userId(),
                    agentId));
        } catch (RuntimeException ex) {
            log.warning("episodic memory vector failed");
        }
    }

    /** 上下文没带标识时用锁定人格，避免向量缺 Agent。 */
    private static String agentId(RuntimeContext context) {
        if (context == null) {
            return "companion-v1";
        }
        Object value = context.get("agentId");
        if (value == null || value.toString().isBlank()) {
            return "companion-v1";
        }
        return value.toString();
    }

    /**
     * 阶段仍用锁定人格里的约定。不读画像表，也不从对话里升级关系。
     */
    private void upsertProfile(CallIdentity identity) {
        try {
            profiles.upsert(identity.tenantId(), identity.userId(), persona.relationshipStage(), null);
        } catch (RuntimeException ex) {
            log.warning("user profile record failed");
        }
    }

    /** 危机档由安全门写入。这里只读，不再判定。 */
    private static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }

    /** 从后往前找用户消息。助手回复不进摘要。 */
    private static String userText(AgentInput input) {
        if (input == null || input.msgs() == null) {
            return "";
        }
        List<Msg> messages = input.msgs();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg message = messages.get(i);
            if (message == null || message.getRole() != MsgRole.USER) {
                continue;
            }
            String text = message.getTextContent();
            return text == null ? "" : text;
        }
        return "";
    }
}
