package com.zhu.docs.api;

/**
 * 一条搜索命中。snippet 是块在正文里前后各 60 字，不是整篇。
 *
 * @param docId   文档 id，用来打开详情
 * @param title   文档标题
 * @param heading 小节标题
 * @param anchor  详情页里的标题 id
 * @param snippet 展示用片段
 * @param score   向量相似度。关键词兜底时是 0，因为没有向量分
 */
public record DocsSearchHit(
        String docId,
        String title,
        String heading,
        String anchor,
        String snippet,
        double score) {
}
