package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * ChannelManager 只读显式：Builder 只挂 chatui，启动后从 manager 取回，名单不能多通道。
 */
class AccessChannelManagerTest {

    /** 与 AccessChannelGateway 相同：.channel(ChatUiChannel.create()) 后 requireChatUi。 */
    @Test
    void requireChatUiReadsOnlyBootstrapChannel() {
        HarnessAgent agent = slowAgent("cm-test");
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("cm-test", agent)
                .mainAgent("cm-test")
                .channel(ChatUiChannel.create())
                .build()
                .start();
        try {
            ChannelManager manager = bootstrap.channelManager();
            ChatUiChannel chat = AccessChannelGateway.requireChatUi(manager);
            assertThat(manager.channelIds()).containsExactly(ChatUiChannel.CHANNEL_ID);
            assertThat(chat.channelId()).isEqualTo(ChatUiChannel.CHANNEL_ID);
            Msg reply = chat.send(SendOptions.of("u1", "s1"), "hi").block(Duration.ofSeconds(5));
            assertThat(reply).isNotNull();
            assertThat(reply.getTextContent()).isEqualTo("ok");
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    /** 没挂通道时 requireChatUi 直接失败，不回退到另造 ChatUi。 */
    @Test
    void requireChatUiRejectsEmptyManager() {
        HarnessAgent agent = slowAgent("cm-empty");
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .agent("cm-empty", agent)
                .mainAgent("cm-empty")
                .build()
                .start();
        try {
            assertThatThrownBy(() -> AccessChannelGateway.requireChatUi(bootstrap.channelManager()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chatui");
        } finally {
            bootstrap.stop();
            agent.close();
        }
    }

    private static HarnessAgent slowAgent(String id) {
        Model model = new Model() {
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
                return "cm-test-model";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(id)
                .agentId(id)
                .model(model)
                .sysPrompt("test")
                .maxIters(1);
        builder.disableWebTools();
        builder.disableShellTool();
        return builder.build();
    }
}
