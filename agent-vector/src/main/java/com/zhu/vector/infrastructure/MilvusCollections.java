package com.zhu.vector.infrastructure;

import com.zhu.vector.api.KnowledgeFilter;
import com.zhu.vector.api.MemoryFilter;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import java.util.List;

/**
 * 用户进程启动时准备检索集合。管理进程不调用。
 * 这里不写入向量，也不做检索。
 */
public final class MilvusCollections {

    /** 没配知识集合名时用这个。和指南里的 knowledge 对齐。 */
    public static final String DEFAULT_KNOWLEDGE = "knowledge";

    /** 情景记忆集合。id 对应 MySQL 行 id。 */
    public static final String EPISODIC_MEMORY = "episodic_memory";

    /** 主键。由调用方生成，不让 Milvus 自动编号。 */
    public static final String ID = "id";

    /** text-embedding-v4 的维度。配置小于 1 时用这个。 */
    public static final int DEFAULT_DIMENSIONS = 1024;

    private MilvusCollections() {
    }

    /**
     * 知识集合和情景记忆集合都不存在就按给定维度创建。
     * 已经存在但字段或维度不对时拒绝启动。
     * 换嵌入模型后要先删掉旧集合再启动，这里不会自动删数据。
     * 不关闭传入的客户端。
     */
    public static void ensure(MilvusClientV2 client, String knowledgeCollection, int dimensions) {
        String knowledge = knowledgeCollection == null || knowledgeCollection.isBlank()
                ? DEFAULT_KNOWLEDGE : knowledgeCollection;
        int size = dimensions < 1 ? DEFAULT_DIMENSIONS : dimensions;
        ensureKnowledge(client, knowledge, size);
        ensureEpisodicMemory(client, size);
    }

    /**
     * 令牌为空就不传。不把地址和令牌写进异常。
     */
    public static MilvusClientV2 connect(String uri, String token) {
        ConnectConfig.ConnectConfigBuilder connect = ConnectConfig.builder().uri(uri);
        if (token != null && !token.isBlank()) {
            connect.token(token);
        }
        return new MilvusClientV2(connect.build());
    }

    private static void ensureKnowledge(MilvusClientV2 client, String name, int dimensions) {
        if (exists(client, name)) {
            checkExisting(client, name, dimensions, List.of(KnowledgeFilter.USER, KnowledgeFilter.AGENT));
            return;
        }
        create(client, name, dimensions, false);
    }

    private static void ensureEpisodicMemory(MilvusClientV2 client, int dimensions) {
        if (exists(client, EPISODIC_MEMORY)) {
            checkExisting(client, EPISODIC_MEMORY, dimensions,
                    List.of(MemoryFilter.TENANT, KnowledgeFilter.USER, KnowledgeFilter.AGENT));
            return;
        }
        create(client, EPISODIC_MEMORY, dimensions, true);
    }

    private static boolean exists(MilvusClientV2 client, String name) {
        return Boolean.TRUE.equals(client.hasCollection(
                HasCollectionReq.builder().collectionName(name).build()));
    }

    private static void create(MilvusClientV2 client, String name, int dimensions, boolean withTenant) {
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(ID)
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(KnowledgeFilter.VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(dimensions)
                .build());
        if (withTenant) {
            schema.addField(varchar(MemoryFilter.TENANT));
        }
        schema.addField(varchar(KnowledgeFilter.USER));
        schema.addField(varchar(KnowledgeFilter.AGENT));
        IndexParam index = IndexParam.builder()
                .fieldName(KnowledgeFilter.VECTOR)
                .metricType(IndexParam.MetricType.COSINE)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .build();
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(List.of(index))
                .build());
    }

    private static AddFieldReq varchar(String name) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build();
    }

    /**
     * 旧集合必须仍是同一维度，并且带齐身份字段。对不上就退出，避免检索串数据。
     */
    private static void checkExisting(
            MilvusClientV2 client, String name, int dimensions, List<String> required) {
        DescribeCollectionResp described = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(name).build());
        List<String> fields = described.getFieldNames();
        if (fields == null || !fields.contains(KnowledgeFilter.VECTOR) || !fields.containsAll(required)) {
            throw new IllegalStateException("milvus collection is missing required fields");
        }
        CreateCollectionReq.CollectionSchema schema = described.getCollectionSchema();
        if (schema == null || schema.getField(KnowledgeFilter.VECTOR) == null) {
            throw new IllegalStateException("milvus collection dimension does not match");
        }
        Integer actual = schema.getField(KnowledgeFilter.VECTOR).getDimension();
        if (actual == null || actual != dimensions) {
            throw new IllegalStateException("milvus collection dimension does not match");
        }
    }
}
