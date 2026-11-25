package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ChatUi 路由只在标识不同于已启动的 main 时钉死。不启动 Gateway。
 */
class ChatUiRouteTest {

    /** 空标识和 main 自己都不改路由。别的标识才钉上。 */
    @Test
    void pinsOnlyWhenDifferentFromMain() {
        assertThat(ChatUiRoute.options("u", "s", "t", null, "companion-v1").agentId()).isNull();
        assertThat(ChatUiRoute.options("u", "s", "t", "  ", "companion-v1").agentId()).isNull();
        assertThat(ChatUiRoute.options("u", "s", "t", "companion-v1", "companion-v1").agentId()).isNull();
        assertThat(ChatUiRoute.options("u", "s", "t", "echo", "companion-v1").agentId()).isEqualTo("echo");
    }

    /** main 还没发布时，非空标识会被钉成显式路由。调用方必须先懒启动。 */
    @Test
    void unsetMainPinsAnyAgentId() {
        assertThat(ChatUiRoute.options("u", "s", "t", "companion-v1", null).agentId()).isEqualTo("companion-v1");
    }
}
