package com.zhu.admin.api.controller.deletion;

import com.zhu.admin.api.dto.deletion.DeletionDecisionRequest;
import com.zhu.ops.approval.ApprovalDecision;
import com.zhu.ops.deletion.DeletionRequestCatalog;
import com.zhu.admin.api.deletion.DeletionRequestDecider;
import com.zhu.ops.deletion.DeletionRequestSummary;
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
 * 管理进程看与决定数据删除请求。不执行删除：批准后由用户进程扫 APPROVED 真删，
 * 再把状态写成 DONE / FAILED；本进程只负责读结果。
 */
@RestController
public class AdminDeletionRequestController {

    private final ObjectProvider<DeletionRequestCatalog> catalogs;

    private final ObjectProvider<DeletionRequestDecider> deciders;

    /** MySQL 关闭时没有读口也没有决定口。接口仍在，调用会返回不可用。 */
    public AdminDeletionRequestController(
            ObjectProvider<DeletionRequestCatalog> catalogs,
            ObjectProvider<DeletionRequestDecider> deciders) {
        this.catalogs = catalogs;
        this.deciders = deciders;
    }

    /**
     * 近期删除请求。status 为空或 ALL 表示不过滤，可选 PENDING / APPROVED / REJECTED / RUNNING / DONE / FAILED。
     * 批准之后靠这里核对执行结果（含 executedAt / executedNote）。
     */
    @GetMapping("/api/admin/deletion-requests")
    public ApiResult<List<DeletionRequestSummary>> recent(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        DeletionRequestCatalog catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "deletion request is unavailable");
        }
        return ApiResult.ok(catalog.recent(status, limit));
    }

    /**
     * 把一张待处理改成通过或拒绝。不触发删除，真删异步发生。
     */
    @PostMapping("/api/admin/deletion-requests/{requestId}/decision")
    public ApiResult<DeletionRequestSummary> decide(
            @PathVariable String requestId,
            @RequestBody DeletionDecisionRequest request) {
        DeletionRequestDecider decider = deciders.getIfAvailable();
        if (decider == null) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "deletion request is unavailable");
        }
        DeletionDecisionRequest body = request == null ? new DeletionDecisionRequest(null) : request;
        return ApiResult.ok(decider.decide(requestId, ApprovalDecision.parse(body.decision())));
    }
}
