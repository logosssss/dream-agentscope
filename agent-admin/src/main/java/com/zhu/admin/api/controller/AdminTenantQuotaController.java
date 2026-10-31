package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TenantQuotaRequest;
import com.zhu.audit.api.TenantQuotaCatalog;
import com.zhu.audit.api.TenantQuotaSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记租户的每日请求上限。不改已有行，也不记消耗。
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
}
