package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.runtime.infrastructure.gateway.ApiChannel;
import com.zhu.runtime.infrastructure.gateway.ChannelGateway;
import com.zhu.runtime.infrastructure.gateway.RoomNormalizedSessionTurnGate;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.LocalSessionTurnGate;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.SessionTurnGate;
import io.agentscope.harness.agent.gateway.TurnBusyException;
import io.agentscope.harness.agent.gateway.TurnLease;
import io.agentscope.harness.agent.gateway.channel.Channel;
import io.agentscope.harness.agent.gateway.channel.ChannelConfig;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API Channel 落地 ①：对照 spike。
 * 钉清双注册白名单、ChatUi 主路径仍可读、闸键含 channel 前缀导致跨 Channel 默不同键、
 * 按 room 归一化后同 session 可串行。不改 Gateway 主路径注册（仍可只挂 chatui）。
 * 见 docs/api-channel-落地.md。
 */
class ApiChannelSurfaceSpikeTest {

    /**
     * 双注册 chatui + api stub 后，requireChatUi 仍取回 ChatUi；名单恰为二者。
     */
    @Test
    void dualRegister_whitelistAllowsChatUiAndApi() {
        HarnessAgent agent = stubAgent("api-spike-dual");
        StubApiChannel api = new StubApiChannel();
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("api-spike-dual", agent)
                .mainAgent("api-spike-dual")
                .channel(ChatUiChannel.create(), api)
                .build()
                .start();
        try {
            ChannelManager manager = bootstrap.channelManager();
            assertThat(manager.channelIds())
                    .containsExactlyInAnyOrder(ChatUiChannel.CHANNEL_ID, ChannelGateway.API_CHANNEL_ID);
            ChatUiChannel chat = ChannelGateway.requireChatUi(manager);
            assertThat(chat.channelId()).isEqualTo(ChatUiChannel.CHANNEL_ID);
            assertThat(manager.getChannel(ChannelGateway.API_CHANNEL_ID)).contains(api);
            Msg reply = chat.send(SendOptions.of("u1", "s1"), "hi").block(Duration.ofSeconds(5));
            assertThat(reply.getTextContent()).isEqualTo("ok");
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    /** 仅 chatui 仍通过白名单（主路径形状）。 */
    @Test
    void chatUiOnly_stillAllowed() {
        HarnessAgent agent = stubAgent("api-spike-one");
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("api-spike-one", agent)
                .mainAgent("api-spike-one")
                .channel(ChatUiChannel.create())
                .build()
                .start();
        try {
            assertThat(ChannelGateway.requireChatUi(bootstrap.channelManager()).channelId())
                    .isEqualTo(ChatUiChannel.CHANNEL_ID);
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    /** 第三者 id 不在白名单。 */
    @Test
    void thirdChannel_rejectedByWhitelist() {
        assertThat(ChannelGateway.allowedChannelIds(
                        List.of(ChatUiChannel.CHANNEL_ID, ChannelGateway.API_CHANNEL_ID, "other")))
                .isFalse();
        assertThatThrownBy(() -> {
                    HarnessAgent agent = stubAgent("api-spike-three");
                    Channel other = new StubApiChannel("other");
                    GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                            .agent("api-spike-three", agent)
                            .mainAgent("api-spike-three")
                            .channel(ChatUiChannel.create(), new StubApiChannel(), other)
                            .build()
                            .start();
                    try {
                        ChannelGateway.requireChatUi(bootstrap.channelManager());
                    } finally {
                        bootstrap.stop();
                        agent.close();
                    }
                })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chatui+api");
    }

    /**
     * 关键发现：MsgContext.canonicalKey 以 channel 为前缀（chatui|r:session vs api|r:session）。
     * 同 session 跨 Channel 直调 Gateway.run 时，默认闸键不同 → 可并行占轮。
     */
    @Test
    void naiveCrossChannel_differentGateKeys_allowsParallel() throws Exception {
        assertThat(msgContext("chatui", "session-a").canonicalKey())
                .isNotEqualTo(msgContext("api", "session-a").canonicalKey());

        TrackingGate tracking = new TrackingGate();
        HarnessAgent agent = slowAgent("api-spike-parallel");
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("api-spike-parallel", agent)
                .mainAgent("api-spike-parallel")
                .channel(ChatUiChannel.create(), new StubApiChannel())
                .build()
                .start();
        bootstrap.gateway().setSessionTurnGate(tracking);
        HarnessGateway gateway = (HarnessGateway) bootstrap.gateway();
        try {
            CountDownLatch done = new CountDownLatch(2);
            List<Throwable> errors = new ArrayList<>();
            Runnable chatUiTurn = () -> runTurn(done, errors, () ->
                    gateway.run(msgContext("chatui", "session-a"), List.of(userMsg("hi")), outbound("chatui", "session-a"))
                            .block(Duration.ofSeconds(5)));
            Runnable apiTurn = () -> runTurn(done, errors, () ->
                    gateway.run(msgContext("api", "session-a"), List.of(userMsg("hi")), outbound("api", "session-a"))
                            .block(Duration.ofSeconds(5)));
            Thread a = new Thread(chatUiTurn);
            Thread b = new Thread(apiTurn);
            a.start();
            b.start();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).isEmpty();
            assertThat(tracking.gateKeys).hasSize(2);
            assertThat(tracking.maxInFlight.get())
                    .as("闸键含 channel 前缀时同 session 可并行 keys=%s", tracking.gateKeys)
                    .isGreaterThanOrEqualTo(2);
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    /**
     * 对照：room 归一化后，经 Channel.send（产品路径）跨 Channel 同 session 串行。
     * 2.0.5 起直调 {@code gateway.run} 的闸键解析与 Channel 路径不完全一致；串行验收对齐产品入口。
     */
    @Test
    void roomNormalizedGate_sameSessionSerializesAcrossChannels() throws Exception {
        TrackingGate tracking =
                new TrackingGate(new RoomNormalizedSessionTurnGate(new LocalSessionTurnGate()));
        HarnessAgent agent = slowAgent("api-spike-serial");
        ApiChannel api = ApiChannel.create();
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("api-spike-serial", agent)
                .mainAgent("api-spike-serial")
                .channel(ChatUiChannel.create(), api)
                .build()
                .start();
        bootstrap.gateway().setSessionTurnGate(tracking);
        try {
            ChatUiChannel chat = ChannelGateway.requireChatUi(bootstrap.channelManager());
            ApiChannel boundApi = ChannelGateway.requireApiChannel(bootstrap.channelManager());
            CountDownLatch done = new CountDownLatch(2);
            List<Throwable> errors = new ArrayList<>();
            Thread chatUiTurn = new Thread(() -> runTurn(done, errors, () ->
                    chat.send(SendOptions.of("u1", "session-a"), "from-web").block(Duration.ofSeconds(5))));
            Thread apiTurn = new Thread(() -> runTurn(done, errors, () ->
                    boundApi.send("u1", "session-a", "from-api").block(Duration.ofSeconds(5))));
            chatUiTurn.start();
            apiTurn.start();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).as("errors=%s keys=%s", errors, tracking.gateKeys).isEmpty();
            assertThat(tracking.gateKeys).contains("chatui|r:session-a", "api|r:session-a");
            assertThat(tracking.maxInFlight.get())
                    .as("room 归一化后同 session 串行 keys=%s", tracking.gateKeys)
                    .isEqualTo(1);
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    private static void runTurn(CountDownLatch done, List<Throwable> errors, Runnable body) {
        try {
            body.run();
        } catch (Throwable error) {
            errors.add(error);
        } finally {
            done.countDown();
        }
    }

    private static MsgContext msgContext(String channel, String sessionId) {
        return new MsgContext(channel, null, sessionId, null, null, Map.of()).withUserId("u1");
    }

    private static OutboundAddress outbound(String channelId, String sessionId) {
        return OutboundAddress.direct(channelId, sessionId);
    }

    private static Msg userMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private static HarnessAgent stubAgent(String id) {
        return agentBuilder(id, stubModel()).build();
    }

    private static HarnessAgent slowAgent(String id) {
        return agentBuilder(id, slowModel()).build();
    }

    private static HarnessAgent.Builder agentBuilder(String id, Model model) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(id)
                .agentId(id)
                .model(model)
                .sysPrompt("test")
                .maxIters(1);
        builder.disableWebTools();
        builder.disableShellTool();
        return builder;
    }

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("r1")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "api-spike-model";
            }
        };
    }

    private static Model slowModel() {
        return new Model() {
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
                return "api-spike-slow";
            }
        };
    }

    /**
     * spike 用占位 Channel：只占 channelId=api（或自定义），dispatch 不支撑产品路径。
     */
    private static final class StubApiChannel implements Channel {
        private final ChannelConfig config;

        StubApiChannel() {
            this(ChannelGateway.API_CHANNEL_ID);
        }

        StubApiChannel(String channelId) {
            this.config = ChannelConfig.of(channelId);
        }

        @Override
        public String channelId() {
            return config.channelId();
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public Mono<Msg> dispatch(InboundMessage message) {
            return Mono.error(new UnsupportedOperationException("spike stub api channel"));
        }

        @Override
        public Flux<AgentEvent> dispatchStream(InboundMessage message) {
            return Flux.error(new UnsupportedOperationException("spike stub api channel"));
        }
    }

    /** 记录 acquire 键与并发占轮；可包一层归一化闸。 */
    private static final class TrackingGate implements SessionTurnGate {
        private final SessionTurnGate delegate;
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();
        private final Set<String> gateKeys = ConcurrentHashMap.newKeySet();

        TrackingGate() {
            this(new LocalSessionTurnGate());
        }

        TrackingGate(SessionTurnGate delegate) {
            this.delegate = delegate;
        }

        @Override
        public TurnLease acquire(String sessionKey) throws InterruptedException, TurnBusyException {
            gateKeys.add(sessionKey);
            TurnLease lease = delegate.acquire(sessionKey);
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            // 先减计数再放行下一等待者，避免 close 与 decrement 之间的交接把 max 抬成 2。
            return () -> {
                inFlight.decrementAndGet();
                lease.close();
            };
        }

        @Override
        public boolean isRunning(String sessionKey) {
            return delegate.isRunning(sessionKey);
        }
    }

}
