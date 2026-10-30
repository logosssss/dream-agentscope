package com.zhu.audit.application;

import com.zhu.audit.api.ApprovalRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 审批单的进程内实现。MySQL 关闭时使用，对应 approval_record 的待处理状态。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "false", matchIfMissing = true)
public class MemoryApprovalRecorder implements ApprovalRecorder {

    private final List<String> approvals = new CopyOnWriteArrayList<>();

    /**
     * 记下一次待审批。推送不在这里做，由事件交给接入层。
     */
    @Override
    public void requested(String approvalId, String toolId, String userId, String sessionId) {
        approvals.add(approvalId + "|" + toolId + "|" + userId + "|" + sessionId);
    }

    /** 已记下的审批单。返回副本。 */
    public List<String> approvals() {
        return List.copyOf(approvals);
    }
}
