package com.zhu.docs.infrastructure;

import java.util.ArrayList;
import java.util.List;

/**
 * 把解析结果切成嵌入用的小节。一个二级标题一份，标题写进正文。
 * 一级标题之前的导语不单独成块，它仍留在整篇 Markdown 里。
 */
public final class Chunker {

    private final MarkdownParser parser = new MarkdownParser();

    /**
     * 从原文切块。锚点在这一篇内编号，空文档得到空列表。
     *
     * @param markdown 原始 Markdown
     */
    public List<DocChunk> chunk(String markdown) {
        return chunk(parser.parse(markdown));
    }

    /**
     * 从已解析的文档切块。调用方已经有解析结果时不必再扫一遍。
     * 正文为空时块里只留标题行。
     *
     * @param document 解析结果，null 得到空列表
     */
    public List<DocChunk> chunk(MarkdownDocument document) {
        if (document == null || document.sections().isEmpty()) {
            return List.of();
        }
        AnchorSlug anchors = new AnchorSlug();
        List<DocChunk> chunks = new ArrayList<>();
        int ordinal = 0;
        for (MarkdownSection section : document.sections()) {
            String text = section.body().isBlank()
                    ? "## " + section.heading()
                    : "## " + section.heading() + "\n\n" + section.body();
            chunks.add(new DocChunk(section.heading(), anchors.next(section.heading()), ordinal, text));
            ordinal++;
        }
        return List.copyOf(chunks);
    }
}
