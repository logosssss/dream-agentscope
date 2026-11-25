package com.zhu.runtime.infrastructure.gateway;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import io.agentscope.core.agent.RuntimeContext;
import java.time.Duration;
import reactor.core.publisher.Flux;

/**
 * WEB 与 API 流式一轮共用的排水。发送和看图降级仍由各自入口提供。
 */
final class StreamAgentTurn {

    /** 只提供静态方法，不让两条流式入口各写一份排水。 */
    private StreamAgentTurn() {
    }

    /**
     * 校验通过才计入在途。准备阶段抛错立刻放行；订上之后无论完成、失败还是取消，都在 finally 放行。
     * 超时包在调用方返回的整段流外面，看图降级重试不会另计一次在途。
     * 运行号在订阅前生成：审计中间件要靠它把审计行挂到这一轮。
     */
    static Flux<AgentStreamEvent> run(
            AgentTurnSupport turns,
            ResidentAgentDrain drain,
            AgentRequest request,
            Duration timeout,
            Open open) {
        turns.require(request);
        drain.beginTurn();
        try {
            String runId = Ids.newId();
            RuntimeContext context = turns.context(request, runId);
            long started = System.nanoTime();
            return open.open(runId, context, started)
                    .timeout(timeout)
                    .doFinally(signal -> drain.endTurn());
        } catch (RuntimeException error) {
            drain.endTurn();
            throw error;
        }
    }

    /** 用已经定好的运行号打开发送流。抛错算准备失败，不另计在途。 */
    @FunctionalInterface
    interface Open {
        Flux<AgentStreamEvent> open(String runId, RuntimeContext context, long startedNanos);
    }
}
