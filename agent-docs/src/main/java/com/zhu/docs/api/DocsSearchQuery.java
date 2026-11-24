package com.zhu.docs.api;

/**
 * 搜索请求。topic 和 source 可以空，表示不收窄。
 *
 * @param q      问句或关键词
 * @param topic  七个主题之一，空则全主题
 * @param source 仓库键，空则两个仓库都搜
 * @param limit  返回条数，空或超出时按 8
 */
public record DocsSearchQuery(String q, String topic, String source, Integer limit) {
}
