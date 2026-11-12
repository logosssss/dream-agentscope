package com.zhu.admin.api.controller.quota;

import com.zhu.admin.api.dto.quota.TenantQuotaRequest;
import com.zhu.audit.api.quota.TenantQuotaCatalog;
import com.zhu.audit.api.quota.TenantQuotaSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记、查看和改写租户的每日 token 上限。不记消耗。
 */
@RestController
public class AdminTenantTokenQuotaController {

    private final ObjectProvider<TenantQuotaCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantTokenQuotaController(ObjectProvider<TenantQuotaCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的每日 token 上限。条数默认 50，上限 100。不带回已用量。
     */
    @GetMapping("/api/admin/tenant-token-quotas")
    public ApiResult<List<TenantQuotaSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recentTokens(limit));
    }

    /**
     * 新增 TOKENS / DAILY。同一租户再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-token-quotas")
    public ApiResult<TenantQuotaSummary> setDailyTokens(@RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().setDailyTokens(body.tenantId(), body.limit()));
    }

    /**
     * 改已有 TOKENS / DAILY 的上限。没有这一行由目录返回找不到。
     */
    @PostMapping("/api/admin/tenant-token-quotas/{tenantId}")
    public ApiResult<TenantQuotaSummary> updateDailyTokens(
            @PathVariable String tenantId, @RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().updateDailyTokens(tenantId, body.limit()));
    }

    /** MySQL 关闭时没有目录。 */
    private TenantQuotaCatalog catalog() {
        TenantQuotaCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant quota is unavailable");
        }
        return catalog;
    }
}
