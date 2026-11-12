package com.zhu.bootstrap;

import com.zhu.audit.application.schedule.MysqlTaskScheduleCatalog;
import com.zhu.audit.infrastructure.persistence.schedule.AgentTaskScheduleMapper;
import com.zhu.audit.infrastructure.persistence.entity.schedule.AgentTaskSchedule;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务调度登记、停用、恢复和列表。不连接 MySQL，也不触发执行。
 */
class TaskScheduleCatalogTest {

    /** cron 和延迟二选一。同一标识再写一次按冲突处理。 */
    @Test
    void registerAcceptsCronOrDelay() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);

        assertThat(catalog.register("sch-a", "REPORT", "0 0 * * *", null)).satisfies(saved -> {
            assertThat(saved.scheduleId()).isEqualTo("sch-a");
            assertThat(saved.taskType()).isEqualTo("REPORT");
            assertThat(saved.cronExpr()).isEqualTo("0 0 * * *");
            assertThat(saved.delaySeconds()).isNull();
            assertThat(saved.enabled()).isTrue();
        });
        ArgumentCaptor<AgentTaskSchedule> cron = ArgumentCaptor.forClass(AgentTaskSchedule.class);
        verify(schedules).insert(cron.capture());
        assertThat(cron.getValue().getEnabled()).isEqualTo(1);
        assertThat(cron.getValue().getDelaySeconds()).isNull();

        assertThat(catalog.register("sch-b", "BATCH", " ", 30).delaySeconds()).isEqualTo(30);

        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", "0 0 * * *", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> catalog.register("sch-c", "REPORT", " ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> catalog.register("sch-c", "CLEAN", "0 0 * * *", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        when(schedules.insert(any(AgentTaskSchedule.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> catalog.register("sch-a", "REPORT", "0 0 * * *", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    /** 停用只动仍启用的行；恢复只动已停用的行。 */
    @Test
    void disableAndRestoreToggleEnabled() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);

        AgentTaskSchedule enabled = new AgentTaskSchedule();
        enabled.setScheduleId("sch-a");
        enabled.setTaskType("REPORT");
        enabled.setCronExpr("0 0 * * *");
        enabled.setEnabled(1);
        when(schedules.findByScheduleId("sch-a")).thenReturn(enabled);
        when(schedules.disableEnabled("sch-a")).thenReturn(1);
        assertThat(catalog.disable("sch-a").enabled()).isFalse();

        AgentTaskSchedule disabled = new AgentTaskSchedule();
        disabled.setScheduleId("sch-a");
        disabled.setTaskType("REPORT");
        disabled.setCronExpr("0 0 * * *");
        disabled.setEnabled(0);
        when(schedules.findByScheduleId("sch-a")).thenReturn(disabled);
        when(schedules.restoreDisabled("sch-a")).thenReturn(1);
        assertThat(catalog.restore("sch-a").enabled()).isTrue();

        when(schedules.findByScheduleId("sch-a")).thenReturn(disabled);
        assertThatThrownBy(() -> catalog.disable("sch-a"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        when(schedules.findByScheduleId("missing")).thenReturn(null);
        assertThatThrownBy(() -> catalog.restore("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 列表含停用。空结果原样返回。不触发。 */
    @Test
    void recentListsRegisteredSchedules() {
        AgentTaskScheduleMapper schedules = mock(AgentTaskScheduleMapper.class);
        MysqlTaskScheduleCatalog catalog = new MysqlTaskScheduleCatalog(schedules);
        when(schedules.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        AgentTaskSchedule disabled = new AgentTaskSchedule();
        disabled.setScheduleId("sch-a");
        disabled.setTaskType("REPORT");
        disabled.setCronExpr("0 0 * * *");
        disabled.setEnabled(0);
        when(schedules.recent(50)).thenReturn(List.of(disabled));
        assertThat(catalog.recent(0)).singleElement().satisfies(saved -> {
            assertThat(saved.scheduleId()).isEqualTo("sch-a");
            assertThat(saved.taskType()).isEqualTo("REPORT");
            assertThat(saved.cronExpr()).isEqualTo("0 0 * * *");
            assertThat(saved.delaySeconds()).isNull();
            assertThat(saved.enabled()).isFalse();
        });
    }
}
