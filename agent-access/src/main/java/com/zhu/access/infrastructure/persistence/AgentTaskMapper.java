package com.zhu.access.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.access.infrastructure.persistence.entity.AgentTaskRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_task 的访问。插入用 BaseMapper，结束状态和列表在 XML 里。
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

    /**
     * 当前用户在这个租户下最近的任务。不带回 payload。
     */
    List<AgentTaskRow> listOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("limit") int limit);
}
