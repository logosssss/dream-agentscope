package com.zhu.ops.schedule;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
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
 */
public final class ScheduleTimes {

    private ScheduleTimes() {
    }

    /**
     * 算出下次触发时间。
     *
     * @param cronExpr     cron 表达式；延迟任务传 null
     * @param delaySeconds 延迟秒数；cron 任务传 null
     * @param from         基准时间，通常是「现在」
     * @return 下次触发时间，一定晚于 {@code from}
     * @throws BusinessException 两个都给、都不给、cron 写法非法或已无下一次
     */
    public static LocalDateTime nextFireAt(String cronExpr, Integer delaySeconds, LocalDateTime from) {
        boolean hasCron = cronExpr != null && !cronExpr.isBlank();
        boolean hasDelay = delaySeconds != null;
        if (hasCron == hasDelay) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "task schedule needs exactly one of cron or delay");
        }
        if (hasDelay) {
            if (delaySeconds < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "delay seconds must be >= 1");
            }
            return from.plusSeconds(delaySeconds);
        }
        return nextCron(cronExpr, from);
    }

    /**
     * cron 的下一次。Spring 的表达式在 2 月 30 日这类「永远不成立」的写法上会返回 null，
     * 这里当作非法，避免存一条永远不会触发的调度。
     */
    public static LocalDateTime nextCron(String cronExpr, LocalDateTime from) {
        CronExpression parsed;
        try {
            parsed = CronExpression.parse(normalize(cronExpr));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cron expression is invalid");
        }
        LocalDateTime next = parsed.next(from);
        if (next == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cron expression has no next fire");
        }
        return next;
    }

    /** 五段 cron 补一个「秒」位（0）；其余原样交给 Spring，写错由它报。 */
    private static String normalize(String cronExpr) {
        String trimmed = cronExpr.trim().replaceAll("\\s+", " ");
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
