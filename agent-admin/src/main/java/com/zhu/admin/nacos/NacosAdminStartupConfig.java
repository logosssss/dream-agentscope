package com.zhu.admin.nacos;

/**
 * 管理进程 Nacos 结构约定：dataId / group / 属性源名。
 * 允许哪些键见 classpath {@code nacos-allowlist.yml}。
 */
public final class NacosAdminStartupConfig {

    /** 业务占位 dataId。正文非空即可；白名单为空时不合并任何键。 */
    public static final String BUSINESS_DATA_ID = "agent-admin.yaml";

    /** 连接与密钥 dataId。只启动合并。 */
    public static final String INFRA_DATA_ID = "agent-admin-infra.yaml";

    /** group。和用户进程同一平台组，dataId 不同。 */
    public static final String GROUP = "AGENT_PLATFORM";

    /** 业务属性源名。 */
    public static final String BUSINESS_PROPERTY_SOURCE = "nacosAdminConfig";

    /** infra 属性源名。 */
    public static final String INFRA_PROPERTY_SOURCE = "nacosAdminInfraConfig";

    /** 拉取超时毫秒。超时按启动失败处理。 */
    public static final long TIMEOUT_MS = 3000L;

    private NacosAdminStartupConfig() {
    }
}
