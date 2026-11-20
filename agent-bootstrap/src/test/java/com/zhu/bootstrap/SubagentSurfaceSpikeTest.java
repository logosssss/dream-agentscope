package com.zhu.bootstrap;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.DynamicSubagentsMiddleware;
import io.agentscope.harness.agent.middleware.SubagentsMiddleware;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Subagent 落地 ①：对照 spike。
 * 钉清默认动态面、disableDynamicSubagents、静态 subagent-probe 可召回。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。
 */
class SubagentSurfaceSpikeTest {

    private static final String PROBE = "subagent-probe";

    @TempDir
    Path workspace;

    /**
     * 未 disable、零静态声明：挂 DynamicSubagentsMiddleware；有 agent_* 工具；subagents=true。
     * （无静态条目时不一定挂 SubagentsMiddleware，以实测为准。）
     */
    @Test
    void defaultBuild_hasDynamicSubagentSurface() throws Exception {
        try (HarnessAgent agent = baseBuilder("sub-on").build()) {
            assertThat(hasMiddleware(agent, DynamicSubagentsMiddleware.class))
                    .as("默认挂 DynamicSubagentsMiddleware（动态开）")
                    .isTrue();
            assertThat(agentSpawnToolNames(agent))
                    .as("默认注册 agent_spawn / agent_send 类工具")
                    .isNotEmpty();
            assertThat(agent.getSubagentAgentManager())
                    .as("默认有 Subagent AgentManager")
                    .isNotNull();
        }
    }

    /**
     * 只关动态：Dynamic 中间件消失；零声明时无探针；agent_* 工具与静态中间件仍可存在。
     */
    @Test
    void disableDynamicSubagents_removesDynamicMiddleware() throws Exception {
        try (HarnessAgent agent = baseBuilder("sub-dyn-off").disableDynamicSubagents().build()) {
            assertThat(hasMiddleware(agent, DynamicSubagentsMiddleware.class))
                    .as("disableDynamicSubagents 后无 DynamicSubagentsMiddleware")
                    .isFalse();
            assertThat(agentSpawnToolNames(agent))
                    .as("关动态后静态面仍可保留 agent_* 工具")
                    .isNotEmpty();
            assertThat(agent.getSubagentAgentManager().hasAgent(PROBE))
                    .as("未声明时没有 subagent-probe")
                    .isFalse();
        }
    }

    /**
     * 关动态 + 静态声明：manager 可召回探针；声明面为 ISOLATED / 继承权限 / 零工具 / 不持久会话。
     */
    @Test
    void staticProbe_recallableAfterDynamicDisabled() throws Exception {
        SubagentDeclaration probe = probeDeclaration();
        try (HarnessAgent agent = baseBuilder("sub-probe")
                .disableDynamicSubagents()
                .subagent(probe)
                .build()) {
            assertThat(hasMiddleware(agent, DynamicSubagentsMiddleware.class)).isFalse();
            DefaultAgentManager manager = agent.getSubagentAgentManager();
            assertThat(manager.hasAgent(PROBE))
                    .as("静态 subagent-probe 可被 AgentManager 召回")
                    .isTrue();
            Optional<SubagentDeclaration> found = manager.getDeclaration(PROBE);
            assertThat(found).isPresent();
            SubagentDeclaration decl = found.get();
            assertThat(decl.getWorkspaceMode()).isEqualTo(WorkspaceMode.ISOLATED);
            assertThat(decl.isInheritParentPermissions()).isTrue();
            assertThat(decl.isPersistSession()).isFalse();
            assertThat(decl.getTools() == null || decl.getTools().isEmpty())
                    .as("零工具：tools 空或 null")
                    .isTrue();
            assertThat(decl.isRemote()).isFalse();
        }
    }

    /**
     * 对照：disableSubagents 整面关掉后无 Subagents / Dynamic 中间件（本专题不用此开关）。
     */
    @Test
    void disableSubagents_removesWholeSurface() throws Exception {
        try (HarnessAgent agent = baseBuilder("sub-all-off")
                .subagent(probeDeclaration())
                .disableSubagents()
                .build()) {
            assertThat(hasMiddleware(agent, SubagentsMiddleware.class)).isFalse();
            assertThat(hasMiddleware(agent, DynamicSubagentsMiddleware.class)).isFalse();
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
                .disableToolResultEviction()
                .disableDynamicSkills();
    }

    /** 与专题锁定一致的探针声明（spike 内临时构造，不改工厂）。 */
    private static SubagentDeclaration probeDeclaration() {
        return SubagentDeclaration.builder()
                .name(PROBE)
                .description("对照用占位子代理，不承诺产品能力")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .inheritParentPermissions(true)
                .persistSession(false)
                .mode(SubagentDeclaration.Mode.SUBAGENT)
                .tools(List.of())
                .inlineAgentsBody("你是对照用占位子代理。用一两句中文直接短答，不要调用工具。")
                .build();
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

    private static Set<String> agentSpawnToolNames(HarnessAgent agent) {
        return agent.getToolkit().getToolNames().stream()
                .filter(name -> name != null)
                .filter(name -> {
                    String n = name.toLowerCase();
                    return n.startsWith("agent_") || n.contains("spawn");
                })
                .collect(Collectors.toSet());
    }

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("subagent-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "subagent-spike-model";
            }
        };
    }
}
