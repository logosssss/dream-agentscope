package com.zhu.runtime.infrastructure.agent;

import com.zhu.semantic.contract.tool.ProductTool;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.core.tool.Toolkit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 按工具目录把业务工具挂到这次 Agent 的 Toolkit。不在目录里的实现不会注册。
 * 单工具失败隔离：不拖垮整次注册与 Gateway 启动。
 */
public final class CatalogToolRegistrar {

    private static final Logger log = Logger.getLogger(CatalogToolRegistrar.class.getName());

    /**
     * 目录或实现为空时返回空报告。同一个标识只注册第一次。
     * 调用方传入的 Toolkit 应是这次 Agent 自己的，不要改共享实例。
     * 单个 {@code registerTool} 抛错记入 failed 并继续其余工具。
     */
    public CatalogToolRegistrationReport register(
            Toolkit toolkit, List<ToolDescriptor> enabled, List<ProductTool> available) {
        if (toolkit == null || enabled == null || enabled.isEmpty() || available == null || available.isEmpty()) {
            return CatalogToolRegistrationReport.empty();
        }
        Set<String> allowed = new HashSet<>();
        for (ToolDescriptor descriptor : enabled) {
            if (descriptor != null && descriptor.toolId() != null && !descriptor.toolId().isBlank()) {
                allowed.add(descriptor.toolId());
            }
        }
        Set<String> seen = new HashSet<>();
        List<String> registered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (ProductTool tool : available) {
            if (tool == null || tool.toolId() == null || tool.toolId().isBlank()) {
                continue;
            }
            String toolId = tool.toolId();
            if (!allowed.contains(toolId) || !seen.add(toolId)) {
                continue;
            }
            try {
                Object target = tool.target();
                if (target == null) {
                    continue;
                }
                toolkit.registerTool(target);
                registered.add(toolId);
            } catch (RuntimeException error) {
                failed.add(toolId);
                log.warning("catalog tool register failed toolId=" + toolId + ": " + error.getMessage());
            }
        }
        return new CatalogToolRegistrationReport(registered, failed);
    }
}
