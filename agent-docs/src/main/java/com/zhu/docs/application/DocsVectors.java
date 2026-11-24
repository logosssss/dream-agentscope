package com.zhu.docs.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * docs_chunks 集合上的写入和近邻检索。
 */
public interface DocsVectors {

    /**
     * 这些 id 里 Milvus 已经有的。查询失败时返回空集，调用方会当成缺向量再写一次。
     */
    Set<String> present(Collection<String> ids);

    /**
     * 写入一条。向量维度必须是 1024。
     */
    void upsert(String id, float[] vector, String docId, String topic, String visibility, String source);

    /** 按主键删除。空集合直接返回。 */
    void delete(Collection<String> ids);

    /**
     * 近邻检索。过滤表达式由调用方按身份拼好。
     * 查询失败时返回空列表，搜索会改走关键词。
     */
    List<DocsVectorHit> search(float[] vector, String filter, int topK);
}
