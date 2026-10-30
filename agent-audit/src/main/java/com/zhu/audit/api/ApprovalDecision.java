package com.zhu.audit.api;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;

/**
 * 管理端可以写入的审批决定。待处理和过期由系统维护，不从这里进入。
 */
public enum ApprovalDecision {
    /** 批准，之后才允许执行。本枚举不负责执行。 */
    APPROVED,
    /** 拒绝。拒绝后不再执行。 */
    REJECTED;

    /**
     * 把请求里的文本收成枚举。空值、大小写不符或其它状态都拒绝，避免把 PENDING 再写回去。
     */
    public static ApprovalDecision parse(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
        try {
            return ApprovalDecision.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision must be APPROVED or REJECTED");
        }
    }
}
