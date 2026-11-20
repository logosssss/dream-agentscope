package com.zhu.state.api.knowledge;

/**
 * 一条切块。id 与 Milvus 主键一致。
 *
 * @param id         切块主键
 * @param kbId       知识库
 * @param docId      文档
 * @param version    文档版本
 * @param titleHint  来源标题，检索展示用；可空
 * @param content    正文
 * @param chunkIndex 块序号
 */
public record KnowledgeChunkItem(
        String id,
        String kbId,
        String docId,
        int version,
        String titleHint,
        String content,
        int chunkIndex) {
}
