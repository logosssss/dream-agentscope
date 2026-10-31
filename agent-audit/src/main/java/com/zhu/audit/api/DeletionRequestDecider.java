package com.zhu.audit.api;

/**
 * 删除请求的决定口。只改仍为 PENDING 的请求，不执行删除。
 */
public interface DeletionRequestDecider {

    /**
     * 写入通过或拒绝。请求不存在时失败；已经被决定过时也失败。
     */
    DeletionRequestSummary decide(String requestId, ApprovalDecision decision);
}
