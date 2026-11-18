package com.zhu.runtime.infrastructure.agent;

import io.agentscope.harness.agent.HarnessAgent;

/**
 * 长驻产品 Agent 的装配缝。运行层基础设施：Gateway 按注册表挂 Agent，不写死产品名。
 * 平台 echo 探针不走本缝（见 {@link HarnessAgentFactory#customizeEcho}）。
 * ② 起由各 {@code agent-product-*} 提供实现；① 暂由 runtime 内 companion 实现占位。
 */
public interface ResidentAgentBinding {

    /**
     * 状态命名空间与 Bootstrap agent 键。须稳定，改了会丢旧会话状态。
     */
    String agentId();

    /**
     * 是否作为 Gateway {@code mainAgent}。全集有且仅有一个为 true。
     */
    boolean mainAgent();

    /**
     * 在 {@code configureCommon} 之后定制本 Agent。不要调用 build。
     */
    void customize(HarnessAgent.Builder builder);

    /**
     * build/start 之后挂业务工具。Toolkit 此时才可用；缺省空实现。
     */
    default void attachTools(HarnessAgent agent) {
        // 多数产品无目录工具。
    }
}
