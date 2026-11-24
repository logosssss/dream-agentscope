package com.zhu.docs.api;

/**
 * 嵌入端口。实现转调运行层已有的 {@code TextEmbedder}，这里不发 HTTP。
 */
public interface DocsEmbedder {

    /**
     * 把一块文本变成向量。失败时返回空数组，不抛给同步循环。
     *
     * @param text 小节正文，已带标题
     */
    float[] embed(String text);
}
