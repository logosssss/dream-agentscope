package com.zhu.cache.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 族连接属性。enabled=false 时不建连，由测试或调用方提供替身；禁止生产内存回落。
 */
@ConfigurationProperties(prefix = "agentscope.cache.redis")
public class RedisCacheProperties {

    /** 打开后才创建 Lettuce / Redisson。 */
    private boolean enabled;

    /** 主机。enabled 为 true 时必填。 */
    private String host = "";

    /** 端口，默认 6379。 */
    private int port = 6379;

    /** 密码；空表示不认证。勿提交真实口令。 */
    private String password = "";

    /** 是否启用。 */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 主机，可能为空。 */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /** 端口。 */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /** 密码，可能为空。 */
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
