package com.zhu.docs.api;

/**
 * 文档只读查询。看不见的文档和没有的文档一样，都是不存在。
 */
public interface DocsQuery {

    /**
     * 七个主题下当前身份能看的文档。没有文档的主题也要出现。
     *
     * @param userId 登录用户，匿名是 null
     */
    DocsTree tree(String userId);

    /**
     * 一篇的正文和目录。私有文档对白名单外的人是不存在。
     *
     * @param userId 登录用户，匿名是 null
     * @param id     稳定文档 id
     */
    DocsPage page(String userId, String id);
}
