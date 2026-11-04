package com.zhu.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行内核配置。model / 超时 / 轮数 / 字数 / 写文件上限可由 Nacos 业务 yaml 热更；
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
}
