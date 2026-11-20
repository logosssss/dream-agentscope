package com.zhu.runtime.infrastructure.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 产品长驻 Agent 绑定注册表。运行层基础设施：启动时校验唯一 main 与 agentId，供 Gateway 遍历。
 * 不包含平台 echo。
 */
@Component
public class ResidentAgentBindingRegistry {

    private final List<ResidentAgentBinding> bindings;

    private final ResidentAgentBinding main;

    /**
     * 收集全部 {@link ResidentAgentBinding} Bean。缺 main、多个 main、或 agentId 冲突则启动失败。
     */
    public ResidentAgentBindingRegistry(List<ResidentAgentBinding> bindings) {
        List<ResidentAgentBinding> copy =
                bindings == null ? List.of() : List.copyOf(bindings);
        if (copy.isEmpty()) {
            throw new IllegalStateException("no ResidentAgentBinding registered");
        }
        Set<String> seen = new HashSet<>();
        List<ResidentAgentBinding> mains = new ArrayList<>();
        for (ResidentAgentBinding binding : copy) {
            if (binding == null) {
                throw new IllegalStateException("ResidentAgentBinding is null");
            }
            String agentId = binding.agentId();
            if (agentId == null || agentId.isBlank()) {
                throw new IllegalStateException("ResidentAgentBinding agentId is blank");
            }
            if (HarnessAgentFactory.ECHO_AGENT_ID.equals(agentId)) {
                throw new IllegalStateException("ResidentAgentBinding must not use reserved echo id");
            }
            if (!seen.add(agentId)) {
                throw new IllegalStateException("duplicate ResidentAgentBinding agentId=" + agentId);
            }
            if (binding.mainAgent()) {
                mains.add(binding);
            }
        }
        if (mains.size() != 1) {
            throw new IllegalStateException(
                    "expected exactly one main ResidentAgentBinding, got " + mains.size());
        }
        this.bindings = copy;
        this.main = mains.get(0);
    }

    /** 全部产品绑定（不含 echo），顺序与注入列表一致。 */
    public List<ResidentAgentBinding> all() {
        return bindings;
    }

    /** 唯一的 main 绑定。 */
    public ResidentAgentBinding main() {
        return main;
    }
}
