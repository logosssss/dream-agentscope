package com.zhu.admin.api.controller.eval;

import com.zhu.admin.api.dto.eval.EvalRunRequest;
import com.zhu.admin.api.dto.eval.EvalScoreRequest;
import com.zhu.audit.api.eval.EvalRunCatalog;
import com.zhu.audit.api.eval.EvalRunSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程登记和打分一次评测执行。不调用模型，也不写逐条结果。
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

    /**
     * 给仍在跑的执行写入通过数。已经打过分由目录返回冲突。
     */
    @PostMapping("/api/admin/eval-runs/{evalRunId}/score")
    public ApiResult<EvalRunSummary> score(@PathVariable String evalRunId, @RequestBody EvalScoreRequest request) {
        EvalRunCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "eval run is unavailable");
        }
        EvalScoreRequest body = request == null ? new EvalScoreRequest(null) : request;
        return ApiResult.ok(catalog.score(evalRunId, body.passedCases()));
    }
}
