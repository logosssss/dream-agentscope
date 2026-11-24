package com.zhu.docs.api;

/**
 * 就一篇已索引文档提问。未登录、看不见的文档都不进模型。
 */
public interface DocsAsk {

    /**
     * 读这篇、截断、提问、解析引用。
     *
     * @param userId 登录用户，空则拒绝
     * @param docId  稳定文档 id
     * @param query  问题，空白则拒绝
     */
    DocsAskResult ask(String userId, String docId, DocsAskQuery query);
}
