package com.zhu.admin.api.controller;

import com.zhu.audit.api.QuotaUsageQuery;
import com.zhu.audit.api.QuotaUsageRow;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程查看配额消耗。只读一个租户的最近流水，不回补。
 */
@RestController
public class AdminQuotaUsageController {

    private final ObjectProvider<QuotaUsageQuery> queries;

    /** MySQL 关闭时没有查询。接口仍在，调用会返回不可用。 */
    public AdminQuotaUsageController(ObjectProvider<QuotaUsageQuery> queries) {
        this.queries = queries;
    }

    /**
     * 最近的流水。条数默认 50，上限 100。必须带租户标识。
     */
    @GetMapping("/api/admin/quota-usage")
    public ApiResult<List<QuotaUsageRow>> recent(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        QuotaUsageQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "quota usage is unavailable");
        }
        return ApiResult.ok(query.recent(tenantId, limit));
    }
}
