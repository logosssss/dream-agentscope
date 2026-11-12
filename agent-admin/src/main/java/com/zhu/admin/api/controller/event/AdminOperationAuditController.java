package com.zhu.admin.api.controller.event;

import com.zhu.admin.api.dto.event.OperationAuditRequest;
import com.zhu.audit.api.event.OperationAuditCatalog;
import com.zhu.audit.api.event.OperationAuditSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记和查看后台操作审计。不改业务结果，也不写前后快照。
 */
@RestController
public class AdminOperationAuditController {

    private final ObjectProvider<OperationAuditCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminOperationAuditController(ObjectProvider<OperationAuditCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近的操作。条数默认 50，上限 100。
     */
    @GetMapping("/api/admin/operation-audits")
    public ApiResult<List<OperationAuditSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 追加一行。字段不合法由目录拒绝。
     */
    @PostMapping("/api/admin/operation-audits")
    public ApiResult<OperationAuditSummary> record(@RequestBody OperationAuditRequest request) {
        OperationAuditRequest body = request == null
                ? new OperationAuditRequest(null, null, null, null, null)
                : request;
        return ApiResult.ok(catalog().record(
                body.operatorId(),
                body.tenantId(),
                body.operation(),
                body.objectType(),
                body.objectId()));
    }

    /** MySQL 关闭时没有目录。 */
    private OperationAuditCatalog catalog() {
        OperationAuditCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "operation audit is unavailable");
        }
        return catalog;
    }
}
