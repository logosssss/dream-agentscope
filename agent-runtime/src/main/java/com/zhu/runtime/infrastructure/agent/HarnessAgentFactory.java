package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.application.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.semantic.contract.CompletionCriteria;
import com.zhu.semantic.contract.ProductSemanticProvider;
import com.zhu.semantic.contract.ProductTool;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 组装长驻 HarnessAgent。由接入层 Gateway 持有实例，不要每请求关闭。
 * Builder 可变，不要在线程之间共享同一个 Builder 再改提示词。
 */
@Component
public class HarnessAgentFactory {

    /** 瘦路由探测 Agent 的固定标识。不进默认 mainAgent。 */
    public static final String ECHO_AGENT_ID = "echo-v1";

    /** 瘦路由探测 Agent 的短系统提示。不挂搭子工具与记忆中间件。 */
    public static final String ECHO_SYS_PROMPT = "你是路由探测助手。用一两句中文直接回答用户，不要调用工具。";

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final ProductSemanticProvider semantics;

    private final ObjectProvider<DistributedStore> stores;

    private final AuditMiddleware auditMiddleware;

    private final ApprovalMiddleware approvalMiddleware;

    private final FileWriteLimitMiddleware fileWriteLimit;

    private final SafetyGateMiddleware safetyGate;

    private final PersonaConsistencyMiddleware personaConsistency;

    private final CompanionMemoryMiddleware companionMemory;

    private final AgentShutdown shutdown;

    private final List<ProductTool> productTools;

    /** 每个 Agent 共用这一份。构造函数只注册一次 Reactor 上下文传播，不连接采集器。 */
    private final OtelTracingMiddleware tracing = new OtelTracingMiddleware();

    /** 按目录挑选业务工具。目录为空时不注册。 */
    private final CatalogToolRegistrar toolRegistrar = new CatalogToolRegistrar();

    /** 写文件限制、审批、安全门、人设标记和记忆写入都挂上。体积检查更靠前，危机换话术发生在模型调用之前。 */
    public HarnessAgentFactory(
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            ProductSemanticProvider semantics,
            ObjectProvider<DistributedStore> stores,
            AuditMiddleware auditMiddleware,
            ApprovalMiddleware approvalMiddleware,
            FileWriteLimitMiddleware fileWriteLimit,
            SafetyGateMiddleware safetyGate,
            PersonaConsistencyMiddleware personaConsistency,
            CompanionMemoryMiddleware companionMemory,
            AgentShutdown shutdown,
            List<ProductTool> productTools) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.semantics = semantics;
        this.stores = stores;
        this.auditMiddleware = auditMiddleware;
        this.approvalMiddleware = approvalMiddleware;
        this.fileWriteLimit = fileWriteLimit;
        this.safetyGate = safetyGate;
        this.personaConsistency = personaConsistency;
        this.companionMemory = companionMemory;
        this.shutdown = shutdown;
        this.productTools = productTools == null ? List.of() : List.copyOf(productTools);
    }

    /**
     * 长驻搭子：提示词与工具来自产品语义，挂齐中间件。由接入层 Gateway 持有。
     */
    public HarnessAgent buildCompanionResident() {
        return buildCompanion(companionAgentId(), semantics.promptFallback(null), null);
    }

    /**
     * 长驻瘦路由件：真模型、短提示，不挂搭子记忆/人设/危机/业务工具。
     */
    public HarnessAgent buildEchoResident() {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("echo")
                .agentId(ECHO_AGENT_ID)
                .model(chatModel.resolve(null))
                .sysPrompt(ECHO_SYS_PROMPT)
                .maxIters(Math.min(8, maxIterations()));
        builder.disableWebTools();
        builder.disableShellTool();
        builder.middleware(tracing);
        builder.middleware(shutdown.middleware());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
            builder.filesystem(WorkspaceFilesystem.sessionScoped());
        }
        return builder.build();
    }

    /**
     * 搭子装配。tenantId 为空时用进程默认模型行。
     */
    private HarnessAgent buildCompanion(String agentId, String sysPrompt, String tenantId) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(properties.getName())
                .agentId(agentId)
                .model(chatModel.resolve(tenantId))
                .sysPrompt(sysPrompt)
                .maxIters(maxIterations());
        // 未做出口白名单前关掉内置 web 工具，避免会话内 SSRF（元数据/内网）。
        // 远程工作区路径下 shell 本就不注册；显式关掉防止以后换沙箱文件系统时裸奔。
        builder.disableWebTools();
        builder.disableShellTool();
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
        builder.middleware(personaConsistency);
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

    /** 默认搭子标识：语义 → 运行配置 → companion-v1。 */
    private String companionAgentId() {
        String fromSemantic = semantics.agentId();
        if (!blank(fromSemantic)) {
            return fromSemantic;
        }
        if (!blank(properties.getAgentId())) {
            return properties.getAgentId();
        }
        return "companion-v1";
    }

    /**
     * 优先用运行配置（可 Nacos 热更），再退回产品域 completion。
     * 都无效时按 20，避免 0 轮直接结束。
     */
    private int maxIterations() {
        int configured = properties.getMaxIters();
        if (configured >= 1) {
            return configured;
        }
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
