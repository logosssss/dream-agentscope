package com.zhu.runtime.infrastructure.gateway;

import com.zhu.common.util.Ids;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import java.util.function.Supplier;

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
     */
    static AgentResponse run(
            AgentTurnSupport turns, ResidentAgentDrain drain, AgentRequest request, Supplier<Msg> send) {
        turns.require(request);
        drain.beginTurn();
        try {
            String runId = Ids.newId();
            // 本地上下文给 finish/用量；Gateway 侧由 resolver + MsgContext 合并。
            RuntimeContext context = turns.context(request, runId);
            long started = System.nanoTime();
            Msg reply = send.get();
            String text = turns.finish(reply == null ? null : reply.getTextContent(), context);
            turns.recordUsage(request, runId, reply, started);
            return new AgentResponse(runId, text);
        } finally {
            drain.endTurn();
        }
    }
}
