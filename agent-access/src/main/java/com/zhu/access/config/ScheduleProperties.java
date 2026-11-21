package com.zhu.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务调度触发面的开关。默认关：打开后用户进程才会按 {@code next_fire_at} 取调度并发布任务。
 */
@ConfigurationProperties(prefix = "agentscope.schedule")
public class ScheduleProperties {

    /** 是否打开触发面。默认 false，配置中心改成 true 才开始。 */
    private boolean enabled = false;

    /** 扫描间隔，毫秒。 */
    private long intervalMillis = 30_000L;

    /** 首次扫描延迟，毫秒。让进程先起完再扫。 */
    private long initialDelayMillis = 20_000L;

    /** 一次最多取几条。防止积压时一口气把线程占满。 */
    private int batchSize = 20;

    /** 是否打开触发面。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 写入开关。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 扫描间隔，毫秒。 */
    public long getIntervalMillis() {
        return intervalMillis;
    }

    /** 写入扫描间隔。 */
    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    /** 首次扫描延迟，毫秒。 */
    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    /** 写入首次扫描延迟。 */
    public void setInitialDelayMillis(long initialDelayMillis) {
        this.initialDelayMillis = initialDelayMillis;
    }

    /** 一次最多取几条。 */
    public int getBatchSize() {
        return batchSize;
    }

    /** 写入批量大小。 */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
