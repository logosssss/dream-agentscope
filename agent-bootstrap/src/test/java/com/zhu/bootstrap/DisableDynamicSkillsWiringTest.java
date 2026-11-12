package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.HarnessSkillMiddleware;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skills 落地 ②：工厂 configureCommon 显式 disableDynamicSkills；
 * 工作区默认 skill 仍开（中间件 + load 工具 + 仓储），中间件 frozen。
 */
class DisableDynamicSkillsWiringTest {

    @TempDir
    Path workspace;

    /**
     * 源码约定：configureCommon 必须调用 disableDynamicSkills，且不调用 disableDefaultWorkspaceSkills。
     */
    @Test
    void factorySource_disablesDynamic_keepsDefaultWorkspaceSkills() throws Exception {
        Path factory = Path.of("..", "agent-runtime", "src", "main", "java",
                        "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(factory)) {
            factory = Path.of("agent-runtime", "src", "main", "java",
                            "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                    .toAbsolutePath()
                    .normalize();
        }
        String source = Files.readString(factory, StandardCharsets.UTF_8);
        int commonStart = source.indexOf("public void configureCommon");
        assertThat(commonStart).isGreaterThanOrEqualTo(0);
        int commonEnd = source.indexOf("public void customizeCompanion", commonStart);
        assertThat(commonEnd).isGreaterThan(commonStart);
        String common = source.substring(commonStart, commonEnd);
        assertThat(common)
                .as("configureCommon 须 disableDynamicSkills")
                .contains("disableDynamicSkills()");
        assertThat(common)
                .as("本专题不关工作区默认 skill")
                .doesNotContain("disableDefaultWorkspaceSkills()");
    }

    /**
     * 行为：与工厂公共项一致关掉动态 skill 后，中间件 frozen，仓储与 load_skill 仍在。
     */
    @Test
    void disableDynamicSkills_freezesButKeepsWorkspaceSurface() throws Exception {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("skills-wired")
                .agentId("skills-wired")
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
            HarnessSkillMiddleware skill = skillMiddleware(agent);
            assertThat(skill.isFrozen())
                    .as("disableDynamicSkills 后 frozen")
                    .isTrue();
            assertThat(agent.getSkillRepositories()).isNotEmpty();
            assertThat(agent.getToolkit().getToolNames())
                    .anyMatch(name -> name != null
                            && name.toLowerCase().contains("skill")
                            && name.toLowerCase().contains("load"));
            assertThat(agent.getWorkspaceManager().getSkillsDir()).isNotNull();
        }
    }

    private static HarnessSkillMiddleware skillMiddleware(HarnessAgent agent) {
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            if (middleware instanceof HarnessSkillMiddleware) {
                return (HarnessSkillMiddleware) middleware;
            }
        }
        throw new AssertionError("HarnessSkillMiddleware missing");
    }

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("skills-wired")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "skills-wired-model";
            }
        };
    }
}
