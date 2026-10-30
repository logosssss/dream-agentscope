package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/**
 * 审批单的进程内实现。对应以后的 approval_record，当前不落库。
 */
@Service
public class MemoryApprovalRecorder implements ApprovalRecorder {

    private final List<String> approvals = new CopyOnWriteArrayList<>();

    /**
     * 记下一次待审批。推送不在这里做，由事件交给接入层。
     */
    @Override
    public void requested(String approvalId, String toolId, String userId) {
        approvals.add(approvalId + "|" + toolId + "|" + userId);
    }

    /** 已记下的审批单。返回副本。 */
    public List<String> approvals() {
        return List.copyOf(approvals);
    }
}
