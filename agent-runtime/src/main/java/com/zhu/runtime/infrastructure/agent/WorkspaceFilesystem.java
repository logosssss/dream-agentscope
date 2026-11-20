package com.zhu.runtime.infrastructure.agent;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

/**
 * 工作区文件系统。多副本时文件进 MySQL 的 BaseStore，不落在本机盘。
 * 隔离粒度定成按用户：跨会话共享 {@code MEMORY.md}（Harness Memory 终态）。
 * 同用户多会话会共用工作区文件；改粒度不会迁移旧键。
 */
public final class WorkspaceFilesystem {

    private WorkspaceFilesystem() {
    }

    /**
     * 按用户隔离。BaseStore 由 DistributedStore 在构建 Agent 时注入。
     * 与 docs/memory-longterm-落地.md 锁定的 IsolationScope.USER 一致。
     */
    public static RemoteFilesystemSpec userScoped() {
        return new RemoteFilesystemSpec().isolationScope(IsolationScope.USER);
    }
}
