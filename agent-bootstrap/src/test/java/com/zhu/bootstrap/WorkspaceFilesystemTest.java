package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.agent.WorkspaceFilesystem;
import io.agentscope.harness.agent.IsolationScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作区隔离粒度上线前定死。改了旧键不会迁移。
 */
class WorkspaceFilesystemTest {

    /** 按用户隔离，跨会话共享 MEMORY.md（Harness Memory 终态）。 */
    @Test
    void workspaceIsIsolatedByUser() {
        assertThat(WorkspaceFilesystem.userScoped().getIsolationScope()).isEqualTo(IsolationScope.USER);
    }
}
