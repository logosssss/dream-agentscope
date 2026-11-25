package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import org.junit.jupiter.api.Test;

/**
 * 通道取出。不启动 Gateway，不连 Redis。
 */
class BoundChannelTest {

    /** 类型吻合时原样返回。缺失和类型不对沿用原来的文案。 */
    @Test
    void readKeepsMissingAndMismatchMessages() {
        ApiChannel api = ApiChannel.create();
        ChatUiChannel chat = ChatUiChannel.create();
        assertThat(BoundChannel.read(api, ApiChannel.class, "api")).isSameAs(api);
        assertThat(BoundChannel.read(chat, ChatUiChannel.class, "chatui")).isSameAs(chat);
        assertThatThrownBy(() -> BoundChannel.read(null, ApiChannel.class, "api"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("api channel missing");
        assertThatThrownBy(() -> BoundChannel.read(api, ChatUiChannel.class, "chatui"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("chatui channel type mismatch: " + api.getClass().getName());
    }

    /** 管理器为空时两条入口共用同一句失败文案。 */
    @Test
    void missingManagerFails() {
        assertThatThrownBy(() -> BoundChannel.present(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("channel manager is missing");
    }
}
