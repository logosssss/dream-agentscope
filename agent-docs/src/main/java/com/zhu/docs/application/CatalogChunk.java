package com.zhu.docs.application;

/**
 * 库里的一个小节。embeddedHash 为空表示 Milvus 里还没有对应向量。
 *
 * @param id           稳定 id
 * @param heading      标题
 * @param anchor       锚点
 * @param ordinal      从 0 开始
 * @param textHash     块文本哈希
 * @param text         带标题的块文本
 * @param embeddedHash 已成功写入向量时等于 textHash
 */
public record CatalogChunk(
        String id,
        String heading,
        String anchor,
        int ordinal,
        String textHash,
        String text,
        String embeddedHash) {
}
