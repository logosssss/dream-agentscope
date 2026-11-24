package com.zhu.docs.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一篇文档的阅读数据。markdown 是索引副本，不是 git 回写。
 * links 在打开这篇时就算好，前端只替换 href，不再自己拼文档 id。
 *
 * @param title     标题
 * @param topic     主题
 * @param markdown  整篇正文，仍是原文
 * @param toc       二级标题目录
 * @param updatedAt 最近一次写入索引的时间
 * @param links     同仓相对 .md 链接的替换表，目标不可见时不出现
 */
public record DocsPage(
        String title,
        String topic,
        String markdown,
        List<DocsTocEntry> toc,
        LocalDateTime updatedAt,
        List<DocsResolvedLink> links) {
}
