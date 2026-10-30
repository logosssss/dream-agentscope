package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.config.AgentRuntimeProperties;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 按请求组装 HarnessAgent。Builder 可变，所以每次新建，不在线程之间共享后再改提示词。
 */
@Component
public class HarnessAgentFactory {

    private final AgentRuntimeProperties properties;

    private final ObjectProvider<DistributedStore> stores;

    public HarnessAgentFactory(AgentRuntimeProperties properties, ObjectProvider<DistributedStore> stores) {
        this.properties = properties;
        this.stores = stores;
    }

    /**
     * 创建一个尚未执行的 Agent。调用方必须在结束时关闭它。
     * 有 DistributedStore 时挂上，没有时仍可构建，状态只留在本进程。
     */
    public HarnessAgent build(AgentRequest request) {
        String agentId = blank(request.agentId()) ? properties.getAgentId() : request.agentId();
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(properties.getName())
                .agentId(agentId)
                .model(properties.getModel())
                .sysPrompt(blank(request.systemPrompt()) ? "." : request.systemPrompt())
                .maxIters(properties.getMaxIters());
        if (!blank(properties.getWorkspace())) {
            builder.workspace(properties.getWorkspace());
        }
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
        }
        return builder.build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
