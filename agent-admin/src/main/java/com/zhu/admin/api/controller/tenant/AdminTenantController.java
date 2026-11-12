package com.zhu.admin.api.controller.tenant;

import com.zhu.admin.api.dto.tenant.TenantRequest;
import com.zhu.audit.api.tenant.TenantCatalog;
import com.zhu.audit.api.tenant.TenantSummary;
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
 * 管理进程登记、查看、停用、关闭和恢复租户。不创建用户，也不改用户状态。
 */
@RestController
public class AdminTenantController {

    private final ObjectProvider<TenantCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantController(ObjectProvider<TenantCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的租户。条数默认 50，上限 100。含停用和关闭。
     */
    @GetMapping("/api/admin/tenants")
    public ApiResult<List<TenantSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增一个可用租户。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/tenants")
    public ApiResult<TenantSummary> register(@RequestBody TenantRequest request) {
        TenantRequest body = request == null ? new TenantRequest(null, null) : request;
        return ApiResult.ok(catalog().register(body.tenantId(), body.name()));
    }

    /**
     * 把可用租户改成停用。已经停用或关闭由目录返回冲突。
     */
    @PostMapping("/api/admin/tenants/{tenantId}/suspend")
    public ApiResult<TenantSummary> suspend(@PathVariable String tenantId) {
        return ApiResult.ok(catalog().suspend(tenantId));
    }

    /**
     * 把尚未关闭的租户改成关闭。已经关闭由目录返回冲突。
     */
    @PostMapping("/api/admin/tenants/{tenantId}/close")
    public ApiResult<TenantSummary> close(@PathVariable String tenantId) {
        return ApiResult.ok(catalog().close(tenantId));
    }

    /**
     * 把停用或关闭的租户改回可用。已经可用由目录返回冲突。
     */
    @PostMapping("/api/admin/tenants/{tenantId}/restore")
    public ApiResult<TenantSummary> restore(@PathVariable String tenantId) {
        return ApiResult.ok(catalog().restore(tenantId));
    }

    /** MySQL 关闭时没有目录。 */
    private TenantCatalog catalog() {
        TenantCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant catalog is unavailable");
        }
        return catalog;
    }
}
