package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.middleware.ToolResultEvictionMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool Result Eviction 落地 ①：对照 spike。
 * 钉清 defaults、构造依赖、Builder 未 disable 时是否已挂框架 Middleware。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。
 */
class ToolResultEvictionSurfaceSpikeTest {

    @TempDir
    Path workspace;

    /**
     * defaults：80k / 2k / large_tool_results；排除文件与 memory/session 检索类工具。
     */
    @Test
    void defaults_matchLockedThresholdsAndExclusions() {
        ToolResultEvictionConfig config = ToolResultEvictionConfig.defaults();
        assertThat(config.getMaxResultChars()).isEqualTo(80_000);
        assertThat(config.getPreviewChars()).isEqualTo(2_000);
        assertThat(config.getEvictionPath()).isEqualTo("large_tool_results");
        assertThat(config.getExcludedToolNames()).containsExactlyInAnyOrder(
                "read_file",
                "write_file",
                "edit_file",
                "memory_search",
                "memory_get",
                "session_search");
    }

    /**
     * Middleware 要 AbstractFilesystem + Config。
     * 仅 {@code new WorkspaceManager(path)} 时 filesystem 可能为空；build 后的 Harness 才有可用 fs。
     */
    @Test
    void middleware_needsFilesystemAndConfig() throws Exception {
        assertThat(hasParam(ToolResultEvictionMiddleware.class, AbstractFilesystem.class)).isTrue();
        assertThat(hasParam(ToolResultEvictionMiddleware.class, ToolResultEvictionConfig.class)).isTrue();
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("evict-fs")
                .agentId("evict-fs")
                .model(stubModel())
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .disableToolResultEviction()
                .build()) {
            WorkspaceManager manager = agent.getWorkspaceManager();
            assertThat(manager.getFilesystem())
                    .as("产品闸应从 Harness WorkspaceManager 取 filesystem")
                    .isNotNull();
            ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(
                    manager.getFilesystem(), ToolResultEvictionConfig.defaults());
            assertThat(middleware).isNotNull();
        }
    }

    /**
     * Builder 构造即预置 defaults 且未 disable 时，build 后委托链上有 ToolResultEvictionMiddleware。
     * 故落地 ② 必须先 disable，再由产品危机闸按需委托——否则 CRITICAL 仍可能被框架截断落盘。
     */
    @Test
    void builderDefault_installsEvictionMiddleware_disableClearsIt() throws Exception {
        Model model = stubModel();
        try (HarnessAgent enabled = HarnessAgent.builder()
                .name("evict-on")
                .agentId("evict-on")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .build()) {
            assertThat(hasEviction(enabled))
                    .as("未 disableToolResultEviction 时框架已挂 eviction")
                    .isTrue();
        }
        try (HarnessAgent disabled = HarnessAgent.builder()
                .name("evict-off")
                .agentId("evict-off")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .disableToolResultEviction()
                .build()) {
            assertThat(hasEviction(disabled))
                    .as("disableToolResultEviction 后链上不应再有框架 eviction")
                    .isFalse();
        }
    }

    /** 委托 Agent 中间件链是否含 ToolResultEvictionMiddleware。 */
    private static boolean hasEviction(HarnessAgent agent) {
        List<MiddlewareBase> middlewares = agent.getDelegate().getMiddlewares();
        if (middlewares == null) {
            return false;
        }
        for (MiddlewareBase middleware : middlewares) {
            if (middleware instanceof ToolResultEvictionMiddleware) {
                return true;
            }
        }
        return false;
    }

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

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("evict-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "evict-spike-model";
            }
        };
    }
}
