package com.zhu.semantic.config;

import com.zhu.semantic.api.NoToolCatalog;
import com.zhu.semantic.api.PlatformPrompt;
import com.zhu.semantic.api.ToolCatalogService;
import com.zhu.semantic.contract.CompletionCriteria;
import com.zhu.semantic.contract.ProductSemanticProvider;
import com.zhu.semantic.contract.ToolDescriptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 平台默认的产品语义。运行层只依赖契约，具体数值在这里收口。
 * 工具列表来自目录。MySQL 打开时目录读 tool_registry，关闭时目录为空。
 */
@Service
public class PlatformSemanticProvider implements ProductSemanticProvider {

    private static final String DEFAULT_AGENT_ID = "product-agent";

    private static final int DEFAULT_MAX_ITERATIONS = 20;

    private final String agentId;

    private final int maxIterations;

    private final ToolCatalogService catalog;

    /**
     * 标识或轮数没配、配成无效值时用缺省，避免空标识把会话写进无名状态。
     */
    public PlatformSemanticProvider(
            @Value("${agentscope.runtime.agent-id:product-agent}") String agentId,
            @Value("${agentscope.runtime.max-iters:20}") int maxIterations,
            ToolCatalogService catalog) {
        this.agentId = agentId == null || agentId.isBlank() ? DEFAULT_AGENT_ID : agentId;
        this.maxIterations = maxIterations < 1 ? DEFAULT_MAX_ITERATIONS : maxIterations;
        this.catalog = catalog == null ? new NoToolCatalog() : catalog;
    }

    /** 状态命名空间。请求里另有标识时运行层以请求为准。 */
    @Override
    public String agentId() {
        return agentId;
    }

    /** 目录为空时返回空列表。 */
    @Override
    public List<ToolDescriptor> tools() {
        List<ToolDescriptor> enabled = catalog.listEnabled();
        if (enabled == null || enabled.isEmpty()) {
            return List.of();
        }
        return List.copyOf(enabled);
    }

    /** 单次执行最多推理这么多轮。 */
    @Override
    public CompletionCriteria completion() {
        return new CompletionCriteria(maxIterations);
    }

    /**
     * 已知的平台键返回约定兜底。其它键也返回非空占位，避免空提示词进入模型。
     */
    @Override
    public String promptFallback(String key) {
        if (PlatformPrompt.SYSTEM_KEY.equals(key)) {
            return PlatformPrompt.SYSTEM_FALLBACK;
        }
        return PlatformPrompt.SYSTEM_FALLBACK;
    }
}
