package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.embed.DashScopeTextEmbedder;
import com.zhu.vector.infrastructure.MilvusCollections;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 嵌入解析和密钥门槛。不连接 DashScope。
 */
class DashScopeTextEmbedderTest {

    /** 没密钥时不发起调用，对话仍可继续。 */
    @Test
    void missingKeySkipsNetwork() {
        DashScopeTextEmbedder embedder = new DashScopeTextEmbedder("");
        assertThat(embedder.embed("今天聊了散步")).isEmpty();
    }

    /** 只接受 1024 维。少一个或解析失败都丢掉。 */
    @Test
    void parseRequiresLockedDimension() {
        assertThat(DashScopeTextEmbedder.parse(body(1024, 0.5f))).hasSize(MilvusCollections.DEFAULT_DIMENSIONS);
        assertThat(DashScopeTextEmbedder.parse(body(1024, 0.5f))[0]).isEqualTo(0.5f);
        assertThat(DashScopeTextEmbedder.parse(body(3, 0.1f))).isEmpty();
        assertThat(DashScopeTextEmbedder.parse("{\"data\":[]}")).isEmpty();
        assertThat(DashScopeTextEmbedder.payload("说\"你好\"")).contains("text-embedding-v4");
        assertThat(DashScopeTextEmbedder.payload("说\"你好\"")).contains("说\\\"你好\\\"");
    }

    /** 拼一段兼容接口的响应。只要 embedding 数组。 */
    private static String body(int size, float first) {
        StringBuilder json = new StringBuilder("{\"data\":[{\"embedding\":[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(i == 0 ? first : 0);
        }
        json.append("]}]}");
        return json.toString();
    }
}
