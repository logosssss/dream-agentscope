package com.zhu.docs.infrastructure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhu.docs.application.DocsVectorHit;
import com.zhu.docs.application.DocsVectors;
import com.zhu.vector.infrastructure.MilvusCollections;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 把文档块写到 docs_chunks。查询失败当成「向量不在」，同步会再写一次。
 */
@Service
@ConditionalOnBean(MilvusClientV2.class)
public class MilvusDocsVectors implements DocsVectors {

    private static final Logger LOG = Logger.getLogger(MilvusDocsVectors.class.getName());

    private final MilvusClientV2 client;

    /** 复用进程里的客户端，不在每次同步时建连。 */
    public MilvusDocsVectors(MilvusClientV2 client) {
        this.client = client;
    }

    /**
     * 按主键看哪些已经在库里。空列表不打 Milvus。查询异常时返回空集。
     */
    @Override
    public Set<String> present(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        try {
            QueryResp response = client.query(QueryReq.builder()
                    .collectionName(DocsCollections.NAME)
                    .ids(List.copyOf(ids))
                    .outputFields(List.of(DocsCollections.ID))
                    .limit(ids.size())
                    .build());
            Set<String> found = new HashSet<>();
            if (response.getQueryResults() == null) {
                return Set.of();
            }
            for (QueryResp.QueryResult result : response.getQueryResults()) {
                if (result.getEntity() == null) {
                    continue;
                }
                Object id = result.getEntity().get(DocsCollections.ID);
                if (id != null) {
                    found.add(String.valueOf(id));
                }
            }
            return Set.copyOf(found);
        } catch (RuntimeException ex) {
            LOG.warning("docs vector lookup failed");
            return Set.of();
        }
    }

    /**
     * 维度不对直接拒绝，避免把残缺向量写进集合。
     */
    @Override
    public void upsert(String id, float[] vector, String docId, String topic, String visibility, String source) {
        if (id == null || id.isBlank() || vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS) {
            throw new IllegalArgumentException("docs vector is invalid");
        }
        JsonObject row = new JsonObject();
        row.addProperty(DocsCollections.ID, id);
        row.add(DocsCollections.VECTOR, toArray(vector));
        row.addProperty(DocsCollections.DOC_ID, docId);
        row.addProperty(DocsCollections.TOPIC, topic);
        row.addProperty(DocsCollections.VISIBILITY, visibility);
        row.addProperty(DocsCollections.SOURCE, source);
        client.upsert(UpsertReq.builder()
                .collectionName(DocsCollections.NAME)
                .data(List.of(row))
                .build());
    }

    /** 没有 id 时不调用 Milvus。 */
    @Override
    public void delete(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        client.delete(DeleteReq.builder()
                .collectionName(DocsCollections.NAME)
                .ids(List.copyOf(ids))
                .build());
    }

    /**
     * 在 docs_chunks 里取近邻。维度不对或查询抛错时返回空，不把知识集合扯进来。
     */
    @Override
    public List<DocsVectorHit> search(float[] vector, String filter, int topK) {
        if (vector == null || vector.length != MilvusCollections.DEFAULT_DIMENSIONS || topK < 1) {
            return List.of();
        }
        try {
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(DocsCollections.NAME)
                    .data(List.of(new FloatVec(vector)))
                    .annsField(DocsCollections.VECTOR)
                    .filter(filter == null ? "" : filter)
                    .topK(topK)
                    .outputFields(List.of(DocsCollections.ID))
                    .build());
            return hits(response);
        } catch (RuntimeException ex) {
            LOG.warning("docs vector search failed");
            return List.of();
        }
    }

    private static List<DocsVectorHit> hits(SearchResp response) {
        if (response == null || response.getSearchResults() == null) {
            return List.of();
        }
        List<DocsVectorHit> found = new ArrayList<>();
        for (List<SearchResp.SearchResult> batch : response.getSearchResults()) {
            if (batch == null) {
                continue;
            }
            for (SearchResp.SearchResult result : batch) {
                if (result == null || result.getId() == null) {
                    continue;
                }
                double score = result.getScore() == null ? 0d : result.getScore();
                found.add(new DocsVectorHit(String.valueOf(result.getId()), score));
            }
        }
        return List.copyOf(found);
    }

    private static JsonArray toArray(float[] vector) {
        JsonArray values = new JsonArray();
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
