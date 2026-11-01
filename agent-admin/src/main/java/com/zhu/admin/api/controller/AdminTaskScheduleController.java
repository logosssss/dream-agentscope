package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.TaskScheduleRequest;
import com.zhu.audit.api.TaskScheduleCatalog;
import com.zhu.audit.api.TaskScheduleSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记任务调度。不计算下次触发，也不消费。
 */
@RestController
public class AdminTaskScheduleController {

    private final ObjectProvider<TaskScheduleCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminTaskScheduleController(ObjectProvider<TaskScheduleCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 新增一条。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/task-schedules")
    public ApiResult<TaskScheduleSummary> register(@RequestBody TaskScheduleRequest request) {
        TaskScheduleCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "task schedule is unavailable");
        }
        TaskScheduleRequest body = request == null
                ? new TaskScheduleRequest(null, null, null, null)
                : request;
        return ApiResult.ok(catalog.register(body.scheduleId(), body.taskType(), body.cronExpr(), body.delaySeconds()));
    }
}
