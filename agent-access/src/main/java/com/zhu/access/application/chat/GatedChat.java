package com.zhu.access.application.chat;

import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;

/**
 * 四个对话入口共用的进闸再收口。接入层。
 * 同步动作码是 {@code chat}，流式是 {@code chat-stream}。占位和释放用同一把并发闸。
 */
final class GatedChat {

    /** 只提供静态方法，避免四条入口各抄一遍闸参数和动作码。 */
    private GatedChat() {
    }

    /**
     * 先按固定顺序过闸并占名额，再走同步收口。闸拒绝时不记会话、不发送。
     */
    static AgentResponse sync(
            TenantGate tenants,
            TenantUserGate tenantUsers,
            SessionOwnerGate owners,
            QuotaGate quota,
            ConcurrentGate concurrent,
            AgentRequest request,
            Runnable remember,
            Supplier<AgentResponse> call,
            ChatTurn.Recorder recorder) {
        ConcurrentGate.Slot slot =
                GateChain.enter(tenants, tenantUsers, owners, quota, concurrent, request, "chat");
        return ChatTurn.sync(concurrent, slot, remember, call, recorder);
    }

    /**
     * 先按固定顺序过闸并占名额，再走流式收口。额度动作码是 {@code chat-stream}。
     */
    static Flux<AgentStreamEvent> stream(
            TenantGate tenants,
            TenantUserGate tenantUsers,
            SessionOwnerGate owners,
            QuotaGate quota,
            ConcurrentGate concurrent,
            AgentRequest request,
            Runnable remember,
            Supplier<Flux<AgentStreamEvent>> call,
            ChatTurn.Recorder recorder) {
        ConcurrentGate.Slot slot =
                GateChain.enter(tenants, tenantUsers, owners, quota, concurrent, request, "chat-stream");
        return ChatTurn.stream(concurrent, slot, remember, call, recorder);
    }
}
