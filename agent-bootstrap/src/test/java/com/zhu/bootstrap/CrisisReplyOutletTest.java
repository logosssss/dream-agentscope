package com.zhu.bootstrap;

import com.zhu.runtime.api.AgentStreamEvent;
import com.zhu.runtime.application.CrisisReplyOutlet;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 危机流式出口扣增量。普通陪伴不改映射。
 */
class CrisisReplyOutletTest {

    /** 非危机文本增量原样带出。 */
    @Test
    void nonCriticalKeepsDelta() {
        List<AgentStreamEvent> events = CrisisReplyOutlet.emit(
                new TextBlockDeltaEvent("reply-1", "block-1", "开个玩笑"),
                false,
                "危机话术");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("text_block_delta");
        assertThat(events.get(0).text()).isEqualTo("开个玩笑");
    }

    /** 危机丢掉文本增量，避免半句先漏出去。 */
    @Test
    void criticalDropsDelta() {
        assertThat(CrisisReplyOutlet.emit(
                new TextBlockDeltaEvent("reply-1", "block-1", "开个玩笑"),
                true,
                "危机话术")).isEmpty();
    }

    /** 危机仍放行待确认，只扣模型正文增量。 */
    @Test
    void criticalKeepsConfirm() {
        ToolUseBlock shell = new ToolUseBlock.Builder().id("tool-1").name("shell").input(Map.of("cmd", "rm")).build();
        List<AgentStreamEvent> events = CrisisReplyOutlet.emit(
                new RequireUserConfirmEvent("reply-1", List.of(shell)),
                true,
                "危机话术");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("require_user_confirm");
        assertThat(events.get(0).text()).isEqualTo("shell");
    }

    /** 危机结束事件带检查后的全文。命中红线换成预置稿。 */
    @Test
    void criticalResultIsFiltered() {
        Msg joke = Msg.builder().role(MsgRole.ASSISTANT).textContent("开个玩笑").build();
        List<AgentStreamEvent> blocked = CrisisReplyOutlet.emit(new AgentResultEvent(joke), true, "危机话术");
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).text()).isEqualTo("危机话术");

        Msg safe = Msg.builder().role(MsgRole.ASSISTANT).textContent("先找身边的人").build();
        List<AgentStreamEvent> kept = CrisisReplyOutlet.emit(new AgentResultEvent(safe), true, "危机话术");
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).text()).isEqualTo("先找身边的人");
    }

    /** hold=true 时扣增量，结束事件再换全文（危机出口仍走这条）。 */
    @Test
    void personaHoldFiltersResult() {
        assertThat(CrisisReplyOutlet.holdThenFinish(
                new TextBlockDeltaEvent("reply-1", "block-1", "我是你的恋人"),
                true,
                text -> "基线")).isEmpty();

        Msg lover = Msg.builder().role(MsgRole.ASSISTANT).textContent("我是你的恋人").build();
        List<AgentStreamEvent> events = CrisisReplyOutlet.holdThenFinish(
                new AgentResultEvent(lover),
                true,
                text -> "基线");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).text()).isEqualTo("基线");
    }
}
