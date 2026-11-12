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
 * 管理进程登记、查看和改写租户的每日费用上限。limit 为金额微单位（×1e6）。
 */
@RestController
public class AdminTenantCostQuotaController {

    private final ObjectProvider<TenantQuotaCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantCostQuotaController(ObjectProvider<TenantQuotaCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的每日费用上限。条数默认 50，上限 100。不带回已用量。
     */
    @GetMapping("/api/admin/tenant-cost-quotas")
    public ApiResult<List<TenantQuotaSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recentCosts(limit));
    }

    /**
     * 新增 COST / DAILY。同一租户再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-cost-quotas")
    public ApiResult<TenantQuotaSummary> setDailyCost(@RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().setDailyCost(body.tenantId(), body.limit()));
    }

    /**
     * 改已有 COST / DAILY 的上限。没有这一行由目录返回找不到。
     */
    @PostMapping("/api/admin/tenant-cost-quotas/{tenantId}")
    public ApiResult<TenantQuotaSummary> updateDailyCost(
            @PathVariable String tenantId, @RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().updateDailyCost(tenantId, body.limit()));
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
