package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TenantQuotaRequest;
import com.zhu.audit.api.TenantQuotaCatalog;
import com.zhu.audit.api.TenantQuotaSummary;
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
 * 管理进程登记、查看和改写租户的同时运行上限。不记当前占位。
 */
@RestController
public class AdminTenantConcurrentQuotaController {

    private final ObjectProvider<TenantQuotaCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantConcurrentQuotaController(ObjectProvider<TenantQuotaCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的同时运行上限。条数默认 50，上限 100。不带回当前占位。
     */
    @GetMapping("/api/admin/tenant-concurrent-quotas")
    public ApiResult<List<TenantQuotaSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recentConcurrent(limit));
    }

    /**
     * 新增 CONCURRENT_RUNS / CURRENT。同一租户再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-concurrent-quotas")
    public ApiResult<TenantQuotaSummary> setConcurrentRuns(@RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().setConcurrentRuns(body.tenantId(), body.limit()));
    }

    /**
     * 改已有 CONCURRENT_RUNS / CURRENT 的上限。没有这一行由目录返回找不到。
     */
    @PostMapping("/api/admin/tenant-concurrent-quotas/{tenantId}")
    public ApiResult<TenantQuotaSummary> updateConcurrentRuns(
            @PathVariable String tenantId, @RequestBody TenantQuotaRequest request) {
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog().updateConcurrentRuns(tenantId, body.limit()));
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
