package com.zhu.bootstrap.nacos;

import java.util.Set;

/**
 * 用户进程启动配置在 Nacos 上的约定。只拉一次，不热更新。
 * 管理进程不读这份。提示词仍走 AI Prompt 监听。
 */
public final class NacosStartupConfig {

    /** dataId。控制台按这个名字建配置。 */
    public static final String DATA_ID = "agent-bootstrap.yaml";

    /** group。和指南里的 AGENT_PLATFORM 对齐。 */
    public static final String GROUP = "AGENT_PLATFORM";

    /** 放进 Environment 的属性源名。 */
    public static final String PROPERTY_SOURCE = "nacosBootstrapConfig";

    /** 拉取超时毫秒。超时按启动失败处理。 */
    public static final long TIMEOUT_MS = 3000L;

    /**
     * 允许覆盖的键。其它键一律丢掉，避免把数据源或密钥从配置中心灌进来。
     */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "agentscope.access.daily-limit",
            "agentscope.access.per-minute",
            "agentscope.access.login-session-seconds",
            "agentscope.runtime.max-user-chars",
            "agentscope.runtime.timeout-seconds",
            "agentscope.runtime.model",
            "agentscope.runtime.max-iters",
            "agentscope.runtime.max-write-bytes");

    private NacosStartupConfig() {
    }
}
