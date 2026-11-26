package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import com.zhu.runtime.application.turn.AgentTurnSupport;
import com.zhu.runtime.domain.TurnSignals;
import io.agentscope.core.agent.RuntimeContext;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 流式排水只计一次。不启动 Gateway，也不连模型。
 */
class StreamAgentTurnTest {

    /** 校验失败不计在途，也不打开发送。 */
    @Test
    void requireFailureDoesNotBeginTurn() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AgentRequest request = request();
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST, "missing")).when(turns).require(request);
        AtomicBoolean opened = new AtomicBoolean();

        assertThatThrownBy(() -> StreamAgentTurn.run(
                        turns, drain, request, Duration.ofSeconds(1), (runId, signals, context, started) -> {
                            opened.set(true);
                            return Flux.empty();
                        })).isInstanceOf(BusinessException.class);

        assertThat(opened).isFalse();
        assertThat(drain.inFlight()).isZero();
    }

    /** 准备阶段抛错立刻放行。订阅后的完成、失败和取消也放行。 */
    @Test
    void everyExitReleasesTheDrain() {
        ResidentAgentDrain prepared = new ResidentAgentDrain();
        assertThatThrownBy(() -> StreamAgentTurn.run(readyTurns(), prepared, request(), Duration.ofSeconds(1),
                (runId, signals, context, started) -> {
                    throw new IllegalStateException("prepare");
                })).isInstanceOf(IllegalStateException.class);
        assertThat(prepared.inFlight()).isZero();

        ResidentAgentDrain completed = new ResidentAgentDrain();
        run(completed, Flux.just(new AgentStreamEvent("text", "hi"))).blockLast();
        assertThat(completed.inFlight()).isZero();

        ResidentAgentDrain failed = new ResidentAgentDrain();
        assertThatThrownBy(() -> run(failed, Flux.error(new IllegalStateException("down"))).blockLast())
                .isInstanceOf(IllegalStateException.class);
        assertThat(failed.inFlight()).isZero();

        ResidentAgentDrain cancelled = new ResidentAgentDrain();
        run(cancelled, Flux.never()).subscribe().dispose();
        assertThat(cancelled.inFlight()).isZero();
    }

    /** 超时包在整段流外面，到点也放行。 */
    @Test
    void timeoutReleasesTheDrain() {
        ResidentAgentDrain drain = new ResidentAgentDrain();
        assertThatThrownBy(() -> StreamAgentTurn.run(
                        readyTurns(), drain, request(), Duration.ofMillis(30),
                        (runId, signals, context, started) -> Flux.never())
                .blockLast())
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(drain.inFlight()).isZero();
    }

    /**
     * 打开发送时手里要有这一轮的共享信号：安全与人设中间件把档位写回它，
     * 收口读同一个对象。每轮各建一个，不能跨轮复用。
     */
    @Test
    void opensWithAFreshSignalPerTurn() {
        ResidentAgentDrain drain = new ResidentAgentDrain();
        AtomicReference<TurnSignals> first = new AtomicReference<>();
        AtomicReference<TurnSignals> second = new AtomicReference<>();

        StreamAgentTurn.run(readyTurns(), drain, request(), Duration.ofSeconds(1),
                (runId, signals, context, started) -> {
                    first.set(signals);
                    return Flux.empty();
                }).blockLast();
        StreamAgentTurn.run(readyTurns(), drain, request(), Duration.ofSeconds(1),
                (runId, signals, context, started) -> {
                    second.set(signals);
                    return Flux.empty();
                }).blockLast();

        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isNotNull().isNotSameAs(first.get());
        assertThat(drain.inFlight()).isZero();
    }

    private static Flux<AgentStreamEvent> run(ResidentAgentDrain drain, Flux<AgentStreamEvent> body) {
        return StreamAgentTurn.run(readyTurns(), drain, request(), Duration.ofSeconds(30),
                (runId, signals, context, started) -> body);
    }

    private static AgentTurnSupport readyTurns() {
        AgentTurnSupport turns = mock(AgentTurnSupport.class);
        RuntimeContext context = RuntimeContext.builder().userId("u").sessionId("s").build();
        when(turns.context(eq(request()), any(), any())).thenReturn(context);
        return turns;
    }

    private static AgentRequest request() {
        return new AgentRequest("companion-v1", "t", "u", "s", "", "hi");
    }
}
