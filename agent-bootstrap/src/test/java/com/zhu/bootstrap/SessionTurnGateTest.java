package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.SessionTurnGate;
import io.agentscope.harness.agent.gateway.TurnLease;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 同 session 串行：显式 LocalSessionTurnGate + ChatUi 进 Gateway 后，第二轮等第一轮结束。
 */
class SessionTurnGateTest {

    /** 闸本身：第二线程 acquire 会阻塞到第一轮 release。 */
    @Test
    void localGateSecondAcquireWaits() throws Exception {
        LocalSessionTurnGate gate = new LocalSessionTurnGate();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicLong secondStart = new AtomicLong();
        AtomicLong firstEnd = new AtomicLong();
        Thread first = new Thread(() -> {
            try {
                var lease = gate.acquire("s1");
                held.countDown();
                Thread.sleep(120L);
                firstEnd.set(System.nanoTime());
                lease.close();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Thread second = new Thread(() -> {
            try {
                assertThat(held.await(2, TimeUnit.SECONDS)).isTrue();
                var lease = gate.acquire("s1");
                secondStart.set(System.nanoTime());
                secondEntered.countDown();
                lease.close();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        first.start();
        second.start();
        assertThat(secondEntered.await(3, TimeUnit.SECONDS)).isTrue();
        first.join(3000L);
        second.join(3000L);
        assertThat(secondStart.get()).isGreaterThanOrEqualTo(firstEnd.get());
    }

    /**
     * 与 AccessChannelGateway 相同接线：Bootstrap 启动后显式 setSessionTurnGate，
     * 同 user/session 两路 send 并发时，闸键相同且并发占轮数不超过 1。
     * 不观测模型 in-flight：单轮内 memory 等仍可能并行调模型。
     */
    @Test
    void chatUiSameSessionSerializesViaExplicitGate() throws Exception {
        AtomicInteger gateInFlight = new AtomicInteger();
        AtomicInteger maxGateInFlight = new AtomicInteger();
        Set<String> gateKeys = ConcurrentHashMap.newKeySet();
        SessionTurnGate trackingGate = new SessionTurnGate() {
            private final LocalSessionTurnGate delegate = new LocalSessionTurnGate();

            @Override
            public TurnLease acquire(String sessionKey) throws InterruptedException {
                gateKeys.add(sessionKey);
                // 必须在占到许可之后再计数，否则等待中的线程会把 max 抬高。
                TurnLease lease = delegate.acquire(sessionKey);
                int now = gateInFlight.incrementAndGet();
                maxGateInFlight.accumulateAndGet(now, Math::max);
                return () -> {
                    try {
                        lease.close();
                    } finally {
                        gateInFlight.decrementAndGet();
                    }
                };
            }

            @Override
            public boolean isRunning(String sessionKey) {
                return delegate.isRunning(sessionKey);
            }
        };
        Model slow = new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
                return Flux.just(ChatResponse.builder()
                        .id("r1")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "slow-test";
            }
        };
        HarnessAgent.Builder agentBuilder = HarnessAgent.builder()
                .name("turn-test")
                .agentId("turn-test")
                .model(slow)
                .sysPrompt("test")
                .maxIters(1);
        agentBuilder.disableWebTools();
        agentBuilder.disableShellTool();
        HarnessAgent agent = agentBuilder.build();
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("turn-test", agent)
                .mainAgent("turn-test")
                .channel(ChatUiChannel.create())
                .build()
                .start();
        // 与 AccessChannelGateway.startGatewayLocked 相同：start 后显式挂闸，通道从 manager 只读。
        bootstrap.gateway().setSessionTurnGate(trackingGate);
        ChatUiChannel chat = AccessChannelGateway.requireChatUi(bootstrap.channelManager());
        SendOptions options = SendOptions.of("u1", "session-a");
        try {
            CountDownLatch done = new CountDownLatch(2);
            List<Throwable> errors = new ArrayList<>();
            List<Msg> replies = new ArrayList<>();
            Runnable turn = () -> {
                try {
                    Msg reply = chat.send(options, "hi").block(Duration.ofSeconds(5));
                    synchronized (replies) {
                        replies.add(reply);
                    }
                } catch (Throwable error) {
                    errors.add(error);
                } finally {
                    done.countDown();
                }
            };
            Thread a = new Thread(turn);
            Thread b = new Thread(turn);
            a.start();
            b.start();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).as("errors=%s keys=%s", errors, gateKeys).isEmpty();
            assertThat(gateKeys).as("gate keys").hasSize(1);
            assertThat(maxGateInFlight.get()).as("max concurrent gate holds keys=%s", gateKeys).isEqualTo(1);
            assertThat(replies).hasSize(2);
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }
}
