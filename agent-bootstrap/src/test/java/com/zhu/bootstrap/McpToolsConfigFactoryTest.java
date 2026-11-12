package com.zhu.bootstrap;

import com.zhu.runtime.config.AgentRuntimeProperties;
import com.zhu.runtime.infrastructure.agent.HarnessAgentFactory;
import com.zhu.runtime.infrastructure.agent.McpToolsConfigFactory;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.tools.McpConnectionException;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.McpServerRegistrar;
import io.agentscope.harness.agent.tools.ToolsConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MCP 落地 ②：空清单不装配；占位展开；required 硬/软失败；companion 源码接线、echo 不挂。
 * 不连真实 MCP 进程或外网。
 */
class McpToolsConfigFactoryTest {

    /** 空 servers 返回 null，调用方不得设 toolsConfig。 */
    @Test
    void emptyServersYieldsNullToolsConfig() {
        AgentRuntimeProperties.Mcp mcp = new AgentRuntimeProperties.Mcp();
        assertThat(McpToolsConfigFactory.createOrNull(mcp)).isNull();
        assertThat(McpToolsConfigFactory.createOrNull(null)).isNull();
    }

    /** 绑定字段映射到框架配置，且 {@code ${ENV}} 按 lookup 展开。 */
    @Test
    void mapsFieldsAndResolvesPlaceholders() {
        AgentRuntimeProperties.Server server = new AgentRuntimeProperties.Server();
        server.setTransport("stdio");
        server.setCommand("${MCP_CMD}");
        server.setArgs(java.util.List.of("-y", "${MCP_PKG}"));
        server.setEnv(Map.of("TOKEN", "prefix-${MCP_TOKEN}-suffix"));
        server.setUrl("https://example/${MCP_PATH}");
        server.setRequired(true);
        server.setPrefixToolNames(true);

        AgentRuntimeProperties.Mcp mcp = new AgentRuntimeProperties.Mcp();
        mcp.setServers(Map.of("demo", server));

        // 注入假环境，不读真实 getenv。
        Map<String, McpServerConfig> resolved = McpToolsConfigFactory.toServers(
                mcp,
                key -> switch (key) {
                    case "MCP_CMD" -> "npx";
                    case "MCP_PKG" -> "@scope/pkg";
                    case "MCP_TOKEN" -> "secret";
                    case "MCP_PATH" -> "mcp";
                    default -> null;
                });
        assertThat(resolved).containsOnlyKeys("demo");
        McpServerConfig cfg = resolved.get("demo");
        assertThat(cfg.getTransport()).isEqualTo("stdio");
        assertThat(cfg.getCommand()).isEqualTo("npx");
        assertThat(cfg.getArgs()).containsExactly("-y", "@scope/pkg");
        assertThat(cfg.getEnv()).containsEntry("TOKEN", "prefix-secret-suffix");
        assertThat(cfg.getUrl()).isEqualTo("https://example/mcp");
        assertThat(cfg.isRequired()).isTrue();
        assertThat(cfg.isPrefixToolNames()).isTrue();

        ToolsConfig tools = McpToolsConfigFactory.createOrNull(mcp);
        assertThat(tools).isNotNull();
        assertThat(tools.getMcpServers()).containsOnlyKeys("demo");
    }

    /** 缺省占位按空串，避免把未替换的 ${} 交给子进程。 */
    @Test
    void missingEnvBecomesEmpty() {
        AgentRuntimeProperties.Server server = new AgentRuntimeProperties.Server();
        server.setTransport("sse");
        server.setUrl("https://x/${MISSING_TOKEN}");
        AgentRuntimeProperties.Mcp mcp = new AgentRuntimeProperties.Mcp();
        mcp.setServers(Map.of("s", server));

        McpServerConfig cfg = McpToolsConfigFactory.toServers(mcp, key -> null).get("s");
        assertThat(cfg.getUrl()).isEqualTo("https://x/");
    }

    /**
     * 框架 required 语义：非法 transport 时 soft 不抛、hard 抛 {@link McpConnectionException}。
     * 不启动真实 MCP。
     */
    @Test
    void requiredSemanticsViaRegistrar() {
        Toolkit toolkit = new Toolkit();
        McpServerConfig soft = new McpServerConfig();
        soft.setTransport("not-a-real-transport");
        soft.setRequired(false);
        assertThatCode(() -> McpServerRegistrar.register(toolkit, Map.of("soft", soft)))
                .doesNotThrowAnyException();

        McpServerConfig hard = new McpServerConfig();
        hard.setTransport("not-a-real-transport");
        hard.setRequired(true);
        assertThatThrownBy(() -> McpServerRegistrar.register(new Toolkit(), Map.of("hard", hard)))
                .isInstanceOf(McpConnectionException.class);
    }

    /** companion 源码须接线；echo 方法体不得出现 toolsConfig / MCP 工厂。 */
    @Test
    void companionWiresMcpEchoDoesNot() throws Exception {
        Path factory = Path.of("..", "agent-runtime", "src", "main", "java",
                        "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(factory)) {
            factory = Path.of("agent-runtime", "src", "main", "java",
                            "com", "zhu", "runtime", "infrastructure", "agent", "HarnessAgentFactory.java")
                    .toAbsolutePath()
                    .normalize();
        }
        String source = Files.readString(factory, StandardCharsets.UTF_8);
        int companionStart = source.indexOf("public void customizeCompanion");
        int echoStart = source.indexOf("public void customizeEcho", companionStart);
        int echoEnd = source.indexOf("public static SubagentDeclaration subagentProbe", echoStart);
        assertThat(companionStart).isGreaterThanOrEqualTo(0);
        assertThat(echoStart).isGreaterThan(companionStart);
        assertThat(echoEnd).isGreaterThan(echoStart);

        String companion = source.substring(companionStart, echoStart);
        String echo = source.substring(echoStart, echoEnd);
        assertThat(companion)
                .as("customizeCompanion 须经工厂装配 MCP")
                .contains("McpToolsConfigFactory.createOrNull")
                .contains("toolsConfig(")
                .contains("mcpServerRegistrationListener");
        assertThat(echo)
                .as("customizeEcho 不得挂 MCP")
                .doesNotContain("McpToolsConfigFactory")
                .doesNotContain("toolsConfig(")
                .doesNotContain("mcpServerRegistrationListener");
        assertThat(HarnessAgentFactory.SUBAGENT_PROBE_NAME).isEqualTo("subagent-probe");
    }

    /** 工厂 toServers 跳过空白名与 null 项。 */
    @Test
    void skipsBlankNames() {
        Map<String, AgentRuntimeProperties.Server> raw = new LinkedHashMap<>();
        raw.put(" ", new AgentRuntimeProperties.Server());
        raw.put("ok", new AgentRuntimeProperties.Server());
        raw.put("gone", null);
        AgentRuntimeProperties.Mcp mcp = new AgentRuntimeProperties.Mcp();
        mcp.setServers(raw);
        AgentRuntimeProperties.Server ok = mcp.getServers().get("ok");
        ok.setTransport("stdio");
        ok.setCommand("echo");

        assertThat(McpToolsConfigFactory.toServers(mcp, key -> null)).containsOnlyKeys("ok");
    }
}
