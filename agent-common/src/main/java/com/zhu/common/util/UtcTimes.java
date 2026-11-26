package com.zhu.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 全仓唯一的墙钟入口。数据库里的 DATETIME 一律按 UTC 写。
 *
 * <p>为什么收成一个入口：原先同一张库里三种时区口径并存——代码写 UTC
 * （{@code LocalDateTime.now(ZoneOffset.UTC)}）、代码写服务器本地
 * （{@code LocalDateTime.now()}）、以及列默认值 {@code CURRENT_TIMESTAMP}（跟会话时区走）。
 * 于是新增的模型单价按本地时间落库、取价按 UTC 比较，东八区下八小时内取不到价、费用记 0；
 * 保留策略与用量报表的窗口也跟着偏。
 *
 * <p>只在业务代码里用。测试要固定时间就自己造 {@link Instant} / {@link java.time.Clock}，
 * 不要在断言里读真实墙钟。
 *
 * <p>残留：没有显式写入的时间列仍取列默认值，而 {@code CURRENT_TIMESTAMP} 跟会话时区走。
 * 彻底统一要把连接的会话时区设成 UTC（见 {@code docs/time-utc-落地.md} 的残留一节）。
 */
public final class UtcTimes {

    private UtcTimes() {
    }

    /** 当前 UTC 时间。落库的 DATETIME 用它。 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** 当前 UTC 时间，截到毫秒。列宽是 DATETIME(3) 时用这个，避免驱动自己截断。 */
    public static LocalDateTime nowMillis() {
        return now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** 当前时刻。只需要「什么时候发生」而不落 DATETIME 时用它。 */
    public static Instant instant() {
        return Instant.now();
    }
}
