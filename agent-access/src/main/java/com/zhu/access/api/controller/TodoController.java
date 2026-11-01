package com.zhu.access.api.controller;

import com.zhu.access.api.dto.TodoRequest;
import com.zhu.audit.api.UserTodoCatalog;
import com.zhu.audit.api.UserTodoRow;
import com.zhu.common.context.UserContext;
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
 * 当前用户的未完成待办。可登记确认和任务，不关闭，也不返回别人的行。
 */
@RestController
public class TodoController {

    private final ObjectProvider<UserTodoCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public TodoController(ObjectProvider<UserTodoCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 只返回这次请求头里的用户。没有用户时拒绝。条数默认 50，最多 100。
     */
    @GetMapping("/api/todos")
    public ApiResult<List<UserTodoRow>> open(@RequestParam(defaultValue = "50") int limit) {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        return ApiResult.ok(catalog.openApprovals(userId, limit));
    }

    /**
     * 用户取请求头。只开确认和任务。不核运行或任务。
     */
    @PostMapping("/api/todos")
    public ApiResult<UserTodoRow> register(@RequestBody TodoRequest request) {
        String userId = UserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }
        UserTodoCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "todo catalog is unavailable");
        }
        TodoRequest body = request == null ? new TodoRequest(null, null, null) : request;
        return ApiResult.ok(catalog.open(userId, body.todoType(), body.refId(), body.title()));
    }
}
