package com.zhu.runtime.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行内核配置。model / 超时 / 轮数 / 字数 / 写文件上限 / MCP / Sandbox / Wakeup 旋钮可由 Nacos 业务 yaml 热更；
 * agentId / name / workspace 不在热更白名单，改身份仍要升版本。
 */
@ConfigurationProperties(prefix = "agentscope.runtime")
public class AgentRuntimeProperties {

    /** 模型坐标，例如 dashscope:qwen-plus。 */
    private String model = "dashscope:qwen-plus";

    /** 默认状态命名空间。改核心身份时升版本，不要把 userId 拼进来。 */
    private String agentId = "companion-v1";

    /** 展示名，工作区按它分桶。 */
    private String name = "companion";

    /** 工作区路径。空则用框架默认目录。 */
    private String workspace = "";

    /** 单次执行的最大推理轮数。 */
    private int maxIters = 20;

    /** 等待模型返回的最长时间，单位秒。 */
    private int timeoutSeconds = 120;

    /** 用户输入的字符上限。小于 1 时按 32768，避免配成 0 后不再限制。 */
    private int maxUserChars = 32768;

    /** 单次写文件 UTF-8 字节上限。小于 1 时按 5MB。 */
    private int maxWriteBytes = 5 * 1024 * 1024;

    /**
     * MCP 清单。空 servers 表示 companion 不连；仅 companion 装配，见 docs/mcp-落地.md。
     * 白名单前缀 {@code agentscope.runtime.mcp.servers}。
     */
    private Mcp mcp = new Mcp();

    /**
     * Sandbox 旋钮。默认关；仅 companion 在 enabled=true 时换 DockerFilesystem，见 docs/sandbox-落地.md。
     * 白名单前缀 {@code agentscope.runtime.sandbox}。
     */
    private Sandbox sandbox = new Sandbox();

    /**
     * Wakeup 旋钮。默认关；enabled=true 才启进程内 Bus + Dispatcher，见 docs/wakeup-落地.md。
     * 白名单前缀 {@code agentscope.runtime.wakeup}；令牌只走环境变量。
     */
    private Wakeup wakeup = new Wakeup();

    /** 模型坐标。 */
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /** 默认 Agent 标识。 */
    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /** 展示名。 */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 工作区路径，可能为空。 */
    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    /** 最大推理轮数。 */
    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    /** 调用超时秒数。 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 用户输入字符上限。 */
    public int getMaxUserChars() {
        return maxUserChars;
    }

    public void setMaxUserChars(int maxUserChars) {
        this.maxUserChars = maxUserChars;
    }

    /** 单次写文件字节上限。 */
    public int getMaxWriteBytes() {
        return maxWriteBytes;
    }

    /** 写入单次写文件字节上限。 */
    public void setMaxWriteBytes(int maxWriteBytes) {
        this.maxWriteBytes = maxWriteBytes;
    }

    /** MCP 根节点。 */
    public Mcp getMcp() {
        return mcp;
    }

    /** 写入 MCP 根节点；null 按空清单。 */
    public void setMcp(Mcp mcp) {
        this.mcp = mcp == null ? new Mcp() : mcp;
    }

    /** Sandbox 根节点。 */
    public Sandbox getSandbox() {
        return sandbox;
    }

    /** 写入 Sandbox 根节点；null 按默认关。 */
    public void setSandbox(Sandbox sandbox) {
        this.sandbox = sandbox == null ? new Sandbox() : sandbox;
    }

    /** Wakeup 根节点。 */
    public Wakeup getWakeup() {
        return wakeup;
    }

    /** 写入 Wakeup 根节点；null 按默认关。 */
    public void setWakeup(Wakeup wakeup) {
        this.wakeup = wakeup == null ? new Wakeup() : wakeup;
    }

    /**
     * 运行层 Wakeup 配置。enabled 控制接入层 Coordinator 是否启进程内 Bus + Dispatcher。
     */
    public static class Wakeup {

        /** false 时不启 Dispatcher；true 才装配进程内 Bus 与 companion Target。 */
        private boolean enabled;

        /** 是否打开 Harness 唤醒面。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 写入开关。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 运行层 Sandbox 配置。本块只绑属性；Docker 接线见落地 ②。
     */
    public static class Sandbox {

        /** 指南推荐默认镜像；image 为空时接线层回退到此常量。 */
        public static final String DEFAULT_IMAGE = "ubuntu:24.04";

        /** false 时 companion 仍用 RemoteFilesystem；true 才换 Docker。 */
        private boolean enabled;

        /**
         * Docker 镜像 tag。空则接线时用 {@link #DEFAULT_IMAGE}。
         * 拉取凭证不进本字段，只用环境/宿主机 Docker 配置。
         */
        private String image = "";

        /** 是否打开 companion Docker 文件系统。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 写入开关。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 当前镜像；可能为空串。 */
        public String getImage() {
            return image;
        }

        /** 写入镜像；null 按空串。 */
        public void setImage(String image) {
            this.image = image == null ? "" : image;
        }
    }

    /**
     * 运行层 MCP 配置根。只承载 servers map，不写死传输默认值。
     */
    public static class Mcp {

        /** name → 单项；热更后走 rebuildAll 重建。 */
        private Map<String, Server> servers = new LinkedHashMap<>();

        /** 当前清单；可能为空。 */
        public Map<String, Server> getServers() {
            return servers;
        }

        /** 写入清单；null 按空 map。 */
        public void setServers(Map<String, Server> servers) {
            this.servers = servers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(servers);
        }
    }

    /**
     * 单项 MCP 服务器。字段对齐框架 {@code McpServerConfig}；
     * env/url/headers 等字符串可写 {@code ${ENV}}，装配时展开。
     */
    public static class Server {

        /** 传输：stdio / sse / http / streamable-http（框架同义）。 */
        private String transport;

        /** stdio 可执行文件；可含环境变量占位。 */
        private String command;

        /** stdio 参数列表。 */
        private List<String> args = new ArrayList<>();

        /** stdio 附加环境；值可含 {@code ${ENV}}。 */
        private Map<String, String> env = new LinkedHashMap<>();

        /** SSE / HTTP 地址；可含占位。 */
        private String url;

        /** 额外请求头；值可含占位。 */
        private Map<String, String> headers = new LinkedHashMap<>();

        /** 额外查询参数；值可含占位。 */
        private Map<String, String> queryParams = new LinkedHashMap<>();

        /** true 时注册失败拒绝启动/重建；默认 false 软失败。 */
        private boolean required;

        /** 是否给工具名加 server 前缀。 */
        private boolean prefixToolNames;

        /** 是否启用该 server 默认工具集；默认 true。 */
        private boolean defaultToolsEnabled = true;

        /** 仅启用这些工具名；空表示不额外收窄。 */
        private List<String> enableTools = new ArrayList<>();

        /** 禁用这些工具名。 */
        private List<String> disableTools = new ArrayList<>();

        /** 调用超时；未配则交给框架默认。 */
        private Duration timeout;

        /** 初始化超时；未配则交给框架默认。 */
        private Duration initializationTimeout;

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args == null ? new ArrayList<>() : new ArrayList<>(args);
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env == null ? new LinkedHashMap<>() : new LinkedHashMap<>(env);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(queryParams);
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public boolean isPrefixToolNames() {
            return prefixToolNames;
        }

        public void setPrefixToolNames(boolean prefixToolNames) {
            this.prefixToolNames = prefixToolNames;
        }

        public boolean isDefaultToolsEnabled() {
            return defaultToolsEnabled;
        }

        public void setDefaultToolsEnabled(boolean defaultToolsEnabled) {
            this.defaultToolsEnabled = defaultToolsEnabled;
        }

        public List<String> getEnableTools() {
            return enableTools;
        }

        public void setEnableTools(List<String> enableTools) {
            this.enableTools = enableTools == null ? new ArrayList<>() : new ArrayList<>(enableTools);
        }

        public List<String> getDisableTools() {
            return disableTools;
        }

        public void setDisableTools(List<String> disableTools) {
            this.disableTools = disableTools == null ? new ArrayList<>() : new ArrayList<>(disableTools);
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getInitializationTimeout() {
            return initializationTimeout;
        }

        public void setInitializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = initializationTimeout;
        }
    }
}
