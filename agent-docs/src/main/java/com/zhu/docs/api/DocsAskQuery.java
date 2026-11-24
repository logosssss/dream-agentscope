package com.zhu.docs.api;

/**
 * 就这一篇提问的请求。只带问题，正文由服务端按文档 id 注入。
 *
 * @param q 用户的问题
 */
public record DocsAskQuery(String q) {
}
