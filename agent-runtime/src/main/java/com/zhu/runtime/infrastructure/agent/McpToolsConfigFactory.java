package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.config.AgentRuntimeProperties;
import io.agentscope.harness.agent.tools.McpServerConfig;
import io.agentscope.harness.agent.tools.ToolsConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行层：把 {@link AgentRuntimeProperties.Mcp} 转成 Harness {@link ToolsConfig}。
 * 空清单返回 null，调用方不得设 {@code toolsConfig}，避免覆盖工作区默认工具过滤。
 * 字符串里的 {@code ${ENV}} 在此展开；缺省环境变量按空串，密钥不进 Nacos 明文。
 */
public final class McpToolsConfigFactory {

    /** 仅匹配 {@code ${NAME}}；NAME 为字母/数字/下划线。 */
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private McpToolsConfigFactory() {
    }

    /**
     * 有有效 server 时构造 ToolsConfig；否则 null。
     * 默认工具过滤保持框架默认（defaultToolsEnabled=true），只挂 mcpServers。
     */
    public static ToolsConfig createOrNull(AgentRuntimeProperties.Mcp mcp) {
        Map<String, McpServerConfig> servers = toServers(mcp, McpToolsConfigFactory::lookupEnv);
        if (servers.isEmpty()) {
            return null;
        }
        ToolsConfig tools = new ToolsConfig();
        tools.setMcpServers(servers);
        return tools;
    }

    /**
     * 属性 → 框架 McpServerConfig；跳过空名与 null 项。
     * {@code envLookup} 可注入，便于单测不碰真实进程环境。
     */
    public static Map<String, McpServerConfig> toServers(
            AgentRuntimeProperties.Mcp mcp,
            UnaryOperator<String> envLookup) {
        Map<String, McpServerConfig> out = new LinkedHashMap<>();
        if (mcp == null || mcp.getServers() == null || mcp.getServers().isEmpty()) {
            return out;
        }
        UnaryOperator<String> lookup = envLookup == null ? McpToolsConfigFactory::lookupEnv : envLookup;
        for (Map.Entry<String, AgentRuntimeProperties.Server> entry : mcp.getServers().entrySet()) {
            String name = entry.getKey();
            AgentRuntimeProperties.Server src = entry.getValue();
            if (name == null || name.isBlank() || src == null) {
                continue;
            }
            out.put(name.trim(), toServerConfig(src, lookup));
        }
        return out;
    }

    /** 单项映射；required 默认 false，交给框架做硬/软失败。 */
    private static McpServerConfig toServerConfig(
            AgentRuntimeProperties.Server src,
            UnaryOperator<String> envLookup) {
        McpServerConfig cfg = new McpServerConfig();
        cfg.setTransport(resolve(src.getTransport(), envLookup));
        cfg.setCommand(resolve(src.getCommand(), envLookup));
        cfg.setArgs(resolveList(src.getArgs(), envLookup));
        cfg.setEnv(resolveMap(src.getEnv(), envLookup));
        cfg.setUrl(resolve(src.getUrl(), envLookup));
        cfg.setHeaders(resolveMap(src.getHeaders(), envLookup));
        cfg.setQueryParams(resolveMap(src.getQueryParams(), envLookup));
        cfg.setRequired(src.isRequired());
        cfg.setPrefixToolNames(src.isPrefixToolNames());
        cfg.setDefaultToolsEnabled(src.isDefaultToolsEnabled());
        cfg.setEnableTools(copyList(src.getEnableTools()));
        cfg.setDisableTools(copyList(src.getDisableTools()));
        if (src.getTimeout() != null) {
            cfg.setTimeout(src.getTimeout());
        }
        if (src.getInitializationTimeout() != null) {
            cfg.setInitializationTimeout(src.getInitializationTimeout());
        }
        return cfg;
    }

    /** 展开字符串中全部 {@code ${ENV}}；lookup 返回 null 时按空串。 */
    static String resolve(String raw, UnaryOperator<String> envLookup) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = envLookup.apply(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static List<String> resolveList(List<String> raw, UnaryOperator<String> envLookup) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String item : raw) {
            out.add(resolve(item, envLookup));
        }
        return List.copyOf(out);
    }

    private static Map<String, String> resolveMap(Map<String, String> raw, UnaryOperator<String> envLookup) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(e.getKey(), resolve(e.getValue(), envLookup));
        }
        return Map.copyOf(out);
    }

    private static List<String> copyList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return List.copyOf(raw.stream().filter(Objects::nonNull).toList());
    }

    /** 先 getenv，再 system property；都没有返回 null。 */
    static String lookupEnv(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String fromEnv = System.getenv(key);
        if (fromEnv != null) {
            return fromEnv;
        }
        return System.getProperty(key);
    }
}
