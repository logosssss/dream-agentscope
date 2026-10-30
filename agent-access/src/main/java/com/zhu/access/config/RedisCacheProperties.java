package com.zhu.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 只在打开时连接。默认关闭，配置里不写死地址。
 */
@ConfigurationProperties(prefix = "agentscope.cache.redis")
public class RedisCacheProperties {

    /** 打开后才创建连接。关闭时用进程内计数。 */
    private boolean enabled;

    /** 服务端主机。enabled 为 true 时必填。 */
    private String host = "";

    /** 服务端端口。未填写时用 Redis 默认端口。 */
    private int port = 6379;

    /** 密码。空表示不认证。不要写进仓库里的真实口令。 */
    private String password = "";

    /** 是否启用 Redis。 */
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
