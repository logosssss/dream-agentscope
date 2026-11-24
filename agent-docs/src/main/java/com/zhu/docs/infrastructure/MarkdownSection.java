package com.zhu.docs.infrastructure;

/**
 * 一个二级标题及其正文。三级及更深的标题留在正文里，不单独成节。
 *
 * @param heading 不含 {@code ##} 的标题
 * @param body    标题以下、下一节之前的正文，已去掉首尾空白
 */
public record MarkdownSection(String heading, String body) {
}
