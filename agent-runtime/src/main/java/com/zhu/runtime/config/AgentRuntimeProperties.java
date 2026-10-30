package com.zhu.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行内核的稳定配置。这些值在进程启动时确定，不放进可热更新的产品配置。
 */
@ConfigurationProperties(prefix = "agentscope.runtime")
public class AgentRuntimeProperties {

    /** 模型坐标，例如 dashscope:qwen-plus。 */
    private String model = "dashscope:qwen-plus";

    /** 默认状态命名空间。改展示名不能改这个值，否则会指到空状态。 */
    private String agentId = "product-agent";

    /** 展示名，工作区按它分桶。 */
    private String name = "product-agent";

    /** 工作区路径。空则用框架默认目录。 */
    private String workspace = "";

    /** 单次执行的最大推理轮数。 */
    private int maxIters = 20;

    /** 等待模型返回的最长时间，单位秒。 */
    private int timeoutSeconds = 120;

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
}
