package com.zhu.access.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.access.api.tenant.TenantGate;
import com.zhu.access.api.tenant.TenantUserGate;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 闸门顺序与短路行为。用 lambda 替身，不 mock Redis，因此不依赖 inline mock maker。
 */
class GateChainTest {

    private final List<String> calls = new ArrayList<>();
    private final ConcurrentGate.Slot slot = () -> "tenant-1:user-1";

    private AgentRequest request(String userId) {
        return new AgentRequest(null, "tenant-1", userId, "session-1", "system", "你好");
    }

    private TenantGate tenants() {
        return tenantId -> calls.add("tenant:" + tenantId);
    }

    private TenantUserGate tenantUsers() {
        return (tenantId, userId) -> calls.add("tenant-user:" + tenantId + ":" + userId);
    }

    private SessionOwnerGate owners() {
        return (tenantId, userId, sessionId) -> calls.add("owner:" + sessionId);
    }

    private QuotaGate quota() {
        return (tenantId, userId, action, requestId) -> calls.add("quota:" + action);
    }

    private ConcurrentGate concurrent() {
        return new ConcurrentGate() {
            @Override
            public Slot open(String tenantId) {
                calls.add("concurrent:" + tenantId);
                return slot;
            }

            @Override
            public void close(Slot held) {
                calls.add("close");
            }
        };
    }

    @Test
    void runsGatesInFixedOrder() {
        ConcurrentGate.Slot held =
                GateChain.enter(tenants(), tenantUsers(), owners(), quota(), concurrent(), request("user-1"), "chat");

        assertThat(held).isSameAs(slot);
        assertThat(calls)
                .containsExactly(
                        "tenant:tenant-1",
                        "tenant-user:tenant-1:user-1",
                        "owner:session-1",
                        "quota:chat",
                        "concurrent:tenant-1");
    }

    @Test
    void passesActionToQuota() {
        GateChain.enter(
                tenants(), tenantUsers(), owners(), quota(), concurrent(), request("user-1"), "chat-stream");

        assertThat(calls).contains("quota:chat-stream");
    }

    @Test
    void rejectsMissingLoginBeforeAnyGate() {
        assertThatThrownBy(
                        () -> GateChain.enter(tenants(), tenantUsers(), owners(), quota(), concurrent(), request(" "), "chat"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(calls).isEmpty();
    }

    @Test
    void stopsAtFirstRejection() {
        TenantGate rejecting = tenantId -> {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "tenant disabled");
        };

        assertThatThrownBy(
                        () -> GateChain.enter(rejecting, tenantUsers(), owners(), quota(), concurrent(), request("user-1"), "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tenant disabled");
        assertThat(calls).isEmpty();
    }
}
