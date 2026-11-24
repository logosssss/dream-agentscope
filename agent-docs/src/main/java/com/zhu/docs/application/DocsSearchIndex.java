package com.zhu.docs.application;

import java.util.Collection;
import java.util.List;

/**
 * 搜索要读的块。和同步用的目录分开，避免搜索把写入端口拖进来。
 */
public interface DocsSearchIndex {

    /**
     * 按块 id 取出当前身份能看的块。看不见的 id 直接丢掉。
     * 空集合不查库。
     */
    List<DocsSearchChunk> chunksByIds(Collection<String> ids, boolean owner);

    /**
     * 标题或块正文包含问句的块。pattern 已由调用方转义并加上两侧百分号。
     * topic、source 为空串表示不限制。
     */
    List<DocsSearchChunk> likeChunks(String pattern, String topic, String source, boolean owner, int limit);
}
