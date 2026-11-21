package com.zhu.ops.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 下次触发时间的算法。cron 交给 Spring，延迟就是加法。 */
class ScheduleTimesTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 21, 10, 30, 15);

    @Test
    void cronGivesNextOccurrence() {
        assertThat(ScheduleTimes.nextFireAt("0 0 * * * *", null, BASE))
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 11, 0, 0));
    }

    /** 运维习惯写五段（分 时 日 月 周），秒按 0 补——两种写法算出来的时间必须一样。 */
    @Test
    void fiveFieldCronMeansZeroSeconds() {
        assertThat(ScheduleTimes.nextFireAt("0 * * * *", null, BASE))
                .isEqualTo(ScheduleTimes.nextFireAt("0 0 * * * *", null, BASE))
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 11, 0, 0));
        ScheduleTimes.requireValidCron("*/5 * * * *");
    }

    @Test
    void delayAddsSeconds() {
        assertThat(ScheduleTimes.nextFireAt(null, 90, BASE))
                .isEqualTo(BASE.plusSeconds(90));
    }

    @Test
    void rejectsBothOrNeither() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("0 0 * * * *", 60, BASE))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt(null, null, BASE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("  ", null, BASE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsIllegalCron() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("not a cron", null, BASE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ScheduleTimes.requireValidCron("61 * * * * *"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsZeroDelay() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt(null, 0, BASE))
                .isInstanceOf(BusinessException.class);
    }
}
