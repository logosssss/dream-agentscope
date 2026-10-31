package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.EvalRunRequest;
import com.zhu.audit.api.EvalRunCatalog;
import com.zhu.audit.api.EvalRunSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记一次评测执行。不调用模型，也不把状态改成完成。
 */
@RestController
public class AdminEvalRunController {

    private final ObjectProvider<EvalRunCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminEvalRunController(ObjectProvider<EvalRunCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 为该数据集的 1.0.0 记下一次执行。没有数据集或没有用例时由目录拒绝。
     */
    @PostMapping("/api/admin/eval-runs")
    public ApiResult<EvalRunSummary> start(@RequestBody EvalRunRequest request) {
        EvalRunCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "eval run is unavailable");
        }
        EvalRunRequest body = request == null ? new EvalRunRequest(null) : request;
        return ApiResult.ok(catalog.start(body.datasetId()));
    }
}
