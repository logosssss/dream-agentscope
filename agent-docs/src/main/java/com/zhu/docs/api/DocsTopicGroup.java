package com.zhu.docs.api;

import java.util.List;

/**
 * 一个主题下能看的文档。
 *
 * @param topic     主题键
 * @param documents 按标题排序
 */
public record DocsTopicGroup(String topic, List<DocsTreeItem> documents) {
}
