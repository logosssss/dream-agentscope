package com.zhu.ops.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 下次触发时间的算法。cron 交给 Spring，延迟就是加法。
 *
 * <p>库里的 {@code next_fire_at} 是 UTC，而 cron 是运维的墙上时间：这里固定用 UTC 与
 * {@code Asia/Shanghai} 两种时区验证「算在哪个时区、存成哪个时区」。
 */
class ScheduleTimesTest {

    /** UTC 基准：2026-11-21 10:30:15Z。 */
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 21, 10, 30, 15);

    @Test
    void cronGivesNextOccurrence() {
        assertThat(ScheduleTimes.nextFireAt("0 0 * * * *", null, BASE, ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 11, 0, 0));
    }

    /** 运维习惯写五段（分 时 日 月 周），秒按 0 补——两种写法算出来的时间必须一样。 */
    @Test
    void fiveFieldCronMeansZeroSeconds() {
        assertThat(ScheduleTimes.nextFireAt("0 * * * *", null, BASE, ZoneOffset.UTC))
                .isEqualTo(ScheduleTimes.nextFireAt("0 0 * * * *", null, BASE, ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 11, 0, 0));
        ScheduleTimes.requireValidCron("*/5 * * * *");
    }

    @Test
    void delayAddsSeconds() {
        assertThat(ScheduleTimes.nextFireAt(null, 90, BASE, ZoneOffset.UTC))
                .isEqualTo(BASE.plusSeconds(90));
    }

    /** 延迟与时区无关：换个时区加的还是同样的秒数。 */
    @Test
    void delayIgnoresZone() {
        assertThat(ScheduleTimes.nextFireAt(null, 90, BASE, ZoneId.of("Asia/Shanghai")))
                .isEqualTo(ScheduleTimes.nextFireAt(null, 90, BASE, ZoneOffset.UTC));
    }

    /**
     * cron 按给定时区解释，结果换回 UTC：上海「每天 03:00」= 前一日 19:00Z。
     * 按进程默认时区算的话，换台机器跑触发点就漂了。
     */
    @Test
    void cronIsInterpretedInTheGivenZoneAndStoredAsUtc() {
        LocalDateTime next = ScheduleTimes.nextFireAt(
                "0 3 * * *", null, LocalDateTime.of(2026, 11, 26, 0, 0, 0), ZoneId.of("Asia/Shanghai"));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 11, 26, 19, 0, 0));
    }

    @Test
    void rejectsBothOrNeither() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("0 0 * * * *", 60, BASE, ZoneOffset.UTC))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt(null, null, BASE, ZoneOffset.UTC))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("  ", null, BASE, ZoneOffset.UTC))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsIllegalCron() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt("not a cron", null, BASE, ZoneOffset.UTC))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ScheduleTimes.requireValidCron("61 * * * * *"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsZeroDelay() {
        assertThatThrownBy(() -> ScheduleTimes.nextFireAt(null, 0, BASE, ZoneOffset.UTC))
                .isInstanceOf(BusinessException.class);
    }

    /** 时区名空白按进程默认；写错直接报，不悄悄按默认跑。 */
    @Test
    void zoneFallsBackToProcessDefaultOrRejects() {
        assertThat(ScheduleTimes.zone("  ")).isEqualTo(ZoneId.systemDefault());
        assertThat(ScheduleTimes.zone("Asia/Shanghai")).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThatThrownBy(() -> ScheduleTimes.zone("Mars/Olympus"))
                .isInstanceOf(BusinessException.class);
    }
}
