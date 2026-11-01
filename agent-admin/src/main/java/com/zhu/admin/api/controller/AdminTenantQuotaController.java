package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TenantQuotaRequest;
import com.zhu.audit.api.TenantQuotaCatalog;
import com.zhu.audit.api.TenantQuotaSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记和改写租户的每日请求上限。不记消耗，也不写其它类型。
 */
@RestController
public class AdminTenantQuotaController {

    private final ObjectProvider<TenantQuotaCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantQuotaController(ObjectProvider<TenantQuotaCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 新增 REQUESTS / DAILY。同一租户再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-quotas")
    public ApiResult<TenantQuotaSummary> setDailyRequests(@RequestBody TenantQuotaRequest request) {
        TenantQuotaCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant quota is unavailable");
        }
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog.setDailyRequests(body.tenantId(), body.limit()));
    }

    /**
     * 改已有 REQUESTS / DAILY 的上限。没有这一行由目录返回找不到。
     */
    @PostMapping("/api/admin/tenant-quotas/{tenantId}")
    public ApiResult<TenantQuotaSummary> updateDailyRequests(
            @PathVariable String tenantId, @RequestBody TenantQuotaRequest request) {
        TenantQuotaCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant quota is unavailable");
        }
        TenantQuotaRequest body = request == null ? new TenantQuotaRequest(null, null) : request;
        return ApiResult.ok(catalog.updateDailyRequests(tenantId, body.limit()));
    }
}
