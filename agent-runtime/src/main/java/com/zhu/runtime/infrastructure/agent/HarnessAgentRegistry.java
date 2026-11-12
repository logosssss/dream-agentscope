package com.zhu.runtime.infrastructure.agent;

import io.agentscope.harness.agent.HarnessAgent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 长驻 {@link HarnessAgent} 按 agentId 索引。
 * Middleware 链路里框架传入的常是 ReAct 委托，拿不到 {@code WorkspaceManager}；
 * 危机闸 flush 需经本表回查 Harness。Gateway 启停时登记/注销。
 */
public final class HarnessAgentRegistry {

    private static final Map<String, HarnessAgent> BY_ID = new ConcurrentHashMap<>();

    private HarnessAgentRegistry() {
    }

    /** 覆盖同 id；agent 或 id 为空则忽略。 */
    public static void register(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        String id = agent.getAgentId();
        if (id == null || id.isBlank()) {
            return;
        }
        BY_ID.put(id, agent);
    }

    /** 按 id 注销；用于 Gateway stop。 */
    public static void unregister(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        BY_ID.remove(agentId);
    }

    /** 清空全部登记。 */
    public static void clear() {
        BY_ID.clear();
    }

    /** 未登记返回 null。 */
    public static HarnessAgent get(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return BY_ID.get(agentId);
    }
}
