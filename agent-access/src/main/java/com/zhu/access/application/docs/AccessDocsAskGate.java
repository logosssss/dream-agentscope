package com.zhu.access.application.docs;

import com.zhu.access.application.chat.ConcurrentGate;
import com.zhu.access.application.chat.QuotaGate;
import com.zhu.common.context.RequestIdContext;
import com.zhu.docs.api.DocsAskGate;
import com.zhu.ops.tenant.TenantGate;
import com.zhu.ops.tenant.TenantUserGate;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 文档提问的额度闸。当日请求、token 和费用与对话共用同一套计数。
 * 每分钟限流的键带上 {@code docs-ask}，所以和对话的分钟桶分开。
 * Redis 失败仍由额度实现抛出不可用，这里不改走内存。
 */
@Component
public class AccessDocsAskGate implements DocsAskGate {

    /** 限流动作名。不要改成 chat，否则分钟桶会和对话混在一起。 */
    public static final String ACTION = "docs-ask";

    private final TenantGate tenants;

    private final TenantUserGate tenantUsers;

    private final QuotaGate quota;

    private final ConcurrentGate concurrent;

    /** 顺序与对话一致：租户、租户用户、额度、并发。会话归属不查，因为每次都是新会话。 */
    public AccessDocsAskGate(
            TenantGate tenants,
            TenantUserGate tenantUsers,
            QuotaGate quota,
            ConcurrentGate concurrent) {
        this.tenants = tenants;
        this.tenantUsers = tenantUsers;
        this.quota = quota;
        this.concurrent = concurrent;
    }

    /**
     * 额度在补全之前扣。并发名额在补全结束或失败后归还。
     */
    @Override
    public <T> T enter(String tenantId, String userId, Supplier<T> action) {
        String tenant = tenantId == null ? "" : tenantId;
        tenants.requireActive(tenant);
        tenantUsers.requireActive(tenant, userId);
        quota.check(tenant, userId, ACTION, RequestIdContext.get());
        ConcurrentGate.Slot slot = concurrent.open(tenant);
        try {
            return action.get();
        } finally {
            concurrent.close(slot);
        }
    }
}
