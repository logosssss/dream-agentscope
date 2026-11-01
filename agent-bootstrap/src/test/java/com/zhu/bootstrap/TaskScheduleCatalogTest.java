package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlTaskScheduleCatalog;
import com.zhu.audit.infrastructure.persistence.AgentTaskScheduleMapper;
import com.zhu.audit.infrastructure.persistence.entity.AgentTaskSchedule;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
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
 * 任务调度只登记目录。不连接 MySQL，也不触发执行。
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
}
