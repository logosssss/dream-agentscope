package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.semantic.contract.CompletionCriteria;
import com.zhu.semantic.contract.ProductSemanticProvider;
import com.zhu.semantic.contract.ProductTool;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 按请求组装 HarnessAgent。Builder 可变，所以每次新建，不在线程之间共享后再改提示词。
 */
@Component
public class HarnessAgentFactory {

    private final AgentRuntimeProperties properties;

    private final ProductSemanticProvider semantics;

    private final ObjectProvider<DistributedStore> stores;

    private final AuditMiddleware auditMiddleware;

    private final ApprovalMiddleware approvalMiddleware;

    private final FileWriteLimitMiddleware fileWriteLimit;

    private final SafetyGateMiddleware safetyGate;

    private final CompanionMemoryMiddleware companionMemory;

    private final AgentShutdown shutdown;

    private final List<ProductTool> productTools;

    /** 每个 Agent 共用这一份。构造函数只注册一次 Reactor 上下文传播，不连接采集器。 */
    private final OtelTracingMiddleware tracing = new OtelTracingMiddleware();

    /** 按目录挑选业务工具。目录为空时不注册。 */
    private final CatalogToolRegistrar toolRegistrar = new CatalogToolRegistrar();

    /** 写文件限制、审批、安全门和记忆写入都挂上。体积检查更靠前，危机换话术发生在模型调用之前。 */
    public HarnessAgentFactory(
            AgentRuntimeProperties properties,
            ProductSemanticProvider semantics,
            ObjectProvider<DistributedStore> stores,
            AuditMiddleware auditMiddleware,
            ApprovalMiddleware approvalMiddleware,
            FileWriteLimitMiddleware fileWriteLimit,
            SafetyGateMiddleware safetyGate,
            CompanionMemoryMiddleware companionMemory,
            AgentShutdown shutdown,
            List<ProductTool> productTools) {
        this.properties = properties;
        this.semantics = semantics;
        this.stores = stores;
        this.auditMiddleware = auditMiddleware;
        this.approvalMiddleware = approvalMiddleware;
        this.fileWriteLimit = fileWriteLimit;
        this.safetyGate = safetyGate;
        this.companionMemory = companionMemory;
        this.shutdown = shutdown;
        this.productTools = productTools == null ? List.of() : List.copyOf(productTools);
    }

    /**
     * 创建一个尚未执行的 Agent。调用方必须在结束时关闭它。
     * 有 DistributedStore 时同时挂远程文件系统，工作区进 MySQL。
     * 没有 Store 时不挂远程文件系统，只留给测试。
     */
    public HarnessAgent build(AgentRequest request) {
        String agentId = blank(request.agentId()) ? semantics.agentId() : request.agentId();
        if (blank(agentId)) {
            agentId = "companion-v1";
        }
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(properties.getName())
                .agentId(agentId)
                .model(properties.getModel())
                .sysPrompt(blank(request.systemPrompt()) ? semantics.promptFallback(null) : request.systemPrompt())
                .maxIters(maxIterations());
        if (!blank(properties.getWorkspace())) {
            builder.workspace(properties.getWorkspace());
        }
        // 审计、写文件上限、审批、安全门和记忆写入走 Middleware。Hook 自 2.0.0 起标成删除，不再挂到 Builder 上。
        // 追踪放在同顺序中间件的前面。没配导出地址时 span 被丢掉。
        builder.middleware(tracing);
        builder.middleware(auditMiddleware);
        builder.middleware(fileWriteLimit);
        builder.middleware(approvalMiddleware);
        builder.middleware(safetyGate);
        builder.middleware(companionMemory);
        builder.middleware(shutdown.middleware());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
            builder.filesystem(WorkspaceFilesystem.sessionScoped());
        }
        HarnessAgent agent = builder.build();
        // 加在这次 Agent 自己的 Toolkit 上。目录里没有的业务工具不会出现。
        toolRegistrar.register(agent.getToolkit(), semantics.tools(), productTools);
        return agent;
    }

    /** 产品域没给出有效轮数时按 20，避免 0 轮直接结束。 */
    private int maxIterations() {
        CompletionCriteria completion = semantics.completion();
        if (completion == null || completion.maxIterations() < 1) {
            return 20;
        }
        return completion.maxIterations();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
