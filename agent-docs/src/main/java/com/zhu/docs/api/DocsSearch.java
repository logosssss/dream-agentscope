package com.zhu.docs.api;

/**
 * 文档搜索。向量没有命中时改走关键词，调用方看到的仍是同一种结果。
 */
public interface DocsSearch {

    /**
     * 按当前身份搜索。私有文档对白名单外不出现。
     * 问句为空时返回空列表，不打嵌入，也不打库。
     *
     * @param userId 登录用户，匿名是 null
     * @param query  可以是 null
     */
    DocsSearchResult search(String userId, DocsSearchQuery query);
}
