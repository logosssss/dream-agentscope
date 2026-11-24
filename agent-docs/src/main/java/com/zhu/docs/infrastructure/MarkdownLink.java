package com.zhu.docs.infrastructure;

/**
 * 文内的一行内相对链接。只要目标，不在这里换成文档 id。
 * 文档 id 要等整库路径都知道以后才能算，那是阅读页阶段的事。
 *
 * @param text   链接可见文字，可以是空串
 * @param target 原始目标，可能带 {@code #} 片段
 */
public record MarkdownLink(String text, String target) {
}
