package com.zhu.product.companion.binding;

import com.zhu.product.companion.persona.CompanionPersona;
import com.zhu.runtime.application.turn.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrationReport;
import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrar;
import com.zhu.runtime.infrastructure.agent.CompanionMiddlewareChain;
import com.zhu.runtime.infrastructure.agent.CompanionSandboxFilesystem;
import com.zhu.runtime.infrastructure.agent.McpToolsConfigFactory;
import com.zhu.runtime.infrastructure.agent.ResidentAgentBinding;
import com.zhu.runtime.infrastructure.agent.WorkspaceFilesystem;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import com.zhu.semantic.contract.tool.ProductTool;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import io.agentscope.harness.agent.tools.McpServerRegistrationListener;
import io.agentscope.harness.agent.tools.McpServerRegistrationResult;
import io.agentscope.harness.agent.tools.ToolsConfig;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 搭子产品长驻 Binding。产品层：组装差异项与目录工具；中间件顺序经 runtime {@link CompanionMiddlewareChain}（C1）。
 */
@Component
public class CompanionResidentAgentBinding implements ResidentAgentBinding {

    private static final Logger LOG = Logger.getLogger(CompanionResidentAgentBinding.class.getName());

    /** companion 静态子代理探针名。对照用占位，不承诺产品能力。 */
    public static final String SUBAGENT_PROBE_NAME = "subagent-probe";

    /** 探针子代理提示词：极简短答，零工具。 */
    public static final String SUBAGENT_PROBE_PROMPT = "你是对照用占位子代理。用一两句中文直接短答，不要调用工具。";

    private static final McpServerRegistrationListener MCP_LISTENER =
            CompanionResidentAgentBinding::onMcpRegistration;

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final ProductSemanticProvider semantics;

    private final ObjectProvider<DistributedStore> stores;

    private final CompanionMiddlewareChain companionMiddlewares;

    private final List<ProductTool> productTools;

    private final CatalogToolRegistrar toolRegistrar = new CatalogToolRegistrar();

    /**
     * 产品语义与中间件链来自容器；不依赖 HarnessAgentFactory。
     */
    public CompanionResidentAgentBinding(
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            ProductSemanticProvider semantics,
            ObjectProvider<DistributedStore> stores,
            CompanionMiddlewareChain companionMiddlewares,
            List<ProductTool> productTools) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.semantics = semantics;
        this.stores = stores;
        this.companionMiddlewares = companionMiddlewares;
        this.productTools = productTools == null ? List.of() : List.copyOf(productTools);
    }

    @Override
    public String agentId() {
        String fromSemantic = semantics.agentId();
        if (!blank(fromSemantic)) {
            return fromSemantic;
        }
        if (!blank(properties.getAgentId())) {
            return properties.getAgentId();
        }
        return CompanionPersona.AGENT_ID;
    }

    @Override
    public boolean mainAgent() {
        return true;
    }

    /**
     * 搭子差异：标识、模型、提示词、轮数、产品中间件与工作区路径。
     * 挂静态探针；MCP / Sandbox 仅 companion。
     */
    @Override
    public void customize(HarnessAgent.Builder builder) {
        String id = agentId();
        builder.name(properties.getName())
                .agentId(id)
                .model(chatModel.resolve(null))
                .sysPrompt(semantics.promptFallback(null))
                .maxIters(maxIterations());
        if (!blank(properties.getWorkspace())) {
            builder.workspace(properties.getWorkspace());
        }
        builder.subagent(subagentProbe());
        ToolsConfig mcpTools = McpToolsConfigFactory.createOrNull(properties.getMcp());
        if (mcpTools != null) {
            builder.toolsConfig(mcpTools);
            builder.mcpServerRegistrationListener(MCP_LISTENER);
        }
        applyCompanionFilesystem(builder);
        companionMiddlewares.apply(builder);
    }

    /**
     * 目录工具挂到本 Agent Toolkit；单工具失败只打日志。
     */
    @Override
    public void attachTools(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        CatalogToolRegistrationReport report =
                toolRegistrar.register(agent.getToolkit(), semantics.tools(), productTools);
        if (!report.failed().isEmpty()) {
            LOG.warning("companion catalog tools partial failure failed=" + report.failed()
                    + " registered=" + report.registered());
        }
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

    private void applyCompanionFilesystem(HarnessAgent.Builder builder) {
        DistributedStore store = stores.getIfAvailable();
        SandboxFilesystemSpec docker = CompanionSandboxFilesystem.createWhenEnabledOrNull(
                properties.getSandbox(), store);
        if (docker != null) {
            builder.filesystem(docker);
            return;
        }
        if (store != null) {
            builder.filesystem(WorkspaceFilesystem.userScoped());
        }
    }

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

    private static void onMcpRegistration(McpServerRegistrationResult result) {
        if (result == null) {
            return;
        }
        String name = result.serverName();
        String transport = result.transport();
        McpServerRegistrationResult.Status status = result.status();
        if (status == McpServerRegistrationResult.Status.SUCCESS) {
            LOG.info(() -> "MCP registered: name=" + name + ", transport=" + transport);
            return;
        }
        Throwable cause = result.cause();
        String detail = cause == null ? "" : cause.getMessage();
        LOG.warning("MCP " + status + ": name=" + name + ", transport=" + transport + ", detail=" + detail);
    }
}
