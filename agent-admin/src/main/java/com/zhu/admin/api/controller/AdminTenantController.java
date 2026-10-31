package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TenantRequest;
import com.zhu.audit.api.TenantCatalog;
import com.zhu.audit.api.TenantSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记租户。不创建用户，也不停用已有租户。
 */
@RestController
public class AdminTenantController {

    private final ObjectProvider<TenantCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantController(ObjectProvider<TenantCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 新增一个可用租户。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenants")
    public ApiResult<TenantSummary> register(@RequestBody TenantRequest request) {
        TenantCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant catalog is unavailable");
        }
        TenantRequest body = request == null ? new TenantRequest(null, null) : request;
        return ApiResult.ok(catalog.register(body.tenantId(), body.name()));
    }
}
