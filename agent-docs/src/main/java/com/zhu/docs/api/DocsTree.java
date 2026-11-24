package com.zhu.docs.api;

import java.util.List;

/**
 * 按主题分组的文档树。顺序固定为七个主题，不跟着库里的出现顺序走。
 *
 * @param topics 每个主题一份列表，可以是空的
 */
public record DocsTree(List<DocsTopicGroup> topics) {
}
