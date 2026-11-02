package com.zhu.access.api.controller;

import com.zhu.access.api.dto.RunSummary;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.RunQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看已经结束的运行。不返回步骤或对话全文。
 */
@RestController
public class RunController {

    private final ObjectProvider<RunQuery> queries;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有查询口。接口仍在，调用会返回不可用。 */
    public RunController(ObjectProvider<RunQuery> queries, AccessAccountGate accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户和租户。条数默认 50，最多 100。
     * 不读步骤表，也不返回别人的行。
     */
    @GetMapping("/api/runs")
    public ApiResult<List<RunSummary>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        RunQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "run query is unavailable");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(query.owned(UserContext.get(), tenantId, limit));
    }
}
