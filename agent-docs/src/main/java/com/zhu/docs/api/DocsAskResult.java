package com.zhu.docs.api;

import java.util.List;

/**
 * 单篇问答的结果。引用是从答案文本里解析出来的，不是模型另给的结构。
 *
 * @param answer    模型原文
 * @param citations 按出现顺序、去重后的小节
 */
public record DocsAskResult(String answer, List<DocsCitation> citations) {
}
