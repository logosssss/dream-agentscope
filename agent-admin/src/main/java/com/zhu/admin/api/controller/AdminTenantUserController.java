package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TenantUserRequest;
import com.zhu.audit.api.TenantUserCatalog;
import com.zhu.audit.api.TenantUserSummary;
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
 * 管理进程登记、查看、停用和恢复租户用户。不写角色。
 */
@RestController
public class AdminTenantUserController {

    private final ObjectProvider<TenantUserCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTenantUserController(ObjectProvider<TenantUserCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 某个租户最近登记的用户。条数默认 50，上限 100。含停用。
     */
    @GetMapping("/api/admin/tenant-users")
    public ApiResult<List<TenantUserSummary>> recent(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(tenantId, limit));
    }

    /**
     * 给已登记租户挂一个可用用户。同一租户再挂这个用户由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-users")
    public ApiResult<TenantUserSummary> register(@RequestBody TenantUserRequest request) {
        TenantUserRequest body = request == null ? new TenantUserRequest(null, null, null) : request;
        return ApiResult.ok(catalog().register(body.tenantId(), body.userId(), body.displayName()));
    }

    /**
     * 把可用用户改成停用。已经停用由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-users/{tenantId}/{userId}/disable")
    public ApiResult<TenantUserSummary> disable(@PathVariable String tenantId, @PathVariable String userId) {
        return ApiResult.ok(catalog().disable(tenantId, userId));
    }

    /**
     * 把停用用户改回可用。已经可用由目录返回冲突。
     */
    @PostMapping("/api/admin/tenant-users/{tenantId}/{userId}/restore")
    public ApiResult<TenantUserSummary> restore(@PathVariable String tenantId, @PathVariable String userId) {
        return ApiResult.ok(catalog().restore(tenantId, userId));
    }

    /** MySQL 关闭时没有目录。 */
    private TenantUserCatalog catalog() {
        TenantUserCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "tenant user catalog is unavailable");
        }
        return catalog;
    }
}
