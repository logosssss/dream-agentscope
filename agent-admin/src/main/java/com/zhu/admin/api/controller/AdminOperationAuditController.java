package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.OperationAuditRequest;
import com.zhu.audit.api.OperationAuditCatalog;
import com.zhu.audit.api.OperationAuditSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记后台操作审计。不改业务结果，也不写前后快照。
 */
@RestController
public class AdminOperationAuditController {

    private final ObjectProvider<OperationAuditCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminOperationAuditController(ObjectProvider<OperationAuditCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 追加一行。字段不合法由目录拒绝。
     */
    @PostMapping("/api/admin/operation-audits")
    public ApiResult<OperationAuditSummary> record(@RequestBody OperationAuditRequest request) {
        OperationAuditCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "operation audit is unavailable");
        }
        OperationAuditRequest body = request == null
                ? new OperationAuditRequest(null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog.record(
                body.operatorId(),
                body.tenantId(),
                body.operation(),
                body.objectType(),
                body.objectId()));
    }
}
