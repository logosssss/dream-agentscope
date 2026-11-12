package com.zhu.runtime.application;

import com.zhu.audit.api.model.ModelConfigLookup;
import com.zhu.runtime.config.AgentRuntimeProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 运行层选这次对话的模型名。有启用配置用配置，没有再用进程值。
 */
@Component
public class ResolvedChatModel {

    private final AgentRuntimeProperties properties;

    private final ObjectProvider<ModelConfigLookup> lookups;

    /** MySQL 关闭时没有目录，一直用进程配置。 */
    public ResolvedChatModel(AgentRuntimeProperties properties, ObjectProvider<ModelConfigLookup> lookups) {
        this.properties = properties;
        this.lookups = lookups;
    }

    /**
     * 目录查失败由目录抛不可用。没有行时退回进程配置，避免没登记就不能对话。
     */
    public String resolve(String tenantId) {
        ModelConfigLookup lookup = lookups.getIfAvailable();
        if (lookup == null) {
            return properties.getModel();
        }
        String registered = lookup.resolveModel(tenantId);
        if (registered == null || registered.isBlank()) {
            return properties.getModel();
        }
        return registered;
    }
}
