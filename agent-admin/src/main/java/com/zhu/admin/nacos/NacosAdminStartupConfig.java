package com.zhu.admin.nacos;

import java.util.Set;

/**
 * 管理进程启动配置在 Nacos 上的约定。只拉一次，不热更新。
 * 与用户进程的 agent-bootstrap.yaml 分开；不做服务发现和 MCP。
 */
public final class NacosAdminStartupConfig {

    /** dataId。控制台按这个名字建配置。 */
    public static final String DATA_ID = "agent-admin.yaml";

    /** group。和用户进程同一平台组，dataId 不同。 */
    public static final String GROUP = "AGENT_PLATFORM";

    /** 放进 Environment 的属性源名。 */
    public static final String PROPERTY_SOURCE = "nacosAdminConfig";

    /** 拉取超时毫秒。超时按启动失败处理。 */
    public static final long TIMEOUT_MS = 3000L;

    /**
     * 允许覆盖的键。管理进程当前没有额度/模型一类旋钮，集合为空：
     * 仍要求 Nacos 正文非空可解析，其它键一律丢掉，连接与密钥不从这里进。
     */
    public static final Set<String> ALLOWED_KEYS = Set.of();

    private NacosAdminStartupConfig() {
    }
}
