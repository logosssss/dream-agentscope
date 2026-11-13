package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.gateway.AccessApiChannel;
import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import com.zhu.access.application.chat.gateway.RoomNormalizedSessionTurnGate;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.SessionTurnGate;
import io.agentscope.harness.agent.gateway.TurnBusyException;
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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * API Channel 落地 ②：工厂/Gateway 接线。
 * 双注册 chatui+api、room 归一化闸、WEB 仍可读 ChatUi、跨 Channel 同 session 串行。
 * 见 docs/api-channel-落地.md。
 */
class ApiChannelGatewayWiringTest {

    /** 与 AccessChannelGateway.startGatewayLocked 相同：双通道 + 归一化闸。 */
    @Test
    void dualChannelAndNormalizedGate_webChatUiStillWorks_crossChannelSerial() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        Set<String> gateKeys = ConcurrentHashMap.newKeySet();
        SessionTurnGate tracking = new SessionTurnGate() {
            private final SessionTurnGate delegate =
                    new RoomNormalizedSessionTurnGate(new LocalSessionTurnGate());

            @Override
            public TurnLease acquire(String sessionKey) throws InterruptedException, TurnBusyException {
                gateKeys.add(sessionKey);
                TurnLease lease = delegate.acquire(sessionKey);
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                return () -> {
                    try {
                        lease.close();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                };
            }

            @Override
            public boolean isRunning(String sessionKey) {
                return delegate.isRunning(sessionKey);
            }
        };

        HarnessAgent agent = slowAgent("api-wired");
        AccessApiChannel api = AccessApiChannel.create();
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("api-wired", agent)
                .mainAgent("api-wired")
                .channel(ChatUiChannel.create(), api)
                .build()
                .start();
        bootstrap.gateway().setSessionTurnGate(tracking);
        try {
            ChatUiChannel chat = AccessChannelGateway.requireChatUi(bootstrap.channelManager());
            AccessApiChannel boundApi = AccessChannelGateway.requireApiChannel(bootstrap.channelManager());
            assertThat(boundApi).isSameAs(api);
            assertThat(bootstrap.channelManager().channelIds())
                    .containsExactlyInAnyOrder(ChatUiChannel.CHANNEL_ID, AccessChannelGateway.API_CHANNEL_ID);

            Msg webReply = chat.send(SendOptions.of("u1", "s-web"), "hi").block(Duration.ofSeconds(5));
            assertThat(webReply.getTextContent()).isEqualTo("ok");

            CountDownLatch done = new CountDownLatch(2);
            List<Throwable> errors = new ArrayList<>();
            Thread chatUiTurn = new Thread(() -> {
                try {
                    chat.send(SendOptions.of("u1", "session-a"), "from-web").block(Duration.ofSeconds(5));
                } catch (Throwable error) {
                    errors.add(error);
                } finally {
                    done.countDown();
                }
            });
            Thread apiTurn = new Thread(() -> {
                try {
                    boundApi.send("u1", "session-a", "from-api").block(Duration.ofSeconds(5));
                } catch (Throwable error) {
                    errors.add(error);
                } finally {
                    done.countDown();
                }
            });
            chatUiTurn.start();
            apiTurn.start();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).as("errors=%s keys=%s", errors, gateKeys).isEmpty();
            // Gateway 仍传入带 channel 前缀的键；归一化在委托闸内，故并发占轮 = 1。
            assertThat(gateKeys).contains("chatui|r:session-a", "api|r:session-a");
            assertThat(maxInFlight.get()).as("keys=%s", gateKeys).isEqualTo(1);
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    /** roomKey 抽段：chatui|r:s1|t:x → s1。 */
    @Test
    void roomKey_extractsSessionSegment() {
        assertThat(RoomNormalizedSessionTurnGate.roomKey("chatui|r:session-a")).isEqualTo("session-a");
        assertThat(RoomNormalizedSessionTurnGate.roomKey("api|r:session-a|t:1")).isEqualTo("session-a");
        assertThat(RoomNormalizedSessionTurnGate.roomKey("plain")).isEqualTo("plain");
    }

    private static HarnessAgent slowAgent(String id) {
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
                return "api-wired-slow";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(id)
                .agentId(id)
                .model(slow)
                .sysPrompt("test")
                .maxIters(1);
        builder.disableWebTools();
        builder.disableShellTool();
        return builder.build();
    }
}
