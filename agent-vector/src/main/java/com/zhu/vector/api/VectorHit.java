package com.zhu.vector.api;

/**
 * 一条近邻结果。只带回主键和分数，正文仍在 MySQL。
 *
 * @param id    主键
 * @param score 相似度。COSINE 下越大越近
 */
public record VectorHit(String id, float score) {
}
