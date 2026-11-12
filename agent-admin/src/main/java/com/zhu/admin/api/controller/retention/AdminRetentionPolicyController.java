package com.zhu.admin.api.controller.retention;

import com.zhu.admin.api.dto.retention.RetentionPolicyRequest;
import com.zhu.admin.api.dto.retention.RetentionPolicyScopeRequest;
import com.zhu.audit.api.retention.RetentionPolicyCatalog;
import com.zhu.audit.api.retention.RetentionPolicySummary;
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
 * 管理进程登记、停用、恢复和查看数据保留策略。不执行清理，也不改已有业务数据。
 */
@RestController
public class AdminRetentionPolicyController {

    private final ObjectProvider<RetentionPolicyCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminRetentionPolicyController(ObjectProvider<RetentionPolicyCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的保留策略。条数默认 50，上限 100。含停用。不执行清理。
     */
    @GetMapping("/api/admin/retention-policies")
    public ApiResult<List<RetentionPolicySummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增一条。同一数据类型和范围再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/retention-policies")
    public ApiResult<RetentionPolicySummary> register(@RequestBody RetentionPolicyRequest request) {
        RetentionPolicyRequest body = request == null
                ? new RetentionPolicyRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog().register(body.dataType(), body.scope(), body.retainDays(), body.action()));
    }

    /**
     * 停用仍启用的策略。空白范围按全局。不执行清理。
     */
    @PostMapping("/api/admin/retention-policies/{dataType}/disable")
    public ApiResult<RetentionPolicySummary> disable(
            @PathVariable String dataType, @RequestBody RetentionPolicyScopeRequest request) {
        RetentionPolicyScopeRequest body = request == null
                ? new RetentionPolicyScopeRequest(null)
                : request;
        return ApiResult.ok(catalog().disable(dataType, body.scope()));
    }

    /**
     * 恢复已停用的策略。空白范围按全局。不执行清理。
     */
    @PostMapping("/api/admin/retention-policies/{dataType}/restore")
    public ApiResult<RetentionPolicySummary> restore(
            @PathVariable String dataType, @RequestBody RetentionPolicyScopeRequest request) {
        RetentionPolicyScopeRequest body = request == null
                ? new RetentionPolicyScopeRequest(null)
                : request;
        return ApiResult.ok(catalog().restore(dataType, body.scope()));
    }

    /** MySQL 关闭时没有目录。 */
    private RetentionPolicyCatalog catalog() {
        RetentionPolicyCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "retention policy is unavailable");
        }
        return catalog;
    }
}
