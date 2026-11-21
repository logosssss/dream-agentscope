package com.zhu.access.application.task;

import com.zhu.access.application.lifecycle.RetentionSweeper;
import com.zhu.runtime.api.event.AuditRecorder;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.handle.AgentTaskExecutor;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BATCH：把保留策略的批量清理做成一条可调度、可追溯的任务。
 *
 * <p>保留策略本来就有自己的定时器（{@link RetentionSweeper}），这里的价值是「按人触发一次并留痕」：
 * 运维改完策略想立刻验证、或者只想在某个租户上跑一轮时，不必等下一个周期，也不必去翻日志。
 *
 * <p>清理范围与安全阀完全复用 {@link RetentionSweeper#runOnce()}：只执行 {@code action=DELETE}，
 * 每张表按 {@code retention-batch-rows} 限量，附件只删表行不删字节。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class RetentionBatchExecutor implements AgentTaskExecutor {

    /** 任务类型。调度表里 task_type 填这个名字。 */
    public static final String TYPE = "BATCH";

    private final RetentionSweeper sweeper;

    private final AuditRecorder audit;

    public RetentionBatchExecutor(RetentionSweeper sweeper, AuditRecorder audit) {
        this.sweeper = sweeper;
        this.audit = audit;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * 跑一轮清理，把每张表删掉的行数写进审计。没有启用的策略时结果是空 Map。
     */
    @Override
    public void execute(AgentTask task) {
        Map<String, Integer> deleted = new TreeMap<>(sweeper.runOnce());
        audit.record("task.batch", task.userId(), "deleted=" + deleted);
    }
}
