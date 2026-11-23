package com.zhu.product.companion.semantic;

import com.zhu.product.companion.persona.CompanionPersona;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.tool.NoToolCatalog;
import com.zhu.semantic.api.tool.ToolCatalogService;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import java.util.List;

/**
 * 搭子产品语义。纯对象，不是 Bean：唯一的 Bean 是 {@code CompanionResidentAgentBinding}（ProductModule），
 * 两条缝共用同一份数值，避免 agentId / 轮数在装配与语义两处漂移。
 */
public final class CompanionSemantics implements ProductSemanticProvider {

    private static final int DEFAULT_MAX_ITERATIONS = 20;

    private final String agentId;

    private final int maxIterations;

    private final ToolCatalogService catalog;

    /**
     * 标识或轮数没配、配成无效值时用缺省，避免空标识把会话写进无名状态。
     *
     * @param agentId       运行配置里的标识，可为空
     * @param maxIterations 运行配置里的上限，可小于 1
     * @param catalog       工具目录，可为 null（等价空目录）
     */
    public CompanionSemantics(String agentId, int maxIterations, ToolCatalogService catalog) {
        this.agentId = agentId == null || agentId.isBlank() ? CompanionPersona.AGENT_ID : agentId;
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
     *
     * @param key 提示词键
     */
    @Override
    public String promptFallback(String key) {
        return PlatformPrompt.SYSTEM_FALLBACK;
    }
}
