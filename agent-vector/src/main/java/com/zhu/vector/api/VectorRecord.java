package com.zhu.vector.api;

/**
 * 一条待写入的向量。id 由调用方给出，不让 Milvus 自动编号。
 *
 * @param collection 集合名
 * @param id         主键。情景记忆用 MySQL 行 id 的字符串
 * @param vector     与集合维度一致
 * @param tenantId   情景记忆必填。知识集合忽略
 * @param userId     用户，必填
 * @param agentId    Agent，必填
 */
public record VectorRecord(
        String collection,
        String id,
        float[] vector,
        String tenantId,
        String userId,
        String agentId) {
}
