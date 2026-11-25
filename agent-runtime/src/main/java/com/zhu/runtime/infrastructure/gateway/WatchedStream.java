package com.zhu.runtime.infrastructure.gateway;

import com.zhu.runtime.api.agent.AgentStreamEvent;
import io.agentscope.core.event.AgentEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 流式事件先交给记账，再映射给调用方。WEB 主流、看图重试和 API 共用这一条顺序。
 */
final class WatchedStream {

    /** 只提供静态方法，不让三条流各写一份记账顺序。 */
    private WatchedStream() {
    }

    /**
     * 记账在映射之前。用量和待确认看到的是框架原事件，调用方看到的是护栏之后的片段。
     */
    static Flux<AgentStreamEvent> of(
            Flux<? extends AgentEvent> raw,
            Consumer<AgentEvent> onEvent,
            Function<AgentEvent, ? extends Iterable<AgentStreamEvent>> map) {
        return raw.doOnNext(onEvent).concatMapIterable(map);
    }
}
