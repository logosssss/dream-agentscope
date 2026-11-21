package com.zhu.access.application.schedule;

import com.zhu.readmodel.entity.schedule.AgentTaskSchedule;
import com.zhu.readmodel.persistence.schedule.AgentTaskScheduleMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 用 MySQL 实现触发面的三个动作。只在存储开关打开时创建。
 *
 * <p>没有缓存：这一层每个扫描周期只跑一次，挡住重复触发的是 SQL 里的乐观锁，不是本地状态。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlScheduleQueue implements ScheduleQueue {

    private final AgentTaskScheduleMapper schedules;

    /** MySQL 打开时由接入层 Mapper 扫描提供实现。 */
    public MysqlScheduleQueue(AgentTaskScheduleMapper schedules) {
        this.schedules = schedules;
    }

    /** 到期扫描，交给 SQL 排序与限量。 */
    @Override
    public List<AgentTaskSchedule> due(LocalDateTime now, int limit) {
        List<AgentTaskSchedule> rows = schedules.due(now, limit);
        return rows == null ? List.of() : rows;
    }

    /** 乐观锁推进；返回 0 表示没抢到。 */
    @Override
    public int advance(Long id, LocalDateTime expectedNextFireAt, LocalDateTime nextFireAt) {
        return schedules.advance(id, expectedNextFireAt, nextFireAt);
    }

    /** 触发结果写回。写失败直接抛，让调用方决定是记日志还是重试。 */
    @Override
    public void markFired(Long id, LocalDateTime firedAt, String status) {
        schedules.markFired(id, firedAt, status);
    }
}
