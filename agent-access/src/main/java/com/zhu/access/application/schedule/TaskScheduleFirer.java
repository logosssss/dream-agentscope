package com.zhu.access.application.schedule;

import com.zhu.access.config.ScheduleProperties;
import com.zhu.common.exception.BusinessException;
import com.zhu.ops.schedule.ScheduleTimes;
import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.publish.AgentTaskPublisher;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 把到期的任务调度真的触发一次。
 *
 * <p>做法是最小闭环：取出到期的行 → 用旧 {@code next_fire_at} 做乐观锁推进到下一次 →
 * 通过 {@link AgentTaskPublisher} 发布一条 {@link AgentTask}（走与文档索引同一条任务管线，
 * 落 {@code agent_task} 并记审计）→ 把 {@code last_fire_at} / {@code last_status} / {@code fire_count} 写回。
 *
 * <p>先推进再发布：发布失败不会让同一行在每个扫描周期里重试到死，失败只留 {@code last_status=FAILED}，
 * 运维在管理端能看到。要重跑就把 {@code next_fire_at} 改到过去，或直接恢复一次调度。
 */
@Component
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class TaskScheduleFirer {

    /** 任务标识前缀，和文档索引的 taskId 区分开。 */
    private static final String TASK_PREFIX = "sched-";

    /** agent_task.task_id / schedule_id 的列宽。 */
    private static final int TASK_ID_LIMIT = 128;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Logger log = Logger.getLogger(TaskScheduleFirer.class.getName());

    private final ScheduleQueue schedules;

    private final AgentTaskPublisher publisher;

    private final ScheduleProperties properties;

    /** 到期队列由接入层实现，发布走运行层的任务口。 */
    public TaskScheduleFirer(ScheduleQueue schedules, AgentTaskPublisher publisher,
                             ScheduleProperties properties) {
        this.schedules = schedules;
        this.publisher = publisher;
        this.properties = properties;
    }

    /**
     * 定时扫描。开关关着时什么都不做——默认配置下这个类只是被装配，不产生任何副作用。
     */
    @Scheduled(fixedDelayString = "${agentscope.schedule.interval-millis:30000}",
            initialDelayString = "${agentscope.schedule.initial-delay-millis:20000}")
    public void scheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int fired = runOnce();
            if (fired > 0) {
                log.info("task schedules fired: " + fired);
            }
        } catch (RuntimeException ex) {
            log.warning("task schedule scan failed: " + ex.getClass().getSimpleName());
        }
    }

    /**
     * 扫一轮，返回成功发布的条数。异常按行吞掉，一行坏不影响下一行。
     *
     * <p>公开是给测试和运维脚本用的；正常路径由 {@link #scheduled()} 调用。
     */
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<AgentTaskSchedule> due = schedules.due(now, properties.getBatchSize());
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int fired = 0;
        for (AgentTaskSchedule row : due) {
            if (fire(row, now)) {
                fired++;
            }
        }
        return fired;
    }

    /** 单行：推进下次触发，再发布。抢不到或算出非法下次时间的都不算触发。 */
    private boolean fire(AgentTaskSchedule row, LocalDateTime now) {
        LocalDateTime expected = row.getNextFireAt();
        LocalDateTime next;
        try {
            next = ScheduleTimes.nextFireAt(row.getCronExpr(), row.getDelaySeconds(), now);
        } catch (BusinessException ex) {
            // 登记时校验过，运行时仍可能因为表达式被改坏而算不出来：留痕并停在这里，不再重试。
            schedules.markFired(row.getId(), now, "FAILED");
            return false;
        }
        if (schedules.advance(row.getId(), expected, next) != 1) {
            return false;
        }
        String status;
        try {
            publisher.publish(task(row, now));
            status = "SUCCEEDED";
        } catch (RuntimeException ex) {
            status = "FAILED";
        }
        schedules.markFired(row.getId(), now, status);
        return "SUCCEEDED".equals(status);
    }

    /**
     * 一条调度对应一条任务：类型原样传，参数只带调度标识（表里不存任务参数）。
     * 同一调度在同一秒内重复触发时任务标识会撞唯一键，正好挡掉重复执行。
     */
    private static AgentTask task(AgentTaskSchedule row, LocalDateTime now) {
        String taskId = TASK_PREFIX + row.getScheduleId() + "@" + now.format(STAMP);
        if (taskId.length() > TASK_ID_LIMIT) {
            taskId = taskId.substring(0, TASK_ID_LIMIT);
        }
        return new AgentTask(
                taskId,
                row.getTaskType(),
                row.getTenantId() == null ? "" : row.getTenantId(),
                row.getUserId(),
                row.getAgentId(),
                null,
                Map.of("scheduleId", row.getScheduleId()),
                Instant.now());
    }
}
