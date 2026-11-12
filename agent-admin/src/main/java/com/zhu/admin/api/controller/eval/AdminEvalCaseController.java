package com.zhu.admin.api.controller.eval;

import com.zhu.admin.api.dto.eval.EvalCaseRequest;
import com.zhu.audit.api.eval.EvalCaseCatalog;
import com.zhu.audit.api.eval.EvalCaseSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程给评测数据集追加用例。不执行，也不改版本。
 */
@RestController
public class AdminEvalCaseController {

    private final ObjectProvider<EvalCaseCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminEvalCaseController(ObjectProvider<EvalCaseCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 追加到该数据集的 1.0.0。数据集不存在或序号重复由目录返回失败。
     */
    @PostMapping("/api/admin/eval-datasets/{datasetId}/cases")
    public ApiResult<EvalCaseSummary> add(
            @PathVariable String datasetId,
            @RequestBody EvalCaseRequest request) {
        EvalCaseCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "eval case is unavailable");
        }
        EvalCaseRequest body = request == null ? new EvalCaseRequest(null, null, null) : request;
        return ApiResult.ok(catalog.add(datasetId, body.caseNo(), body.inputText(), body.expectedText()));
    }
}
