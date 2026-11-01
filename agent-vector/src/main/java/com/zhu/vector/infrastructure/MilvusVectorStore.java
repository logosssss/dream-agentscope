package com.zhu.vector.infrastructure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhu.vector.api.KnowledgeFilter;
import com.zhu.vector.api.MemoryFilter;
import com.zhu.vector.api.VectorHit;
import com.zhu.vector.api.VectorQuery;
import com.zhu.vector.api.VectorRecord;
import com.zhu.vector.api.VectorStore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 把向量写到 Milvus，并按身份过滤近邻。
 * 复用进程里的客户端，不在每次调用时建连。
 * 测试关掉启动依赖时不要装配这个类。
 */
@Service
@ConditionalOnProperty(name = "agentscope.startup.dependencies.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusVectorStore implements VectorStore {

    private final MilvusClientV2 client;

    /** 客户端由容器创建，停机时关闭。 */
    public MilvusVectorStore(MilvusClientV2 client) {
        this.client = client;
    }

    /**
     * 主键已存在就覆盖。知识集合不写租户。情景记忆必须带租户。
     */
    @Override
    public void upsert(VectorRecord record) {
        String collection = requireCollection(record == null ? null : record.collection());
        if (record.id() == null || record.id().isBlank()
                || record.vector() == null || record.vector().length == 0) {
            throw new IllegalArgumentException("vector record is invalid");
        }
        filterOf(collection, record.tenantId(), record.userId(), record.agentId());
        JsonObject row = new JsonObject();
        row.addProperty(MilvusCollections.ID, record.id());
        row.add(KnowledgeFilter.VECTOR, toArray(record.vector()));
        row.addProperty(KnowledgeFilter.USER, record.userId());
        row.addProperty(KnowledgeFilter.AGENT, record.agentId());
        if (isMemory(collection)) {
            row.addProperty(MemoryFilter.TENANT, record.tenantId());
        }
        client.upsert(UpsertReq.builder()
                .collectionName(collection)
                .data(List.of(row))
                .build());
    }

    /**
     * 没有过滤条件就不检索。limit 小于 1 时按 1。
     */
    @Override
    public List<VectorHit> search(VectorQuery query) {
        if (query == null || query.vector() == null || query.vector().length == 0) {
            throw new IllegalArgumentException("vector query is invalid");
        }
        String collection = requireCollection(query.collection());
        String filter = filterOf(collection, query.tenantId(), query.userId(), query.agentId());
        int limit = query.limit() < 1 ? 1 : query.limit();
        SearchResp response = client.search(SearchReq.builder()
                .collectionName(collection)
                .data(List.of(new FloatVec(query.vector())))
                .filter(filter)
                .topK(limit)
                .outputFields(List.of(MilvusCollections.ID))
                .build());
        return hits(response);
    }

    /** 主键为空时不调用 Milvus。 */
    @Override
    public void deleteById(String collection, String id) {
        String name = requireCollection(collection);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        client.delete(DeleteReq.builder()
                .collectionName(name)
                .ids(List.of(id))
                .build());
    }

    /** 身份不完整时不调用 Milvus。 */
    @Override
    public void deleteByScope(String collection, String tenantId, String userId, String agentId) {
        String name = requireCollection(collection);
        String filter = filterOf(name, tenantId, userId, agentId);
        client.delete(DeleteReq.builder()
                .collectionName(name)
                .filter(filter)
                .build());
    }

    static String filterOf(String collection, String tenantId, String userId, String agentId) {
        if (isMemory(collection)) {
            return MemoryFilter.of(tenantId, userId, agentId);
        }
        return KnowledgeFilter.of(userId, agentId);
    }

    private static boolean isMemory(String collection) {
        return MilvusCollections.EPISODIC_MEMORY.equals(collection);
    }

    private static String requireCollection(String collection) {
        if (collection == null || collection.isBlank()) {
            throw new IllegalArgumentException("collection is required");
        }
        return collection;
    }

    private static JsonArray toArray(float[] vector) {
        JsonArray values = new JsonArray();
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private static List<VectorHit> hits(SearchResp response) {
        if (response == null || response.getSearchResults() == null) {
            return List.of();
        }
        List<VectorHit> hits = new ArrayList<>();
        for (List<SearchResp.SearchResult> batch : response.getSearchResults()) {
            if (batch == null) {
                continue;
            }
            for (SearchResp.SearchResult result : batch) {
                if (result == null || result.getId() == null) {
                    continue;
                }
                float score = result.getScore() == null ? 0f : result.getScore();
                hits.add(new VectorHit(String.valueOf(result.getId()), score));
            }
        }
        return List.copyOf(hits);
    }
}
