package com.zhu.docs.infrastructure;

/**
 * 一个可嵌入的小节。正文带上标题，避免向量里只剩没有语境的段落。
 *
 * @param heading 小节标题
 * @param anchor  篇内唯一锚点
 * @param ordinal 从 0 开始的顺序
 * @param text    {@code ## 标题} 加正文
 */
public record DocChunk(String heading, String anchor, int ordinal, String text) {
}
