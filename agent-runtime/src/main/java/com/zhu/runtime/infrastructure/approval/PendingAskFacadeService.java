package com.zhu.runtime.infrastructure.approval;

import com.zhu.runtime.api.approval.PendingAskFacade;
import com.zhu.runtime.api.approval.PendingAskSummary;
import com.zhu.runtime.infrastructure.gateway.ChannelGateway;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 运行层：待确认 Facade 正式实现。
 * 读摘要不暴露 ToolUseBlock；决定路径原子 take 后调 Gateway 续跑。
 */
@Service
public class PendingAskFacadeService implements PendingAskFacade {

    private final PendingAskStore pendingAsks;

    private final ChannelGateway gateway;

    /**
     * 仓与 Gateway 同进程；Gateway 不依赖本 Facade，避免环。
     */
    public PendingAskFacadeService(PendingAskStore pendingAsks, ChannelGateway gateway) {
        this.pendingAsks = pendingAsks;
        this.gateway = gateway;
    }

    /**
     * 列表摘要。
     */
    @Override
    public List<PendingAskSummary> pendingFor(String tenantId, String userId, int limit) {
        List<PendingAsk> rows = pendingAsks.pendingFor(tenantId, userId, limit);
        List<PendingAskSummary> out = new ArrayList<>(rows.size());
        for (PendingAsk row : rows) {
            out.add(summaryOf(row));
        }
        return out;
    }

    /**
     * 单条摘要；不存在或不属于本人返回 null。
     */
    @Override
    public PendingAskSummary ownedOne(String tenantId, String userId, String approvalId) {
        PendingAsk ask = pendingAsks.ownedOne(tenantId, userId, approvalId);
        return ask == null ? null : summaryOf(ask);
    }

    /**
     * 原子取出并续跑；找不到返回 null，由接入层转 NOT_FOUND。
     */
    @Override
    public PendingAskSummary decideAndResume(
            String tenantId, String userId, String approvalId, boolean approved, String reason) {
        PendingAsk ask = pendingAsks.takeOwned(tenantId, userId, approvalId);
        if (ask == null) {
            return null;
        }
        gateway.resumeConfirm(
                ask.tenantId(),
                ask.userId(),
                ask.sessionId(),
                ask.agentId(),
                ask.toolCall(),
                approved,
                reason);
        return summaryOf(ask);
    }

    private static PendingAskSummary summaryOf(PendingAsk ask) {
        return new PendingAskSummary(ask.approvalId(), ask.toolName(), ask.sessionId());
    }
}
