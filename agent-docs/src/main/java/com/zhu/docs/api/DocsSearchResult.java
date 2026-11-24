package com.zhu.docs.api;

import java.util.List;

/**
 * 一次文档搜索的命中列表。没有命中时是空列表，不是错误。
 *
 * @param hits 按相关度或关键词顺序，最多 8 条
 */
public record DocsSearchResult(List<DocsSearchHit> hits) {
}
