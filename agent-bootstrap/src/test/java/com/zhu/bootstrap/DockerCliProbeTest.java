package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.agent.DockerCliProbe;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Docker CLI 探测硬失败语义。不启动真 docker 进程。
 */
class DockerCliProbeTest {

    /** 退出码 0 视为可用。 */
    @Test
    void zeroExitPasses() {
        assertThatCode(() -> DockerCliProbe.ensureAvailable((command, detail) -> {
                    assertCommandIsDockerVersion(command);
                    return 0;
                }))
                .doesNotThrowAnyException();
    }

    /** 非 0 退出硬失败。 */
    @Test
    void nonZeroExitHardFails() {
        assertThatThrownBy(() -> DockerCliProbe.ensureAvailable((command, detail) -> {
                    detail.append(" detail=fake-stderr");
                    return 127;
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker unavailable")
                .hasMessageContaining("exit=127");
    }

    /** runner 抛错同样硬失败。 */
    @Test
    void runnerExceptionHardFails() {
        assertThatThrownBy(() -> DockerCliProbe.ensureAvailable((command, detail) -> {
                    throw new java.io.IOException("Cannot run program \"docker\"");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("docker unavailable");
    }

    private static void assertCommandIsDockerVersion(List<String> command) {
        if (command == null || command.size() < 2
                || !"docker".equals(command.get(0))
                || !"version".equals(command.get(1))) {
            throw new AssertionError("unexpected probe command: " + command);
        }
    }
}
