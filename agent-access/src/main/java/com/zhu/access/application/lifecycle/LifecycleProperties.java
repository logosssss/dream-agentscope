package com.zhu.access.application.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据生命周期旋钮：删除请求的执行与保留策略的清理。
 *
 * <p>默认全部关闭：进程起来不会自己开始删数据。运维在 Nacos 里显式打开，
 * 并且 {@code dry-run} 默认 true —— 第一次打开先看报告，确认计数无误再关掉 dry-run。
 */
@ConfigurationProperties(prefix = "agentscope.lifecycle")
public class LifecycleProperties {

    /** 定时执行开关。关着时 worker 空转，不碰库。 */
    private boolean enabled = false;

    /** 只统计不删。第一次上线必须先用它核计数。 */
    private boolean dryRun = true;

    /** 单表一次最多删多少行；超过就整表跳过，留给人工。 */
    private int maxRowsPerTable = 5000;

    /** 执行器轮询间隔，毫秒。 */
    private long intervalMillis = 60_000;

    /** 保留策略清理开关。与删除请求执行分开，便于只开一个。 */
    private boolean retentionEnabled = false;

    /** 保留策略单次每表最多删多少行。 */
    private int retentionBatchRows = 1000;

    /** 定时执行开关。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 写入定时执行开关。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 是否只统计不删。 */
    public boolean isDryRun() {
        return dryRun;
    }

    /** 写入是否只统计不删。 */
    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /** 单表行数上限。 */
    public int getMaxRowsPerTable() {
        return maxRowsPerTable;
    }

    /** 写入单表行数上限。 */
    public void setMaxRowsPerTable(int maxRowsPerTable) {
        this.maxRowsPerTable = maxRowsPerTable;
    }

    /** 轮询间隔毫秒。 */
    public long getIntervalMillis() {
        return intervalMillis;
    }

    /** 写入轮询间隔毫秒。 */
    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    /** 保留策略清理开关。 */
    public boolean isRetentionEnabled() {
        return retentionEnabled;
    }

    /** 写入保留策略清理开关。 */
    public void setRetentionEnabled(boolean retentionEnabled) {
        this.retentionEnabled = retentionEnabled;
    }

    /** 保留策略单次每表行数。 */
    public int getRetentionBatchRows() {
        return retentionBatchRows;
    }

    /** 写入保留策略单次每表行数。 */
    public void setRetentionBatchRows(int retentionBatchRows) {
        this.retentionBatchRows = retentionBatchRows;
    }
}
