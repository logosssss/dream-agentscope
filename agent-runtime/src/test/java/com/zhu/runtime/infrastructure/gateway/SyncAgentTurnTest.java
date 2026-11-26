package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 同步收口的顺序。不启动 Gateway，也不连模型。
 */
class SyncAgentTurnTest {

    /** 校验失败不计在途，也不发送。 */
    @Test
    void requireFailureDoesNotBeginTurn() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "missing")).when(turns).require(request);
        AtomicBoolean sent = new AtomicBoolean();

        assertThatThrownBy(() -> SyncAgentTurn.run(turns, drain, request, runId -> {
            sent.set(true);
            return null;
        })).isInstanceOf(BusinessException.class);

        assertThat(sent).isFalse();
        assertThat(drain.inFlight()).isZero();
    }

    /** 发送失败仍放行排水闸，并且不记用量。 */
    @Test
    void sendFailureEndsTurn() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        when(turns.context(eq(request), any())).thenReturn(context);

        assertThatThrownBy(() -> SyncAgentTurn.run(turns, drain, request, runId -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(drain.inFlight()).isZero();
        verify(turns, never()).recordUsage(any(), any(), nullable(Msg.class), anyLong());
    }

    /** 空回复仍用同一个运行号收口。 */
    @Test
    void nullReplyUsesTheSameRunId() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        when(turns.context(eq(request), any())).thenReturn(context);
        when(turns.finish(isNull(), eq(context))).thenReturn("");

        AgentResponse response = SyncAgentTurn.run(turns, drain, request, runId -> null);

        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(turns).recordUsage(eq(request), runId.capture(), isNull(), anyLong());
        assertThat(response.runId()).isEqualTo(runId.getValue());
        assertThat(response.text()).isEmpty();
        assertThat(drain.inFlight()).isZero();
    }

    /** 发送拿到的就是同一个运行号：中间件靠它挂审计行，两处不一致就白记。 */
    @Test
    void sendReceivesTheSameRunId() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        when(turns.context(eq(request), any())).thenReturn(context);
        when(turns.finish(isNull(), eq(context))).thenReturn("");
        AtomicReference<String> sent = new AtomicReference<>();

        AgentResponse response = SyncAgentTurn.run(turns, drain, request, runId -> {
            sent.set(runId);
            return null;
        });

        assertThat(sent.get()).isEqualTo(response.runId());
        assertThat(drain.inFlight()).isZero();
    }

    /** 回复正文先过护栏，再按同一运行号记账。 */
    @Test
    void finishThenRecordUsage() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        Msg reply = mock(Msg.class);
        when(reply.getTextContent()).thenReturn("raw");
        when(turns.context(eq(request), any())).thenReturn(context);
        when(turns.finish("raw", context)).thenReturn("safe");

        AgentResponse response = SyncAgentTurn.run(turns, drain, request, runId -> reply);

        assertThat(response.text()).isEqualTo("safe");
        verify(turns).recordUsage(eq(request), eq(response.runId()), eq(reply), anyLong());
        assertThat(drain.inFlight()).isZero();
    }

    private static AgentRequest request() {
        return new AgentRequest("companion-v1", "t", "u", "s", "", "hi");
    }
}
