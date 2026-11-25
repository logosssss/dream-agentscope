package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zhu.runtime.api.agent.AgentStreamEvent;
import io.agentscope.core.event.AgentEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 流式记账在映射之前。不启动 Gateway。
 */
class WatchedStreamTest {

    /** 订阅方只看到映射结果，原事件先被记账。 */
    @Test
    void recordsBeforeMapping() {
        AgentEvent event = mock(AgentEvent.class);
        List<String> order = new ArrayList<>();
        List<AgentStreamEvent> out = WatchedStream.of(
                        Flux.just(event),
                        raw -> {
                            order.add("record");
                            assertThat(raw).isSameAs(event);
                        },
                        raw -> {
                            order.add("map");
                            return List.of(new AgentStreamEvent("text", "hi"));
                        })
                .collectList()
                .block();

        assertThat(order).containsExactly("record", "map");
        assertThat(out).containsExactly(new AgentStreamEvent("text", "hi"));
    }
}
