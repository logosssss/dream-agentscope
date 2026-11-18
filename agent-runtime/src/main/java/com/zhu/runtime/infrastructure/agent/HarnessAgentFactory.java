package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.application.turn.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
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
 * 长驻 Agent 的平台装配口。真正 {@code build()} 由 {@code GatewayBootstrap} 完成。
 * 公共项走 {@link #configureCommon}；平台 echo 走 {@link #customizeEcho}。
 * 产品 Agent 差异由 {@link ResidentAgentBinding} 提供（实现在各 product 模块）。
 * 不要在线程之间共享同一个 Builder 再改提示词。
 */
@Component
public class HarnessAgentFactory {

    /** 瘦路由探测 Agent 的固定标识。不进默认 mainAgent，不进产品 Binding。 */
    public static final String ECHO_AGENT_ID = "echo-v1";

    /** 瘦路由探测 Agent 的短系统提示。不挂搭子工具与记忆中间件。 */
    public static final String ECHO_SYS_PROMPT = "你是路由探测助手。用一两句中文直接回答用户，不要调用工具。";

    /**
     * @deprecated 探针常量在 product companion Binding；保留以免外部引用断裂。
     */
    @Deprecated
    public static final String SUBAGENT_PROBE_NAME = "subagent-probe";

    /**
     * @deprecated 见 product companion Binding。
     */
    @Deprecated
    public static final String SUBAGENT_PROBE_PROMPT = "你是对照用占位子代理。用一两句中文直接短答，不要调用工具。";

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final ProductSemanticProvider semantics;

    private final ObjectProvider<DistributedStore> stores;

    private final PermissionContextFactory permissionContexts;

    private final AgentShutdown shutdown;

    /** 每个 Agent 共用这一份。构造函数只注册一次 Reactor 上下文传播，不连接采集器。 */
    private final OtelTracingMiddleware tracing = new OtelTracingMiddleware();

    /**
     * 平台公共项与 echo；产品闸与目录工具在 {@link ResidentAgentBinding}。
     */
    public HarnessAgentFactory(
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            ProductSemanticProvider semantics,
            ObjectProvider<DistributedStore> stores,
            PermissionContextFactory permissionContexts,
            AgentShutdown shutdown) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.semantics = semantics;
        this.stores = stores;
        this.permissionContexts = permissionContexts;
        this.shutdown = shutdown;
    }

    /**
     * 给 Bootstrap {@code configureAllAgents} 用的公共项。
     * 产品与 echo 共用：关 web/shell、打开 Harness Memory、若干 disable*、权限、追踪、停机闸与状态库。
     * 文件系统不在此处挂：Remote 与 Docker 规格互斥，由各 Binding / echo 决定。
     */
    public void configureCommon(HarnessAgent.Builder builder) {
        builder.disableWebTools();
        builder.disableShellTool();
        builder.memory(MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofMinutes(10)))
                .build());
        builder.disableMemoryHooks();
        builder.disableCompaction();
        builder.disableToolResultEviction();
        builder.disableDynamicSkills();
        builder.disableDynamicSubagents();
        builder.permissionContext(permissionContexts.create());
        builder.middleware(tracing);
        builder.middleware(shutdown.middleware());
        DistributedStore store = stores.getIfAvailable();
        if (store != null) {
            builder.distributedStore(store);
        }
    }

    /**
     * echo 差异：固定标识与短提示，轮数封顶 8，不挂产品中间件 / 子代理 / MCP / Docker。
     * 有 Store 时挂 Remote USER；继承 common 的 disable*。
     */
    public void customizeEcho(HarnessAgent.Builder builder) {
        builder.name("echo")
                .agentId(ECHO_AGENT_ID)
                .model(chatModel.resolve(null))
                .sysPrompt(ECHO_SYS_PROMPT)
                .maxIters(Math.min(8, maxIterations()));
        if (stores.getIfAvailable() != null) {
            builder.filesystem(WorkspaceFilesystem.userScoped());
        }
    }

    /**
     * @deprecated 使用 product companion {@code CompanionResidentAgentBinding#subagentProbe()}。
     */
    @Deprecated
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
}
