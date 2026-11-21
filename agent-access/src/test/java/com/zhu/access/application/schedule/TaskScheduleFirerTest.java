package com.zhu.access.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.config.ScheduleProperties;
import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import com.zhu.runtime.task.AgentTask;
import com.zhu.runtime.task.publish.AgentTaskPublisher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 触发面的顺序与失败处理。不起库、不 mock 胖接口：队列用几个 lambda 顶替。
 */
class TaskScheduleFirerTest {

    /** 到期的 cron 调度发布一条任务，下次触发推进，结果写回。 */
    @Test
    void firesDueScheduleAndAdvancesNextFire() {
        LocalDateTime due = LocalDateTime.now().minusMinutes(1);
        AgentTaskSchedule row = row(7L, "sch-a", "REPORT", "0 0 * * * *", null, due);
        FakeQueue queue = new FakeQueue(List.of(row));
        List<AgentTask> published = new ArrayList<>();

        int fired = firer(queue, published::add).runOnce();

        assertThat(fired).isEqualTo(1);
        assertThat(published).singleElement().satisfies(task -> {
            assertThat(task.type()).isEqualTo("REPORT");
            assertThat(task.tenantId()).isEqualTo("demo");
            assertThat(task.userId()).isEqualTo("u-1");
            assertThat(task.agentId()).isEqualTo("companion-v1");
            assertThat(task.payload()).containsEntry("scheduleId", "sch-a");
            assertThat(task.taskId()).startsWith("sched-sch-a@");
        });
        assertThat(queue.advanced).isEqualTo(1);
        assertThat(queue.nextFireAt).isAfter(LocalDateTime.now());
        assertThat(queue.markedStatus).containsExactly("SUCCEEDED");
    }

    /** 发布失败不抛，只把状态记成 FAILED；下次触发已经推进，不会每轮重试到死。 */
    @Test
    void recordsFailureWhenPublishThrows() {
        AgentTaskSchedule row = row(8L, "sch-b", "BATCH", null, 60, LocalDateTime.now().minusSeconds(5));
        FakeQueue queue = new FakeQueue(List.of(row));

        AgentTaskPublisher failing = task -> {
            throw new IllegalStateException("publisher down");
        };
        int fired = firer(queue, failing).runOnce();

        assertThat(fired).isZero();
        assertThat(queue.advanced).isEqualTo(1);
        assertThat(queue.markedStatus).containsExactly("FAILED");
    }

    /** 抢不到（别的进程先推进了）就不发布、不记触发。 */
    @Test
    void skipsWhenAnotherProcessWonTheRace() {
        AgentTaskSchedule row = row(9L, "sch-c", "REPORT", "0 0 * * * *", null, LocalDateTime.now().minusMinutes(1));
        FakeQueue queue = new FakeQueue(List.of(row));
        queue.advanceWins = false;
        List<AgentTask> published = new ArrayList<>();

        assertThat(firer(queue, published::add).runOnce()).isZero();
        assertThat(published).isEmpty();
        assertThat(queue.markedStatus).isEmpty();
    }

    /** 没有到期的行时什么都不做。 */
    @Test
    void doesNothingWhenQueueIsEmpty() {
        FakeQueue queue = new FakeQueue(List.of());
        List<AgentTask> published = new ArrayList<>();

        assertThat(firer(queue, published::add).runOnce()).isZero();
        assertThat(published).isEmpty();
    }

    /** cron 被改坏时留痕为 FAILED，不发布也不抛。 */
    @Test
    void recordsFailureWhenScheduleCannotComputeNext() {
        AgentTaskSchedule row = row(10L, "sch-d", "REPORT", "not a cron", null, LocalDateTime.now().minusMinutes(1));
        FakeQueue queue = new FakeQueue(List.of(row));
        List<AgentTask> published = new ArrayList<>();

        assertThat(firer(queue, published::add).runOnce()).isZero();
        assertThat(published).isEmpty();
        assertThat(queue.advanced).isZero();
        assertThat(queue.markedStatus).containsExactly("FAILED");
    }

    /** 开关打开的触发面；批量与间隔用默认值。 */
    private static TaskScheduleFirer firer(ScheduleQueue queue, AgentTaskPublisher publisher) {
        ScheduleProperties properties = new ScheduleProperties();
        properties.setEnabled(true);
        return new TaskScheduleFirer(queue, publisher, properties);
    }

    /** 一条已到期的调度。 */
    private static AgentTaskSchedule row(Long id, String scheduleId, String taskType, String cron, Integer delay,
                                         LocalDateTime nextFireAt) {
        AgentTaskSchedule row = new AgentTaskSchedule();
        row.setId(id);
        row.setScheduleId(scheduleId);
        row.setTaskType(taskType);
        row.setTenantId("demo");
        row.setUserId("u-1");
        row.setAgentId("companion-v1");
        row.setCronExpr(cron);
        row.setDelaySeconds(delay);
        row.setEnabled(1);
        row.setNextFireAt(nextFireAt);
        row.setFireCount(0);
        return row;
    }

    /** 记录调用顺序的假队列。 */
    private static final class FakeQueue implements ScheduleQueue {

        private final List<AgentTaskSchedule> rows;

        private boolean advanceWins = true;

        private int advanced;

        private LocalDateTime nextFireAt;

        private final List<String> markedStatus = new ArrayList<>();

        private FakeQueue(List<AgentTaskSchedule> rows) {
            this.rows = rows;
        }

        @Override
        public List<AgentTaskSchedule> due(LocalDateTime now, int limit) {
            return rows;
        }

        @Override
        public int advance(Long id, LocalDateTime expectedNextFireAt, LocalDateTime nextFireAt) {
            if (!advanceWins) {
                return 0;
            }
            advanced++;
            this.nextFireAt = nextFireAt;
            return 1;
        }

        @Override
        public void markFired(Long id, LocalDateTime firedAt, String status) {
            markedStatus.add(status);
        }
    }
}
