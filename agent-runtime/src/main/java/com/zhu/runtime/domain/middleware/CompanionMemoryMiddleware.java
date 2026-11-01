package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.domain.CallIdentity;
import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.semantic.contract.PersonaContract;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileRepository;
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
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 对话成功结束后写情景记忆和画像。危机不写。
 * 摘要只留用户输入的短句，不存对话全文，也不调用对话模型。
 * MySQL 有行 id 后再嵌入并写入情景记忆集合。画像不进向量。
 * 写失败只打日志，不改变已经生成的回复。
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
     * 等这次调用正常结束后再写。失败或取消不落库。
     * 档位由安全门事先放进上下文。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input).doOnComplete(() -> remember(context, input));
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
