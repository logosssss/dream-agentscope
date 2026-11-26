package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.runtime.api.agent.AgentImagePart;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * ChatUi 首发和看图重试。不启动 Gateway，不连 Redis。
 */
class ChatUiAttemptTest {

    /** 没图时同步错误原样抛出，只发一次。 */
    @Test
    void textSendDoesNotRetry() {
        IllegalStateException error = new IllegalStateException("down");
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger degraded = new AtomicInteger();
        ChatUiAttempt attempt = ChatUiAttempt.open(options(), textRequest());
        assertThatThrownBy(() -> attempt.send((opts, msg) -> {
            sends.incrementAndGet();
            throw error;
        }, ignored -> degraded.incrementAndGet()))
                .isSameAs(error);
        assertThat(sends.get()).isEqualTo(1);
        assertThat(degraded.get()).isZero();
    }

    /** 有图时第二次仍用同一组选项，正文是不带 name 的纯文本。 */
    @Test
    void imageSendRetriesWithTheSameOptions() {
        SendOptions options = options();
        List<SendOptions> seen = new ArrayList<>();
        List<Msg> sent = new ArrayList<>();
        AtomicInteger degraded = new AtomicInteger();
        ChatUiAttempt attempt = ChatUiAttempt.open(options, imageRequest());
        Msg reply = attempt.send((opts, msg) -> {
            seen.add(opts);
            sent.add(msg);
            if (sent.size() == 1) {
                throw new IllegalStateException("down");
            }
            return msg;
        }, ignored -> degraded.incrementAndGet());
        assertThat(seen).containsExactly(options, options);
        assertThat(sent.get(1).getName()).isNull();
        assertThat(sent.get(1).getTextContent()).startsWith(AgentUserMessages.VISION_DEGRADE_PREFIX);
        assertThat(reply).isSameAs(sent.get(1));
        assertThat(degraded.get()).isEqualTo(1);
    }

    /** 没图的流错误原样结束，不推状态句。有图时先推状态句，再用同一组选项重试。 */
    @Test
    void streamTipOnlyWhenImagesFail() {
        IllegalStateException error = new IllegalStateException("down");
        ChatUiAttempt text = ChatUiAttempt.open(options(), textRequest());
        assertThatThrownBy(() -> text.stream((opts, msg) -> Flux.error(error), ignored -> {
        }).blockLast())
                .isSameAs(error);

        SendOptions options = options();
        List<SendOptions> seen = new ArrayList<>();
        List<AgentStreamEvent> events = ChatUiAttempt.open(options, imageRequest())
                .stream((opts, msg) -> {
                    seen.add(opts);
                    if (seen.size() == 1) {
                        return Flux.error(error);
                    }
                    return Flux.just(new AgentStreamEvent("agent_result", msg.getTextContent()));
                }, ignored -> {
                })
                .collectList()
                .block();
        assertThat(seen).containsExactly(options, options);
        assertThat(events).extracting(AgentStreamEvent::type).containsExactly("tool_call", "agent_result");
        assertThat(events.get(0).text()).isEqualTo(VisionDegrade.CLIENT_TIP);
        assertThat(events.get(1).text()).startsWith(AgentUserMessages.VISION_DEGRADE_PREFIX);
    }

    private static SendOptions options() {
        return ChatUiRoute.options("u", "s", "t", null, "companion-v1", "run-1", null);
    }

    private static AgentRequest textRequest() {
        return new AgentRequest("companion-v1", "t", "u", "s", "", "hello");
    }

    private static AgentRequest imageRequest() {
        return new AgentRequest(
                "companion-v1",
                "t",
                "u",
                "s",
                "",
                "hello",
                List.of(new AgentImagePart("a", "image/png", "aaaa")));
    }
}
