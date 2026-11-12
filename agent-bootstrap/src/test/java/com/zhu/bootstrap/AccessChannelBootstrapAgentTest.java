package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.AccessChannelGateway;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.GatewayBootstrap;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Bootstrap 用 Consumer 建 Agent：configureAllAgents 先于各 Agent 定制，且只挂公共项。
 */
class AccessChannelBootstrapAgentTest {

    /** 公共项对 companion/echo 各跑一次；定制项只在对应 Agent 上出现。 */
    @Test
    void configureAllAgentsRunsBeforePerAgentCustomize() {
        AtomicInteger commonHits = new AtomicInteger();
        AtomicInteger companionHits = new AtomicInteger();
        AtomicInteger echoHits = new AtomicInteger();
        Model model = replyModel("bootstrap-agent");
        GatewayBootstrap bootstrap = GatewayBootstrap.builder()
                .configureAllAgents(builder -> {
                    commonHits.incrementAndGet();
                    builder.model(model);
                    builder.disableWebTools();
                    builder.disableShellTool();
                    builder.maxIters(1);
                })
                .agent("companion-v1", builder -> {
                    companionHits.incrementAndGet();
                    builder.name("companion").agentId("companion-v1").sysPrompt("companion");
                })
                .agent(HarnessAgentFactory.ECHO_AGENT_ID, builder -> {
                    echoHits.incrementAndGet();
                    builder.name("echo")
                            .agentId(HarnessAgentFactory.ECHO_AGENT_ID)
                            .sysPrompt(HarnessAgentFactory.ECHO_SYS_PROMPT);
                })
                .mainAgent("companion-v1")
                .channel(ChatUiChannel.create())
                .build()
                .start();
        try {
            assertThat(commonHits.get()).isEqualTo(2);
            assertThat(companionHits.get()).isEqualTo(1);
            assertThat(echoHits.get()).isEqualTo(1);
            assertThat(bootstrap.agents()).containsOnlyKeys("companion-v1", HarnessAgentFactory.ECHO_AGENT_ID);
            assertThat(bootstrap.mainAgentId()).isEqualTo("companion-v1");
            ChatUiChannel chat = AccessChannelGateway.requireChatUi(bootstrap.channelManager());
            Msg reply = chat.send(SendOptions.of("u1", "s1"), "hi").block(Duration.ofSeconds(5));
            assertThat(reply.getTextContent()).isEqualTo("ok");
            HarnessAgent echo = bootstrap.agents().get(HarnessAgentFactory.ECHO_AGENT_ID);
            Msg routed = chat.send(
                            SendOptions.of("u1", "s2").withAgentId(HarnessAgentFactory.ECHO_AGENT_ID), "ping")
                    .block(Duration.ofSeconds(5));
            assertThat(echo).isNotNull();
            assertThat(routed.getTextContent()).isEqualTo("ok");
        } finally {
            bootstrap.stop();
            for (HarnessAgent agent : bootstrap.agents().values()) {
                agent.close();
            }
        }
    }

    private static Model replyModel(String name) {
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
                return name;
            }
        };
    }
}
