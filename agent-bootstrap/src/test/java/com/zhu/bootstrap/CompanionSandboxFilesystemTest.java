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

    /** companion Binding 源码须接线；echo 不得调用 CompanionSandboxFilesystem。 */
    @Test
    void companionWiresSandboxEchoDoesNot() throws Exception {
        String companion = readModuleSource("agent-products/agent-product-companion",
                "com", "zhu", "product", "companion", "binding", "CompanionResidentAgentBinding.java");
        String factory = readModuleSource("agent-runtime",
                "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java");
        int echoStart = factory.indexOf("public void customizeEcho");
        assertThat(echoStart).isGreaterThanOrEqualTo(0);
        String echo = factory.substring(echoStart);
        assertThat(companion)
                .as("companion Binding 须走 companion 文件系统装配")
                .contains("applyCompanionFilesystem")
                .contains("CompanionSandboxFilesystem.createWhenEnabledOrNull");
        assertThat(echo)
                .as("customizeEcho 不得挂 Docker Sandbox")
                .doesNotContain("CompanionSandboxFilesystem")
                .doesNotContain("DockerFilesystemSpec")
                .doesNotContain("applyCompanionFilesystem")
                .contains("WorkspaceFilesystem.userScoped()");
        assertThat(factory)
                .contains("builder.disableShellTool()");
        assertThat(HarnessAgentFactory.ECHO_AGENT_ID).isEqualTo("echo-v1");
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

    /** 最小 Store 桩：只满足非 null，不碰 JDBC。 */
    private static DistributedStore mockStore() {
        return DistributedStore.builder()
                .agentStateStore(mock(AgentStateStore.class))
                .baseStore(mock(BaseStore.class))
                .build();
    }
}
