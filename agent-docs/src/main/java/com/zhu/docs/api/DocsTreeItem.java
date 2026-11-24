package com.zhu.docs.api;

/**
 * 树上的一篇，不含正文。
 *
 * @param id         稳定 id
 * @param title      标题
 * @param docType    路径种类，只做角标
 * @param visibility {@code public} 或 {@code private}
 * @param source     与仓库键相同
 */
public record DocsTreeItem(String id, String title, String docType, String visibility, String source) {
}
