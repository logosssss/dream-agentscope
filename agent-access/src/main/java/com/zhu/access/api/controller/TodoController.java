package com.zhu.access.api.controller;

import com.zhu.access.api.dto.TodoRequest;
import com.zhu.access.application.AccessAccountGate;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.api.UserTodoDetail;
import com.zhu.audit.api.UserTodoRow;
import com.zhu.common.context.UserContext;
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
 * 当前用户的待办。可登记、查看、完成或取消确认和任务，不返回别人的行。
 */
@RestController
public class TodoController {

    private final ObjectProvider<UserTodoCatalog> catalogs;

    private final AccessAccountGate accounts;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public TodoController(ObjectProvider<UserTodoCatalog> catalogs, AccessAccountGate accounts) {
        this.catalogs = catalogs;
        this.accounts = accounts;
    }

    /**
     * 只返回这次请求头里的用户。没有用户或账号不可用时拒绝。条数默认 50，最多 100。
     */
    @GetMapping("/api/todos")
    public ApiResult<List<UserTodoRow>> open(@RequestParam(defaultValue = "50") int limit) {
        accounts.requireActive();
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        return ApiResult.ok(catalog.openApprovals(UserContext.get(), limit));
    }

    /**
     * 别人的主键按找不到处理。含已关闭行，不带回截止时间。
     */
    @GetMapping("/api/todos/{todoId}")
    public ApiResult<UserTodoDetail> get(@PathVariable Long todoId) {
        accounts.requireActive();
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        return ApiResult.ok(catalog.ownedOne(UserContext.get(), todoId));
    }

    /**
     * 用户取请求头。只开确认和任务。不核运行或任务。
     */
    @PostMapping("/api/todos")
    public ApiResult<UserTodoRow> register(@RequestBody TodoRequest request) {
        accounts.requireActive();
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        TodoRequest body = request == null ? new TodoRequest(null, null, null) : request;
        return ApiResult.ok(catalog.open(UserContext.get(), body.todoType(), body.refId(), body.title()));
    }

    /**
     * 只关这个用户还开着的确认或任务。审批待办仍走决定接口。
     */
    @PostMapping("/api/todos/{todoId}/done")
    public ApiResult<UserTodoRow> complete(@PathVariable Long todoId) {
        accounts.requireActive();
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        return ApiResult.ok(catalog.complete(UserContext.get(), todoId));
    }

    /**
     * 只取消这个用户还开着的确认或任务。审批待办仍走决定接口。
     */
    @PostMapping("/api/todos/{todoId}/cancel")
    public ApiResult<UserTodoRow> cancel(@PathVariable Long todoId) {
        accounts.requireActive();
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        return ApiResult.ok(catalog.cancel(UserContext.get(), todoId));
    }
}
