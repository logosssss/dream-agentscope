package com.zhu.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接入层额度。0 表示这一项不限制，避免本地没配 Redis 时把对话直接拒绝。
 */
@ConfigurationProperties(prefix = "agentscope.access")
public class AccessLimitProperties {

    /** 每个租户用户每天可发起的对话次数。0 不限制。 */
    private int dailyLimit;

    /** 每个用户每分钟可打到同一入口的次数。0 不限制。 */
    private int perMinute;

    /** 当日上限。 */
    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 每分钟上限。 */
    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }
}
