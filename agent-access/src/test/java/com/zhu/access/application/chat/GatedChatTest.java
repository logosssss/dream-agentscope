package com.zhu.access.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.common.exception.BusinessException;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 进闸再收口。不启动 Spring，不连 Redis。
 */
class GatedChatTest {

    /** 同步先过闸再记会话再发送，额度动作码是 chat，结束放名额。 */
    @Test
    void syncEntersBeforeSendAndCloses() {
        Trace trace = new Trace();
        AgentResponse returned = GatedChat.sync(
                trace.tenants(),
                trace.tenantUsers(),
                trace.owners(),
                trace.quota(),
                trace.concurrent(),
                request("user-1"),
                () -> trace.calls.add("remember"),
                () -> {
                    trace.calls.add("call");
                    return new AgentResponse("run-1", "ok");
                },
                (status, runId, output, errorCode, requestId, started, steps) -> trace.calls.add(status));
        assertThat(returned.text()).isEqualTo("ok");
        assertThat(trace.calls)
                .containsExactly(
                        "tenant",
                        "tenant-user",
                        "owner",
                        "quota:chat",
                        "open",
                        "remember",
                        "call",
                        RunLog.SUCCEEDED,
                        "close");
        assertThat(trace.closed.get()).isEqualTo(1);
    }

    /** 流式额度动作码是 chat-stream。流结束后放名额。 */
    @Test
    void streamUsesChatStreamAction() {
        Trace trace = new Trace();
        AgentStreamEvent event = GatedChat.stream(
                        trace.tenants(),
                        trace.tenantUsers(),
                        trace.owners(),
                        trace.quota(),
                        trace.concurrent(),
                        request("user-1"),
                        () -> trace.calls.add("remember"),
                        () -> Flux.just(new AgentStreamEvent("agent_result", "done")),
                        (status, runId, output, errorCode, requestId, started, steps) -> trace.calls.add(status))
                .blockLast();
        assertThat(event.text()).isEqualTo("done");
        assertThat(trace.calls).contains("quota:chat-stream", RunLog.SUCCEEDED, "close");
        assertThat(trace.closed.get()).isEqualTo(1);
    }

    /** 用户为空时闸直接拒绝，不记会话，也不占名额。 */
    @Test
    void rejectedGateSkipsRemember() {
        Trace trace = new Trace();
        assertThatThrownBy(() -> GatedChat.sync(
                        trace.tenants(),
                        trace.tenantUsers(),
                        trace.owners(),
                        trace.quota(),
                        trace.concurrent(),
                        request("  "),
                        () -> trace.calls.add("remember"),
                        () -> new AgentResponse("run", "ok"),
                        (status, runId, output, errorCode, requestId, started, steps) -> trace.calls.add(status)))
                .isInstanceOf(BusinessException.class);
        assertThat(trace.calls).isEmpty();
        assertThat(trace.closed.get()).isZero();
    }

    private static AgentRequest request(String userId) {
        return new AgentRequest(null, "tenant-1", userId, "session-1", "system", "hi");
    }

    /** 按调用顺序记下闸、记会话和发送。 */
    private static final class Trace {
        private final List<String> calls = new ArrayList<>();
        private final AtomicInteger closed = new AtomicInteger();
        private final ConcurrentGate.Slot slot = () -> "tenant-1";

        /** 租户闸只记一次调用。 */
        private TenantGate tenants() {
            return tenantId -> calls.add("tenant");
        }

        /** 租户用户闸只记一次调用。 */
        private TenantUserGate tenantUsers() {
            return (tenantId, userId) -> calls.add("tenant-user");
        }

        /** 会话归属闸只记一次调用。 */
        private SessionOwnerGate owners() {
            return (tenantId, userId, sessionId) -> calls.add("owner");
        }

        /** 额度闸记下动作码。 */
        private QuotaGate quota() {
            return (tenantId, userId, action, requestId) -> calls.add("quota:" + action);
        }

        /** 占名额记 open，释放记 close。 */
        private ConcurrentGate concurrent() {
            return new ConcurrentGate() {
                @Override
                public Slot open(String tenantId) {
                    calls.add("open");
                    return slot;
                }

                @Override
                public void close(Slot held) {
                    calls.add("close");
                    closed.incrementAndGet();
                }
            };
        }
    }
}
