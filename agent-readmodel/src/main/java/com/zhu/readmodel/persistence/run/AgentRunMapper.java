package com.zhu.readmodel.persistence.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.readmodel.entity.run.AgentRun;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * agent_run 的访问。插入、计数和列表在 XML 里，Java 不写 SQL。
 */
public interface AgentRunMapper extends BaseMapper<AgentRun> {

    /**
     * 写入一次已经结束的运行。不更新中途状态。
     */
    int insertFinished(AgentRun row);

    /**
     * 同一会话已经成功结束的运行条数。不读摘要。
     */
    int countSucceeded(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId);

    /**
     * 当前用户在这个租户下最近结束的运行。不读步骤，也不带回异常原文。
     */
    List<AgentRun> listOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("limit") int limit);

    /**
     * 当前用户在这个租户下的一次运行。别人的行按找不到。
     */
    AgentRun findOwned(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("runId") String runId);

    /**
     * 管理端按请求号、租户、用户、会话、状态回查运行。五个条件都可空。
     * 租户和用户一起给出时是 AND，避免同名用户串到别的租户。
     * 请求号是等值，走 idx_request_id，报障时用 X-Request-Id 定位一轮。
     */
    List<AgentRun> search(
            @Param("requestId") String requestId,
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("status") String status,
            @Param("limit") int limit);

    /**
     * 管理端按运行号取一行。不按用户过滤，找不到给 null。
     */
    AgentRun findByRunId(@Param("runId") String runId);
}
