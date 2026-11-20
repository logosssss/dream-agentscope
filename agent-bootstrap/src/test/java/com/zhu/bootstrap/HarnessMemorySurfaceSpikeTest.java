package com.zhu.bootstrap;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.middleware.MemoryFlushMiddleware;
import io.agentscope.harness.agent.tool.MemoryGetTool;
import io.agentscope.harness.agent.tool.MemorySaveTool;
import io.agentscope.harness.agent.tool.MemorySearchTool;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Memory 落地 ③：Harness Memory 面 vs 情景语义 spike。
 * 钉清存哪里、是否调模型、与 Companion 情景表/向量/画像能否映射。
 * 不改 Gateway / Companion 主路径，不连 Redis/MySQL/Milvus。
 */
class HarnessMemorySurfaceSpikeTest {

    @TempDir
    Path workspace;

    /**
     * MemorySave/Get/Search 走工作区文件（MEMORY.md），不是情景表。
     * RuntimeContext 只影响工作区相对路径解析，不写 tenant 列。
     */
    @Test
    void memoryTools_writeAndReadWorkspaceFiles() throws Exception {
        try (WorkspaceManager manager = new WorkspaceManager(workspace)) {
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId("user-a")
                    .sessionId("session-a")
                    .put("tenantId", "tenant-a")
                    .build();
            MemorySaveTool save = new MemorySaveTool(manager);
            MemoryGetTool get = new MemoryGetTool(manager);
            MemorySearchTool search = new MemorySearchTool(manager);

            String saved = save.memorySave(ctx, "喜欢散步");
            assertThat(saved).doesNotContain("Error");

            Path memoryMd = workspace.resolve("MEMORY.md");
            assertThat(memoryMd).exists();
            String content = Files.readString(memoryMd, StandardCharsets.UTF_8);
            assertThat(content).contains("喜欢散步");

            String got = get.memoryGet(ctx, "MEMORY.md", 1, 50);
            assertThat(got).contains("喜欢散步");

            String hit = search.memorySearch(ctx, "散步");
            assertThat(hit).contains("散步");
        }
    }

    /**
     * defaults() 不带 Model；Builder 挂 flush/consolidation 必须显式给模型。
     * 与产品「摘要不调对话模型」冲突。
     */
    @Test
    void memoryConfig_defaultsHaveNoModel_flushNeedsModel() {
        MemoryConfig defaults = MemoryConfig.defaults();
        assertThat(defaults.model())
                .as("defaults 不应自带对话模型")
                .isNull();

        assertThat(hasModelCtor(MemoryFlushMiddleware.class))
                .as("MemoryFlushMiddleware 构造依赖 Model")
                .isTrue();
        assertThat(hasModelCtor(MemoryConsolidator.class))
                .as("MemoryConsolidator 构造依赖 Model")
                .isTrue();

        // 能构造，证明 API 强制要 Model；不真正跑 flush（会调模型）。
        Model model = mock(Model.class);
        try (WorkspaceManager manager = new WorkspaceManager(workspace)) {
            MemoryFlushMiddleware flush = new MemoryFlushMiddleware(manager, model);
            assertThat(flush).isNotNull();
            MemoryConsolidator consolidator = new MemoryConsolidator(manager, model);
            assertThat(consolidator).isNotNull();
        }
    }

    /**
     * 与 Companion 语义对照表（钉在断言注释里，供落地文档引用）：
     * Harness = 工作区 md + 可选 LLM flush；Companion = MySQL/向量/画像 + 截断用户句、危机短路。
     * 本块结论：不对齐，不能把情景权威迁进 Harness Memory。
     */
    @Test
    void surface_doesNotMapToEpisodicTables() {
        // 工具类只依赖 WorkspaceManager，构造参数里没有仓储接口。
        assertThat(MemorySaveTool.class.getConstructors()[0].getParameterTypes())
                .containsExactly(WorkspaceManager.class);
        assertThat(MemoryGetTool.class.getConstructors()[0].getParameterTypes())
                .containsExactly(WorkspaceManager.class);
        assertThat(MemorySearchTool.class.getConstructors()[0].getParameterTypes())
                .containsExactly(WorkspaceManager.class);

        // 检索是工作区关键字，不是向量近邻。
        assertThat(Arrays.stream(MemorySearchTool.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("memorySearch")))
                .isTrue();
    }

    /** 任一公开构造函数参数含 Model 即视为依赖对话模型。 */
    private static boolean hasModelCtor(Class<?> type) {
        for (Constructor<?> ctor : type.getConstructors()) {
            for (Class<?> param : ctor.getParameterTypes()) {
                if (Model.class.isAssignableFrom(param)) {
                    return true;
                }
            }
        }
        return false;
    }
}
