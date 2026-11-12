package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.application.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedCompactionMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedMemoryFlushMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedToolResultEvictionMiddleware;
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
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 长驻 Agent 的 Builder 定制口。真正 {@code build()} 由 {@code GatewayBootstrap} 完成。
 * 公共项走 {@link #configureCommon}；搭子 / echo 差异走各自 customize。
 * Skills 窄范围：公共项关动态 skill，工作区默认 skill 仍开；不写种子、不开 manage/curator（见 docs/skills-落地.md）。
 * Subagent：公共项关动态；companion 挂一份 {@code subagent-probe}；echo 不挂（见 docs/subagent-落地.md）。
 * 不要在线程之间共享同一个 Builder 再改提示词。
 */
@Component
public class HarnessAgentFactory {

    /** 瘦路由探测 Agent 的固定标识。不进默认 mainAgent。 */
    public static final String ECHO_AGENT_ID = "echo-v1";

    /** 瘦路由探测 Agent 的短系统提示。不挂搭子工具与记忆中间件。 */
    public static final String ECHO_SYS_PROMPT = "你是路由探测助手。用一两句中文直接回答用户，不要调用工具。";

    /** companion 静态子代理探针名。对照用占位，不承诺产品能力。 */
    public static final String SUBAGENT_PROBE_NAME = "subagent-probe";

    /** 探针子代理提示词：极简短答，零工具。 */
    public static final String SUBAGENT_PROBE_PROMPT = "你是对照用占位子代理。用一两句中文直接短答，不要调用工具。";

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

    private final CrisisGatedMemoryFlushMiddleware crisisGatedMemoryFlush;

    private final CrisisGatedCompactionMiddleware crisisGatedCompaction;

    private final CrisisGatedToolResultEvictionMiddleware crisisGatedToolResultEviction;

    private final AgentShutdown shutdown;

    private final List<ProductTool> productTools;

    /** 每个 Agent 共用这一份。构造函数只注册一次 Reactor 上下文传播，不连接采集器。 */
    private final OtelTracingMiddleware tracing = new OtelTracingMiddleware();

    /** 按目录挑选业务工具。目录为空时不注册。 */
    private final CatalogToolRegistrar toolRegistrar = new CatalogToolRegistrar();

    /** 写文件限制、审批、安全门、人设标记、Eviction/Compaction 闸与 Memory flush 闸都挂上。体积检查更靠前，危机换话术在模型前。 */
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
            CrisisGatedMemoryFlushMiddleware crisisGatedMemoryFlush,
            CrisisGatedCompactionMiddleware crisisGatedCompaction,
            CrisisGatedToolResultEvictionMiddleware crisisGatedToolResultEviction,
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
        this.crisisGatedMemoryFlush = crisisGatedMemoryFlush;
        this.crisisGatedCompaction = crisisGatedCompaction;
        this.crisisGatedToolResultEviction = crisisGatedToolResultEviction;
        this.shutdown = shutdown;
        this.productTools = productTools == null ? List.of() : List.copyOf(productTools);
    }

    /**
     * 给 Bootstrap {@code configureAllAgents} 用的公共项。
     * 只放 companion 与 echo 都要的：关 web/shell、打开 Harness Memory、
     * disableCompaction / disableToolResultEviction / disableDynamicSkills / disableDynamicSubagents、
     * 权限上下文、追踪、停机闸、状态库与按用户工作区。
     * Eviction / Compaction 危机闸与静态子代理声明只在 {@link #customizeCompanion} 挂。
     */
    public void configureCommon(HarnessAgent.Builder builder) {
        // 未做出口白名单前关掉内置 web 工具，避免会话内 SSRF（元数据/内网）。
        // 远程工作区路径下 shell 本就不注册；显式关掉防止以后换沙箱文件系统时裸奔。
        builder.disableWebTools();
        builder.disableShellTool();
        // 跨会话权威迁入 Harness Memory：开放 memory_*；不另配 model（回落对话模型）。
        // 关掉框架自带 Memory hooks，改由 CrisisGatedMemoryFlushMiddleware 在非 CRITICAL 时触发 flush。
        // 见 docs/memory-longterm-落地.md。
        builder.memory(MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10)))
                .build());
        builder.disableMemoryHooks();
        // companion 再挂危机闸；echo 保持禁用且不挂闸。
        // 见 docs/compaction-落地.md、docs/tool-result-eviction-落地.md。
        builder.disableCompaction();
        // 框架默认已挂 eviction；必须显式 disable，否则 CRITICAL 仍可能被框架截断落盘。
        builder.disableToolResultEviction();
        // 框架默认开动态 skill；关掉后中间件 frozen。工作区默认 skill 仍开（目录可空），本专题不写种子文件。
        // 见 docs/skills-落地.md。
        builder.disableDynamicSkills();
        // 框架默认开动态子代理；关掉后仍可挂静态 SubagentDeclaration。见 docs/subagent-落地.md。
        builder.disableDynamicSubagents();
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
     * 含 Eviction → Compaction → Memory flush 危机闸（须在 SafetyGate 之后）。
     * 挂一份静态 {@link #SUBAGENT_PROBE_NAME}（ISOLATED / 继承权限 / 零工具）。
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
        // 静态探针：Mode.SUBAGENT + 空 tools，降低嵌套 spawn 面；难硬禁则见专题已知限制。
        builder.subagent(subagentProbe());
        // 审计、写文件上限、审批、安全门、人设检查走 Middleware。
        // 跨会话记忆权威是 Harness Memory（CrisisGated flush）；不再挂 CompanionMemoryMiddleware。
        builder.middleware(auditMiddleware);
        builder.middleware(fileWriteLimit);
        builder.middleware(approvalMiddleware);
        builder.middleware(safetyGate);
        // 须在 SafetyGate 之后、Compaction 之前：先截断大工具结果。
        builder.middleware(crisisGatedToolResultEviction);
        // 须在 SafetyGate 之后：CRITICAL 整段跳过 Compaction（含压前 flush）。
        builder.middleware(crisisGatedCompaction);
        builder.middleware(personaConsistency);
        // 须在 SafetyGate 之后：靠本轮 RiskLevel 决定是否入队 flush。
        builder.middleware(crisisGatedMemoryFlush);
    }

    /**
     * echo 差异：固定标识与短提示，轮数封顶 8，不挂搭子产品中间件，不挂静态子代理。
     * 继承 common 的 disableCompaction / disableToolResultEviction / disableDynamicSkills /
     * disableDynamicSubagents，不挂危机闸。
     */
    public void customizeEcho(HarnessAgent.Builder builder) {
        builder.name("echo")
                .agentId(ECHO_AGENT_ID)
                .model(chatModel.resolve(null))
                .sysPrompt(ECHO_SYS_PROMPT)
                .maxIters(Math.min(8, maxIterations()));
    }

    /**
     * companion 唯一静态子代理声明。不写 Remote、不持久会话、工具名单为空。
     */
    public static SubagentDeclaration subagentProbe() {
        return SubagentDeclaration.builder()
                .name(SUBAGENT_PROBE_NAME)
                .description("对照用占位子代理，不承诺产品能力")
                .workspaceMode(WorkspaceMode.ISOLATED)
                .inheritParentPermissions(true)
                .persistSession(false)
                .mode(SubagentDeclaration.Mode.SUBAGENT)
                .tools(List.of())
                .inlineAgentsBody(SUBAGENT_PROBE_PROMPT)
                .build();
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
