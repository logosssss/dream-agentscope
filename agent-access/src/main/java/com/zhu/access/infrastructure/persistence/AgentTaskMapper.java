package com.zhu.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentTaskRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * agent_task 的访问。插入用 BaseMapper，结束状态的更新在 XML 里。
 */
public interface AgentTaskMapper extends BaseMapper<AgentTaskRow> {

    /**
     * 按任务标识改结束状态。没有对应行时返回 0。
     */
    int finish(
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt);
}
