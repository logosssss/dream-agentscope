package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.DynamicSubagentsMiddleware;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subagent 落地 ②：工厂 configureCommon 关动态；customizeCompanion 挂探针；echo 源码不挂。
 */
class DisableDynamicSubagentsWiringTest {

    @TempDir
    Path workspace;

    /**
     * 源码约定：common 必须 disableDynamicSubagents；companion 挂 subagent；echo 段不出现 subagent(。
     */
    @Test
    void factorySource_disablesDynamic_companionDeclaresProbe_echoDoesNot() throws Exception {
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
        int companionStart = source.indexOf("public void customizeCompanion", commonStart);
        int echoStart = source.indexOf("public void customizeEcho", companionStart);
        assertThat(commonStart).isGreaterThanOrEqualTo(0);
        assertThat(companionStart).isGreaterThan(commonStart);
        assertThat(echoStart).isGreaterThan(companionStart);
        String common = source.substring(commonStart, companionStart);
        String companion = source.substring(companionStart, echoStart);
        String echo = source.substring(echoStart, source.indexOf("public void attachCompanionTools", echoStart));
        assertThat(common)
                .as("configureCommon 须 disableDynamicSubagents")
                .contains("disableDynamicSubagents()");
        assertThat(common)
                .as("本专题不用整面 disableSubagents")
                .doesNotContain("disableSubagents()");
        assertThat(companion)
                .as("customizeCompanion 须挂静态探针")
                .contains("subagent(subagentProbe())");
        assertThat(echo)
                .as("customizeEcho 不挂静态子代理")
                .doesNotContain("subagent(");
    }

    /**
     * 行为：与工厂约定一致关动态并挂探针后，无 Dynamic 中间件，Manager 可召回探针声明面。
     */
    @Test
    void wiredLikeFactory_probeRecallableWithoutDynamicMiddleware() throws Exception {
        try (HarnessAgent agent = HarnessAgent.builder()
                .name("sub-wired")
                .agentId("sub-wired")
                .model(stubModel())
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .disableCompaction()
                .disableToolResultEviction()
                .disableDynamicSkills()
                .disableDynamicSubagents()
                .subagent(HarnessAgentFactory.subagentProbe())
                .build()) {
            assertThat(hasMiddleware(agent, DynamicSubagentsMiddleware.class))
                    .as("关动态后无 DynamicSubagentsMiddleware")
                    .isFalse();
            assertThat(agent.getSubagentAgentManager().hasAgent(HarnessAgentFactory.SUBAGENT_PROBE_NAME))
                    .isTrue();
            SubagentDeclaration decl = agent.getSubagentAgentManager()
                    .getDeclaration(HarnessAgentFactory.SUBAGENT_PROBE_NAME)
                    .orElseThrow();
            assertThat(decl.getWorkspaceMode()).isEqualTo(WorkspaceMode.ISOLATED);
            assertThat(decl.isInheritParentPermissions()).isTrue();
            assertThat(decl.isPersistSession()).isFalse();
            assertThat(decl.getTools() == null || decl.getTools().isEmpty()).isTrue();
            assertThat(decl.getMode()).isEqualTo(SubagentDeclaration.Mode.SUBAGENT);
            assertThat(decl.isRemote()).isFalse();
        }
    }

    /** 工厂探针工厂方法本身满足锁定字段。 */
    @Test
    void subagentProbeFactory_matchesLock() {
        SubagentDeclaration probe = HarnessAgentFactory.subagentProbe();
        assertThat(probe.getName()).isEqualTo(HarnessAgentFactory.SUBAGENT_PROBE_NAME);
        assertThat(probe.getInlineAgentsBody()).isEqualTo(HarnessAgentFactory.SUBAGENT_PROBE_PROMPT);
        assertThat(probe.getWorkspaceMode()).isEqualTo(WorkspaceMode.ISOLATED);
        assertThat(probe.isInheritParentPermissions()).isTrue();
        assertThat(probe.isPersistSession()).isFalse();
        assertThat(probe.getTools()).isEmpty();
        assertThat(probe.getMode()).isEqualTo(SubagentDeclaration.Mode.SUBAGENT);
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
                        .id("sub-wired")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "sub-wired-model";
            }
        };
    }
}
