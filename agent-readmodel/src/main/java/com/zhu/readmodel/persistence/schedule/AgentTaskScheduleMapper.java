package com.zhu.readmodel.persistence.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_task_schedule 的访问。管理进程登记与停用，用户进程按到期时间取出并推进。
 *
 * <p>取出用到期时间做乐观锁：两个进程同时扫到同一行时，只有把 {@code next_fire_at}
 * 从旧值改成功的那一个会真的发布任务。
 */
public interface AgentTaskScheduleMapper extends BaseMapper<AgentTaskSchedule> {

    /** 最近登记的调度。调用方已经把条数收进 1 到 100。含停用。 */
    List<AgentTaskSchedule> recent(@Param("limit") int limit);

    /** 按调度标识取一行。停用也返回。 */
    AgentTaskSchedule findByScheduleId(@Param("scheduleId") String scheduleId);

    /** 只把仍启用的那一行改成停用。返回更新行数。 */
    int disableEnabled(@Param("scheduleId") String scheduleId);

    /**
     * 只把已停用的那一行改回启用，并写上下次触发时间。
     * 恢复不能留着旧的到期时间，否则一恢复就会立刻触发一次。
     */
    int restoreDisabledWithNext(@Param("scheduleId") String scheduleId,
                                @Param("nextFireAt") LocalDateTime nextFireAt);

    /** 已到期且启用的调度，按到期时间从早到晚。 */
    List<AgentTaskSchedule> due(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 抢一行：只有 {@code next_fire_at} 仍是取出来时的旧值才推进。
     * 返回 0 表示别的进程已经抢走。
     */
    int advance(@Param("id") Long id,
                @Param("expectedNextFireAt") LocalDateTime expectedNextFireAt,
                @Param("nextFireAt") LocalDateTime nextFireAt);

    /** 记一次触发：上次触发时间、状态，并把次数加一。 */
    int markFired(@Param("id") Long id,
                  @Param("firedAt") LocalDateTime firedAt,
                  @Param("status") String status);
}
