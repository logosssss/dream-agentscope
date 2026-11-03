package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TaskScheduleRequest;
import com.zhu.audit.api.TaskScheduleCatalog;
import com.zhu.audit.api.TaskScheduleSummary;
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
 * 管理进程登记、停用、恢复和查看任务调度。不计算下次触发，也不消费。
 */
@RestController
public class AdminTaskScheduleController {

    private final ObjectProvider<TaskScheduleCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTaskScheduleController(ObjectProvider<TaskScheduleCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的任务调度。条数默认 50，上限 100。含停用。不触发。
     */
    @GetMapping("/api/admin/task-schedules")
    public ApiResult<List<TaskScheduleSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增一条。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/task-schedules")
    public ApiResult<TaskScheduleSummary> register(@RequestBody TaskScheduleRequest request) {
        TaskScheduleRequest body = request == null
                ? new TaskScheduleRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog().register(body.scheduleId(), body.taskType(), body.cronExpr(), body.delaySeconds()));
    }

    /**
     * 停用仍启用的调度。不计算下次触发，也不消费。
     */
    @PostMapping("/api/admin/task-schedules/{scheduleId}/disable")
    public ApiResult<TaskScheduleSummary> disable(@PathVariable String scheduleId) {
        return ApiResult.ok(catalog().disable(scheduleId));
    }

    /**
     * 恢复已停用的调度。不计算下次触发，也不消费。
     */
    @PostMapping("/api/admin/task-schedules/{scheduleId}/restore")
    public ApiResult<TaskScheduleSummary> restore(@PathVariable String scheduleId) {
        return ApiResult.ok(catalog().restore(scheduleId));
    }

    /** MySQL 关闭时没有目录。 */
    private TaskScheduleCatalog catalog() {
        TaskScheduleCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "task schedule is unavailable");
        }
        return catalog;
    }
}
