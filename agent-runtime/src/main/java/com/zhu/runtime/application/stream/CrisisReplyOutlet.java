package com.zhu.runtime.application.stream;

import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.domain.CrisisReplyGuard;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 需要攒齐再查的流式出口。当前由危机档使用。
 * 普通人设检查在执行服务里放行增量，终态再覆盖。不写干预。
 */
public final class CrisisReplyOutlet {

    private CrisisReplyOutlet() {
    }

    /**
     * 危机时丢掉文本增量。结束事件带出检查后的全文。
     * 其它事件仍按原映射，避免工具名也被吞掉。
     */
    public static List<AgentStreamEvent> emit(AgentEvent event, boolean critical, String crisisText) {
        return holdThenFinish(event, critical, text -> CrisisReplyGuard.finalize(text, true, crisisText));
    }

    /**
     * 需要攒齐时扣下文本增量，结束事件再套检查。
     * 不需要时原样映射。其它事件仍放行。
     */
    public static List<AgentStreamEvent> holdThenFinish(
            AgentEvent event,
            boolean hold,
            UnaryOperator<String> finish) {
        if (!hold) {
            return List.of(AgentStreamEvents.from(event));
        }
        if (event instanceof TextBlockDeltaEvent) {
            return List.of();
        }
        if (event instanceof AgentResultEvent result) {
            Msg message = result.getResult();
            String raw = message == null ? null : message.getTextContent();
            String type = event.getType() == null ? "unknown" : event.getType().name().toLowerCase();
            UnaryOperator<String> check = finish == null ? text -> text == null ? "" : text : finish;
            return List.of(new AgentStreamEvent(type, check.apply(raw)));
        }
        return List.of(AgentStreamEvents.from(event));
    }
}
