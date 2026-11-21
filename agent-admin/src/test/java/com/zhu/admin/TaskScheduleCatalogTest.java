package com.zhu.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.admin.application.schedule.MysqlTaskScheduleCatalog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import com.zhu.readmodel.persistence.schedule.AgentTaskScheduleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 任务调度登记、停用、恢复和列表。不连接 MySQL；登记时算下次触发，触发本身在用户进程。
 */
class TaskScheduleCatalogTest {

    /** cron 和延迟二选一，归属用户必填。登记时就把下次触发算好。 */
    @Test
    void registerAcceptsCronOrDelay() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);

        assertThat(catalog.register("sch-a", "REPORT", "demo", "u-1", null, "0 0 * * *", null))
                .satisfies(saved -> {
                    assertThat(saved.scheduleId()).isEqualTo("sch-a");
                    assertThat(saved.taskType()).isEqualTo("REPORT");
                    assertThat(saved.tenantId()).isEqualTo("demo");
                    assertThat(saved.userId()).isEqualTo("u-1");
                    assertThat(saved.cronExpr()).isEqualTo("0 0 * * *");
                    assertThat(saved.delaySeconds()).isNull();
                    assertThat(saved.enabled()).isTrue();
                    assertThat(saved.nextFireAt()).isNotEmpty();
                    assertThat(saved.lastFireAt()).isEmpty();
                    assertThat(saved.lastStatus()).isEmpty();
                });
        ArgumentCaptor<AgentTaskSchedule> cron = ArgumentCaptor.forClass(AgentTaskSchedule.class);
        verify(schedules).insert(cron.capture());
        assertThat(cron.getValue().getEnabled()).isEqualTo(1);
        assertThat(cron.getValue().getNextFireAt()).isAfter(LocalDateTime.now());
        assertThat(cron.getValue().getFireCount()).isZero();
        assertThat(cron.getValue().getAgentId()).isNull();

        assertThat(catalog.register("sch-b", "BATCH", null, "u-2", "companion-v1", " ", 30).delaySeconds())
                .isEqualTo(30);

        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", "demo", "u-1", null, "0 0 * * *", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", "demo", "u-1", null, " ", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> catalog.register("sch-c", "CLEAN", "demo", "u-1", null, "0 0 * * *", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", "demo", " ", null, "0 0 * * *", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", "demo", "u-1", null, "not a cron", null))
                .isInstanceOf(BusinessException.class);

        when(schedules.insert(any(AgentTaskSchedule.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("sch-a", "REPORT", "demo", "u-1", null, "0 0 * * *", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 停用只动仍启用的行；恢复只动已停用的行，并重算下次触发。 */
    @Test
    void disableAndRestoreToggleEnabled() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);

        AgentTaskSchedule enabled = row("sch-a", 1, "0 0 * * *", null);
        when(schedules.findByScheduleId("sch-a")).thenReturn(enabled);
        when(schedules.disableEnabled("sch-a")).thenReturn(1);
        assertThat(catalog.disable("sch-a").enabled()).isFalse();

        AgentTaskSchedule disabled = row("sch-a", 0, "0 0 * * *", null);
        when(schedules.findByScheduleId("sch-a")).thenReturn(disabled);
        when(schedules.restoreDisabledWithNext(eq("sch-a"), any(LocalDateTime.class))).thenReturn(1);
        assertThat(catalog.restore("sch-a")).satisfies(restored -> {
            assertThat(restored.enabled()).isTrue();
            assertThat(restored.nextFireAt()).isNotEmpty();
        });

        // 恢复会把这一行改成启用（同一对象被就地改写），所以再要一行「已停用」的来测二次停用。
        when(schedules.findByScheduleId("sch-a")).thenReturn(row("sch-a", 0, "0 0 * * *", null));
        assertThatThrownBy(() -> catalog.disable("sch-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        AgentTaskSchedule noUser = row("sch-b", 0, "0 0 * * *", null);
        noUser.setUserId(null);
        when(schedules.findByScheduleId("sch-b")).thenReturn(noUser);
        assertThatThrownBy(() -> catalog.restore("sch-b"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(schedules.findByScheduleId("missing")).thenReturn(null);
        assertThatThrownBy(() -> catalog.restore("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 列表含停用，并带出下次 / 上次触发与最近状态。空结果原样返回。 */
    @Test
    void recentListsRegisteredSchedules() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);
        when(schedules.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        AgentTaskSchedule fired = row("sch-a", 1, "0 0 * * *", null);
        fired.setNextFireAt(LocalDateTime.of(2026, 11, 22, 0, 0));
        fired.setLastFireAt(LocalDateTime.of(2026, 11, 21, 0, 0));
        fired.setLastStatus("SUCCEEDED");
        when(schedules.recent(50)).thenReturn(List.of(fired));
        assertThat(catalog.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.scheduleId()).isEqualTo("sch-a");
            assertThat(saved.taskType()).isEqualTo("REPORT");
            assertThat(saved.cronExpr()).isEqualTo("0 0 * * *");
            assertThat(saved.delaySeconds()).isNull();
            assertThat(saved.enabled()).isTrue();
            assertThat(saved.nextFireAt()).isEqualTo("2026-11-22T00:00:00");
            assertThat(saved.lastFireAt()).isEqualTo("2026-11-21T00:00:00");
            assertThat(saved.lastStatus()).isEqualTo("SUCCEEDED");
        });
    }

    /** 一行启用状态可改的调度。 */
    private static AgentTaskSchedule row(String scheduleId, int enabled, String cron, Integer delay) {
        AgentTaskSchedule row = new AgentTaskSchedule();
        row.setScheduleId(scheduleId);
        row.setTaskType("REPORT");
        row.setTenantId("demo");
        row.setUserId("u-1");
        row.setCronExpr(cron);
        row.setDelaySeconds(delay);
        row.setEnabled(enabled);
        row.setFireCount(0);
        return row;
    }
}
