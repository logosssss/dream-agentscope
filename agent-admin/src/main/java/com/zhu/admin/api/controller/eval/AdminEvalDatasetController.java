package com.zhu.admin.api.controller.eval;

import com.zhu.admin.api.dto.eval.EvalDatasetRequest;
import com.zhu.audit.api.eval.EvalDatasetCatalog;
import com.zhu.audit.api.eval.EvalDatasetSummary;
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
 * 管理进程登记、停用、恢复和查看评测数据集。不写用例，也不发起评测。
 */
@RestController
public class AdminEvalDatasetController {

    private final ObjectProvider<EvalDatasetCatalog> catalogs;

    /** MySQL 关闭时没有目录。接口仍在，调用会返回不可用。 */
    public AdminEvalDatasetController(ObjectProvider<EvalDatasetCatalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * 最近登记的评测数据集。条数默认 50，上限 100。含停用。不带回用例。
     */
    @GetMapping("/api/admin/eval-datasets")
    public ApiResult<List<EvalDatasetSummary>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(catalog().recent(limit));
    }

    /**
     * 新增 1.0.0。同一标识再提交一次由目录返回冲突。
     */
    @PostMapping("/api/admin/eval-datasets")
    public ApiResult<EvalDatasetSummary> register(@RequestBody EvalDatasetRequest request) {
        EvalDatasetRequest body = request == null
                ? new EvalDatasetRequest(null, null, null)
                : request;
        return ApiResult.ok(catalog().register(body.datasetId(), body.name(), body.description()));
    }

    /**
     * 停用仍启用的 1.0.0。停用后不再开跑。不删用例。
     */
    @PostMapping("/api/admin/eval-datasets/{datasetId}/disable")
    public ApiResult<EvalDatasetSummary> disable(@PathVariable String datasetId) {
        return ApiResult.ok(catalog().disable(datasetId));
    }

    /**
     * 恢复已停用的 1.0.0。
     */
    @PostMapping("/api/admin/eval-datasets/{datasetId}/restore")
    public ApiResult<EvalDatasetSummary> restore(@PathVariable String datasetId) {
        return ApiResult.ok(catalog().restore(datasetId));
    }

    /** MySQL 关闭时没有目录。 */
    private EvalDatasetCatalog catalog() {
        EvalDatasetCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "eval dataset is unavailable");
        }
        return catalog;
    }
}
