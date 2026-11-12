package com.zhu.admin.api.controller.deletion;

import com.zhu.admin.api.dto.deletion.DeletionDecisionRequest;
import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.deletion.DeletionRequestDecider;
import com.zhu.audit.api.deletion.DeletionRequestSummary;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程决定数据删除请求。不执行删除。
 */
@RestController
public class AdminDeletionRequestController {

    private final ObjectProvider<DeletionRequestDecider> deciders;

    /** MySQL 关闭时没有决定口。接口仍在，调用会返回不可用。 */
    public AdminDeletionRequestController(ObjectProvider<DeletionRequestDecider> deciders) {
        this.deciders = deciders;
    }

    /**
     * 把一张待处理改成通过或拒绝。不触发删除。
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
