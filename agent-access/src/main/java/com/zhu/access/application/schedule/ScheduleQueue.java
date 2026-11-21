package com.zhu.access.application.schedule;

import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 触发面用到的调度读写，收窄到三个动作。
 *
 * <p>为什么不让 {@link TaskScheduleFirer} 直接拿 Mapper：Mapper 是 MyBatis 的胖接口，
 * 测试要么起库、要么 mock 一整个接口；收成三个动作后，触发顺序与失败处理可以用几个 lambda 测完。
 */
public interface ScheduleQueue {

    /** 已到期且启用的调度，按到期时间从早到晚。 */
    List<AgentTaskSchedule> due(LocalDateTime now, int limit);

    /**
     * 抢一行：只有 {@code next_fire_at} 仍是 {@code expectedNextFireAt} 才推进。
     * 返回 0 表示别的进程已经抢走（或这一行被停用）。
     */
    int advance(Long id, LocalDateTime expectedNextFireAt, LocalDateTime nextFireAt);

    /** 记一次触发：上次触发时间、状态，并把次数加一。 */
    void markFired(Long id, LocalDateTime firedAt, String status);
}
