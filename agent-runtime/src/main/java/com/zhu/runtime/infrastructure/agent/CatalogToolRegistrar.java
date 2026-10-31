package com.zhu.runtime.infrastructure.agent;

import com.zhu.semantic.contract.ProductTool;
import com.zhu.semantic.contract.ToolDescriptor;
import io.agentscope.core.tool.Toolkit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按工具目录把业务工具挂到这次 Agent 的 Toolkit。不在目录里的实现不会注册。
 */
public final class CatalogToolRegistrar {

    /**
     * 目录或实现为空时什么都不做。同一个标识只注册第一次。
     * 调用方传入的 Toolkit 应是这次 Agent 自己的，不要改共享实例。
     */
    public void register(Toolkit toolkit, List<ToolDescriptor> enabled, List<ProductTool> available) {
        if (toolkit == null || enabled == null || enabled.isEmpty() || available == null || available.isEmpty()) {
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (ToolDescriptor descriptor : enabled) {
            if (descriptor != null && descriptor.toolId() != null && !descriptor.toolId().isBlank()) {
                allowed.add(descriptor.toolId());
            }
        }
        Set<String> seen = new HashSet<>();
        for (ProductTool tool : available) {
            if (tool == null || tool.toolId() == null || tool.toolId().isBlank()) {
                continue;
            }
            if (!allowed.contains(tool.toolId()) || !seen.add(tool.toolId())) {
                continue;
            }
            Object target = tool.target();
            if (target != null) {
                toolkit.registerTool(target);
            }
        }
    }
}
