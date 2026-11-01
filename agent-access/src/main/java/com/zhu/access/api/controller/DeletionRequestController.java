package com.zhu.access.api.controller;

import com.zhu.access.api.dto.DeletionRequest;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.audit.api.DeletionRequestCatalog;
import com.zhu.audit.api.DeletionRequestSummary;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户提出数据删除请求。不批准，也不真正删除。
 */
@RestController
public class DeletionRequestController {

    private final ObjectProvider<DeletionRequestCatalog> catalogs;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public DeletionRequestController(ObjectProvider<DeletionRequestCatalog> catalogs, AccessAccountGate accounts) {
        this.catalogs = catalogs;
        this.accounts = accounts;
    }

    /**
     * 用户取请求头。没有用户或账号不可用时拒绝。租户没有时按空串登记。
     */
    @PostMapping("/api/deletion-requests")
    public ApiResult<DeletionRequestSummary> submit(@RequestBody DeletionRequest request) {
        accounts.requireActive();
        DeletionRequestCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "deletion request is unavailable");
        }
        DeletionRequest body = request == null ? new DeletionRequest(null, null) : request;
        return ApiResult.ok(catalog.submit(UserContext.get(), TenantContext.get(), body.dataType(), body.reason()));
    }
}
