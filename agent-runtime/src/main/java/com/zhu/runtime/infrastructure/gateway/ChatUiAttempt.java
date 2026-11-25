package com.zhu.runtime.infrastructure.gateway;

import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.gateway.channel.chatui.SendOptions;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;

/**
 * ChatUi 一轮的首发和看图降级。运行层网关内部。
 * 同步和流式共用同一组选项、同一条首发消息；重试仍用这组选项。
 */
final class ChatUiAttempt {

    private final SendOptions options;

    private final Msg first;

    private final AgentRequest request;

    /** 首发消息在这里组好，避免同步和流式各调一次 {@link AgentUserMessages#of}。 */
    private ChatUiAttempt(SendOptions options, Msg first, AgentRequest request) {
        this.options = options;
        this.first = first;
        this.request = request;
    }

    /**
     * 选项来自已经懒启动后的路由。首发消息按请求里的图片组装。
     */
    static ChatUiAttempt open(SendOptions options, AgentRequest request) {
        return new ChatUiAttempt(options, AgentUserMessages.of(request), request);
    }

    /**
     * 先发首条。运行时失败且有图时，用同一组选项再发纯文本。
     * 没图时原错误继续抛出，{@code onDegrade} 不会被调用。
     */
    <T> T send(BiFunction<SendOptions, Msg, T> send, Consumer<RuntimeException> onDegrade) {
        try {
            return send.apply(options, first);
        } catch (RuntimeException error) {
            Msg plain = VisionDegrade.plainOrThrow(request, error);
            onDegrade.accept(error);
            return send.apply(options, plain);
        }
    }

    /**
     * 先发首条流。没图时错误原样往下传。有图时先推状态句，再用同一组选项重试。
     * 状态句只在流式出现，同步重试不推这条提示。
     */
    Flux<AgentStreamEvent> stream(
            BiFunction<SendOptions, Msg, Flux<AgentStreamEvent>> send, Consumer<Throwable> onDegrade) {
        Flux<AgentStreamEvent> primary = send.apply(options, first);
        if (request == null || !request.hasImages()) {
            return primary;
        }
        return primary.onErrorResume(error -> {
            Msg plain = VisionDegrade.plainOrThrow(request, error);
            onDegrade.accept(error);
            Flux<AgentStreamEvent> tip = Flux.just(new AgentStreamEvent("tool_call", VisionDegrade.CLIENT_TIP));
            return tip.concatWith(send.apply(options, plain));
        });
    }
}
