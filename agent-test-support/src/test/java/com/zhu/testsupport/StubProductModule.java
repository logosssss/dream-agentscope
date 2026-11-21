package com.zhu.testsupport;

import com.zhu.product.api.ProductModule;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品缝的测试替身。默认空工具、20 轮上限，装配动作只记事件，不碰 Harness 真对象。
 */
public final class StubProductModule implements ProductModule {

    private final String agentId;
    private final boolean mainAgent;
    private final int maxIterations;
    private final List<String> events = new ArrayList<>();

    public StubProductModule(String agentId, boolean mainAgent) {
        this(agentId, mainAgent, 20);
    }

    public StubProductModule(String agentId, boolean mainAgent, int maxIterations) {
        this.agentId = agentId;
        this.mainAgent = mainAgent;
        this.maxIterations = maxIterations;
    }

    /** 主 Agent 的标准替身。 */
    public static StubProductModule main(String agentId) {
        return new StubProductModule(agentId, true);
    }

    /** 非主 Agent 的替身。 */
    public static StubProductModule secondary(String agentId) {
        return new StubProductModule(agentId, false);
    }

    /** 记录到的装配动作，按调用顺序。 */
    public List<String> events() {
        return List.copyOf(events);
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean mainAgent() {
        return mainAgent;
    }

    @Override
    public List<ToolDescriptor> tools() {
        return List.of();
    }

    @Override
    public CompletionCriteria completion() {
        return new CompletionCriteria(maxIterations);
    }

    @Override
    public String promptFallback(String key) {
        return "stub:" + (key == null ? "" : key);
    }

    @Override
    public void customize(HarnessAgent.Builder builder) {
        events.add("customize:" + agentId);
    }

    @Override
    public void attachTools(HarnessAgent agent) {
        events.add("attach:" + agentId);
    }
}
