package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.application.AgentStreamEvents;
import com.zhu.runtime.application.AgentTurnSupport;
import com.zhu.runtime.application.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import org.springframework.beans.factory.ObjectProvider;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 流式事件只保留类型和短文本。缺身份时护栏先拒，不进 Gateway。
 */
class AgentStreamTest {

    /** 文本增量原样带出，类型用小写事件名。 */
    @Test
    void mapsTextDelta() {
        AgentStreamEvent event = AgentStreamEvents.from(new TextBlockDeltaEvent("reply-1", "block-1", "你好"));

        assertThat(event.type()).isEqualTo("text_block_delta");
        assertThat(event.text()).isEqualTo("你好");
    }

    /** 待确认事件只带工具名，不带调用参数。 */
    @Test
    void mapsConfirmToolNameOnly() {
        ToolUseBlock shell = new ToolUseBlock.Builder().id("tool-1").name("shell").input(Map.of("cmd", "rm")).build();
        AgentStreamEvent event = AgentStreamEvents.from(new RequireUserConfirmEvent("reply-1", List.of(shell)));

        assertThat(event.type()).isEqualTo("require_user_confirm");
        assertThat(event.text()).isEqualTo("shell");
    }

    /** 校验失败发生在进 Channel 之前，避免空请求去连模型。 */
    @Test
    void requireRejectsBlankIdentity() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        AgentTurnSupport turns = new AgentTurnSupport(
                properties,
                new ResolvedChatModel(properties, mock(ObjectProvider.class)),
                (tenant, user, session, run, model, input, output, latency) -> {
                },
                null,
                null);

        assertThatThrownBy(() -> turns.require(new AgentRequest(null, null, " ", "session-a", null, "hi")))
                .isInstanceOf(BusinessException.class);
    }
}
