package com.zhu.admin.api.dto.deletion;

/**
 * 删除请求的决定。只接受通过或拒绝。
 *
 * @param decision APPROVED 或 REJECTED
 */
public record DeletionDecisionRequest(String decision) {
}
