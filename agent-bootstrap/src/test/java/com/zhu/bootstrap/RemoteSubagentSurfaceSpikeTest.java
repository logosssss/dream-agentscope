package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.RemoteAskPolicy;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

/**
 * Remote Subagent 落地 ①：声明面 spike。
 * 钉清 url→isRemote、streaming/AskPolicy 读回；与本地探针对照。
 * 不对假 url 发 HTTP，不改 HarnessAgentFactory。见 grill L/S2/D1/A1。
 */
class RemoteSubagentSurfaceSpikeTest {

    private static final String LOCAL = "subagent-probe";

    private static final String REMOTE = "remote-probe";

    /** 占位 url：仅声明面；本测不发起连接。 */
    private static final String PLACEHOLDER_URL = "http://127.0.0.1:9/agentscope-remote-spike";

    @TempDir
    Path workspace;

    /**
     * 无 url 的本地声明：isRemote=false；与现有 subagent-probe 口径一致。
     */
    @Test
    void localDeclaration_isNotRemote() {
        SubagentDeclaration local = localProbe();
        assertThat(local.isRemote()).isFalse();
        assertThat(local.getUrl()).isNull();
        // Builder 默认 RemoteAskPolicy.DENY；无 url 时 isRemote=false，策略字段仍带默认值
        assertThat(local.getRemoteAskPolicy()).isEqualTo(RemoteAskPolicy.DENY);
    }

    /**
     * 设置 url 后 isRemote=true，url / streaming / AskPolicy 可原样读回。
     */
    @Test
    void urlMarksRemote_andRemoteFieldsRoundTrip() {
        SubagentDeclaration deny = remoteProbe(RemoteAskPolicy.DENY, true);
        assertThat(deny.isRemote()).as("有 url 即为 Remote").isTrue();
        assertThat(deny.getUrl()).isEqualTo(PLACEHOLDER_URL);
        assertThat(deny.isRemoteStreaming()).isTrue();
        assertThat(deny.getRemoteAskPolicy()).isEqualTo(RemoteAskPolicy.DENY);

        SubagentDeclaration propagate = remoteProbe(RemoteAskPolicy.PROPAGATE, false);
        assertThat(propagate.isRemote()).isTrue();
        assertThat(propagate.isRemoteStreaming()).isFalse();
        assertThat(propagate.getRemoteAskPolicy()).isEqualTo(RemoteAskPolicy.PROPAGATE);
    }

    /**
     * 关动态后静态挂 Remote 声明：AgentManager 可召回，且仍标 Remote（不发起 HTTP）。
     */
    @Test
    void remoteDeclaration_recallableOnAgentManager() throws Exception {
        SubagentDeclaration remote = remoteProbe(RemoteAskPolicy.DENY, false);
        try (HarnessAgent agent = baseBuilder("remote-decl")
                .disableDynamicSubagents()
                .subagent(remote)
                .build()) {
            DefaultAgentManager manager = agent.getSubagentAgentManager();
            assertThat(manager.hasAgent(REMOTE)).isTrue();
            Optional<SubagentDeclaration> found = manager.getDeclaration(REMOTE);
            assertThat(found).isPresent();
            assertThat(found.get().isRemote()).isTrue();
            assertThat(found.get().getUrl()).isEqualTo(PLACEHOLDER_URL);
            assertThat(found.get().getRemoteAskPolicy()).isEqualTo(RemoteAskPolicy.DENY);
        }
    }

    /** 与本地专题一致的探针形状，显式不设 url。 */
    private static SubagentDeclaration localProbe() {
        return SubagentDeclaration.builder()
                .name(LOCAL)
                .description("对照用本地占位子代理")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .inheritParentPermissions(true)
                .persistSession(false)
                .mode(SubagentDeclaration.Mode.SUBAGENT)
                .tools(List.of())
                .inlineAgentsBody("本地对照探针。")
                .build();
    }

    /**
     * Remote 声明：占位 url + AskPolicy + streaming；零工具；不承诺产品能力。
     */
    private static SubagentDeclaration remoteProbe(RemoteAskPolicy askPolicy, boolean streaming) {
        return SubagentDeclaration.builder()
                .name(REMOTE)
                .description("对照用 Remote 占位声明，不发起真实调用")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .inheritParentPermissions(true)
                .persistSession(false)
                .mode(SubagentDeclaration.Mode.SUBAGENT)
                .tools(List.of())
                .url(PLACEHOLDER_URL)
                .remoteStreaming(streaming)
                .remoteAskPolicy(askPolicy)
                .build();
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

    private static Model stubModel() {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("remote-subagent-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "remote-subagent-spike-model";
            }
        };
    }
}
