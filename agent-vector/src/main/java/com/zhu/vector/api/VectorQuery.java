package com.zhu.vector.api;

/**
 * 一次近邻查询。必须带上身份，避免串到别人的向量。
 *
 * @param collection 集合名
 * @param vector     查询向量，维度与集合一致
 * @param tenantId   情景记忆必填。知识集合忽略
 * @param userId     用户，必填
 * @param agentId    Agent，必填
 * @param limit      返回条数。小于 1 时按 1
 */
public record VectorQuery(
        String collection,
        float[] vector,
        String tenantId,
        String userId,
        String agentId,
        int limit) {
}
