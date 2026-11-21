package com.zhu.product.api;

import com.zhu.semantic.contract.product.ProductSemanticProvider;
import io.agentscope.harness.agent.HarnessAgent;

/**
 * 一个产品的全部可变部分：语义、装配、工具。一个进程可有多个实现，其中恰好一个 {@link #mainAgent()} 为 true。
 *
 * <p>合并了原 runtime 的 {@code ResidentAgentBinding} 与 semantic 的 {@code ProductSemanticProvider}——
 * 两者本来就是同一个产品的两份视图，分成两个接口只会让实现类漂移。
 *
 * <p>本接口继承 {@link ProductSemanticProvider}，所以注入方（access 的提示词兜底、runtime 的轮数）可以按窄视图注入，
 * 而 Gateway 按宽视图遍历。产品实现必须是一个 Spring Bean，且不得同时暴露第二个 {@code ProductSemanticProvider} Bean。
 *
 * <p>人格（{@code PersonaContract}）不在本契约里：它是平台级语义，由 runtime 单独注入，避免一个产品出现两份身份定义。
 */
public interface ProductModule extends ProductSemanticProvider {

    /**
     * 是否作为 Gateway 的 main Agent。全集有且仅有一个为 true；多个会让启动失败。
     */
    boolean mainAgent();

    /**
     * 在平台公共项（{@code configureAllAgents}）之后定制本 Agent。不要在这里调用 build。
     *
     * @param builder Harness Agent 构建器
     */
    void customize(HarnessAgent.Builder builder);

    /**
     * build/start 之后挂业务工具。Toolkit 此时才可用；缺省空实现。
     *
     * @param agent 已启动的 Agent
     */
    default void attachTools(HarnessAgent agent) {
        // 多数产品没有目录工具。
    }
}
