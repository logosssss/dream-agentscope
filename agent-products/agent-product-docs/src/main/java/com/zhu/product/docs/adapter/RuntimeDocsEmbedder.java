package com.zhu.product.docs.adapter;

import com.zhu.docs.api.DocsEmbedder;
import com.zhu.runtime.domain.TextEmbedder;
import org.springframework.stereotype.Component;

/**
 * 文档产品把嵌入转给运行层已有的嵌入器。不另写 HTTP 客户端。
 */
@Component
public class RuntimeDocsEmbedder implements DocsEmbedder {

    private final TextEmbedder embedder;

    /** 密钥和模型都在 TextEmbedder 里，这里只转发。 */
    public RuntimeDocsEmbedder(TextEmbedder embedder) {
        this.embedder = embedder;
    }

    /** 空白或失败时原样返回空数组，同步循环自己决定要不要记 PARTIAL。 */
    @Override
    public float[] embed(String text) {
        float[] vector = embedder.embed(text);
        return vector == null ? new float[0] : vector;
    }
}
