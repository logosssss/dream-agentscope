package com.zhu.access.api.controller;

import com.zhu.access.api.dto.TaskSummary;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.access.application.TaskQuery;
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
 * 当前用户查看已经落下的同步任务。不返回任务参数，也不是待办列表。
 */
@RestController
public class TaskController {

    private final ObjectProvider<TaskQuery> queries;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有查询口。接口仍在，调用会返回不可用。 */
    public TaskController(ObjectProvider<TaskQuery> queries, AccessAccountGate accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户和租户。条数默认 50，最多 100。
     * 不读 payload，也不返回别人的行。待办仍走 `/api/todos`。
     */
    @GetMapping("/api/tasks")
    public ApiResult<List<TaskSummary>> list(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        TaskQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "task query is unavailable");
        }
        String tenantId = TenantContext.get() == null ? "" : TenantContext.get();
        return ApiResult.ok(query.owned(UserContext.get(), tenantId, limit));
    }
}
