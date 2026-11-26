package com.zhu.product.companion.binding;

import com.zhu.product.api.ProductModule;
import com.zhu.product.companion.semantic.CompanionSemantics;
import com.zhu.runtime.application.turn.ResolvedChatModel;
import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrationReport;
import com.zhu.runtime.infrastructure.agent.CatalogToolRegistrar;
import com.zhu.runtime.infrastructure.agent.CompanionMiddlewareChain;
import com.zhu.runtime.infrastructure.agent.CompanionSandboxFilesystem;
import com.zhu.runtime.infrastructure.agent.McpToolsConfigFactory;
import com.zhu.runtime.infrastructure.agent.WorkspaceFilesystem;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.api.tool.ToolCatalogService;
import com.zhu.semantic.contract.product.CompletionCriteria;
import com.zhu.semantic.contract.tool.ProductTool;
import com.zhu.semantic.contract.tool.ToolDescriptor;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import io.agentscope.harness.agent.tools.McpServerRegistrationListener;
import io.agentscope.harness.agent.tools.McpServerRegistrationResult;
import io.agentscope.harness.agent.tools.ToolsConfig;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 搭子产品模块：一个 Bean 同时提供语义（agentId / 工具 / 轮数 / 提示词兜底）、装配（customize）与工具挂载。
 *
 * <p>原先是 {@code CompanionResidentAgentBinding} 与 {@code PlatformSemanticProvider} 两个 Bean，
 * 同一份 agentId 两处维护。本类是 main Agent，也是容器里 {@code @Primary} 的
 * {@code ProductSemanticProvider}。文档产品 {@code docs-v1} 是另一个 {@code ProductModule}，
 * {@code mainAgent()} 为 false；单点注入仍落到这里，避免第二个产品把对话入口切走。
 *
 * <p>中间件顺序经 runtime {@link CompanionMiddlewareChain}（C1）。
 */
@Primary
@Component
public class CompanionResidentAgentBinding implements ProductModule {

    private static final Logger LOG = Logger.getLogger(CompanionResidentAgentBinding.class.getName());

    /** companion 静态子代理探针名。对照用占位，不承诺产品能力。 */
    public static final String SUBAGENT_PROBE_NAME = "subagent-probe";

    /** 探针子代理提示词：极简短答，零工具。 */
    public static final String SUBAGENT_PROBE_PROMPT = "你是对照用占位子代理。用一两句中文直接短答，不要调用工具。";

    private static final McpServerRegistrationListener MCP_LISTENER =
            CompanionResidentAgentBinding::onMcpRegistration;

    private final AgentRuntimeProperties properties;

    private final ResolvedChatModel chatModel;

    private final CompanionSemantics semantics;

    private final ObjectProvider<DistributedStore> stores;

    private final CompanionMiddlewareChain companionMiddlewares;

    private final List<ProductTool> productTools;

    private final PromptService prompts;

    private final CatalogToolRegistrar toolRegistrar = new CatalogToolRegistrar();

    /**
     * 语义数值从运行配置取一次，语义与装配共用；工具目录在 MySQL 关闭时为空。
     *
     * @param properties            运行配置
     * @param chatModel             已解析的模型
     * @param catalog               工具目录
     * @param stores                分布式存储，可缺席
     * @param companionMiddlewares  产品中间件链
     * @param productTools          产品自带工具
     * @param prompts               平台提示词读取口（Nacos → 兜底）
     */
    public CompanionResidentAgentBinding(
            AgentRuntimeProperties properties,
            ResolvedChatModel chatModel,
            ToolCatalogService catalog,
            ObjectProvider<DistributedStore> stores,
            CompanionMiddlewareChain companionMiddlewares,
            List<ProductTool> productTools,
            PromptService prompts) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.semantics = new CompanionSemantics(properties.getAgentId(), properties.getMaxIters(), catalog);
        this.stores = stores;
        this.companionMiddlewares = companionMiddlewares;
        this.productTools = productTools == null ? List.of() : List.copyOf(productTools);
        this.prompts = prompts;
    }

    @Override
    public String agentId() {
        return semantics.agentId();
    }

    @Override
    public boolean mainAgent() {
        return true;
    }

    @Override
    public List<ToolDescriptor> tools() {
        return semantics.tools();
    }

    @Override
    public CompletionCriteria completion() {
        return semantics.completion();
    }

    @Override
    public String promptFallback(String key) {
        return semantics.promptFallback(key);
    }

    /**
     * 长驻 Agent 的系统提示词：平台键先问外部来源（Nacos），取不到才用产品兜底。
     *
     * <p>早先这里直接取 {@link #promptFallback}，于是 Nacos 提示词热更、模板校验与发布
     * 对线上人格零效果——改的是接入层那份只用于校验的字符串。外部来源读失败时
     * {@link PromptService} 自己会退回兜底，不会把空串交给模型。
     *
     * <p>外部模板在 Agent 装配时读一次：改完要重建（`rebuildAll`）或重启才生效。
     */
    public String systemPrompt() {
        if (prompts == null) {
            return promptFallback(PlatformPrompt.SYSTEM_KEY);
        }
        return prompts.get(PlatformPrompt.SYSTEM_KEY, Map.of(), promptFallback(PlatformPrompt.SYSTEM_KEY));
    }

    /**
     * 搭子差异：标识、模型、提示词、轮数、产品中间件与工作区路径。
     * 挂静态探针；MCP / Sandbox / Web 工具仅 companion。
     * web-tools.enabled=false（默认）时关内置 web_search/web_fetch；Shell 仍由公共项关闭。
     */
    @Override
    public void customize(HarnessAgent.Builder builder) {
        String id = agentId();
        if (!properties.getWebTools().isEnabled()) {
            builder.disableWebTools();
        }
        builder.name(properties.getName())
                .agentId(id)
                .model(chatModel.resolve(null))
                .sysPrompt(systemPrompt())
                .maxIters(completion().maxIterations());
        if (properties.getWorkspace() != null && !properties.getWorkspace().isBlank()) {
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
                toolRegistrar.register(agent.getToolkit(), tools(), productTools);
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
