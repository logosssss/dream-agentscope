package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.runtime.domain.TurnSignals;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import org.junit.jupiter.api.Test;

/**
 * ChatUi 路由只在标识不同于已启动的 main 时钉死。不启动 Gateway。
 */
class ChatUiRouteTest {

    /** 空标识和 main 自己都不改路由。别的标识才钉上。 */
    @Test
    void pinsOnlyWhenDifferentFromMain() {
        assertThat(options(null).agentId()).isNull();
        assertThat(options("  ").agentId()).isNull();
        assertThat(ChatUiRoute.options("u", "s", "t", "companion-v1", "companion-v1", null, null).agentId())
                .isNull();
        assertThat(ChatUiRoute.options("u", "s", "t", "echo", "companion-v1", null, null).agentId())
                .isEqualTo("echo");
    }

    /** main 还没发布时，非空标识会被钉成显式路由。调用方必须先懒启动。 */
    @Test
    void unsetMainPinsAnyAgentId() {
        assertThat(ChatUiRoute.options("u", "s", "t", "companion-v1", null, null, null).agentId())
                .isEqualTo("companion-v1");
    }

    /**
     * 运行号要进 caller 暂存。审计中间件只读框架那份上下文；
     * 只写本地上下文时（P14 之后的一轮重构就是这样）agent_event_audit.run_id 恒空。
     */
    @Test
    void carriesRunIdIntoCallerStaging() {
        SendOptions options = ChatUiRoute.options("u", "s", "t", null, "companion-v1", "run-1", null);
        assertThat(options.runtimeContext().get("runId", String.class)).isEqualTo("run-1");
    }

    /** 没有运行的入口（Gate 续跑）传 null，不能凭空编一个号。 */
    @Test
    void blankRunIdIsNotStaged() {
        SendOptions options = ChatUiRoute.options("u", "s", "t", null, "companion-v1", "  ", null);
        assertThat(options.runtimeContext().get("runId", String.class)).isNull();
    }

    /**
     * 共享信号要按同一个实例带进去：中间件往里写档位与人设标记，收口读同一个对象。
     * 传的不是同一个实例时，危机输出红线与人设检查依然读不到。
     */
    @Test
    void carriesTheSameTurnSignalsInstance() {
        TurnSignals signals = new TurnSignals();
        SendOptions options = ChatUiRoute.options("u", "s", "t", null, "companion-v1", "run-1", signals);
        assertThat(options.runtimeContext().get(TurnSignals.class)).isSameAs(signals);
    }

    /** main 已发布的场景下发一轮，只关心路由。 */
    private static SendOptions options(String agentId) {
        return ChatUiRoute.options("u", "s", "t", agentId, "companion-v1", null, null);
    }
}
