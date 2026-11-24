package com.zhu.docs.infrastructure;

import java.util.List;
import java.util.Map;

/**
 * 一篇 Markdown 解析后的只读结果。给切块用，不代表已经入库。
 *
 * @param title       第一个一级标题；没有时用 front matter 的 {@code title}；再没有就是空串
 * @param frontMatter 文首平铺的 front matter，没有就是空 map
 * @param preamble    第一个二级标题之前的正文，不含 front matter
 * @param sections    按出现顺序的二级节
 * @param links       代码块之外的行内相对链接
 */
public record MarkdownDocument(
        String title,
        Map<String, String> frontMatter,
        String preamble,
        List<MarkdownSection> sections,
        List<MarkdownLink> links) {
}
