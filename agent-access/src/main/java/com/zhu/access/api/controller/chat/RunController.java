package com.zhu.access.api.controller.chat;

import com.zhu.access.api.dto.RunDetail;
import com.zhu.access.api.dto.RunSummary;
import com.zhu.access.application.auth.AccessAccountGate;
import com.zhu.access.application.chat.RunQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看已经结束的运行。列表不带步骤和开始结束时间；详情才带，仍不返回全文。
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
        return ApiResult.ok(query().owned(UserContext.get(), tenantId(), limit));
    }

    /**
     * 别人的运行按找不到处理。带开始和结束时间。步骤不含参数、耗时或 token。
     */
    @GetMapping("/api/runs/{runId}")
    public ApiResult<RunDetail> get(@PathVariable String runId) {
        return ApiResult.ok(query().ownedOne(UserContext.get(), tenantId(), runId));
    }

    /** 没有查询口时返回不可用，不改走内存。 */
    private RunQuery query() {
        accounts.requireActive();
        RunQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "run query is unavailable");
        }
        return query;
    }

    /** 没有租户头按空串，和写入时一致。 */
    private static String tenantId() {
        return TenantContext.get() == null ? "" : TenantContext.get();
    }
}
