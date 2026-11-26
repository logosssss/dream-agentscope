package com.zhu.ops.schedule;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.scheduling.support.CronExpression;

/**
 * 算一条调度「下次什么时候触发」。
 *
 * <p>cron 与延迟二选一：cron 交给 Spring 的 {@link CronExpression}（不自己写解析器，
 * 也不引入第三方 cron 库），延迟就是「从现在起 N 秒」。两个进程都用这一个实现——
 * 管理进程登记时算一次，用户进程触发后再算一次，算法只有一份。
 *
 * <p>cron 接受两种写法：Spring 的六段（秒 分 时 日 月 周）与运维更常用的五段
 * （分 时 日 月 周，秒补 0）。只做「段数=5 就补一个 0 秒」这一种归一，
 * 不做别的猜测——写错仍然报错，让登记的人当场看见。
 *
 * <p><b>时区</b>：库里的 {@code next_fire_at} 一律是 UTC（跟全仓 DATETIME 口径一致），
 * 但 cron 表达的是运维的墙上时间（「每天 03:00」）。所以这里必须收一个时区：
 * 在 {@code zone} 里算下一次，再换算回 UTC。不传时区就等于按进程默认时区解释，
 * 机器换个时区跑，触发点就会跟着漂——这是显式参数的原因。
 */
public final class ScheduleTimes {

    private ScheduleTimes() {
    }

    /**
     * 算出下次触发时间（UTC）。
     *
     * @param cronExpr     cron 表达式；延迟任务传 null
     * @param delaySeconds 延迟秒数；cron 任务传 null
     * @param fromUtc      基准时间，UTC
     * @param zone         cron 按这个时区解释；传 null 用进程默认
     * @return 下次触发时间（UTC），一定晚于 {@code fromUtc}
     * @throws BusinessException 两个都给、都不给、cron 写法非法或已无下一次
     */
    public static LocalDateTime nextFireAt(
            String cronExpr, Integer delaySeconds, LocalDateTime fromUtc, ZoneId zone) {
        boolean hasCron = cronExpr != null && !cronExpr.isBlank();
        boolean hasDelay = delaySeconds != null;
        if (hasCron == hasDelay) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task schedule needs exactly one of cron or delay");
        }
        if (hasDelay) {
            if (delaySeconds < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "delay seconds must be >= 1");
            }
            // 延迟与时区无关，直接加秒。
            return fromUtc.plusSeconds(delaySeconds);
        }
        return nextCron(cronExpr, fromUtc, zone);
    }

    /**
     * cron 的下一次。Spring 的表达式在 2 月 30 日这类「永远不成立」的写法上会返回 null，
     * 这里当作非法，避免存一条永远不会触发的调度。
     */
    public static LocalDateTime nextCron(String cronExpr, LocalDateTime fromUtc, ZoneId zone) {
        CronExpression parsed = parse(cronExpr);
        ZoneId effective = zone == null ? ZoneId.systemDefault() : zone;
        LocalDateTime fromLocal =
                fromUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(effective).toLocalDateTime();
        LocalDateTime nextLocal = parsed.next(fromLocal);
        if (nextLocal == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cron expression has no next fire");
        }
        return nextLocal.atZone(effective).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * 配置里的时区名。空白按进程默认（与改造前一致）；写错直接报，避免悄悄按默认跑。
     */
    public static ZoneId zone(String configured) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "schedule zone is invalid");
        }
    }

    /** 五段 cron 补一个「秒」位（0）；其余原样交给 Spring，写错由它报。 */
    private static CronExpression parse(String cronExpr) {
        try {
            return CronExpression.parse(normalize(cronExpr));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cron expression is invalid");
        }
    }

    private static String normalize(String cronExpr) {
        String trimmed = cronExpr == null ? "" : cronExpr.trim().replaceAll("\\s+", " ");
        return trimmed.split(" ").length == 5 ? "0 " + trimmed : trimmed;
    }

    /**
     * 只校验写法，不取下一次。登记时先校验再落库，能更快给前端一句人话。
     */
    public static void requireValidCron(String cronExpr) {
        try {
            CronExpression.parse(normalize(cronExpr));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cron expression is invalid");
        }
    }
}
