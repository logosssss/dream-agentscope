package com.zhu.access.api.controller;

import com.zhu.access.api.dto.QuotaSnapshot;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.QuotaGuard;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看当日额度和已用量。只读 Redis 计数，不扣额度。
 */
@RestController
public class QuotaController {

    private final QuotaGuard quotas;

    private final AccessAccountGate accounts;

    /** 额度计数只走 Redis。失败时由额度门返回不可用。 */
    public QuotaController(QuotaGuard quotas, AccessAccountGate accounts) {
        this.quotas = quotas;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户和租户。不增加计数，也不写流水。
     */
    @GetMapping("/api/quota")
    public ApiResult<QuotaSnapshot> current() {
        accounts.requireActive();
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(quotas.snapshot(tenantId, UserContext.get()));
    }
}
