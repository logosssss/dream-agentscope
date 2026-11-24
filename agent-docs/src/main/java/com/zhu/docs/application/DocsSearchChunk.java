package com.zhu.docs.application;

/**
 * 搜索时从 MySQL 取出的一块。正文只为了切出前后 60 字。
 *
 * @param chunkId  块 id，和 Milvus 主键相同
 * @param docId    文档 id
 * @param title    文档标题
 * @param heading  小节标题
 * @param anchor   锚点
 * @param text     块文本，含标题
 * @param markdown 整篇索引副本，可以是空
 */
public record DocsSearchChunk(
        String chunkId,
        String docId,
        String title,
        String heading,
        String anchor,
        String text,
        String markdown) {
}
