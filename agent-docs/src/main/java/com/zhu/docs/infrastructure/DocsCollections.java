package com.zhu.docs.infrastructure;

import com.zhu.vector.infrastructure.MilvusCollections;
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
 * 单独准备 docs_chunks。不调用 {@link MilvusCollections#ensure(MilvusClientV2, String, int)}，
 * 那个方法只认 knowledge 的身份字段。
 */
public final class DocsCollections {

    /** 文档块集合。和 knowledge 分开，避免绑上 user_id。 */
    public static final String NAME = "docs_chunks";

    /** 主键。调用方用稳定哈希，不让 Milvus 自动编号。 */
    public static final String ID = "id";

    /** 向量字段名。维度与知识集合相同，集合不是同一个。 */
    public static final String VECTOR = "vector";

    public static final String DOC_ID = "doc_id";

    public static final String TOPIC = "topic";

    public static final String VISIBILITY = "visibility";

    public static final String SOURCE = "source";

    private DocsCollections() {
    }

    /**
     * 集合不存在就创建。字段或维度对不上就拒绝启动，不改已有数据。
     * 不关闭传入的客户端。
     */
    public static void ensure(MilvusClientV2 client) {
        if (client == null) {
            throw new IllegalStateException("milvus client is required for docs_chunks");
        }
        if (exists(client)) {
            checkExisting(client);
            return;
        }
        create(client);
    }

    private static boolean exists(MilvusClientV2 client) {
        return Boolean.TRUE.equals(client.hasCollection(
                HasCollectionReq.builder().collectionName(NAME).build()));
    }

    private static void create(MilvusClientV2 client) {
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName(ID)
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(VECTOR)
                .dataType(DataType.FloatVector)
                .dimension(MilvusCollections.DEFAULT_DIMENSIONS)
                .build());
        schema.addField(varchar(DOC_ID, 64));
        schema.addField(varchar(TOPIC, 32));
        schema.addField(varchar(VISIBILITY, 16));
        schema.addField(varchar(SOURCE, 64));
        IndexParam index = IndexParam.builder()
                .fieldName(VECTOR)
                .metricType(IndexParam.MetricType.COSINE)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .build();
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(NAME)
                .collectionSchema(schema)
                .indexParams(List.of(index))
                .build());
    }

    private static AddFieldReq varchar(String name, int length) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.VarChar)
                .maxLength(length)
                .build();
    }

    /**
     * 旧集合必须仍是 1024 维，并且带齐过滤字段。对不上就退出，避免和 knowledge 串数据。
     */
    private static void checkExisting(MilvusClientV2 client) {
        DescribeCollectionResp described = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(NAME).build());
        List<String> fields = described.getFieldNames() == null ? List.of() : described.getFieldNames();
        List<String> required = List.of(VECTOR, DOC_ID, TOPIC, VISIBILITY, SOURCE);
        if (!fields.containsAll(required)) {
            throw new IllegalStateException(
                    "milvus collection '" + NAME + "' is missing required fields; actual=" + fields
                            + ", required=" + required + "; drop the collection and restart");
        }
        CreateCollectionReq.CollectionSchema schema = described.getCollectionSchema();
        if (schema == null || schema.getField(VECTOR) == null) {
            throw new IllegalStateException(
                    "milvus collection '" + NAME + "' has no vector field; drop it and restart");
        }
        Integer actual = schema.getField(VECTOR).getDimension();
        if (actual == null || actual != MilvusCollections.DEFAULT_DIMENSIONS) {
            throw new IllegalStateException(
                    "milvus collection '" + NAME + "' dimension does not match; actual=" + actual
                            + ", expected=" + MilvusCollections.DEFAULT_DIMENSIONS
                            + "; drop the collection and restart");
        }
    }
}
