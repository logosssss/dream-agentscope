package com.zhu.runtime.infrastructure.agent;

import com.zhu.product.api.ProductModule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 产品模块注册表。运行层基础设施：启动时校验唯一 main 与 agentId，供 Gateway 遍历。
 * 不包含平台 echo。
 */
@Component
public class ProductModuleRegistry {

    private final List<ProductModule> modules;

    private final ProductModule main;

    /**
     * 收集全部 {@link ProductModule} Bean。缺 main、多个 main、或 agentId 冲突则启动失败。
     *
     * @param modules 容器里的产品模块
     */
    public ProductModuleRegistry(List<ProductModule> modules) {
        List<ProductModule> copy = modules == null ? List.of() : List.copyOf(modules);
        if (copy.isEmpty()) {
            throw new IllegalStateException("no ProductModule registered");
        }
        Set<String> seen = new HashSet<>();
        List<ProductModule> mains = new ArrayList<>();
        for (ProductModule module : copy) {
            if (module == null) {
                throw new IllegalStateException("ProductModule is null");
            }
            String agentId = module.agentId();
            if (agentId == null || agentId.isBlank()) {
                throw new IllegalStateException("ProductModule agentId is blank");
            }
            if (HarnessAgentFactory.ECHO_AGENT_ID.equals(agentId)) {
                throw new IllegalStateException("ProductModule must not use reserved echo id");
            }
            if (!seen.add(agentId)) {
                throw new IllegalStateException("duplicate ProductModule agentId=" + agentId);
            }
            if (module.mainAgent()) {
                mains.add(module);
            }
        }
        if (mains.size() != 1) {
            throw new IllegalStateException("expected exactly one main ProductModule, got " + mains.size());
        }
        this.modules = copy;
        this.main = mains.get(0);
    }

    /** 全部产品模块（不含 echo），顺序与注入列表一致。 */
    public List<ProductModule> all() {
        return modules;
    }

    /** 唯一的 main 模块。 */
    public ProductModule main() {
        return main;
    }

    /**
     * 按 agentId 找已注册的产品模块；找不到返回 null。
     *
     * @param agentId 产品标识
     */
    public ProductModule find(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        for (ProductModule module : modules) {
            if (agentId.equals(module.agentId())) {
                return module;
            }
        }
        return null;
    }
}
