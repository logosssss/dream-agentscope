package com.zhu.runtime.application;

import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.domain.CrisisReplyGuard;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import java.util.List;

/**
 * 危机档流式出口。增量先扣下，等整段结束再过红线。
 * 普通和升高不走这里的扣增量。不写干预。
 */
public final class CrisisReplyOutlet {

    private CrisisReplyOutlet() {
    }

    /**
     * 危机时丢掉文本增量。结束事件带出检查后的全文。
     * 其它事件仍按原映射，避免工具名也被吞掉。
     */
    public static List<AgentStreamEvent> emit(AgentEvent event, boolean critical, String crisisText) {
        if (!critical) {
            return List.of(AgentStreamEvents.from(event));
        }
        if (event instanceof TextBlockDeltaEvent) {
            return List.of();
        }
        if (event instanceof AgentResultEvent result) {
            Msg message = result.getResult();
            String raw = message == null ? null : message.getTextContent();
            String type = event.getType() == null ? "unknown" : event.getType().name().toLowerCase();
            return List.of(new AgentStreamEvent(type, CrisisReplyGuard.finalize(raw, true, crisisText)));
        }
        return List.of(AgentStreamEvents.from(event));
    }
}
