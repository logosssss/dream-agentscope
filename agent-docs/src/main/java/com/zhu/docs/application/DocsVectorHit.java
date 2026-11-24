package com.zhu.docs.application;

/**
 * Milvus 返回的一条近邻。id 是块主键。
 *
 * @param id    块 id
 * @param score 余弦相似度，越大越近
 */
public record DocsVectorHit(String id, double score) {
}
