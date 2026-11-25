package com.zhu.admin.api.controller.run;

import com.zhu.admin.api.run.AdminRunQuery;
import com.zhu.admin.api.run.RunDetail;
import com.zhu.admin.api.run.RunRow;
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
 * 管理端的运行只读查询。运维按请求号或运行号倒着查：先看列表定位那一轮，再看详情里的步骤。
 *
 * <p>只看不回改：这里没有任何写接口。
 */
@RestController
public class AdminRunController {

    private final ObjectProvider<AdminRunQuery> runs;

    /** MySQL 关闭时没有读口。接口仍在，调用返回 503，和删除请求的写法一致。 */
    public AdminRunController(ObjectProvider<AdminRunQuery> runs) {
        this.runs = runs;
    }

    /**
     * 近期运行。四个条件都可选，按时间倒序。请求号用来从报障单直接定位一轮。
     */
    @GetMapping("/api/admin/runs")
    public ApiResult<List<RunRow>> recent(
            @RequestParam(name = "requestId", required = false) String requestId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResult.ok(query().runs(requestId, userId, sessionId, status, limit));
    }

    /**
     * 一轮运行的详情，带步骤。找不到给 404，不返回空对象——调用方要能区分「没有这一轮」。
     */
    @GetMapping("/api/admin/runs/{runId}")
    public ApiResult<RunDetail> detail(@PathVariable String runId) {
        RunDetail detail = query().run(runId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "run not found");
        }
        return ApiResult.ok(detail);
    }

    /** 读口是按 MySQL 开关注册的，关掉时这里拿不到实例。 */
    private AdminRunQuery query() {
        AdminRunQuery available = runs.getIfAvailable();
        if (available == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "run query is unavailable");
        }
        return available;
    }
}
