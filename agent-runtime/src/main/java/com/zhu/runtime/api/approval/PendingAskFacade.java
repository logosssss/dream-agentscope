package com.zhu.runtime.api.approval;

import java.util.List;

/**
 * 运行层对外端口：待确认列表/详情摘要 + 原子决定续跑。
 * 正式实现持有进程内仓与 ChannelGateway；接入层不碰 ToolUseBlock。
 */
public interface PendingAskFacade {

    /**
     * 当前用户本租户仍待确认的摘要；上限由调用方截；顺序不保证。
     */
    List<PendingAskSummary> pendingFor(String tenantId, String userId, int limit);

    /**
     * 本人本租户看一张摘要；别人的或不存在返回 null。
     */
    PendingAskSummary ownedOne(String tenantId, String userId, String approvalId);

    /**
     * 原子取出活单并驱动框架续跑。找不到或不属于本人返回 null（由接入层转 NOT_FOUND）。
     */
    PendingAskSummary decideAndResume(
            String tenantId, String userId, String approvalId, boolean approved, String reason);
}
