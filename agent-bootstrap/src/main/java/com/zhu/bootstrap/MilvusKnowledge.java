package com.zhu.bootstrap;

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
 * 用户进程启动时准备知识集合。管理进程不调用。不写入向量，也不做检索。
 */
public final class MilvusKnowledge {

    /** 没配集合名时用这个。和指南里的 knowledge 对齐。 */
    public static final String DEFAULT_COLLECTION = "knowledge";

    /** text-embedding-v4 的维度。配置小于 1 时用这个。 */
    public static final int DEFAULT_DIMENSIONS = 1024;

    /** 主键。由调用方生成，不让 Milvus 自动编号。 */
    public static final String ID = "id";

    private MilvusKnowledge() {
    }

    /**
     * 集合不存在就按给定维度创建。已经存在但维度或身份字段不对时拒绝启动。
     * 换嵌入模型后要先删掉旧集合再启动，这里不会自动删数据。
     * 令牌为空就不传。不把地址和令牌写进异常。
     */
    public static void ensure(String uri, String token, String collection, int dimensions) {
        String name = collection == null || collection.isBlank() ? DEFAULT_COLLECTION : collection;
        int size = dimensions < 1 ? DEFAULT_DIMENSIONS : dimensions;
        ConnectConfig.ConnectConfigBuilder connect = ConnectConfig.builder().uri(uri);
        if (token != null && !token.isBlank()) {
            connect.token(token);
        }
        MilvusClientV2 client = new MilvusClientV2(connect.build());
        try {
            boolean exists = Boolean.TRUE.equals(client.hasCollection(
                    HasCollectionReq.builder().collectionName(name).build()));
            if (exists) {
                checkExisting(client, name, size);
                return;
            }
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
                    .dimension(size)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(KnowledgeFilter.USER)
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(KnowledgeFilter.AGENT)
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .build());
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
        } finally {
            client.close();
        }
    }

    /**
     * 旧集合必须仍是同一维度，并且带用户和 Agent 字段。对不上就退出，避免检索串数据。
     */
    private static void checkExisting(MilvusClientV2 client, String name, int dimensions) {
        DescribeCollectionResp described = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(name).build());
        List<String> fields = described.getFieldNames();
        if (fields == null
                || !fields.contains(KnowledgeFilter.USER)
                || !fields.contains(KnowledgeFilter.AGENT)
                || !fields.contains(KnowledgeFilter.VECTOR)) {
            throw new IllegalStateException("milvus collection is missing user or agent");
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
