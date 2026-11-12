package com.zhu.access.api.controller.account;

import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.audit.api.usage.TokenUsageDetail;
import com.zhu.audit.api.usage.TokenUsageQuery;
import com.zhu.audit.api.usage.TokenUsageRow;
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
 * 当前用户查看已经记下的 token 用量。不带提示词，也不改金额。
 */
@RestController
public class UsageController {

    private final ObjectProvider<TokenUsageQuery> queries;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有查询口。接口仍在，调用会返回不可用。 */
    public UsageController(ObjectProvider<TokenUsageQuery> queries, AccessAccountGate accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户和租户。条数默认 50，最多 100。
     * 不返回别人的行，也不带提示词。
     */
    @GetMapping("/api/usage")
    public ApiResult<List<TokenUsageRow>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        TokenUsageQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "usage is unavailable");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(query.owned(UserContext.get(), tenantId, limit));
    }

    /**
     * 别人的主键按找不到处理。带记账时间，不改金额，也不带提示词。
     */
    @GetMapping("/api/usage/{usageId}")
    public ApiResult<TokenUsageDetail> get(@PathVariable Long usageId) {
        accounts.requireActive();
        TokenUsageQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "usage is unavailable");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(query.ownedOne(UserContext.get(), tenantId, usageId));
    }
}
