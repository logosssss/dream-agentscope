package com.zhu.audit.api;

public interface ApprovalRecorder {

    void requested(String approvalId, String toolId, String userId);
}
