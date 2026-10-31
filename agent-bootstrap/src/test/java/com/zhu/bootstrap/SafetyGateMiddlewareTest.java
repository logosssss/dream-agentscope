package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import com.zhu.semantic.contract.CrisisPrompt;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 关键词命中危机时换危机话术。普通输入仍用当前人设。不连模型。
 */
class SafetyGateMiddlewareTest {

    /** 危机词改系统提示词。闲聊保持原话术。 */
    @Test
    void criticalReplacesSystemPrompt() {
        SafetyGateMiddleware middleware = new SafetyGateMiddleware(() -> "危机话术");

        assertThat(promptOf(middleware, "我想自杀", "搭子人设")).isEqualTo("危机话术");
        assertThat(promptOf(middleware, "今天天气不错", "搭子人设")).isEqualTo("搭子人设");
    }

    /** 危机正文读失败时用代码兜底，不能退回搭子人设。 */
    @Test
    void crisisReadFailureUsesFallback() {
        SafetyGateMiddleware middleware = new SafetyGateMiddleware(() -> {
            throw new IllegalStateException("missing");
        });

        assertThat(promptOf(middleware, "我想自杀", "搭子人设")).isEqualTo(CrisisPrompt.FALLBACK);
    }

    /** 先判定再换话术。和框架里 onAgent 包住 onSystemPrompt 的顺序一致。 */
    private static String promptOf(SafetyGateMiddleware middleware, String userText, String current) {
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        AgentInput input = new AgentInput(List.of(
                Msg.builder().role(MsgRole.USER).textContent(userText).build()));
        middleware.onAgent(mock(Agent.class), context, input, ignored -> Flux.empty()).blockLast();
        return middleware.onSystemPrompt(mock(Agent.class), context, current).block();
    }
}
