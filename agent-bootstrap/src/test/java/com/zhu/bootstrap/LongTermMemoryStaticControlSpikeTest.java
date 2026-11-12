package com.zhu.bootstrap;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.StaticLongTermMemoryHook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 落地 ①：STATIC_CONTROL / longTermMemory 接线 spike。
 * 只验证框架 Builder 挂 LTM 后是否自动 retrieve/record、是否仍走 StaticLongTermMemoryHook。
 * 不改 Companion 主路径，不连 Redis/MySQL/Milvus。
 */
class LongTermMemoryStaticControlSpikeTest {

    /**
     * STATIC_CONTROL 在 build 时把 StaticLongTermMemoryHook 塞进 hooks；
     * 一轮 call 后计数桩应看到 retrieve 与 record。
     */
    @Test
    void staticControl_wiresHookAndAutoRetrieveRecord() {
        CountingLongTermMemory ltm = new CountingLongTermMemory();
        ReActAgent agent = ReActAgent.builder()
                .name("ltm-static-spike")
                .model(fixedReplyModel("ok"))
                .sysPrompt("spike")
                .maxIters(1)
                .longTermMemory(ltm)
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .longTermMemoryAsyncRecord(false)
                .build();
        try {
            assertThat(agent.getHooks())
                    .as("STATIC_CONTROL 应自动挂 StaticLongTermMemoryHook")
                    .anyMatch(StaticLongTermMemoryHook.class::isInstance);

            Msg reply = agent.call("记住我喜欢散步").block(Duration.ofSeconds(10));
            assertThat(reply).isNotNull();

            assertThat(ltm.retrieveCount.get())
                    .as("PreCall 应触发 retrieve")
                    .isGreaterThanOrEqualTo(1);
            assertThat(ltm.recordCount.get())
                    .as("PostCall 应触发 record")
                    .isGreaterThanOrEqualTo(1);
            assertThat(ltm.retrieveQueries).isNotEmpty();
            assertThat(ltm.recordedBatches).isNotEmpty();
        } finally {
            agent.close();
        }
    }

    /**
     * AGENT_CONTROL 不挂 StaticLongTermMemoryHook；一轮 call 不应自动 retrieve/record。
     * 工具态是否可用留给后续块，本块只钉「非 STATIC 不会自动记」。
     */
    @Test
    void agentControl_doesNotAutoHookOrCallLtm() {
        CountingLongTermMemory ltm = new CountingLongTermMemory();
        ReActAgent agent = ReActAgent.builder()
                .name("ltm-agent-spike")
                .model(fixedReplyModel("ok"))
                .sysPrompt("spike")
                .maxIters(1)
                .longTermMemory(ltm)
                .longTermMemoryMode(LongTermMemoryMode.AGENT_CONTROL)
                .build();
        try {
            assertThat(agent.getHooks())
                    .filteredOn(StaticLongTermMemoryHook.class::isInstance)
                    .isEmpty();

            agent.call("hello").block(Duration.ofSeconds(10));

            assertThat(ltm.retrieveCount.get()).isZero();
            assertThat(ltm.recordCount.get()).isZero();
        } finally {
            agent.close();
        }
    }

    /**
     * 产品入口是 HarnessAgent.Builder：2.0.4 无公开 longTermMemory / longTermMemoryMode。
     * 钉住缺口，避免后续误以为 Gateway 路径已能一键挂 LTM。
     */
    @Test
    void harnessBuilder_hasNoPublicLongTermMemoryApi() {
        List<String> names = Arrays.stream(HarnessAgent.Builder.class.getMethods())
                .map(Method::getName)
                .toList();
        assertThat(names)
                .as("Harness Builder 公开方法")
                .doesNotContain("longTermMemory", "longTermMemoryMode", "longTermMemoryAsyncRecord");
        // hook() 仍在：若产品要走 STATIC，只能手动 hook 或先 ReAct 再 fromAgent。
        assertThat(names).contains("hook", "memory", "disableMemoryHooks");
    }

    /**
     * 固定一句回复的模型桩。不连真实推理服务。
     */
    private static Model fixedReplyModel(String text) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("ltm-spike")
                        .content(List.of(TextBlock.builder().text(text).build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "ltm-spike-model";
            }
        };
    }

    /**
     * 计数用 LongTermMemory 桩。retrieve 返回固定短文，record 只记账。
     */
    private static final class CountingLongTermMemory implements LongTermMemory {

        private final AtomicInteger retrieveCount = new AtomicInteger();

        private final AtomicInteger recordCount = new AtomicInteger();

        private final List<Msg> retrieveQueries = new CopyOnWriteArrayList<>();

        private final List<List<Msg>> recordedBatches = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> record(List<Msg> msgs) {
            recordCount.incrementAndGet();
            recordedBatches.add(msgs == null ? List.of() : new ArrayList<>(msgs));
            return Mono.empty();
        }

        @Override
        public Mono<String> retrieve(Msg msg) {
            retrieveCount.incrementAndGet();
            retrieveQueries.add(msg);
            return Mono.just("spike-memory: likes walking");
        }
    }
}
