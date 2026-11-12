package com.zhu.bootstrap;

import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.CompanionSandboxFilesystem;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Sandbox 落地 ②：companion Docker 规格与硬失败；不连真 Docker / 中间件。
 */
class CompanionSandboxFilesystemTest {

    /** 默认关返回 null，调用方继续 Remote。 */
    @Test
    void disabledYieldsNull() {
        assertThat(CompanionSandboxFilesystem.createWhenEnabledOrNull(null, mockStore())).isNull();
        AgentRuntimeProperties.Sandbox sandbox = new AgentRuntimeProperties.Sandbox();
        sandbox.setEnabled(false);
        assertThat(CompanionSandboxFilesystem.createWhenEnabledOrNull(sandbox, mockStore())).isNull();
    }

    /** enabled 但无 Store → 硬失败，文案禁止 Remote 回退。 */
    @Test
    void enabledWithoutStoreHardFails() {
        AgentRuntimeProperties.Sandbox sandbox = new AgentRuntimeProperties.Sandbox();
        sandbox.setEnabled(true);
        assertThatThrownBy(() -> CompanionSandboxFilesystem.createWhenEnabledOrNull(sandbox, null, () -> {
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DistributedStore")
                .hasMessageContaining("refusing Remote fallback");
    }

    /** Docker 探测失败 → 硬失败，不返回 Remote 规格。 */
    @Test
    void enabledWhenDockerProbeFailsHardFails() {
        AgentRuntimeProperties.Sandbox sandbox = new AgentRuntimeProperties.Sandbox();
        sandbox.setEnabled(true);
        DistributedStore store = mockStore();
        assertThatThrownBy(() -> CompanionSandboxFilesystem.createWhenEnabledOrNull(
                        sandbox, store, () -> {
                            throw new IllegalStateException(
                                    "sandbox.enabled=true but docker unavailable: exit=1");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker unavailable");
    }

    /** 探测通过：DockerFilesystemSpec + USER；空 image 用默认 tag。 */
    @Test
    void enabledBuildsDockerUserScopedWithDefaultImage() {
        AgentRuntimeProperties.Sandbox sandbox = new AgentRuntimeProperties.Sandbox();
        sandbox.setEnabled(true);
        sandbox.setImage("");
        SandboxFilesystemSpec spec = CompanionSandboxFilesystem.createWhenEnabledOrNull(
                sandbox, mockStore(), () -> {
                });
        assertThat(spec).isInstanceOf(DockerFilesystemSpec.class);
        assertThat(spec.getIsolationScope()).isEqualTo(IsolationScope.USER);
        assertThat(CompanionSandboxFilesystem.resolveImage("")).isEqualTo("ubuntu:24.04");
        assertThat(CompanionSandboxFilesystem.resolveImage(" custom:tag ")).isEqualTo("custom:tag");
    }

    /** companion 源码须接线；echo 不得调用 CompanionSandboxFilesystem。 */
    @Test
    void companionWiresSandboxEchoDoesNot() throws Exception {
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
        int companionStart = source.indexOf("public void customizeCompanion");
        int echoStart = source.indexOf("public void customizeEcho", companionStart);
        // echo 方法体止于其后的 private applyCompanionFilesystem，勿把 companion 私有方法算进 echo。
        int echoEnd = source.indexOf("private void applyCompanionFilesystem", echoStart);
        assertThat(companionStart).isGreaterThanOrEqualTo(0);
        assertThat(echoStart).isGreaterThan(companionStart);
        assertThat(echoEnd).isGreaterThan(echoStart);

        String companion = source.substring(companionStart, echoStart);
        String echo = source.substring(echoStart, echoEnd);
        assertThat(companion)
                .as("customizeCompanion 须走 companion 文件系统装配")
                .contains("applyCompanionFilesystem");
        assertThat(echo)
                .as("customizeEcho 不得挂 Docker Sandbox")
                .doesNotContain("CompanionSandboxFilesystem")
                .doesNotContain("DockerFilesystemSpec")
                .doesNotContain("applyCompanionFilesystem")
                .contains("applyRemoteFilesystemIfStorePresent");
        // 工厂须有 Docker 接线与 shell 关闭。
        assertThat(source)
                .contains("CompanionSandboxFilesystem.createWhenEnabledOrNull")
                .contains("builder.disableShellTool()");
        assertThat(HarnessAgentFactory.ECHO_AGENT_ID).isEqualTo("echo-v1");
    }

    /** 最小 Store 桩：只满足非 null，不碰 JDBC。 */
    private static DistributedStore mockStore() {
        return DistributedStore.builder()
                .agentStateStore(mock(AgentStateStore.class))
                .baseStore(mock(BaseStore.class))
                .build();
    }
}
