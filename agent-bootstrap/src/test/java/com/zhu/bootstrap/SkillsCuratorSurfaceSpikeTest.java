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
import io.agentscope.harness.agent.middleware.SkillCuratorMiddleware;
import io.agentscope.harness.agent.middleware.SkillUsageMiddleware;
import io.agentscope.harness.agent.skill.curator.SkillCuratorConfig;
import io.agentscope.harness.agent.tool.ProposeSkillTool;
import io.agentscope.harness.agent.tool.SkillManageConfig;
import io.agentscope.harness.agent.tool.SkillManageTool;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skills Curator/manage 落地 ①：对照 spike。
 * 钉清：框架默认不开 manage/Curator；与 disableDynamicSkills 正交；显式 enable 后的面。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。见 docs/skills-curator-落地.md。
 */
class SkillsCuratorSurfaceSpikeTest {

    @TempDir
    Path workspace;

    /**
     * 动态开（框架默认）：不挂 Curator；无 skill_manage / propose_skill。
     * 开启必须显式 enable*，没有对称的 disableManage/Curator API。
     */
    @Test
    void dynamicOn_default_noManageNoCurator() throws Exception {
        try (HarnessAgent agent = baseBuilder("curator-dyn-on").build()) {
            assertManageCuratorAbsent(agent, "动态开默认");
        }
    }

    /**
     * 动态关（当前工程主路径形状）：manage/Curator 仍默认不开。
     * 与 disableDynamicSkills 正交——关动态不会「顺便」打开或关闭本面（本面本就关）。
     */
    @Test
    void dynamicOff_currentShape_noManageNoCurator() throws Exception {
        try (HarnessAgent agent = baseBuilder("curator-dyn-off")
                .disableDynamicSkills()
                .build()) {
            assertManageCuratorAbsent(agent, "动态关（工程形状）");
        }
    }

    /**
     * 对照：显式 enableSkillManageTool(true) 注册 manage + propose，并挂 SkillUsageMiddleware。
     * 仅开 manage 不挂 Curator。本专题不接线开启。
     */
    @Test
    void enableSkillManageTool_dynamicOn_registersManageAndUsage_notCurator() throws Exception {
        try (HarnessAgent agent = baseBuilder("curator-manage-on")
                .enableSkillManageTool(true)
                .build()) {
            assertThat(toolNames(agent.getToolkit()))
                    .as("enableSkillManageTool(true) 注册 skill_manage 与 propose_skill")
                    .contains(SkillManageTool.NAME, ProposeSkillTool.NAME);
            assertThat(hasMiddleware(agent, SkillUsageMiddleware.class))
                    .as("开 manage 时挂 SkillUsageMiddleware")
                    .isTrue();
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class))
                    .as("仅开 manage 不自动挂 Curator 中间件")
                    .isFalse();
            assertThat(agent.runCuratorOnce().block())
                    .as("未 enableSkillCurator 时 runCuratorOnce 为空")
                    .isNull();
        }
    }

    /**
     * 对照：动态关 + enableSkillManageTool 仍可注册工具（与 disableDynamicSkills 正交）。
     */
    @Test
    void enableSkillManageTool_dynamicOff_stillRegisters() throws Exception {
        try (HarnessAgent agent = baseBuilder("curator-manage-dyn-off")
                .disableDynamicSkills()
                .enableSkillManageTool(SkillManageConfig.defaults())
                .build()) {
            assertThat(toolNames(agent.getToolkit()))
                    .contains(SkillManageTool.NAME, ProposeSkillTool.NAME);
        }
    }

    /**
     * 关键发现：单独 enableSkillCurator 不够——Curator 装配嵌在 skillManage 开启分支内。
     * 只调 Curator、不开 manage → 无中间件、runCuratorOnce 空。
     */
    @Test
    void enableSkillCurator_alone_doesNotInstall() throws Exception {
        assertThat(SkillCuratorConfig.defaults().enabled())
                .as("SkillCuratorConfig.defaults().enabled 默认为 true（调用 enable 时才有意义）")
                .isTrue();
        try (HarnessAgent agent = baseBuilder("curator-alone")
                .enableSkillCurator(SkillCuratorConfig.defaults())
                .build()) {
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class))
                    .as("单独 enableSkillCurator 不挂 SkillCuratorMiddleware")
                    .isFalse();
            assertThat(toolNames(agent.getToolkit()))
                    .doesNotContain(SkillManageTool.NAME);
            assertThat(agent.runCuratorOnce().block()).isNull();
        }
    }

    /**
     * 对照：manage + Curator 同时显式开，才挂 SkillCuratorMiddleware。
     * 动态关下同样成立。
     */
    @Test
    void enableManageAndCurator_dynamicOff_installsCuratorMiddleware() throws Exception {
        try (HarnessAgent agent = baseBuilder("curator-manage-both")
                .disableDynamicSkills()
                .enableSkillManageTool(SkillManageConfig.defaults())
                .enableSkillCurator(SkillCuratorConfig.defaults())
                .build()) {
            assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class))
                    .as("manage + Curator 同时开才挂 SkillCuratorMiddleware")
                    .isTrue();
            assertThat(hasMiddleware(agent, SkillUsageMiddleware.class)).isTrue();
            assertThat(toolNames(agent.getToolkit()))
                    .contains(SkillManageTool.NAME, ProposeSkillTool.NAME);
            assertThat(agent.runCuratorOnce().block())
                    .as("Curator 已装配时 runCuratorOnce 有报告对象")
                    .isNotNull();
        }
    }

    private void assertManageCuratorAbsent(HarnessAgent agent, String label) {
        assertThat(hasMiddleware(agent, SkillCuratorMiddleware.class))
                .as("%s：不挂 SkillCuratorMiddleware", label)
                .isFalse();
        Set<String> names = toolNames(agent.getToolkit());
        assertThat(names)
                .as("%s：无 skill_manage", label)
                .doesNotContain(SkillManageTool.NAME);
        assertThat(names)
                .as("%s：无 propose_skill", label)
                .doesNotContain(ProposeSkillTool.NAME);
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

    private static Set<String> toolNames(Toolkit toolkit) {
        return Set.copyOf(toolkit.getToolNames());
    }

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("skills-curator-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "skills-curator-spike-model";
            }
        };
    }
}
