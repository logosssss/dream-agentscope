package com.zhu.runtime.infrastructure.tool;

import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.domain.knowledge.ChunkReranker;
import com.zhu.runtime.domain.knowledge.HybridChunkMerger;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Agentic 知识检索。身份只从 RuntimeContext 取，不用 ThreadLocal。
 * Hybrid：向量优先，FULLTEXT ngram 补缺，合并最多 20 条后再 Rerank 到 5。
 * 向量失败时降级纯全文；Rerank 失败退回 Hybrid 原序。只回 SUCCESS 文档上的切块。
 */
@Component
public class KnowledgeSearchTool implements ProductTool {

    private static final Logger log = Logger.getLogger(KnowledgeSearchTool.class.getName());

    public static final String TOOL_ID = "knowledge_search";

    private static final int TOP_K = 5;
    /** 单路召回与 Hybrid 合并上限；Rerank 后再截到 TOP_K。 */
    private static final int CANDIDATE_LIMIT = 20;

    private final VectorStore vectors;

    private final KnowledgeRepository knowledge;

    private final TextEmbedder embedder;

    private final ChunkReranker reranker;

    /** 与索引写入同一集合，须跟 agentscope.milvus.collection / 启动 ensure 一致。 */
    private final String knowledgeCollection;

    /**
     * @param vectors   向量近邻；失败时本工具降级全文
     * @param knowledge 回表 SUCCESS 切块与 FULLTEXT
     * @param embedder  查询嵌入；维度不符时视为向量路空
     * @param reranker  Hybrid 之后重排；失败时原序截断
     */
    public KnowledgeSearchTool(
            VectorStore vectors,
            KnowledgeRepository knowledge,
            TextEmbedder embedder,
            ChunkReranker reranker,
            @Value("${agentscope.milvus.collection:knowledge}") String knowledgeCollection) {
        this.vectors = vectors;
        this.knowledge = knowledge;
        this.embedder = embedder;
        this.reranker = reranker;
        this.knowledgeCollection = MilvusCollections.resolveKnowledge(knowledgeCollection);
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
     * Hybrid 检索后格式化摘录。嵌入或向量失败不抛给模型线程，改走全文。
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
            List<KnowledgeChunkItem> vectorChunks = loadVectorChunks(tenantId, userId, agentId, q);
            List<KnowledgeChunkItem> fulltextChunks =
                    knowledge.searchSuccessByFulltext(tenantId, userId, agentId, q, CANDIDATE_LIMIT);
            List<KnowledgeChunkItem> merged =
                    HybridChunkMerger.merge(vectorChunks, fulltextChunks, CANDIDATE_LIMIT);
            List<KnowledgeChunkItem> ranked = reranker.rerank(q, merged, TOP_K);
            if (ranked.isEmpty()) {
                return "未找到相关知识。";
            }
            return format(ranked);
        } catch (RuntimeException ex) {
            log.warning("knowledge search failed");
            return "未找到相关知识。";
        }
    }

    /**
     * 嵌入 + 近邻 + 回表。任一步失败返回空列表，由全文路兜底。
     */
    private List<KnowledgeChunkItem> loadVectorChunks(
            String tenantId, String userId, String agentId, String query) {
        try {
            float[] vector = embedder.embed(query);
            if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                return List.of();
            }
            List<VectorHit> hits = vectors.search(new VectorQuery(
                    knowledgeCollection, vector, "", userId, agentId, CANDIDATE_LIMIT));
            List<String> ids = new ArrayList<>();
            if (hits != null) {
                for (VectorHit hit : hits) {
                    if (hit != null && hit.id() != null && !hit.id().isBlank()) {
                        ids.add(hit.id());
                    }
                }
            }
            if (ids.isEmpty()) {
                return List.of();
            }
            return knowledge.findSuccessChunksByIds(tenantId, userId, agentId, ids);
        } catch (RuntimeException ex) {
            log.warning("knowledge search vector failed, degrade to fulltext");
            return List.of();
        }
    }

    /** 按来源标题拼摘录；空内容跳过。 */
    private static String format(List<KnowledgeChunkItem> chunks) {
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
    }
}
