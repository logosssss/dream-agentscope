package com.zhu.runtime.infrastructure.tool;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.semantic.contract.ProductTool;
import com.zhu.state.api.KnowledgeChunkItem;
import com.zhu.state.api.KnowledgeRepository;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/**
 * Agentic 知识检索。身份只从 RuntimeContext 取，不用 ThreadLocal。
 * 只回 SUCCESS 文档上的切块。
 */
@Component
public class KnowledgeSearchTool implements ProductTool {

    private static final Logger log = Logger.getLogger(KnowledgeSearchTool.class.getName());

    public static final String TOOL_ID = "knowledge_search";

    private static final int TOP_K = 5;

    private final VectorStore vectors;

    private final KnowledgeRepository knowledge;

    private final TextEmbedder embedder;

    public KnowledgeSearchTool(VectorStore vectors, KnowledgeRepository knowledge, TextEmbedder embedder) {
        this.vectors = vectors;
        this.knowledge = knowledge;
        this.embedder = embedder;
    }

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    @Override
    public Object target() {
        return this;
    }

    /**
     * 近邻后回表。嵌入或检索失败返回友好空结果，不抛给模型线程。
     * RuntimeContext 由框架注入，不要加 @ToolParam。
     */
    @Tool(
            name = TOOL_ID,
            description = "当需要查找当前用户专有知识、文档或规范时使用。输入是检索问题。")
    public String search(
            @ToolParam(name = "query", description = "检索问题") String query, RuntimeContext ctx) {
        try {
            String userId = ctx == null ? null : ctx.getUserId();
            Object agentRaw = ctx == null ? null : ctx.get("agentId");
            String agentId = agentRaw == null ? null : agentRaw.toString();
            Object tenantRaw = ctx == null ? null : ctx.get("tenantId");
            String tenantId = tenantRaw == null ? "" : tenantRaw.toString();
            if (userId == null || userId.isBlank() || agentId == null || agentId.isBlank()) {
                return "未找到相关知识。";
            }
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                return "未找到相关知识。";
            }
            float[] vector = embedder.embed(q);
            if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                return "未找到相关知识。";
            }
            List<VectorHit> hits = vectors.search(new VectorQuery(
                    MilvusCollections.DEFAULT_KNOWLEDGE, vector, "", userId, agentId, TOP_K));
            List<String> ids = new ArrayList<>();
            if (hits != null) {
                for (VectorHit hit : hits) {
                    if (hit != null && hit.id() != null && !hit.id().isBlank()) {
                        ids.add(hit.id());
                    }
                }
            }
            List<KnowledgeChunkItem> chunks =
                    knowledge.findSuccessChunksByIds(tenantId, userId, agentId, ids);
            if (chunks.isEmpty()) {
                return "未找到相关知识。";
            }
            StringBuilder out = new StringBuilder();
            for (KnowledgeChunkItem chunk : chunks) {
                if (chunk == null || chunk.content() == null || chunk.content().isBlank()) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append("\n\n---\n\n");
                }
                String title = chunk.titleHint() == null || chunk.titleHint().isBlank()
                        ? chunk.docId()
                        : chunk.titleHint();
                out.append("【来源：").append(title).append("】\n").append(chunk.content());
            }
            return out.isEmpty() ? "未找到相关知识。" : out.toString();
        } catch (RuntimeException ex) {
            log.warning("knowledge search failed");
            return "未找到相关知识。";
        }
    }
}
