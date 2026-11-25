package com.zhu.access.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.common.context.RequestIdContext;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 四个对话入口共用的占位释放。不启动 Spring。
 */
class ChatTurnTest {

    @AfterEach
    void clearRequestId() {
        RequestIdContext.clear();
    }

    /** 同步成功记下回复并放名额。发送失败先记失败再放名额，然后原样抛出。 */
    @Test
    void syncRecordsThenReleases() {
        FakeGate gate = new FakeGate();
        List<String> statuses = new ArrayList<>();
        AgentResponse response = new AgentResponse("run-1", "ok");
        AgentResponse returned = ChatTurn.sync(gate, gate.slot, () -> {
        }, () -> response, (status, runId, output, errorCode, requestId, started, steps) -> {
            statuses.add(status);
            assertThat(runId).isEqualTo("run-1");
            assertThat(output).isEqualTo("ok");
            assertThat(steps).containsExactly(new RunStep(RunStep.REASONING, null, RunLog.SUCCEEDED));
        });

        assertThat(returned).isSameAs(response);
        assertThat(statuses).containsExactly(RunLog.SUCCEEDED);
        assertThat(gate.closed.get()).isEqualTo(1);

        statuses.clear();
        assertThatThrownBy(() -> ChatTurn.sync(gate, gate.slot, () -> {
        }, () -> {
            throw new IllegalStateException("down");
        }, (status, runId, output, errorCode, requestId, started, steps) -> {
            statuses.add(status + ":" + errorCode);
            assertThat(steps).containsExactly(new RunStep(RunStep.REASONING, null, RunLog.FAILED));
        })).isInstanceOf(IllegalStateException.class);
        assertThat(statuses).containsExactly("FAILED:IllegalStateException");
        assertThat(gate.closed.get()).isEqualTo(2);
    }

    /** 准备失败立刻放名额。流结束先落库再放名额，取消也放。 */
    @Test
    void streamReleasesOnEveryExit() {
        FakeGate gate = new FakeGate();
        List<String> statuses = new ArrayList<>();
        assertThatThrownBy(() -> ChatTurn.stream(gate, gate.slot, () -> {
            throw new IllegalStateException("remember");
        }, Flux::empty, (status, runId, output, errorCode, requestId, started, steps) -> statuses.add(status)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(statuses).containsExactly(RunLog.FAILED);
        assertThat(gate.closed.get()).isEqualTo(1);

        statuses.clear();
        ChatTurn.stream(gate, gate.slot, () -> {
        }, () -> Flux.just(new AgentStreamEvent("agent_result", "done")), record(statuses)).blockLast();
        assertThat(statuses).containsExactly(RunLog.SUCCEEDED);
        assertThat(gate.closed.get()).isEqualTo(2);

        statuses.clear();
        assertThatThrownBy(() -> ChatTurn.stream(gate, gate.slot, () -> {
        }, () -> Flux.error(new IllegalStateException("down")), record(statuses)).blockLast())
                .isInstanceOf(IllegalStateException.class);
        assertThat(statuses).containsExactly(RunLog.FAILED);
        assertThat(gate.closed.get()).isEqualTo(3);

        statuses.clear();
        ChatTurn.stream(gate, gate.slot, () -> {
        }, Flux::never, record(statuses)).subscribe().dispose();
        assertThat(statuses).containsExactly(RunLog.CANCELLED);
        assertThat(gate.closed.get()).isEqualTo(4);
    }

    /** 入口线程上的请求号会带到落库，不靠流回调再去读线程。 */
    @Test
    void copiesRequestIdBeforeTheStream() {
        RequestIdContext.set("req-9");
        List<String> ids = new ArrayList<>();
        FakeGate gate = new FakeGate();
        ChatTurn.sync(gate, gate.slot, () -> RequestIdContext.clear(), () -> new AgentResponse("run", "ok"),
                (status, runId, output, errorCode, requestId, started, steps) -> ids.add(requestId));
        assertThat(ids).containsExactly("req-9");
    }

    private static ChatTurn.Recorder record(List<String> statuses) {
        return (status, runId, output, errorCode, requestId, started, steps) -> statuses.add(status);
    }

    private static final class FakeGate implements ConcurrentGate {
        private final Slot slot = () -> "slot";
        private final AtomicInteger closed = new AtomicInteger();

        @Override
        public Slot open(String tenantId) {
            return slot;
        }

        @Override
        public void close(Slot slot) {
            closed.incrementAndGet();
        }
    }
}
