package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.SkillCuratorMiddleware;
import io.agentscope.harness.agent.middleware.SkillUsageMiddleware;
import io.agentscope.harness.agent.tool.ProposeSkillTool;
import io.agentscope.harness.agent.tool.SkillManageTool;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skills Curator/manage 落地 ②：工厂默认关接线。
 * 源码不调 enableSkillManageTool / enableSkillCurator；行为面与工程动态关形状一致。
 * 见 docs/skills-curator-落地.md。
 */
class SkillsCuratorDefaultOffWiringTest {

    @TempDir
    Path workspace;

    /**
     * 源码约定：configureCommon / companion Binding / customizeEcho 均不调用 manage/Curator 开启 API。
     */
    @Test
    void factorySource_doesNotEnableManageOrCurator() throws Exception {
        String factory = readModuleSource("agent-runtime",
                "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java");
        String companion = readModuleSource("agent-products/agent-product-companion",
                "com", "zhu", "product", "companion", "binding", "CompanionResidentAgentBinding.java");
        int commonStart = factory.indexOf("public void configureCommon");
        int echoStart = factory.indexOf("public void customizeEcho", commonStart);
        assertThat(commonStart).isGreaterThanOrEqualTo(0);
        assertThat(echoStart).isGreaterThan(commonStart);

        String common = factory.substring(commonStart, echoStart);
        String echo = factory.substring(echoStart);

        assertNoEnableManageCurator(common, "configureCommon");
        assertNoEnableManageCurator(companion, "CompanionResidentAgentBinding");
        assertNoEnableManageCurator(echo, "customizeEcho");
        assertThat(common)
                .as("configureCommon 仍保持 disableDynamicSkills")
                .contains("disableDynamicSkills()");
    }

    /**
     * 行为：与工厂公共项一致（动态关、不调 enable*）时无 manage/Curator 面。
     */
    @Test
    void factoryShape_dynamicOff_noManageNoCuratorSurface() throws Exception {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("curator-wired-off")
                .agentId("curator-wired-off")
                .model(stubModel())
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .disableToolResultEviction()
                .disableDynamicSkills()
                .build()) {
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class)).isFalse();
            assertThat(hasMiddleware(agent, SkillUsageMiddleware.class)).isFalse();
            Set<String> names = Set.copyOf(agent.getToolkit().getToolNames());
            assertThat(names).doesNotContain(SkillManageTool.NAME, ProposeSkillTool.NAME);
            assertThat(agent.runCuratorOnce().block()).isNull();
        }
    }

    /**
     * 只禁调用形态（.enableXxx(），注释里可写方法名说明。
     */
    private static void assertNoEnableManageCurator(String block, String label) {
        assertThat(block)
                .as("%s 不得调用 enableSkillManageTool", label)
                .doesNotContain(".enableSkillManageTool(");
        assertThat(block)
                .as("%s 不得调用 enableSkillCurator", label)
                .doesNotContain(".enableSkillCurator(");
        assertThat(block)
                .as("%s 不得调用 enableSkillPromotionGate", label)
                .doesNotContain(".enableSkillPromotionGate(");
    }

    private static String readModuleSource(String moduleRelative, String... parts) throws Exception {
        Path path = Path.of("..").resolve(moduleRelative).resolve("src").resolve("main").resolve("java");
        for (String part : parts) {
            path = path.resolve(part);
        }
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            path = Path.of(moduleRelative).resolve("src").resolve("main").resolve("java");
            for (String part : parts) {
                path = path.resolve(part);
            }
            path = path.toAbsolutePath().normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static boolean hasMiddleware(HarnessAgent agent, Class<?> type) {
        List<MiddlewareBase> middlewares = agent.getDelegate().getMiddlewares();
        if (middlewares == null) {
            return false;
        }
        for (MiddlewareBase middleware : middlewares) {
            if (type.isInstance(middleware)) {
                return true;
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
                        .id("curator-wired")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "curator-wired-model";
            }
        };
    }
}
