package com.zhu.runtime.application;

import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.domain.ModelTextGuard;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;

/**
 * 把框架事件收成接入层能直接写出的 SSE 数据。运行层内部使用，不把框架类型漏出去。
 */
public final class AgentStreamEvents {

    private AgentStreamEvents() {
    }

    /**
     * 只摘出前端需要的类型和短文本。工具参数和完整提示词不放进 text。
     */
    public static AgentStreamEvent from(AgentEvent event) {
        if (event == null || event.getType() == null) {
            return new AgentStreamEvent("unknown", "");
        }
        String type = event.getType().name().toLowerCase();
        return new AgentStreamEvent(type, textOf(event));
    }

    /** 按事件种类取短文本。没对应字段时为空串。 */
    private static String textOf(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            return delta.getDelta() == null ? "" : delta.getDelta();
        }
        if (event instanceof ToolCallStartEvent tool) {
            return tool.getToolCallName() == null ? "" : tool.getToolCallName();
        }
        if (event instanceof RequireUserConfirmEvent confirm) {
            return toolNames(confirm.getToolCalls());
        }
        if (event instanceof AgentResultEvent result) {
            Msg message = result.getResult();
            if (message == null || message.getTextContent() == null) {
                return "";
            }
            return ModelTextGuard.outbound(message.getTextContent());
        }
        return "";
    }

    /** 审批事件只带工具名，不带调用参数。 */
    private static String toolNames(List<ToolUseBlock> calls) {
        if (calls == null || calls.isEmpty()) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (ToolUseBlock call : calls) {
            if (call == null || call.getName() == null || call.getName().isBlank()) {
                continue;
            }
            if (!names.isEmpty()) {
                names.append(',');
            }
            names.append(call.getName());
        }
        return names.toString();
    }
}
