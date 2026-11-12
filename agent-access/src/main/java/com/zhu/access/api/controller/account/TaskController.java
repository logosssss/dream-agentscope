package com.zhu.access.api.controller.account;

import com.zhu.access.api.dto.account.TaskDetail;
import com.zhu.access.api.dto.account.TaskSummary;
import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.access.application.account.TaskQuery;
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
 * 当前用户查看已经落下的同步任务。列表不带时间；详情才带开始结束时间，仍不读参数。
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
        return ApiResult.ok(query().owned(UserContext.get(), tenantId(), limit));
    }

    /**
     * 别人的任务按找不到处理。不读 payload，也不是待办。
     */
    @GetMapping("/api/tasks/{taskId}")
    public ApiResult<TaskDetail> get(@PathVariable String taskId) {
        return ApiResult.ok(query().ownedOne(UserContext.get(), tenantId(), taskId));
    }

    /** 没有查询口时返回不可用，不改走内存。 */
    private TaskQuery query() {
        accounts.requireActive();
        TaskQuery query = queries.getIfAvailable();
        if (query == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "task query is unavailable");
        }
        return query;
    }

    /** 没有租户头按空串，和写入时一致。 */
    private static String tenantId() {
        return TenantContext.get() == null ? "" : TenantContext.get();
    }
}
