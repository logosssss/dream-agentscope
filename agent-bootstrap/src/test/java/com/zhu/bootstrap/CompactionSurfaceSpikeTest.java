package com.zhu.bootstrap;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryFlushManager;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ConversationCompactor;
import io.agentscope.harness.agent.middleware.CompactionMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compaction 落地 ①：对照 spike。
 * 钉清默认 {@link CompactionConfig}、压前 flush 依赖 {@link MemoryFlushManager}、摘要要 Model。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。
 */
class CompactionSurfaceSpikeTest {

    @TempDir
    Path workspace;

    /**
     * 框架默认：压前 flush/offload 打开；不配独立摘要模型；条数触发约 50/保留 20。
     */
    @Test
    void defaultConfig_flushBeforeCompactAndNoDedicatedModel() {
        CompactionConfig config = CompactionConfig.builder().build();
        assertThat(config.isFlushBeforeCompact())
                .as("默认压前 flush，CRITICAL 不能只靠 Memory 闸")
                .isTrue();
        assertThat(config.isOffloadBeforeCompact()).isTrue();
        assertThat(config.getModel())
                .as("不配 model 时回落 Agent 对话模型")
                .isNull();
        assertThat(config.getTriggerMessages()).isEqualTo(50);
        assertThat(config.getKeepMessages()).isEqualTo(20);
        assertThat(config.getTriggerTokens()).isZero();
    }

    /**
     * 结构对照：Compactor 持有 MemoryFlushManager；Middleware 构造要 Workspace + Model + Config。
     * 压前 flush 不经产品 CrisisGatedMemoryFlushMiddleware。
     */
    @Test
    void compactPath_dependsOnMemoryFlushManagerAndModel() {
        assertThat(hasParam(ConversationCompactor.class, MemoryFlushManager.class))
                .as("ConversationCompactor 依赖 MemoryFlushManager（压前 flush/offload）")
                .isTrue();
        assertThat(hasParam(ConversationCompactor.class, Model.class)).isTrue();
        assertThat(hasParam(CompactionMiddleware.class, WorkspaceManager.class)).isTrue();
        assertThat(hasParam(CompactionMiddleware.class, Model.class)).isTrue();
        assertThat(hasParam(CompactionMiddleware.class, CompactionConfig.class)).isTrue();
        assertThat(hasParam(MemoryFlushManager.class, WorkspaceManager.class)).isTrue();
        assertThat(hasParam(MemoryFlushManager.class, Model.class)).isTrue();
    }

    /**
     * disableCompaction 后无 compactionHook；compaction(config) 后有 hook。
     * 落地 ② 走 disable + 产品闸时，紧急溢出路径会失去 hook（已知限制）。
     */
    @Test
    void disableCompaction_clearsHook_enableInstallsHook() throws Exception {
        Model model = stubModel(new AtomicInteger());
        try (HarnessAgent disabled = HarnessAgent.builder()
                .name("compact-off")
                .agentId("compact-off")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableCompaction()
                .disableWebTools()
                .disableShellTool()
                .build()) {
            assertThat(disabled.getCompactionHook())
                    .as("disableCompaction 后紧急溢出无 hook")
                    .isNull();
        }
        try (HarnessAgent enabled = HarnessAgent.builder()
                .name("compact-on")
                .agentId("compact-on")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .compaction(CompactionConfig.builder().build())
                .disableWebTools()
                .disableShellTool()
                .build()) {
            assertThat(enabled.getCompactionHook())
                    .as("显式 compaction(config) 才安装框架 hook")
                    .isNotNull();
        }
    }

    /**
     * 条数触发时 compactIfNeeded 会调模型（摘要；压前 flush 也可能调）。
     * 证明 Compaction 与 Memory 一样是 LLM 路径，危机轮不应进入。
     */
    @Test
    void compactIfNeeded_invokesModel_whenTriggered() throws Exception {
        AtomicInteger modelCalls = new AtomicInteger();
        Model model = stubModel(modelCalls);
        CompactionConfig config = CompactionConfig.builder()
                .triggerMessages(3)
                .keepMessages(1)
                .flushBeforeCompact(true)
                .build();
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text("msg-" + i).build())
                    .build());
        }
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("u-spike")
                .sessionId("s-spike")
                .build();
        try (WorkspaceManager workspaceManager = new WorkspaceManager(workspace)) {
            MemoryFlushManager flushManager = new MemoryFlushManager(workspaceManager, model);
            ConversationCompactor compactor = new ConversationCompactor(model, flushManager);
            Optional<List<Msg>> compacted = compactor
                    .compactIfNeeded(ctx, messages, config, "agent", "session")
                    .block(Duration.ofSeconds(15));
            assertThat(compacted).isNotNull();
            assertThat(modelCalls.get())
                    .as("触发 compact 后至少一次模型调用（flush 摘要和/或 compact 摘要）")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    /** 任一公开构造函数参数含指定类型。 */
    private static boolean hasParam(Class<?> type, Class<?> needle) {
        for (Constructor<?> ctor : type.getConstructors()) {
            for (Class<?> param : ctor.getParameterTypes()) {
                if (needle.isAssignableFrom(param)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 计数 stub：每次 stream 记一次，回固定摘要文本。 */
    private static Model stubModel(AtomicInteger calls) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                calls.incrementAndGet();
                return Flux.just(ChatResponse.builder()
                        .id("compact-spike")
                        .content(List.of(TextBlock.builder().text("SESSION INTENT\nspike\n").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "compact-spike-model";
            }
        };
    }
}
