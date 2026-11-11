package com.zhu.access.application.chat;

import com.zhu.access.api.dto.RunDetail;
import com.zhu.access.api.dto.RunSummary;
import java.util.List;

/**
 * 当前用户的运行记录查询。MySQL 关闭时没有这个实现。
 * 列表不带步骤；单次详情才读步骤，仍不返回全文。
 */
public interface RunQuery {

    /**
     * 当前用户在这个租户下最近的运行。默认 50 条，最多 100 条。
     * 不把别人的行带出来，也不读步骤表。
     */
    List<RunSummary> owned(String userId, String tenantId, int limit);

    /**
     * 当前用户在这个租户下的一次运行，附带步骤、开始时间和结束时间。
     * 找不到或不是本人时按找不到处理，不读别人的步骤。不返回异常原文。
     */
    RunDetail ownedOne(String userId, String tenantId, String runId);
}
