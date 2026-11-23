package com.zhu.admin.api.run;

import java.util.List;

/**
 * 管理端的运行只读查询。运维报障时从请求号或运行号倒着查。
 */
public interface AdminRunQuery {

    /**
     * 按用户、会话、状态回查运行。三个条件都可选，条数由实现收口。
     *
     * @param userId    用户
     * @param sessionId 会话
     * @param status    SUCCEEDED / FAILED / CANCELLED
     * @param limit     条数
     * @return 最近的运行，没有给空列表
     */
    List<RunRow> runs(String userId, String sessionId, String status, int limit);

    /**
     * 按运行号取一次运行，带步骤。
     *
     * @param runId 运行标识
     * @return 找不到给 null
     */
    RunDetail run(String runId);
}
