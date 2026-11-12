package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 迁入 ①：打开 Harness Memory（与工厂 configureCommon 等价配置）。
 * 有对话模型时暴露 memory_*；不 disable；MemoryConfig 为 throttled(10m) 且无独立 model。
 */
class HarnessMemoryOpenedMigrationTest {

    private static final List<String> HARNESS_MEMORY_TOOLS =
            List.of("memory_save", "memory_get", "memory_search");

    /**
     * 迁入公共项：挂 throttled MemoryConfig、不 disable → Toolkit 含 memory_*。
     */
    @Test
    void openedMemoryConfig_registersMemoryTools() {
        MemoryConfig memory = MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10)))
                .build();
        assertThat(memory.model()).isNull();
        assertThat(memory.flushTrigger().minGap()).isEqualTo(Duration.ofMinutes(10));

        HarnessAgent.Builder builder = baseBuilder("mem-opened");
        builder.memory(memory);
        HarnessAgent agent = builder.build();
        try {
            assertThat(agent.getToolkit().getToolNames())
                    .as("打开 Harness Memory 后应含 memory_*")
                    .containsAll(HARNESS_MEMORY_TOOLS);
        } finally {
            agent.close();
        }
    }

    /** 最小可 build 的 Builder。 */
    private static HarnessAgent.Builder baseBuilder(String id) {
        Model model = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("mem-opened")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "mem-opened-model";
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
        return builder;
    }
}
