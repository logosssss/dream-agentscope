package com.zhu.docs.api;

/**
 * 阅读页上的一条同仓链接。target 是原文括号里的地址，href 已经是站内文档地址。
 *
 * @param target 与 Markdown 里写的一致，前端靠它对上 href
 * @param href   {@code /docs/doc/{id}}，片段有锚点时再带 {@code #}
 */
public record DocsResolvedLink(String target, String href) {
}
