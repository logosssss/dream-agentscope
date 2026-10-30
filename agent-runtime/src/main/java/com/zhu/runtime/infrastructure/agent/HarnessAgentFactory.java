package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
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

    private final AuditMiddleware auditMiddleware;

    private final ApprovalMiddleware approvalMiddleware;

    private final FileWriteLimitMiddleware fileWriteLimit;

    private final AgentShutdown shutdown;

    /** 写文件限制和审批都挂上。体积检查的顺序更靠前，超限内容不会进入审批。 */
    public HarnessAgentFactory(
            AgentRuntimeProperties properties,
            ObjectProvider<DistributedStore> stores,
            AuditMiddleware auditMiddleware,
            ApprovalMiddleware approvalMiddleware,
            FileWriteLimitMiddleware fileWriteLimit,
            AgentShutdown shutdown) {
        this.properties = properties;
        this.stores = stores;
        this.auditMiddleware = auditMiddleware;
        this.approvalMiddleware = approvalMiddleware;
        this.fileWriteLimit = fileWriteLimit;
        this.shutdown = shutdown;
    }

    /**
     * 创建一个尚未执行的 Agent。调用方必须在结束时关闭它。
     * 有 DistributedStore 时同时挂远程文件系统，工作区进 MySQL。
     * 没有 Store 时不挂远程文件系统，只留给测试。
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
        // 审计、写文件上限和审批走 Middleware。Hook 自 2.0.0 起标成删除，不再挂到 Builder 上。
        builder.middleware(auditMiddleware);
        builder.middleware(fileWriteLimit);
        builder.middleware(approvalMiddleware);
        builder.middleware(shutdown.middleware());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
            builder.filesystem(WorkspaceFilesystem.sessionScoped());
        }
        return builder.build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
