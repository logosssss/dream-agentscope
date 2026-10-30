package com.zhu.runtime.infrastructure.agent;

import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;

/**
 * 工作区文件系统。多副本时文件进 MySQL 的 BaseStore，不落在本机盘。
 * 隔离粒度定成按对话。上线后不要改，旧键不会自动迁移。
 */
public final class WorkspaceFilesystem {

    private WorkspaceFilesystem() {
    }

    /**
     * 按对话隔离。BaseStore 由 DistributedStore 在构建 Agent 时注入。
     * 框架默认是按用户隔离，这里显式改掉，避免两个会话共用同一份工作区。
     */
    public static RemoteFilesystemSpec sessionScoped() {
        return new RemoteFilesystemSpec().isolationScope(IsolationScope.SESSION);
    }
}
