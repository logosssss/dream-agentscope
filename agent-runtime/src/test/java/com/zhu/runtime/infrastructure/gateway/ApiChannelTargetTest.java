package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.Gateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API 按整份发送参数进出。不启动 GatewayBootstrap，不连 Redis。
 */
class ApiChannelTargetTest {

    /** 同步和流式都把同一份 Target 的用户、会话、标识、正文和租户暂存交给框架。 */
    @Test
    void sendAndStreamUseTheSameTargetFields() {
        RecordingGateway gateway = new RecordingGateway();
        ApiChannel channel = ApiChannel.create();
        channel.init(gateway);
        ApiOutbound.Target target = new ApiOutbound.Target(
                "u",
                "s",
                "docs-v1",
                "hi",
                AgentChannelRuntimeContextResolver.staging("t", "docs-v1", "run-1", null));
        channel.send(target).block();
        assertDelivery(gateway, false);
        channel.sendStream(target).blockLast();
        assertDelivery(gateway, true);
    }

    /** 空标识不写进路由附加字段。 */
    @Test
    void blankAgentIdStaysOffTheRoute() {
        RecordingGateway gateway = new RecordingGateway();
        ApiChannel channel = ApiChannel.create();
        channel.init(gateway);
        channel.send(new ApiOutbound.Target("u", "s", null, "hi", RuntimeContext.builder().build()))
                .block();
        assertThat(gateway.context.extra()).isEmpty();
    }

    private static void assertDelivery(RecordingGateway gateway, boolean stream) {
        assertThat(gateway.stream).isEqualTo(stream);
        assertThat(gateway.context.channel()).isEqualTo(ChannelGateway.API_CHANNEL_ID);
        assertThat(gateway.context.userId()).isEqualTo("u");
        assertThat(gateway.context.room()).isEqualTo("s");
        assertThat(gateway.context.extra()).containsEntry("agentId", "docs-v1");
        assertThat(gateway.messages).hasSize(1);
        assertThat(gateway.messages.get(0).getTextContent()).isEqualTo("hi");
        Object tenant = gateway.runtime.get(AgentChannelRuntimeContextResolver.TENANT_ID);
        assertThat(tenant).isEqualTo("t");
    }

    /** 记下框架真正收到的上下文。其余入口不参与这次断言。 */
    private static final class RecordingGateway implements Gateway {
        private MsgContext context;
        private List<Msg> messages;
        private RuntimeContext runtime;
        private boolean stream;

        /** 本测试不绑定主 Agent。 */
        @Override
        public void bindMainAgent(HarnessAgent agent) {
        }

        /** 四参数同步入口未走到时的兜底，本测试会覆盖四参数方法。 */
        @Override
        public Mono<Msg> run(MsgContext context, List<Msg> messages) {
            this.context = context;
            this.messages = messages;
            return Mono.just(Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build());
        }

        /** 同步发送落到这里，保留租户暂存。 */
        @Override
        public Mono<Msg> run(
                MsgContext context, List<Msg> messages, OutboundAddress outbound, RuntimeContext runtimeContext) {
            this.stream = false;
            this.context = context;
            this.messages = messages;
            this.runtime = runtimeContext;
            return Mono.just(Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build());
        }

        /** 流式发送落到这里，字段与同步相同。 */
        @Override
        public Flux<AgentEvent> runStream(
                MsgContext context, List<Msg> messages, OutboundAddress outbound, RuntimeContext runtimeContext) {
            this.stream = true;
            this.context = context;
            this.messages = messages;
            this.runtime = runtimeContext;
            return Flux.empty();
        }
    }
}
