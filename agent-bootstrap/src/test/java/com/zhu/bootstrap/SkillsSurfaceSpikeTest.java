package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.HarnessSkillMiddleware;
import io.agentscope.harness.agent.middleware.SkillCuratorMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skills 落地 ①：对照 spike。
 * 钉清默认工作区 skill 面、动态 skill 默认开、disableDynamicSkills 效果、manage/curator 默认不开。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。
 */
class SkillsSurfaceSpikeTest {

    @TempDir
    Path workspace;

    /**
     * 未 disable 时：挂 HarnessSkillMiddleware；有 load_skill 类工具；skills 目录可解析；
     * 不挂 Curator。中间件未 frozen → 动态默认开。
     */
    @Test
    void defaultBuild_hasWorkspaceSkillSurface_dynamicNotFrozen() throws Exception {
        try (HarnessAgent agent = baseBuilder("skills-on").build()) {
            assertThat(hasMiddleware(agent, HarnessSkillMiddleware.class))
                    .as("默认挂 HarnessSkillMiddleware")
                    .isTrue();
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class))
                    .as("默认不挂 Curator")
                    .isFalse();
            assertThat(agent.getSkillRepositories())
                    .as("默认有 skill 仓储（工作区）")
                    .isNotEmpty();
            assertThat(loadSkillToolNames(agent.getToolkit()))
                    .as("默认注册 load_skill 类工具")
                    .isNotEmpty();
            assertThat(agent.getToolkit().getToolNames())
                    .as("默认不注册 skill_manage 类工具")
                    .noneMatch(name -> name != null && name.toLowerCase().contains("skill_manage"));
            WorkspaceManager wm = agent.getWorkspaceManager();
            assertThat(wm.getSkillsDir())
                    .as("工作区 skills 目录可解析，可空")
                    .isNotNull();
            assertThat(skillMiddleware(agent).isFrozen())
                    .as("未 disableDynamicSkills 时中间件未 frozen（动态默认开）")
                    .isFalse();
        }
    }

    /**
     * disableDynamicSkills 后仍保留工作区 skill 中间件与 load 工具；中间件 frozen。
     */
    @Test
    void disableDynamicSkills_keepsWorkspaceSurface_freezesMiddleware() throws Exception {
        try (HarnessAgent agent = baseBuilder("skills-dyn-off")
                .disableDynamicSkills()
                .build()) {
            assertThat(hasMiddleware(agent, HarnessSkillMiddleware.class)).isTrue();
            assertThat(skillMiddleware(agent).isFrozen())
                    .as("disableDynamicSkills 后 HarnessSkillMiddleware.frozen")
                    .isTrue();
            assertThat(agent.getSkillRepositories()).isNotEmpty();
            assertThat(loadSkillToolNames(agent.getToolkit())).isNotEmpty();
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class)).isFalse();
        }
    }

    /**
     * 对照：关掉默认工作区 skill 后仓储数量减少（本专题保持开启，只作对照）。
     */
    @Test
    void disableDefaultWorkspaceSkills_shrinksRepositories() throws Exception {
        try (HarnessAgent withDefault = baseBuilder("skills-ws-on").disableDynamicSkills().build();
                HarnessAgent withoutDefault = baseBuilder("skills-ws-off")
                        .disableDynamicSkills()
                        .disableDefaultWorkspaceSkills()
                        .build()) {
            assertThat(withDefault.getSkillRepositories().size())
                    .as("保持默认工作区 skill 时仓储非空")
                    .isGreaterThan(0);
            assertThat(withoutDefault.getSkillRepositories().size())
                    .as("disableDefaultWorkspaceSkills 后仓储数量少于开启时")
                    .isLessThan(withDefault.getSkillRepositories().size());
        }
    }

    private HarnessAgent.Builder baseBuilder(String id) {
        return HarnessAgent.builder()
                .name(id)
                .agentId(id)
                .model(stubModel())
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .disableToolResultEviction();
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

    private static HarnessSkillMiddleware skillMiddleware(HarnessAgent agent) {
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            if (middleware instanceof HarnessSkillMiddleware) {
                return (HarnessSkillMiddleware) middleware;
            }
        }
        throw new AssertionError("HarnessSkillMiddleware missing");
    }

    /** 名称含 load 与 skill 的工具（如 load_skill_through_path）。 */
    private static Set<String> loadSkillToolNames(Toolkit toolkit) {
        return toolkit.getToolNames().stream()
                .filter(name -> name != null)
                .filter(name -> {
                    String n = name.toLowerCase();
                    return n.contains("skill") && n.contains("load");
                })
                .collect(Collectors.toSet());
    }

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("skills-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "skills-spike-model";
            }
        };
    }
}
