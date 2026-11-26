package com.zhu.runtime.infrastructure.gateway;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import com.zhu.runtime.domain.TurnSignals;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;

/**
 * WEB 与 API 同步一轮共用的收口。发送仍由各自入口提供，这里只固定运行号、护栏和排水闸。
 */
final class SyncAgentTurn {

    /** 只提供静态方法，不让两条同步入口各写一份收口。 */
    private SyncAgentTurn() {
    }

    /**
     * 校验通过才计入在途。发送抛错也会放行排水闸。
     * 运行号在发送前生成：审计中间件要靠它把审计行挂到这一轮，等执行完再拿框架消息 id 就晚了。
     * 这一轮的共享信号也在这里建：中间件写档位/人设标记，收口读同一个对象。
     * 入口拿到的同一个号与同一份信号既要进本地上下文，也要进框架上下文。
     */
    static AgentResponse run(
            AgentTurnSupport turns, ResidentAgentDrain drain, AgentRequest request, Send send) {
        turns.require(request);
        drain.beginTurn();
        try {
            String runId = Ids.newId();
            TurnSignals signals = new TurnSignals();
            RuntimeContext context = turns.context(request, runId, signals);
            long started = System.nanoTime();
            Msg reply = send.send(runId, signals);
            String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
            turns.recordUsage(request, runId, reply, started);
            return new AgentResponse(runId, text);
        } finally {
            drain.endTurn();
        }
    }

    /** 用已经定好的运行号与共享信号发送。实现要把两者放进交给框架的上下文。 */
    @FunctionalInterface
    interface Send {

        /** 发送一轮并等回复。 */
        Msg send(String runId, TurnSignals signals);
    }
}
