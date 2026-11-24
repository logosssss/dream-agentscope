package com.zhu.docs.api;

/**
 * 目录里的一节。
 *
 * @param heading 标题原文
 * @param anchor  篇内锚点
 */
public record DocsTocEntry(String heading, String anchor) {
}
