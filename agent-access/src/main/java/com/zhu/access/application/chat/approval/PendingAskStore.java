package com.zhu.access.application.chat.approval;

import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 接入层：Gate ASK 待确认的进程内登记。
 * 权威是框架 pendingAsk；本表只给审批 HTTP 提供 id→ToolUseBlock，重启即空。
 */
@Component
public class PendingAskStore {

    private final ConcurrentMap<String, PendingAsk> byId = new ConcurrentHashMap<>();

    /**
     * 流式遇到 RequireUserConfirm 时登记。无 id 的调用跳过。
     * 同一 id 后写覆盖，避免重复暂停残留。
     */
    public void remember(
            String tenantId,
            String userId,
            String sessionId,
            String agentId,
            List<ToolUseBlock> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        String tenant = tenantId == null ? "" : tenantId;
        String user = userId == null ? "" : userId;
        String session = sessionId == null ? "" : sessionId;
        String agent = agentId == null ? "" : agentId;
        for (ToolUseBlock call : toolCalls) {
            if (call == null || call.getId() == null || call.getId().isBlank()) {
                continue;
            }
            String toolName = call.getName() == null ? "" : call.getName();
            byId.put(
                    call.getId(),
                    new PendingAsk(call.getId(), tenant, user, session, agent, toolName, call));
        }
    }

    /** 当前用户本租户仍待确认的摘要，新到旧不保证；上限由调用方截。 */
    public List<PendingAsk> pendingFor(String tenantId, String userId, int limit) {
        String tenant = tenantId == null ? "" : tenantId;
        String user = userId == null ? "" : userId;
        int cap = Math.max(0, limit);
        List<PendingAsk> out = new ArrayList<>();
        for (PendingAsk ask : byId.values()) {
            if (out.size() >= cap) {
                break;
            }
            if (tenant.equals(ask.tenantId()) && user.equals(ask.userId())) {
                out.add(ask);
            }
        }
        return out;
    }

    /** 本人本租户看一张；别人的或不存在返回 null。 */
    public PendingAsk ownedOne(String tenantId, String userId, String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return null;
        }
        PendingAsk ask = byId.get(approvalId);
        if (ask == null) {
            return null;
        }
        String tenant = tenantId == null ? "" : tenantId;
        String user = userId == null ? "" : userId;
        if (!tenant.equals(ask.tenantId()) || !user.equals(ask.userId())) {
            return null;
        }
        return ask;
    }

    /**
     * 决定前取出并删除，避免双次续跑。别人的或不存在返回 null。
     */
    public PendingAsk takeOwned(String tenantId, String userId, String approvalId) {
        PendingAsk ask = ownedOne(tenantId, userId, approvalId);
        if (ask == null) {
            return null;
        }
        // 仍校验 map 里是同一条，防止并发决定。
        if (!byId.remove(approvalId, ask)) {
            return null;
        }
        return ask;
    }

    /** 测试或热更排空时清掉全部。 */
    public void clear() {
        byId.clear();
    }
}
