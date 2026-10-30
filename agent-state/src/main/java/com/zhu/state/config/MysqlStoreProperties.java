package com.zhu.state.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MySQL DistributedStore 开关。默认关闭，没有数据库时进程仍能启动。
 */
@ConfigurationProperties(prefix = "agentscope.distributed.mysql")
public class MysqlStoreProperties {

    /** 为 true 时才装配 DistributedStore。 */
    private boolean enabled;

    /**
     * 为 false 时不让框架自动建表，表结构只由 Flyway 管理。
     */
    private boolean createIfNotExist;

    /** 是否装配 MySQL DistributedStore。 */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 为 true 时框架启动时建表；生产应为 false。 */
    public boolean isCreateIfNotExist() {
        return createIfNotExist;
    }

    public void setCreateIfNotExist(boolean createIfNotExist) {
        this.createIfNotExist = createIfNotExist;
    }
}
