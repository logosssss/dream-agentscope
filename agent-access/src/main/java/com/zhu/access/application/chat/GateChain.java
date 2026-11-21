package com.zhu.access.application.chat;

import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;

/**
 * 进入对话前的闸门顺序，只有这一份：用户 → 租户 → 租户用户 → 会话归属 → 额度 → 并发名额。
 * 四个入口（WEB/API × 同步/流式）以前各抄一遍，顺序靠人记；现在由这里定序，改动只改一处。
 * 闸门自己只判不改状态；并发名额的释放仍由调用方在 finally 里做（流式在 {@code doFinally}）。
 */
public final class GateChain {

    private GateChain() {
    }

    /**
     * 依次过闸，最后占租户并发名额。任何一闸拒绝都会立即抛出，后面的闸不再执行。
     *
     * @param action 计入限流的动作名，如 {@code chat} / {@code chat-stream}
     * @return 并发名额句柄，调用方负责在结束时 {@code concurrent.close(slot)}
     */
    public static ConcurrentGate.Slot enter(
            TenantGate tenants,
            TenantUserGate tenantUsers,
            SessionOwnerGate owners,
            QuotaGate quota,
            ConcurrentGate concurrent,
            AgentRequest request,
            String action) {
        requireUser(request.userId());
        tenants.requireActive(request.tenantId());
        tenantUsers.requireActive(request.tenantId(), request.userId());
        owners.requireExclusive(request.tenantId(), request.userId(), request.sessionId());
        quota.check(request.tenantId(), request.userId(), action, RequestIdContext.get());
        return concurrent.open(request.tenantId());
    }

    /**
     * 身份只来自登录会话写进线程的值；请求体里的 userId 早就丢掉。
     * 空用户直接拒绝，避免后续闸门拿到空串去查库。
     */
    static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login is required");
        }
    }
}
