package com.zhu.runtime.domain.middleware;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.harness.agent.HarnessAgent;

/**
 * 运行层：从当前 Agent 取出框架 {@link PermissionEngine}。
 * 单测里的裸 {@link Agent} mock 没有引擎，调用方按 ALLOW 处理引擎侧。
 */
final class PermissionEngineAccess {

    private PermissionEngineAccess() {
    }

    /**
     * 长驻路径是 {@link HarnessAgent}；直接测 ReAct 时也可能是委托本身。
     * 都不是则返回 null，不抛错。
     */
    static PermissionEngine of(Agent agent) {
        if (agent instanceof HarnessAgent harness) {
            ReActAgent delegate = harness.getDelegate();
            return delegate == null ? null : delegate.getPermissionEngine();
        }
        if (agent instanceof ReActAgent react) {
            return react.getPermissionEngine();
        }
        return null;
    }
}
