package com.zhu.bootstrap.nacos;

/**
 * 用户进程 Nacos 结构约定：dataId / group / 属性源名。
 * 允许哪些键进 Environment 见 classpath {@code nacos-allowlist.yml}。
 */
public final class NacosStartupConfig {

    /** 业务旋钮 dataId。可热更新。 */
    public static final String BUSINESS_DATA_ID = "agent-bootstrap.yaml";

    /** 连接与密钥 dataId。只启动合并。 */
    public static final String INFRA_DATA_ID = "agent-bootstrap-infra.yaml";

    /** group。和指南里的 AGENT_PLATFORM 对齐。 */
    public static final String GROUP = "AGENT_PLATFORM";

    /** 业务属性源名。热更时替换同名源。 */
    public static final String BUSINESS_PROPERTY_SOURCE = "nacosBootstrapConfig";

    /** infra 属性源名。启动后不再改。 */
    public static final String INFRA_PROPERTY_SOURCE = "nacosBootstrapInfraConfig";

    /** 拉取超时毫秒。超时按启动失败处理。 */
    public static final long TIMEOUT_MS = 3000L;

    private NacosStartupConfig() {
    }
}
