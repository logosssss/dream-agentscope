package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.application.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import com.zhu.semantic.contract.tool.ProductTool;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 长驻 Agent 的 Builder 定制口。真正 {@code build()} 由 {@code GatewayBootstrap} 完成。
 * 公共项走 {@link #configureCommon}；搭子 / echo 差异走各自 customize。
 * 不要在线程之间共享同一个 Builder 再改提示词。
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

    private final PermissionContextFactory permissionContexts;

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
            PermissionContextFactory permissionContexts,
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
        this.permissionContexts = permissionContexts;
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
     * 给 Bootstrap {@code configureAllAgents} 用的公共项。
     * 只放 companion 与 echo 都要的：关 web/shell、打开 Harness Memory、权限上下文、追踪、停机闸、状态库与按用户工作区。
     * 产品中间件和提示词不要放这里。
     */
    public void configureCommon(HarnessAgent.Builder builder) {
        // 未做出口白名单前关掉内置 web 工具，避免会话内 SSRF（元数据/内网）。
        // 远程工作区路径下 shell 本就不注册；显式关掉防止以后换沙箱文件系统时裸奔。
        builder.disableWebTools();
        builder.disableShellTool();
        // 跨会话权威迁入 Harness Memory：不 disable 工具/flush；不另配 model（回落对话模型）。
        // throttled 10m；CRITICAL 禁 flush 在迁入 ② 产品闸。见 docs/memory-longterm-落地.md。
        builder.memory(MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10)))
                .build());
        // 无条件规则 + BYPASS；热更后排空重建会再次 create。
        builder.permissionContext(permissionContexts.create());
        builder.middleware(tracing);
        builder.middleware(shutdown.middleware());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
            builder.filesystem(WorkspaceFilesystem.userScoped());
        }
    }

    /**
     * 搭子差异：标识、模型、提示词、轮数、产品中间件与工作区路径。
     * 业务工具在 {@link #attachCompanionTools} 里挂，因为 Toolkit 要等 build 之后才有。
     */
    public void customizeCompanion(HarnessAgent.Builder builder) {
        String agentId = companionAgentId();
        builder.name(properties.getName())
                .agentId(agentId)
                .model(chatModel.resolve(null))
                .sysPrompt(semantics.promptFallback(null))
                .maxIters(maxIterations());
        if (!blank(properties.getWorkspace())) {
            builder.workspace(properties.getWorkspace());
        }
        // 审计、写文件上限、审批、安全门和记忆写入走 Middleware。Hook 自 2.0.0 起标成删除。
        builder.middleware(auditMiddleware);
        builder.middleware(fileWriteLimit);
        builder.middleware(approvalMiddleware);
        builder.middleware(safetyGate);
        builder.middleware(personaConsistency);
        builder.middleware(companionMemory);
    }

    /**
     * echo 差异：固定标识与短提示，轮数封顶 8，不挂搭子产品中间件。
     */
    public void customizeEcho(HarnessAgent.Builder builder) {
        builder.name("echo")
                .agentId(ECHO_AGENT_ID)
                .model(chatModel.resolve(null))
                .sysPrompt(ECHO_SYS_PROMPT)
                .maxIters(Math.min(8, maxIterations()));
    }

    /**
     * 搭子 build 完成后挂目录工具。加在这次 Agent 自己的 Toolkit 上。
     * 目录里没有的业务工具不会出现。
     */
    public void attachCompanionTools(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        toolRegistrar.register(agent.getToolkit(), semantics.tools(), productTools);
    }

    /** 默认搭子标识：语义 → 运行配置 → companion-v1。给 Bootstrap mainAgent / agent 键使用。 */
    public String companionAgentId() {
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
