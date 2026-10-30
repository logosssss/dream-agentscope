package com.zhu.state.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentscope.distributed.mysql")
public class MysqlStoreProperties {

    private boolean enabled;

    private boolean createIfNotExist;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCreateIfNotExist() {
        return createIfNotExist;
    }

    public void setCreateIfNotExist(boolean createIfNotExist) {
        this.createIfNotExist = createIfNotExist;
    }
}
