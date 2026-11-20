package com.zhu.runtime.task.handle;

import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.domain.TextEmbedder;
import com.zhu.runtime.domain.knowledge.TextChunker;
import com.zhu.state.api.knowledge.KnowledgeChunkItem;
import com.zhu.state.api.knowledge.KnowledgeDocumentItem;
import com.zhu.state.api.knowledge.KnowledgeRepository;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.api.VectorStore;
import com.zhu.vector.infrastructure.MilvusCollections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DOCUMENT_INDEX 任务的实际索引逻辑。切块、嵌入、双写；失败改文档 FAILED。
 */
@Component
public class DocumentIndexExecutor {

    private static final Logger log = Logger.getLogger(DocumentIndexExecutor.class.getName());

    public static final String TYPE = "DOCUMENT_INDEX";

    private final KnowledgeRepository knowledge;

    private final TextEmbedder embedder;

    private final VectorStore vectors;

    /** 与启动 ensure 同一套集合名，禁止写死 knowledge。 */
    private final String knowledgeCollection;

    public DocumentIndexExecutor(
            KnowledgeRepository knowledge,
            TextEmbedder embedder,
            VectorStore vectors,
            @Value("${agentscope.milvus.collection:knowledge}") String knowledgeCollection) {
        this.knowledge = knowledge;
        this.embedder = embedder;
        this.vectors = vectors;
        this.knowledgeCollection = MilvusCollections.resolveKnowledge(knowledgeCollection);
    }

    /**
     * payload 必须带 docId 与 version。身份以任务上的 tenant/user/agent 为准。
     */
    public void index(AgentTask task) {
        String docId = text(task.payload(), "docId");
        int version = number(task.payload(), "version");
        if (docId.isEmpty() || version < 1) {
            throw new IllegalArgumentException("docId and version are required");
        }
        String tenantId = task.tenantId() == null ? "" : task.tenantId();
        String userId = task.userId();
        KnowledgeDocumentItem doc = knowledge.findDocument(docId, version, tenantId, userId);
        if (doc == null) {
            throw new IllegalStateException("document not found");
        }
        knowledge.updateDocumentStatus(docId, version, tenantId, userId, KnowledgeRepository.PROCESSING, null);
        try {
            clearPrevious(doc);
            List<String> parts = TextChunker.chunk(
                    doc.content(), TextChunker.DEFAULT_SIZE, TextChunker.DEFAULT_OVERLAP);
            if (parts.isEmpty()) {
                throw new IllegalStateException("no chunks");
            }
            int index = 0;
            for (String part : parts) {
                float[] vector = embedder.embed(part);
                if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
                    throw new IllegalStateException("embedding failed");
                }
                String chunkId = chunkId(doc.docId(), doc.version(), index);
                knowledge.insertChunk(
                        new KnowledgeChunkItem(
                                chunkId, doc.kbId(), doc.docId(), doc.version(), doc.title(), part, index),
                        tenantId,
                        userId,
                        doc.agentId());
                vectors.upsert(new VectorRecord(
                        knowledgeCollection,
                        chunkId,
                        vector,
                        "",
                        userId,
                        doc.agentId()));
                index++;
            }
            knowledge.updateDocumentStatus(docId, version, tenantId, userId, KnowledgeRepository.SUCCESS, null);
        } catch (RuntimeException ex) {
            // 只记类名加短消息，方便联调对上 Milvus/嵌入失败原因；不带正文与密钥。
            String detail = ex.getClass().getSimpleName();
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                String clipped = message.length() > 200 ? message.substring(0, 200) : message;
                detail = detail + ": " + clipped;
            }
            log.warning("document index failed: " + detail);
            knowledge.updateDocumentStatus(
                    docId, version, tenantId, userId, KnowledgeRepository.FAILED, detail);
            throw ex;
        }
    }

    /** 重试前清掉本版本旧块和向量。 */
    private void clearPrevious(KnowledgeDocumentItem doc) {
        // 先查不到旧 id 列表时，只删 MySQL；向量按约定 id 规则在重写时覆盖 upsert。
        knowledge.deleteChunks(doc.docId(), doc.version());
    }

    static String chunkId(String docId, int version, int index) {
        return docId + "_v" + version + "_" + index;
    }

    private static String text(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static int number(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return 0;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
